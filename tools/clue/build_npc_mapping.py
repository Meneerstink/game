"""Verify ledger-owned clue NPCs against both installed caches before publishing a runtime mapping.

Default audits only. --write publishes this worktree's TSV only after every NPC
has been verified; it never calls a cache writer or accepts an apply argument.
Claude runs it after applying the clue-steps NPC batch. No proposed IDs are used.
"""
from hashlib import sha256
from pathlib import Path
import json
import sys
import yaml

ROOT = Path(__file__).resolve().parents[2]
SOURCE_IDS = [6586, 7306, 7312, 6587, 6588, 7307, 7308, 7309, 7310]
TARGETS = [Path('C:/RSPS/game/game/data/cache'), Path('C:/RSPS/file-server/cache')]


def main():
    sys.path.insert(0, 'C:/RSPS/tools/cache667')
    from js5 import Cache
    from local_npcs import decode
    source = json.loads((ROOT/'docs/CLUE/cache/step-assets.json').read_text())['npcs']
    guardians = {row['osrsId']: row for row in json.loads((ROOT/'data/cfg/clue/guardians.json').read_text())}
    pins = {row['page']: row for row in json.loads((ROOT/'docs/CLUE/sources/manifest.json').read_text(encoding='utf8'))['pages'] if 'file' in row}
    ledger = Path('C:/RSPS/OSRS_IMPORT_MASTER.yml')
    before = sha256(ledger.read_bytes()).hexdigest()
    imported = {}
    for row in yaml.safe_load(ledger.read_text(encoding='utf8'))['imports']:
        if isinstance(row.get('fx_kind'), str) and isinstance(row.get('upstream_fx_id'), int) and isinstance(row.get('local_fx_id'), int):
            imported[row['fx_kind'], row['upstream_fx_id']] = row['local_fx_id']
    caches = [Cache(str(path)) for path in TARGETS]
    rows, missing = [], []
    profiles = {}
    try:
        for osrs in SOURCE_IDS:
            record = source[str(osrs)]; expected = record['definition']
            if osrs in guardians:
                wiki = guardians[osrs]['source']
                examine = guardians[osrs]['examine']
            else:
                wiki = pins[expected['name']]
                raw = (ROOT/'docs/CLUE/sources'/wiki['file']).read_bytes()
                assert sha256(raw).hexdigest() == wiki['sha256'], wiki['file']
                import re
                examine = re.search(r'(?m)^\|examine\s*=\s*(.+)$', raw.decode('utf8'))[1].strip()
            profiles[str(osrs)] = dict(name=expected['name'], level=expected['combat'], options=expected['ops'],
                heads=len(expected['chatheads']), cacheSha256=record['sha256'], examine=examine, wiki=wiki['url'])
            local = imported.get(('npc', osrs))
            if local is None:
                missing.append(dict(source=osrs, name=expected['name'], reason='No applied ledger-owned NPC import'))
                continue
            payloads = [cache.file(18, local >> 7, local & 127) for cache in caches]
            assert payloads[0] and payloads[0] == payloads[1], (osrs, local, 'NPC cache bytes differ or absent')
            actual = decode(payloads[0])
            assert not actual.get('stopped_at'), (osrs, local, actual.get('stopped_at'))
            assert actual['name'] == expected['name'] and actual.get('level', 0) == expected['combat'], (osrs, local, 'Name/level mismatch')
            assert actual['ops'] == expected['ops'] and actual['size'] == expected['size'], (osrs, local, 'Menu/size mismatch')
            assert actual['bas'] >= 0, (osrs, local, 'Missing movement set')
            assert actual['models'] == [imported['npc_model', model] for model in expected['models']], (osrs, local, 'Body identities differ')
            assert actual['heads'] == [imported['npc_model', model] for model in expected['chatheads']], (osrs, local, 'Portrait identities differ')
            rows.append(dict(source=osrs, local=local, name=expected['name'], localSha256=sha256(payloads[0]).hexdigest()))
    finally:
        for cache in caches:
            cache.dat.close()
    assert before == sha256(ledger.read_bytes()).hexdigest(), 'Ledger changed during this read-only audit'
    (ROOT/'data/cfg/clue/npc-assets.json').write_text(json.dumps(profiles, indent=2)+'\n', encoding='utf8')
    (ROOT/'docs/CLUE/NPC-MAPPING-AUDIT.json').write_text(json.dumps(dict(sourceCache=2686, verified=rows, missing=missing), indent=2)+'\n', encoding='utf8')
    print('Verified NPCs:', len(rows), '; awaiting applied imports:', len(missing))
    if '--write' in sys.argv:
        assert not missing and len(rows) == len(SOURCE_IDS), 'Incomplete NPC imports; runtime mapping retained'
        assert len({row['local'] for row in rows}) == len(rows), 'Local NPC identities collide'
        (ROOT/'data/cfg/clue/npcs.tsv').write_text('# CLUE NPC assets; applied=true\n' + ''.join(
            f"npc\t{row['source']}\t{row['local']}\n" for row in rows), encoding='utf8')
        print('Published verified mapping in this worktree; no cache or ledger writes')
    else:
        print('AUDIT_ONLY; runtime mapping/cache/ledger retained')


if __name__ == '__main__':
    main()
