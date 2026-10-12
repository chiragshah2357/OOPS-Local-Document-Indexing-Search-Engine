package docsearch;

import java.util.HashSet;
import java.util.Set;

/** "Match any word": a document needs at least one search word, so we take the union of the sets. */
public class AnyWordMatcher extends Matcher {

    public Set<Integer> combine(Set<Integer> first, Set<Integer> second) {
        Set<Integer> result = new HashSet<Integer>(first); // a copy, so the original stays unchanged
        result.addAll(second);                             // add everything from the second set
        return result;
    }

    public String getName() {
        return "any";
    }
}
