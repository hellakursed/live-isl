#!/usr/bin/env bash
# Import a real ISL recording into the asset pack.
# Usage: ./scripts/import_clip.sh GLOSS_ID /path/to/source.mp4
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GLOSS="${1:?gloss id required e.g. HELLO}"
SRC="${2:?source mp4 required}"
OUT_DIR="$ROOT/app/src/main/assets/videos"
# Fingerspell single chars go under fingerspell/
if [[ ${#GLOSS} -eq 1 ]]; then
  OUT_DIR="$ROOT/app/src/main/assets/fingerspell"
fi
mkdir -p "$OUT_DIR"
OUT="$OUT_DIR/${GLOSS}.mp4"
ffmpeg -y -hide_banner -loglevel error -i "$SRC" \
  -vf "scale=480:-2" -c:v libx264 -pix_fmt yuv420p -an -movflags +faststart \
  "$OUT"
echo "Imported $OUT"
