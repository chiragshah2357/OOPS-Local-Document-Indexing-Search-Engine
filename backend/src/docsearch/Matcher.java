package docsearch;

import java.util.Set;

/**
 * Decides how the results for several search words are combined.
 *
 * Each search word has its own set of documents that contain it. For
 * "java thread" we have one set for "java" and one for "thread", and we
 * must decide which documents to keep. That decision is the only thing
 * that differs between the two modes, so each mode is a subclass that
 * fills in the abstract combine method (inheritance and polymorphism):
 *
 *     AllWordsMatcher  keeps documents that are in both sets
 *     AnyWordMatcher   keeps documents that are in either set
 */
public abstract class Matcher {

    /** Combines two sets of document ids into a new set. The two sets are not changed. */
    public abstract Set<Integer> combine(Set<Integer> first, Set<Integer> second);

    /** The mode name used by the user interface and the web API: "all" or "any". */
    public abstract String getName();

    /** Picks the matcher for a mode name. Anything other than "any" means "all". */
    public static Matcher forMode(String mode) {
        if (mode != null && mode.trim().equalsIgnoreCase("any")) {
            return new AnyWordMatcher();
        }
        return new AllWordsMatcher();
    }
}
