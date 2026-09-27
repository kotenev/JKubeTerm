#!/usr/bin/env bash
# Export JKubeTerm training modules (01-12) to PDF via headless
# Chromium --print-to-pdf against a running `mkdocs serve`.
#
# Usage:
#   mkdocs serve &                              # http://127.0.0.1:8000
#   tools/export-training-pdf.sh                # writes docs/training/*.pdf
#   tools/export-training-pdf.sh --pages 04-workloads,05-services-ingress
#
# Same technique as tools/export-guides-pdf.sh. Generated PDFs are
# git-ignored build artifacts (see .gitignore docs/training/*.pdf).
set -euo pipefail

BASE_URL="http://127.0.0.1:8000"
OUT_DIR="docs/training"
CHROMIUM="/Applications/Chromium.app/Contents/MacOS/Chromium"
ALL_PAGES=(index 01-basics 02-cluster 03-access 04-workloads 05-services-ingress 06-config-storage 07-advanced-workloads 08-observability 09-helm-gitops 10-security 11-troubleshooting 12-demo-apps)
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
if ! curl -sf -m 5 "$BASE_URL/training/" -o /dev/null; then
  echo "Docs server not reachable at $BASE_URL — run 'mkdocs serve' first" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
for page in "${PAGES[@]}"; do
  url="$BASE_URL/training/$page/"
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
