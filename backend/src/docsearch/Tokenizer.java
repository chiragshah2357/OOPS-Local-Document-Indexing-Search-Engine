package docsearch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns text into a list of words.
 *
 * "A Thread is the smallest unit!" becomes [thread, smallest, unit]:
 * everything is lowercased, anything that is not a letter or a digit
 * separates words, and common filler words (the stop words) are dropped.
 */
public class Tokenizer {

    // A HashSet answers "is this word in the list?" very quickly.
    private final Set<String> stopWords = new HashSet<String>();

    public Tokenizer() {
        String[] common = {
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "if", "in",
            "into", "is", "it", "of", "on", "or", "so", "that", "the", "then", "to", "was", "with"
        };
        for (String word : common) {
            stopWords.add(word);
        }
    }

    public boolean isStopWord(String word) {
        return stopWords.contains(word);
    }

    /**
     * Splits the text into words. A word that appears three times in the
     * text appears three times in the result, because the index needs to
     * count it.
     */
    public List<String> tokenize(String text) {
        List<String> words = new ArrayList<String>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(Character.toLowerCase(c));
            } else {
                addWord(words, current);
            }
        }
        addWord(words, current); // the text may end in the middle of a word

        return words;
    }

    // Moves the word collected so far into the list, unless it is a stop word.
    private void addWord(List<String> words, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        String word = current.toString();
        if (!isStopWord(word)) {
            words.add(word);
        }
        current.setLength(0);
    }
}
