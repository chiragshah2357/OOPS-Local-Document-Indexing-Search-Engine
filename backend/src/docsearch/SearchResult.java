package docsearch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row of the search results: a document, how well it matches and why.
 *
 * It implements Comparable, which tells Java how to put results in order.
 * A result that should be shown earlier compares as "smaller", so a sorted
 * list or a PriorityQueue gives the best result first:
 * the higher score wins, and the file name decides when scores are equal.
 */
public class SearchResult implements Comparable<SearchResult> {

    private final Document document;
    private final int score;
    private final Map<String, Integer> matches; // search word -> times it appears in the document
    private final String snippet;

    public SearchResult(Document document, int score, Map<String, Integer> matches, String snippet) {
        this.document = document;
        this.score = score;
        this.matches = new LinkedHashMap<String, Integer>(matches);
        this.snippet = snippet;
    }

    public Document getDocument() {
        return document;
    }

    /** The total number of times the search words appear in the document. */
    public int getScore() {
        return score;
    }

    /** For each search word that appears in the document, how many times. */
    public Map<String, Integer> getMatches() {
        return new LinkedHashMap<String, Integer>(matches);
    }

    /** A short piece of the document around the first match. */
    public String getSnippet() {
        return snippet;
    }

    public int compareTo(SearchResult other) {
        if (score != other.score) {
            return other.score - score; // a bigger score goes first
        }
        return document.getTitle().compareTo(other.document.getTitle());
    }
}
