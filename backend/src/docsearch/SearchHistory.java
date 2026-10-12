package docsearch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Remembers the last few searches, newest first.
 *
 * A LinkedList is a good fit: new searches are added at the front, and
 * when the list is full the oldest one is removed from the back.
 */
public class SearchHistory {

    public static final int LIMIT = 10;

    private final LinkedList<HistoryEntry> entries = new LinkedList<HistoryEntry>();

    /** Records a search. Repeating the same words in the same mode moves it to the front instead of adding a copy. */
    public void add(String query, String mode) {
        Iterator<HistoryEntry> it = entries.iterator();
        while (it.hasNext()) {
            HistoryEntry entry = it.next();
            if (entry.getQuery().equals(query) && entry.getMode().equals(mode)) {
                it.remove();
            }
        }

        entries.addFirst(new HistoryEntry(query, mode, System.currentTimeMillis()));

        while (entries.size() > LIMIT) {
            entries.removeLast();
        }
    }

    /** The remembered searches, newest first. It is a copy, so changing it does not change the history. */
    public List<HistoryEntry> getAll() {
        return new ArrayList<HistoryEntry>(entries);
    }

    public void clear() {
        entries.clear();
    }
}
