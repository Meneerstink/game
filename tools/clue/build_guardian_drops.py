"""Extract guardian loot as exact slot trees from pinned Wiki/cache evidence.

The default output is research data; --runtime promotes the same verified tree.
Does not open or change either target cache. Looting bags are owner-excluded.
"""
from fractions import Fraction
from collections import defaultdict
from functools import cache
from hashlib import sha256
from math import lcm
from pathlib import Path
import json
import re
import sys
from build_catalogue import ROOT, SOURCES, canonical, split_template
from build_step_catalogue import templates


def main():
    pins = {r['page']: r for r in json.loads((SOURCES/'manifest.json').read_text())['pages'] if 'file' in r}
    pins.update({r['page']: r for r in json.loads((SOURCES/'guardian-drop-manifest.json').read_text()) if 'file' in r})
    used = {}

    def raw(page):
        pin = pins[page]
        data = (SOURCES/pin['file']).read_bytes()
        assert sha256(data).hexdigest() == pin['sha256'], page
        used[page] = pin
        return data.decode('utf8')

    source = {int(k): v for k,v in json.loads((ROOT/'build/clue/osrs-items.json').read_text()).items()}
    local = {int(k): v for k,v in json.loads((ROOT/'build/clue/local-items.json').read_text()).items()}
    identities = {}

    def identity_name(name):
        # 667 uses the shorter herb names for the same grimy plant.
        return canonical({'Grimy guam leaf':'Grimy guam','Grimy irit leaf':'Grimy irit',
                          'Adamantite bar':'Adamant bar', 'Runite crossbow (u)':"Runite c'bow (u)"}.get(name,name))

    source_names, local_names = defaultdict(list), defaultdict(list)
    for i,v in source.items():
        if v.get('notedTemplate',-1) < 0 and v.get('placeholderTemplateId',-1) < 0 and v.get('boughtTemplateId',-1) < 0 and v.get('geTradeable'):
            source_names[canonical(v.get('name',''))].append(i)
    for i,v in local.items():
        if v.get('98',-1) < 0 and v.get('149',-1) < 0 and v.get('140',-1) < 0:
            local_names[identity_name(v.get('name') or '')].append(i)

    @cache
    def item(name, noted=False):
        if name == 'Nothing': return dict(kind='nothing')
        candidates = source_names[canonical(name)]
        if name == 'Coins':
            # Cache currency995 is not sold on the GE; old coins617 are a quest prop.
            assert source[995]['name'] == 'Coins' and source[995]['stackable'] == 1
            candidates = [995]
        assert len(candidates) == 1, (name,candidates)
        osrs = candidates[0]
        targets = local_names[identity_name(name)]
        if name in ['Zamorak monk top','Zamorak monk bottom']:
            # Both 667 robe pieces share a label; the stable cache identity and
            # torso/legs wear positions distinguish them without a name guess.
            wear = 4 if name.endswith('top') else 7
            assert local[osrs]['name'] == 'Zamorak robe' and source[osrs]['wearPos1'] == wear
            targets = [osrs]
        if osrs in targets: targets = [osrs]
        assert len(targets) <= 1, (name,targets)
        native = targets[0] if targets else None
        if noted:
            osrs = source[osrs]['notedId']
            native = local[native]['97'] if native is not None else None
            assert source[osrs]['notedTemplate'] >= 0 and (native is None or local[native]['98'] >= 0)
        identities[str(osrs)] = dict(name=name, noted=noted, local=native)
        return dict(kind='item', osrs=osrs, local=native)

    def entry(fields):
        name = fields['name']
        if name == 'Looting bag': return None
        quantity = fields.get('quantity','1')
        match = re.fullmatch(r'(\d+)(?:-(\d+))?( \(noted\))?',quantity)
        assert match, (name,quantity)
        return dict(**item(name, bool(match[3])), minimum=int(match[1]), maximum=int(match[2] or match[1]))

    def rows(page):
        data = raw(page)
        result = []
        for fields in templates(data,'DropsLine'):
            if fields.get('name') == 'Looting bag': continue
            chance = Fraction(1) if fields['rarity'] == 'Always' else Fraction(fields['rarity'])
            result.append((entry(fields),chance))
        # Named shared tables each occupy one main-table outcome, not extra rolls.
        for template, key in [('HerbDropLines','herbs'),('AllotmentSeedDropLines','seeds'),('GemDropTable','gems')]:
            for match in re.finditer(r'\{\{'+template+r'\|([^|{}]+)',data,re.I):
                result.append((dict(kind='table',table=key),Fraction(match[1])))
        return result

    def table(entries):
        denominator = lcm(*(chance.denominator for _,chance in entries))
        occupied = sum(chance for _,chance in entries)
        assert occupied <= 1, occupied
        return dict(total=denominator, entries=[dict(**row,slots=int(chance*denominator)) for row,chance in entries])

    shared = {}
    # Read weights from expressions, preserving the exact /128 fractions rather
    # than the Wiki's rounded inverse rates. Names and quantities are source rows.
    for page,key,variable in [('Template:HerbDropLines','herbs','herbbase'),
                              ('Template:AllotmentSeedDropLines','seeds','vasdtbase')]:
        data = raw(page)
        entries = []
        for fields in templates(data,'DropsLine'):
            match = re.search(r'(\d+)\*\{\{#var:'+variable+r'}}',fields['rarity'])
            assert match, fields
            quantity = fields.get('quantity','1')
            if key == 'herbs': quantity = '1'  # {{{2|1}}}: caller supplies no quantity.
            else: quantity = re.sub(r'\{\{\{[^|{}]+\|([^{}]+)}}}',r'\1',quantity)
            fields['quantity'] = quantity
            entries.append((entry(fields),Fraction(int(match[1]),128)))
        assert sum(chance for _,chance in entries) == 1
        shared[key] = table(entries)

    # Gem and mega-rare table rows are pinned separately. Runtime selects one
    # talisman by source coordinates, and the Legends' Quest/ring branches.
    gemdata = raw('Gem drop table')
    gem_sections = gemdata.split('==Gem Drop Table==')[1].split('==Talisman==')[0].split('==Mega-rare drop table==')
    for key, section in zip(['gems','mega'],gem_sections):
        entries = []
        for fields in templates(section,'DropsLine'):
            name = fields['name']
            if name == 'Chaos talisman': continue  # Mutually exclusive geographic variant.
            row = dict(kind='talisman') if name == 'Nature talisman' else (
                dict(kind='table',table='mega') if name == 'Mega-rare drop table' else entry(fields))
            entries.append((row,Fraction(fields['rarity'])))
        assert sum(chance for _,chance in entries) == 1
        shared[key] = table(entries)
    talismans = {name: item(name) for name in ['Chaos talisman','Nature talisman']}
    raw('Module:GemDropLines')
    profiles = [(name,ids) for name,ids in [
        ('Double agent',[1777,1778,7312]),('Saradomin wizard',[2955]),('Zamorak wizard',[2954]),
        ('Armadylean guard',[6587]),('Bandosian guard',[6588]),('Ancient Wizard',[7307,7308,7309]),('Brassican Mage',[7310])]]
    guardians = {}
    for name,ids in profiles:
        extracted = rows(name)
        guaranteed = [row for row,chance in extracted if chance == 1]
        pre = [(row,chance) for row,chance in extracted if chance.denominator == 2000000]
        main = [(row,chance) for row,chance in extracted if chance < 1 and chance.denominator != 2000000]
        assert len(guaranteed) == 1 and guaranteed[0]['osrs'] == 526
        for source_id in ids:
            guardians[str(source_id)] = dict(page=name, guaranteed=guaranteed, main=table(main) if main else None,
                                            pre=table(pre) if pre else None)
    out = dict(sources=used, items=identities, talismans=talismans, shared=shared, guardians=guardians,
               ownerExcluded=['Looting bag'], note='Elite 1/1000000 god-item pre-roll replaces main loot; each god item is 1/2000000.')
    destination = ROOT/('data/cfg/clue/guardian-drops.json' if '--runtime' in sys.argv else 'docs/CLUE/cache/guardian-drops.json')
    destination.write_text(json.dumps(out,indent=2)+'\n',encoding='utf8')
    print(f'{len(guardians)} guardian variants, {len(identities)} verified item identities, exact shared trees; {destination}')


if __name__ == '__main__':main()
