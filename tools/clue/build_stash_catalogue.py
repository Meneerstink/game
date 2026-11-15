"""Exact selected STASH placements/recipes. Research only; no cache or world writes."""
from collections import defaultdict
from hashlib import sha256
import json
import re
from build_catalogue import ROOT, SOURCES, TIERS
from build_step_catalogue import templates


def main():
    manifest = {r['page']: r for r in json.loads((SOURCES/'manifest.json').read_text())['pages'] if 'file' in r}

    def pinned(page):
        pin = manifest[page]
        raw = (SOURCES/pin['file']).read_bytes()
        assert sha256(raw).hexdigest() == pin['sha256'], page
        return raw.decode('utf8')

    raw = pinned('STASH')
    nails = re.findall(r'\{\{CostLine\|([^}|]+)}}', pinned('Nails'))
    assert 'Dragon nails' in nails and "[[dragon nails]] do '''not''' work" in raw
    nails.remove('Dragon nails')
    assert len(nails) == 7
    source_items = json.loads((ROOT/'build/clue/osrs-items.json').read_text())
    local_items = json.loads((ROOT/'build/clue/local-items.json').read_text())
    names = defaultdict(list)
    for key, item in source_items.items():
        if item.get('geTradeable') and item.get('notedTemplate', -1) == -1 and item.get('placeholderTemplateId', -1) == -1:
            names[item['name'].casefold()].append(int(key))

    def identity(name):
        ids = names[name.casefold()]
        assert len(ids) == 1, (name, ids)
        osrs = ids[0]
        local = local_items.get(str(osrs), {})
        assert (local.get('name') or '').casefold() == name.casefold() and not local.get('98'), (name, local)
        return dict(name=name, osrsId=osrs, existingLocal=osrs)

    # Both display names have unrelated quest/event duplicates in the source
    # cache. Their item pages independently pin the canonical Construction IDs.
    for page in ('Gold leaf', 'Steel nails'):
        fields = next(templates(pinned(page), 'Infobox Item'))
        assert identity(page)['osrsId'] == int(fields['id'])

    recipes = {}
    for recipe in templates(raw, 'Recipe'):
        tier = recipe.get('output1subtxt', '').lower()
        if tier not in TIERS:
            continue
        assert tier not in recipes and recipe['skill1'] == 'Construction'
        materials = []
        for index in range(1, 4):
            name = recipe.get(f'mat{index}')
            if name is None:
                continue
            amount = int(recipe[f'mat{index}quantity'])
            accepted = nails if recipe.get(f'mat{index}txt') == 'Nails (any)' else [name]
            materials.append(dict(amount=amount, alternatives=[identity(n) for n in accepted], sameMetal=True if accepted == nails else None))
        recipes[tier] = dict(level=int(recipe['skill1lvl']), experience=int(recipe['skill1exp']),
            boostable=recipe['skill1boostable'].casefold() == 'yes', ticks=int(recipe['ticks']),
            tools=[identity(n.strip()) for n in recipe['tools'].split(',')], materials=materials)
    assert set(recipes) == set(TIERS)

    evidence_file = ROOT/'docs/CLUE/cache/step-assets.json'
    evidence = json.loads(evidence_file.read_text())
    steps = {r['key']: r for r in json.loads((ROOT/'data/cfg/clue/steps.json').read_text())}
    units = []
    for scene in evidence['scenes']:
        step = steps[scene['key']]
        if step['action'] != 'EMOTE':
            continue
        tier = step['tier']
        matching = [r for r in scene['placements'] if any(f['name'].startswith('Inconspicuous') and f['name'].endswith('('+tier+')') for f in r['forms'].values())]
        assert len(matching) == 1, (step['key'], len(matching))
        loc = matching[0]
        forms = loc['definition']['transforms']
        assert len(forms) == 3 and forms[2] == -1
        unbuilt, built = [loc['forms'][str(id)] for id in forms[:2]]
        assert unbuilt['name'].startswith('Inconspicuous') and unbuilt['options'][0] == 'Build'
        assert built['name'] == f'STASH ({tier})' and built['options'][1] == 'Search'
        selector = loc['sourceVarbit']
        assert selector['id'] == loc['definition']['varbitId'] and selector['leastBit'] == selector['mostBit']
        units.append(dict(key=step['key'], tier=tier, osrsId=loc['id'], physicalPoint=loc['point'], effectivePlane=loc['effectivePlane'],
            type=loc['type'], rotation=loc['rotation'], unbuiltId=forms[0], builtId=forms[1], sourceVarbit=selector,
            objectSha256=loc['sha256'], formSha256=loc['formSha256'], equipment=step['equipment']))
    assert len(units) == 17 and len({r['osrsId'] for r in units}) == 17
    result = dict(source=manifest['STASH'], cacheEvidenceSha256=sha256(evidence_file.read_bytes()).hexdigest(),
        activation='Pending object import/varbit remapping, world placement, personal storage handlers and client verification',
        rules=dict(sameNailMetal=True, nailsBend=False, fullSetOnly=True, withdrawAllOnly=True,
                   inventoryBeforeEquipment=True, equippedItemsRequireSetting=True), recipes=recipes, units=units)
    (ROOT/'data/cfg/clue/stashes.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    print('17 source/cache-checked STASH units; five exact recipes; seven legal nail metals; no activation/cache writes')


if __name__ == '__main__':
    main()
