package docsearch;

import java.util.ArrayList;
import java.util.List;

/** Everything one search produces: the words used, the ranked results and how long it took. */
public class SearchResponse {
    private final String query;
    private final String mode;
    private final List<String> terms;
    private final List<SearchResult> results;
    private final long tookMs;

    public SearchResponse(String query, String mode, List<String> terms, List<SearchResult> results, long tookMs) {
        this.query = query;
        this.mode = mode;
        this.terms = new ArrayList<String>(terms);
        this.results = new ArrayList<SearchResult>(results);
        this.tookMs = tookMs;
    }

    /** What the user typed. */
    public String getQuery() {
        return query;
    }

    /** "all" or "any". */
    public String getMode() {
        return mode;
    }

    /** The words actually searched for: lowercase, without stop words and without repeats. */
    public List<String> getTerms() {
        return new ArrayList<String>(terms);
    }

    /** The results, best first. */
    public List<SearchResult> getResults() {
        return new ArrayList<SearchResult>(results);
    }

    public int getTotal() {
        return results.size();
    }

    public long getTookMs() {
        return tookMs;
    }
}
