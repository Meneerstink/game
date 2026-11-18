"""Extract pinned Wiki skilling base rates for activities implemented in this server.

RS2-only activities are deliberately not assigned a guessed OSRS equivalent.
"""
import json
import re
from pathlib import Path
from build_catalogue import ROOT, SOURCES, OUT

ACTIVITIES = {
    "woodcutting": ("Clue_nest", {
        "TREE": "Normal trees", "ACHEY": "Achey trees", "OAK": "Oak trees", "WILLOW": "Willow trees",
        "TEAK": "Teak trees", "MAPLE": "Maple trees", "HOLLOW": "Hollow trees", "MAHOGANY": "Mahogany trees",
        "YEW": "Yew trees", "MAGIC": "Magic trees",
    }),
    "mining": ("Clue_geode", {
        "CLAY": "Clay rocks", "COPPER": "Copper rocks", "TIN": "Tin rocks", "BLURITE": "Blurite rocks",
        "IRON": "Iron rocks", "SILVER": "Silver rocks", "COAL": "Coal rocks", "GOLD": "Gold rocks",
        "MITHRIL": "Mithril rocks", "ADAMANTITE": "Adamantite rocks", "RUNITE": "Runite rocks",
        "SANDSTONE": "Sandstone rocks", "GRANITE": "Granite rocks",
    }),
    "fishing": ("Clue_bottle", {
        "SHRIMP": "Shrimp", "ANCHOVIES": "Anchovies", "KARAMBWANJI": "Karambwanji", "SARDINE": "Sardine",
        "HERRING": "Herring", "MACKEREL": "Mackerel", "COD": "Cod", "BASS": "Bass", "TROUT": "Trout",
        "SALMON": "Salmon", "PIKE": "Pike", "RAINBOW_FISH": "Rainbow fish", "TUNA": "Tuna",
        "SWORDFISH": "Swordfish", "LOBSTER": "Lobsters", "CAVE_EEL": "Cave eels", "MONKFISH": "Monkfish",
        "RAW_KARAMBWAN": "Karambwan", "LEAPING_TROUT": "Leaping trout", "LEAPING_SALMON": "Leaping salmon",
        "LEAPING_STURGEON": "Leaping sturgeon", "SHARK": "Sharks", "CAVEFISH": "Cavefish",
    }),
}


def build():
    manifest = json.loads((SOURCES / "manifest.json").read_text())
    sources = {p.get("file"): p for p in manifest["pages"]}
    data = {}
    for skill, (page, mapping) in ACTIVITIES.items():
        raw = (SOURCES / (page + ".wiki")).read_text(encoding="utf-8")
        start = raw.index("===Base chance")
        end = raw.find("Clue tier===", start)
        section = raw[start:end] if end >= 0 else raw[start:]
        rates = {}
        for label, number in re.findall(r"\|\s*(\[\[[^\n]+)\n\|\s*([\d,]+)", section):
            label = re.sub(r"\[\[([^]|]+)\|([^]]+)]]", r"\2", label).replace("[[", "").replace("]]", "").strip()
            rates[label] = int(number.replace(",", ""))
        pin = sources[page + ".wiki"]
        data[skill] = {"source": pin["url"], "revision": pin["revision"], "sha256": pin["sha256"],
                       "activities": {key: rates[label] for key, label in mapping.items()}}
    (OUT / "skilling.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print("Sourced skilling activities:", {key: len(value["activities"]) for key, value in data.items()})


if __name__ == "__main__":
    build()
