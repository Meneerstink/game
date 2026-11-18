"""Fetch actual Wiki clue pages, including source map coordinates and equipment.

Uses Category:Clue scrolls rather than assuming a donor's tier classification.
"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
import json
import urllib.parse
import urllib.request
import urllib.error
import time
from fetch_sources import save_source, OUT


def query(params):
    req = urllib.request.Request("https://oldschool.runescape.wiki/api.php?" +
        urllib.parse.urlencode(dict(params, format="json")), headers={"User-Agent": "78legacy-ClueResearch/1.0"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=40) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 2:
                raise
            delay = min(30, int(error.headers.get("Retry-After", 15)))
            print(f"Wiki rate limit: respecting Retry-After ({delay}s)", flush=True)
            time.sleep(delay)


if __name__ == "__main__":
    pages, continuation = [], {}
    while True:
        obj = query(dict(action="query", list="categorymembers", cmtitle="Category:Clue scrolls",
                         cmtype="page", cmlimit=500, **continuation))
        pages.extend(row["title"] for row in obj["query"]["categorymembers"] if row["title"].startswith("Clue scroll ("))
        continuation = obj.get("continue")
        if not continuation:
            break
    print(f"Found {len(pages)} Wiki clue pages", flush=True)
    rows = []
    # A single API call can retrieve multiple revisions. Keep a modest request
    # rate instead of making hundreds of individual requests to the community Wiki.
    for start in range(0, len(pages), 15):
        batch = pages[start:start + 15]
        obj = query(dict(action="query", titles="|".join(batch), prop="revisions",
                         rvprop="ids|timestamp|content", rvslots="main", redirects=1, formatversion=2))
        rows.extend(save_source(page) for page in obj["query"]["pages"])
        print(f"Saved {len(rows)}/{len(pages)} clue pages", flush=True)
        (OUT / "step-manifest.json").write_text(json.dumps({
            "fetched_utc": datetime.now(timezone.utc).isoformat(),
            "license": "OSRS Wiki CC BY-NC-SA 3.0; attribution in each source URL", "pages": rows,
        }, indent=2) + "\n", encoding="utf-8")
        time.sleep(1)
    (OUT / "step-manifest.json").write_text(json.dumps({
        "fetched_utc": datetime.now(timezone.utc).isoformat(),
        "license": "OSRS Wiki CC BY-NC-SA 3.0; attribution in each source URL", "pages": rows,
    }, indent=2) + "\n", encoding="utf-8")
    print("Saved", sum("file" in row for row in rows), "source pages", flush=True)
