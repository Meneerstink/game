"""Read-only dependency check for the DMM2 plan; output stays in docs/dmm2-research."""
import hashlib
import json
import re
from pathlib import Path
from probe_cache import Cache, SOURCE, ROOT, group, item_decode, items

source = Cache(str(SOURCE))
source.osrs = True
defs = group(source, 2, 10)
out = json.loads((ROOT/'cache2686-items.json').read_text())
# IDs from the captured individual wiki pages, not guessed equivalent items.
missing_ids = []
for iid in [13660, 13658, 26149, 30689, 29598, 34607, *range(33021, 33039)]:
    if iid not in defs:
        missing_ids.append(iid)
    else:
        out[str(iid)] = item_decode(defs[iid])
(ROOT/'source-missing-item-candidates.json').write_text(json.dumps(missing_ids), encoding='utf-8')
for d in list(out.values()):
    for op in (97, 98, 148, 149):
        iid = d['fields'].get(str(op), d['fields'].get(op))
        if iid and iid != 65535 and str(iid) not in out:
            out[str(iid)] = item_decode(defs[iid])
(ROOT/'cache2686-items.json').write_text(json.dumps(out, indent=2), encoding='utf-8')

names = {d['name'].casefold() for d in out.values() if d.get('name')}
local = items.Items()
local.c.dat.close()
local.c = Cache(str(Path.cwd()/'data/cache'))
matches = {}
errors = {}
for iid in local.all_ids():
    raw = local.raw(iid)
    if not raw or not any(n.encode('latin-1', errors='replace') in raw.lower() for n in names):
        continue
    try:
        d = items.decode(raw)
        if (d.get('name') or '').casefold() in names:
            matches[iid] = d
    except Exception as e:
        errors[iid] = str(e)
(ROOT/'local-all-source-name-matches.json').write_text(json.dumps(matches, indent=2), encoding='utf-8')
(ROOT/'local-dependency-errors.json').write_text(json.dumps(errors, indent=2), encoding='utf-8')

assets = {}
for iid, d in out.items():
    for op, mid in d.get('models', {}).items():
        if mid not in (0, 65535, -1):
            assets.setdefault(('model', int(mid)), []).append('item '+str(iid)+' opcode '+str(op))
for nid, d in json.loads((ROOT/'cache2686-npcs.json').read_text()).items():
    for mid in d.get('models', []) + d.get('heads', []):
        assets.setdefault(('model',mid),[]).append('npc '+nid)
for filename, kind, pattern in [
    ('AnimationID.java','seq',r'KHOPESH|TRINKET|DEADMAN_.*(?:REWARD_TELEPORT|HOME_TELE)|AMULET_OF_FIRE'),
    ('SpotanimID.java','spotanim',r'KHOPESH|TRINKET|DEADMAN_2026_HOME_TELE|AMULET_OF_FIRE'),
]:
    for line in (Path(r'C:\RSPS\reference\runelite\gameval')/filename).read_text().splitlines():
        m=re.search(r'public static final int (\w+) = (\d+);',line)
        if m and re.search(pattern,m[1]):assets.setdefault((kind,int(m[2])),[]).append(m[1])
for sid in [829,828,830,170,1041,1042,1524,1681]:
    assets.setdefault(('sprite',sid),[]).append('safebox or Deadman Tutor icon candidate')
indices={'model':7,'seq':2,'spotanim':2,'sprite':8}
groups={}
result=[]
for (kind,aid),why in sorted(assets.items()):
    try:
        if kind in ('seq','spotanim'):
            gid=12 if kind=='seq' else 13
            if gid not in groups:groups[gid]=group(source,2,gid)
            raw=groups[gid][aid]
        else:raw=source.file(indices[kind],aid,0)
        result.append(dict(kind=kind,id=aid,ref=why,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),present=bool(raw)))
    except Exception as e:result.append(dict(kind=kind,id=aid,ref=why,error=str(e),present=False))
(ROOT/'source-assets-presence.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print('Local matching names',len(matches),'errors',len(errors),'assets',len(result),'missing',sum(not x['present'] for x in result))
