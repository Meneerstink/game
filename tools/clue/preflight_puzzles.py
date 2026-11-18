"""Sliding puzzle UI PLAN/PREFLIGHT only; use the same verified mapping as reward/text UI."""
import hashlib
import json
import subprocess
import sys
from preflight_stashes import snapshot, ROOT


def main():
    render_fix = '--render-fix' in sys.argv
    out = ROOT/('build/clue/puzzle-render/preflight' if render_fix else 'build/clue/puzzles-preflight')
    out.mkdir(parents=True, exist_ok=True)
    runtime = ROOT/'data/cfg/clue/interfaces.tsv'
    codec = ROOT/('build/clue/puzzle-render/codec' if render_fix else 'build/clue/interface-port')
    args = [json.loads(line) for line in (codec/'server.args').read_text().splitlines()]
    cp = str(codec/'server')+';'+args[args.index('-cp')+1]
    command = ['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe', '-Xmx512m', '-cp', cp,
               'gg.rsmod.game.tools.importer.ClueInterfaceImportTool', str(ROOT/'docs/CLUE/cache/puzzle-interface.json'),
               str(runtime), str(ROOT/('docs/CLUE/PUZZLE-RENDER-PREFLIGHT.tsv' if render_fix else 'docs/CLUE/PUZZLE-UI-PREFLIGHT.tsv')), '--puzzles']

    def state():
        value = snapshot()
        value['interfaceMapping'] = hashlib.sha256(runtime.read_bytes()).hexdigest() if runtime.exists() else None
        return value

    before = state()
    with (out/'preflight.txt').open('w') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after = state()
    (out/'read-only-proof.json').write_text(json.dumps(dict(command=command, exit=result.returncode,
        before=before, after=after, unchanged=before == after), indent=2))
    print((out/'preflight.txt').read_text(errors='replace')[-3500:])
    print('exit', result.returncode, 'cache/ledger/runtime mappings unchanged', before == after)
    assert before == after
    assert result.returncode == 0


if __name__ == '__main__':
    main()
