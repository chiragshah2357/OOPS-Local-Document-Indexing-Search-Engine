# DocIndex: Local Document Indexing and Search Engine

A CSC2104 Object Oriented Programming mini project (project 25) by **Chirag Shah**, Reg. No. 2503080249, Section T4.

Point it at a folder of text files. It reads them once, builds an index, and then answers searches by ranking every matching file by how often your words appear in it. The engine is plain Java. A website sits on top of it, and the website also runs on its own in the browser, so it can be hosted on Vercel.

![The DocIndex landing page with a live search of the sample files](docs/screenshots/landing.png)

## Contents

- [What it does](#what-it-does)
- [Screenshots](#screenshots)
- [Try it](#try-it)
- [How it works](#how-it-works)
- [Class design](#class-design)
- [Semantic search and LLM integration](#semantic-search-and-llm-integration)
- [Course topics](#course-topics)
- [What is in the repository](#what-is-in-the-repository)
- [Tests](#tests)
- [Put the website online](#put-the-website-online)

## What it does

The four features from the project brief, and where to see each one:

| Feature | What you see | Java that does it |
|---|---|---|
| **File indexing** | Index a folder (by path with the Java engine, or by picking or dropping it in the browser). The panel shows how many files and different words were indexed. | `DocumentLoader`, `Tokenizer`, `InvertedIndex` |
| **Keyword frequency** | Each result says how often each word appears in it (`java ×1 + thread ×2`), and the **Most frequent words** list ranks the words of the whole index. | `InvertedIndex.countsFor`, `InvertedIndex.topWords`, `WordCount` |
| **Ranked results** | Files sorted by score, best first, with a snippet and a bar per result. Click a file name to read it with every match highlighted. | `SearchEngine`, `SearchResult`, `PriorityQueue` |
| **Search history** | The last 10 searches, newest first. Click one to run it again. | `SearchHistory`, `HistoryEntry`, `LinkedList` |

Searches can ask for files that contain **all** the words or **any** of them.

The title's two extra parts use Google's Gemini, and only with the Java engine (see [Semantic search and LLM integration](#semantic-search-and-llm-integration)):

| Feature | What you see | Java that does it |
|---|---|---|
| **Semantic search** | Turn on **Smart search**: Gemini adds up to 5 related words (`concurrency` also finds `thread`, `deadlock`), and the same engine ranks the files. | `QueryExpander` |
| **LLM answers** | **Ask your notes**: the engine finds the 3 best files, and Gemini answers from those files only, naming them as sources. | `AnswerGenerator`, `Answer` |

## Screenshots

| Ranked results and keyword frequency | Document view |
|---|---|
| ![Search results for java thread, with the index panel and the most frequent words](docs/screenshots/app-results.png) | ![A document opened from the results, with every match highlighted](docs/screenshots/app-viewer.png) |

<p align="center"><img src="docs/screenshots/app-mobile.png" alt="The search app on a phone" width="300"></p>

## Try it

| I want to | Run | Then |
|---|---|---|
| Use the website with the Java engine | `backend\serve.bat` | Open http://localhost:8080 |
| Use the console menu | `backend\run.bat` | Follow the menu |
| Run the tests | `backend\test.bat` | Expect `108 passed, 0 failed` |
| Try the website with no Java | `python -m http.server 5173` in `frontend` | Open http://localhost:5173 |

The Java parts need a JDK (IntelliJ ships one: set `JAVA_HOME` to it). A plain Java runtime cannot compile.

In the app, press **Use the sample files** to index the six sample files, then search for `java thread`. Without the Java engine you can also index your own notes: press **Choose folder**, or drop a folder on the page. The files are read inside your browser and never uploaded.

## How it works

### The big picture

The website talks to one file, `api.js`, which decides who answers. When the Java backend is running, every request goes to it. Otherwise the same steps run in `engine.js`, a JavaScript copy of the Java classes, so the website still works on a static host.

```mermaid
flowchart LR
    subgraph web["Website (frontend/)"]
        landing["index.html<br/>landing page and live demo"]
        app["app.html + app.js<br/>search app"]
        api["api.js<br/>picks the engine"]
        files["files.js<br/>reads picked or dropped files"]
        js["engine.js<br/>browser engine"]
    end
    subgraph java["Java backend (backend/), optional"]
        server["WebServer<br/>127.0.0.1:8080"]
        engine["SearchEngine"]
    end
    disk[("Folder of .txt files")]

    landing --> js
    app --> api
    app --> files
    files --> js
    api -->|browser mode| js
    api -->|"Java mode: /api/..."| server
    server --> engine
    engine -->|DocumentLoader| disk
```

### 1. Indexing: from a folder to an index

Indexing happens once. Every file is read, split into words, and each word is recorded with the files it appears in and how many times.

```mermaid
flowchart LR
    folder[("Folder of .txt files")] --> loader["DocumentLoader<br/>visits sub folders by recursion,<br/>reads each file with a BufferedReader"]
    loader --> docs["Document objects<br/>id, title, path, text"]
    docs --> tok["Tokenizer<br/>lowercase, split on anything that is<br/>not a letter or digit, drop stop words"]
    tok --> index["InvertedIndex<br/>HashMap: word to file to count"]
```

`"A Thread is the smallest unit!"` becomes `[thread, smallest, unit]`: the words "a", "is" and "the" are in the stop word `HashSet`, so they are dropped.

### 2. The inverted index

The index works like the one at the back of a book: look a word up and it tells you where it appears. This is part of it for the six sample files.

```mermaid
flowchart LR
    java(["java"])
    thread(["thread"])
    exc["exception-handling.txt"]
    fio["file-io-basics.txt"]
    col["java-collections.txt"]
    thr["threads-and-synchronization.txt"]

    java -->|1| exc
    java -->|2| fio
    java -->|2| col
    java -->|1| thr
    thread -->|2| thr
```

In Java this is `HashMap<String, Map<Integer, Integer>>`: the word, then the document id, then the count. Finding every file that contains a word is a single lookup, however many files there are.

### 3. Searching: all words or any word

Each search word gives a set of files. **All words** keeps the files that are in every set (intersection), **any word** keeps the files that are in at least one (union). `AllWordsMatcher` and `AnyWordMatcher` are two subclasses of the abstract `Matcher`, and the engine does not need to know which one it has.

```mermaid
flowchart LR
    j["java<br/>exception-handling, file-io-basics,<br/>java-collections, threads-and-sync"]
    t["thread<br/>threads-and-sync"]
    all["All words: intersection<br/>threads-and-sync"]
    any["Any word: union<br/>all four files"]

    j --> all
    t --> all
    j --> any
    t --> any
```

### 4. Ranking: keyword frequency as the score

A file's score is the total number of times the search words appear in it. Results go into a `PriorityQueue`, and `SearchResult` implements `Comparable`, so the queue always hands back the best result first. Equal scores are ordered by file name.

| `java thread`, any word | java | thread | Score |
|---|---|---|---|
| threads-and-synchronization.txt | 1 | 2 | **3** |
| file-io-basics.txt | 2 | 0 | **2** |
| java-collections.txt | 2 | 0 | **2** |
| exception-handling.txt | 1 | 0 | **1** |

### 5. One search from start to finish

```mermaid
sequenceDiagram
    actor you as You
    participant app as app.js
    participant api as api.js
    participant se as SearchEngine
    participant ix as InvertedIndex
    participant m as Matcher
    participant pq as PriorityQueue

    you->>app: types "java thread", picks Any word
    app->>api: search("java thread", "any")
    api->>se: GET /api/search?q=java+thread&mode=any
    se->>se: Tokenizer gives [java, thread]
    se->>ix: countsFor("java"), countsFor("thread")
    ix-->>se: 4 files for java, 1 file for thread
    se->>m: combine the two sets (AnyWordMatcher: union)
    m-->>se: 4 files
    se->>pq: one SearchResult per file, score = sum of counts
    pq-->>se: best first: 3, 2, 2, 1
    se->>se: SearchHistory remembers the search
    se-->>api: SearchResponse as JSON
    api-->>app: ranked results
    app-->>you: results with highlighted snippets
    Note over api,se: Without the Java backend, the same steps run in engine.js inside the browser.
```

### 6. Which engine answers

```mermaid
flowchart TD
    start(["app.html opens"]) --> host{"Is the address<br/>localhost?"}
    host -->|"no, for example Vercel"| browser["Browser mode<br/>engine.js indexes the sample files<br/>or the files you pick"]
    host -->|yes| health["Ask GET /api/health"]
    health -->|"the Java backend answers"| javamode["Java mode<br/>every request goes to /api/..."]
    health -->|"404, a web page, or no answer in 2.5 s"| browser
```

## Class design

All Java classes are in the package `docsearch`. The diagrams show the important fields and methods.

### How the classes work together

`SearchEngine` is in the middle: it uses `DocumentLoader` to read a folder, owns the `InvertedIndex` and the `SearchHistory`, and is used by both the console menu (`Main`) and the `WebServer`.

```mermaid
classDiagram
    direction TB
    class Main {
        +main(String[] args)$
    }
    class WebServer {
        -SearchEngine engine
        -File webRoot
        +start(int port)
        +stop()
    }
    class SearchEngine {
        -Tokenizer tokenizer
        -SearchHistory history
        -InvertedIndex index
        +indexFolder(String path) IndexStats
        +search(String query, String mode) SearchResponse
        +getTopWords(int limit) List
        +getDocument(int id) Document
        +getHistory() List
    }
    class DocumentLoader {
        +loadFolder(String path) List
        +getSkipped() List
    }
    class InvertedIndex {
        -HashMap index
        +addDocument(Document document)
        +countsFor(String word) Map
        +topWords(int limit) List
    }
    class Tokenizer {
        -HashSet stopWords
        +tokenize(String text) List
    }
    class Document {
        -int id
        -String title
        -String path
        -String text
    }
    class SearchHistory {
        -LinkedList entries
        +add(String query, String mode)
        +getAll() List
    }
    class HistoryEntry {
        -String query
        -String mode
        -long time
    }

    Main --> SearchEngine
    Main --> WebServer
    WebServer --> SearchEngine
    SearchEngine ..> DocumentLoader : reads a folder with
    SearchEngine *-- InvertedIndex
    SearchEngine *-- SearchHistory
    DocumentLoader ..> Document : creates
    InvertedIndex --> Tokenizer
    InvertedIndex o-- Document
    SearchHistory o-- HistoryEntry
```

### Inheritance and interfaces

```mermaid
classDiagram
    direction TB
    class Matcher {
        <<abstract>>
        +combine(Set first, Set second)* Set
        +getName()* String
        +forMode(String mode)$ Matcher
    }
    class AllWordsMatcher {
        +combine(Set first, Set second) Set
    }
    class AnyWordMatcher {
        +combine(Set first, Set second) Set
    }
    class Comparable {
        <<interface>>
        +compareTo(T other) int
    }
    class SearchResult {
        -Document document
        -int score
        -Map matches
        -String snippet
        +compareTo(SearchResult other) int
    }
    class WordCount {
        -String word
        -int total
        -int documents
        +compareTo(WordCount other) int
    }
    class LlmProvider {
        <<abstract>>
        +complete(String prompt, int maxTokens)* String
        +isAvailable()* boolean
        +fromEnvironment()$ LlmProvider
    }
    class GeminiProvider {
        +complete(String prompt, int maxTokens) String
    }
    class OfflineProvider {
        +complete(String prompt, int maxTokens) String
    }
    class Exception
    class IndexNotReadyException
    class LlmException

    Matcher <|-- AllWordsMatcher : intersection
    Matcher <|-- AnyWordMatcher : union
    LlmProvider <|-- GeminiProvider : calls Gemini
    LlmProvider <|-- OfflineProvider : no key
    Comparable <|.. SearchResult : by score, then name
    Comparable <|.. WordCount : by total, then word
    Exception <|-- IndexNotReadyException
    Exception <|-- LlmException
```

The other classes are small helpers: `SnippetBuilder` cuts the text around the first match, `Json` turns answers into JSON for the website, and `IndexStats`, `SearchResponse` and `Answer` carry answers back to the caller. `QueryExpander` and `AnswerGenerator` use an `LlmProvider` without knowing which one it is. [backend/README.md](backend/README.md) has a table of every class.

## Semantic search and LLM integration

Gemini sits on top of the search engine and never replaces it: the engine still reads, indexes and ranks every file. Gemini is used as little as possible:

- **At most one call per new smart search or question.** Answers and suggested words are remembered in a `HashMap`, so asking again is free.
- **No call at all** when no file matches, when the words are all stop words, or when no key is set.
- **Small prompts:** a question sends only the 3 best files, about 600 characters from each, and asks for at most 80 words back.
- **Never during indexing**, and the app shows how many calls have been made.

```mermaid
sequenceDiagram
    actor you as You
    participant ag as AnswerGenerator
    participant se as SearchEngine
    participant llm as LlmProvider (Gemini)

    you->>ag: "What causes a deadlock?"
    ag->>se: search(question, "any")
    se-->>ag: ranked files
    alt no file matches
        ag-->>you: "None of the files contain these words" (Gemini not asked)
    else
        ag->>llm: question + the top 3 files (600 characters each)
        llm-->>ag: short answer naming [file.txt]
        ag-->>you: answer + source files (remembered for next time)
    end
```

To turn it on, set your Gemini API key before starting the Java engine. The key stays on your computer and is never written in the code:

```bat
set GEMINI_API_KEY=your-key-here
backend\serve.bat
```

The model is `gemini-3.8-flash`; set `GEMINI_MODEL` to use another. Without a key everything else still works: smart search becomes a normal search, and a question lists the best matching files. The Vercel site has no Java engine, so these two features are switched off there.

## Course topics

| Topic | Where it is used |
|---|---|
| Classes, objects and encapsulation | `Document`, `SearchResult`, `WordCount`, `IndexStats` and others: private fields, getters only |
| Inheritance, abstract classes, polymorphism | `Matcher` with `AllWordsMatcher` and `AnyWordMatcher`; `LlmProvider` with `GeminiProvider` and `OfflineProvider` |
| Method overloading | `SnippetBuilder.build` (default size, or a chosen size for the LLM), `Json.searchResponse` |
| Nested classes | `SelfTest.FakeProvider`, a stand-in for Gemini in the tests |
| Interfaces: `Comparable` | `SearchResult` (by score) and `WordCount` (by total) know their own order |
| `HashMap` | `InvertedIndex`: word to file to count |
| `HashSet` | Stop words in `Tokenizer`, and the file sets the matchers combine |
| `PriorityQueue` | Ranking results in `SearchEngine`, and the most frequent words in `InvertedIndex` |
| `LinkedList` | `SearchHistory` |
| File I/O and recursion | `DocumentLoader`: `File.listFiles`, `BufferedReader`, a method that calls itself for sub folders |
| Strings and `StringBuilder` | `Tokenizer`, `SnippetBuilder`, `Json` |
| Exception handling | `IndexNotReadyException` and `LlmException` (custom checked exceptions), and try/catch/finally around file reading |
| `Scanner` | The console menu in `Main` |

Two classes go outside the course, and both only pass messages along: `WebServer` uses the `HttpServer` that comes with Java so the browser can talk to the engine, and `GeminiProvider` uses `HttpURLConnection` to send a prompt to Gemini.

## What is in the repository

```
backend/            the Java engine, console menu and web server (see backend/README.md)
  src/docsearch/      the classes
  sample-docs/        six short text files to try it on
  build.bat run.bat serve.bat test.bat
frontend/           the website (see frontend/README.md)
  index.html          landing page with a live demo
  app.html            the search app
  js/engine.js        the browser engine, a JavaScript copy of the Java classes
  js/api.js           decides whether the browser or the Java backend answers
docs/screenshots/   the pictures in this README
```

## Tests

`backend\test.bat` builds the project and runs `SelfTest`, which prints `PASS` or `FAIL` for 108 checks: the tokenizer, the loader, the index and its top words, both matchers, ranking, snippets, history, the engine end to end, the JSON the web server sends, and the Gemini layer. The Gemini checks use a fake provider and a small fake Gemini server on your own computer, so they need no key and no internet. No test framework is needed.

The sample files give the same answers in both engines, which the tests and the frontend rely on: 6 documents, 169 different words, `java thread` with any word gives scores 3, 2, 2, 1, and the most frequent word is `java` (6 times in 4 files).

## Put the website online

The website is plain static files with no build step, so it can be hosted on Vercel:

1. On vercel.com choose **Add New, Project** and import this repository.
2. Set **Framework Preset** to `Other` and **Root Directory** to `frontend`.
3. Leave the build settings empty and deploy.

Vercel cannot run the Java server, so the hosted site runs in browser mode: visitors can index the sample files or their own folder, with nothing uploaded. More detail is in [frontend/README.md](frontend/README.md).

## License

MIT. See [LICENSE](LICENSE).
