package docsearch;

/** One remembered search: the words, the mode ("all" or "any") and when it happened. */
public class HistoryEntry {
    private final String query;
    private final String mode;
    private final long time; // milliseconds since 1 January 1970, as System.currentTimeMillis() gives

    public HistoryEntry(String query, String mode, long time) {
        this.query = query;
        this.mode = mode;
        this.time = time;
    }

    public String getQuery() {
        return query;
    }

    public String getMode() {
        return mode;
    }

    public long getTime() {
        return time;
    }
}
