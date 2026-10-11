package docsearch;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

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
