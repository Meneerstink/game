"""Current clue-steps NPC PLAN/PREFLIGHT, with no apply argument or mapping writes."""
from hashlib import sha256
import json
import re
import subprocess
from preflight_stashes import ROOT, snapshot


def main():
    out = ROOT/'build/clue/npc-preflight-current'
    out.mkdir(parents=True, exist_ok=True)
    mapping = ROOT/'data/cfg/clue/npcs.tsv'
    profiles = ROOT/'data/cfg/clue/npc-assets.json'
    expected = {int(key) for key in json.loads(profiles.read_text())}
    cp = ';'.join(str(ROOT/p) for p in
                  ['game/build/classes/kotlin/main', 'game/build/classes/java/main', 'game/build/install/game/lib/*'])
    command = ['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe', '-Xmx768m', '-cp', cp,
               'gg.rsmod.game.tools.importer.OsrsNpcImportTool', 'clue-steps']

    def state():
        value = snapshot()
        value['npcMapping'] = sha256(mapping.read_bytes()).hexdigest() if mapping.exists() else None
        value['npcProfiles'] = sha256(profiles.read_bytes()).hexdigest()
        return value

    before = state()
    with (out/'result.txt').open('w') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after = state()
    (out/'read-only-proof.json').write_text(json.dumps(dict(command=command, exit=result.returncode,
        before=before, after=after, unchanged=before == after), indent=2))
    assert before == after, 'Cache/ledger/mapping/profile fingerprints changed; inspect proof'
    assert result.returncode == 0, (out/'result.txt').read_text(errors='replace')[-2500:]
    text = (out/'result.txt').read_text(errors='replace')
    transaction = re.search(r'^PREFLIGHT transaction=(\S+) mutations=(\d+)', text, re.M)
    assert transaction and re.search(r'^DRY_RUN records=\d+$', text, re.M)
    assert not re.search(r'^APPLIED|BLOCKING:', text, re.M)
    rows = re.findall(r"^PLAN npc (\d+) '(.*?)' -> (\d+) (.*)$", text, re.M)
    assert len(rows) == len(expected) and {int(row[0]) for row in rows} == expected
    (ROOT/'docs/CLUE/NPC-PREFLIGHT.txt').write_text(text, encoding='utf8')
    report = f'# {transaction[1]}; applied=false; source NPCs={len(rows)}; mutations={transaction[2]}\n'
    report += '# source\tproposed local\tname\tassets\n'
    report += ''.join(f'{source}\t{local}\t{name}\t{assets}\n' for source, name, local, assets in rows)
    (ROOT/'docs/CLUE/NPC-PREFLIGHT.tsv').write_text(report, encoding='utf8')
    print(transaction[1], len(rows), 'source NPCs;', transaction[2], 'mutations; both caches/ledger/mapping unchanged')


if __name__ == '__main__':
    main()
