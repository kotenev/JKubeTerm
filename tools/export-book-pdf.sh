#!/usr/bin/env bash
# Export the Best Practices book (docs/best-practices/) to PDF via headless
# Chromium --print-to-pdf against a running `mkdocs serve`.
#
# Usage:
#   mkdocs serve &                              # http://127.0.0.1:8000
#   tools/export-book-pdf.sh                    # writes docs/best-practices/*.pdf
#   tools/export-book-pdf.sh --pages index,01-pod
#
# Same technique as tools/export-guides-pdf.sh. Generated PDFs are
# git-ignored build artifacts (see .gitignore docs/best-practices/*.pdf).
set -euo pipefail

BASE_URL="http://127.0.0.1:8000"
OUT_DIR="docs/best-practices"
CHROMIUM="/Applications/Chromium.app/Contents/MacOS/Chromium"
ALL_PAGES=(index 01-pod 02-deployment 03-statefulset 04-daemonset 05-service 06-configmap 07-job 08-cronjob 09-ingress 10-pvc 11-events 12-node 13-namespace 14-pv)
PAGES=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --chromium) CHROMIUM="$2"; shift 2 ;;
    --pages) IFS=',' read -ra PAGES <<< "$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--base-url URL] [--out-dir DIR] [--chromium PATH] [--pages a,b,c]"
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

if [[ ${#PAGES[@]} -eq 0 ]]; then
  PAGES=("${ALL_PAGES[@]}")
fi

if [[ ! -x "$CHROMIUM" ]]; then
  echo "Chromium not found at $CHROMIUM (use --chromium PATH)" >&2
  exit 1
fi
if ! curl -sf -m 5 "$BASE_URL/best-practices/" -o /dev/null; then
  echo "Docs server not reachable at $BASE_URL — run 'mkdocs serve' first" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
for page in "${PAGES[@]}"; do
  url="$BASE_URL/best-practices/$page/"
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
