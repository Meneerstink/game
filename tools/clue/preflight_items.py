"""Run the reviewed clue item roster as PLAN/PREFLIGHT and verify read-only invariants."""
import hashlib
import json
import re
import subprocess
import sys
from preflight_stashes import ROOT, snapshot


def main():
    puzzles = '--puzzles' in sys.argv
    out = ROOT/('build/clue/puzzle-item-preflight' if puzzles else 'build/clue/item-preflight-current')
    out.mkdir(parents=True, exist_ok=True)
    runtime = ROOT/'data/cfg/clue/items.tsv'
    roster = ROOT/('docs/CLUE/cache/puzzle-import-items.tsv' if puzzles else 'data/cfg/clue/import-items.tsv')
    roster_count = sum(bool(line) and not line.startswith('#') for line in roster.read_text().splitlines())
    cp = ';'.join(str(ROOT/p) for p in ['game/build/classes/kotlin/main', 'game/build/classes/java/main', 'game/build/install/game/lib/*'])
    command = ['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe', '-Xmx1500m', '-cp', cp,
        'gg.rsmod.game.tools.importer.OsrsItemImportTool', 'clue', '--clue-roster='+str(roster)]

    def state():
        result = snapshot()
        result['itemMapping'] = hashlib.sha256(runtime.read_bytes()).hexdigest()
        result['roster'] = hashlib.sha256(roster.read_bytes()).hexdigest()
        return result

    before = state()
    with (out/'result.txt').open('w') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after = state()
    (out/'read-only-proof.json').write_text(json.dumps(dict(command=command, exit=result.returncode,
        before=before, after=after, unchanged=before == after), indent=2))
    text = (out/'result.txt').read_text(errors='replace')
    print(text[-1500:])
    assert before == after, 'Read-only cache/ledger/mapping/roster invariants changed; inspect proof'
    assert result.returncode == 0
    transaction = re.search(r'TRANSACTION (\S+) applied=false finalized=0 recovered=0', text)
    assert transaction, 'Not an unapplied, unfinalized preflight'
    rows = re.findall(r"^PLAN item (\d+) '(.*)' <- upstream_item:(\d+) bytes=(\d+) models=(.*)$", text, re.M)
    assert len(rows) >= roster_count
    output = f'# {transaction[1]}; applied=false; {roster_count} source roster entries including linked notes\n'
    output += '# OSRS id\tproposed local id\tname\tdefinition bytes\tmodel mappings\n'
    output += ''.join(f'{source}\t{local}\t{name}\t{size}\t{models}\n' for local, name, source, size, models in rows)
    (ROOT/('docs/CLUE/PUZZLE-ITEM-PREFLIGHT.tsv' if puzzles else 'docs/CLUE/ITEM-PREFLIGHT.tsv')).write_text(output, encoding='utf8')
    print(transaction[1], roster_count, 'roster entries,', len(rows), 'definitions; cache/ledger/mappings unchanged')


if __name__ == '__main__':
    main()
