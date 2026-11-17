"""Verify only DMM2 public data, own preserved test evidence, PLAN outcomes and source hashes."""
from pathlib import Path
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
from validate_phase5_data import validate
from phase5_plan_round import verify as verify_plans

ROOT = Path(__file__).resolve().parents[2]
DOC = Path(__file__).resolve().parent


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def junit(directory):
    rows = {}
    for file in sorted(directory.glob('TEST-*.xml')):
        root = ET.parse(file).getroot()
        assert root.tag == 'testsuite'
        assert int(root.get('failures')) == int(root.get('errors')) == int(root.get('skipped')) == 0, file
        rows[root.get('name')] = dict(tests=int(root.get('tests')), timestamp=root.get('timestamp'), sha256=sha(file))
    assert rows, directory
    return rows


if __name__ == '__main__':
    data = validate()
    verify_plans()
    tests = junit(DOC / 'phase5-output/regression-final')
    latest = junit(DOC / 'phase5-output/regression-sound-final')
    assert len(tests) == 39 and sum(r['tests'] for r in tests.values()) == 281
    assert len(latest) == 3
    tests.update(latest)
    assert 'OK (2 tests)' in (DOC / 'phase5-ui-output/tests.txt').read_text()
    ids = ROOT / 'data/cfg/dmm2/ids.tsv'
    expected = 'e7efbb117ec161980fe20e20d232da8e8c92ecd8c34fdd71d71795866084c7d4'
    blob = subprocess.check_output(['git', 'show', ':data/cfg/dmm2/ids.tsv'], cwd=ROOT)
    assert sha(ids) == hashlib.sha256(blob).hexdigest() == expected
    files = [
        'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/deadman/Dmm2Boot.kt',
        'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/deadman/CorruptedWeapons.kt',
        'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/deadman/FireAmulet.kt',
        'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/deadman/dmm2.plugin.kts',
        'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt',
        'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MeleeCombatStrategy.kt',
        'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/DropSources.kt',
        'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/CollectionLogPages.kt',
        'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/deadman/Dmm2WeaponPresentationTests.kt',
        'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/deadman/Dmm2ItemServicesTests.kt',
        'game/src/main/kotlin/gg/rsmod/game/tools/importer/OsrsInterfacePortTool.kt',
        'game/src/main/kotlin/gg/rsmod/game/tools/importer/OsrsFxImportTool.kt',
    ]
    source_files = [p for p in (DOC / 'phase5-sources').iterdir() if p.is_file()]
    # Kotlin is committed with LF by the repository's existing attributes; Windows working copies may still contain CRLF.
    code_hashes = {}
    for name in files:
        current = (ROOT / name).read_bytes().replace(b'\r\n', b'\n')
        indexed = subprocess.check_output(['git', 'show', ':' + name], cwd=ROOT).replace(b'\r\n', b'\n')
        assert current == indexed, 'Unpublished source change: ' + name
        code_hashes[name] = hashlib.sha256(current).hexdigest()
    report = dict(data=data, test_classes=len(tests), unique_tests=sum(r['tests'] for r in tests.values()),
                  importer_tests=2, tests=tests, plan_verification=json.loads((DOC / 'phase5-ui-output/verification.json').read_text()),
                  source_sha256={p.relative_to(ROOT).as_posix(): sha(p) for p in sorted(source_files)},
                  current_code_sha256=code_hashes, code_hash_format='LF, matching the repository Kotlin attributes',
                  limitations='Focused current-source compile/JUnit and PLAN evidence only; no client, server boot or live run by GPT-A.')
    (DOC / 'PHASE5-VALIDATION.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f"PASS {len(tests)} classes / {report['unique_tests']} unique tests + 2 importer tests; 78 unchanged copied-cache files; actual IDs byte-exact in Git")
