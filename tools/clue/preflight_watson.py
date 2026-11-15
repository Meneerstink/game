"""Read-only Watson variant preflight; never supplies an apply argument."""
from pathlib import Path
import json
import hashlib
import subprocess
from preflight_stashes import snapshot, ROOT


def main():
    out = ROOT/'build/clue/watson-import'
    runtime = ROOT/'data/cfg/clue/watson-mapping.tsv'
    args = [json.loads(line) for line in (ROOT/'build/clue/compile/watson-tests/game.args').read_text().splitlines()]
    cp = str(ROOT/'build/clue/compile/watson-tests/game')+';'+args[args.index('-cp')+1]
    command = ['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe', '-Xmx512m', '-cp', cp,
               'gg.rsmod.game.tools.importer.ClueWatsonCacheTool', str(runtime), str(ROOT/'docs/CLUE/WATSON-PREFLIGHT.tsv')]
    def state():
        value = snapshot()
        value['watsonMapping'] = hashlib.sha256(runtime.read_bytes()).hexdigest() if runtime.exists() else None
        return value
    before = state()
    with (out/'preflight.txt').open('w') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after = state()
    (out/'read-only-proof.json').write_text(json.dumps(dict(command=command, exit=result.returncode,
        before=before, after=after, unchanged=before==after), indent=2))
    print((out/'preflight.txt').read_text(errors='replace')[-4500:])
    print('exit', result.returncode, 'cache/ledger/runtime mappings unchanged', before==after)
    assert before == after
    assert result.returncode == 0


if __name__=='__main__':
    main()
