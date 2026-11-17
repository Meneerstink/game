"""Download the exact post-2686 Bloodhound dig clip into a private sparse SOURCE fixture.

Never opens either target cache. Each JS5 container comes verbatim from OpenRS2.
The original reference tables remain intact; only the required groups are present.
"""
from hashlib import sha256
from pathlib import Path
import json
import struct
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, 'C:/RSPS/tools/cache667')
from js5 import Cache

BUILD = 2762  # OSRS build242, 11 November2026; pinned2686 predates the8September dog update.
OUT = ROOT/'build/clue/pet-dig/source-2762'


class Source(Cache):
    osrs = True

    def __init__(self):
        self.refs = {}
        self.containers = {}

    def raw(self, index, group):
        key = index, group
        if key not in self.containers:
            url = f'https://archive.openrs2.org/caches/runescape/{BUILD}/archives/{index}/groups/{group}.dat'
            request = urllib.request.Request(url, headers={'User-Agent': '78legacy-ClueResearch/1.0'})
            with urllib.request.urlopen(request, timeout=40) as response:
                self.containers[key] = response.read()
        return self.containers[key]


def write_source(containers):
    # This fresh fixture is always inside this worktree's ignored build directory.
    # It is never a CacheLibrary/CacheTransaction write to a game cache.
    destination = OUT/'cache'
    destination.mkdir(parents=True, exist_ok=True)
    assert destination.resolve().is_relative_to((ROOT/'build/clue/pet-dig').resolve())
    with (destination/'main_file_cache.dat2').open('wb') as dat:
        dat.write(bytes(520))
        sector = 1
        for index in sorted({i for i, _ in containers}):
            with (destination/f'main_file_cache.idx{index}').open('wb') as idx:
                for _, group in sorted(k for k in containers if k[0] == index):
                    raw = containers[index, group]
                    idx.seek(group*6)
                    idx.write(len(raw).to_bytes(3, 'big')+sector.to_bytes(3, 'big'))
                    size = 510 if group > 65535 else 512
                    for chunk, offset in enumerate(range(0, len(raw), size)):
                        payload = raw[offset:offset+size]
                        following = sector+1 if offset+size < len(raw) else 0
                        header = group.to_bytes(4 if group > 65535 else 2, 'big')
                        header += chunk.to_bytes(2, 'big')+following.to_bytes(3, 'big')+bytes([index])
                        dat.write((header+payload).ljust(520, b'\0'))
                        sector += 1
    return destination


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source = Source()
    raw = source.file(2, 12, 14498)
    assert sha256(raw).hexdigest() == 'a63fb9217b502a54d088bfb190f7782341bbd6b7be6238f0b7085651373955a7'
    assert raw[0] == 1
    count = int.from_bytes(raw[1:3], 'big')
    assert count == 8
    framesets = sorted(set(struct.unpack('>'+str(count)+'H', raw[3+count*4:3+count*6])))
    bases = set()
    for frameset in framesets:
        for file in source.reference(0)[frameset]:
            frame = source.file(0, frameset, file)
            bases.add(int.from_bytes(frame[:2], 'big'))
    for base in sorted(bases):
        assert source.file(1, base, 0)
    pinned=Cache('C:/RSPS/import-source/openrs2-2686/cache')
    pinned.osrs=True
    try:
        same_base=sha256(pinned.file(1,1491,0)).hexdigest()
        assert sha256(source.file(1,1491,0)).hexdigest()==same_base
    finally:pinned.dat.close()
    pos = 3+count*6
    assert raw[pos] == 14  # Frame sound records: frame, synth, weight, loops, radius, retain.
    sounds = []
    records = int.from_bytes(raw[pos+1:pos+3], 'big')
    for i in range(records):
        frame, synth, weight, loops, radius, retain = struct.unpack('>HHBBBB', raw[pos+3+i*8:pos+11+i*8])
        assert source.file(4, synth, 0)
        sounds.append(dict(frame=frame, source=synth, weight=weight, loops=loops, radius=radius, retain=retain))
    assert raw[pos+3+records*8:] == b'\0'
    fixture = write_source(source.containers)
    proof = dict(cache=BUILD, build=242, timestamp='2026-11-11T11:45:06.321160Z',
                 sourceSequence=14498, name='DOG_UPDATE_DIG', sequenceSha256=sha256(raw).hexdigest(),
                 framesets=framesets, bases=sorted(bases), sameBase1491Sha256=same_base, sounds=sounds,
                 fixture=str(fixture.relative_to(ROOT)).replace('\\', '/'),
                 containers=[dict(index=i, group=g, sha256=sha256(b).hexdigest(), bytes=len(b),
                                  url=f'https://archive.openrs2.org/caches/runescape/{BUILD}/archives/{i}/groups/{g}.dat')
                             for (i, g), b in sorted(source.containers.items())])
    (ROOT/'docs/CLUE/PET-DIG-SOURCE.json').write_text(json.dumps(proof, indent=2)+'\n')
    (ROOT/'docs/CLUE/cache/pet-dig-14498.bin').write_bytes(raw)
    print('SOURCE ONLY:', fixture, '; framesets', framesets, '; bases', sorted(bases), '; sounds', sounds)


if __name__ == '__main__':
    main()
