"""Map UI PLAN/PREFLIGHT only; use the same verified mapping as reward/text UI."""
import hashlib
import json
import subprocess
import sys
from preflight_stashes import snapshot, ROOT


def main():
    staged = '--staged-original-maps' in sys.argv
    out = ROOT/'build/clue/original-maps/preflight' if staged else ROOT/'build/clue/maps-preflight'
    out.mkdir(parents=True, exist_ok=True)
    runtime = ROOT/'data/cfg/clue/interfaces.tsv'
    codec = ROOT/'build/clue/original-maps/codec' if staged else ROOT/'build/clue/interface-port'
    args = [json.loads(line) for line in (codec/'server.args').read_text().splitlines()]
    cp = str(codec/'server')+';'+args[args.index('-cp')+1]
    command = ['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe', '-Xmx512m', '-cp', cp,
               'gg.rsmod.game.tools.importer.ClueInterfaceImportTool',
               str(ROOT/'build/clue/original-maps/next/cache/map-interfaces.json' if staged else ROOT/'docs/CLUE/cache/map-interfaces.json'),
               str(runtime), str(out/'MAP-UI-PREFLIGHT.tsv' if staged else ROOT/'docs/CLUE/MAP-UI-PREFLIGHT.tsv'), '--maps']

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
