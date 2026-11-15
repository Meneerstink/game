"""Match clue acquisition rows to read-only local cache NPC identities.

A match requires the same name AND combat level and agreement between every
matching Wiki variant. Conflicting variants are reported, never guessed.
"""
from collections import defaultdict, Counter
from hashlib import sha256
from pathlib import Path
import json
import re
import sys
from build_catalogue import ROOT, OUT, SOURCES, TARGET_CACHE


def normalize(name):
    return re.sub(r"[^a-z0-9]", "", name.lower())


def main():
    sys.path.insert(0, "C:/RSPS/tools/cache667")
    from js5 import Cache
    from local_npcs import decode
    cache = Cache(str(TARGET_CACHE))
    npcs = {}
    errors = []
    for group, files in cache.reference(18).items():
        for file in files:
            raw = cache.file(18, group, file)
            if not raw:
                continue
            row = decode(raw)
            npc = (group << 7) | file
            if row.get("stopped_at"):
                errors.append([npc, row.get("name"), row["stopped_at"]])
            elif row.get("name") and "attack" in [str(x).lower() for x in row["ops"]]:
                npcs[npc] = dict(name=row["name"], level=row.get("level"), sha256=sha256(raw).hexdigest())
    cache.dat.close()
    assert not errors, f"NPC decoder gaps: {errors[:10]}"
    source = json.loads((OUT / "acquisition-sources.json").read_text())
    lookup = defaultdict(list)
    for tier, rows in source.items():
        for row in rows:
            if row.get("method") == "combat" and row["fractions"] and row["page"] and "impling" not in row["page"].lower():
                lookup[normalize(row["page"].split("#")[0])].append((tier, row))
    resolved, conflicts = [], []
    unmatched = []
    for npc, definition in npcs.items():
        by_tier = defaultdict(list)
        for tier, row in lookup[normalize(definition["name"])]:
            if str(definition["level"]) in re.findall(r"\d+", row["levels"]):
                by_tier[tier].append(row)
        for tier, rows in by_tier.items():
            fractions = {row["fractions"][0].replace(",", "") for row in rows}
            if len(fractions) != 1 or not re.fullmatch(r"1/\d+", next(iter(fractions))):
                conflicts.append(dict(npc=npc, **definition, tier=tier, variants=rows))
                continue
            denominator = int(next(iter(fractions)).split("/")[1])
            resolved.append(dict(npc=npc, **definition, tier=tier, denominator=denominator,
                                 sources=[dict(url=row["url"], variant=row["variant"], fractions=row["fractions"]) for row in rows]))
    matched = {(row["tier"], src["url"], src["variant"]) for row in resolved for src in row["sources"]}
    conflicting = {(row["tier"], src["url"], src["variant"]) for row in conflicts for src in row["variants"]}
    for name, rows in lookup.items():
        for tier, row in rows:
            key = (tier, row["url"], row["variant"])
            if key in matched or key in conflicting:
                continue
            candidates = [dict(npc=npc, **definition) for npc, definition in npcs.items() if normalize(definition["name"]) == name]
            unmatched.append(dict(tier=tier, source=row,
                                  reason="Local combat level does not match this Wiki variant" if candidates else "NPC/variant absent from local cache",
                                  candidates=candidates))
    (OUT / "npc-sources.json").write_text(json.dumps(resolved, indent=2) + "\n", encoding="utf-8")
    (ROOT / "docs/CLUE/NPC-SOURCE-CONFLICTS.json").write_text(json.dumps(conflicts, indent=2) + "\n", encoding="utf-8")
    (ROOT / "docs/CLUE/NPC-SOURCE-GAPS.json").write_text(json.dumps(unmatched, indent=2) + "\n", encoding="utf-8")
    print(f"{len(set(r['npc'] for r in resolved))} NPCs, {len(resolved)} tier routes: {Counter(r['tier'] for r in resolved)}; conflicts {len(conflicts)}")


if __name__ == "__main__":
    main()
