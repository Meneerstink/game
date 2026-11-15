"""Derive the limited Falo/Sherlock contracts from the pinned, reviewed source rows.

Use --plan during a frozen integration: writes only the review artifact, never
runtime catalogues. No source fetching, target cache writer or guessed item ID.
"""
from hashlib import sha256
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[2]


def build():
    candidates = json.loads((ROOT/'docs/CLUE/CHALLENGE-CANDIDATES.json').read_text(encoding='utf8'))

    def source(row):
        pin = row['source']
        assert sha256((ROOT/'docs/CLUE/sources'/pin['file']).read_bytes()).hexdigest() == pin['sha256'], pin['file']
        return pin

    falo = []
    for row in candidates['falo']:
        if not row.get('selected'):
            continue
        items = [dict(osrs=item['osrs'], existingLocal=item['existingLocal'], name=item['name'])
                 for group in row['items'] for item in group['variants']]
        assert items and any(item['existingLocal'] is not None for item in items), row['lyric']
        # These are internal save keys, not allocated OSRS/local entity identities.
        key = sha256(row['lyric'].encode('utf8')).hexdigest()[:16]
        falo.append(dict(key=key, lyric=row['lyric'], items=items, source=source(row)))
    sherlock = []
    for row in candidates['sherlock']:
        if not row.get('selected'):
            continue
        assert row['tier'] in ('elite', 'master') and row['skills']
        sherlock.append(dict(key=row['key'], tier=row['tier'], task=row['task'], skills=row['skills'],
            otherRequirements=row['otherRequirements'], items=row['items'], source=source(row)))
    assert len(falo) == 7 and len({r['key'] for r in falo}) == 7
    assert len(sherlock) == 13 and len({(r['tier'], r['key']) for r in sherlock}) == 13
    return dict(falo=falo, sherlock=sherlock)


def main():
    data = build()
    destination = ROOT/('docs/CLUE/CHALLENGE-RUNTIME-PLAN.json' if '--plan' in sys.argv else 'data/cfg/clue/challenges.json')
    destination.write_text(json.dumps(data, indent=2, ensure_ascii=False)+'\n', encoding='utf8')
    print('Source-verified contracts:', len(data['falo']), 'Falo lyrics and', len(data['sherlock']), 'Sherlock actions;', destination)
    print('No handler activation or cache writes. Successful action adapters remain required.')


if __name__ == '__main__':
    main()
