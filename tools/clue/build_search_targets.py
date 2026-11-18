"""Resolve supported searches against both actual scenes, not same-name guesses."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
TARGETS = {
    'Search the crate in Rommiks crafting shop in Rimmington': (9533, 9533, 'Crate'),
    'Search for a crate in Varrock Castle': (5113, 46266, 'Crate'),
    'Search the bucket in the Port Sarim jail': (9568, 9568, 'Bucket'),
    'A crate in the Lumber Yard': (2620, 2620, 'Crate'),
    "The crate in McGrubor's Wood": (357, 24202, 'Crate'),
    'In a crate by the stairs to the Observatory Dungeon': (18506, 18506, 'Crate'),
    "In a crate at the Dark Warriors' Fortress": (354, 46331, 'Crate'),
    'The crate west of the Clock Tower': (354, 46331, 'Crate'),
}


def main():
    steps = json.loads((ROOT/'data/cfg/clue/steps.json').read_text())
    scenes = json.loads((ROOT/'docs/CLUE/cache/search-scenes.json').read_text())
    result = []
    for step in steps:
        if step['action'] != 'SEARCH':
            continue
        suffix = next((key for key in TARGETS if step['key'].endswith(' - '+key)), None)
        assert suffix is not None, step['key']
        source, local, name = TARGETS[suffix]
        scene = next(s for s in scenes if s['key'] == step['key'])
        point, = step['points']
        def match(rows, ident):
            candidates = [r for r in rows if r['id'] == ident and r['point'] == point]
            assert len(candidates) == 1, (step['key'], ident)
            row, = candidates
            assert row['definition']['name'] == name
            return row
        osrs = match(scene['source'], source)
        native = match(scene['native'], local)
        assert 'Search' in osrs['definition']['options'], step['key']
        result.append(dict(key=step['key'], osrsId=source, existingLocal=local,
                           point=point, name=name, sourceSha256=osrs['sha256'],
                           nativeSha256=native['sha256'], option='Search',
                           requiresMenuPatch='Search' not in native['definition']['options']))
    assert len(result) == len(TARGETS)
    (ROOT/'data/cfg/clue/search-targets.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    print(f'{len(result)} exact source/native search targets; missing menus stay explicitly marked')


if __name__ == '__main__':
    main()
