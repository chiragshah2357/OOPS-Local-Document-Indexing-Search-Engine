package docsearch;

/**
 * How often one word appears across the whole index: the keyword frequency
 * feature. For the sample files, "java" appears 6 times in 4 documents.
 *
 * Like SearchResult, it implements Comparable so a PriorityQueue can hand
 * back the most frequent word first. Equal totals are ordered alphabetically.
 */
public class WordCount implements Comparable<WordCount> {

    private final String word;
    private final int total;     // times the word appears in all documents together
    private final int documents; // how many documents contain it

    public WordCount(String word, int total, int documents) {
        this.word = word;
        this.total = total;
        this.documents = documents;
    }

    public String getWord() {
        return word;
    }

    public int getTotal() {
        return total;
    }

    public int getDocuments() {
        return documents;
    }

    public int compareTo(WordCount other) {
        if (total != other.total) {
            return other.total - total; // a bigger total goes first
        }
        return word.compareTo(other.word);
    }
}
