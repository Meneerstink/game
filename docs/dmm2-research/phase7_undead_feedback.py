"""Read-only search for a primary-source undead cooldown message; no cache changes."""
import hashlib
import json
import re
from probe_cache import Cache, SOURCE, ROOT

cache = Cache(str(SOURCE))
cache.osrs = True
pattern = re.compile(rb'thrall|trinket|undead|resurrect|cooldown', re.I)
hits = {}
errors = {}
groups = cache.reference(12)
for gid in groups:
    try:
        raw = cache.file(12, gid, 0)
        if raw and pattern.search(raw):
            hits[gid] = dict(sha256=hashlib.sha256(raw).hexdigest(),
                             strings=[s.decode('cp1252') for s in re.findall(rb'[ -~]{4,}', raw)])
    except Exception as error:
        errors[gid] = str(error)
cache.dat.close()
result = dict(source=str(SOURCE), index=12, groups=len(groups), errors=errors, hits=hits,
              scope='Client-script strings only; absence does not establish server-side message text.')
(ROOT / 'phase7-sources/undead-client-strings.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(dict(groups=len(groups), errors=len(errors), hit_groups=list(hits))))
