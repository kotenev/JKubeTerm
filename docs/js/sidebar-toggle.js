/*
 * Header controls for the Material for MkDocs theme.
 * - Two buttons (Nav / ToC) in the right corner of the top header
 *   (.md-header__inner) that toggle left navigation and right ToC via the
 *   [hidden] attribute. Material's own CSS already reflows .md-content__inner
 *   when a sidebar is [hidden]; extra CSS only styles the header buttons.
 * - A dropdown navigation menu ("Sections") in the same header group,
 *   built by cloning the primary sidebar nav so it never drifts from mkdocs.yml.
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
    if (kind === "menu") {
      return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 8a3 3 0 0 0 3-3 3 3 0 0 0-3-3 3 3 0 0 0-3 3 3 3 0 0 0 3 3m0 3.54C9.64 9.35 6.5 8 3 8v11c3.5 0 6.64 1.35 9 3.54 2.36-2.19 5.5-3.54 9-3.54V8c-3.5 0-6.64 1.35-9 3.54"/></svg>';
    }
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 5h18v2H3V5m0 4h18v2H3V9m0 4h12v2H3v-2m0 4h18v2H3v-2Z"/></svg>';
  }

  function siteRoot() {
    var base = null;
    try {
      base = document.querySelector("head base[href]");
    } catch (e) {
      base = null;
    }
    var href = base ? base.getAttribute("href") : null;
    if (href) return href.replace(/\/?$/, "/");
    var scope = null;
    try {
      if (window.__md_scope instanceof URL) scope = window.__md_scope;
    } catch (e) { /* fall through */ }
    if (scope) {
      var path = scope.pathname || "/";
      return path.replace(/\/?$/, "/");
    }
    return "./";
  }

  function resolveHref(href) {
    if (!href) return null;
    href = href.trim();
    if (!href || href.charAt(0) === "#") return null;
    if (/^(https?:|mailto:|tel:)/i.test(href)) return href;
    var root = siteRoot();
    if (href.charAt(0) === "/") {
      var origin = window.location.origin || "";
      return origin + href;
    }
    return root + href.replace(/^\.\//, "");
  }

  function navLabel(node) {
    var ellipsis = node.querySelector(":scope > a.md-nav__link .md-ellipsis");
    if (ellipsis) return ellipsis.textContent.trim();
    var label = node.querySelector(":scope > label.md-nav__link .md-ellipsis");
    if (label) return label.textContent.trim();
    var linkText = node.querySelector(":scope > a.md-nav__link");
    if (linkText) return linkText.textContent.replace(/\s+/g, " ").trim();
    var labelText = node.querySelector(":scope > label.md-nav__link");
    if (labelText) return labelText.textContent.replace(/\s+/g, " ").trim();
    return "";
  }

  function navHref(node) {
    var link = node.matches("a.md-nav__link") ? node : node.querySelector(":scope > a.md-nav__link");
    if (link) return resolveHref(link.getAttribute("href"));
    var label = node.querySelector(":scope > label.md-nav__link");
    if (label && label.getAttribute("for")) {
      var input = document.getElementById(label.getAttribute("for"));
      if (input) {
        var nested = input.parentElement
          ? input.parentElement.querySelector("nav a.md-nav__link")
          : null;
        if (nested) return resolveHref(nested.getAttribute("href"));
      }
    }
    var fallback = node.querySelector("a.md-nav__link");
    if (fallback) return resolveHref(fallback.getAttribute("href"));
    return null;
  }

  function isActive(node) {
    return !!node.querySelector(".md-nav__link--active")
      || node.classList.contains("md-nav__item--active");
  }

  function cloneMenu(node, panel, depth, skipAnchors) {
    var items = node.querySelectorAll(":scope > .md-nav__list > .md-nav__item");
    if (!items.length) return false;
    var added = false;
    items.forEach(function (item) {
      var label = navLabel(item);
      var href = navHref(item);
      var nested = item.querySelector(":scope > nav.md-nav, :scope nav.md-nav");
      var nestedAdded = false;
      if (skipAnchors && href && href.charAt(0) === "#") {
        if (nested && cloneMenu(nested, panel, depth, skipAnchors)) added = true;
        return;
      }
      if (!label && !href && !nested) return;
      if (depth === 0 && (label || href)) {
        var section = document.createElement("div");
        section.className = "jk-nav-section";
        var title = null;
        if (label) {
          if (href) {
            title = document.createElement("a");
            title.setAttribute("href", href);
          } else {
            title = document.createElement("span");
          }
          title.className = "jk-nav-section-title" + (href ? " jk-nav-link" : "");
          title.textContent = label;
          section.appendChild(title);
        }
        if (nested) {
          nestedAdded = cloneMenu(nested, section, depth + 1, skipAnchors);
          if (!nestedAdded && !label) return;
        } else if (href && !label) {
          return;
        } else if (!nested && href && label) {
          var single = document.createElement("a");
          single.className = "jk-nav-link" + (isActive(item) ? " jk-nav-link--active" : "");
          single.setAttribute("href", href);
          single.textContent = label;
          title.parentNode.replaceChild(single, title);
          single.classList.remove("jk-nav-section-title");
        }
        if (section.childNodes.length) {
          panel.appendChild(section);
          added = true;
        }
        return;
      }
      if (label && href) {
        var link = document.createElement("a");
        link.className = "jk-nav-link" + (isActive(item) ? " jk-nav-link--active" : "");
        link.setAttribute("href", href);
        link.textContent = label;
        panel.appendChild(link);
        added = true;
      } else if (label) {
        var head = document.createElement("span");
        head.className = "jk-nav-section-title";
        head.textContent = label;
        panel.appendChild(head);
        added = true;
      }
      if (nested) {
        if (cloneMenu(nested, panel, depth + 1, skipAnchors)) added = true;
      }
    });
    return added;
  }

  function buildMenu(bar) {
    var wrap = bar.querySelector("#jk-nav-menu");
    var btnMenu = wrap ? wrap.querySelector("#jk-toggle-menu") : null;
    var panel = wrap ? wrap.querySelector("#jk-nav-panel") : null;
    var source = document.querySelector(".md-sidebar--primary .md-nav--primary, .md-nav--primary");
    if (!wrap) {
      wrap = document.createElement("div");
      wrap.className = "jk-nav-menu";
      wrap.id = "jk-nav-menu";
      btnMenu = document.createElement("button");
      btnMenu.type = "button";
      btnMenu.id = "jk-toggle-menu";
      btnMenu.title = "Site sections (G)";
      btnMenu.setAttribute("aria-haspopup", "true");
      btnMenu.setAttribute("aria-expanded", "false");
      btnMenu.innerHTML = iconFor("menu") + "<span>Sections</span>";
      panel = document.createElement("div");
      panel.className = "jk-nav-panel";
      panel.id = "jk-nav-panel";
      panel.setAttribute("role", "menu");
      panel.setAttribute("hidden", "");
      wrap.appendChild(btnMenu);
      wrap.appendChild(panel);
      bar.insertBefore(wrap, bar.firstChild);
      btnMenu.addEventListener("click", function (event) {
        event.stopPropagation();
        var open = panel.hasAttribute("hidden");
        if (open) {
          refreshMenu(panel);
          panel.removeAttribute("hidden");
          btnMenu.setAttribute("aria-expanded", "true");
        } else {
          panel.setAttribute("hidden", "");
          btnMenu.setAttribute("aria-expanded", "false");
        }
      });
      document.addEventListener("click", function (event) {
        if (!panel.hasAttribute("hidden") && !wrap.contains(event.target)) {
          panel.setAttribute("hidden", "");
          btnMenu.setAttribute("aria-expanded", "false");
        }
      });
      document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && !panel.hasAttribute("hidden")) {
          panel.setAttribute("hidden", "");
          btnMenu.setAttribute("aria-expanded", "false");
          btnMenu.focus();
        }
      });
    }
    refreshMenu(panel);
    return wrap;
  }

  function refreshMenu(panel) {
    if (!panel) return;
    var source = document.querySelector(".md-sidebar--primary .md-nav--primary, .md-nav--primary");
    panel.textContent = "";
    var ok = source ? cloneMenu(source, panel, 0, true) : false;
    if (!ok) {
      var empty = document.createElement("div");
      empty.className = "jk-nav-empty";
      empty.textContent = "Navigation is unavailable on this page.";
      panel.appendChild(empty);
    }
  }

  function buildBar() {
    var bar = document.querySelector(".md-header__inner > .jk-side-toggle");
    var header = document.querySelector(".md-header__inner");
    if (!header) return;
    if (!bar) {
      bar = document.createElement("div");
      bar.className = "jk-side-toggle";
      bar.setAttribute("role", "group");
      bar.setAttribute("aria-label", "Sidebar visibility");
      header.appendChild(bar);
    }

    var btnNav = bar.querySelector("#jk-toggle-nav");
    if (!btnNav) {
      btnNav = document.createElement("button");
      btnNav.type = "button";
      btnNav.id = "jk-toggle-nav";
      btnNav.title = "Show/hide left navigation (N)";
      btnNav.innerHTML = iconFor("nav") + "<span>Nav</span>";
      btnNav.addEventListener("click", function () {
        state.nav = !state.nav;
        apply(first(PRIMARY), state.nav);
        syncButtons(
          bar.querySelector("#jk-toggle-nav"),
          bar.querySelector("#jk-toggle-toc")
        );
        save();
      });
      bar.appendChild(btnNav);
    }

    var btnToc = bar.querySelector("#jk-toggle-toc");
    if (!btnToc) {
      btnToc = document.createElement("button");
      btnToc.type = "button";
      btnToc.id = "jk-toggle-toc";
      btnToc.title = "Show/hide table of contents (T)";
      btnToc.innerHTML = iconFor("toc") + "<span>ToC</span>";
      btnToc.addEventListener("click", function () {
        state.toc = !state.toc;
        apply(first(SECONDARY), state.toc);
        syncButtons(
          bar.querySelector("#jk-toggle-nav"),
          bar.querySelector("#jk-toggle-toc")
        );
        save();
      });
      bar.appendChild(btnToc);
    }

    syncButtons(btnNav, btnToc);
    buildMenu(bar);
  }

  function onKey(event) {
    var tag = (event.target && event.target.tagName) || "";
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(tag) || event.target.isContentEditable) return;
    var key = (event.key || "").toLowerCase();
    if (key !== "n" && key !== "t" && key !== "g" || event.ctrlKey || event.metaKey || event.altKey) return;
    var btnNav = document.getElementById("jk-toggle-nav");
    var btnToc = document.getElementById("jk-toggle-toc");
    if (key === "n") {
      state.nav = !state.nav;
      apply(first(PRIMARY), state.nav);
    } else if (key === "t") {
      state.toc = !state.toc;
      apply(first(SECONDARY), state.toc);
    } else {
      var btnMenu = document.getElementById("jk-toggle-menu");
      if (btnMenu) btnMenu.click();
      return;
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
