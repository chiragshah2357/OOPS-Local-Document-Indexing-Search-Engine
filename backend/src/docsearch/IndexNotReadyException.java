package docsearch;

/**
 * Thrown when someone searches before any folder has been indexed.
 * It is a checked exception, so the compiler makes callers deal with it.
 */
public class IndexNotReadyException extends Exception {

    public IndexNotReadyException() {
        super("Nothing is indexed yet. Index a folder first.");
    }
}
