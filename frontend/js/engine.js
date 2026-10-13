/*
 * Browser engine: the Java search engine rewritten in JavaScript, so the
 * website also works with no server (on Vercel, or when the Java backend is
 * not running).
 *
 * Each part follows a Java class in backend/src/docsearch with the same rules,
 * so both engines give the same answers for the same files:
 *
 *   tokenize      Tokenizer        lowercase, split on anything that is not a letter or digit, drop stop words
 *   buildSnippet  SnippetBuilder   a short piece of text around the first match
 *   create()      InvertedIndex + SearchEngine (index, search, rank, top words)
 *
 * Exposed as window.Engine.
 */
window.Engine = (function () {
  "use strict";

  const STOP_WORDS = new Set([
    "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "if", "in",
    "into", "is", "it", "of", "on", "or", "so", "that", "the", "then", "to", "was", "with"
  ]);

  // A run of letters or digits in any language, like Java's Character.isLetterOrDigit.
  const WORD = /[\p{L}\p{Nd}]+/gu;

  const SNIPPET_BEFORE = 70; // characters kept before the first match
  const SNIPPET_AFTER = 130; // and after it

  // Orders text the way Java's String.compareTo does, so ties break the same way in both engines.
  const compare = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

  function fail(message, code) {
    const err = new Error(message);
    err.code = code;
    return err;
  }

  /* ---- Tokenizer ---------------------------------------------------------- */
  function tokenize(text) {
    const words = [];
    for (const m of String(text).matchAll(WORD)) {
      const word = m[0].toLowerCase();
      if (!STOP_WORDS.has(word)) words.push(word);
    }
    return words;
  }

  /* ---- SnippetBuilder ----------------------------------------------------- */
  function buildSnippet(text, terms) {
    const flat = text.split(/\s+/).filter(Boolean).join(" ");
    const wanted = new Set(terms);

    let at = 0;
    for (const m of flat.matchAll(WORD)) {
      if (wanted.has(m[0].toLowerCase())) { at = m.index; break; }
    }

    const start = Math.max(0, at - SNIPPET_BEFORE);
    const end = Math.min(flat.length, at + SNIPPET_AFTER);
    let piece = flat.slice(start, end).trim();

    // The cut can land in the middle of a word, so drop that half word.
    if (start > 0 && piece.indexOf(" ") >= 0) piece = piece.slice(piece.indexOf(" ") + 1);
    if (end < flat.length && piece.lastIndexOf(" ") >= 0) piece = piece.slice(0, piece.lastIndexOf(" "));

    return (start > 0 ? "... " : "") + piece + (end < flat.length ? " ..." : "");
  }

  /* ---- InvertedIndex + SearchEngine --------------------------------------- */
  function create() {
    let index = null;      // Map<word, Map<document id, count>>, null until something is indexed
    let documents = null;  // Map<document id, { id, title, path, text }>
    let stats = null;

    function requireIndex() {
      if (!index) throw fail("Nothing is indexed yet. Index a folder first.", "NOT_INDEXED");
    }

    /**
     * Builds a new index from a list of { title, path, text }. Documents get
     * ids in path order, as the Java loader gives them in file order.
     * `label` names what was indexed, for example a folder name.
     */
    function build(list, label, skipped) {
      const started = performance.now();
      const sorted = list.slice().sort((a, b) => compare(a.path, b.path));
      const nextIndex = new Map();
      const nextDocuments = new Map();

      sorted.forEach((item, i) => {
        const doc = { id: i + 1, title: item.title, path: item.path, text: item.text };
        nextDocuments.set(doc.id, doc);
        for (const word of tokenize(doc.text)) {
          let counts = nextIndex.get(word);
          if (!counts) {
            counts = new Map();
            nextIndex.set(word, counts);
          }
          counts.set(doc.id, (counts.get(doc.id) || 0) + 1);
        }
      });

      // The old index is replaced only once the new one is complete.
      index = nextIndex;
      documents = nextDocuments;
      stats = {
        path: label,
        documents: nextDocuments.size,
        terms: nextIndex.size,
        tookMs: Math.max(1, Math.round(performance.now() - started)),
        skipped: skipped || 0
      };
      return Object.assign({}, stats);
    }

    function search(query, mode) {
      requireIndex();
      const started = performance.now();
      const clean = String(query).trim();
      const modeName = String(mode || "").trim().toLowerCase() === "any" ? "any" : "all";

      // The words to look for: no stop words, no repeats, in the order they were typed.
      const terms = Array.from(new Set(tokenize(clean)));
      const countsByWord = new Map(terms.map((t) => [t, index.get(t) || new Map()]));

      // "all" keeps documents that contain every word (intersection), "any" keeps the rest too (union).
      let ids = null;
      for (const term of terms) {
        const found = new Set(countsByWord.get(term).keys());
        if (ids === null) ids = found;
        else if (modeName === "any") found.forEach((id) => ids.add(id));
        else ids = new Set(Array.from(ids).filter((id) => found.has(id)));
      }

      // A document's score is the total number of times the words appear in it.
      const results = Array.from(ids || []).map((id) => {
        const doc = documents.get(id);
        const matches = {};
        let score = 0;
        for (const term of terms) {
          const count = countsByWord.get(term).get(id);
          if (count) {
            matches[term] = count;
            score += count;
          }
        }
        return { id, title: doc.title, path: doc.path, score, matches, snippet: buildSnippet(doc.text, Object.keys(matches)) };
      });
      results.sort((a, b) => b.score - a.score || compare(a.title, b.title));

      return {
        query: clean,
        mode: modeName,
        terms,
        total: results.length,
        tookMs: Math.max(1, Math.round(performance.now() - started)),
        results
      };
    }

    // Keyword frequency across the whole index: the words that appear most often in total.
    function topWords(limit) {
      requireIndex();
      const rows = [];
      index.forEach((counts, word) => {
        let total = 0;
        counts.forEach((n) => { total += n; });
        rows.push({ word, total, documents: counts.size });
      });
      rows.sort((a, b) => b.total - a.total || compare(a.word, b.word));
      return rows.slice(0, limit);
    }

    function getDocument(id) {
      requireIndex();
      const doc = documents.get(Number(id));
      if (!doc) throw fail("That document is not in the index.", "NOT_FOUND");
      return Object.assign({}, doc);
    }

    return {
      build,
      search,
      topWords,
      getDocument,
      status: () => (stats ? Object.assign({}, stats) : null)
    };
  }

  return { create, tokenize, buildSnippet, STOP_WORDS, WORD };
})();
