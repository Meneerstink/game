"""Run the compiled STASH tool without apply; prove cache/ledger/mapping immutability."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/clue/stash-preflight'
LEDGER = Path('C:/RSPS/OSRS_IMPORT_MASTER.yml')
TARGETS = [Path('C:/RSPS/game/game/data/cache'), Path('C:/RSPS/file-server/cache')]
RUNTIME = ROOT / 'data/cfg/clue/stash-mapping.tsv'


def snapshot():
    return dict(ledger=hashlib.sha256(LEDGER.read_bytes()).hexdigest(),
                mapping=hashlib.sha256(RUNTIME.read_bytes()).hexdigest() if RUNTIME.exists() else None,
                files={str(p): [p.stat().st_size, p.stat().st_mtime_ns]
                       for target in TARGETS for p in target.glob('main_file_cache.*')})


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    arguments = [json.loads(line) for line in (ROOT / 'build/clue/compile/stash-import-tests/game.args').read_text().splitlines()]
    cp = str(ROOT / 'build/clue/compile/stash-import-tests/game') + ';' + arguments[arguments.index('-cp') + 1]
    command = ['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe', '-Xmx1g', '-cp', cp,
               'gg.rsmod.game.tools.importer.ClueStashCacheTool', str(ROOT / 'data/cfg/clue/stashes.json'),
               str(RUNTIME), str(ROOT / 'docs/CLUE/STASH-PREFLIGHT.tsv'), '--fixtures-out='+str(OUT/'fixtures')]
    before = snapshot()
    with (OUT / 'result.txt').open('w') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after = snapshot()
    (OUT / 'read-only-proof.json').write_text(json.dumps(dict(command=command, exit=result.returncode,
        before=before, after=after, unchanged=before == after), indent=2))
    print('exit', result.returncode, 'cache/ledger/runtime mapping unchanged', before == after)
    print((OUT / 'result.txt').read_text()[-5500:])
    assert before == after
    assert result.returncode == 0


if __name__ == '__main__':
    main()
