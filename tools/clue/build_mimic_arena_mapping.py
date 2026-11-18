"""Audit applied Mimic room/identities; --write publishes only the verified worktree runtime mapping."""
from hashlib import sha256
from pathlib import Path
import json
import re
import subprocess
import sys
import yaml
from preflight_stashes import ROOT, snapshot, LEDGER

JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')


def main():
    before=snapshot()
    records=[r for r in yaml.safe_load(LEDGER.read_text(encoding='utf8'))['imports']
             if str(r.get('name','')).startswith("OSRS region import 'clue-mimic-arena' -")]
    if not records:
        assert '--write' not in sys.argv,'Cannot publish an unapplied room'
        print('Mimic arena import pending; no runtime mapping written');return
    assert len(records)==1,'Ambiguous room imports; audit the exact finalized transaction first'
    record=records[0]
    assert '2686' in record['source']
    offset=re.search(r'moved (-?\d+),(-?\d+) squares',record['name']);assert offset
    x=42+int(offset[1]);z=67+int(offset[2]);assert 0<=x<256 and 0<=z<256
    region=(x<<8)|z;assert region!=10819
    identities={int(r['upstream_id']):int(r['local_id']) for r in record['loc_ids']}
    fixture=ROOT/'docs/CLUE/cache/mimic-arena-source.json'
    expected={int(i) for i in json.loads(fixture.read_text())['definitions']}
    assert len(identities)==len(record['loc_ids'])==43 and set(identities)==expected
    assert len(set(identities.values()))==43
    out=ROOT/'build/clue/mimic-arena/applied-mapping';out.mkdir(parents=True,exist_ok=True)
    candidate=dict(region=region,locals=identities,transaction=record['transaction_id'])
    (out/'candidate.json').write_text(json.dumps(candidate,indent=2)+'\n')
    cp=';'.join(str(p) for p in (ROOT/'game/build/install/game/lib').glob('*.jar'))
    def run(args):subprocess.run(list(map(str,args)),cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    run([JAVA/'javac.exe','-cp',cp,'-d',out,ROOT/'tools/clue/ClueMimicArenaMappingProbe.java'])
    run([JAVA/'java.exe','-Xmx256m','-cp',str(out)+';'+cp,'ClueMimicArenaMappingProbe',out/'candidate.json',
        'C:/RSPS/game/game/data/cache','C:/RSPS/file-server/cache',fixture,ROOT/'data/cfg/clue/mimic-scenery.json',out/'proof.json'])
    assert snapshot()==before,'Caches/ledger changed while certifying the room; rerun'
    proof=json.loads((out/'proof.json').read_text());assert proof['verified']
    lines=[f'# {record["transaction_id"]}; dual-cache-placement-verified; applied=true',f'region\t10819\t{region}']
    lines += [f'loc\t{source}\t{identities[source]}' for source in range(34727,34733)]
    if '--write' in sys.argv:(ROOT/'data/cfg/clue/mimic-arena.tsv').write_text('\n'.join(lines)+'\n')
    print('Applied room verified;', 'runtime mapping published' if '--write' in sys.argv else 'audit only')


if __name__=='__main__':main()
