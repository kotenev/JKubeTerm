/*
 * Initialises Mermaid for the Material for MkDocs theme (including instant
 * navigation, where document$ fires for every page change).
 */
(function () {
  "use strict";
  var configured = false;
  function render() {
    if (!window.mermaid) {
      return;
    }
    if (!configured) {
      window.mermaid.initialize({
        startOnLoad: false,
        theme: "dark",
        securityLevel: "loose",
        flowchart: { htmlLabels: true },
        sequence: {
          actorFontFamily: "inherit",
          noteFontFamily: "inherit",
          messageFontFamily: "inherit"
        }
      });
      configured = true;
    }
    window.mermaid.run({ querySelector: "div.mermaid, pre.mermaid" });
  }
  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(render);
  } else {
    document.addEventListener("DOMContentLoaded", render);
  }
})();
