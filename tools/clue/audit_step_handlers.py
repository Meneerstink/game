"""Trace every selected source row to its actual adapter and explicit source/asset gates.

This is a static implementation inventory, NEVER an active-world count. No cache
writer, player data, source generation, registration or server process is used.
"""
from collections import Counter
from hashlib import sha256
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[2]
MAIN=ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'

def main():
    rows=json.loads((ROOT/'data/cfg/clue/steps.json').read_text(encoding='utf8'))
    original=json.loads((ROOT/'docs/CLUE/ORIGINAL-POOL-AUDIT.json').read_text(encoding='utf8'))
    originals={r['candidateSource'] for r in original['records'] if r.get('inCurrentCatalogue')}
    assert len(rows)==107 and len(originals)==38
    result=[];hashes={}
    for row in rows:
        raw=(ROOT/'docs/CLUE/sources'/row['sourceFile']).read_bytes()
        assert sha256(raw).hexdigest()==row['sourceSha256'],row['key']
        action=row['action'];challenge=row['challenge'].get('type','NONE');kind=row['kind']
        gates=[];assets=['reward/text UI; final applied item mapping']
        if kind=='THREE_PART_COMPONENT':
            adapters=['TreasureTrailTornParts','TreasureTrailTornDialogue','TreasureTrailThreePartStep']
            assets+=['source19837/19838/19839; one grouped master choice']
        elif action=='HOT_COLD':
            adapters=['TreasureTrailHotCold','TreasureTrailHotColdDialogue','TreasureTrailDigGuards']
            assets+=['source19939 device; verified guardian NPC/combat assets']
            gates+=['Q8: all possible Brassican/Ancient visual identities required']
        elif challenge=='GUARD':
            ids=row['challenge']['guards']
            if set(ids)<=set([2954,2955]):adapters=['TreasureTrailWizards','TreasureTrailGuardians']
            else:
                adapters=['TreasureTrailDigGuards','TreasureTrailGuardians','ClueGuardianCombat']
                gates+=['Q8: per-guardian verified visual rows and applied combat mapping']
                if row['challenge'].get('spawnMode')=='CHOICE':gates+=['Q8: elite alternative choice probability not sourced']
            assets+=['guard source IDs '+','.join(map(str,ids))]
        elif challenge=='DOUBLE_AGENT':
            adapters=['TreasureTrailAgents','TreasureTrailGuardians','TreasureTrailEmotes','TreasureTrailUri']
            assets+=['native5144/5145 or imported source7312; Uri; valid gear']
        elif action=='EMOTE':
            adapters=['TreasureTrailEmotes','TreasureTrailUri']
            assets+=['Uri; every required gear group; optional alternatives may be absent']
        elif challenge=='NUMBER':
            adapters=['TreasureTrailBasicSteps','TreasureTrailNumberChallenge','TreasureTrailNumberDialogue']
            assets+=['tier paper2842/7269/12139; actual named Talk-to actor']
            if not row['npcCandidates']:
                if row['key'].endswith('XJABSE USBJCPSO'):
                    source=(MAIN/'TreasureTrailNpcSteps.kt').read_text(encoding='utf8')
                    if 'private const val TRAIBORN_CIPHER' not in source:gates+=['native881 Wizard-prefix alias staged, not promoted']
                else:gates+=['no native NPC candidate; identity still unresolved']
        elif challenge=='SLIDING_PUZZLE':
            adapters=['TreasureTrailPuzzleBoxes','TreasureTrailPuzzleDialogue']
            assets+=['source306 UI/picture atlas; tree boxes19887/23173; source306 Guide or native Gunnjorn']
        elif challenge=='PUZZLE_CHOICE':
            adapters=['TreasureTrailMasterPuzzles','TreasureTrailPuzzleBoxes','TreasureTrailLightBoxes']
            assets+=['source306/322 UI and pictures;5master box types']
        elif action=='FALO':
            adapters=['TreasureTrailFalo','TreasureTrailFaloDialogue'];assets+=['source7306 Falo; source requested-item catalogue']
        elif action=='SHERLOCK':
            adapters=['TreasureTrailSherlock','TreasureTrailSherlockDialogue'];assets+=['source6586 Sherlock; actual skill action hooks']
        elif action in ['DIG','SEARCH','TALK','KILL']:
            adapters=['TreasureTrailBasicSteps']
            if action=='SEARCH':adapters+=['TreasureTrailSearchTargets'];assets+=['exact scenery/menu; bucket Search batch where used']
            if action=='TALK':adapters+=['TreasureTrailNpcSteps'];assets+=['exact native Talk-to actor and source dialogue']
            if action=='KILL':assets+=['exact source NPC variants; kill notification']
        else:raise AssertionError(('unclassified adapter',row['key'],action,challenge))
        if kind=='MAP':adapters+=['TreasureTrailMaps'];assets+=['exact map UI in26-map batch']
        for name in adapters:
            path=MAIN/(('TreasureTrailTornParts' if name=='TreasureTrailThreePartStep' else name)+'.kt');assert path.is_file(),(row['key'],path)
            hashes[str(path.relative_to(ROOT)).replace('\\','/')]=sha256(path.read_bytes()).hexdigest()
        result.append(dict(key=row['key'],tier=row['tier'],original=row['key'] in originals,kind=kind,action=action,
            challenge=challenge,adapters=adapters,assetRequirements=assets,sourceGates=gates,source=row['source']))
    assert sum(not r['original'] for r in result)==69
    counts={tier:dict(records=sum(r['tier']==tier for r in result),
            sourceGated=sum(r['tier']==tier and bool(r['sourceGates']) for r in result))
            for tier in ['easy','medium','hard','elite','master']}
    report=dict(kind='STATIC_ADAPTER_INVENTORY_NOT_ACTIVE_POOL',records=len(rows),originals=38,newSourceRecords=69,
        groupedTrailChoices=105,note='Three master torn parts are one choice. Every import/readiness predicate and loaded-client check still applies.',
        tiers=counts,sourceHashes=hashes,steps=result)
    (ROOT/'docs/CLUE/STEP-HANDLER-AUDIT.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print('107 source records /105 grouped choices,38 originals+69new; source gates:',counts)

if __name__=='__main__':main()
