/*
 * API layer: the only file that knows where answers come from.
 *
 * The app calls window.Api.* and never touches fetch() or the engine
 * directly. Api works out by itself which of two engines answers:
 *
 *   - Java mode: the page was served by the Java backend (backend\serve.bat),
 *     which answers GET /api/health. Requests go to the /api endpoints listed
 *     in frontend/README.md, and folders are indexed by their path on disk.
 *   - Browser mode: anything else (Vercel, a plain file server). The browser
 *     engine in engine.js indexes the sample files, or files the visitor
 *     picks, inside the tab. Nothing is uploaded.
 *
 * Every function returns a Promise. Failures reject with an Error that may
 * carry a `code`, for example "NOT_INDEXED". Wait for Api.ready before
 * reading Api.mode.
 */
(function () {
  "use strict";

  const API_BASE = "/api";
  const FORCE_MODE = null; // "browser" or "java" skips the check; leave null to detect it

  const HISTORY_KEY = "lds.history";
  const HISTORY_LIMIT = 10;
  const SAMPLE_FOLDER = "sample-docs"; // the folder next to the Java backend
  const SAMPLE_LABEL = "the sample files";

  function fail(message, code) {
    const err = new Error(message);
    err.code = code;
    return err;
  }

  /* ---- Browser mode --------------------------------------------------------- */
  const local = Engine.create();
  const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  function loadHistory() {
    try { return JSON.parse(localStorage.getItem(HISTORY_KEY)) || []; } catch (e) { return []; }
  }

  function saveHistory(list) {
    try { localStorage.setItem(HISTORY_KEY, JSON.stringify(list)); } catch (e) { /* storage unavailable */ }
  }

  // Same rules as the Java SearchHistory: newest first, a repeat moves to the front, at most 10.
  function remember(query, mode) {
    const list = loadHistory().filter((h) => !(h.query === query && h.mode === mode));
    list.unshift({ query, mode, at: Date.now() });
    saveHistory(list.slice(0, HISTORY_LIMIT));
  }

  const browser = {
    async status() { return local.status(); },

    async indexSamples() {
      await delay(350); // long enough to see the indexing state, short enough not to annoy
      return local.build(window.SAMPLE_DOCS, SAMPLE_LABEL, 0);
    },

    // `read` comes from Files.read: { docs, label, skipped }.
    async indexFiles(read) {
      if (!read.docs.length) {
        throw fail(read.skipped ? "None of the .txt files could be read." : "No .txt files were found there.", "NO_FILES");
      }
      return local.build(read.docs, read.label, read.skipped);
    },

    async indexFolder() {
      throw fail("Indexing by path needs the Java backend. Choose or drop a folder instead.", "BAD_PATH");
    },

    async search(query, mode) {
      await delay(90);
      const data = local.search(query, mode);
      if (data.query) remember(data.query, data.mode);
      return data;
    },

    async words(limit) { return local.topWords(limit); },
    async document(id) { return local.getDocument(id); },
    async history() { return loadHistory(); },
    async clearHistory() { saveHistory([]); }
  };

  /* ---- Java mode ------------------------------------------------------------ */
  async function http(method, path, body) {
    const res = await fetch(API_BASE + path, {
      method,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined
    });
    if (!res.ok) {
      let info = {};
      try { info = await res.json(); } catch (e) { /* body was not JSON */ }
      throw fail(info.error || "Request failed (" + res.status + ")", info.code);
    }
    return res.status === 204 ? null : res.json();
  }

  const java = {
    status: async () => (await http("GET", "/health")).index || null,
    indexSamples: () => http("POST", "/index", { path: SAMPLE_FOLDER }),
    indexFolder: (path) => http("POST", "/index", { path }),
    indexFiles: async () => { throw fail("With the Java backend, type the folder path instead.", "BAD_PATH"); },
    search: (query, mode) => http("GET", "/search?q=" + encodeURIComponent(query) + "&mode=" + encodeURIComponent(mode)),
    words: (limit) => http("GET", "/words?limit=" + encodeURIComponent(limit)),
    document: (id) => http("GET", "/document?id=" + encodeURIComponent(id)),
    history: () => http("GET", "/history"),
    clearHistory: () => http("DELETE", "/history")
  };

  /* ---- Choosing between them ------------------------------------------------ */
  let useJava = false; // true once the Java backend has answered /api/health
  let readyPromise = null;

  // The Java backend only listens on this computer, so on any other address
  // (a Vercel site, for example) there is nothing to look for and no request is made.
  const LOCAL_HOSTS = ["localhost", "127.0.0.1", "[::1]"];

  async function detect() {
    if (FORCE_MODE) {
      useJava = FORCE_MODE === "java";
      return;
    }
    if (LOCAL_HOSTS.indexOf(location.hostname) === -1) {
      useJava = false;
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2500);
    try {
      const res = await fetch(API_BASE + "/health", { cache: "no-store", signal: controller.signal });
      const info = await res.json(); // a static server answers 404 or HTML, which fails here
      useJava = res.ok && info.ok === true;
    } catch (e) {
      useJava = false;
    } finally {
      clearTimeout(timer);
    }
  }

  function ensureReady() {
    if (!readyPromise) readyPromise = detect();
    return readyPromise;
  }

  async function active() {
    await ensureReady();
    return useJava ? java : browser;
  }

  window.Api = {
    get ready() { return ensureReady(); },
    get mode() { return useJava ? "java" : "browser"; },

    // True when the index holds the bundled sample files, so the app can suggest searches that fit them.
    isSampleIndex: (stats) => !!stats && (stats.path === SAMPLE_FOLDER || stats.path === SAMPLE_LABEL),

    status: async () => (await active()).status(),
    indexSamples: async () => (await active()).indexSamples(),
    indexFolder: async (path) => (await active()).indexFolder(path),
    indexFiles: async (read) => (await active()).indexFiles(read),
    search: async (query, mode) => (await active()).search(query, mode),
    words: async (limit) => (await active()).words(limit),
    document: async (id) => (await active()).document(id),
    history: async () => (await active()).history(),
    clearHistory: async () => (await active()).clearHistory()
  };
})();
