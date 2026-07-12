#!/usr/bin/env bash
# Fetch the gated CISLR pack from Hugging Face and prepare an on-device pack.
#
# Prerequisites:
#   1. Create a Hugging Face account
#   2. Accept terms for https://huggingface.co/datasets/Exploration-Lab/CISLR
#   3. Create an access token (read) at https://huggingface.co/settings/tokens
#   4. export HF_TOKEN=hf_...
#
# Usage:
#   HF_TOKEN=hf_xxx ./scripts/fetch_cislr_pack.sh
#   HF_TOKEN=hf_xxx ./scripts/fetch_cislr_pack.sh --limit 500   # smaller pack
#   ./scripts/fetch_cislr_pack.sh   # reuse packs/cache zip if already downloaded
#
# Output:
#   packs/cislr/glosses.json
#   packs/cislr/videos/*.mp4
#   packs/cislr_pack.zip
#
# Install on a debug device:
#   ./scripts/install_cislr_pack_adb.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/packs/cislr"
LIMIT=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --limit) LIMIT="${2:-0}"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

ZIP_CACHE="$ROOT/packs/cache/CISLR_v1.5-a_videos.zip"
CSV_CACHE="$OUT/dataset.csv"
if [[ -z "${HF_TOKEN:-}" ]]; then
  if [[ -f "$ZIP_CACHE" && -f "$CSV_CACHE" ]]; then
    echo "HF_TOKEN unset — using cached CISLR zip + dataset.csv"
  else
    echo "ERROR: Set HF_TOKEN after accepting the CISLR dataset terms on Hugging Face."
    echo "  https://huggingface.co/datasets/Exploration-Lab/CISLR"
    exit 1
  fi
fi

mkdir -p "$OUT/videos" "$ROOT/packs/cache"
export HF_HUB_ENABLE_HF_TRANSFER=0
export PYTHONUNBUFFERED=1

python3 - <<PY
import csv, json, os, urllib.request, zipfile
from pathlib import Path

token = os.environ.get("HF_TOKEN", "")
limit = int("${LIMIT}")
out = Path(r"${OUT}")
cache = Path(r"${ROOT}") / "packs" / "cache"
videos_dir = out / "videos"
videos_dir.mkdir(parents=True, exist_ok=True)
cache.mkdir(parents=True, exist_ok=True)

base = "https://huggingface.co/datasets/Exploration-Lab/CISLR/resolve/main"
headers = {"Authorization": f"Bearer {token}", "User-Agent": "LiveISL-fetch/1.0"} if token else {}

def fetch(path: str, dest: Path, resume: bool = True, expected_min: int = 0):
    if dest.exists() and dest.stat().st_size >= max(expected_min, 1):
        print(f"cached {path} ({dest.stat().st_size} bytes)")
        return
    if not token:
        raise SystemExit(f"Need HF_TOKEN to download missing {path}")
    url = f"{base}/{path}"
    dest.parent.mkdir(parents=True, exist_ok=True)
    start = dest.stat().st_size if resume and dest.exists() else 0
    req_headers = dict(headers)
    if start > 0:
        req_headers["Range"] = f"bytes={start}-"
        print(f"resuming {path} from {start}…")
    else:
        print(f"Downloading {path}…")
    req = urllib.request.Request(url, headers=req_headers)
    with urllib.request.urlopen(req, timeout=600) as resp:
        mode = "ab" if start > 0 and resp.status == 206 else "wb"
        if mode == "wb":
            start = 0
        with open(dest, mode) as f:
            while True:
                chunk = resp.read(1024 * 1024)
                if not chunk:
                    break
                f.write(chunk)
                if f.tell() % (50 * 1024 * 1024) < 1024 * 1024:
                    print(f"  … {dest.name} {f.tell() / (1024*1024):.1f} MB")
    print("ok", path, dest.stat().st_size)

csv_path = out / "dataset.csv"
fetch("dataset.csv", csv_path, resume=False, expected_min=1000)

rows = list(csv.DictReader(csv_path.open(encoding="utf-8")))
print(f"rows={len(rows)} cols={list(rows[0].keys()) if rows else []}")

