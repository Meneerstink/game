"""Watson paper interfaces: PLAN/PREFLIGHT only, using freshly verified classes."""
import hashlib
import json
from pathlib import Path
import subprocess
from preflight_stashes import ROOT, snapshot


def main():
    out = ROOT/'build/clue/boards-preflight'
    out.mkdir(parents=True, exist_ok=True)
    verified = Path((ROOT/'build/clue/watson-boards/latest-verify.txt').read_text().strip())
    proof = json.loads((verified/'proof.json').read_text())
    assert proof['unchanged'] and not proof['staged'] and proof['nativeDecoder']
    assert all(hashlib.sha256(Path(p).read_bytes()).hexdigest() == h
               for p, h in proof['inputs'].items()), 'Rerun verify_boards.py against current production sources'
    args = [json.loads(line) for line in (verified/'libraries.args').read_text().splitlines()]
    cp = str(verified/'libraries')+';'+args[args.index('-cp')+1]
    runtime = ROOT/'data/cfg/clue/interfaces.tsv'
    command = ['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe', '-Xmx512m', '-cp', cp,
               'gg.rsmod.game.tools.importer.ClueInterfaceImportTool', str(ROOT/'docs/CLUE/cache/board-interfaces.json'),
               str(runtime), str(ROOT/'docs/CLUE/BOARD-PREFLIGHT.tsv'), '--boards']

    def state():
        value = snapshot()
        value['runtimeMappings'] = {str(p): hashlib.sha256(p.read_bytes()).hexdigest()
                                    for p in (ROOT/'data/cfg/clue').glob('*.tsv')}
        return value

    before = state()
    with (out/'preflight.txt').open('w') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after = state()
    (out/'read-only-proof.json').write_text(json.dumps(dict(command=command, exit=result.returncode,
        before=before, after=after, unchanged=before == after, verifiedInputs=proof['inputs']), indent=2)+'\n')
    print((out/'preflight.txt').read_text(errors='replace')[-3500:])
    print('exit', result.returncode, 'cache/ledger/runtime mappings unchanged', before == after)
    assert before == after
    assert result.returncode == 0


if __name__ == '__main__':
    main()
