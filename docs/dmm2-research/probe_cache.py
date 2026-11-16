"""Read-only evidence extraction. Outputs only to this research directory.

Uses cache667 JS5 splitting; item opcode widths from ModernItemDef.kt,
NPC widths from OsrsNpcProbeTool.kt. Unknown opcodes and trailing bytes fail.
No imports, CacheTransaction, allocation or cache writes.
"""
import hashlib
import json
from pathlib import Path
import re
import sys

sys.path.insert(0, r'C:\RSPS\tools\cache667')
from js5 import Cache
from osrs_items import R
import items
import npcs
from osrs_if3 import split, decode as decode_interface

ROOT = Path(__file__).parent
SOURCE = Path(r'C:\RSPS\import-source\openrs2-2686\cache')

def item_decode(data):
    r = R(data)
    d = dict(models={}, fields={}, params={}, inv=[None]*5, counts=[])
    while True:
        op = r.u8()
        if not op:
            if r.p != len(data): raise ValueError('trailing bytes')
            return d
        if op in (1,4,5,6,7,8,24,26,75,78,79,90,91,92,93,94,95,97,98,110,111,112,139,140,148,149):
            v = r.u16(); d['fields'][op] = v
            if op in (1,24,26,78,79,90,91,92,93): d['models'][op] = v
        elif op in (2,3,9):
            v=r.s(); d['fields'][op]=v
            if op==2: d['name']=v
        elif op in (11,15,16,65,160): d['fields'][op]=True
        elif op==12: d['fields'][op]=r.i32()
        elif op in (13,14,27,42,113,114,115): d['fields'][op]=r.u8()
        elif op in (23,25,45,48):
            d['models'][op]=r.i32() if op in (45,48) else r.u16()
            d['fields'][str(op)+'offset']=r.u8()
        elif 30<=op<35: d['fields'][op]=r.s()
        elif 35<=op<40: d['inv'][op-35]=r.s()
        elif op in (40,41): d['fields'][op]=[[r.u16(),r.u16()] for _ in range(r.u8())]
        elif op==43:
            entry=[r.u8()]; sub=[]
            while True:
                sid=r.u8()
                if not sid: break
                sub.append([sid,r.s()])
            d['fields'].setdefault(op,[]).append(entry+sub)
        elif op in (44,46,47,49,50,51,52,53,54): d['models'][op]=r.i32()
        elif 100<=op<110: d['counts'].append([r.u16(),r.u16()])
        elif op==200: r.u8(); r.u8(); r.s()
        elif op==201: r.u8(); r.u16(); r.u16(); r.i32(); r.i32(); r.s()
        elif op==202: r.u8(); r.u16(); r.u16(); r.u16(); r.i32(); r.i32(); r.s()
        elif op==249:
            for _ in range(r.u8()):
                isstr=r.u8(); key=r.u24(); d['params'][key]=r.s() if isstr else r.i32()
        else: raise ValueError(f'unknown item opcode {op} at {r.p}')

def npc_decode(data):
    r=R(data); d=dict(ops=[None]*5,params={},sequences={},transforms=[])
    while True:
        op=r.u8()
        if not op:
            if r.p!=len(data): raise ValueError('trailing bytes')
            return d
        if op in (1,60,61,62): d['models' if op in (1,61) else 'heads']=[r.i32() if op in (61,62) else r.u16() for _ in range(r.u8())]
        elif op==2: d['name']=r.s()
        elif op==12: d['size']=r.u8()
        elif op in (13,14,15,16,114,116): d['sequences'][op]=r.u16()
        elif op in (17,115,117): d['sequences'][op]=[r.u16() for _ in range(4)]
        elif op in (18,74,75,76,77,78,79,95,97,98,103,124,126,146): d[op]=r.u16()
        elif 30<=op<35: d['ops'][op-30]=r.s()
        elif op in (40,41): d[op]=[[r.u16(),r.u16()] for _ in range(r.u8())]
        elif op in (93,99,107,109,111,122,123,129,130,145,147): d[op]=True
        elif op in (100,101): d[op]=r.s8()
        elif op==102:
            mask=r.u8()
            for i in range(mask.bit_length()):
                if mask & (1<<i):
                    r.i32() if r.b[r.p]>=128 else r.u16()
                    r.u8() if r.b[r.p]<128 else r.u16()
        elif op in (106,118):
            d['varbit']=r.u16(); d['varp']=r.u16()
            if op==118: d['fallback']=r.u16()
            d['transforms']=[r.u16() for _ in range(r.u8()+1)]
        elif op==249:
            for _ in range(r.u8()):
                isstr=r.u8(); k=r.u24(); d['params'][k]=r.s() if isstr else r.i32()
        elif op==251: r.u8(); r.u8(); r.s()
        elif op==252: r.u8(); r.u16(); r.u16(); r.i32(); r.i32(); r.s()
        elif op==253: r.u8(); r.u16(); r.u16(); r.u16(); r.i32(); r.i32(); r.s()
        else: raise ValueError(f'unknown npc opcode {op}')

