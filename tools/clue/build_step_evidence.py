"""Keep the selected source DB rows and exact source/native search placements.

Inputs are produced by read-only Java probes. No runtime catalogue or cache is
changed. A DB reference proves identity, not that the server implements a step.
"""
from hashlib import sha256
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]


def main():
    raw = json.loads((ROOT/'build/clue/db-rows.json').read_text())
    steps = json.loads((ROOT/'data/cfg/clue/steps.json').read_text())
    wiki = {r['key']: r for r in json.loads((ROOT/'docs/CLUE/steps-source.json').read_text())}
    tables = {'ANAGRAM': 4, 'MAP': 5, 'CIPHER': 6, 'COORDINATE': 7, 'CRYPTIC': 8, 'EMOTE': 9}
    selected, links, gaps = {}, [], []

    def keep(key):
        key = str(key)
        if key in selected:
            return
        row = raw[key]
        selected[key] = row
        for column in row.get('columns', {}).values():
            types = column['types']
            for i, value in enumerate(column['values']):
                if types[i % len(types)] == 74:
                    keep(value)

    for step in steps:
        source = wiki.get(step['key'])
        if source is None or source['kind'] not in tables:
            continue
        ident = source['fields'].get('id', '')
        if not ident.isdecimal():
            continue
        matches = [k for k, row in raw.items() if row.get('table') == tables[source['kind']] and
                   row.get('columns', {}).get('0', {}).get('values') == [int(ident)]]
        if len(matches) != 1:
            gaps.append(dict(key=step['key'], wikiClueId=int(ident),
                             reason='Wiki clue ID does not uniquely resolve in pinned cache table', matches=matches))
            continue
        keep(matches[0])
        links.append(dict(key=step['key'], wikiClueId=int(ident), dbRow=int(matches[0])))

    destination = ROOT/'docs/CLUE/cache/selected-step-db.json'
    destination.write_text(json.dumps(dict(source='openrs2-2686 config 2/38', steps=links, gaps=gaps, rows=selected),
                                      indent=2)+'\n', encoding='utf8')
    scenes = ROOT/'build/clue/next/search-scenes.json'
    assert len(json.loads(scenes.read_text())) == 4
    (destination.parent/'search-scenes.json').write_bytes(scenes.read_bytes())
    print(f'{len(links)} selected clue identities, {len(selected)} source DB rows, {len(gaps)} explicit ID gaps; 4 paired search scenes')
    print('DB evidence SHA256', sha256(destination.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
