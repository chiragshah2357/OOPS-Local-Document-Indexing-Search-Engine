package docsearch;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * The starting point of the program. It can run in two ways:
 *
 *   run.bat    a console menu: index a folder, search it, look at the history
 *   serve.bat  a web server, so the browser frontend can use the engine
 *
 * Both use the same SearchEngine. The word "web" as the first argument
 * chooses the web server; anything else starts the console menu.
 */
public class Main {

    private static final String SAMPLE_FOLDER = "sample-docs";
    private static final int TOP_WORDS = 10;
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_FRONTEND = "../frontend";

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("web")) {
            runWebServer(args);
        } else {
            runConsole();
        }
    }

    // Usage: web [port] [frontend folder]
    private static void runWebServer(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("The port must be a number, for example 8080.");
                return;
            }
        }

        File webRoot = new File(args.length > 2 ? args[2] : DEFAULT_FRONTEND);
        if (!webRoot.isDirectory()) {
            System.out.println("Could not find the frontend folder: " + webRoot.getAbsolutePath());
            System.out.println("Start the server from the backend folder (serve.bat does this), or give the frontend folder as the third argument.");
            return;
        }

        WebServer server = new WebServer(new SearchEngine(), webRoot);
        try {
            server.start(port);
        } catch (IOException e) {
            System.out.println("Could not start the server on port " + port + ": " + e.getMessage());
            System.out.println("Another program may be using that port. Try a different one, for example: serve.bat 9000");
            return;
        }

        System.out.println("DocSearch is running at http://localhost:" + port);
        System.out.println("Open that address in your browser. Press Ctrl+C to stop.");
    }

    private static void runConsole() {
        SearchEngine engine = new SearchEngine();
        Scanner input = new Scanner(System.in);

        System.out.println("DocSearch: search the text files of a folder");

        while (true) {
            System.out.println();
            System.out.println("1. Index a folder");
            System.out.println("2. Search");
            System.out.println("3. Show the most frequent words");
            System.out.println("4. Show search history");
            System.out.println("5. Clear search history");
            System.out.println("6. Exit");

            String choice = ask(input, "Choose an option: ");
            if (choice == null || choice.equals("6")) {
                System.out.println("Goodbye.");
                return;
            }

            if (choice.equals("1")) {
                indexFolder(engine, input);
            } else if (choice.equals("2")) {
                search(engine, input);
            } else if (choice.equals("3")) {
                showTopWords(engine);
            } else if (choice.equals("4")) {
                showHistory(engine);
            } else if (choice.equals("5")) {
                engine.clearHistory();
                System.out.println("Search history cleared.");
            } else {
                System.out.println("Please type a number from 1 to 6.");
            }
        }
    }

    // Prints a prompt and reads one line. Returns null when the input has ended.
    private static String ask(Scanner input, String prompt) {
        System.out.print(prompt);
        if (!input.hasNextLine()) {
            return null;
        }
        return input.nextLine().trim();
    }

    private static void indexFolder(SearchEngine engine, Scanner input) {
        String path = ask(input, "Folder to index (press Enter for " + SAMPLE_FOLDER + "): ");
        if (path == null) {
            return;
        }
        if (path.isEmpty()) {
            path = SAMPLE_FOLDER;
        }

        try {
            IndexStats stats = engine.indexFolder(path);
            System.out.println("Indexed " + stats.getDocuments() + " documents and "
                    + stats.getWords() + " different words in " + stats.getTookMs() + " ms.");
            if (stats.getSkipped() > 0) {
                System.out.println(stats.getSkipped() + " file(s) could not be read and were skipped.");
            }
        } catch (IOException e) {
            System.out.println("Could not index that folder: " + e.getMessage());
        }
    }

    private static void search(SearchEngine engine, Scanner input) {
        if (!engine.isReady()) {
            System.out.println("Index a folder first (option 1).");
            return;
        }

        String words = ask(input, "Words to find: ");
        if (words == null || words.isEmpty()) {
            return;
        }
        String mode = ask(input, "Match all words (1) or any word (2)? [1]: ");
        if (mode == null) {
            return;
        }
        String modeName = mode.equals("2") ? "any" : "all";

        try {
            printResults(engine.search(words, modeName));
        } catch (IndexNotReadyException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void printResults(SearchResponse response) {
        System.out.println();
        if (response.getTerms().isEmpty()) {
            System.out.println("Those words are too common to search for. Try something more specific.");
            return;
        }

        System.out.println(response.getTotal() + " file(s) for \"" + response.getQuery() + "\" ("
                + modeLabel(response.getMode()) + ") in " + response.getTookMs() + " ms");
        if (response.getTotal() == 0) {
            System.out.println("No file matches. Try fewer words, or match any word.");
            return;
        }

        int rank = 1;
        for (SearchResult result : response.getResults()) {
            System.out.println();
            System.out.println(rank + ". " + result.getDocument().getTitle() + "   score " + result.getScore());
            System.out.println("   " + result.getDocument().getPath());
            System.out.println("   words: " + describeMatches(result.getMatches()));
            System.out.println("   " + result.getSnippet());
            rank++;
        }
    }

    // Keyword frequency: the words that appear most often across all indexed files.
    private static void showTopWords(SearchEngine engine) {
        List<WordCount> words;
        try {
            words = engine.getTopWords(TOP_WORDS);
        } catch (IndexNotReadyException e) {
            System.out.println("Index a folder first (option 1).");
            return;
        }

        System.out.println();
        System.out.println("The " + words.size() + " most frequent words:");
        int rank = 1;
        for (WordCount word : words) {
            System.out.println(rank + ". " + word.getWord() + "   " + word.getTotal() + " times in "
                    + word.getDocuments() + " file(s)");
            rank++;
        }
    }

    private static void showHistory(SearchEngine engine) {
        List<HistoryEntry> entries = engine.getHistory();
        if (entries.isEmpty()) {
            System.out.println("No searches yet.");
            return;
        }

        int number = 1;
        for (HistoryEntry entry : entries) {
            System.out.println(number + ". " + entry.getQuery() + "   (" + modeLabel(entry.getMode())
                    + ", " + new Date(entry.getTime()) + ")");
            number++;
        }
    }

    // Turns {java=1, thread=2} into "java x1, thread x2".
    private static String describeMatches(Map<String, Integer> matches) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> match : matches.entrySet()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(match.getKey()).append(" x").append(match.getValue());
        }
        return text.toString();
    }

    private static String modeLabel(String mode) {
        return mode.equals("any") ? "any word" : "all words";
    }
}
