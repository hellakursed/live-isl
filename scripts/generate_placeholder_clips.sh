#!/usr/bin/env bash
# Generate solid-color placeholder ISL clips (replace with real recordings later).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"
FS="$ASSETS/fingerspell"
VID="$ASSETS/videos"
mkdir -p "$FS" "$VID"

gen_clip() {
  local out="$1"
  local secs="${2:-0.55}"
  local color="${3:-0x2EC4B6}"
  if [[ -f "$out" && -s "$out" ]]; then
    return 0
  fi
  ffmpeg -y -hide_banner -loglevel error \
    -f lavfi -i "color=c=${color}:s=480x480:d=${secs}" \
    -c:v libx264 -pix_fmt yuv420p -movflags +faststart \
    "$out"
  echo "generated $(basename "$out")"
}

echo "Generating fingerspell A-Z, 0-9…"
for ch in A B C D E F G H I J K L M N O P Q R S T U V W X Y Z 0 1 2 3 4 5 6 7 8 9; do
  gen_clip "$FS/${ch}.mp4" 0.45 "0x1A7A72"
done

echo "Generating sample word clips…"
for w in HELLO GOODBYE YES NO THANK-YOU PLEASE WATER FOOD HELP I YOU WHAT WHERE HOW GOOD WANT NEED GO COME EAT DRINK; do
  gen_clip "$VID/${w}.mp4" 0.75 "0x0F2F3F"
done

echo "Done. Use scripts/import_clip.sh to replace with real ISL footage."
