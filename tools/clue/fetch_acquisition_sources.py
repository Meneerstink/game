"""Snapshot Wiki item-source tables for every tier, including NPC variants and rates."""
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import gzip
import json
import urllib.parse
import urllib.request
from bs4 import BeautifulSoup
from build_catalogue import OUT, SOURCES, TIERS


if __name__ == "__main__":
    tiers, manifest = {}, []
    for tier in TIERS:
        page = f"Clue scroll ({tier})"
        url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(dict(
            action="parse", page=page, prop="text", format="json", formatversion=2))
        req = urllib.request.Request(url, headers={"User-Agent": "78legacy-ClueResearch/1.0"})
        with urllib.request.urlopen(req, timeout=40) as response:
            result = json.load(response)["parse"]
        raw = result["text"].encode()
        soup = BeautifulSoup(raw, "html.parser")
        rows = []
        for table in soup.select("table.item-drops"):
            for row in table.select("tr"):
                cells = row.find_all("td", recursive=False)
                if len(cells) < 4:
                    continue
                link = cells[0].find("a", href=True)
                variant = cells[0].select_one(".beast-version")
                fractions = [entry["data-drop-fraction"] for entry in cells[3].select("[data-drop-fraction]")]
                rows.append(dict(name=cells[0].get_text(" ", strip=True),
                                 page=link.get("title") if link else None,
                                 url="https://oldschool.runescape.wiki" + link["href"] if link else None,
                                 variant=variant.get_text(" ", strip=True) if variant else None,
                                 levels=cells[1].get_text(" ", strip=True),
                                 quantity=cells[2].get_text(" ", strip=True),
                                 fractions=fractions, rarity=cells[3].get_text(" ", strip=True)))
        tiers[tier] = rows
        filename = f"acquisition-{tier}.html.gz"
        (SOURCES / filename).write_bytes(gzip.compress(raw, mtime=0))
        manifest.append(dict(page=page, revision=result.get("revid"), file=filename,
                             html_sha256=sha256(raw).hexdigest(), rows=len(rows), url=url))
        print(tier, len(rows), "acquisition rows", flush=True)
    (OUT / "acquisition-sources.json").write_text(json.dumps(tiers, indent=2) + "\n", encoding="utf-8")
    (SOURCES / "acquisition-manifest.json").write_text(json.dumps(dict(
        fetched_utc=datetime.now(timezone.utc).isoformat(),
        license="OSRS Wiki CC BY-NC-SA 3.0", note="Drop-source transclusions are a snapshot at fetch time, including on old article revisions.",
        pages=manifest), indent=2) + "\n", encoding="utf-8")
