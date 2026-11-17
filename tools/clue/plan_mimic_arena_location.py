"""Prove a proposed Mimic source square is unused in BOTH target caches; no writes to caches."""
from hashlib import sha256
from pathlib import Path
import json
import sys

ROOT=Path(__file__).resolve().parents[2]
SOURCE=10819
TARGET=(16 << 8) | 64  # An internal relocation, not an OSRS world coordinate or a reservation.


def name_hash(value):
    result=0
    for c in value:result=(result*31+ord(c)) & 0xffffffff
    return result if result < 0x80000000 else result-0x100000000


def main():
    sys.path.insert(0,'C:/RSPS/tools/cache667')
    from js5 import Cache
    reports=[]
    for folder in ['C:/RSPS/game/game/data/cache','C:/RSPS/file-server/cache']:
        cache=Cache(folder)
        try:
            cache.reference(5)
            names=cache.names[5]
            def present(square):
                return {f'{p}{square >> 8}_{square & 255}':names.get(name_hash(f'{p}{square >> 8}_{square & 255}'))
                        for p in ['m','l']}
            old=present(SOURCE);new=present(TARGET)
            assert all(x is not None for x in old.values()),'Expected occupied native source square'
            assert all(x is None for x in new.values()),'Target occupied; replan relocation, never replace its groups'
            reports.append(dict(cache=folder,original=old,proposal=new,
                                referenceSha256=sha256(cache.raw(255,5)).hexdigest()))
        finally:cache.dat.close()
    report=dict(sourceRegion=SOURCE,targetProposal=TARGET,squareOffset=[-26,-3],reserved=False,
        owner='14September2026 Q10/17: unused square, instance (OSRS_IMPORT_MASTER.yml owner answers)',
        sourceBounds=[2688,4288,2751,4351],targetBounds=[1024,4096,1087,4159],targets=reports,
        note='Original10819 is occupied in667; preserve it. Recheck proposed4160 in both caches immediately before apply.')
    (ROOT/'docs/CLUE/MIMIC-ARENA-LOCATION.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Both native10819 groups occupied; proposed4160 groups absent in both targets. Offset(-26,-3); no reservation.')


if __name__=='__main__':main()
