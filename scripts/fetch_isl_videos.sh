#!/usr/bin/env bash
# Download real ISL clips from https://github.com/vikasops/ISL (educational dataset).
# Usage: ./scripts/fetch_isl_videos.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "Re-run the Python fetch in scripts if you need to refresh clips."
echo "Videos live in: $ROOT/app/src/main/assets/videos"
echo "Source: vikasops/ISL (takingdata/ISL) — scraped from indiansignlanguage.org"
ls "$ROOT/app/src/main/assets/videos" | wc -l
