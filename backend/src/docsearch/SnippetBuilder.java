package docsearch;

import java.util.List;

/**
 * Cuts a short piece of text around the first search word it finds, so a
 * result can show where the word appears. If the piece does not start or
 * end at the edge of the text, "..." marks the cut.
 */
public class SnippetBuilder {

    private static final int BEFORE = 70; // characters to keep before the match
    private static final int AFTER = 130; // characters to keep after it

    /** The words in terms must already be lowercase, as the Tokenizer produces them. */
    public static String build(String text, List<String> terms) {
        String flat = flatten(text);
        int at = findFirstMatch(flat, terms);

        int start = Math.max(0, at - BEFORE);
        int end = Math.min(flat.length(), at + AFTER);
        String piece = flat.substring(start, end).trim();

        // The cut can land in the middle of a word, so drop that half word.
        if (start > 0) {
            int space = piece.indexOf(' ');
            if (space >= 0) {
                piece = piece.substring(space + 1);
            }
        }
        if (end < flat.length()) {
            int space = piece.lastIndexOf(' ');
            if (space >= 0) {
                piece = piece.substring(0, space);
            }
        }

        String before = start > 0 ? "... " : "";
        String after = end < flat.length() ? " ..." : "";
        return before + piece + after;
    }

    // Turns line breaks and runs of spaces into single spaces, so the snippet fits on one line.
    private static String flatten(String text) {
        StringBuilder out = new StringBuilder();
        boolean lastWasSpace = true; // true at the start, so leading spaces are skipped
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastWasSpace) {
                    out.append(' ');
                    lastWasSpace = true;
                }
            } else {
                out.append(c);
                lastWasSpace = false;
            }
        }
        return out.toString().trim();
    }

    // Walks through the words the same way the Tokenizer does and returns where the first search word starts.
    private static int findFirstMatch(String text, List<String> terms) {
        StringBuilder word = new StringBuilder();
        int wordStart = 0;

        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) {
                if (word.length() == 0) {
                    wordStart = i;
                }
                word.append(Character.toLowerCase(text.charAt(i)));
            } else if (word.length() > 0) {
                if (terms.contains(word.toString())) {
                    return wordStart;
                }
                word.setLength(0);
            }
        }
        return 0; // no match found: show the start of the text
    }
}
