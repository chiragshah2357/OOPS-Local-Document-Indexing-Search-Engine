package docsearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Checks that the classes behave as expected and prints PASS or FAIL for
 * each check. It is an ordinary main method, so no test framework is needed.
 *
 * Run it from the backend folder with test.bat.
 */
public class SelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws IOException {
        testTokenizer();
        List<Document> documents = testLoader();
        testIndex(documents);
        testMatchers();
        testRanking();
        testSnippet();
        testHistory();
        testSearchEngine();
        testJson();
        testLlm();
        testGemini();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failed++;
            System.out.println("FAIL  " + name);
        }
    }

    private static void testTokenizer() {
        Tokenizer tokenizer = new Tokenizer();

        check("tokenizer lowercases and splits on punctuation",
                tokenizer.tokenize("A Thread, is the SMALLEST unit!").toString().equals("[thread, smallest, unit]"));
        check("tokenizer keeps the last word of the text",
                tokenizer.tokenize("hello world").toString().equals("[hello, world]"));
        check("tokenizer drops stop words", tokenizer.tokenize("the and of").isEmpty());
        check("tokenizer keeps digits", tokenizer.tokenize("Java 8").toString().equals("[java, 8]"));
        check("tokenizer keeps repeated words so they can be counted", tokenizer.tokenize("java Java JAVA").size() == 3);
        check("tokenizer handles empty text", tokenizer.tokenize("").isEmpty());
    }

    private static List<Document> testLoader() throws IOException {
        List<Document> documents = new DocumentLoader().loadFolder("sample-docs");

        check("loader reads the 6 sample documents", documents.size() == 6);
        check("loader reads files in name order", documents.get(0).getTitle().equals("exception-handling.txt"));
        check("loader gives ids 1 to 6", documents.get(0).getId() == 1 && documents.get(5).getId() == 6);
        check("loader reads the text of a file", documents.get(5).getText().startsWith("A thread is"));

        boolean rejected = false;
        try {
            new DocumentLoader().loadFolder("no-such-folder");
        } catch (IOException e) {
            rejected = true;
        }
        check("loader rejects a path that is not a folder", rejected);

        // Build a small folder with a sub folder and a file that is not .txt.
        File root = new File("out/selftest-folder");
        File sub = new File(root, "sub");
        sub.mkdirs();
        write(new File(root, "a.txt"), "alpha");
        write(new File(root, "notes.md"), "ignored");
        write(new File(sub, "b.txt"), "beta");

        List<Document> found = new DocumentLoader().loadFolder(root.getPath());
        check("loader reads sub folders and ignores files that are not .txt",
                found.size() == 2 && found.get(0).getTitle().equals("a.txt") && found.get(1).getTitle().equals("b.txt"));

        new File(sub, "b.txt").delete();
        new File(root, "notes.md").delete();
        new File(root, "a.txt").delete();
        sub.delete();
        root.delete();

        return documents;
    }

    private static void testIndex(List<Document> documents) {
        InvertedIndex index = new InvertedIndex(new Tokenizer());
        for (Document document : documents) {
            index.addDocument(document);
        }

        check("index holds 6 documents", index.getDocumentCount() == 6);
        check("index knows 169 unique words, the same as the frontend demo", index.getWordCount() == 169);

        Map<Integer, Integer> thread = index.countsFor("thread");
        check("'thread' is in exactly one document", thread.size() == 1);
        int id = thread.keySet().iterator().next();
        check("'thread' is in threads-and-synchronization.txt",
                index.getDocument(id).getTitle().equals("threads-and-synchronization.txt"));
        check("'thread' appears 2 times there", thread.get(id) == 2);

        check("'java' is in 4 documents", index.countsFor("java").size() == 4);
        check("an unknown word gives an empty map", index.countsFor("zebra").isEmpty());
        check("stop words are not indexed", index.countsFor("the").isEmpty());

        thread.clear(); // this is only a copy of the index data
        check("changing a returned map does not change the index", index.countsFor("thread").size() == 1);

        List<WordCount> top = index.topWords(3);
        check("the most frequent word is 'java', 6 times in 4 documents",
                top.get(0).getWord().equals("java") && top.get(0).getTotal() == 6 && top.get(0).getDocuments() == 4);
        check("top words come out by total: java 6, file 5, class 4",
                top.size() == 3 && top.get(1).getWord().equals("file") && top.get(1).getTotal() == 5
                        && top.get(2).getWord().equals("class") && top.get(2).getTotal() == 4);
        check("asking for more words than exist gives every word once", index.topWords(1000).size() == 169);
    }

    private static void testMatchers() {
        Set<Integer> first = new HashSet<Integer>(Arrays.asList(1, 2, 3));
        Set<Integer> second = new HashSet<Integer>(Arrays.asList(2, 3, 4));

        Set<Integer> both = new AllWordsMatcher().combine(first, second);
        check("AllWordsMatcher keeps only ids that are in both sets",
                both.size() == 2 && both.contains(2) && both.contains(3));

        Set<Integer> either = new AnyWordMatcher().combine(first, second);
        check("AnyWordMatcher keeps ids from either set",
                either.size() == 4 && either.contains(1) && either.contains(4));

        check("combining does not change the original sets", first.size() == 3 && second.size() == 3);
        check("mode 'any' gives an AnyWordMatcher", Matcher.forMode("any") instanceof AnyWordMatcher);
        check("the mode name is not case sensitive", Matcher.forMode("ANY") instanceof AnyWordMatcher);
        check("any other mode gives an AllWordsMatcher",
                Matcher.forMode("all") instanceof AllWordsMatcher && Matcher.forMode(null) instanceof AllWordsMatcher);
    }

    private static void testRanking() {
        Map<String, Integer> none = new HashMap<String, Integer>();
        PriorityQueue<SearchResult> queue = new PriorityQueue<SearchResult>();
        queue.add(new SearchResult(new Document(3, "c.txt", "c.txt", ""), 2, none, ""));
        queue.add(new SearchResult(new Document(1, "a.txt", "a.txt", ""), 1, none, ""));
        queue.add(new SearchResult(new Document(2, "b.txt", "b.txt", ""), 2, none, ""));

        String order = queue.poll().getDocument().getTitle() + " "
                + queue.poll().getDocument().getTitle() + " "
                + queue.poll().getDocument().getTitle();
        check("results come out by score, then by file name", order.equals("b.txt c.txt a.txt"));

        PriorityQueue<WordCount> words = new PriorityQueue<WordCount>();
        words.add(new WordCount("data", 4, 4));
        words.add(new WordCount("java", 6, 4));
        words.add(new WordCount("class", 4, 2));
        String wordOrder = words.poll().getWord() + " " + words.poll().getWord() + " " + words.poll().getWord();
        check("word counts come out by total, then alphabetically", wordOrder.equals("java class data"));
    }

    private static void testSnippet() {
        String shortText = "The Java Collections Framework provides ready made data structures.";
        check("a short text is returned whole",
                SnippetBuilder.build(shortText, Arrays.asList("java")).equals(shortText));

        String longText = "A thread is the smallest unit of execution in Java. Multithreading lets a program "
                + "do several things at once, but threads that share data need synchronization to avoid "
                + "race conditions. Use the synchronized keyword or a ReentrantLock so only one thread "
                + "changes the shared balance at a time.";
        String snippet = SnippetBuilder.build(longText, Arrays.asList("synchronization"));
        check("a long text is cut around the match, with ... at the cut edges",
                snippet.contains("synchronization") && snippet.startsWith("... ") && snippet.endsWith(" ..."));
        check("the cut snippet is shorter than the text", snippet.length() < longText.length());

        check("line breaks become single spaces",
                SnippetBuilder.build("one\ntwo\n\nthree", Arrays.asList("java")).equals("one two three"));
        check("with no match the snippet starts at the beginning",
                SnippetBuilder.build("hello world", Arrays.asList("zebra")).equals("hello world"));
    }

    private static void testHistory() {
        SearchHistory history = new SearchHistory();
        check("a new history is empty", history.getAll().isEmpty());

        history.add("java", "all");
        history.add("thread", "any");
        check("the newest search comes first", history.getAll().get(0).getQuery().equals("thread"));

        history.add("java", "all");
        check("repeating a search moves it to the front without a copy",
                history.getAll().size() == 2 && history.getAll().get(0).getQuery().equals("java"));

        history.add("java", "any");
        check("the same words in another mode are a different search", history.getAll().size() == 3);

        for (int i = 0; i < 15; i++) {
            history.add("word" + i, "all");
        }
        check("only the last 10 searches are kept",
                history.getAll().size() == SearchHistory.LIMIT && history.getAll().get(0).getQuery().equals("word14"));

        List<HistoryEntry> copy = history.getAll();
        copy.clear();
        check("changing the returned list does not change the history", history.getAll().size() == SearchHistory.LIMIT);

        history.clear();
        check("clear empties the history", history.getAll().isEmpty());
    }

    private static void testSearchEngine() throws IOException {
        SearchEngine engine = new SearchEngine();

        boolean refused = false;
        try {
            engine.search("java", "all");
        } catch (IndexNotReadyException e) {
            refused = true;
        }
        check("searching before indexing raises IndexNotReadyException", refused && !engine.isReady());

        boolean wordsRefused = false;
        try {
            engine.getTopWords(5);
        } catch (IndexNotReadyException e) {
            wordsRefused = true;
        }
        check("asking for top words before indexing raises IndexNotReadyException", wordsRefused);
        check("there are no index stats before indexing", engine.getIndexStats() == null);

        boolean badPath = false;
        try {
            engine.indexFolder("no-such-folder");
        } catch (IOException e) {
            badPath = true;
        }
        check("indexing a path that is not a folder fails and the engine stays not ready",
                badPath && !engine.isReady());

        IndexStats stats = engine.indexFolder("sample-docs");
        check("indexing reports 6 documents and 169 words", stats.getDocuments() == 6 && stats.getWords() == 169);
        check("the engine is ready after indexing", engine.isReady());
        check("the engine remembers the stats of its index",
                engine.getIndexStats() != null && engine.getIndexStats().getPath().equals("sample-docs"));

        try {
            SearchResponse any = engine.search("java thread", "any");
            List<SearchResult> results = any.getResults();
            check("'java thread' in any mode finds 4 files", any.getTotal() == 4);
            check("results are ranked 3, 2, 2, 1 hits, the same as the frontend demo",
                    scoresOf(any).equals("[3, 2, 2, 1]"));
            check("the best result is threads-and-synchronization.txt",
                    results.get(0).getDocument().getTitle().equals("threads-and-synchronization.txt"));
            check("equal scores are ordered by file name",
                    results.get(1).getDocument().getTitle().equals("file-io-basics.txt")
                            && results.get(2).getDocument().getTitle().equals("java-collections.txt"));
            check("a result says how often each word appears", results.get(0).getMatches().toString().equals("{java=1, thread=2}"));
            check("a result has a snippet around a match", results.get(0).getSnippet().contains("thread"));

            SearchResponse all = engine.search("java thread", "all");
            check("'java thread' in all mode finds only the file with both words",
                    all.getTotal() == 1 && all.getResults().get(0).getScore() == 3);

            check("searched words are lowercase and without repeats",
                    engine.search("Java JAVA java", "all").getTerms().toString().equals("[java]"));
            check("a query of only stop words has no terms and no results",
                    engine.search("the and of", "all").getTerms().isEmpty());
            check("an unknown word gives no results", engine.search("zebra", "any").getTotal() == 0);
            check("in all mode one unknown word means no results", engine.search("java zebra", "all").getTotal() == 0);
            check("in any mode one unknown word does not matter", engine.search("java zebra", "any").getTotal() == 4);

            int bestId = results.get(0).getDocument().getId();
            check("a result's document can be fetched whole by its id",
                    engine.getDocument(bestId).getText().startsWith("A thread is the smallest unit"));
            check("an unknown document id gives null", engine.getDocument(999) == null);
            check("the engine gives its top words", engine.getTopWords(1).get(0).getWord().equals("java"));
        } catch (IndexNotReadyException e) {
            check("searching after indexing must not raise IndexNotReadyException", false);
        }

        check("every different search is recorded in the history", engine.getHistory().size() == 7);
        check("the newest search is first in the history", engine.getHistory().get(0).getQuery().equals("java zebra"));
        engine.clearHistory();
        check("the engine can clear its history", engine.getHistory().isEmpty());
    }

    private static void testJson() throws IOException {
        check("quote escapes quotes, backslashes and line breaks",
                Json.quote("say \"hi\"\nC:\\notes").equals("\"say \\\"hi\\\"\\nC:\\\\notes\""));
        check("quote escapes other control characters", Json.quote("a\u0001b").equals("\"a\\u0001b\""));
        check("error has a message and a code",
                Json.error("Not a folder", "BAD_PATH").equals("{\"error\":\"Not a folder\",\"code\":\"BAD_PATH\"}"));
        check("index stats use the field names the frontend expects",
                Json.indexStats(new IndexStats("sample-docs", 6, 169, 5, 0)).equals(
                        "{\"path\":\"sample-docs\",\"documents\":6,\"terms\":169,\"tookMs\":5,\"skipped\":0}"));

        List<HistoryEntry> entries = Arrays.asList(new HistoryEntry("java", "all", 123L), new HistoryEntry("a \"b\"", "any", 456L));
        check("history is a list of query, mode and time",
                Json.history(entries).equals("[{\"query\":\"java\",\"mode\":\"all\",\"at\":123},{\"query\":\"a \\\"b\\\"\",\"mode\":\"any\",\"at\":456}]"));
        check("an empty history is an empty list", Json.history(new ArrayList<HistoryEntry>()).equals("[]"));

        check("health says nothing is indexed yet, and that Gemini is off",
                Json.health(null, new OfflineProvider()).equals(
                        "{\"ok\":true,\"index\":null,\"llm\":{\"available\":false,\"model\":\"none\",\"calls\":0}}"));
        check("health includes the index stats once there is an index",
                Json.health(new IndexStats("sample-docs", 6, 169, 5, 0), new OfflineProvider())
                        .startsWith("{\"ok\":true,\"index\":{\"path\":\"sample-docs\",\"documents\":6"));
        Answer answer = new Answer("Yes [a.txt]", Arrays.asList(new Document(2, "a.txt", "a.txt", "x")), Arrays.asList("thread"), true);
        check("an answer lists its text, its search words and its source files",
                Json.answer(answer, 3).equals("{\"answer\":\"Yes [a.txt]\",\"fromLlm\":true,\"terms\":[\"thread\"],"
                        + "\"sources\":[{\"id\":2,\"title\":\"a.txt\"}],\"llmCalls\":3}"));
        check("top words are a list of word, total and documents",
                Json.words(Arrays.asList(new WordCount("java", 6, 4), new WordCount("file", 5, 2))).equals(
                        "[{\"word\":\"java\",\"total\":6,\"documents\":4},{\"word\":\"file\",\"total\":5,\"documents\":2}]"));
        check("a document has its id, title, path with forward slashes, and escaped text",
                Json.document(new Document(2, "a.txt", "notes\\a.txt", "line one\nline two")).equals(
                        "{\"id\":2,\"title\":\"a.txt\",\"path\":\"notes/a.txt\",\"text\":\"line one\\nline two\"}"));

        check("readString reads a plain value", Json.readString("{\"path\":\"sample-docs\"}", "path").equals("sample-docs"));
        check("readString unescapes Windows paths",
                Json.readString("{\"path\":\"C:\\\\notes\\\\docs\"}", "path").equals("C:\\notes\\docs"));
        check("readString ignores spaces around the colon", Json.readString("{ \"path\" :  \"x\" }", "path").equals("x"));
        check("readString gives null for a missing field", Json.readString("{\"other\":\"x\"}", "path") == null);
        check("readString gives null when the value is not text", Json.readString("{\"path\":5}", "path") == null);
        check("readString gives null when the closing quote is missing", Json.readString("{\"path\":\"abc", "path") == null);

        SearchEngine engine = new SearchEngine();
        engine.indexFolder("sample-docs");
        String json;
        try {
            json = Json.searchResponse(engine.search("java thread", "any"));
        } catch (IndexNotReadyException e) {
            json = "";
        }
        check("a search response starts with the query, mode, words and total",
                json.startsWith("{\"query\":\"java thread\",\"mode\":\"any\",\"terms\":[\"java\",\"thread\"],\"total\":4,\"tookMs\":"));
        check("a search response lists each result with its score and word counts",
                json.contains("\"title\":\"threads-and-synchronization.txt\"") && json.contains("\"score\":3")
                        && json.contains("\"matches\":{\"java\":1,\"thread\":2}"));
        check("paths in a search response use forward slashes", json.contains("\"path\":\"sample-docs/threads-and-synchronization.txt\""));

        String smart;
        try {
            smart = Json.searchResponse(engine.search("thread lock", "any"), Arrays.asList("lock"), null, 1);
        } catch (IndexNotReadyException e) {
            smart = "";
        }
        check("a smart search response adds the words Gemini suggested and the call count",
                smart.contains("\"results\":[") && smart.endsWith(",\"added\":[\"lock\"],\"llmCalls\":1}"));
    }

    /** A stand-in for Gemini: it gives the same reply to every prompt and remembers the last prompt. */
    static class FakeProvider extends LlmProvider {
        private final String reply;
        private String lastPrompt = "";

        FakeProvider(String reply) {
            this.reply = reply;
        }

        public String complete(String prompt, int maxTokens) {
            recordCall();
            lastPrompt = prompt;
            return reply;
        }

        public boolean isAvailable() {
            return true;
        }

        public String getModel() {
            return "fake";
        }
    }

    private static void testLlm() throws IOException {
        FakeProvider fake = new FakeProvider("Concurrency, thread, the Deadlock, synchronization, lock, mutex, extra");
        QueryExpander expander = new QueryExpander(fake);
        try {
            check("smart search adds up to 5 related words, lowercase, without stop words or the query's own words",
                    expander.expand("Thread").toString().equals("[concurrency, deadlock, synchronization, lock, mutex]"));
            expander.expand("thread");
            check("the same query is sent to the LLM only once", fake.getCalls() == 1);
            check("a query of only stop words is never sent to the LLM",
                    expander.expand("the and of").isEmpty() && fake.getCalls() == 1);
        } catch (LlmException e) {
            check("the expander must not fail with a working provider", false);
        }

        boolean refused = false;
        try {
            new OfflineProvider().complete("hello", 10);
        } catch (LlmException e) {
            refused = true;
        }
        check("the offline provider refuses, and says it is not available", refused && !new OfflineProvider().isAvailable());

        check("the longer snippet for the LLM is cut on whole words around the match",
                SnippetBuilder.build("one two three four five six seven", Arrays.asList("four"), 5, 5).equals("... four ..."));

        SearchEngine engine = new SearchEngine();
        engine.indexFolder("sample-docs");
        FakeProvider writer = new FakeProvider("A thread is the smallest unit of execution [threads-and-synchronization.txt].");
        AnswerGenerator answers = new AnswerGenerator(engine, writer);
        try {
            Answer answer = answers.answer("What is a thread?");
            check("an answer is written by the LLM and names the best file first",
                    answer.isFromLlm() && answer.getSources().get(0).getTitle().equals("threads-and-synchronization.txt"));
            check("at most 3 files go to the LLM", answer.getSources().size() <= AnswerGenerator.MAX_FILES);
            check("the prompt holds the question and the matching notes",
                    writer.lastPrompt.contains("Question: What is a thread?")
                            && writer.lastPrompt.contains("[threads-and-synchronization.txt] A thread is"));
            answers.answer("what is a thread?  ");
            check("asking the same question again does not call the LLM again", writer.getCalls() == 1);

            Answer none = answers.answer("zebra giraffe");
            check("when no file matches, the LLM is not asked", !none.isFromLlm() && none.getSources().isEmpty() && writer.getCalls() == 1);

            Answer offline = new AnswerGenerator(engine, new OfflineProvider()).answer("What is a thread?");
            check("without Gemini, a question still lists the best files", !offline.isFromLlm() && offline.getSources().size() == 1);
        } catch (Exception e) {
            check("asking questions must not fail with a working provider (" + e + ")", false);
        }
    }

    private static void testGemini() throws IOException {
        check("the Gemini request holds the prompt, the token limit and low thinking",
                Json.geminiRequest("say \"hi\"", 64, true).equals("{\"contents\":[{\"parts\":[{\"text\":\"say \\\"hi\\\"\"}]}],"
                        + "\"generationConfig\":{\"maxOutputTokens\":64,\"thinkingConfig\":{\"thinkingLevel\":\"LOW\"}}}"));
        check("the request can leave out the thinking setting", !Json.geminiRequest("x", 64, false).contains("thinking"));

        try {
            check("the answer is read from candidates[0].content.parts[0].text",
                    GeminiProvider.readText("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"thread, lock\"}],"
                            + "\"role\":\"model\"},\"finishReason\":\"STOP\"}]}").equals("thread, lock"));
        } catch (LlmException e) {
            check("a normal Gemini reply must be readable", false);
        }
        boolean empty = false;
        try {
            GeminiProvider.readText("{\"candidates\":[]}");
        } catch (LlmException e) {
            empty = true;
        }
        check("an empty reply is an error, not an empty answer", empty);

        // A tiny fake Gemini on this computer, so the HTTP part is tested without the internet.
        // It refuses the thinking setting (as some models do) and rejects the key "bad-key".
        final List<String> requests = new ArrayList<String>();
        HttpServer fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext("/", new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                String body = readAll(exchange.getRequestBody());
                String key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
                requests.add(key + " " + exchange.getRequestURI().getPath());
                int status;
                String reply;
                if ("bad-key".equals(key)) {
                    status = 400;
                    reply = "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\"}}";
                } else if (body.contains("thinkingLevel")) {
                    status = 400;
                    reply = "{\"error\":{\"code\":400,\"message\":\"Thinking level is not supported for this model.\"}}";
                } else {
                    status = 200;
                    reply = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"concurrency, lock\"}]}}]}";
                }
                byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            }
        });
        fake.start();
        String address = "http://127.0.0.1:" + fake.getAddress().getPort() + "/v1beta/models/";
        try {
            GeminiProvider gemini = new GeminiProvider("test-key", "test-model", address);
            check("a model that refuses the thinking setting is asked once more without it",
                    gemini.complete("related words for thread", 64).equals("concurrency, lock") && gemini.getCalls() == 2);
            check("the key goes in the x-goog-api-key header and the model in the address",
                    requests.get(0).equals("test-key /v1beta/models/test-model:generateContent"));
        } catch (LlmException e) {
            check("the call to the fake Gemini must work (" + e.getMessage() + ")", false);
        }

        GeminiProvider wrongKey = new GeminiProvider("bad-key", "test-model", address);
        String message = "";
        try {
            wrongKey.complete("hello", 64);
        } catch (LlmException e) {
            message = e.getMessage();
        }
        check("a wrong API key gives a clear message and is not retried",
                message.contains("did not accept the API key") && wrongKey.getCalls() == 1);
        fake.stop(0);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, count);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    // The scores of a response as text, for example [3, 2, 2, 1].
    private static String scoresOf(SearchResponse response) {
        StringBuilder text = new StringBuilder("[");
        for (SearchResult result : response.getResults()) {
            if (text.length() > 1) {
                text.append(", ");
            }
            text.append(result.getScore());
        }
        return text.append("]").toString();
    }

    private static void write(File file, String text) throws IOException {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
