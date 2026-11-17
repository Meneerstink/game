"""Verify applied supplementary clue sequences in both caches; no target-cache writer.

Default audits only; --write publishes only this worktree's runtime identity map.
The roster is independent of clue-steps NPC/item/UI allocation.
"""
from hashlib import sha256
from pathlib import Path
import json
import sys
import yaml

ROOT=Path(__file__).resolve().parents[2]
SOURCES=[376,378,390,424,426,427,727,836,1162,1167,4320,4321,4322,6957,6958,6959]


def main():
    sys.path.insert(0,'C:/RSPS/tools/cache667')
    from js5 import Cache
    ledger=Path('C:/RSPS/OSRS_IMPORT_MASTER.yml')
    digest=sha256(ledger.read_bytes()).hexdigest()
    mapping={}
    for row in yaml.safe_load(ledger.read_text(encoding='utf8'))['imports']:
        if row.get('fx_kind')=='seq' and isinstance(row.get('upstream_fx_id'),int) and isinstance(row.get('local_fx_id'),int):
            mapping[row['upstream_fx_id']]=row['local_fx_id']
    caches=[Cache(p) for p in ['C:/RSPS/game/game/data/cache','C:/RSPS/file-server/cache']]
    missing=[];rows=[];proof={}
    try:
        for source in SOURCES:
            local=mapping.get(source)
            if local is None:missing.append(source);continue
            data=[cache.file(20,local>>7,local&127) for cache in caches]
            assert data[0] and data[0]==data[1],(source,local,'missing/different installed bytes')
            rows.append(f'seq\t{source}\t{local}\n')
            proof[str(source)]={'local':local,'sha256':sha256(data[0]).hexdigest()}
    finally:
        for cache in caches:cache.dat.close()
    assert digest==sha256(ledger.read_bytes()).hexdigest(),'Ledger changed during read-only audit; rerun'
    out=ROOT/'build/clue/combat-assets';out.mkdir(parents=True,exist_ok=True)
    (out/'mapping-proof.json').write_text(json.dumps(dict(ledger=digest,missing=missing,sequences=proof),indent=2)+'\n')
    if missing:
        print('Supplemental sequence imports pending:',missing)
        assert '--write' not in sys.argv,'Cannot publish an incomplete applied mapping'
        return
    assert len(rows)==len(SOURCES)
    if '--write' in sys.argv:
        (ROOT/'data/cfg/clue/combat-assets.tsv').write_text('# dual-cache-ledger-verified; applied=true\n'+''.join(rows))
    print('Verified16applied sequences in both caches;', 'published mapping' if '--write' in sys.argv else 'audit only')


if __name__=='__main__':main()
