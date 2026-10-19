package docsearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small web server that lets the browser frontend use the search engine.
 *
 * Think of it as a postman. The browser sends a request ("search for java
 * thread"), the server hands it to the SearchEngine, and posts the answer
 * back as JSON. It also hands out the frontend files themselves, so one
 * program serves the whole application.
 *
 * Like GeminiProvider, it needs something outside the course: the
 * HttpServer that comes with Java. All the searching is still done by the
 * classes you already know.
 *
 * The server answers one request at a time, so the engine needs no locking.
 */
public class WebServer {

    private static final int MAX_BODY_BYTES = 100000;
    private static final int DEFAULT_WORDS = 10;
    private static final int MAX_WORDS = 100;

    private final SearchEngine engine;
    private final LlmProvider llm;
    private final QueryExpander expander;
    private final AnswerGenerator answers;
    private final File webRoot; // the folder with index.html, app.html, css and js
    private HttpServer server;

    public WebServer(SearchEngine engine, LlmProvider llm, File webRoot) {
        this.engine = engine;
        this.llm = llm;
        this.expander = new QueryExpander(llm);
        this.answers = new AnswerGenerator(engine, llm);
        this.webRoot = webRoot;
    }

    public void start(int port) throws IOException {
        // 127.0.0.1 means only this computer can connect, which keeps your files private.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", new ApiHandler());
        server.createContext("/", new FileHandler());
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    /** The port the server is really listening on (useful when it was started with port 0). */
    public int getPort() {
        return server.getAddress().getPort();
    }

    // ------------------------------------------------------------------
    // Requests whose address starts with /api/ go to the search engine.
    // ------------------------------------------------------------------
    private class ApiHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {
            try {
                route(exchange);
            } catch (Exception e) {
                System.err.println("Request failed: " + e);
                sendJson(exchange, 500, Json.error("Something went wrong on the server.", "SERVER_ERROR"));
            } finally {
                exchange.close();
            }
        }

        private void route(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/api/health")) {
                // Also says what is indexed, so a reloaded page can pick up where it left off, and whether Gemini is set up.
                sendJson(exchange, 200, Json.health(engine.getIndexStats(), llm));
            } else if (path.equals("/api/index")) {
                if (method.equals("POST")) {
                    indexFolder(exchange);
                } else {
                    wrongMethod(exchange);
                }
            } else if (path.equals("/api/search")) {
                if (method.equals("GET")) {
                    search(exchange);
                } else {
                    wrongMethod(exchange);
                }
            } else if (path.equals("/api/ask")) {
                if (method.equals("GET")) {
                    ask(exchange);
                } else {
                    wrongMethod(exchange);
                }
            } else if (path.equals("/api/words")) {
                if (method.equals("GET")) {
                    topWords(exchange);
                } else {
                    wrongMethod(exchange);
                }
            } else if (path.equals("/api/document")) {
                if (method.equals("GET")) {
                    document(exchange);
                } else {
                    wrongMethod(exchange);
                }
            } else if (path.equals("/api/history")) {
                if (method.equals("GET")) {
                    sendJson(exchange, 200, Json.history(engine.getHistory()));
                } else if (method.equals("DELETE")) {
                    engine.clearHistory();
                    exchange.sendResponseHeaders(204, -1); // 204 means "done, nothing to send back"
                } else {
                    wrongMethod(exchange);
                }
            } else {
                sendJson(exchange, 404, Json.error("There is no such address.", "NOT_FOUND"));
            }
        }

        // POST /api/index with {"path": "C:\\notes"}
        private void indexFolder(HttpExchange exchange) throws IOException {
            String folder = Json.readString(readBody(exchange), "path");
            if (folder == null || folder.trim().isEmpty()) {
                sendJson(exchange, 400, Json.error("Enter the path of a folder to index.", "BAD_PATH"));
                return;
            }

            try {
                IndexStats stats = engine.indexFolder(folder.trim());
                sendJson(exchange, 200, Json.indexStats(stats));
            } catch (IOException e) {
                sendJson(exchange, 400, Json.error(e.getMessage(), "BAD_PATH"));
            }
        }

        // GET /api/search?q=java+thread&mode=any      (add &smart=1 for a smart search)
        private void search(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String words = params.get("q");
            if (words == null || words.trim().isEmpty()) {
                sendJson(exchange, 400, Json.error("Type some words to search for.", "EMPTY_QUERY"));
                return;
            }

            try {
                if ("1".equals(params.get("smart"))) {
                    smartSearch(exchange, words);
                } else {
                    SearchResponse response = engine.search(words, params.get("mode"));
                    sendJson(exchange, 200, Json.searchResponse(response));
                }
            } catch (IndexNotReadyException e) {
                sendJson(exchange, 409, Json.error(e.getMessage(), "NOT_INDEXED"));
            }
        }

        // Smart search: Gemini suggests related words, then the same engine searches for all of them (any word).
        private void smartSearch(HttpExchange exchange, String words) throws IOException, IndexNotReadyException {
            if (!engine.isReady()) {
                throw new IndexNotReadyException(); // no point asking Gemini before there is anything to search
            }
            List<String> added = new ArrayList<String>();
            String note = null;
            if (!llm.isAvailable()) {
                note = "Gemini is not set up, so this was a normal search.";
            } else {
                try {
                    added = expander.expand(words);
                } catch (LlmException e) {
                    note = e.getMessage() + " This was a normal search.";
                }
            }

            StringBuilder all = new StringBuilder(words);
            for (String word : added) {
                all.append(' ').append(word);
            }
            SearchResponse response = engine.search(all.toString(), "any");
            sendJson(exchange, 200, Json.searchResponse(response, added, note, llm.getCalls()));
        }

        // GET /api/ask?q=what+is+a+deadlock
        private void ask(HttpExchange exchange) throws IOException {
            String question = parseQuery(exchange.getRequestURI().getRawQuery()).get("q");
            if (question == null || question.trim().isEmpty()) {
                sendJson(exchange, 400, Json.error("Type a question.", "EMPTY_QUERY"));
                return;
            }

            try {
                Answer answer = answers.answer(question);
                sendJson(exchange, 200, Json.answer(answer, llm.getCalls()));
            } catch (IndexNotReadyException e) {
                sendJson(exchange, 409, Json.error(e.getMessage(), "NOT_INDEXED"));
            } catch (LlmException e) {
                sendJson(exchange, 502, Json.error(e.getMessage(), "LLM_ERROR"));
            }
        }

        // GET /api/words?limit=10
        private void topWords(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            int limit = DEFAULT_WORDS;
            try {
                if (params.get("limit") != null) {
                    limit = Integer.parseInt(params.get("limit"));
                }
            } catch (NumberFormatException e) {
                // not a number: keep the default
            }
            limit = Math.max(1, Math.min(MAX_WORDS, limit));

            try {
                sendJson(exchange, 200, Json.words(engine.getTopWords(limit)));
            } catch (IndexNotReadyException e) {
                sendJson(exchange, 409, Json.error(e.getMessage(), "NOT_INDEXED"));
            }
        }

        // GET /api/document?id=6
        private void document(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            int id;
            try {
                id = Integer.parseInt(params.get("id"));
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Json.error("The document id must be a number.", "BAD_ID"));
                return;
            }

            try {
                Document found = engine.getDocument(id);
                if (found == null) {
                    sendJson(exchange, 404, Json.error("That document is not in the index.", "NOT_FOUND"));
                } else {
                    sendJson(exchange, 200, Json.document(found));
                }
            } catch (IndexNotReadyException e) {
                sendJson(exchange, 409, Json.error(e.getMessage(), "NOT_INDEXED"));
            }
        }

        private void wrongMethod(HttpExchange exchange) throws IOException {
            sendJson(exchange, 405, Json.error("That address does not accept this kind of request.", "METHOD_NOT_ALLOWED"));
        }
    }

    // ------------------------------------------------------------------
    // Every other request is for a frontend file such as /app.html or /css/base.css.
    // ------------------------------------------------------------------
    private class FileHandler implements HttpHandler {

        public void handle(HttpExchange exchange) throws IOException {
            try {
                serve(exchange);
            } catch (Exception e) {
                System.err.println("Request failed: " + e);
                sendText(exchange, 500, "Something went wrong on the server.");
            } finally {
                exchange.close();
            }
        }

        private void serve(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("GET")) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            File root = webRoot.getCanonicalFile();
            File file = new File(root, path).getCanonicalFile();

            // The file must be inside the frontend folder. This blocks addresses such as /../secret.txt.
            boolean inside = file.getPath().startsWith(root.getPath() + File.separator);
            if (!inside || !file.isFile()) {
                sendText(exchange, 404, "Not found");
                return;
            }

            byte[] bytes = readFile(file);
            exchange.getResponseHeaders().set("Content-Type", contentTypeOf(file.getName()));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream out = exchange.getResponseBody();
            out.write(bytes);
            out.close();
        }
    }

    // ------------------------------------------------------------------
    // Small helpers shared by both handlers.
    // ------------------------------------------------------------------

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json);
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        send(exchange, status, "text/plain; charset=utf-8", text);
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, count);
            if (bytes.size() > MAX_BODY_BYTES) {
                throw new IOException("The request is too large.");
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private byte[] readFile(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
        } finally {
            in.close();
        }
        return bytes.toByteArray();
    }

    // Turns "q=java+thread&mode=any" into a map: q -> "java thread", mode -> "any".
    private Map<String, String> parseQuery(String rawQuery) throws IOException {
        Map<String, String> params = new HashMap<String, String>();
        if (rawQuery == null) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            params.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
        }
        return params;
    }

    private String contentTypeOf(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else if (name.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        } else if (name.endsWith(".json")) {
            return "application/json; charset=utf-8";
        } else if (name.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (name.endsWith(".png")) {
            return "image/png";
        } else if (name.endsWith(".ico")) {
            return "image/x-icon";
        } else if (name.endsWith(".txt") || name.endsWith(".md")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
