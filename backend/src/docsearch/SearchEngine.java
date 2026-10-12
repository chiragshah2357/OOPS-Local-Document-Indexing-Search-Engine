package docsearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The search engine: it ties the other classes together.
 *
 *   indexFolder  loads a folder with DocumentLoader and builds an InvertedIndex
 *   search       looks the words up, combines them with a Matcher, scores and ranks the documents
 *
 * It handles one request at a time, which is all the console menu and the
 * web server need.
 */
public class SearchEngine {

    private final Tokenizer tokenizer = new Tokenizer();
    private final SearchHistory history = new SearchHistory();
    private InvertedIndex index; // stays null until a folder has been indexed

    public boolean isReady() {
        return index != null;
    }

    /**
     * Reads every .txt file in the folder and builds a new index from it.
     * The old index is replaced only when the new one is complete.
     *
     * @throws IOException if the path is not a folder
     */
    public IndexStats indexFolder(String folderPath) throws IOException {
        long started = System.nanoTime();

        DocumentLoader loader = new DocumentLoader();
        List<Document> documents = loader.loadFolder(folderPath);

        InvertedIndex fresh = new InvertedIndex(tokenizer);
        for (Document document : documents) {
            fresh.addDocument(document);
        }
        index = fresh;

        return new IndexStats(folderPath, fresh.getDocumentCount(), fresh.getWordCount(),
                elapsedMs(started), loader.getSkipped().size());
    }

    /**
     * Searches the index.
     *
     * @param query    the words to look for, as typed
     * @param modeName "all" (a document needs every word) or "any" (at least one)
     * @throws IndexNotReadyException if no folder has been indexed yet
     */
    public SearchResponse search(String query, String modeName) throws IndexNotReadyException {
        if (index == null) {
            throw new IndexNotReadyException();
        }
        long started = System.nanoTime();

        String cleanQuery = query.trim();
        Matcher matcher = Matcher.forMode(modeName);

        // The words to look for: no stop words, no repeats, in the order they were typed.
        List<String> terms = new ArrayList<String>(new LinkedHashSet<String>(tokenizer.tokenize(cleanQuery)));

        // Step 1: for every word, which documents contain it and how many times.
        Map<String, Map<Integer, Integer>> countsByWord = new LinkedHashMap<String, Map<Integer, Integer>>();
        for (String term : terms) {
            countsByWord.put(term, index.countsFor(term));
        }

        // Step 2: combine the document sets of the words. The matcher decides how (all or any).
        Set<Integer> ids = null;
        for (String term : terms) {
            Set<Integer> found = countsByWord.get(term).keySet();
            if (ids == null) {
                ids = new HashSet<Integer>(found);
            } else {
                ids = matcher.combine(ids, found);
            }
        }

        // Step 3: score every document and put it in a PriorityQueue, which always keeps the best result on top.
        PriorityQueue<SearchResult> queue = new PriorityQueue<SearchResult>();
        if (ids != null) {
            for (int id : ids) {
                queue.add(buildResult(index.getDocument(id), terms, countsByWord));
            }
        }

        // Step 4: take the results out one by one, best first.
        List<SearchResult> results = new ArrayList<SearchResult>();
        while (!queue.isEmpty()) {
            results.add(queue.poll());
        }

        history.add(cleanQuery, matcher.getName());
        return new SearchResponse(cleanQuery, matcher.getName(), terms, results, elapsedMs(started));
    }

    /** The remembered searches, newest first. */
    public List<HistoryEntry> getHistory() {
        return history.getAll();
    }

    public void clearHistory() {
        history.clear();
    }

    // The score of a document is the total number of times the search words appear in it.
    private SearchResult buildResult(Document document, List<String> terms,
                                     Map<String, Map<Integer, Integer>> countsByWord) {
        Map<String, Integer> matches = new LinkedHashMap<String, Integer>();
        int score = 0;

        for (String term : terms) {
            Integer count = countsByWord.get(term).get(document.getId());
            if (count != null) {
                matches.put(term, count);
                score += count;
            }
        }

        String snippet = SnippetBuilder.build(document.getText(), new ArrayList<String>(matches.keySet()));
        return new SearchResult(document, score, matches, snippet);
    }

    private long elapsedMs(long startedNanos) {
        long ms = (System.nanoTime() - startedNanos) / 1000000;
        return Math.max(1, ms);
    }
}
