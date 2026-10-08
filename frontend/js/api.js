/*
 * API layer: the only file that knows where data comes from.
 *
 * The UI calls window.Api.* and never touches fetch() or the mock directly.
 * Right now USE_MOCK is true, so everything is answered in the browser from
 * the sample documents in mock-data.js. When the Java backend is ready, set
 * USE_MOCK to false and the same calls go to the /api endpoints documented in
 * frontend/README.md. No other file needs to change.
 *
 * Every function returns a Promise and rejects with an Error that may carry a
 * `code` (for example "NOT_INDEXED").
 */
(function () {
  "use strict";

  const USE_MOCK = true;
  const API_BASE = "/api";

  /* ---- Mock implementation ------------------------------------------------
   * A tiny stand-in for the Java engine so the UI can be demonstrated today.
   * It is intentionally simple and is deleted once USE_MOCK goes away.
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

  window.Api = Object.assign({ isMock: USE_MOCK }, USE_MOCK ? mock : remote);

  // The landing page always demos against the bundled sample files, even after
  // the app is switched over to the Java backend.
  window.DemoApi = mock;
})();
