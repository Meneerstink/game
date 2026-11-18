"""Pinned per-variant clue-guardian stats; no inferred levels or target-cache writes."""
from hashlib import sha256
from pathlib import Path
import json
import re
from build_step_catalogue import templates, text
from build_catalogue import ROOT, SOURCES

SPECS = [
    ('Double agent', 1, 1777, 5144), ('Double agent', 2, 1778, 5145), ('Double agent', 3, 7312, None),
    ('Zamorak wizard', 0, 2954, 1007), ('Saradomin wizard', 0, 2955, 1264),
    ('Armadylean guard', 0, 6587, None), ('Bandosian guard', 0, 6588, None),
    ('Ancient Wizard', 1, 7307, None), ('Ancient Wizard', 2, 7308, None), ('Ancient Wizard', 3, 7309, None),
    ('Brassican Mage', 0, 7310, None),
    ('Third Age Warrior', 0, 8635, 14403), ('Third Age Ranger', 0, 8636, 14404),
    ('Third Age Mage', 0, 8637, 14405),
]


def main():
    pins = {r['page']: r for r in json.loads((SOURCES/'manifest.json').read_text())['pages'] if 'file' in r}
    source = json.loads((ROOT/'docs/CLUE/cache/step-assets.json').read_text())['npcs']
    local = json.loads((ROOT/'build/clue/local-npcs.json').read_text())
    rows = []
    for page, version, osrs, existing in SPECS:
        pin = pins[page]
        raw = (SOURCES/pin['file']).read_bytes()
        assert sha256(raw).hexdigest() == pin['sha256'], page
        fields = next(templates(raw.decode('utf8'), 'Infobox Monster'))

        def field(key):
            value = fields.get(key+str(version)) if version else None
            return value if value is not None else fields[key]

        def number(key):
            value = field(key)
            assert re.fullmatch(r'-?\d+(?:\.\d+)?', value), (page, version, key, value)
            return float(value) if '.' in value else int(value)

        npc = source[str(osrs)]['definition']
        assert number('combat') == npc['combat'] and number('size') == npc['size']
        assert text(field('name')).casefold() == npc['name'].casefold()
        assert str(osrs) in re.findall(r'\d+', field('id')), (page, osrs)
        if existing is not None:
            target = local[str(existing)]
            assert target['name'].casefold() == npc['name'].casefold() and target['level'] == npc['combat']
        row = dict(osrsId=osrs, existingLocal=existing, name=npc['name'], level=npc['combat'], size=npc['size'],
                   examine=text(field('examine')), hitpoints=number('hitpoints'), attackSpeed=number('attack speed'),
                   experienceBonusPercent=number('xpbonus'),
                   attackStyle=text(field('attack style')), maxHitText=text(field('max hit')),
                   stats={name: number(key) for name, key in dict(attack='att', strength='str', defence='def', magic='mage', ranged='range').items()},
                   bonuses={name: number(key) for name, key in dict(attackMelee='attbns', strengthMelee='strbns', attackMagic='amagic',
                       magicDamage='mbns', attackRanged='arange', strengthRanged='rngbns', defenceStab='dstab', defenceSlash='dslash',
                       defenceCrush='dcrush', defenceMagic='dmagic', defenceRangedLight='dlight', defenceRangedStandard='dstandard',
                       defenceRangedHeavy='dheavy').items()},
                   poisonous=text(field('poisonous')), poisonResistance=number('poisonresistance'), venomResistance=number('venomresistance'),
                   source=dict(url=pin['url'], revision=pin['revision'], file=pin['file'], sha256=pin['sha256'],
                               cacheSha256=source[str(osrs)]['sha256']))
        rows.append(row)
    assert len(rows) == len(SPECS) and len({r['osrsId'] for r in rows}) == len(SPECS)
    destination = ROOT/'data/cfg/clue/guardians.json'
    destination.write_text(json.dumps(rows,indent=2)+'\n',encoding='utf8')
    print(f'{len(rows)} source/cache-checked guardians/minions; no combat activation or cache writes')
    for row in rows: print(row['osrsId'], row['name'], 'HP', row['hitpoints'], row['attackStyle'], 'speed', row['attackSpeed'])


if __name__ == '__main__':
    main()
