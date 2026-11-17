"""PLAN evidence only. Reuses the previously hashed, physical cache COPIES; never targets a real cache."""
import json
from pathlib import Path
import shutil
import sys
from phase4_round import hashes, BOX, DOC

OUT = DOC / 'phase5-ui-output'


def prepare():
    actual = hashes()
    expected = json.loads((DOC / 'phase4-output/before.json').read_text())
    assert actual == expected and len(actual) == 78, 'Cache-copy baseline changed; do not overwrite or reuse silently'
    assert not list((BOX / 'import-journal').iterdir())
    OUT.mkdir(exist_ok=True)
    (OUT / 'before.json').write_text(json.dumps(actual, indent=2) + '\n', encoding='utf-8')
    shutil.copy2(DOC / 'dmm2-interfaces.json', BOX / 'docs/dmm2-research/dmm2-interfaces.json')


def verify():
    before = json.loads((OUT / 'before.json').read_text())
    after = hashes()
    assert before == after and len(after) == 78
    assert not list((BOX / 'import-journal').iterdir())
    results = {p.name: json.loads(p.read_text()) for p in OUT.glob('*-command.json')}
    assert len(results) == 4 and all(r['exit'] == 0 for r in results.values())
    for r in results.values():
        assert '--apply' not in r['argv']
        assert not any(arg == 'apply' for arg in r['argv'])
        if 'org.junit.runner.JUnitCore' not in r['argv']:
            roots = [arg.split('=', 1)[1] for arg in r['argv'] if arg.startswith('-Drsps.root=')]
            assert len(roots) == 1 and Path(roots[0]).resolve() == BOX.resolve()
    report = dict(files=len(after), unchanged=True, journal_empty=True, results={k: v['exit'] for k, v in results.items()})
    (OUT / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    {'prepare': prepare, 'verify': verify}[sys.argv[1]]()
