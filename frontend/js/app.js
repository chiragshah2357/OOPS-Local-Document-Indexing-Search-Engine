/*
 * Search app: indexing, searching, results, the document viewer, the most
 * frequent words, the search history, and the Gemini features (smart search
 * and "Ask your notes", Java mode only). All data comes through window.Api
 * (see api.js), so this file works the same in browser mode and Java mode.
 */
(function () {
  "use strict";

  const { esc, plural, highlight, countUp, ago, reducedMotion } = window.UI;
  const $ = (id) => document.getElementById(id);

  const els = {
    searchForm: $("search-form"),
    query: $("query"),
    results: $("results"),
    panel: $("index-panel"),
    badge: $("mode-badge"),
    pick: $("pick"),
    pickFolderBtn: $("pick-folder-btn"),
    pickFilesBtn: $("pick-files-btn"),
    pickFolder: $("pick-folder"),
    pickFiles: $("pick-files"),
    indexForm: $("index-form"),
    folder: $("folder-path"),
    stats: $("stats"),
    statDocs: $("stat-docs"),
    statTerms: $("stat-terms"),
    indexNote: $("index-note"),
    modeNote: $("mode-note"),
    wordsPanel: $("words-panel"),
    wordsList: $("words-list"),
    historyList: $("history-list"),
    historyEmpty: $("history-empty"),
    historyClear: $("history-clear"),
    viewer: $("viewer"),
    viewerTitle: $("viewer-title"),
    viewerPath: $("viewer-path"),
    viewerTerms: $("viewer-terms"),
    viewerPos: $("viewer-pos"),
    viewerPrev: $("viewer-prev"),
    viewerNext: $("viewer-next"),
    viewerBody: $("viewer-body"),
    viewerClose: $("viewer-close"),
    smart: $("smart"),
    smartLabel: $("smart-label"),
    askPanel: $("ask-panel"),
    askForm: $("ask-form"),
    question: $("question"),
    askBtn: $("ask-btn"),
    answer: $("answer"),
    askNote: $("ask-note"),
    llmBadge: $("llm-badge")
  };

  // Searches that show off the sample files: two words in each mode, one word, and a miss in "all".
  const SAMPLE_SUGGESTIONS = [
    { q: "java thread", mode: "any" },
    { q: "hashmap", mode: "all" },
    { q: "exception", mode: "all" },
    { q: "index word", mode: "all" }
  ];
  const TOP_WORDS = 8;
  const PAGE = 50; // results drawn at a time, so a big folder stays quick

  let indexed = false;
  let busy = false;
  let sampleIndex = false; // the sample files are indexed, so the sample suggestions fit
  let topWords = [];
  let lastData = null;     // the search on screen
  let lastTerms = [];      // its terms, for the viewer's highlights
  let answerTerms = [];    // the search words of the last question, for highlighting its source files
  let shown = 0;           // how many of its results are drawn
  let searchSeq = 0;       // lets a newer search ignore the answer to an older one
  let pending = null;      // a search from the address bar, run once something is indexed

  const modeOf = () => els.searchForm.elements.mode.value;
  const setMode = (mode) => { els.searchForm.elements.mode.value = mode === "any" ? "any" : "all"; };

  /* ---- Rendering ------------------------------------------------------------ */
  function state(title, text, extra, modifier) {
    return (
      '<div class="state' + (modifier ? " state--" + modifier : "") + '">' +
        '<h2 class="state__title">' + esc(title) + "</h2>" +
        '<p class="state__text">' + esc(text) + "</p>" +
        (extra || "") +
      "</div>"
    );
  }

  const sampleButton = (label) =>
    '<button class="btn btn--solid" type="button" data-action="sample">' + esc(label) +
    ' <i class="ph ph-arrow-right" aria-hidden="true"></i></button>';

  const chip = (q, mode, label) =>
    '<li><button class="suggest__btn" type="button" data-q="' + esc(q) + '" data-mode="' + mode + '">' +
    esc(label || q) + ' <i class="ph ph-arrow-up-right" aria-hidden="true"></i></button></li>';

  function showIntro() {
    if (!indexed) {
      const java = Api.mode === "java";
      const then = pending ? " Then “" + pending.q + "” runs straight away." : "";
      els.results.innerHTML = state(
        java ? "Index a folder to begin" : "Index some files to begin",
        (java
          ? "Type the path of a folder on the right, or start with the sample-docs folder."
          : "Drop a folder of .txt files anywhere on this page, choose one on the right, or start with the six sample files. Nothing is uploaded.") + then,
        sampleButton(java ? "Use the sample-docs folder" : "Use the sample files")
      );
      return;
    }

    const chips = sampleIndex
      ? SAMPLE_SUGGESTIONS.map((s) => chip(s.q, s.mode, s.q + (s.mode === "any" ? " (any)" : ""))).join("")
      : topWords.slice(0, 4).map((w) => chip(w.word, "all")).join("");
    els.results.innerHTML = state(
      "Ready to search",
      sampleIndex ? "Type some words above, or start from one of these." : "Type some words above, or start from the most frequent ones.",
      chips ? '<ul class="suggest">' + chips + "</ul>" : ""
    );
  }

  function showSkeleton() {
    els.results.innerHTML = '<div aria-hidden="true">' + '<div class="skel"><i></i><i></i><i></i></div>'.repeat(3) + "</div>";
  }

  // `i` only staggers the entrance animation, so it is capped to keep long lists quick.
  function hitRow(r, terms, topScore, i) {
    i = Math.min(i, 8);
    const counts = Object.keys(r.matches)
      .map((t) => '<span><mark>' + esc(t) + "</mark> ×" + r.matches[t] + "</span>")
      .join('<span class="hit__plus">+</span>');
    return (
      '<li style="--i:' + i + '">' +
        '<div class="hit__main">' +
          '<h3 class="hit__name"><button class="hit__open" type="button" data-open="' + r.id + '">' +
            esc(r.title) + ' <i class="ph ph-arrow-up-right" aria-hidden="true"></i></button></h3>' +
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

  // After a smart search: the words Gemini added, or why it could not help.
  function smartLine(data) {
    if (!data.added) return "";
    const words = data.added.map((w) => "<mark>" + esc(w) + "</mark>").join(" ");
    const text = data.llmNote ? esc(data.llmNote)
      : data.added.length ? "Gemini also searched for " + words : "Gemini found no related words to add.";
    return '<p class="results__smart"><i class="ph ph-sparkle" aria-hidden="true"></i><span>' + text + "</span></p>";
  }

  function renderResults(data) {
    lastTerms = data.terms;
    const smart = smartLine(data);
    if (!data.terms.length) {
      els.results.innerHTML = smart + state("Nothing to search for", "Your words are all common ones that the index ignores, such as “the” or “and”. Try something more specific.");
      return;
    }
    if (!data.total) {
      const tryAny = data.mode === "all" && data.terms.length > 1;
      els.results.innerHTML = smart + state(
        "No files match",
        tryAny ? "No single file contains every word." : "None of the indexed files contain “" + data.terms.join(" ") + "”.",
        tryAny ? '<ul class="suggest">' + chip(data.query, "any", "Search for any of the words") + "</ul>" : ""
      );
      return;
    }
    lastData = data;
    shown = Math.min(PAGE, data.total);
    els.results.innerHTML = smart +
      '<p class="results__meta">' + plural(data.total, "file") + " for “" + esc(data.query) + "”, " +
        (data.added ? "smart search" : data.mode === "any" ? "any word" : "all words") + ", in " + data.tookMs + " ms</p>" +
      '<ul class="hits">' + rows(0, shown) + "</ul>" + moreButton();
  }

  function rows(from, to) {
    const top = lastData.results[0].score;
    return lastData.results.slice(from, to).map((r, i) => hitRow(r, lastData.terms, top, i)).join("");
  }

  function moreButton() {
    const left = lastData.total - shown;
    return left > 0
      ? '<button class="btn btn--line results__more" type="button" data-action="more">Show ' + Math.min(PAGE, left) + " more (" + left + " left)</button>"
      : "";
  }

  function showMore() {
    const from = shown;
    shown = Math.min(shown + PAGE, lastData.total);
    els.results.querySelector(".hits").insertAdjacentHTML("beforeend", rows(from, shown));
    els.results.querySelector("[data-action='more']").outerHTML = moreButton();
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

  // Keyword frequency over the whole index. Each row searches for its word.
  async function renderWords() {
    try { topWords = await Api.words(TOP_WORDS); } catch (e) { topWords = []; }
    if (!topWords.length) {
      els.wordsPanel.hidden = true;
      return;
    }
    const top = topWords[0].total;
    els.wordsList.innerHTML = topWords
      .map(
        (w, i) =>
          '<li><button class="word" type="button" data-q="' + esc(w.word) + '" data-mode="all" ' +
          'title="' + esc(w.word) + ": " + plural(w.total, "time") + " in " + plural(w.documents, "file") + '">' +
          '<span class="word__name">' + esc(w.word) + "</span>" +
          '<i class="bar word__bar" style="--w:' + (w.total / top).toFixed(2) + "; --i:" + i + '"></i>' +
          '<span class="word__count">' + w.total + "</span></button></li>"
      )
      .join("");
    els.wordsPanel.hidden = false;
  }

  /* ---- Indexing ------------------------------------------------------------- */
  function setBusy(on, note) {
    busy = on;
    els.panel.classList.toggle("busy", on);
    els.panel.querySelectorAll("button, input").forEach((el) => { el.disabled = on; });
    if (note) els.indexNote.textContent = note;
  }

  function describe(info, ignored) {
    const from = Api.isSampleIndex(info) || !info.path ? "" : " from " + info.path;
    let text = "Indexed " + plural(info.documents, Api.isSampleIndex(info) ? "sample file" : "file") + from + " in " + info.tookMs + " ms.";
    if (info.skipped) text += " " + plural(info.skipped, "file") + " could not be read.";
    if (ignored) text += " " + plural(ignored, "file") + " that " + (ignored === 1 ? "is" : "are") + " not .txt left out.";
    return text;
  }

  // Called after any successful index. `restored` means the Java engine already had one when the page loaded.
  async function onIndexed(info, opts) {
    opts = opts || {};
    indexed = true;
    sampleIndex = Api.isSampleIndex(info);
    els.stats.hidden = false;
    countUp(els.statDocs, info.documents, opts.restored ? 0 : 700);
    countUp(els.statTerms, info.terms, opts.restored ? 0 : 900);
    els.indexNote.textContent = opts.restored
      ? "The Java engine already holds an index of " + info.path + "."
      : describe(info, opts.ignored);

    await renderWords();

    if (pending) {
      const p = pending;
      pending = null;
      runWith(p.q, p.mode);
    } else {
      showIntro();
      if (!opts.restored) els.query.focus();
    }
  }

  // `opts` is passed on to onIndexed once the task is done, so the task may still fill it in.
  async function runIndex(task, note, opts) {
    if (busy) return;
    setBusy(true, note || "Indexing…");
    try {
      const info = await task();
      setBusy(false);
      await onIndexed(info, opts);
    } catch (err) {
      setBusy(false);
      els.indexNote.textContent = err.message;
    }
  }

  const indexSamples = () => runIndex(() => Api.indexSamples());

  function indexPath() {
    const path = els.folder.value.trim();
    if (!path) {
      els.indexNote.textContent = "Type the path of a folder first.";
      els.folder.focus();
      return;
    }
    runIndex(() => Api.indexFolder(path));
  }

  // Browser mode: `listPromise` gives the picked or dropped files (see files.js).
  function indexPicked(listPromise) {
    const opts = {};
    runIndex(async () => {
      const list = await listPromise;
      const read = await Files.read(list, (done, total) => {
        els.indexNote.textContent = "Reading " + done + " of " + plural(total, "file") + "…";
      });
      opts.ignored = read.ignored;
      return Api.indexFiles(read);
    }, "Reading files…", opts);
  }

  /* ---- Searching ------------------------------------------------------------ */
  function writeUrl(q, mode, smart) {
    try {
      const url = new URL(location.href);
      url.searchParams.set("q", q);
      url.searchParams.set("mode", mode);
      if (smart) url.searchParams.set("smart", "1");
      else url.searchParams.delete("smart");
      history.replaceState(null, "", url);
    } catch (e) { /* a file:// page may refuse; the search still works */ }
  }

  // Flashes the Index panel when something needs an index first.
  function nudgeIndex() {
    els.panel.classList.remove("nudge");
    void els.panel.offsetWidth; // restart the flash if it is already running
    els.panel.classList.add("nudge");
  }

  const smartOn = () => els.smart.checked && !els.smart.disabled;

  async function runSearch(query, mode) {
    query = query.trim();
    if (!query) {
      els.query.focus();
      return;
    }
    if (!indexed) {
      pending = { q: query, mode };
      els.results.innerHTML = state(
        "Index something first",
        "There is nothing to search yet. Index some files and “" + query + "” runs straight away.",
        sampleButton(Api.mode === "java" ? "Use the sample-docs folder" : "Use the sample files"),
        "error"
      );
      nudgeIndex();
      return;
    }

    const seq = ++searchSeq;
    const smart = smartOn();
    showSkeleton();
    writeUrl(query, mode, smart);
    try {
      const data = await Api.search(query, mode, smart);
      if (seq !== searchSeq) return;
      renderResults(data);
      renderHistory();
      if (smart) showLlmNote();
    } catch (err) {
      if (seq !== searchSeq) return;
      if (err.code === "NOT_INDEXED") {
        // The Java engine was restarted since the page loaded.
        indexed = false;
        els.stats.hidden = true;
        els.wordsPanel.hidden = true;
        pending = { q: query, mode };
        showIntro();
      } else {
        els.results.innerHTML = state("Something went wrong", err.message, null, "error");
      }
    }
  }

  function runWith(q, mode) {
    els.query.value = q;
    setMode(mode);
    runSearch(q, mode);
  }

  /* ---- Ask your notes (Gemini) ---------------------------------------------- */
  // Says whether Gemini can be used here, and how many calls it has had.
  function showLlmNote() {
    const llm = Api.llm;
    if (Api.mode !== "java") {
      els.askNote.textContent = "Smart search and questions need the Java engine (backend\\serve.bat) with a Gemini key, so they are off on this site.";
    } else if (!llm || !llm.available) {
      els.askNote.textContent = "Gemini is off: set GEMINI_API_KEY before starting serve.bat. Until then, questions list the best files and smart search is a normal search.";
    } else {
      els.askNote.textContent = "Gemini (" + llm.model + ") answers from the top 3 matching files only. Calls this session: " + llm.calls + ".";
    }
  }

  function setupLlm() {
    const java = Api.mode === "java";
    const llm = Api.llm;
    els.smart.disabled = !java;
    els.question.disabled = !java;
    els.askBtn.disabled = !java;
    els.smartLabel.title = java ? "Gemini adds related words, then the engine searches for any of them" : "Needs the Java engine";
    els.llmBadge.classList.toggle("badge--java", !!(llm && llm.available));
    els.askPanel.classList.toggle("ask--off", !java);
    showLlmNote();
  }

  async function runAsk() {
    const question = els.question.value.trim();
    if (!question || els.askBtn.disabled) {
      els.question.focus();
      return;
    }
    els.answer.hidden = false;
    if (!indexed) {
      els.answer.innerHTML = '<p class="answer__error">Index some files first, then ask.</p>';
      nudgeIndex();
      return;
    }

    els.askPanel.classList.add("busy");
    els.askBtn.disabled = true;
    els.answer.innerHTML = '<div class="skel" aria-hidden="true"><i></i><i></i><i></i></div>';
    try {
      renderAnswer(await Api.ask(question));
      renderHistory(); // the question was also a search
    } catch (err) {
      els.answer.innerHTML = '<p class="answer__error">' + esc(err.message) + "</p>";
    } finally {
      els.askPanel.classList.remove("busy");
      els.askBtn.disabled = false;
      showLlmNote();
    }
  }

  // The answer, with [file.txt] references marked, and the files it came from (click to read one).
  function renderAnswer(data) {
    answerTerms = data.terms || [];
    const text = esc(data.answer).replace(/\[([^\]\n]+)\]/g, '<span class="cite">$1</span>');
    const sources = (data.sources || [])
      .map((s) => '<li><button class="source" type="button" data-source="' + s.id + '">' + esc(s.title) +
        ' <i class="ph ph-arrow-up-right" aria-hidden="true"></i></button></li>')
      .join("");
    els.answer.innerHTML =
      '<p class="answer__text' + (data.fromLlm ? "" : " answer__text--plain") + '">' + text + "</p>" +
      (sources ? '<p class="answer__label">' + (data.fromLlm ? "Sources" : "Best matching files") + "</p>" +
        '<ul class="answer__sources">' + sources + "</ul>" : "");
  }

  /* ---- Document viewer ------------------------------------------------------ */
  const viewer = { marks: [], at: -1, opener: null };

  function goTo(i) {
    const marks = viewer.marks;
    if (!marks.length) return;
    if (viewer.at >= 0) marks[viewer.at].classList.remove("is-current");
    viewer.at = (i + marks.length) % marks.length;
    const mark = marks[viewer.at];
    mark.classList.add("is-current");
    mark.scrollIntoView({ block: "center", behavior: reducedMotion() ? "auto" : "smooth" });
    els.viewerPos.textContent = "Match " + (viewer.at + 1) + " of " + marks.length;
  }

  // `terms` are the words to highlight: the search on screen, or the question that found this file.
  async function openDocument(id, opener, terms) {
    terms = terms || lastTerms;
    let doc;
    try {
      doc = await Api.document(id);
    } catch (err) {
      els.results.insertAdjacentHTML("afterbegin", '<p class="results__meta results__meta--error">' + esc(err.message) + "</p>");
      return;
    }

    els.viewerTitle.textContent = doc.title;
    els.viewerPath.textContent = doc.path;
    els.viewerBody.innerHTML = highlight(doc.text, terms);
    viewer.marks = Array.from(els.viewerBody.querySelectorAll("mark"));
    viewer.at = -1;
    viewer.opener = opener || null;

    // How often each search word appears, counted from the highlights themselves.
    const counts = {};
    viewer.marks.forEach((m) => {
      const word = m.textContent.toLowerCase();
      counts[word] = (counts[word] || 0) + 1;
    });
    els.viewerTerms.innerHTML = terms
      .filter((t) => counts[t])
      .map((t) => "<span><mark>" + esc(t) + "</mark> ×" + counts[t] + "</span>")
      .join("");

    const none = viewer.marks.length === 0;
    els.viewerPrev.disabled = none;
    els.viewerNext.disabled = none;
    els.viewerPos.textContent = none ? "No matches in this file" : "";

    els.viewer.showModal();
    els.viewerBody.scrollTop = 0;
    if (!none) goTo(0);
    els.viewerBody.focus({ preventScroll: true });
  }

  /* ---- Wiring --------------------------------------------------------------- */
  els.searchForm.addEventListener("submit", (e) => {
    e.preventDefault();
    runSearch(els.query.value, modeOf());
  });

  // Switching the mode or smart search re-runs the search on screen, so the answers are easy to compare.
  els.searchForm.addEventListener("change", (e) => {
    if ((e.target.name === "mode" || e.target === els.smart) && indexed && els.query.value.trim()) {
      runSearch(els.query.value, modeOf());
    }
  });

  els.askForm.addEventListener("submit", (e) => {
    e.preventDefault();
    runAsk();
  });

  // Enter asks; Shift+Enter starts a new line.
  els.question.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      runAsk();
    }
  });

  els.answer.addEventListener("click", (e) => {
    const source = e.target.closest("[data-source]");
    if (source) openDocument(source.dataset.source, source, answerTerms);
  });

  els.indexForm.addEventListener("submit", (e) => {
    e.preventDefault();
    indexPath();
  });

  els.pickFolderBtn.addEventListener("click", () => els.pickFolder.click());
  els.pickFilesBtn.addEventListener("click", () => els.pickFiles.click());
  [els.pickFolder, els.pickFiles].forEach((input) => {
    input.addEventListener("change", () => {
      if (!input.files.length) return;
      const list = Files.fromInput(input.files);
      input.value = ""; // so picking the same folder again still fires "change"
      indexPicked(Promise.resolve(list));
    });
  });

  // Every "use the sample files" button, wherever it is drawn.
  document.addEventListener("click", (e) => {
    if (e.target.closest("[data-action='sample']")) indexSamples();
  });

  // Result titles, suggestion chips and "search any word" buttons are drawn later, so listen on the container.
  els.results.addEventListener("click", (e) => {
    if (e.target.closest("[data-action='more']")) { showMore(); return; }
    const open = e.target.closest("[data-open]");
    if (open) { openDocument(open.dataset.open, open); return; }
    const item = e.target.closest("[data-q]");
    if (item) runWith(item.dataset.q, item.dataset.mode);
  });

  [els.historyList, els.wordsList].forEach((list) => {
    list.addEventListener("click", (e) => {
      const item = e.target.closest("[data-q]");
      if (item) runWith(item.dataset.q, item.dataset.mode);
    });
  });

  els.historyClear.addEventListener("click", async () => {
    await Api.clearHistory();
    renderHistory();
  });

  els.viewerClose.addEventListener("click", () => els.viewer.close());
  els.viewerPrev.addEventListener("click", () => goTo(viewer.at - 1));
  els.viewerNext.addEventListener("click", () => goTo(viewer.at + 1));
  els.viewer.addEventListener("click", (e) => {
    if (e.target === els.viewer) els.viewer.close(); // a click on the dimmed backdrop
  });
  els.viewer.addEventListener("close", () => {
    if (viewer.opener && document.contains(viewer.opener)) viewer.opener.focus();
  });
  els.viewer.addEventListener("keydown", (e) => {
    if (e.key === "n" || e.key === "N" || e.key === "p") {
      e.preventDefault();
      goTo(viewer.at + (e.key === "n" ? 1 : -1));
    }
  });

  // Drag and drop anywhere on the page. Files are always caught, so a drop never makes the browser leave the app.
  let dragDepth = 0;
  const carriesFiles = (e) => e.dataTransfer && Array.from(e.dataTransfer.types || []).indexOf("Files") >= 0;
  const canDrop = () => Api.mode === "browser" && !busy;
  const endDrag = () => { dragDepth = 0; document.body.classList.remove("dragging"); };

  document.addEventListener("dragenter", (e) => {
    if (!carriesFiles(e)) return;
    e.preventDefault();
    dragDepth++;
    if (canDrop()) document.body.classList.add("dragging");
  });
  document.addEventListener("dragover", (e) => {
    if (!carriesFiles(e)) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = canDrop() ? "copy" : "none";
  });
  document.addEventListener("dragleave", (e) => {
    if (!carriesFiles(e)) return;
    dragDepth = Math.max(0, dragDepth - 1);
    if (!dragDepth) endDrag();
  });
  document.addEventListener("drop", (e) => {
    if (!carriesFiles(e)) return;
    e.preventDefault();
    endDrag();
    if (Api.mode === "java") {
      els.indexNote.textContent = "With the Java engine, type the folder's path instead of dropping it.";
      els.folder.focus();
    } else if (!busy) {
      indexPicked(Files.fromDrop(e.dataTransfer));
    }
  });

  // "/" jumps to the search box, like most search UIs.
  document.addEventListener("keydown", (e) => {
    if (e.key !== "/" || e.metaKey || e.ctrlKey || e.altKey || els.viewer.open) return;
    if (/^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName)) return;
    e.preventDefault();
    els.query.focus();
  });

  /* ---- Start ---------------------------------------------------------------- */
  // A link such as app.html?q=java+thread&mode=any fills in the search, and runs it once something is indexed.
  (function readUrl() {
    const params = new URLSearchParams(location.search);
    const q = (params.get("q") || "").trim();
    if (!q) return;
    els.query.value = q;
    setMode(params.get("mode"));
    els.smart.checked = params.get("smart") === "1"; // only takes effect with the Java engine
    pending = { q, mode: modeOf() };
  })();

  // Wait until the API knows whether the Java backend is running, then draw the first screen.
  Api.ready.then(async () => {
    const java = Api.mode === "java";
    els.pick.hidden = java;
    els.indexForm.hidden = !java;
    els.badge.hidden = false;
    els.badge.innerHTML = '<i class="badge__dot" aria-hidden="true"></i>' + (java ? "Java engine" : "In your browser");
    els.badge.classList.toggle("badge--java", java);
    els.modeNote.hidden = false;
    els.modeNote.textContent = java
      ? "Connected to the Java backend on this computer. It can index any folder you can type the path of."
      : "Your files are read inside this tab and never uploaded. To index folders by path, run the Java backend (backend\\serve.bat).";

    let status = null;
    try { status = await Api.status(); } catch (e) { /* treat as not indexed */ }
    setupLlm();
    if (status) {
      await onIndexed(status, { restored: true });
    } else {
      els.indexNote.textContent = "Nothing indexed yet.";
      showIntro();
    }
    renderHistory();
  });
})();
