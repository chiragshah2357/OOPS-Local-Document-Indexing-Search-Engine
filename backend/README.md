# Backend (Java)

The search engine itself, written in plain Java with no libraries and no build tool. This part reads a folder of text files and builds the index that searches will use.

## Build and test

You need a JDK (a JRE alone cannot compile). If you use IntelliJ, it ships one. Point `JAVA_HOME` at a JDK, or put `javac` on your `PATH`, then:

```bat
cd backend
test.bat
```

`test.bat` compiles everything into `out/` and runs the self test. `build.bat` only compiles. The classes are built for Java 8, so they run on older and newer Java alike.

To use an IDE instead, open the `backend` folder in IntelliJ and mark `src` as the Sources Root.

## What is here so far

All classes are in the package `docsearch`.

| Class | What it does | Course topic |
|---|---|---|
| `Document` | One text file: id, name, path and text | Encapsulation (private fields, getters) |
| `Tokenizer` | Lowercases text, splits it into words, drops filler words like "the" | Strings, `HashSet` |
| `DocumentLoader` | Reads every `.txt` file in a folder and its sub folders | File I/O, recursion, try/finally |
| `InvertedIndex` | Maps each word to the documents containing it and how many times | `HashMap` |
| `SelfTest` | Prints PASS or FAIL for each behaviour | Plain `main` method |

## How the index works

Think of the index at the back of a book. Instead of reading every file for every search, the engine reads each file once and writes down where each word appears:

```
"thread"  ->  threads-and-synchronization.txt : 2 times
"java"    ->  file-io-basics.txt : 2, java-collections.txt : 2, threads-and-synchronization.txt : 1, ...
```

A search is then a lookup in that table, not a scan of every file.

## Sample documents

`sample-docs/` holds six short files. They are the same texts the frontend demo uses, so the real engine and the demo can be compared. The self test checks that both give 6 documents and 169 unique words.

## Still to come

Searching (match all words or any word), ranking by keyword frequency, search history, a console menu, and the connection to the web frontend.
