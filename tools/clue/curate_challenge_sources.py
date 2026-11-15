"""Review-only Falo/Sherlock subchallenge selection; never changes runtime data during a frozen build."""
from collections import defaultdict
from hashlib import sha256
from pathlib import Path
import json
import re
from build_catalogue import ROOT, SOURCES
from build_step_catalogue import text


def main():
    manifest = {r['page']: r for r in json.loads((SOURCES/'manifest.json').read_text())['pages'] if 'file' in r}

    def source(page):
        pin = manifest[page]
        raw = (SOURCES/pin['file']).read_bytes()
        assert sha256(raw).hexdigest() == pin['sha256']
        return raw.decode('utf8'), pin

    items = json.loads((ROOT/'build/clue/osrs-items.json').read_text())
    local = json.loads((ROOT/'build/clue/local-items.json').read_text())
    # The deployed 667 cache calls item 7462 simply "Gloves". Its own item
    # metadata identifies it as Barrows-themed and all three model references
    # match cache2686's canonical Barrows gloves; this is an explicit alias,
    # never a name-only fallback for every generic pair of gloves.
    barrows_raw, barrows_pin = source('Barrows gloves')
    assert re.search(r'\|id\s*=\s*7462\b', barrows_raw)
    barrows = items['7462']; old_barrows = local['7462']
    assert old_barrows['name'] == 'Gloves'
    assert [old_barrows['model'], old_barrows['wear23'], old_barrows['wear25']] == [barrows['inventoryModel'], barrows['maleModel0'], barrows['femaleModel0']]
    metadata = (ROOT/'data/cfg/items.yml').read_text(encoding='utf8')
    assert 'Barrows-themed' in re.search(r'(?m)^- id: 7462\r?\n([\s\S]*?)(?=^- id:|\Z)', metadata)[1]
    aliases = {7462: 7462}
    local_by_name = defaultdict(list)
    for key, row in local.items():
        if row.get('name') and not row.get('98'):
            local_by_name[row['name'].casefold()].append(int(key))
    by_name = defaultdict(list)
    for key, row in items.items():
        if row.get('notedTemplate', -1) == -1 and row.get('placeholderTemplateId', -1) == -1:
            by_name[row['name'].casefold()].append(int(key))

    raw, falo_pin = source('Falo the Bard')
    raw = raw.split('==List of lyrics==')[1].split('|}')[0]
    falo = []
    for block in raw.split('|-\n')[1:]:
        names = re.findall(r'\{\{plinkt\|([^}|]+)', block)
        if not names:
            continue
        first = block.splitlines()[0].lstrip('|').strip()
        if not first.startswith('{{plinkt'):
            lyric = re.sub(r'^rowspan="\d+"\s*\|', '', first).strip()
            falo.append(dict(lyric=lyric, items=[], rawRows=[]))
        assert falo
        falo[-1]['rawRows'].append(block)
        for name in names:
            matches = []
            for osrs in by_name[name.casefold()]:
                target = local_by_name[items[str(osrs)]['name'].casefold()]
                matches.append(dict(osrs=osrs, existingLocal=aliases.get(osrs, target[0] if len(target) == 1 else None),
                    localCandidates=target, name=items[str(osrs)]['name']))
            falo[-1]['items'].append(dict(templateName=name, variants=matches))
    assert len(falo) >= 15 and len({r['lyric'] for r in falo}) == len(falo)
    selected_falo = {'Dragon scimitar', 'Armadyl helmet', 'Barrows gloves', 'Cooking gauntlets', 'Rune platebody', 'Tzhaar-ket-om', 'Fighter torso'}
    for row in falo:
        row['selected'] = any(item['templateName'] in selected_falo for item in row['items'])
        row['source'] = falo_pin
        row['note'] = 'Only shown, never consumed; use unnoted item on Falo. Notes/negative alternatives are retained in rawRows.'
        if row['selected']:
            assert any(variant['existingLocal'] is not None for item in row['items'] for variant in item['variants']), row['lyric']
    assert sum(r['selected'] for r in falo) == len(selected_falo)

    selection = {
        'elite': [('NATURE_RUNE', r'^Craft a nature rune\.'), ('SUPER_DEFENCE', r'^Create a super defence potion\.'),
                  ('GREEN_DHIDE_BODY', r'^Craft a green dragonhide body\.'), ('YEW_LONGBOW', r'^String a yew longbow\.'),
                  ('MITHRIL_ORE', r'^Mine a mithril ore\.'), ('SHARK', r'^Catch a raw shark\.'), ('YEW_LOG', r'^Cut a yew log\.')],
        'master': [('RUNE_MED_HELM', r'^Smith a runite med helm\.'), ('NECHRYAEL_TOWER', r'^Slay a Nechryael in the Slayer Tower\.'),
                   ('DRAGONSTONE_AMULET', r'^Create an unstrung dragonstone amulet at a furnace\.'), ('MAGIC_LOG', r'^Burn a magic log\.'),
                   ('ARDOUGNE_GEM_STALL', r'^Steal a gem from the Ardougne market\.'), ('RUNE_DART', r'^Fletch a rune dart\.')],
    }
    sherlock = []
    for tier, desired in selection.items():
        raw, pin = source('Sherlock/'+tier.title())
        rows = []
        for block in raw.split('|-\n')[1:]:
            cells = re.split(r'\n\|(?!})', block.rstrip())
            if len(cells) < 4:
                continue
            task = text(cells[0].lstrip('|'))
            key = next((key for key, pattern in desired if re.search(pattern, task)), None)
            skills = [dict(skill=skill, level=int(level)) for skill, level in re.findall(r'\{\{SCP\|([^|}]+)\|(\d+)', cells[1])]
            row = dict(tier=tier, key=key, selected=key is not None, task=task, skills=skills,
                       otherRequirements=cells[2].strip(), items=cells[3].split('\n|}')[0].strip(), rawRow=block, source=pin)
            rows.append(row)
        assert {r['key'] for r in rows if r['selected']} == {key for key, _ in desired}, (tier, [r['task'] for r in rows])
        sherlock.extend(rows)

    source_items = {id: dict(name=items[str(id)]['name'], options=items[str(id)]['inventoryOptions']) for id in
                    [2842, 7269, 12128, 12139, 19835, 19939]}
    result = dict(activation='RESEARCH ONLY: no runtime data or handler changes', falo=falo, sherlock=sherlock,
        itemEvidence=source_items, rules={
            'falo': 'Use the requested unnoted item on the NPC; showing it does not consume it.',
            'sherlock': 'Actual successful action while the challenge is held; cannot complete by merely owning/buying the result.',
            'numeric': 'Answer by talking to the issuing NPC again; reading is optional; wrong answers have no penalty.',
            'hotCold': 'Only active master hot/cold: Feel damages 3..8 and may kill. No damage when inactive; Jorral gives/retains a bankable device.',
        }, nativeAliases=[dict(osrs=7462, local=7462, source=barrows_pin, targetName='Gloves',
            metadataExamine='A pair of Barrows-themed gloves.', models=[13631, 13307, 13319])],
        extraSources=[manifest[page] for page in ['Challenge scroll', 'Sherlock', 'Transcript:Sherlock',
            'Transcript:Falo the Bard', 'Transcript:Jorral', 'Strange device']])
    destination = ROOT/'docs/CLUE/CHALLENGE-CANDIDATES.json'
    destination.write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    print('Verified source-only candidates:', sum(r['selected'] for r in falo), 'Falo lyrics,',
          sum(r['selected'] for r in sherlock), 'Sherlock tasks; no runtime/cache writes')


if __name__ == '__main__':
    main()
