#!/usr/bin/env python3
"""Validate glosses.json and optionally merge a CSV of lemma,glossId,videoPath."""
import argparse
import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DICT = ROOT / "app/src/main/assets/dictionary/glosses.json"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--merge-csv")
    args = ap.parse_args()
    data = json.loads(DICT.read_text(encoding="utf-8"))
    if args.merge_csv:
        by_id = {e["glossId"]: e for e in data}
        with open(args.merge_csv, newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                gid = row["glossId"]
                entry = by_id.get(gid, {"glossId": gid, "lemmas": [], "hindi": [], "videoPath": None, "durationMs": 800})
                if row.get("lemma"):
                    lemmas = set(entry.get("lemmas", []))
                    lemmas.add(row["lemma"].lower())
                    entry["lemmas"] = sorted(lemmas)
                if row.get("videoPath"):
                    entry["videoPath"] = row["videoPath"]
                by_id[gid] = entry
        data = list(by_id.values())
        DICT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Merged → {len(data)} glosses")
    if args.check or not args.merge_csv:
        ids = [e["glossId"] for e in data]
        print(f"glosses: {len(ids)}")
        print(f"unique: {len(set(ids))}")
        dupes = len(ids) - len(set(ids))
        if dupes:
            raise SystemExit(f"duplicate glossIds: {dupes}")
        print("OK")


if __name__ == "__main__":
    main()
