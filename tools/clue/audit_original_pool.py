"""Reconcile the original 41 donor locations with individually pinned OSRS rows.

Candidate identity is explicit, never a nearest-tile match. Presence in a source
catalogue is NOT proof of live activation, map illustration or area reachability.
No cache writer or gameplay registration is used.
"""
from collections import Counter
from hashlib import sha256
from pathlib import Path
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BLOB = '3ce55b818^:game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls/TreasureTrailSteps.kt'
MAPS = {
    337: ('easy', 'North of Falador.'),
    338: ('hard', 'South-east of the Wilderness Agility Course'),
    339: ('hard', "South of the Legends' Guild"),
    340: ('medium', 'Miscellania'),
    341: ('medium', 'Mort Myre Swamp'),
    342: ('medium', 'At the entrance to the Ourania Cave'),
    343: ('medium', 'South-east of the Lighthouse'),
    344: ('medium', "Between Seers' Village and Rellekka"),
    346: ('easy', "West of the Champions' Guild"),
    347: ('easy', 'South-east Varrock mine'),
    348: ('medium', 'South of Draynor Village bank'),
    349: ('medium', 'To the east of the Ranging guild'),
    350: ('hard', 'A crate in the Lumber Yard'),
    351: ('easy', 'At the standing stones north of Falador'),
    352: ('medium', 'West of the Crafting Guild'),
    353: ('hard', 'Yanille anvils'),
    354: ('easy', "Brother Galahad's house"),
    355: ('medium', "The crate in McGrubor's Wood"),
    357: ('hard', 'In the western section of West Ardougne'),
    358: ('hard', 'In a crate by the stairs to the Observatory Dungeon'),
    359: ('hard', "In a crate at the Dark Warriors' Fortress"),
    360: ('medium', 'North of the Tower of Life'),
    361: ('medium', 'The crate west of the Clock Tower'),
    362: ('medium', "Behind the Chemist's house in Rimmington"),
}
EMOTES = [
    ('easy', 'Headbang in the mine north of Al Kharid'),
    ('hard', 'Salute in the banana plantation'),
    ('medium', 'Cheer in the Barbarian Agility Arena'),
    ('easy', 'Jump for joy at the beehives'),
    ('easy', 'Dance at the crossroads north of Draynor'),
    ('easy', "Cheer at the Druids' Circle"),
    ('easy', "Bow in the office of the Emir's Arena"),
    ('easy', 'Dance a jig by the entrance to the Fishing Guild'),
    ('hard', 'Blow a raspberry in the Fishing Guild bank'),
    ('easy', 'Dance in the shack in Lumbridge Swamp'),
    ('easy', 'Dance in the Party Room'),
    ('easy', 'Cheer for the monks at Port Sarim'),
    None,  # Rogue's Castle: no matching current source record in the pinned archive.
    ('hard', "Yawn in the rogues' general store"),
    ('easy', "Clap on the causeway to the Wizards' Tower"),
    ('medium', 'Jump for joy in Yanille bank'),
    None,  # Eastern Wilderness temple: same explicit source gap; no invented replacement.
]


def main():
    raw = subprocess.check_output(['git', 'show', BLOB], cwd=ROOT)
    text = raw.decode()
    archive = {r['key']: r for r in json.loads((ROOT/'docs/CLUE/steps-source.json').read_text())}
    pins = {r['file']: r for r in json.loads((ROOT/'docs/CLUE/sources/step-manifest.json').read_text())['pages'] if 'file' in r}
    runtime = {r['key']: r for r in json.loads((ROOT/'data/cfg/clue/steps.json').read_text())}
    curated = json.loads((ROOT/'docs/CLUE/STEPS-CURATED.json').read_text())
    initial_selection = {r['key'] for r in curated if not r.get('restoredOriginal')}
    assert len(initial_selection) == 69
    records = []

    def add(kind, point, hint, identity, interface=None):
        key = f'Clue scroll ({identity[0]}) - {identity[1]}' if identity else None
        source = archive[key] if key else None
        if source:
            assert source['kind'] == kind and not source['eventSpecific']
            digest = sha256((ROOT/'docs/CLUE/sources'/source['sourceFile']).read_bytes()).hexdigest()
            assert digest == pins[source['sourceFile']]['sha256']
            if key in runtime: assert digest == runtime[key]['sourceSha256']
        records.append(dict(kind=kind, donorPoint=point, donorHint=hint, donorInterface=interface,
            candidateSource=key, sourcePoints=source['points'] if source else None,
            source=source['source'] if source else None, sourceFile=source['sourceFile'] if source else None,
            sourceSha256=digest if source else None, sourceTier=source['tier'] if source else None,
            inCurrentCatalogue=key in runtime, overlapsInitial69=key in initial_selection,
            status='source handler/asset gates still apply' if key in runtime else
                'pending source UI/scene/handler review' if source else 'no current pinned source identity'))

    for x, z, plane, group in re.findall(r'MapClueLocation\(Tile\((\d+), (\d+), (\d+)\), (\d+)\)', text):
        add('MAP', list(map(int, (x, z, plane))), None, MAPS[int(group)], int(group))
    blocks = text[text.index('val EMOTE_CLUE_LOCATIONS:'):].split('EmoteClueLocation(')[1:]
    assert len(blocks) == len(EMOTES) == 17
    for block, identity in zip(blocks, EMOTES):
        point = list(map(int, re.search(r'Tile\((\d+), (\d+), (\d+)\)', block).groups()))
        hint = re.search(r'listOf\("([^"]+)"', block)[1]
        add('EMOTE', point, hint, identity)
    assert len(records) == 41
    present = [r for r in records if r['inCurrentCatalogue']]
    overlaps = {r['candidateSource'] for r in records if r['overlapsInitial69']}
    result = dict(donorBlob=BLOB, donorBlobSha256=sha256(raw).hexdigest(),
        caveat='Source-catalogue counts only; NOT actual active choices. No donor interface is reused by numeric coincidence.',
        originalLocations=41, originalInCatalogue=len(present), originalDeferred=41-len(present),
        originalSourceTiers=dict(Counter(r['sourceTier'] or 'unresolved' for r in records)),
        originalInCatalogueTiers=dict(Counter(r['sourceTier'] for r in present)),
        initialSelectionRecords=69, initialSelectionOriginalOverlap=len(overlaps),
        initialSelectionUniqueAdditions=69-len(overlaps),
        currentCatalogueRecords=len(runtime), currentCatalogueTiers=dict(Counter(r['tier'] for r in runtime.values())),
        tornPartsAreOneChoice=True, records=records)
    (ROOT/'docs/CLUE/ORIGINAL-POOL-AUDIT.json').write_text(json.dumps(result, indent=2)+'\n')
    print({k:v for k,v in result.items() if k != 'records'})


if __name__ == '__main__':
    main()
