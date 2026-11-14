"""Extract source clue fields and verify map-square presence without writing caches.

An existing map square alone is not proof of reachability or current OSRS layout;
those remain explicit runtime checks. Missing map squares are listed per clue.
"""
from collections import Counter
from hashlib import sha256
from pathlib import Path
import html
import json
import re
import sys
from build_catalogue import ROOT, OUT, SOURCES, TARGET_CACHE, split_template


def templates(raw, name):
    for match in re.finditer(r"\{\{" + re.escape(name) + r"(?=\s*\||\s*\n)", raw, re.I):
        start, pos, depth = match.start(), match.start(), 0
        while pos < len(raw):
            if raw[pos:pos+2] == "{{":
                depth += 1; pos += 2
            elif raw[pos:pos+2] == "}}":
                depth -= 1; pos += 2
                if depth == 0:
                    yield {k.strip(): v.strip() for part in split_template(raw[start+2:pos-2])[1:]
                           if "=" in part for k, v in [part.split("=", 1)]}
                    break
            else:
                pos += 1


def text(raw):
    raw = re.sub(r"\[\[([^][|]+)\|([^][]+)]]", r"\2", raw)
    raw = re.sub(r"\[\[([^][]+)]]", r"\1", raw)
    raw = re.sub(r"<br\s*/?>", "\n", raw, flags=re.I)
    raw = re.sub(r"''+", "", raw)
    return html.unescape(raw).strip()


def map_points(raw):
    maps = list(templates(raw, "Map"))
    # Positional coordinates do not appear in the key/value dictionary.
    positional = re.findall(r"\{\{Map\|\s*(\d+)\s*,\s*(\d+)([^{}]*?)}}", raw, re.I)
    out = []
    for x, y, rest in positional:
        plane = re.search(r"\|plane=(\d+)", rest)
        out.append([int(x), int(y), int(plane[1]) if plane else 0])
    for x, y in re.findall(r"\|x:(\d+),y:(\d+)", raw):
        plane = re.search(r"\|plane=(\d+)", raw)
        out.append([int(x), int(y), int(plane[1]) if plane else 0])
    for fields in maps:
        if "x" in fields and "y" in fields and fields["x"].isdigit() and fields["y"].isdigit():
            out.append([int(fields["x"]), int(fields["y"]), int(fields.get("plane", 0))])
    return list(dict.fromkeys(map(tuple, out)))


def name_hash(name):
    h = 0
    for char in name.lower():
        h = (h * 31 + ord(char)) & 0xffffffff
    return h if h < 0x80000000 else h - 0x100000000


def main():
    sys.path.insert(0, "C:/RSPS/tools/cache667")
    from js5 import Cache
    cache = Cache(str(TARGET_CACHE))
    cache.reference(5)
    names = cache.names[5]
    assert name_hash("m50_50") in names, "Map name hashing must be verified on existing Lumbridge terrain"
    manifest = json.loads((SOURCES / "step-manifest.json").read_text(encoding="utf-8"))
    rows, excluded, unresolved = [], [], []
    for source in manifest["pages"]:
        if "file" not in source:
            unresolved.append(dict(page=source["page"], reason="Missing source page")); continue
        raw = (SOURCES / source["file"]).read_text(encoding="utf-8")
        # Decode by source bytes, with attribution; never derive gameplay from the title alone.
        assert sha256(raw.encode()).hexdigest() == source["sha256"], source["file"]
        info = list(templates(raw, "Clue info"))
        if not info:
            continue  # generic tier pages, no individual step
        infobox = next(templates(raw, "Infobox Item"), {})
        tier = re.search(r"\((beginner|easy|medium|hard|elite|master)\)", source["page"])
        if not tier:
            unresolved.append(dict(page=source["page"], reason="Source has no tier")); continue
        for fields in info:
            kind = "UNKNOWN"
            if fields.get("emote"):
                kind = "EMOTE"
            else:
                for token, label in [("coordinate clue", "COORDINATE"), ("anagram", "ANAGRAM"),
                                     ("cipher", "CIPHER"), ("map clue", "MAP"), ("cryptic clue", "CRYPTIC")]:
                    if token in raw[:raw.find("==Clue info==")].lower():
                        kind = label; break
            points = map_points(fields.get("map", ""))
            if kind == "UNKNOWN":
                if "[[Cecilia]]" in fields["notes"] and "[[music]]" in fields["notes"]:
                    kind = "MUSIC"
                elif "[[Hot and cold clues]]" in fields["notes"]:
                    kind = "HOT_COLD"
                elif tier[1] == "beginner" and re.match(r"^(Bow|Panic|Spin)\b", fields["text"]):
                    kind = "EMOTE"
                    fields["emote"] = fields["text"].split()[0]
            if kind == "CRYPTIC" and not points and re.search(r"\b[Kk]ill\b", fields["notes"]):
                kind = "KILL"
            missing = []
            for x, y, plane in points:
                square = f"m{x >> 6}_{y >> 6}"
                if name_hash(square) not in names:
                    missing.append(square)
            row = dict(key=source["page"], tier=tier[1], kind=kind,
                       hint=text(fields["text"]), points=points,
                       osrsScrollIds=[int(n) for n in re.findall(r"\d+", infobox.get("id", ""))],
                       source=source["url"], sourceFile=source["file"], revision=source["revision"],
                       fields=fields, missingSquares=sorted(set(missing)),
                       eventSpecific="(Twisted League)" in source["page"])
            rows.append(row)
            if missing:
                excluded.append(row)
            if (not points and kind not in ("KILL",)) or kind == "UNKNOWN":
                unresolved.append(dict(page=source["page"], reason=f"kind={kind}, map points={len(points)}"))
    cache.dat.close()
    # Research archive only. The owner selected about 70 runtime steps, not this full inventory.
    (ROOT / "docs/CLUE/steps-source.json").write_text(json.dumps(rows, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (ROOT / "docs/CLUE/EXCLUDED-AREAS.tsv").write_text(
        "# Missing local map squares; existing square is not yet runtime proof\n" +
        "tier\tclue\tmissing map squares\tsource\n" +
        "".join(f"{r['tier']}\t{r['key']}\t{','.join(r['missingSquares'])}\t{r['source']}\n" for r in excluded), encoding="utf-8")
    (ROOT / "docs/CLUE/STEP-SOURCE-GAPS.json").write_text(json.dumps(unresolved, indent=2) + "\n", encoding="utf-8")
    print("Individual source steps", len(rows), Counter(r["tier"] for r in rows))
    print("Kinds", Counter(r["kind"] for r in rows))
    print("Missing squares", len(excluded), "source gaps", len(unresolved))


if __name__ == "__main__":
    main()
