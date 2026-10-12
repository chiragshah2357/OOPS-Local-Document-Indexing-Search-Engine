/*
 * API layer: the only file that knows where data comes from.
 *
 * The UI calls window.Api.* and never touches fetch() or the demo engine
 * directly. Api works out by itself where its answers come from:
 *
 *   - Backend mode: the page is being served by the Java backend, which
 *     answers GET /api/health. Requests go to the /api endpoints described
 *     in frontend/README.md and search the folders on your computer.
 *   - Demo mode: anything else (a static host such as Vercel, a plain file
 *     server, a file opened from disk). Answers come from the sample
 *     documents in mock-data.js, computed inside the browser.
 *
 * Every function returns a Promise and rejects with an Error that may carry a
 * `code` (for example "NOT_INDEXED"). Wait for Api.ready before reading
 * Api.isMock or Api.sampleFolder, because the mode is found by asking the server.
 */
(function () {
  "use strict";

  const API_BASE = "/api";
  const FORCE_MODE = null; // "demo" or "backend" skips the check; leave null to detect it

  /* ---- Demo implementation -------------------------------------------------
   * A tiny stand-in for the Java engine. It lets the whole UI work without
   * a server, and it is what runs when the site is hosted as plain static files.
   */
  const HISTORY_KEY = "lds.history";
  const HISTORY_LIMIT = 10;
  const STOP_WORDS = new Set([
    "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "if", "in",
    "into", "is", "it", "of", "on", "or", "so", "that", "the", "then", "to", "was", "with"
  ]);

  let index = null; // Map<term, Map<docId, count>> (an inverted index)

  const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  function tokenize(text) {
    return (text.toLowerCase().match(/[a-z0-9]+/g) || []).filter((word) => !STOP_WORDS.has(word));
  }

  function loadHistory() {
    try { return JSON.parse(localStorage.getItem(HISTORY_KEY)) || []; } catch (e) { return []; }
  }

  function saveHistory(list) {
    try { localStorage.setItem(HISTORY_KEY, JSON.stringify(list)); } catch (e) { /* storage unavailable */ }
  }

  // A short excerpt around the first matching word, cut on word boundaries.
  function makeSnippet(text, terms) {
    const hit = new RegExp("\\b(?:" + terms.join("|") + ")\\b", "i").exec(text);
    const at = hit ? hit.index : 0;
    const start = Math.max(0, at - 70);
    const end = Math.min(text.length, at + 130);
    let out = text.slice(start, end).trim();
    if (start > 0) out = out.replace(/^\S*\s/, "");
    if (end < text.length) out = out.replace(/\s\S*$/, "");
    return (start > 0 ? "… " : "") + out + (end < text.length ? " …" : "");
  }

  const mock = {
    async indexFolder(path) {
      await delay(500);
      const started = performance.now();
      const next = new Map();
      for (const doc of window.MOCK_DOCS) {
        for (const term of tokenize(doc.text)) {
          if (!next.has(term)) next.set(term, new Map());
          const postings = next.get(term);
          postings.set(doc.id, (postings.get(doc.id) || 0) + 1);
        }
      }
      index = next;
      return {
        path: path || "the sample documents",
        documents: window.MOCK_DOCS.length,
        terms: next.size,
        tookMs: Math.max(1, Math.round(performance.now() - started))
      };
    },

    async search(query, mode, opts) {
      if (!index) {
        const err = new Error("Nothing is indexed yet. Index a folder first.");
        err.code = "NOT_INDEXED";
        throw err;
      }
      await delay(120);
      const started = performance.now();
      const terms = [...new Set(tokenize(query))];

      // "all" intersects the per-word document sets, "any" unions them.
      let ids = null;
      for (const term of terms) {
        const found = new Set((index.get(term) || new Map()).keys());
        if (ids === null) ids = found;
        else if (mode === "any") found.forEach((id) => ids.add(id));
        else ids = new Set([...ids].filter((id) => found.has(id)));
      }

      const results = [...(ids || [])].map((id) => {
        const doc = window.MOCK_DOCS.find((d) => d.id === id);
        const matches = {};
        let score = 0;
        for (const term of terms) {
          const count = (index.get(term) || new Map()).get(id);
          if (count) { matches[term] = count; score += count; }
        }
        return { id, title: doc.title, path: doc.path, score, matches, snippet: makeSnippet(doc.text, Object.keys(matches)) };
      });
      results.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title));

      // The landing-page demo passes { record: false } so it never touches the app's history.
      if (!opts || opts.record !== false) {
        const history = loadHistory().filter((h) => !(h.query === query && h.mode === mode));
        history.unshift({ query, mode, at: Date.now() });
        saveHistory(history.slice(0, HISTORY_LIMIT));
      }

      return {
        query,
        mode,
        terms,
        total: results.length,
        tookMs: Math.max(1, Math.round(performance.now() - started)),
        results
      };
    },

    async history() { return loadHistory(); },
    async clearHistory() { saveHistory([]); }
  };

  /* ---- Real implementation (Java backend) --------------------------------- */
  async function http(method, path, body) {
    const res = await fetch(API_BASE + path, {
      method,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined
    });
    if (!res.ok) {
      let info = {};
      try { info = await res.json(); } catch (e) { /* body was not JSON */ }
      const err = new Error(info.error || "Request failed (" + res.status + ")");
      err.code = info.code;
      throw err;
    }
    return res.status === 204 ? null : res.json();
  }

  const remote = {
    indexFolder: (path) => http("POST", "/index", { path }),
    search: (query, mode) => http("GET", "/search?q=" + encodeURIComponent(query) + "&mode=" + encodeURIComponent(mode)),
    history: () => http("GET", "/history"),
    clearHistory: () => http("DELETE", "/history")
  };

  /* ---- Choosing between them ----------------------------------------------- */
  let backend = false; // true once the Java backend has answered /api/health
  let readyPromise = null;

  // The Java backend only listens on this computer, so on any other address (a Vercel site, for example)
  // there is no backend to look for, and we skip the request.
  const LOCAL_HOSTS = ["localhost", "127.0.0.1", "[::1]"];

  async function detect() {
    if (FORCE_MODE) {
      backend = FORCE_MODE === "backend";
      return;
    }
    if (LOCAL_HOSTS.indexOf(location.hostname) === -1) {
      backend = false;
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2500);
    try {
      const res = await fetch(API_BASE + "/health", { cache: "no-store", signal: controller.signal });
      const info = await res.json(); // a static host answers 404 or HTML, which fails here
      backend = res.ok && info.ok === true;
    } catch (e) {
      backend = false;
    } finally {
      clearTimeout(timer);
    }
  }

  // The check runs the first time something asks for it, so the landing page, which never uses Api, makes no request.
  function ensureReady() {
    if (!readyPromise) readyPromise = detect();
    return readyPromise;
  }

  async function engine() {
    await ensureReady();
    return backend ? remote : mock;
  }

  window.Api = {
    get ready() { return ensureReady(); },
    get isMock() { return !backend; },
    // What the "use the sample files" button should index. The Java backend reads real folders, so it needs a path.
    get sampleFolder() { return backend ? "sample-docs" : ""; },
    indexFolder: async (path) => (await engine()).indexFolder(path),
    search: async (query, mode) => (await engine()).search(query, mode),
    history: async () => (await engine()).history(),
    clearHistory: async () => (await engine()).clearHistory()
  };

  // The landing page always demos against the bundled sample files, even when
  // the Java backend is running.
  window.DemoApi = mock;
})();
