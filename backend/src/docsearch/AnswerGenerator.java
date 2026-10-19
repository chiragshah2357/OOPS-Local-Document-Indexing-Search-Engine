package docsearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Ask your notes": answers a question from the indexed files.
 *
 * The search engine finds the files; the LLM only writes the answer from the
 * best few, and names them as sources. LLM use is kept small:
 *   - no call at all when no file matches, or when Gemini is not set up
 *   - at most MAX_FILES files, about 600 characters from each
 *   - each question is asked once; the answer is remembered in a HashMap
 */
public class AnswerGenerator {

    public static final int MAX_FILES = 3;
    private static final int BEFORE = 150; // characters sent before the first match in a file
    private static final int AFTER = 450;  // and after it
    private static final int MAX_TOKENS = 1024;

    private final SearchEngine engine;
    private final LlmProvider llm;
    private final Map<String, Answer> remembered = new HashMap<String, Answer>();
    private IndexStats rememberedFor; // answers belong to one index; indexing again forgets them

    public AnswerGenerator(SearchEngine engine, LlmProvider llm) {
        this.engine = engine;
        this.llm = llm;
    }

    /**
     * @throws IndexNotReadyException if no folder has been indexed yet
     * @throws LlmException if Gemini was asked and could not answer
     */
    public Answer answer(String question) throws IndexNotReadyException, LlmException {
        if (engine.getIndexStats() != rememberedFor) {
            remembered.clear();
            rememberedFor = engine.getIndexStats();
        }
        String key = question.trim().toLowerCase();
        Answer known = remembered.get(key);
        if (known != null) {
            return known;
        }

        SearchResponse found = engine.search(question, "any");
        List<SearchResult> top = new ArrayList<SearchResult>();
        List<Document> sources = new ArrayList<Document>();
        for (SearchResult result : found.getResults()) {
            if (top.size() == MAX_FILES) {
                break;
            }
            top.add(result);
            sources.add(result.getDocument());
        }

        Answer answer;
        if (top.isEmpty()) {
            answer = new Answer("None of the indexed files contain these words, so Gemini was not asked.",
                    sources, found.getTerms(), false);
        } else if (!llm.isAvailable()) {
            answer = new Answer("Gemini is not set up, so no answer was written. These files match the question best.",
                    sources, found.getTerms(), false);
        } else {
            String text = llm.complete(prompt(question, top, found.getTerms()), MAX_TOKENS);
            answer = new Answer(text, sources, found.getTerms(), true);
        }
        remembered.put(key, answer);
        return answer;
    }

    private String prompt(String question, List<SearchResult> top, List<String> terms) {
        StringBuilder notes = new StringBuilder();
        for (SearchResult result : top) {
            Document document = result.getDocument();
            notes.append('[').append(document.getTitle()).append("] ")
                    .append(SnippetBuilder.build(document.getText(), terms, BEFORE, AFTER))
                    .append("\n\n");
        }
        return "Answer the question using only the notes below. If the notes do not answer it, say that they do not cover it. "
                + "Use at most 80 words, and after each fact put the file it comes from in square brackets, like [notes.txt].\n\n"
                + "Question: " + question.trim() + "\n\nNotes:\n" + notes;
    }
}
