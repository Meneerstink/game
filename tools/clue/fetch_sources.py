"""Refresh read-only OSRS Wiki evidence for CLUE. Never opens a game cache.

Wiki content is CC BY-NC-SA 3.0; the manifest retains attribution and revisions.
RuneLite is a code/navigation aid only, not authority for gameplay or item IDs.
"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import json
import re
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/CLUE/sources"
PAGES = [
    "Treasure Trails", "Watson", "Transcript:Watson", "Strange casket",
    "Transcript:Strange casket", "Mimic", "The Mimic", "STASH", "Uri",
    "Falo the Bard", "Charlie the Tramp", "Clue nest", "Clue geode", "Clue bottle",
    "Scroll box", "Treasure Trails/Guide/Hot Cold", "Treasure Trails/Guide/Light boxes",
    "Treasure Trails/Guide/Puzzle boxes", "Treasure Trails/Guide/Challenge scrolls",
    "Treasure Trails/Full guide/All", "Treasure Trails/Guide/Anagrams",
    "Treasure Trails/Guide/Ciphers", "Treasure Trails/Guide/Coordinates",
    "Treasure Trails/Guide/Cryptic clues", "Treasure Trails/Guide/Emote clues",
    "Treasure Trails/Guide/Maps", "Sherlock/Elite", "Sherlock/Master",
] + [f"{kind} ({tier})" for kind in ("Clue scroll", "Reward casket")
     for tier in ("beginner", "easy", "medium", "hard", "elite", "master")]


def fetch(page):
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode({
        "action": "query", "titles": page, "prop": "revisions",
        "rvprop": "ids|timestamp|content", "rvslots": "main", "format": "json",
        "formatversion": 2,
    })
    req = urllib.request.Request(url, headers={"User-Agent": "78legacy-ClueResearch/1.0"})
    with urllib.request.urlopen(req, timeout=40) as response:
        obj = json.load(response)["query"]["pages"][0]
    if "missing" in obj:
        return {"page": page, "missing": True}
    revision = obj["revisions"][0]
    raw = revision["slots"]["main"]["content"].encode("utf-8")
    filename = page.replace("/", "__").replace(":", "_").replace(" ", "_") + ".wiki"
    (OUT / filename).write_bytes(raw)
    return {"page": obj["title"], "revision": revision["revid"],
            "revision_time": revision["timestamp"], "file": filename,
            "sha256": sha256(raw).hexdigest(),
            "url": "https://oldschool.runescape.wiki/w/" + urllib.parse.quote(obj["title"].replace(" ", "_"))}


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(fetch, PAGES))
    children = set()
    for row in rows:
        if "file" not in row:
            continue
        raw = (OUT / row["file"]).read_text(encoding="utf-8")
        children.update(re.findall(r"\{\{:((?:Treasure Trails/Guide/|Sherlock/)[^}\n]+)", raw))
    with ThreadPoolExecutor(max_workers=4) as pool:
        rows.extend(pool.map(fetch, sorted(children - set(PAGES))))
    (OUT / "manifest.json").write_text(json.dumps({
        "fetched_utc": datetime.now(timezone.utc).isoformat(),
        "license": "OSRS Wiki CC BY-NC-SA 3.0; attribution in each source URL",
        "pages": rows,
    }, indent=2) + "\n", encoding="utf-8")
    print(f"Saved {sum('file' in row for row in rows)} wiki pages to {OUT}")
    print("Missing:", [row["page"] for row in rows if row.get("missing")])
