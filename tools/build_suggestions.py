#!/usr/bin/env python3
"""Builds app/src/main/assets/suggestions.json from the human-friendly tools/suggestions/*.txt files.

Format:
  @ tag1,tag2            -> default interests for the following lines
  B T O R | Title | Description [| extra,tags]
    B = budget   S (<20 €), M (20-50 €), L (50-150 €), P (>150 €)
    T = type     O objet, E expérience, H fait main, A abonnement
    O = occasions: letters among B N X V M F C (commas allowed), or * for any
        (B anniversaire, N fête, X Noël, V Saint-Valentin, M fête des mères, F fête des pères, C autre)
    R = relations: comma list among pa pr ch si gp fa fr co, or * for any
"""
import glob, hashlib, json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUDGET = {"S": "SMALL", "M": "MEDIUM", "L": "LARGE", "P": "PREMIUM"}
TYPE = {"O": "OBJECT", "E": "EXPERIENCE", "H": "HANDMADE", "A": "SUBSCRIPTION"}
OCC = {"B": "BIRTHDAY", "N": "NAME_DAY", "X": "CHRISTMAS", "V": "VALENTINE", "M": "MOTHERS_DAY", "F": "FATHERS_DAY", "C": "CUSTOM"}
REL = {"pa": "PARTNER", "pr": "PARENT", "ch": "CHILD", "si": "SIBLING", "gp": "GRANDPARENT", "fa": "FAMILY", "fr": "FRIEND", "co": "COLLEAGUE"}

def main():
    out, seen = [], set()
    for path in sorted(glob.glob(os.path.join(ROOT, "tools", "suggestions", "*.txt"))):
        interests = []
        for n, raw in enumerate(open(path, encoding="utf-8"), 1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("@"):
                interests = [t.strip() for t in line[1:].split(",") if t.strip() and t.strip() != "*"]
                continue
            parts = [p.strip() for p in line.split("|")]
            if len(parts) < 3:
                sys.exit(f"{path}:{n}: malformed line")
            codes = parts[0].split()
            if len(codes) != 4:
                sys.exit(f"{path}:{n}: expected 4 codes")
            b, t, o, r = codes
            tags = list(interests) + ([x.strip() for x in parts[3].split(",")] if len(parts) > 3 else [])
            title = parts[1]
            key = title.lower()
            if key in seen:
                sys.exit(f"{path}:{n}: duplicate '{title}'")
            seen.add(key)
            out.append({
                "id": hashlib.sha1(key.encode()).hexdigest()[:12],
                "title": title,
                "description": parts[2],
                "interests": sorted(set(tags)),
                "budget": BUDGET[b],
                "occasions": [] if o == "*" else [OCC[c] for c in o if c != ","],
                "type": TYPE[t],
                "relations": [] if r == "*" else [REL[x] for x in r.split(",")],
            })
    dest = os.path.join(ROOT, "app", "src", "main", "assets", "suggestions.json")
    with open(dest, "w", encoding="utf-8") as f:
        json.dump({"version": len(out), "items": out}, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(out)} suggestions -> {dest}")

if __name__ == "__main__":
    main()
