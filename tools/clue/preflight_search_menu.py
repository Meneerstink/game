"""Source-backed bucket Search menu PLAN/PREFLIGHT; never applies mutations."""
import json
import subprocess
from preflight_stashes import ROOT, snapshot


def main():
    out = ROOT/'build/clue/search-menu-preflight'
    out.mkdir(parents=True, exist_ok=True)
    args = [json.loads(line) for line in
            (ROOT/'build/clue/compile/basic-tests/game.args').read_text().splitlines()]
    cp = str(ROOT/'build/clue/compile/basic-tests/game')+';'+args[args.index('-cp')+1]
    command = ['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe',
               '-Xmx256m', '-cp', cp, 'gg.rsmod.game.tools.importer.ClueSearchMenuCacheTool']
    before = snapshot()
    with (out/'preflight.txt').open('w') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after = snapshot()
    (out/'read-only-proof.json').write_text(json.dumps(dict(command=command,
        exit=result.returncode, before=before, after=after, unchanged=before == after), indent=2))
    print((out/'preflight.txt').read_text(errors='replace'))
    print('exit', result.returncode, 'cache/ledger/runtime mapping unchanged', before == after)
    assert before == after
    assert result.returncode == 0


if __name__ == '__main__':
    main()
