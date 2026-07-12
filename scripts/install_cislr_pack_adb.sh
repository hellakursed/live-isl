#!/usr/bin/env bash
# Push packs/cislr into the debug app's filesDir.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACK="$ROOT/packs/cislr"
PKG="${1:-com.liveisl.app.debug}"
SERIAL="${2:-}"
ADB=(adb)
[[ -n "$SERIAL" ]] && ADB=(adb -s "$SERIAL")

[[ -f "$PACK/glosses.json" ]] || { echo "Missing $PACK — run fetch_cislr_pack.sh first"; exit 1; }
[[ -d "$PACK/videos" ]] || { echo "Missing $PACK/videos"; exit 1; }

TMP="/data/local/tmp/cislr_pack_fresh"
echo "Clearing old temp pack…"
"${ADB[@]}" shell "rm -rf $TMP /data/local/tmp/cislr_pack"
echo "Pushing pack ($(du -sh "$PACK" | awk '{print $1}'))…"
# Trailing /. copies contents into TMP instead of nesting as TMP/cislr
"${ADB[@]}" push "$PACK/." "$TMP/"

echo "Installing into app filesDir…"
"${ADB[@]}" shell "run-as $PKG sh -c '
  rm -rf files/sign_sources/cislr
  mkdir -p files/sign_sources/cislr
  cp -R $TMP/. files/sign_sources/cislr/
  echo glosses=\$(wc -c < files/sign_sources/cislr/glosses.json)
  echo videos=\$(ls files/sign_sources/cislr/videos | wc -l)
  ls files/sign_sources/cislr | head
'"
"${ADB[@]}" shell "rm -rf $TMP"
echo "Installed into $PKG files/sign_sources/cislr — open Settings → Video source → CISLR"
