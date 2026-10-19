package docsearch;

import java.util.ArrayList;
import java.util.List;

/** The reply to a question: the answer text, the files it came from, and whether the LLM wrote it. */
public class Answer {
    private final String text;
    private final List<Document> sources;
    private final List<String> terms; // the question's search words, so the files can be shown with them highlighted
    private final boolean fromLlm;

    public Answer(String text, List<Document> sources, List<String> terms, boolean fromLlm) {
        this.text = text;
        this.sources = new ArrayList<Document>(sources);
        this.terms = new ArrayList<String>(terms);
        this.fromLlm = fromLlm;
    }

    public String getText() {
        return text;
    }

    public List<Document> getSources() {
        return new ArrayList<Document>(sources);
    }

    public List<String> getTerms() {
        return new ArrayList<String>(terms);
    }

    /** False when no model was asked: no file matched, or Gemini is not set up. */
    public boolean isFromLlm() {
        return fromLlm;
    }
}
