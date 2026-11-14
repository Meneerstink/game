"""Exact Wiki alternate tables. Identities reuse the verified normal rows, never numeric guesses."""
from pathlib import Path
from fractions import Fraction
import json
from build_catalogue import ROOT, OUT, reward_rows

normal = json.loads((OUT / 'rewards.json').read_text(encoding='utf-8'))
cache = json.loads((ROOT / 'build/clue/osrs-items.json').read_text())
profiles = {}
for tier in ('easy', 'medium'):
    def key(r): return r['name'], r['minimum'], r['maximum'], r['noted']
    by_name = {key(r): r for r in normal[tier]}
    rows = reward_rows(tier, entrana=True)
    assert sum((Fraction(r['numerator'], r['denominator']) for r in rows), Fraction()) == 1
    for row in rows:
        old = by_name[key(row)]
        row['osrsId'] = old['osrsId']
    # Explicit source proves this is exactly rejection of the forbidden normal outcomes.
    by_allowed = {key(r): Fraction(r['numerator'],r['denominator']) for r in rows}
    mass = sum((Fraction(r['numerator'],r['denominator']) for r in normal[tier] if key(r) in by_allowed), Fraction())
    assert all(Fraction(r['numerator'],r['denominator']) / mass == by_allowed[key(r)] for r in normal[tier] if key(r) in by_allowed)
    profiles[tier] = rows
    print(tier, len(rows), 'exact Entrana outcomes; allowed mass',mass)

# Entrana Wiki / Mod Ash: roll the table without prohibited equipment. Both
# explicit easy/medium tables prove the cache classification: all non-equipment
# plus the six god blessings, with precisely the original relative weights.
blessings = {r['osrsId'] for rows in profiles.values() for r in rows if r['name'].endswith('blessing')}
def allowed(row):
    d = cache[str(row['osrsId'])]
    if d['notedTemplate'] >= 0: d = cache[str(d['notedId'])]
    return d['wearPos1'] < 0 or row['osrsId'] in blessings
for tier in ('easy','medium'):
    assert {r['osrsId'] for r in normal[tier] if allowed(r)} == {r['osrsId'] for r in profiles[tier]}
for tier in ('hard','elite'):
    rows = [dict(r) for r in normal[tier] if allowed(r)]
    # The combat-potion bundle is one event despite three marginal item rows.
    events = {}
    for r in rows:
        events[r.get('bundle') or (r['osrsId'],r['sourceLine'])] = Fraction(r['numerator'],r['denominator'])
    mass = sum(events.values(),Fraction())
    for r in rows:
        p = Fraction(r['numerator'],r['denominator']) / mass
        r['numerator'],r['denominator'] = p.numerator,p.denominator
    profiles[tier] = rows
    print(tier,len(rows),'Entrana rule + cache wear classification; allowed event mass',mass)
(OUT / 'rewards-entrana.json').write_text(json.dumps(profiles,indent=2)+'\n',encoding='utf-8')
