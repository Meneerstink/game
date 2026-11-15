"""Pin the nine manually compared Wiki/cache2686 map illustrations. No cache writer.

The UI identities were checked against the Wiki images using the decoded source
meshes, component positions and cameras. This is source identification, not a
claim that the target client has rendered them correctly.
"""
from hashlib import sha256
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
SOURCES = [
    ("At the standing stones north of Falador", 351, 2231, "Falador stones"),
    ("Brother Galahad's house", 354, 2232, "Galahad"),
    ("Directly behind the Wizards' Tower", 356, 2233, "Wizard tower"),
    ("South of Draynor Village bank", 348, 2235, "Draynor willows"),
    ("Behind the Chemist's house in Rimmington", 362, 2240, "Chemist house"),
    ("North of the Tower of Life", 360, 2238, "Tower of Life"),
    ("Yanille anvils", 353, 2248, "Yanille anvil"),
    ("A crate in the Lumber Yard", 350, 2247, "Lumberyard"),
    ("South-west of Tree Gnome Village", 86, 2254, "Castle Wars"),
]


def main():
    steps = json.loads((ROOT/'data/cfg/clue/steps.json').read_text())
    database = json.loads((ROOT/'docs/CLUE/cache/selected-step-db.json').read_text())['rows']
    widgets = json.loads((ROOT/'docs/CLUE/cache/map-interfaces.json').read_text())
    images = json.loads((ROOT/'docs/CLUE/sources/map-images/manifest.json').read_text())
    pins = {page['title']: page for page in images['query']['pages']}
    rows = []
    for suffix, group, dbrow, image in SOURCES:
        step, = [s for s in steps if s['kind'] == 'MAP' and s['key'].endswith(' - '+suffix)]
        name = 'Map clue '+image+'.png'
        image_hash = sha256((ROOT/'docs/CLUE/sources/map-images'/name).read_bytes()).hexdigest()
        assert pins['File:'+name]['downloadSha256'] == image_hash
        source = widgets[str(group)]
        close, = [int(k) for k, w in source.items() if w.get('actions') == ['Close']]
        assert source[str(close)]['onMouseOverListener'] == [44, -2147483645, 540]
        assert source[str(close)]['onMouseLeaveListener'] == [44, -2147483645, 539]
        assert database[str(dbrow)]['table'] == 5
        rows.append(dict(key=step['key'], group=group, close=close, wikiImage=name,
                         imageSha256=image_hash, dbRow=dbrow,
                         dbSha256=database[str(dbrow)]['sha256']))
    assert {r['key'] for r in rows} == {s['key'] for s in steps if s['kind'] == 'MAP'}
    target = ROOT/'data/cfg/clue/map-sources.json'
    target.write_text(json.dumps(rows, indent=2)+'\n')
    print('Pinned', len(rows), 'reviewed maps; no cache or mapping writes')


if __name__ == '__main__':
    main()
