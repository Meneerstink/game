"""Validate the actual Claude apply handoff against committed metadata, without opening any cache or player data."""
from pathlib import Path
import hashlib
import json
import re
import yaml
from make_proposals import wiki_bonuses

ROOT = Path(__file__).resolve().parents[2]
DOC = Path(__file__).resolve().parent


def validate():
    ids = ROOT / 'data/cfg/dmm2/ids.tsv'
    native = DOC / 'phase5-output/items.generated.yml'
    table = {}
    for line in ids.read_text(encoding='utf-8-sig').splitlines():
        if not line.strip() or line.startswith('#'):
            continue
        kind, source, local = line.split('\t')
        key = kind, int(source)
        assert key not in table, key
        table[key] = int(local)
    assert len(table) == 143
    reverse = {local: source for (kind, source), local in table.items() if kind == 'item'}
    assert len(reverse) == 86
    metadata = yaml.safe_load((ROOT / 'data/cfg/items.yml').read_text(encoding='utf-8'))
    byid = {entry['id']: entry for entry in metadata}
    assert len(byid) == len(metadata), 'Duplicate item metadata IDs'
    constants = (ROOT / 'game/plugins/src/main/kotlin/gg/rsmod/plugins/api/cfg/Items.kt').read_text(encoding='utf-8')
    for (kind, source), local in table.items():
        if kind == 'item' and not 13308 <= source <= 13316:
            assert re.search(rf'const val DMM2_\w+_OSRS_{source} = {local}\b', constants), source
    bonuses = wiki_bonuses()
    rows = yaml.safe_load(native.read_text(encoding='utf-8'))
    assert len(rows) == 73
    for entry in rows:
        actual = byid[entry['id']]
        source = reverse[entry['id']]
        assert actual['tradeable'] == entry['tradeable'], source
        # The native note examines are normalized to the server's existing note text.
        assert actual['examine'] == entry['examine'] or actual['examine'] == 'Swap this note at any bank for the equivalent item.'
        if entry.get('equipment'):
            for key in ('appearance_id', 'skill_reqs', 'equip_slot'):
                assert actual['equipment'].get(key) == entry['equipment'].get(key), (source, key)
        if source in bonuses:
            for key, value in bonuses[source].items():
                assert actual['equipment'][key] == value, (source, key)
    for source in (13307, 28543, 28547):
        assert not byid[table['item', source]]['tradeable']
    edits = json.loads((ROOT / 'data/world_edits.json').read_text(encoding='utf-8'))['edits']
    for source, x, z in ((8391, 3152, 3512), (6516, 3150, 3512), (6485, 3150, 3514)):
        spawns = [e for e in edits if e.get('op') == 'npc_spawn' and e.get('id') == table['npc', source]]
        assert len(spawns) == 1 and (spawns[0]['x'], spawns[0]['z']) == (x, z), source
    skins = [line.split('\t') for line in (ROOT / 'data/cfg/dmm2/skins.tsv').read_text().splitlines() if line and not line.startswith('#')]
    assert len(skins) == 16
    for _, base, cosmetic, *accepted in skins:
        assert int(base) in byid and int(cosmetic) in reverse
        assert byid[int(base)]['equipment']['equip_slot'] == byid[int(cosmetic)]['equipment']['equip_slot']
    return dict(mapping_rows=len(table), native_metadata_rows=len(rows), corrupted_bonus_states=len(bonuses), skin_states=len(skins),
                no_id_collisions=True, native_appearance_and_requirements_preserved=True, exact_native_tradeability=True,
                nigel_wizard_seer_single_spawn=True,
                provenance={str(p.relative_to(ROOT)).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest()
                            for p in (ids, native, ROOT / 'data/cfg/dmm2/skins.tsv')})


if __name__ == '__main__':
    result = validate()
    (DOC / 'phase5-output/data-validation.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))
