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
    # Source-table display ranges/blanks are not a reliable list of combat variants.
    # Pin the actual infobox levels before resolving these two known display gaps.
    resolution_pins={r['page']:r for r in json.loads((SOURCES/'npc-resolution-manifest.json').read_text(encoding='utf8'))}
    actual_levels={}
    for page in ['Farmer','Vyrewatch','Jelly']:
        pin=resolution_pins[page];raw=(SOURCES/pin['file']).read_bytes()
        assert sha256(raw).hexdigest()==pin['sha256'],page
        actual_levels[page]=set(re.findall(r'(?m)^\|combat\d*\s*=\s*(\d+)\s*$',raw.decode('utf8')))
    assert actual_levels['Farmer']=={'7'} and actual_levels['Vyrewatch']=={'87','105','110','120','125'}
    # These exact pre-2022 native667 identities are not the OSRS11241..11245 cave imports.
    legacy_jellies={1637,1638,1639,1640,1641,1642,7459,7460,8599,8617,10699}
    jelly_identities=json.loads((ROOT/'docs/CLUE/cache/legacy-jellies.json').read_text())
    assert set(map(int,jelly_identities))==legacy_jellies
    resolutions=[]
    lookup = defaultdict(list)
    for tier, rows in source.items():
        for row in rows:
            if row.get("method") == "combat" and row["fractions"] and row["page"] and "impling" not in row["page"].lower():
                if row['page'] in {'Farmer','Vyrewatch'}:
                    row={**row,'displayLevels':row['levels'],'levels':'; '.join(sorted(actual_levels[row['page']],key=int)),
                         'levelEvidence':resolution_pins[row['page']]}
                lookup[normalize(row["page"].split("#")[0])].append((tier, row))
    resolved, conflicts = [], []
    unmatched = []
    for npc, definition in npcs.items():
        by_tier = defaultdict(list)
        for tier, row in lookup[normalize(definition["name"])]:
            if npc in legacy_jellies and row['page']=='Jelly' and row['variant']=='Wilderness Slayer Cave':
                assert definition['level']==78 and definition['name']=='Jelly'
                assert definition==jelly_identities[str(npc)],'A pinned native Jelly changed; re-audit its source variant'
                resolutions.append(dict(npc=npc,**definition,source=resolution_pins['Jelly'],
                    choice='Regular1/128; original667actor predates cave variants11241..11245 released3Feb2022'))
                continue
            if str(definition["level"]) in re.findall(r"\d+", row["levels"]):
                by_tier[tier].append(row)
        for tier, rows in by_tier.items():
            fractions = {row["fractions"][0].replace(",", "") for row in rows}
            if len(fractions) != 1 or not re.fullmatch(r"1/\d+", next(iter(fractions))):
                conflicts.append(dict(npc=npc, **definition, tier=tier, variants=rows))
                continue
            denominator = int(next(iter(fractions)).split("/")[1])
            resolved.append(dict(npc=npc, **definition, tier=tier, denominator=denominator,
                                 sources=[dict(url=row["url"], variant=row["variant"], fractions=row["fractions"],
                                               **({'levelEvidence':row['levelEvidence']} if 'levelEvidence' in row else {})) for row in rows]))
    matched = {(row["tier"], src["url"], src["variant"]) for row in resolved for src in row["sources"]}
    conflicting = {(row["tier"], src["url"], src["variant"]) for row in conflicts for src in row["variants"]}
    for name, rows in lookup.items():
        for tier, row in rows:
            key = (tier, row["url"], row["variant"])
            if key in matched or key in conflicting:
                continue
            candidates = [dict(npc=npc, **definition) for npc, definition in npcs.items() if normalize(definition["name"]) == name]
            unmatched.append(dict(tier=tier, source=row,
                                  reason=("Source wilderness-cave variants11241..11245 absent; original667Jellies are regular" if row['page']=='Jelly' and row['variant']=='Wilderness Slayer Cave'
                                          else "Local combat level does not match this Wiki variant" if candidates else "NPC/variant absent from local cache"),
                                  candidates=candidates))
    (OUT / "npc-sources.json").write_text(json.dumps(resolved, indent=2) + "\n", encoding="utf-8")
    (ROOT / "docs/CLUE/NPC-SOURCE-CONFLICTS.json").write_text(json.dumps(conflicts, indent=2) + "\n", encoding="utf-8")
    (ROOT / "docs/CLUE/NPC-SOURCE-GAPS.json").write_text(json.dumps(unmatched, indent=2) + "\n", encoding="utf-8")
    (ROOT/'docs/CLUE/NPC-SOURCE-RESOLUTIONS.json').write_text(json.dumps(dict(
        sources=resolution_pins,resolutions=resolutions,actualLevels={k:sorted(v,key=int) for k,v in actual_levels.items()}),indent=2)+'\n')
    print(f"{len(set(r['npc'] for r in resolved))} NPCs, {len(resolved)} tier routes: {Counter(r['tier'] for r in resolved)}; conflicts {len(conflicts)}")


if __name__ == "__main__":
    main()
