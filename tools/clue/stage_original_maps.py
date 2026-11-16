"""Prepare the verified original-map extension without changing frozen runtime inputs."""
from hashlib import sha256
from pathlib import Path
import json
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT/'build/clue/original-maps'
OUT = BASE/'next'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    data = OUT/'data/cfg/clue'
    shutil.copytree(ROOT/'data/cfg/clue', data, dirs_exist_ok=True)
    for script, args in [
        ('curate_steps.py', ['--original-maps', '--output', str(OUT/'STEPS-CURATED.json')]),
        ('build_step_runtime.py', ['--original-maps', '--input', str(OUT/'STEPS-CURATED.json'),
            '--output', str(data/'steps.json'), '--gaps-output', str(OUT/'STEP-ASSET-GAPS.json')]),
    ]:
        subprocess.run(['python', str(ROOT/'tools/clue'/script)] + args, cwd=ROOT, check=True)
    candidates = json.loads((BASE/'candidates.json').read_text())
    components = json.loads((ROOT/'docs/CLUE/cache/map-interfaces.json').read_text())
    components.update(json.loads((BASE/'interfaces.json').read_text()))
    sources = json.loads((ROOT/'data/cfg/clue/map-sources.json').read_text())
    for row in candidates:
        if any(source['key'] == row['key'] for source in sources):
            continue
        group = row['candidateGroup']
        close, = [int(key) for key, value in components[str(group)].items() if value.get('actions') == ['Close']]
        sources.append(dict(key=row['key'], group=group, close=close, wikiImage=row['image'],
            imageSha256=row['imageSha256'], dbRow=row['dbRow'], dbSha256=row['dbSha256']))
    assert len(sources) == len(components) == 26
    cache = OUT/'cache'; cache.mkdir(exist_ok=True)
    (cache/'map-interfaces.json').write_text(json.dumps(components, indent=2)+'\n')
    (data/'map-sources.json').write_text(json.dumps(sources, indent=2)+'\n')
    shutil.copyfile(ROOT/'docs/CLUE/cache/hover.json', cache/'hover.json')

    scenes = json.loads((BASE/'search-scenes.json').read_text())
    targets = json.loads((ROOT/'data/cfg/clue/search-targets.json').read_text())
    chosen = {"The crate in McGrubor's Wood": (357, 24202),
              'In a crate by the stairs to the Observatory Dungeon': (18506, 18506),
              "In a crate at the Dark Warriors' Fortress": (354, 46331),
              'The crate west of the Clock Tower': (354, 46331)}
    for scene in scenes:
        if any(target['key'] == scene['key'] for target in targets):
            continue
        suffix, = [key for key in chosen if scene['key'].endswith(' - '+key)]
        source_id, local_id = chosen[suffix]
        source, = [r for r in scene['source'] if r['id'] == source_id]
        native, = [r for r in scene['native'] if r['id'] == local_id]
        assert source['point'] == native['point']
        assert source['definition']['name'] == native['definition']['name'] == 'Crate'
        assert 'Search' in source['definition']['options'] and 'Search' in native['definition']['options']
        targets.append(dict(key=scene['key'], osrsId=source_id, existingLocal=local_id, point=source['point'],
            name='Crate', sourceSha256=source['sha256'], nativeSha256=native['sha256'],
            option='Search', requiresMenuPatch=False))
    assert len(targets) == 8
    (data/'search-targets.json').write_text(json.dumps(targets, indent=2)+'\n')

    core = ROOT/'game/src/main/kotlin/gg/rsmod/game/tools/importer/ClueInterfaceImportTool.kt'
    text = core.read_text()
    counts = ', '.join(f'{int(group)} to {len(rows)}' for group, rows in sorted(components.items(), key=lambda pair:int(pair[0])))
    text, count = re.subn(r'val MAP_COUNTS = mapOf\(.*?\)', 'val MAP_COUNTS = mapOf('+counts+')', text, count=1, flags=re.S)
    assert count == 1
    (OUT/core.name).write_text(text)
    library = ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls/TreasureTrailMaps.kt'
    text = library.read_text()
    groups = ','.join(map(str, sorted(map(int, components))))
    text, count = re.subn(r'it.group in setOf\([0-9,]+\)', 'it.group in setOf('+groups+')', text, count=1)
    assert count == 1
    (OUT/library.name).write_text(text)

    core_test = ROOT/'game/src/test/kotlin/gg/rsmod/game/tools/importer/ClueMapInterfaceImportTests.kt'
    text = core_test.read_text().replace('all nine source maps', 'all twenty six source maps')
    text = text.replace('../docs/CLUE/cache/map-interfaces.json', (cache/'map-interfaces.json').as_posix())
    (OUT/core_test.name).write_text(text)
    for name in ['TreasureTrailMapsTests', 'TreasureTrailStepCatalogueTests', 'TreasureTrailBasicStepsTests']:
        original = ROOT/f'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls/{name}.kt'
        text = original.read_text()
        if name == 'TreasureTrailMapsTests':
            text = text.replace('all nine source illustrations', 'all twenty six source illustrations').replace('assertEquals(9, maps.size)', 'assertEquals(26, maps.size)')
        elif name == 'TreasureTrailStepCatalogueTests':
            text = text.replace('EASY to 19', 'EASY to 22').replace('MEDIUM to 17', 'MEDIUM to 26').replace('HARD to 15', 'HARD to 20').replace('assertEquals(75,', 'assertEquals(92,')
        elif '        val row = catalogue.single { it.key.endsWith("Search for a crate in Varrock Castle") }' in text:
            # Exercise every exact search route, including two sites sharing local46331.
            begin = text.index('        val row = catalogue.single { it.key.endsWith("Search for a crate in Varrock Castle") }')
            end = text.index('\n    }', begin)
            body = text[begin:end]
            body = body.replace('        val row = catalogue.single { it.key.endsWith("Search for a crate in Varrock Castle") }',
                '        for (row in catalogue.filter { it.action == TreasureTrailStepCatalogue.Action.SEARCH }) {')
            text = text[:begin]+body+'\n        }'+text[end:]
        (OUT/original.name).write_text(text)
    files = [p for p in OUT.rglob('*') if p.is_file() and p.name != 'source-sha256.json']
    (OUT/'source-sha256.json').write_text(json.dumps({str(p.relative_to(OUT)): sha256(p.read_bytes()).hexdigest() for p in files}, indent=2)+'\n')
    print('Staged92 records/26 drawings/8 searches;', sum(len(v) for v in components.values()), 'source components. NO runtime changes.')


if __name__ == '__main__':
    main()
