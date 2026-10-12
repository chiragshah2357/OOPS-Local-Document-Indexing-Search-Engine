package docsearch;

import java.util.HashSet;
import java.util.Set;

/** "Match all words": a document must contain every search word, so we take the intersection of the sets. */
public class AllWordsMatcher extends Matcher {

    public Set<Integer> combine(Set<Integer> first, Set<Integer> second) {
        Set<Integer> result = new HashSet<Integer>(first); // a copy, so the original stays unchanged
        result.retainAll(second);                          // keep only what is also in the second set
        return result;
    }

    public String getName() {
        return "all";
    }
}
