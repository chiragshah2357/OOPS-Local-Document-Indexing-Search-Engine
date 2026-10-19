package docsearch;

/**
 * Raised when the language model cannot answer: no API key, no internet,
 * or an error from the service. The status is the HTTP status of the
 * failed request, or 0 when no request was made.
 */
public class LlmException extends Exception {

    private final int status;

    public LlmException(String message) {
        this(message, 0);
    }

    public LlmException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
