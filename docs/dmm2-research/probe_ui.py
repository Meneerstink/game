"""Read-only scan for source interface texts and map-element names."""
import json
import re
from probe_cache import ROOT, SOURCE, Cache, group, write, decode_interface

if __name__=='__main__':
    cache=Cache(str(SOURCE)); cache.osrs=True
    ui={}; errors={}
    pattern=re.compile(rb'safe deposit|deadman reward|armageddon|annihilation|blood money|fortuity|avarice',re.I)
    for gid in cache.reference(3):
        files=group(cache,3,gid)
        if not any(pattern.search(raw) for raw in files.values()): continue
        selected={}
        for fid,raw in files.items():
            try: selected[fid]=decode_interface(raw)
            except Exception as e: errors[f'{gid}:{fid}']=str(e)
        ui[gid]=selected
    write('cache2686-interfaces.json',ui); write('cache2686-interface-errors.json',errors)
    elements={}
    for eid,raw in group(cache,2,35).items():
        if re.search(rb'deadman|nigel|financial',raw,re.I): elements[eid]={'hex':raw.hex(),'strings':[s.decode('latin-1') for s in re.findall(rb'[ -~]{4,}',raw)]}
    write('cache2686-map-elements.json',elements)
    print('UI groups',list(ui),'decode errors',errors,'map elements',elements)
    for gid,files in ui.items():
        print('INTERFACE',gid)
        for fid,comp in files.items():
            if comp.get('text'): print(fid,comp)
