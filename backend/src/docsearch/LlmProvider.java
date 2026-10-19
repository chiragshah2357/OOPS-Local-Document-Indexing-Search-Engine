package docsearch;

/**
 * Something that can complete a prompt: the LLM part of the project.
 *
 * The rest of the program only talks to this abstract class, so it does not
 * know which model answers. There are two subclasses (polymorphism):
 *
 *     GeminiProvider   asks Google's Gemini over the internet
 *     OfflineProvider  used when no API key is set; it always refuses
 *
 * Every call is counted, so the app can show how little the LLM is used.
 */
public abstract class LlmProvider {

    private int calls = 0;

    /**
     * Sends the prompt and returns the model's answer as plain text.
     *
     * @param maxTokens the most the model may write, which keeps each call small
     */
    public abstract String complete(String prompt, int maxTokens) throws LlmException;

    /** False when complete() can only fail, for example because there is no API key. */
    public abstract boolean isAvailable();

    /** The model's name for the screen, for example "gemini-3.8-flash". */
    public abstract String getModel();

    /** How many requests have been sent to the model since the program started. */
    public int getCalls() {
        return calls;
    }

    /** Subclasses call this once for every request they send. */
    protected void recordCall() {
        calls++;
    }

    /**
     * Picks Gemini when the GEMINI_API_KEY environment variable is set, and the
     * offline provider otherwise. GEMINI_MODEL can name a different model.
     */
    public static LlmProvider fromEnvironment() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.trim().isEmpty()) {
            return new OfflineProvider();
        }
        String model = System.getenv("GEMINI_MODEL");
        if (model == null || model.trim().isEmpty()) {
            model = GeminiProvider.DEFAULT_MODEL;
        }
        return new GeminiProvider(key.trim(), model.trim());
    }
}
