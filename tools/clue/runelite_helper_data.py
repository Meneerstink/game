"""CLUE-QH (owner 18-11 00:3x / 02:0x): the RuneLite Clue Scroll plugin's solution data for OUR clue steps.

Reads the RuneLite cluescrolls sources (BSD-2, reference copy C:/RSPS/reference/runelite-cluescrolls: AnagramClue, CipherClue,
CrypticClue, EmoteClue, CoordinateClue, MapClue, FaloTheBardClue) and writes data/cfg/clue/helper.json: per steps.json key the
plugin's overlay title, npc, area/location text, solution/directions and challenge answer. Matched on the clue text first,
then on the exact world point. Nothing is invented: a step without a RuneLite entry gets no row (the helper then shows only
our own hint + arrow) and is listed as UNMATCHED.

Usage: python -I tools/clue/runelite_helper_data.py [runelite-dir]
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RL = sys.argv[1] if len(sys.argv) > 1 else "C:/RSPS/reference/runelite-cluescrolls"


def read(name):
    with open(os.path.join(RL, name + ".java"), encoding="utf-8") as f:
        return f.read()


def jstr(s):
    return s.encode("utf-8").decode("unicode_escape").encode("latin-1").decode("utf-8") if "\\" in s else s


STR = r'"((?:[^"\\]|\\.)*)"'
POINT = r'new WorldPoint\((\d+),\s*(\d+),\s*(\d+)\)'


def norm(text):
    text = re.sub(r"<[^>]+>", " ", text or "")
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def builders(src, cls):
    """Every Cls.builder() ... .build() block as a dict of its calls (first string / point argument)."""
    out = []
    for block in re.findall(cls + r"\.builder\(\)(.*?)\.build\(\)", src, re.S):
        row = {}
        for call, arg in re.findall(r"\.(\w+)\(((?:[^()]|\([^()]*\))*)\)", block):
            m = re.search(POINT, arg)
            if m:
                row[call] = tuple(int(v) for v in m.groups())
                continue
            m = re.search(STR, arg)
            if m:
                row[call] = jstr(m.group(1))
        out.append(row)
    return out


def constants(src):
    return {k: jstr(v) for k, v in re.findall(r"static final String (\w+)\s*=\s*" + STR, src)}


def rows():
    found = []
    for r in builders(read("AnagramClue"), "AnagramClue"):
        found.append(dict(title="Anagram Clue", text=r.get("text"), point=r.get("location"), npc=r.get("npc"),
                          area=r.get("area"), answer=r.get("answer"), question=r.get("question")))
    for r in builders(read("CipherClue"), "CipherClue"):
        found.append(dict(title="Cipher Clue", text=r.get("text"), point=r.get("location"), npc=r.get("npc"),
                          area=r.get("area"), answer=r.get("answer"), question=r.get("question")))
    for r in builders(read("CrypticClue"), "CrypticClue"):
        found.append(dict(title="Cryptic Clue", text=r.get("text"), point=r.get("location"), npc=r.get("npc"),
                          solution=r.get("solution"), answer=r.get("answer"), question=r.get("question")))
    for r in builders(read("CoordinateClue"), "CoordinateClue"):
        found.append(dict(title="Coordinate Clue", text=None, point=r.get("location"), solution=r.get("directions")))
    src = read("MapClue")
    const = constants(src)
    for m in re.finditer(r"new MapClue\(([^;]*?)\)\s*[,)]\s*\n", src):
        args = m.group(1)
        p = re.search(POINT, args)
        if not p:
            continue
        tail = args[p.end():]
        s = re.findall(STR, tail)
        desc = jstr(s[-1]) if s else next((const[c] for c in re.findall(r"\b([A-Z][A-Z0-9_]+)\b", tail) if c in const), None)
        found.append(dict(title="Map Clue", text=None, point=tuple(int(v) for v in p.groups()), solution=desc))
    src = read("EmoteClue")
    for m in re.finditer(r"new EmoteClue\([^,]+,\s*" + STR + r",\s*" + STR + r",\s*\w+,\s*" + POINT, src):
        found.append(dict(title="Emote Clue", text=jstr(m.group(1)), area=jstr(m.group(2)),
                          point=(int(m.group(3)), int(m.group(4)), int(m.group(5)))))
    return found


def main():
    steps = json.load(open(os.path.join(ROOT, "data/cfg/clue/steps.json"), encoding="utf-8"))
    rl = rows()
    by_text = {}
    by_point = {}
    for r in rl:
        if r.get("text"):
            by_text.setdefault(norm(r["text"]), []).append(r)
        if r.get("point"):
            by_point.setdefault(r["point"], []).append(r)
    out, unmatched = [], []
    for s in steps:
        hint = s["hint"]
        # anagram / cipher scrolls carry the puzzle after the colon; the RuneLite text is only that part.
        puzzle = hint.split(":", 1)[1] if s["kind"] in ("ANAGRAM", "CIPHER") and ":" in hint else hint
        cands = by_text.get(norm(puzzle)) or by_text.get(norm(hint)) or []
        if not cands:
            pts = [tuple(p) for p in s.get("points") or []]
            title = {"COORDINATE": "Coordinate Clue", "MAP": "Map Clue", "EMOTE": "Emote Clue"}.get(s["kind"])
            cands = [r for p in pts for r in by_point.get(p, []) if title is None or r["title"] == title]
            if not cands and title:
                # Wiki and RuneLite name a dig tile up to a tile apart (Draynor bank 3092,3226 vs 3091,3227): nearest within 2.
                near = [(max(abs(p[0] - q[0]), abs(p[1] - q[1])), r) for p in pts for q, rs in by_point.items()
                        if q[2] == p[2] for r in rs if r["title"] == title]
                near = sorted((d, i, r) for i, (d, r) in enumerate(near) if d <= 2)
                cands = [near[0][2]] if near else []
        if not cands:
            unmatched.append(s["key"])
            continue
        r = cands[0]
        row = {"key": s["key"], "title": r["title"]}
        for f in ("npc", "area", "solution", "answer", "question"):
            if r.get(f):
                row[f] = r[f]
        # Emote steps: the emote(s) and items come from OUR steps.json (what the server checks), like RuneLite's overlay lists them.
        ch = s.get("challenge") or {}
        if s["kind"] == "EMOTE":
            row["emotes"] = [e for e in (ch.get("emote"), ch.get("secondEmote")) if e]
            row["items"] = [" / ".join(alt["name"] for alt in group) for group in s.get("equipment") or [] if group]
        out.append(row)
    dest = os.path.join(ROOT, "data/cfg/clue/helper.json")
    with open(dest, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"source": "RuneLite cluescrolls (BSD-2) " + RL, "rows": out}, f, indent=1, ensure_ascii=False)
        f.write("\n")
    print("RUNELITE rows=%d  STEPS=%d  MATCHED=%d  UNMATCHED=%d -> %s" % (len(rl), len(steps), len(out), len(unmatched), dest))
    for k in unmatched:
        print("UNMATCHED " + k)


main()
