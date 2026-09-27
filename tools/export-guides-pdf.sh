#!/usr/bin/env bash
# Export JKubeTerm guides (QuickStart, user guide, admin guide) to PDF
# via headless Chromium --print-to-pdf against a running `mkdocs serve`.
#
# Usage:
#   mkdocs serve &                              # http://127.0.0.1:8000
#   tools/export-guides-pdf.sh                  # writes docs/guides/*.pdf
#   tools/export-guides-pdf.sh --base-url http://127.0.0.1:8000 --out-dir docs/guides
#
# The PDFs land next to the sources (docs/guides/*.pdf) so the
# [*.pdf](*.pdf) links in the guides resolve both in `mkdocs serve`
# and in the built site. Generated PDFs are git-ignored build artifacts.
set -euo pipefail

BASE_URL="http://127.0.0.1:8000"
OUT_DIR="docs/guides"
CHROMIUM="/Applications/Chromium.app/Contents/MacOS/Chromium"
PAGES=(quickstart-minikube user-guide admin-guide)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --chromium) CHROMIUM="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--base-url URL] [--out-dir DIR] [--chromium PATH]"
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

if [[ ! -x "$CHROMIUM" ]]; then
  echo "Chromium not found at $CHROMIUM (use --chromium PATH)" >&2
  exit 1
fi
if ! curl -sf -m 5 "$BASE_URL/guides/user-guide/" -o /dev/null; then
  echo "Docs server not reachable at $BASE_URL — run 'mkdocs serve' first" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
for page in "${PAGES[@]}"; do
  url="$BASE_URL/guides/$page/"
  out="$OUT_DIR/$page.pdf"
  echo "Exporting $url -> $out"
  # CVDisplayLink errors on headless macOS are benign; PDF is still written.
  "$CHROMIUM" --headless --no-sandbox --disable-gpu \
    --print-to-pdf="$out" --print-to-pdf-no-header "$url" 2>/dev/null || true
  if [[ ! -s "$out" ]]; then
    echo "FAILED to write $out" >&2
    exit 1
  fi
  ls -la "$out"
done
echo "Done: ${PAGES[*]}"