def group(cache,index,gid):
    ref=cache.reference(index).get(gid)
    if ref is None: return {}
    data=cache.decompress(cache.raw(index,gid))
    return dict(zip(ref,split(data,len(ref))))

def write(name,data):
    (ROOT/name).write_text(json.dumps(data,indent=2,ensure_ascii=False),encoding='utf-8')

if __name__=='__main__':
    cache=Cache(str(SOURCE)); cache.osrs=True
    upstream=group(cache,2,10)
    pattern=re.compile(r'corrupted (?:voidwaker|dragon claws|armadyl|twisted|scythe|tumeken|volatile|dark bow)|trinket of|thunder khopesh|amulet of fire|fire ruby|blood money|deadman starter|starter (?:sword|bow|staff|cape)|armageddon |annihilation |deadman.s (?:chest|legs|cape|skull)|\(deadman\)|safe deposit',re.I)
    selected={}; failures={}
    for iid,raw in upstream.items():
        # Name at opcode 2; successful decode is still required before using any ids.
        if not pattern.search(raw.decode('latin-1')): continue
        try:
            d=item_decode(raw)
            if pattern.search(d.get('name','')): selected[iid]=d
        except Exception as e: failures[iid]=str(e)
    # Include noted/placeholder links, starter contents and blood-money stack models.
    wanted={13307,22330,22331,22333,22335,33063,22337,19629,772}
    for iid,d in list(selected.items()):
        wanted.update(v for k,v in d['fields'].items() if k in (97,98,139,140,148,149) and v not in (0,65535))
        wanted.update(pair[0] for pair in d['counts'])
    for iid in sorted(wanted):
        if iid in upstream and iid not in selected:
            try: selected[iid]=item_decode(upstream[iid])
            except Exception as e: failures[iid]=str(e)
    write('cache2686-items.json',selected); write('cache2686-item-errors.json',failures)
    ns=group(cache,2,9); wanted_npcs={8390,8391,6093,6485,6486,6510,6514,6515,6516,6517,6518,6519,6703,10391,15569,15570,15571}
    npcdata={}
    for iid in sorted(wanted_npcs):
        try: npcdata[iid]=npc_decode(ns[iid])
        except Exception as e: npcdata[iid]={'error':str(e)}
    write('cache2686-npcs.json',npcdata)
    local=items.Items()
    local.c.dat.close(); local.c=Cache(str(Path.cwd()/'data/cache'))
    local_matches={}; errors={}
    for iid in local.all_ids():
        raw=local.raw(iid)
        if not raw or not pattern.search(raw.decode('latin-1')): continue
        try:
            d=items.decode(raw)
            if pattern.search(d.get('name') or ''): local_matches[iid]=d
        except Exception as e: errors[iid]=str(e)
    write('local-items.json',local_matches); write('local-item-errors.json',errors)
    write('source-cache-provenance.json',dict(source=str(SOURCE),item_group_sha256=hashlib.sha256(cache.raw(2,10)).hexdigest(),npc_group_sha256=hashlib.sha256(cache.raw(2,9)).hexdigest(),note='Read-only; local cache is this worktree/data/cache; ids must not be treated as allocated import ids.'))
    print('source items',len(selected),'source errors',failures,'local items',len(local_matches),'local errors',errors)
    for iid,d in npcdata.items(): print('NPC',iid,d)
