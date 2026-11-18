"""Read-only cache2686 evidence for the named Deadman loot objects; no allocation or writes to a cache."""
import hashlib
import json
from probe_cache import Cache, SOURCE, ROOT, group


def digest(data):
    return hashlib.sha256(data).hexdigest()


cache = Cache(str(SOURCE))
cache.osrs = True
locs = group(cache, 2, 6)
seqs = group(cache, 2, 12)
evidence = {'source': str(SOURCE), 'decoder': 'ModernObjectDef.decode, ModernSeqDef.decode',
            'gameval': 'ObjectID1.java DEADMAN_BLIGHTED_WEAPONS_LOOT_*, AnimationID.java FX_CYCLE_6_ITEMS*',
            'locs': {}, 'seqs': {}}
for source in (49564, 49565):
    raw = locs[source]
    p = 0
    fields = {}
    while True:
        op = raw[p]; p += 1
        if op == 0:
            assert p == len(raw)
            break
        if op == 2:
            end = raw.index(0, p); fields['name'] = raw[p:end].decode('cp1252'); p = end + 1
        elif op == 7:
            count = raw[p]; p += 1
            fields['models'] = [int.from_bytes(raw[p+4*i:p+4*i+4], 'big') for i in range(count)]
            p += 4 * count
        elif op == 24:
            fields['animation'] = int.from_bytes(raw[p:p+2], 'big'); p += 2
        elif op in (17, 21, 89):
            fields[str(op)] = True
        elif op in (19, 29, 39):
            fields[str(op)] = raw[p]; p += 1
        else:
            raise ValueError(f'loc {source}: unknown opcode {op}')
    evidence['locs'][source] = dict(raw_hex=raw.hex(), sha256=digest(raw), **fields)
for source in (10425, 10426):
    raw = seqs[source]
    assert raw[0] == 1, 'Require classic frame table first; do not scan arbitrary bytes for opcode1'
    count = int.from_bytes(raw[1:3], 'big')
    durations = [int.from_bytes(raw[3+2*i:5+2*i], 'big') for i in range(count)]
    evidence['seqs'][source] = dict(raw_hex=raw.hex(), sha256=digest(raw), frames=count,
                                   durations=durations, client_cycles=sum(durations),
                                   note='Client length only; not proof of player pose, object placement or server delay')
(ROOT / 'phase7-sources/advanced-loot-sources.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
print(json.dumps({k: {sid: {f: row[f] for f in row if f not in ('raw_hex', 'durations')} for sid, row in v.items()}
                  for k, v in evidence.items() if isinstance(v, dict)}))
