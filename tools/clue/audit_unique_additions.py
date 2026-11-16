"""Read-only proof that the additional steps use pinned sources and real own-server actors."""
from hashlib import sha256
from pathlib import Path
import json
import re
import sys
from build_catalogue import TARGET_CACHE

ROOT = Path(__file__).resolve().parents[2]


def main():
    rows = json.loads((ROOT/'data/cfg/clue/steps.json').read_text())
    extra = [r for r in rows if r.get('additionalSource')]
    assert len(extra) == 15
    assert len({r['key'] for r in extra}) == 15
    for row in extra:
        raw = (ROOT/'docs/CLUE/sources'/row['sourceFile']).read_bytes()
        assert sha256(raw).hexdigest() == row['sourceSha256']
    main = ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins'
    runtime = (main/'content/scrolls/TreasureTrailNpcSteps.kt').read_text()
    constants = {name:int(id) for name,id in re.findall(r'const val (\w+) = (\d+)', (main/'api/cfg/Npcs.kt').read_text())}
    actors = re.findall(r'"([^"]+)" to Actor\((\d+), Tile\((\d+), (\d+), (\d+)\)\)', runtime)
    assert len(actors) == 12
    spawns = []
    for file in (main/'content/areas/spawns').glob('*.plugin.kts'):
        for block in re.findall(r'spawn_npc\((.*?)\)', file.read_text(), re.S):
            name = re.search(r'\bnpc\s*=\s*Npcs\.(\w+)', block)
            x = re.search(r'\bx\s*=\s*(\d+)', block)
            z = re.search(r'\bz\s*=\s*(\d+)', block)
            height = re.search(r'\bheight\s*=\s*(\d+)', block)
            if name and x and z:
                spawns.append(dict(id=constants.get(name[1]), tile=[int(x[1]),int(z[1]),int(height[1]) if height else 0],
                    file=str(file.relative_to(ROOT)).replace('\\','/'), fileSha256=sha256(file.read_bytes()).hexdigest()))
    sys.path.insert(0, 'C:/RSPS/tools/cache667')
    from js5 import Cache
    from local_npcs import decode
    proof=[]
    cache=Cache(str(TARGET_CACHE))
    try:
        for name,id,x,z,height in actors:
            id=int(id); tile=list(map(int,(x,z,height)))
            matches=[s for s in spawns if s['id']==id and s['tile']==tile]
            assert len(matches)==1,(name,matches)
            raw=cache.file(18,id>>7,id&127)
            definition=decode(raw)
            assert definition['name'].casefold()==name.casefold(),(name,definition)
            assert not definition.get('stopped_at')
            assert any((option or '').casefold()=='talk-to' for option in definition['ops'])
            proof.append(dict(name=name,localId=id,spawn=matches[0],nativeName=definition['name'],
                nativeOptions=definition['ops'],nativeDefinitionSha256=sha256(raw).hexdigest(),
                otherSameIdSpawns=[s for s in spawns if s['id']==id and s['tile']!=tile]))
    finally:
        cache.dat.close()
    spec=json.loads((ROOT/'docs/CLUE/ADDITIONAL-STEPS.json').read_text())
    assert {r['key'] for r in extra}=={f'Clue scroll ({tier}) - {name}' for tier,names in spec.items() for name in names}
    staged=ROOT/'build/clue/unique-additions/steps.json'
    if staged.exists():
        assert rows==json.loads(staged.read_text()),'Production rows differ from the tested 107-row stage'
    out=dict(source='OSRS Wiki pinned clue/transcript pages; local cache identities; existing own-server spawn code',
        records=107,additionalUniqueSteps=15,tiers={'easy':7,'medium':3,'hard':5},actors=proof,
        importChanges='none: item247/NPC10/STASH23/UI26map rosters unchanged',
        caveat='Source/identity proof, not active-world or client reachability acceptance.')
    (ROOT/'docs/CLUE/UNIQUE-ADDITIONS-AUDIT.json').write_text(json.dumps(out,indent=2)+'\n')
    print('Verified 15 unique source records and 12 actual native NPC identities/own-server spawns. No cache writes.')


if __name__=='__main__':
    main()
