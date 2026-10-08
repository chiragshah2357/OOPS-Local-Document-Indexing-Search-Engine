/*
 * Landing page behaviour.
 *   1. Scroll reveals (IntersectionObserver, no scroll listeners).
 *   2. The hero demo: a real search over the bundled sample files. It types a
 *      few example queries by itself, and hands control over the moment the
 *      visitor touches it.
 */
(function () {
  "use strict";

  const { esc, plural, wait, reducedMotion, highlight } = window.UI;
  const $ = (id) => document.getElementById(id);

  /* ---- 1. Scroll reveal --------------------------------------------------- */
  const revealed = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("in");
        revealed.unobserve(entry.target);
      });
    },
    { threshold: 0.18, rootMargin: "0px 0px -6% 0px" }
  );
  document.querySelectorAll(".reveal").forEach((el) => revealed.observe(el));

  /* ---- 2. Hero demo ------------------------------------------------------- */
  const input = $("demo-input");
  const list = $("demo-list");
  const modeBtn = $("demo-mode");
  const foot = $("demo-foot");

  const SCRIPT = [
    { q: "java thread", mode: "any" },
    { q: "hashmap", mode: "all" },
    { q: "index word", mode: "all" }
  ];
  const MAX_ROWS = 3;
  const CYCLES = 2; // the loop rests on its own after this many passes

  let mode = "any";
  let manual = false; // set once the visitor interacts, which ends autoplay
  let seq = 0;
  let stats = null;

  const modeLabel = () => (mode === "any" ? "any word" : "all words");
  const syncMode = () => { modeBtn.textContent = modeLabel(); };

  function renderRows(data) {
    if (!data.terms.length) {
      list.innerHTML = '<li class="demo__empty">Those are common words that the index ignores. Try a more specific one.</li>';
      return;
    }
    if (!data.total) {
      list.innerHTML = '<li class="demo__empty">No file matches. Try fewer words, or switch to any word.</li>';
      return;
    }
    const top = data.results[0].score;
    list.innerHTML = data.results
      .slice(0, MAX_ROWS)
      .map(
        (r, i) =>
          '<li class="demo__row" style="--i:' + i + '">' +
            "<div>" +
              '<p class="demo__name">' + esc(r.title) + "</p>" +
              '<p class="demo__snip">' + highlight(r.snippet, data.terms) + "</p>" +
            "</div>" +
            '<div class="score"><span>' + r.score + '</span><i class="bar" style="--w:' + (r.score / top).toFixed(2) + "; --i:" + i + '"></i></div>' +
          "</li>"
      )
      .join("");
  }

  function setFoot(data) {
    if (!stats) return;
    const base = plural(stats.documents, "sample file") + ", " + plural(stats.terms, "word") + " indexed in your browser.";
    foot.textContent = data && data.total > MAX_ROWS ? "Top " + MAX_ROWS + " of " + data.total + " matches. " + base : base;
  }

  async function run() {
    const q = input.value.trim();
    const mine = ++seq;
    if (!q) {
      list.innerHTML = "";
      setFoot(null);
      return;
    }
    const data = await DemoApi.search(q, mode, { record: false });
    if (mine !== seq) return;
    renderRows(data);
    setFoot(data);
  }

  async function typeInto(text) {
    input.value = "";
    for (let i = 1; i <= text.length; i++) {
      if (manual) return;
      input.value = text.slice(0, i);
      await wait(70 + Math.random() * 50);
    }
  }

  async function autoplay() {
    if (reducedMotion()) {
      input.value = SCRIPT[0].q;
      mode = SCRIPT[0].mode;
      syncMode();
      run();
      return;
    }
    for (let step = 0; step < SCRIPT.length * CYCLES && !manual; step++) {
      const item = SCRIPT[step % SCRIPT.length];
      mode = item.mode;
      syncMode();
      await typeInto(item.q);
      if (manual) return;
      await run();
      await wait(3400);
      if (manual) return;
      // On the very last step, leave the final answer on screen.
      if (step < SCRIPT.length * CYCLES - 1) {
        while (input.value && !manual) {
          input.value = input.value.slice(0, -1);
          await wait(26);
        }
      }
    }
  }

  function takeOver() { manual = true; }

  let timer = 0;
  input.addEventListener("pointerdown", takeOver);
  input.addEventListener("focus", takeOver);
  input.addEventListener("input", () => {
    if (!manual) return;
    clearTimeout(timer);
    timer = setTimeout(run, 120);
  });

  modeBtn.addEventListener("click", () => {
    takeOver();
    mode = mode === "any" ? "all" : "any";
    syncMode();
    run();
  });

  syncMode();
  DemoApi.indexFolder("").then((info) => {
    stats = info;
    setFoot(null);
    autoplay();
  });
})();
