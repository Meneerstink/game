"""Night run 18-11-2026: add the pure DIG classes of the OSRS clue pool to data/cfg/clue/steps.json.

Scope (only classes whose runtime handler already exists and needs no per-NPC dialogue or item identity):
  - cryptic 'Dig ...' steps (easy/hard/elite; master cryptics belong to the three-part scroll and stay out),
  - coordinate steps: medium (no fight) and hard (Saradomin/Zamorak wizard).
Rules: Wiki-pinned source file hash, no event-specific row, no missing local map square, exactly one point.
Existing records are never rewritten. Re-running is idempotent. Run: python tools/clue/expand_steps.py [--write]
"""
import argparse, json, re
from collections import Counter, defaultdict
from hashlib import sha256
from pathlib import Path
from build_step_catalogue import templates
from build_step_runtime import scroll_text

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / 'docs/CLUE/sources'
STEPS = ROOT / 'data/cfg/clue/steps.json'
# Dig points that are terrain-blocked in our 667 map (tools/cache667/terrain_flags.py, 18-11): not diggable here.
EXCLUDE = {'Clue scroll (elite) - Observing someone in a swamp', 'Clue scroll (hard) - CKP0224', 'Clue scroll (hard) - DKS2310',
           'Clue scroll (hard) - The Big High War God left his mark on this place'}
WIZARDS = {'Saradomin wizard': [2955], 'Zamorak wizard': [2954]}


def coord_name(fields):
    parts = re.findall(r'(\d+)\s*degrees\s*(\d+)\s*minutes\s*(north|south|east|west)', fields.get('coords', ''), re.I)
    return ' '.join(f'{int(d):02}.{int(m):02}{x[0].upper()}' for d, m, x in parts)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--write', action='store_true'); args = ap.parse_args()
    source = [r for r in json.loads((ROOT / 'docs/CLUE/steps-source.json').read_text(encoding='utf8')) if r['tier'] != 'beginner']
    manifest = json.loads((SOURCES / 'step-manifest.json').read_text(encoding='utf8'))
    pins = {row['file']: row for row in manifest['pages'] if 'file' in row}
    guide_manifest = json.loads((SOURCES / 'manifest.json').read_text(encoding='utf8'))
    guide_pins = {row['file']: row for row in guide_manifest['pages'] if 'file' in row}
    existing = json.loads(STEPS.read_text(encoding='utf8'))
    have = {r['key'] for r in existing}
    tables = {}
    for tier in ('medium', 'hard'):
        guide = f'Treasure_Trails__Guide__Coordinates__{tier.title()}.wiki'
        raw = (SOURCES / guide).read_bytes()
        assert sha256(raw).hexdigest() == guide_pins[guide]['sha256'], guide
        by = defaultdict(list)
        for f in templates(raw.decode('utf8'), 'Coordinates'): by[coord_name(f)].append(f)
        tables[tier] = (guide, guide_pins[guide], sha256(raw).hexdigest(), by)
    new, skipped = [], Counter()
    for row in source:
        key, tier, kind = row['key'], row['tier'], row['kind']
        if key in have or key in EXCLUDE: continue
        if row['eventSpecific'] or row['missingSquares'] or len(row['points']) != 1: skipped['event/missing/points'] += 1; continue
        notes = row['fields'].get('notes', '')
        challenge = {}
        if kind == 'COORDINATE' and tier in ('medium', 'hard'):
            guide, pin, digest, by = tables[tier]
            name = key.split(' - ', 1)[1]
            match = by.get(name, [])
            if len(match) != 1: skipped['coordinate table'] += 1; continue
            fight = match[0]['fight']
            names = re.findall(r'\[\[([^]|]+)', fight)
            if tier == 'medium':
                if fight.strip().lower() != 'no': skipped['medium fight'] += 1; continue
                challenge = dict(type='NONE', guards=[], spawnMode='ALL')
            else:
                if len(names) != 1 or names[0] not in WIZARDS: skipped['hard fight'] += 1; continue
                challenge = dict(type='GUARD', guards=WIZARDS[names[0]], spawnMode='ALL')
            challenge['source'] = dict(file=guide, sha256=digest, url=pin['url'])
            action = 'DIG'
        elif kind == 'CRYPTIC' and tier in ('easy', 'hard', 'elite') and notes.startswith('Dig '):
            action = 'DIG'
        else:
            continue
        if re.search(r'question:|answer:|puzzle box', notes, re.I): skipped['challenge note'] += 1; continue
        pin = pins[row['sourceFile']]
        digest = sha256((SOURCES / row['sourceFile']).read_bytes()).hexdigest()
        assert digest == pin['sha256'], key
        new.append(dict(key=key, tier=tier, kind=kind, action=action, hint=scroll_text(kind, row['hint']), points=row['points'],
                        npcName=None, npcCandidates=[], equipment=[], challenge=challenge, requirements={}, requirementsSource=None,
                        source=row['source'], sourceFile=row['sourceFile'], sourceSha256=digest, additionalSource=True))
    print('new records:', len(new), dict(Counter((r['tier'], r['kind']) for r in new)), 'skipped:', dict(skipped))
    if args.write:
        STEPS.write_text(json.dumps(existing + new, indent=2) + '\n', encoding='utf8')
        print('written', len(existing) + len(new))


if __name__ == '__main__':
    main()
