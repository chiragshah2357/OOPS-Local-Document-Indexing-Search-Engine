package docsearch;

import java.util.List;
import java.util.Map;

/**
 * Turns the engine's answers into JSON text for the web frontend, and reads
 * one text value out of a JSON request.
 *
 * JSON is just text that looks like {"name": "value", "count": 3}, so we
 * build it by hand with a StringBuilder. The field names match the API
 * described in frontend/README.md.
 */
public class Json {

    /** Puts text in quotes and escapes the characters that are not allowed inside a JSON string. */
    public static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                out.append("\\\"");
            } else if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c)); // other control characters
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }

    /** {"error":"message","code":"CODE"} */
    public static String error(String message, String code) {
        return "{\"error\":" + quote(message) + ",\"code\":" + quote(code) + "}";
    }

    public static String indexStats(IndexStats stats) {
        return "{\"path\":" + quote(stats.getPath())
                + ",\"documents\":" + stats.getDocuments()
                + ",\"terms\":" + stats.getWords()
                + ",\"tookMs\":" + stats.getTookMs()
                + ",\"skipped\":" + stats.getSkipped() + "}";
    }

    /**
     * {"ok":true,"index":null,"llm":{...}} before indexing, or with the current index stats.
     * "llm" says whether Gemini is set up, which model, and how many calls it has had.
     */
    public static String health(IndexStats stats, LlmProvider llm) {
        return "{\"ok\":true,\"index\":" + (stats == null ? "null" : indexStats(stats))
                + ",\"llm\":{\"available\":" + llm.isAvailable()
                + ",\"model\":" + quote(llm.getModel())
                + ",\"calls\":" + llm.getCalls() + "}}";
    }

    /** ["java","thread"] */
    public static String stringList(List<String> items) {
        StringBuilder out = new StringBuilder("[");
        for (String item : items) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(quote(item));
        }
        return out.append(']').toString();
    }

    /**
     * The body of a Gemini generateContent request. maxOutputTokens caps how
     * much the model may write; lowThinking asks it to think briefly.
     */
    public static String geminiRequest(String prompt, int maxTokens, boolean lowThinking) {
        return "{\"contents\":[{\"parts\":[{\"text\":" + quote(prompt) + "}]}],"
                + "\"generationConfig\":{\"maxOutputTokens\":" + maxTokens
                + (lowThinking ? ",\"thinkingConfig\":{\"thinkingLevel\":\"LOW\"}" : "") + "}}";
    }

    /** {"answer":"...","fromLlm":true,"terms":[...],"sources":[{"id":6,"title":"..."}],"llmCalls":2} */
    public static String answer(Answer answer, int llmCalls) {
        StringBuilder sources = new StringBuilder("[");
        for (Document document : answer.getSources()) {
            if (sources.length() > 1) {
                sources.append(',');
            }
            sources.append("{\"id\":").append(document.getId())
                    .append(",\"title\":").append(quote(document.getTitle())).append('}');
        }
        sources.append(']');
        return "{\"answer\":" + quote(answer.getText())
                + ",\"fromLlm\":" + answer.isFromLlm()
                + ",\"terms\":" + stringList(answer.getTerms())
                + ",\"sources\":" + sources
                + ",\"llmCalls\":" + llmCalls + "}";
    }

    /**
     * A smart search: the usual search response plus the words the LLM added,
     * a note when the LLM could not help, and the number of LLM calls so far.
     */
    public static String searchResponse(SearchResponse response, List<String> added, String llmNote, int llmCalls) {
        String plain = searchResponse(response);
        return plain.substring(0, plain.length() - 1) // drop the closing brace, then add the extra fields
                + ",\"added\":" + stringList(added)
                + (llmNote == null ? "" : ",\"llmNote\":" + quote(llmNote))
                + ",\"llmCalls\":" + llmCalls + "}";
    }

    /** [{"word":"java","total":6,"documents":4}, ...] */
    public static String words(List<WordCount> words) {
        StringBuilder out = new StringBuilder("[");
        for (WordCount word : words) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append("{\"word\":").append(quote(word.getWord()))
                    .append(",\"total\":").append(word.getTotal())
                    .append(",\"documents\":").append(word.getDocuments()).append('}');
        }
        return out.append(']').toString();
    }

    /** One whole document: {"id":6,"title":"...","path":"...","text":"..."} */
    public static String document(Document document) {
        return "{\"id\":" + document.getId()
                + ",\"title\":" + quote(document.getTitle())
                + ",\"path\":" + quote(document.getPath().replace('\\', '/'))
                + ",\"text\":" + quote(document.getText()) + "}";
    }

    public static String history(List<HistoryEntry> entries) {
        StringBuilder out = new StringBuilder("[");
        for (HistoryEntry entry : entries) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append("{\"query\":").append(quote(entry.getQuery()))
                    .append(",\"mode\":").append(quote(entry.getMode()))
                    .append(",\"at\":").append(entry.getTime()).append('}');
        }
        return out.append(']').toString();
    }

    public static String searchResponse(SearchResponse response) {
        StringBuilder out = new StringBuilder("{");
        out.append("\"query\":").append(quote(response.getQuery()));
        out.append(",\"mode\":").append(quote(response.getMode()));

        out.append(",\"terms\":[");
        boolean firstTerm = true;
        for (String term : response.getTerms()) {
            if (!firstTerm) {
                out.append(',');
            }
            firstTerm = false;
            out.append(quote(term));
        }
        out.append(']');

        out.append(",\"total\":").append(response.getTotal());
        out.append(",\"tookMs\":").append(response.getTookMs());

        out.append(",\"results\":[");
        boolean firstResult = true;
        for (SearchResult result : response.getResults()) {
            if (!firstResult) {
                out.append(',');
            }
            firstResult = false;
            appendResult(out, result);
        }
        return out.append("]}").toString();
    }

    private static void appendResult(StringBuilder out, SearchResult result) {
        Document document = result.getDocument();
        out.append("{\"id\":").append(document.getId());
        out.append(",\"title\":").append(quote(document.getTitle()));
        out.append(",\"path\":").append(quote(document.getPath().replace('\\', '/'))); // forward slashes read better on screen
        out.append(",\"score\":").append(result.getScore());

        out.append(",\"matches\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> match : result.getMatches().entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(quote(match.getKey())).append(':').append(match.getValue());
        }
        out.append('}');

        out.append(",\"snippet\":").append(quote(result.getSnippet())).append('}');
    }

    /**
     * Reads the text value of one field, for example "path" from {"path":"C:\\notes"}.
     * Returns null if the field is missing or its value is not text. It is
     * deliberately simple: it only has to understand the small requests our own frontend sends.
     */
    public static String readString(String json, String key) {
        String marker = "\"" + key + "\"";
        int at = json.indexOf(marker);
        if (at < 0) {
            return null;
        }
        int colon = json.indexOf(':', at + marker.length());
        if (colon < 0) {
            return null;
        }

        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null; // the value is not text
        }
        i++; // skip the opening quote

        StringBuilder value = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                return value.toString(); // the closing quote
            }
            if (c == '\\' && i + 1 < json.length()) {
                i++;
                char next = json.charAt(i);
                if (next == 'n') {
                    value.append('\n');
                } else if (next == 't') {
                    value.append('\t');
                } else if (next == 'r') {
                    value.append('\r');
                } else if (next == 'u' && i + 4 < json.length()) {
                    try {
                        value.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                        i += 4;
                    } catch (NumberFormatException e) {
                        value.append(next);
                    }
                } else {
                    value.append(next); // \\ and \" and \/ stand for the character itself
                }
            } else {
                value.append(c);
            }
            i++;
        }
        return null; // the closing quote is missing
    }
}
