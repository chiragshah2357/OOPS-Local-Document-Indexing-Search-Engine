# Local Document Indexing and Search Engine

A CSC2104 Object Oriented Programming mini project (project 25) by **Chirag Shah**, Reg. No. 2503080249, Section T4.

Point it at a folder of text files. It reads them once, builds an index, and then answers searches by ranking every matching file by how often your words appear in it. The engine is plain Java, and a website sits on top of it.

## Features

- **File indexing:** reads every `.txt` file in a folder and its sub folders.
- **Keyword frequency:** counts how many times each word appears in each file.
- **Ranked results:** files are sorted by total hits, with a highlighted snippet and a count for each word.
- **Match all words or any word:** all means a file must contain every word, any means at least one.
- **Search history:** remembers the last 10 searches and lets you run one again.
- **Two ways to use it:** a console menu and a website. The website also works without Java, on built-in sample files.

## Try it

You need a JDK for the Java parts (IntelliJ ships one: set `JAVA_HOME` to it). A plain Java runtime cannot compile.

| I want to | Run | Then |
|---|---|---|
| Use the website with the Java engine | `backend\serve.bat` | Open http://localhost:8080 |
| Use the console menu | `backend\run.bat` | Follow the menu |
| Run the tests | `backend\test.bat` | Expect `74 passed, 0 failed` |
| Try the website with no Java | `python -m http.server 5173` in `frontend` | Open http://localhost:5173 |

On the website, press **Use the sample-docs folder** to index the six sample files, then search for `java thread`.

## How it works

1. **Read:** `DocumentLoader` loads every text file.
2. **Split:** `Tokenizer` lowercases the text, breaks it into words and drops filler words such as "the" and "is".
3. **Index:** `InvertedIndex` records, for each word, which files contain it and how many times, like the index at the back of a book.
4. **Search:** each search word is looked up, and a `Matcher` combines the file sets (intersection for all, union for any).
5. **Rank:** a file's score is the total number of times the words appear in it, and a `PriorityQueue` hands the results back best first.

## What is in the repository

```
backend/    the Java engine, console menu and web server (see backend/README.md)
  src/docsearch/   the classes
  sample-docs/     six short text files to try it on
  build.bat run.bat serve.bat test.bat
frontend/   the website: landing page and search app (see frontend/README.md)
```

## Course topics

| Topic | Where it is used |
|---|---|
| Encapsulation | `Document`, `SearchResult`, `IndexStats` and other classes with private fields and getters |
| Inheritance, abstract classes, polymorphism | `Matcher` with `AllWordsMatcher` and `AnyWordMatcher` |
| `Comparable` | `SearchResult` knows how to order itself by score |
| `HashMap` | `InvertedIndex` (word to file to count) |
| `HashSet` | Stop words in `Tokenizer`, and the file sets the matchers combine |
| `PriorityQueue` | Ranking in `SearchEngine` |
| `LinkedList` | `SearchHistory` |
| File I/O and recursion | `DocumentLoader` |
| Strings | `Tokenizer`, `SnippetBuilder`, `Json` |
| Exception handling | `IndexNotReadyException`, and try/catch/finally around file reading |
| `Scanner` | The console menu in `Main` |

The one thing outside the course is `WebServer`, which uses the `HttpServer` that comes with Java so the browser can talk to the engine.

## Put the website online

The website is plain static files, so it can be hosted on Vercel. The hosted copy runs in demo mode on the built-in sample files, because Vercel does not run the Java server. Import the repository on Vercel and set the **Root Directory** to `frontend`. The full steps are in [frontend/README.md](frontend/README.md).

## License

MIT. See [LICENSE](LICENSE).
