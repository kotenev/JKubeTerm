/*
 * Sidebar collapse toggles for the Material for MkDocs theme.
 * Adds two fixed buttons (bottom-right, under the back-to-top control)
 * that toggle left navigation and right ToC via the [hidden] attribute.
 * Material's own CSS already reflows .md-content__inner when a sidebar
 * is [hidden]; extra CSS only defines the toggle buttons themselves.
 */
(function () {
  "use strict";
  var PRIMARY = '.md-sidebar--primary[data-md-type="navigation"], .md-sidebar--primary';
  var SECONDARY = '.md-sidebar--secondary[data-md-type="toc"], .md-sidebar--secondary';
  var NS = "jkubeterm.sidebars";
  var state = { nav: false, toc: false };

  function load() {
    try {
      var raw = localStorage.getItem(NS);
      if (raw) {
        var parsed = JSON.parse(raw);
        state.nav = !!parsed.nav;
        state.toc = !!parsed.toc;
      }
    } catch (e) { /* storage unavailable: keep defaults */ }
  }

  function save() {
    try {
      localStorage.setItem(NS, JSON.stringify(state));
    } catch (e) { /* private mode etc.: toggles still work per page */ }
  }

  function first(match) {
    var nodes = document.querySelectorAll(match);
    return nodes.length ? nodes[0] : null;
  }

  function apply(sidebar, hidden) {
    if (!sidebar) return;
    if (hidden) sidebar.setAttribute("hidden", "");
    else sidebar.removeAttribute("hidden");
  }

  function syncButtons(btnNav, btnToc) {
    if (btnNav) btnNav.setAttribute("aria-pressed", state.nav ? "true" : "false");
    if (btnToc) btnToc.setAttribute("aria-pressed", state.toc ? "true" : "false");
  }

  function applyAll() {
    apply(first(PRIMARY), state.nav);
    apply(first(SECONDARY), state.toc);
  }

  function iconFor(kind) {
    if (kind === "nav") {
      return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18v2H3V6m0 5h12v2H3v-2m0 5h18v2H3v-2Z"/></svg>';
    }
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 5h18v2H3V5m0 4h18v2H3V9m0 4h12v2H3v-2m0 4h18v2H3v-2Z"/></svg>';
  }

  function buildBar() {
    if (document.querySelector(".jk-side-toggle")) return;
    var bar = document.createElement("div");
    bar.className = "jk-side-toggle";
    bar.setAttribute("role", "group");
    bar.setAttribute("aria-label", "Sidebar visibility");

    var btnNav = document.createElement("button");
    btnNav.type = "button";
    btnNav.id = "jk-toggle-nav";
    btnNav.title = "Show/hide left navigation (N)";
    btnNav.innerHTML = iconFor("nav") + "<span>Nav</span>";

    var btnToc = document.createElement("button");
    btnToc.type = "button";
    btnToc.id = "jk-toggle-toc";
    btnToc.title = "Show/hide table of contents (T)";
    btnToc.innerHTML = iconFor("toc") + "<span>ToC</span>";

    btnNav.addEventListener("click", function () {
      state.nav = !state.nav;
      apply(first(PRIMARY), state.nav);
      syncButtons(btnNav, btnToc);
      save();
    });
    btnToc.addEventListener("click", function () {
      state.toc = !state.toc;
      apply(first(SECONDARY), state.toc);
      syncButtons(btnNav, btnToc);
      save();
    });

    bar.appendChild(btnNav);
    bar.appendChild(btnToc);
    document.body.appendChild(bar);
    syncButtons(btnNav, btnToc);
  }

  function onKey(event) {
    var tag = (event.target && event.target.tagName) || "";
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(tag) || event.target.isContentEditable) return;
    var key = (event.key || "").toLowerCase();
    if (key !== "n" && key !== "t" || event.ctrlKey || event.metaKey || event.altKey) return;
    var btnNav = document.getElementById("jk-toggle-nav");
    var btnToc = document.getElementById("jk-toggle-toc");
    if (key === "n") {
      state.nav = !state.nav;
      apply(first(PRIMARY), state.nav);
    } else {
      state.toc = !state.toc;
      apply(first(SECONDARY), state.toc);
    }
    syncButtons(btnNav, btnToc);
    save();
  }

  function render() {
    load();
    applyAll();
    buildBar();
  }

  document.removeEventListener("keydown", onKey);
  document.addEventListener("keydown", onKey);
  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(render);
  } else {
    document.addEventListener("DOMContentLoaded", render);
  }
  render();
})();