def pick(row, *keys):
    for k in keys:
        if k in row and row[k]:
            return row[k].strip()
    lower = {k.lower(): v for k, v in row.items()}
    for k in keys:
        if k.lower() in lower and lower[k.lower()]:
            return lower[k.lower()].strip()
    return None

by_word = {}
for row in rows:
    word = pick(row, "word", "Word", "label", "Label", "gloss", "Gloss", "class", "Class")
    vid = pick(row, "uid", "video", "Video", "file", "File", "filename", "Filename", "path", "Path", "youtube_id", "id")
    if not word or not vid:
        continue
    word = word.strip()
    if word.lower() in {"#n/a", "n/a", "na", "null", "none"}:
        continue
    if word not in by_word:
        by_word[word] = vid

# Everyday vocabulary first so limited packs / demos work.
priority = [
    "i", "me", "you", "need", "help", "how", "yes", "no", "good", "go", "home",
    "food", "water", "drink", "see", "work", "school", "time", "today", "phone",
    "sorry", "please", "friend", "love", "money", "india", "thank you",
]
prio_rank = {w: i for i, w in enumerate(priority)}
items = sorted(
    by_word.items(),
    key=lambda kv: (prio_rank.get(kv[0].lower(), 10_000), kv[0].lower()),
)
if limit > 0:
    items = items[:limit]
print(f"unique words={len(by_word)} selecting={len(items)}")

zip_path = cache / "CISLR_v1.5-a_videos.zip"
fetch("CISLR_v1.5-a_videos/CISLR_v1.5-a_videos.zip", zip_path, resume=True, expected_min=1_000_000_000)

print("Scanning zip…")
glosses = []
ok = 0
fail = 0
# duration lookup
dur_by_uid = {}
for row in rows:
    uid = pick(row, "uid")
    if not uid:
        continue
    try:
        dur_by_uid[uid] = max(500, int(float(pick(row, "duration") or 1.5) * 1000))
    except Exception:
        dur_by_uid[uid] = 1500

with zipfile.ZipFile(zip_path) as zf:
    members = {}
    for info in zf.infolist():
        if info.is_dir():
            continue
        name = Path(info.filename).name
        members[name] = info.filename
        stem = Path(name).stem
        members.setdefault(stem, info.filename)
        members.setdefault(stem + ".mp4", info.filename)
    print(f"zip members={len(zf.infolist())} indexed={len(members)}")

    for i, (word, vid) in enumerate(items, 1):
        gloss_id = "".join(ch if ch.isalnum() else "-" for ch in word.upper()).strip("-")
        if not gloss_id:
            continue
        dest = videos_dir / f"{gloss_id}.mp4"
        stem = Path(vid).stem if str(vid).endswith(".mp4") else str(vid)
        member = (
            members.get(f"{stem}.mp4")
            or members.get(stem)
            or members.get(Path(vid).name)
        )
        if not member:
            print("FAIL missing", word, vid)
            fail += 1
            continue
        if not dest.exists() or dest.stat().st_size < 1000:
            with zf.open(member) as src, open(dest, "wb") as dst:
                while True:
                    chunk = src.read(1024 * 1024)
                    if not chunk:
                        break
                    dst.write(chunk)
        ok += 1
        glosses.append({
            "glossId": gloss_id,
            "lemmas": [word.lower()],
            "hindi": [],
            "videoPath": f"videos/{gloss_id}.mp4",
            "durationMs": dur_by_uid.get(vid, 1500),
        })
        if i % 200 == 0 or i == len(items):
            print(f"progress {i}/{len(items)} ok={ok} fail={fail}")

(out / "glosses.json").write_text(json.dumps(glosses, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"wrote {len(glosses)} glosses; ok={ok} fail={fail}")
PY

echo "Zipping packs/cislr_pack.zip…"
rm -f "$ROOT/packs/cislr_pack.zip"
(
  cd "$ROOT/packs"
  zip -qr cislr_pack.zip cislr/glosses.json cislr/videos
)
echo "Done."
echo "  Pack dir: $OUT"
echo "  Zip:      $ROOT/packs/cislr_pack.zip"
ls -lh "$ROOT/packs/cislr_pack.zip" 2>/dev/null || true
echo "videos: $(find "$OUT/videos" -type f | wc -l | tr -d ' ')"
