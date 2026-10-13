package docsearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Remembers which documents contain which words, and how many times.
 *
 * It works like the index at the back of a book. Instead of reading every
 * document for every search, we look the word up once:
 *
 *     "thread"  ->  { document 6 : 2 times }
 *     "java"    ->  { document 2 : 2 times, document 3 : 2 times, ... }
 */
public class InvertedIndex {

    // word -> (document id -> how many times the word appears in that document)
    private final Map<String, Map<Integer, Integer>> index = new HashMap<>();

    // document id -> the document itself, so results can show its title and text
    private final Map<Integer, Document> documents = new HashMap<>();

    private final Tokenizer tokenizer;

    public InvertedIndex(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    /** Splits the document into words and counts each word. */
    public void addDocument(Document document) {
        documents.put(document.getId(), document);

        for (String word : tokenizer.tokenize(document.getText())) {
            Map<Integer, Integer> counts = index.get(word);
            if (counts == null) { // first time we see this word
                counts = new HashMap<>();
                index.put(word, counts);
            }

            Integer old = counts.get(document.getId());
            if (old == null) {
                counts.put(document.getId(), 1);
            } else {
                counts.put(document.getId(), old + 1);
            }
        }
    }

    /**
     * The documents that contain the word, as (document id -> count).
     * An unknown word gives an empty map. It is a copy, so changing it
     * does not change the index.
     */
    public Map<Integer, Integer> countsFor(String word) {
        Map<Integer, Integer> counts = index.get(word);
        if (counts == null) {
            return new HashMap<>();
        }
        return new HashMap<>(counts);
    }

    /**
     * The most frequent words in the whole index, most frequent first.
     * A word's total is the sum of its counts in every document.
     */
    public List<WordCount> topWords(int limit) {
        PriorityQueue<WordCount> queue = new PriorityQueue<WordCount>();
        for (Map.Entry<String, Map<Integer, Integer>> entry : index.entrySet()) {
            int total = 0;
            for (int count : entry.getValue().values()) {
                total += count;
            }
            queue.add(new WordCount(entry.getKey(), total, entry.getValue().size()));
        }

        List<WordCount> top = new ArrayList<WordCount>();
        while (!queue.isEmpty() && top.size() < limit) {
            top.add(queue.poll());
        }
        return top;
    }

    /** Finds a document by id, or null if there is none. */
    public Document getDocument(int id) {
        return documents.get(id);
    }

    public int getDocumentCount() {
        return documents.size();
    }

    /** How many different words the index knows. */
    public int getWordCount() {
        return index.size();
    }
}
