package docsearch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Asks Google's Gemini model over HTTPS (the generateContent API).
 *
 * Like WebServer, it uses a class that comes with Java (HttpURLConnection)
 * and is outside the course topics; it only sends a prompt and reads back
 * the text. The API key is read from the environment by LlmProvider, never
 * written in the code.
 */
public class GeminiProvider extends LlmProvider {

    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final String GOOGLE_API = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int TIMEOUT_MS = 30000;

    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiProvider(String apiKey, String model) {
        this(apiKey, model, GOOGLE_API);
    }

    /** The self test passes the address of a small fake server here, so it can test without the internet. */
    GeminiProvider(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    public boolean isAvailable() {
        return true;
    }

    public String getModel() {
        return model;
    }

    public String complete(String prompt, int maxTokens) throws LlmException {
        try {
            // Low "thinking" keeps each call quick and small.
            return readText(post(Json.geminiRequest(prompt, maxTokens, true)));
        } catch (LlmException e) {
            // A model that does not accept the thinking setting answers 400; ask once more without it.
            if (e.getStatus() != 400 || e.getMessage().contains("API key")) {
                throw e;
            }
            return readText(post(Json.geminiRequest(prompt, maxTokens, false)));
        }
    }

    // Sends one request and returns the reply body. Any status other than 200 becomes an LlmException.
    private String post(String body) throws LlmException {
        recordCall();
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(baseUrl + model + ":generateContent").toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("x-goog-api-key", apiKey);

            OutputStream out = connection.getOutputStream();
            try {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }

            int status = connection.getResponseCode();
            InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            String reply = in == null ? "" : readAll(in);
            if (status != 200) {
                throw new LlmException(describe(status, Json.readString(reply, "message")), status);
            }
            return reply;
        } catch (IOException e) {
            throw new LlmException("Could not reach Gemini. Check the internet connection. (" + e.getMessage() + ")");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Takes the answer out of a generateContent reply: candidates[0].content.parts[0].text. */
    static String readText(String reply) throws LlmException {
        String text = Json.readString(reply, "text");
        if (text == null || text.trim().isEmpty()) {
            throw new LlmException("Gemini sent back an empty answer.");
        }
        return text.trim();
    }

    // Turns an error status into a sentence a person can act on.
    private String describe(int status, String message) {
        if (message != null && message.contains("API key")) {
            return "Gemini did not accept the API key. Check GEMINI_API_KEY.";
        }
        if (status == 404) {
            return "Gemini has no model called " + model + ". Set GEMINI_MODEL to another model.";
        }
        if (status == 429) {
            return "Gemini's usage limit was reached. Wait a minute and try again.";
        }
        return "Gemini answered with an error (" + status + ")" + (message == null ? "." : ": " + message);
    }

    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
        } finally {
            in.close();
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
