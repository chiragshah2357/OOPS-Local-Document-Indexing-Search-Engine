# Backend (Java)

The search engine itself, written in plain Java with no libraries and no build tool. It reads a folder of text files, builds an index, and answers searches with ranked results.

## Build, test and run

You need a JDK (a JRE alone cannot compile). If you use IntelliJ, it ships one. Point `JAVA_HOME` at a JDK, or put `javac` on your `PATH`, then:

```bat
cd backend
test.bat      rem build, then run the self test
run.bat       rem build, then start the console menu
```

`build.bat` only compiles into `out/`. The classes are built for Java 8, so they run on older and newer Java alike.

To use an IDE instead, open the `backend` folder in IntelliJ and mark `src` as the Sources Root. Run `docsearch.Main` for the menu and `docsearch.SelfTest` for the tests, with `backend` as the working directory so `sample-docs` is found.

## The console menu

```
1. Index a folder
2. Search
3. Show search history
4. Clear search history
5. Exit
```

Choose 1 and press Enter to index the `sample-docs` folder, then choose 2 and type some words. You can ask for files that contain **all** the words or **any** of them.

## How a search works

1. **Index (once):** `DocumentLoader` reads every `.txt` file. `Tokenizer` splits each file into lowercase words and drops filler words. `InvertedIndex` records, for every word, which files contain it and how many times.
2. **Look up:** for each word you typed, the index gives the set of files that contain it.
3. **Combine:** a `Matcher` merges those sets. `AllWordsMatcher` keeps files that are in every set (intersection). `AnyWordMatcher` keeps files that are in at least one (union).
4. **Score:** a file's score is the total number of times your words appear in it.
5. **Rank:** results go into a `PriorityQueue` that always hands back the best one first. Equal scores are ordered by file name.
6. **Remember:** the search is added to the history, which keeps the last 10.

## The classes

All classes are in the package `docsearch`.

| Class | What it does | Course topic |
|---|---|---|
| `Document` | One text file: id, name, path and text | Encapsulation (private fields, getters) |
| `Tokenizer` | Lowercases text, splits it into words, drops filler words like "the" | Strings, `HashSet` |
| `DocumentLoader` | Reads every `.txt` file in a folder and its sub folders | File I/O, recursion, try/finally |
| `InvertedIndex` | Maps each word to the documents containing it and how many times | `HashMap` |
| `Matcher`, `AllWordsMatcher`, `AnyWordMatcher` | Combine the document sets of the search words | Abstract class, inheritance, polymorphism, `HashSet` |
| `SearchResult` | One result row. It can compare itself with another by score | `Comparable` |
| `SnippetBuilder` | Cuts a short piece of text around the first match | Strings |
| `SearchEngine` | Indexes a folder, runs a search and ranks the results | `PriorityQueue`, Collections |
| `SearchHistory`, `HistoryEntry` | Remember the last 10 searches, newest first | `LinkedList` |
| `IndexNotReadyException` | Raised if you search before indexing | Custom exception |
| `IndexStats`, `SearchResponse` | Small classes that carry answers back to the caller | Encapsulation |
| `Main` | The console menu | `Scanner` |
| `SelfTest` | Prints PASS or FAIL for each behaviour | Plain `main` method |

## Sample documents

`sample-docs/` holds six short files. They are the same texts the frontend demo uses, so the real engine and the demo give the same answers. For example, searching `java thread` for **any** word returns four files with scores 3, 2, 2 and 1. The self test checks this.

## Still to come

A small web server so the browser frontend can talk to this engine.
