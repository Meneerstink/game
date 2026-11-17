"""Pin a small master hot/cold pool to source cache DB coordinates and Wiki enemies.

No cache writer. Default output is staged while the Gradle snapshot is frozen.
RuneLite's approximate navigation coordinates are deliberately not copied.
"""
from hashlib import sha256
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    rows = json.loads((ROOT/'build/clue/db-rows.json').read_text())
    sources = ROOT/'docs/CLUE/sources'
    pins = {r['page']: r for r in json.loads((sources/'manifest.json').read_text())['pages'] if 'file' in r}
    used = {}
    for name in ['Strange device', 'Jorral', 'Transcript:Jorral', 'Treasure Trails/Guide/Hot Cold/Master',
                 'Template:Treasure Trails Guide Hot Cold Intro']:
        pin = pins[name]
        raw = (sources/pin['file']).read_bytes()
        assert sha256(raw).hexdigest() == pin['sha256'], name
        used[name] = pin
    wiki = (sources/pins['Treasure Trails/Guide/Hot Cold/Master']['file']).read_text(encoding='utf8')
    blocks = wiki.split('|-')
    selected = []
    for ident in [214, 216, 218, 219, 220, 221]:
        row = rows[str(ident)]
        assert row['table'] == 18 and row['columns']['0']['types'] == [22]
        packed = row['columns']['0']['values'][0]
        point = [packed >> 14 & 16383, packed & 16383, packed >> 28]
        description = row['columns']['1']['values'][0]
        plain = lambda text: re.sub(r"\[\[(?:[^]|]+\|)?([^]]+)]]", r'\1', text).replace("'''",'')
        matching = [b for b in blocks if description in plain(b)]
        assert len(matching) == 1, (description, matching)
        combat = re.findall(r'\|(Single|Multi)\s*(?:\n|$)', matching[0])
        assert len(combat) == 1
        guards = [7310] if combat[0] == 'Single' else [7307,7308,7309]
        parents = [{**r, 'id': int(k)} for k,r in rows.items() if r.get('table') == 12 and
                   r.get('columns',{}).get('1',{}).get('values') == [5] and
                   r.get('columns',{}).get('2',{}).get('values') == [ident]]
        assert len(parents) == 1, (ident, parents)
        selected.append(dict(id=ident, centre=point, radius=4, description=description,
                             guards=guards, location=row, clue=parents[0]))
    result = dict(sourceCache=2686, sources=used, npc=3490, item=19939,
                  targets=selected, selection='Uniform over this owner-authorised limited target pool; not all OSRS locations.',
                  radiusEvidence='Master-specific Wiki Hot Cold Intro:9x9. Strange device generic0..3 table is not master-specific.',
                  messagesSource='OSRS Wiki Strange device temperature names; RuneLite parser confirms device prefix/comparison fragments.')
    out = ROOT/('data/cfg/clue/hotcold.json' if '--runtime' in sys.argv else 'build/clue/hotcold/hotcold.json')
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print('Six source-cache master targets with Wiki combat types:', [(r['id'],r['centre'],r['guards']) for r in selected])


if __name__ == '__main__': main()
