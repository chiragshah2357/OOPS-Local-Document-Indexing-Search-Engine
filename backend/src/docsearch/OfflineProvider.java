package docsearch;

/**
 * Stands in for the LLM when no Gemini API key is set. It never sends
 * anything; the classes that use it see isAvailable() is false and fall
 * back to plain search, so the program still works without the internet.
 */
public class OfflineProvider extends LlmProvider {

    public String complete(String prompt, int maxTokens) throws LlmException {
        throw new LlmException("Gemini is not set up. Set GEMINI_API_KEY and start the program again.");
    }

    public boolean isAvailable() {
        return false;
    }

    public String getModel() {
        return "none";
    }
}
