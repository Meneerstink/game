"""Exact Wiki alternate tables. Identities reuse the verified normal rows, never numeric guesses."""
from pathlib import Path
from fractions import Fraction
import json
from build_catalogue import ROOT, OUT, reward_rows

normal = json.loads((OUT / 'rewards.json').read_text(encoding='utf-8'))
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
(OUT / 'rewards-entrana.json').write_text(json.dumps(profiles,indent=2)+'\n',encoding='utf-8')
