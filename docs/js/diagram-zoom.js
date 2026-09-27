(function () {
  "use strict";

  var MIN = 0.25;
  var MAX = 4;
  var STEP = 0.25;
  var IMG_SELECTOR = "img.uml";
  var MERMAID_SELECTOR = "div.mermaid, pre.mermaid";

  function pct(s) {
    return Math.round(s * 100) + "%";
  }

  function clamp(s) {
    s = Math.round(s * 100) / 100;
    if (s < MIN) return MIN;
    if (s > MAX) return MAX;
    return s;
  }

  function setScale(box, s) {
    s = clamp(s);
    box._jkScale = s;
    if (box._jkCanvas) {
      box._jkCanvas.style.transform = s === 1 ? "" : "scale(" + s + ")";
    }
    if (box._jkLevel) {
      box._jkLevel.textContent = pct(s);
    }
  }

  function zoomBy(box, delta) {
    setScale(box, (box._jkScale == null ? 1 : box._jkScale) + delta);
  }

  function fullscreenActive(box) {
    try {
      if (document.fullscreenElement === box) return true;
    } catch (e) { /* fullscreen unsupported */ }
    try {
      return !!(box.classList && box.classList.contains("jk-zoom-fallback-fullscreen"));
    } catch (e) { return false; }
  }

  function syncFs(box) {
    if (!box) return;
    var active = fullscreenActive(box);
    try {
      if (box.classList && box.classList.toggle) box.classList.toggle("jk-zoom-active", !!active);
    } catch (e) { /* classList unavailable */ }
    if (box._jkFsBtn) {
      box._jkFsBtn.setAttribute("aria-pressed", active ? "true" : "false");
      box._jkFsBtn.title = active ? "Exit fullscreen (Esc)" : "Open fullscreen (F)";
      box._jkFsBtn.setAttribute("aria-label", box._jkFsBtn.title);
    }
    if (box._jkReturnBtn) {
      if (active) box._jkReturnBtn.removeAttribute("hidden");
      else box._jkReturnBtn.setAttribute("hidden", "");
    }
  }

  function fallbackFullscreen(box) {
    try {
      if (box.classList && box.classList.add) box.classList.add("jk-zoom-fallback-fullscreen");
    } catch (e) { /* ignore */ }
    try {
      if (document.body && document.body.classList) document.body.classList.add("jk-zoom-fs-open");
    } catch (e) { /* ignore */ }
    syncFs(box);
    try {
      if (box._jkReturnBtn) box._jkReturnBtn.focus();
    } catch (e) { /* ignore */ }
  }

  function enterFullscreen(box) {
    var requested = false;
    try {
      if (box.requestFullscreen) {
        var p = box.requestFullscreen();
        if (p && p.catch) p.catch(function () { fallbackFullscreen(box); });
        requested = true;
      } else if (box.webkitRequestFullscreen) {
        box.webkitRequestFullscreen();
        requested = true;
      }
    } catch (e) { requested = false; }
    if (!requested) fallbackFullscreen(box);
    else syncFs(box);
  }

  function exitFullscreen(box) {
    var fsEl = null;
    try { fsEl = document.fullscreenElement || document.webkitFullscreenElement || null; } catch (e) { fsEl = null; }
    if (fsEl) {
      try {
        if (document.exitFullscreen) document.exitFullscreen();
        else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
      } catch (e) { /* ignore */ }
    }
    var boxes;
    try { boxes = document.querySelectorAll(".jk-zoom-fallback-fullscreen"); } catch (e) { boxes = []; }
    for (var i = 0; i < boxes.length; i++) {
      try {
        if (boxes[i].classList) boxes[i].classList.remove("jk-zoom-fallback-fullscreen");
      } catch (e) { /* ignore */ }
      syncFs(boxes[i]);
    }
    try {
      if (document.body && document.body.classList) document.body.classList.remove("jk-zoom-fs-open");
    } catch (e) { /* ignore */ }
    if (box) syncFs(box);
  }

  function toggleFullscreen(box) {
    if (fullscreenActive(box)) exitFullscreen(box);
    else enterFullscreen(box);
  }

  function makeButton(label, title, onClick) {
    var btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = label;
    btn.title = title;
    btn.setAttribute("aria-label", title);
    btn.addEventListener("click", onClick);
    return btn;
  }

  function makeBox(kindLabel) {
    var box = document.createElement("div");
    box.className = "jk-zoom";
    var bar = document.createElement("div");
    bar.className = "jk-zoom-bar";
    var level = document.createElement("span");
    level.className = "jk-zoom-level";
    level.setAttribute("aria-live", "polite");
    level.textContent = "100%";
    var viewport = document.createElement("div");
    viewport.className = "jk-zoom-viewport";
    viewport.tabIndex = 0;
    viewport.setAttribute("aria-label", kindLabel + " viewport. Plus and minus keys zoom, zero resets, control with mouse wheel zooms.");
    var canvas = document.createElement("div");
    canvas.className = "jk-zoom-canvas";
    viewport.appendChild(canvas);
    bar.appendChild(makeButton("\u2212", "Zoom out", function () { zoomBy(box, -STEP); }));
    bar.appendChild(level);
    bar.appendChild(makeButton("+", "Zoom in", function () { zoomBy(box, STEP); }));
    bar.appendChild(makeButton("100%", "Reset zoom to 100%", function () { setScale(box, 1); }));
    var fsBtn = makeButton("\u26F6", "Open fullscreen (F)", function () { toggleFullscreen(box); });
    bar.appendChild(fsBtn);
    box._jkFsBtn = fsBtn;
    var hint = document.createElement("span");
    hint.className = "jk-zoom-hint";
    hint.textContent = "Ctrl+scroll zooms · F fullscreen";
    bar.appendChild(hint);
    var returnBtn = makeButton("\u2190 Back to diagrams", "Exit fullscreen and return to the diagram in the page", function () { exitFullscreen(box); });
    returnBtn.className = "jk-zoom-return";
    returnBtn.setAttribute("hidden", "");
    box.appendChild(bar);
    box.appendChild(viewport);
    box.appendChild(returnBtn);
    box._jkCanvas = canvas;
    box._jkLevel = level;
    box._jkReturnBtn = returnBtn;
    viewport.addEventListener("keydown", function (event) {
      var key = event.key || "";
      if (key === "+" || key === "=") { event.preventDefault(); zoomBy(box, STEP); }
      else if (key === "-" || key === "_") { event.preventDefault(); zoomBy(box, -STEP); }
      else if (key === "0") { event.preventDefault(); setScale(box, 1); }
      else if (key === "f" || key === "F" || key === "\u0430" || key === "\u0410") { event.preventDefault(); toggleFullscreen(box); }
      else if (key === "Escape" && fullscreenActive(box)) { event.preventDefault(); exitFullscreen(box); }
    });
    viewport.addEventListener("wheel", function (event) {
      if (!event.ctrlKey && !event.metaKey) return;
      event.preventDefault();
      zoomBy(box, event.deltaY < 0 ? STEP : -STEP);
    }, { passive: false });
    return box;
  }

  function placeBox(node, box) {
    var parent = node.parentNode;
    if (!parent) return;
    var next = node.nextSibling;
    var emptyParagraph = parent.tagName === "P"
      && parent.children.length === 1
      && parent.children[0] === node;
    box._jkCanvas.appendChild(node);
    if (emptyParagraph && parent.parentNode) {
      parent.parentNode.replaceChild(box, parent);
    } else if (next && next.parentNode === parent) {
      parent.insertBefore(box, next);
    } else {
      parent.appendChild(box);
    }
  }

  function enhanceImg(img) {
    if (img.closest(".jk-zoom")) return;
    var box = makeBox("Diagram");
    placeBox(img, box);
    setScale(box, 1);
  }

  function enhanceMermaid(node) {
    if (node.closest(".jk-zoom")) return;
    if (!node.querySelector("svg")) return;
    var box = makeBox("Diagram");
    placeBox(node, box);
    setScale(box, 1);
  }

  function enhanceAll(root) {
    if (!root || !root.querySelectorAll) return;
    var imgs = root.querySelectorAll(IMG_SELECTOR);
    for (var i = 0; i < imgs.length; i++) enhanceImg(imgs[i]);
    var mermaids = root.querySelectorAll(MERMAID_SELECTOR);
    for (var j = 0; j < mermaids.length; j++) enhanceMermaid(mermaids[j]);
  }

  function render() {
    enhanceAll(document);
    syncAllFs();
  }

  function syncAllFs() {
    var boxes;
    try { boxes = document.querySelectorAll(".jk-zoom"); } catch (e) { boxes = []; }
    for (var i = 0; i < boxes.length; i++) syncFs(boxes[i]);
  }

  function onFsChange() {
    var boxes;
    try { boxes = document.querySelectorAll(".jk-zoom"); } catch (e) { boxes = []; }
    for (var i = 0; i < boxes.length; i++) syncFs(boxes[i]);
    var fsEl = null;
    try { fsEl = document.fullscreenElement || document.webkitFullscreenElement || null; } catch (e) { fsEl = null; }
    if (!fsEl) {
      try {
        if (document.body && document.body.classList) document.body.classList.remove("jk-zoom-fs-open");
      } catch (e) { /* ignore */ }
    }
  }

  var scheduled = false;
  function schedule() {
    if (scheduled) return;
    scheduled = true;
    var run = function () { scheduled = false; enhanceAll(document); };
    if (window.requestAnimationFrame) window.requestAnimationFrame(run);
    else window.setTimeout(run, 60);
  }

  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(render);
  } else if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", render);
  }
  render();

  try {
    var observer = new MutationObserver(function () { schedule(); });
    var target = document.documentElement || document.body;
    if (target) observer.observe(target, { childList: true, subtree: true });
  } catch (e) {
    if (window.console && window.console.warn) window.console.warn("diagram zoom unavailable");
  }

  try {
    document.addEventListener("fullscreenchange", onFsChange);
    document.addEventListener("webkitfullscreenchange", onFsChange);
    document.addEventListener("keydown", function (event) {
      if ((event.key || "") !== "Escape") return;
      var tag = (event.target && event.target.tagName) || "";
      if (/^(INPUT|TEXTAREA|SELECT)$/.test(tag)) return;
      exitFullscreen(null);
    });
  } catch (e) { /* fullscreen events unavailable */ }
})();
