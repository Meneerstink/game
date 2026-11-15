"""Resolve the reviewed 69-step selection into explicit runtime actions and cache identities.

No cache writes. Unresolved equipment/NPC identities stay visible in the report;
they never become an empty requirement or a permissive name-only fallback.
"""
from collections import defaultdict, Counter
from hashlib import sha256
from pathlib import Path
import json
import re
import sys

from build_catalogue import ROOT, SOURCES, TARGET_CACHE


def normalize(name):
    return name.casefold().strip()


def main():
    rows = json.loads((ROOT/'docs/CLUE/STEPS-CURATED.json').read_text(encoding='utf8'))
    source_items = json.loads((ROOT/'build/clue/osrs-items.json').read_text(encoding='utf8'))
    local_items = json.loads((ROOT/'build/clue/local-items.json').read_text(encoding='utf8'))
    source_names = defaultdict(list)
    local_names = defaultdict(list)
    for key, item in source_items.items():
        if item.get('notedTemplate', -1) == -1 and item.get('placeholderTemplateId', -1) == -1:
            source_names[normalize(item['name'])].append(int(key))
    for key, item in local_items.items():
        if not item.get('98') and not item.get('122'):
            local_names[normalize(item.get('name') or '')].append(int(key))
    # These spellings are verified by the explicit source/local cache identity pair.
    aliases = {'blue wizard hat': ('wizard hat', 579), 'cape of legends': ("legends' cape", 1052),
               'iron med helm': ('iron med helm', 1137), 'ham boots': ('h.a.m. boots', 4310),
               'steel axe': ('steel hatchet', 1353), 'purple gloves': ('gloves', 2942),
               'dragonstone amulet': ('dragonstone ammy', 1702),
               'castle wars bracelet(1)': ('castle wars brace (1)', 11083),
               'castle wars bracelet(2)': ('castle wars brace (2)', 11081),
               'castle wars bracelet(3)': ('castle wars brace (3)', 11079)}

    def local_id(osrs):
        item = source_items[str(osrs)]
        name = normalize(item['name'])
        exact = local_items.get(str(osrs), {})
        if normalize(exact.get('name') or '') == name and not exact.get('98'):
            return osrs
        match = local_names[name]
        if len(match) == 1:
            return match[0]
        if name in aliases:
            target_name, target_id = aliases[name]
            if normalize(local_items.get(str(target_id), {}).get('name') or '') == target_name:
                return target_id
        return None

    sys.path.insert(0, 'C:/RSPS/tools/cache667')
    from js5 import Cache
    from local_npcs import decode
    cache = Cache(str(TARGET_CACHE))
    npcs = defaultdict(list)
    for group, files in cache.reference(18).items():
        for file in files:
            raw = cache.file(18, group, file)
            if not raw:
                continue
            item = decode(raw)
            if item.get('name') and not item.get('stopped_at'):
                npcs[normalize(item['name'])].append(dict(id=(group << 7) | file,
                    level=item.get('level'), options=item['ops'], sha256=sha256(raw).hexdigest()))
    cache.dat.close()

    result, gaps = [], []
    for row in rows:
        raw = (SOURCES/row['sourceFile']).read_bytes()
        assert sha256(raw).hexdigest() == row['sourceSha256'], row['key']
        fields = row.get('fields', {})
        notes = fields.get('notes', '')
        kind = row.get('subtype', row['kind'])
        action, npc = kind, None
        if kind == 'CRYPTIC' or kind == 'THREE_PART_COMPONENT':
            if notes.startswith('Dig '):
                action = 'DIG'
            elif notes.startswith('Speak to '):
                action = 'TALK'
            elif notes.startswith('Search ') or ' - Search ' in row['key']:
                action = 'SEARCH'
            else:
                raise ValueError(('Unclassified cryptic action', row['key'], notes))
        elif kind in ('ANAGRAM', 'CIPHER'):
            action = 'TALK'
        elif kind == 'MAP':
            action = 'SEARCH' if 'where to search' in fields['text'] else 'DIG'
        elif kind == 'COORDINATE':
            action = 'DIG'
        if action == 'TALK':
            match = re.search(r'Speak to (?:the )?\[\[([^]|]+)', notes)
            if match:
                npc = match[1]
            elif row['key'] == 'master:mandrith':
                npc = 'Mandrith'
            elif row['key'] == 'master:radimus':
                npc = 'Radimus Erkle'
            else:
                raise ValueError(('Missing source NPC', row['key'], notes))
        if kind == 'SHERLOCK': npc = 'Sherlock'
        if kind == 'FALO': npc = 'Falo the Bard'
        if kind == 'HOT_COLD': npc = 'Jorral'
        if kind == 'KILL': npc = re.search(r'Kill an? \[\[([^]|]+)', notes)[1]
        equipment, missing = [], []
        for group in (1, 2, 3):
            names = [v for k, v in fields.items() if re.fullmatch(fr'equipment{group}(?:alt\d+)?', k)]
            accepted = []
            for name in names:
                ids = [id for id in source_names[normalize(name)] if source_items[str(id)].get('wearPos1', -1) >= 0]
                if name == 'Castle wars bracelet':
                    ids = [id for suffix in (1,2,3) for id in source_names[f'castle wars bracelet({suffix})']]
                if not ids:
                    # Lockpicks are travel supplies in this Wiki template, never worn gear.
                    unwearable = source_names[normalize(name)]
                    if unwearable and all(source_items[str(id)].get('wearPos1', -1) < 0 for id in unwearable):
                        continue
                    missing.append(dict(equipment=name, reason='Source wearable identity missing'))
                for id in ids:
                    accepted.append(dict(osrs=id, local=local_id(id), name=source_items[str(id)]['name']))
            if accepted:
                equipment.append(accepted)
        requirement = fields.get('items', '')
        extra = {}
        if row['key'].endswith('Anger Abbot Langley'): extra['prayerBonusBelow'] = 0
        if row['key'].endswith('Robin wishes to see your finest ranged equipment'):
            extra.update(rangedBonusAtLeast=180, excludeBlowpipeBonus=True)
        if kind == 'EMOTE':
            extra['onlyListedEquipment'] = requirement.startswith('Equip only ')
            extra['emptySlots'] = ['BODY', 'LEGS'] if 'nothing on your chest and legs' in requirement else []
        requirements_source = None
        if 'prayerBonusBelow' in extra or 'rangedBonusAtLeast' in extra:
            guide = 'Treasure_Trails__Guide__Cryptic_clues__Master.wiki'
            requirements_source = dict(file=guide, sha256=sha256((SOURCES/guide).read_bytes()).hexdigest())
        record = dict(key=row['key'], tier=row['tier'], kind=kind, action=action,
            hint=row.get('hint'), points=row.get('points', []), npcName=npc,
            npcCandidates=npcs[normalize(npc)] if npc else [], equipment=equipment,
            challenge=row.get('challenge', {}), requirements=extra, requirementsSource=requirements_source,
            source=row['source'], sourceFile=row['sourceFile'], sourceSha256=row['sourceSha256'])
        result.append(record)
        if missing: gaps.append(dict(key=row['key'], problems=missing))
        absent = [item for group in equipment for item in group if item['local'] is None]
        if absent: gaps.append(dict(key=row['key'], reason='Unresolved optional equipment alternatives', items=absent))
        if npc and not record['npcCandidates']: gaps.append(dict(key=row['key'], npc=npc, reason='NPC absent from target cache'))
    assert len(result) == 69
    (ROOT/'data/cfg/clue/steps.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    (ROOT/'docs/CLUE/STEP-ASSET-GAPS.json').write_text(json.dumps(gaps,indent=2)+'\n',encoding='utf8')
    print('69 explicit runtime records:', dict(Counter(r['action'] for r in result)), '; source/asset gaps:', len(gaps))
    print('No runtime registration or cache writes. Missing target alternatives remain local=null.')


if __name__ == '__main__':
    main()
