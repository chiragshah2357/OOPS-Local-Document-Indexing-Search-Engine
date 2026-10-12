package docsearch;

/** A summary of one indexing run: what was indexed and how long it took. */
public class IndexStats {
    private final String path;
    private final int documents;
    private final int words;
    private final long tookMs;
    private final int skipped;

    public IndexStats(String path, int documents, int words, long tookMs, int skipped) {
        this.path = path;
        this.documents = documents;
        this.words = words;
        this.tookMs = tookMs;
        this.skipped = skipped;
    }

    /** The folder that was indexed, as the user typed it. */
    public String getPath() {
        return path;
    }

    public int getDocuments() {
        return documents;
    }

    /** How many different words the index knows. */
    public int getWords() {
        return words;
    }

    public long getTookMs() {
        return tookMs;
    }

    /** How many files could not be read. */
    public int getSkipped() {
        return skipped;
    }
}
