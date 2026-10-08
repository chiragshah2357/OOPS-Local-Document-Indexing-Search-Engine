/*
 * Search app: indexing, searching, results and history.
 * All data comes through window.Api (see api.js).
 */
(function () {
  "use strict";

  const { esc, plural, highlight, countUp, ago } = window.UI;
  const $ = (id) => document.getElementById(id);

  const els = {
    searchForm: $("search-form"),
    query: $("query"),
    results: $("results"),
    panel: $("index-panel"),
    indexForm: $("index-form"),
    folder: $("folder-path"),
    indexBtn: $("index-btn"),
    stats: $("stats"),
    statDocs: $("stat-docs"),
    statTerms: $("stat-terms"),
    indexNote: $("index-note"),
    mockNote: $("mock-note"),
    historyList: $("history-list"),
    historyEmpty: $("history-empty"),
    historyClear: $("history-clear")
  };

  const SUGGESTIONS = [
    { q: "java thread", mode: "any" },
    { q: "hashmap", mode: "all" },
    { q: "exception", mode: "all" },
    { q: "index word", mode: "all" }
  ];

  let indexed = false;
  let searchSeq = 0; // lets a newer search ignore the answer to an older one

  /* ---- Rendering ---------------------------------------------------------- */
  function state(title, text, extra, modifier) {
    return (
      '<div class="state' + (modifier ? " state--" + modifier : "") + '">' +
        '<h2 class="state__title">' + esc(title) + "</h2>" +
        '<p class="state__text">' + esc(text) + "</p>" +
        (extra || "") +
      "</div>"
    );
  }

  // Demo mode only: a one-click way to index the bundled files.
  const sampleButton = () =>
    Api.isMock ? '<button class="btn btn--line" type="button" data-action="sample">Use the sample files</button>' : "";

  function showIntro() {
    if (!indexed) {
      const button = sampleButton();
      els.results.innerHTML = state(
        "Index a folder to begin",
        Api.isMock
          ? "Index the bundled sample files, then search them. Matches are ranked by how often your words appear."
          : "Enter a folder path on the right and index it. Matches are ranked by how often your words appear.",
        button
      );
      return;
    }
    const chips = SUGGESTIONS.map(
      (s) =>
        '<li><button class="suggest__btn" type="button" data-q="' + esc(s.q) + '" data-mode="' + s.mode + '">' +
        esc(s.q) + ' <i class="ph ph-arrow-up-right" aria-hidden="true"></i></button></li>'
    ).join("");
    els.results.innerHTML = state("Ready to search", "Type some words above, or start from one of these.", '<ul class="suggest">' + chips + "</ul>");
  }

  function showSkeleton() {
    els.results.innerHTML = '<div aria-hidden="true">' + '<div class="skel"><i></i><i></i><i></i></div>'.repeat(3) + "</div>";
  }

  function hitRow(r, terms, topScore, i) {
    const counts = Object.keys(r.matches)
      .map((t) => "<span>" + esc(t) + " <b>" + r.matches[t] + "</b></span>")
      .join("");
    return (
      '<li style="--i:' + i + '">' +
        "<div>" +
          '<h3 class="hit__name">' + esc(r.title) + "</h3>" +
          '<p class="hit__path">' + esc(r.path) + "</p>" +
          '<p class="hit__snip">' + highlight(r.snippet, terms) + "</p>" +
          '<p class="hit__terms">' + counts + "</p>" +
        "</div>" +
        '<div class="hit__score">' +
          "<span>" + r.score + '<span class="hit__unit">' + (r.score === 1 ? "hit" : "hits") + "</span></span>" +
          '<i class="bar" style="--w:' + (r.score / topScore).toFixed(2) + "; --i:" + i + '"></i>' +
        "</div>" +
      "</li>"
    );
  }

  function renderResults(data) {
    if (!data.terms.length) {
      els.results.innerHTML = state("Nothing to search for", "Your words are all common ones that the index ignores. Try something more specific.");
      return;
    }
    if (!data.total) {
      const hint = data.mode === "all" && data.terms.length > 1
        ? "No single file contains every word. Try Any word instead."
        : "Try a different word.";
      els.results.innerHTML = state("No files match", hint);
      return;
    }
    const top = data.results[0].score;
    els.results.innerHTML =
      '<p class="results__meta">' + plural(data.total, "file") + " for “" + esc(data.query) + "” in " + data.tookMs + " ms</p>" +
      '<ul class="hits">' + data.results.map((r, i) => hitRow(r, data.terms, top, i)).join("") + "</ul>";
  }

  async function renderHistory() {
    let items = [];
    try { items = await Api.history(); } catch (e) { /* history is optional */ }
    els.historyList.innerHTML = items
      .map(
        (h) =>
          '<li><button class="hist__btn" type="button" data-q="' + esc(h.query) + '" data-mode="' + esc(h.mode) + '">' +
          '<span class="hist__q">' + esc(h.query) + "</span>" +
          '<span class="hist__meta">' + (h.mode === "any" ? "any" : "all") + ", " + ago(h.at) + "</span></button></li>"
      )
      .join("");
    els.historyEmpty.hidden = items.length > 0;
    els.historyClear.hidden = items.length === 0;
  }

  /* ---- Actions ------------------------------------------------------------ */
  async function runIndex() {
    els.panel.classList.add("busy");
    els.indexBtn.disabled = true;
    els.indexNote.textContent = "Indexing…";
    try {
      const info = await Api.indexFolder(els.folder.value.trim());
      indexed = true;
      els.stats.hidden = false;
      countUp(els.statDocs, info.documents, 700);
      countUp(els.statTerms, info.terms, 900);
      els.indexNote.textContent = "Indexed " + info.path + " in " + info.tookMs + " ms.";
      showIntro();
      els.query.focus();
    } catch (err) {
      els.indexNote.textContent = err.message;
    } finally {
      els.panel.classList.remove("busy");
      els.indexBtn.disabled = false;
    }
  }

  async function runSearch(query, mode) {
    query = query.trim();
    if (!query) {
      els.query.focus();
      return;
    }
    const seq = ++searchSeq;
    showSkeleton();
    try {
      const data = await Api.search(query, mode);
      if (seq !== searchSeq) return;
      renderResults(data);
      renderHistory();
    } catch (err) {
      if (seq !== searchSeq) return;
      if (err.code === "NOT_INDEXED") {
        els.results.innerHTML = state("Index a folder first", "There is nothing to search yet. Index a folder, then run this again.", sampleButton(), "error");
        els.panel.classList.remove("nudge");
        void els.panel.offsetWidth; // restart the flash if it is already running
        els.panel.classList.add("nudge");
        els.folder.focus();
      } else {
        els.results.innerHTML = state("Something went wrong", err.message, null, "error");
      }
    }
  }

  function runWith(q, mode) {
    els.query.value = q;
    els.searchForm.elements.mode.value = mode;
    runSearch(q, mode);
  }

  /* ---- Wiring ------------------------------------------------------------- */
  els.searchForm.addEventListener("submit", (e) => {
    e.preventDefault();
    runSearch(els.query.value, els.searchForm.elements.mode.value);
  });

  els.indexForm.addEventListener("submit", (e) => {
    e.preventDefault();
    runIndex();
  });

  // Intro buttons and suggestion chips are rendered dynamically, so listen on the container.
  els.results.addEventListener("click", (e) => {
    const sample = e.target.closest("[data-action='sample']");
    if (sample) { runIndex(); return; }
    const chip = e.target.closest("[data-q]");
    if (chip) runWith(chip.dataset.q, chip.dataset.mode);
  });

  els.historyList.addEventListener("click", (e) => {
    const item = e.target.closest("[data-q]");
    if (item) runWith(item.dataset.q, item.dataset.mode);
  });

  els.historyClear.addEventListener("click", async () => {
    await Api.clearHistory();
    renderHistory();
  });

  // "/" jumps to the search box, like most search UIs.
  document.addEventListener("keydown", (e) => {
    if (e.key !== "/" || e.metaKey || e.ctrlKey || e.altKey) return;
    if (/^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName)) return;
    e.preventDefault();
    els.query.focus();
  });

  els.mockNote.hidden = !Api.isMock;
  showIntro();
  renderHistory();
})();
