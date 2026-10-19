package docsearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic search, kept simple: the LLM suggests a few words that mean the
 * same as the search words, so a search for "concurrency" also finds notes
 * that only say "thread" or "synchronization". The search engine still does
 * all the searching and ranking.
 *
 * To keep LLM use small, each different query is sent at most once: the
 * suggestions are remembered in a HashMap.
 */
public class QueryExpander {

    public static final int MAX_WORDS = 5;
    private static final int MAX_TOKENS = 256;

    private final LlmProvider llm;
    private final Tokenizer tokenizer = new Tokenizer();
    private final Map<String, List<String>> remembered = new HashMap<String, List<String>>();

    public QueryExpander(LlmProvider llm) {
        this.llm = llm;
    }

    /**
     * Up to MAX_WORDS related words, lowercase, without stop words and
     * without the words already in the query.
     */
    public List<String> expand(String query) throws LlmException {
        List<String> own = tokenizer.tokenize(query);
        if (own.isEmpty()) {
            return new ArrayList<String>(); // nothing worth asking about
        }

        String key = own.toString();
        List<String> known = remembered.get(key);
        if (known == null) {
            known = pick(llm.complete(prompt(own), MAX_TOKENS), own);
            remembered.put(key, known);
        }
        return new ArrayList<String>(known);
    }

    // The reply is split by the same Tokenizer as the documents, so the new words can be looked up in the index.
    private List<String> pick(String reply, List<String> own) {
        List<String> words = new ArrayList<String>();
        for (String word : tokenizer.tokenize(reply)) {
            if (words.size() == MAX_WORDS) {
                break;
            }
            if (!own.contains(word) && !words.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private String prompt(List<String> words) {
        StringBuilder text = new StringBuilder();
        for (String word : words) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(word);
        }
        return "List up to " + MAX_WORDS + " single English words that mean the same as, or are closely related to: "
                + text + ". They will be used to search a folder of study notes. "
                + "Reply with only the words, separated by commas.";
    }
}
