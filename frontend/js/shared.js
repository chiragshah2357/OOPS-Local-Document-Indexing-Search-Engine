/*
 * Small helpers shared by the landing page and the app.
 * Exposed as window.UI.
 */
window.UI = (function () {
  "use strict";

  const ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
  const esc = (text) => String(text).replace(/[&<>"']/g, (ch) => ESCAPES[ch]);
  const plural = (n, word) => n + " " + word + (n === 1 ? "" : "s");
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const reducedMotion = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // Wrap every occurrence of a search term in <mark>, escaping everything else.
  function highlight(text, terms) {
    if (!terms.length) return esc(text);
    const safe = terms.map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
    const re = new RegExp("\\b(?:" + safe.join("|") + ")\\b", "gi");
    let out = "";
    let last = 0;
    let m;
    while ((m = re.exec(text))) {
      out += esc(text.slice(last, m.index)) + "<mark>" + esc(m[0]) + "</mark>";
      last = m.index + m[0].length;
    }
    return out + esc(text.slice(last));
  }

  // Eases a number up to its final value so a fresh index "arrives" instead of popping in.
  function countUp(el, to, ms) {
    if (reducedMotion() || to < 2) {
      el.textContent = to;
      return;
    }
    const start = performance.now();
    function frame(now) {
      const k = Math.min(1, (now - start) / ms);
      el.textContent = Math.round(to * (1 - Math.pow(1 - k, 3)));
      if (k < 1) requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
    // Browsers pause animation frames in hidden tabs; make sure the real number still lands.
    setTimeout(() => { el.textContent = to; }, ms + 120);
  }

  function ago(ts) {
    const s = Math.max(0, Math.round((Date.now() - ts) / 1000));
    if (s < 45) return "just now";
    if (s < 3600) return Math.round(s / 60) + " min ago";
    if (s < 86400) return Math.round(s / 3600) + " h ago";
    return Math.round(s / 86400) + " d ago";
  }

  return { esc, plural, wait, reducedMotion, highlight, countUp, ago };
})();
