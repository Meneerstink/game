"""Exact first/later-attempt Mimic bonus outcomes from the pinned Wiki/cache.

The source table is one mutually exclusive roll: ring + 23 equipment outcomes +
five common outcomes. The guaranteed plank and unconsumed scroll case are extra.
"""
from fractions import Fraction
import json
import re
from build_catalogue import ROOT, OUT, SOURCES, split_template, probability, canonical

source = {int(k): v for k,v in json.loads((ROOT/'build/clue/osrs-items.json').read_text()).items()}
normal = json.loads((OUT/'rewards.json').read_text())
known = {canonical(r['name']): (r['osrsId'], r['noted']) for rows in normal.values() for r in rows}

def identity(name, noted=False):
    if canonical(name) in known:
        item, was_noted = known[canonical(name)]
        if was_noted != noted:
            item = source[item]['notedId']
        assert item >= 0
        return item
    matches = [i for i,r in source.items() if canonical(r['name']) == canonical(name)
               and r['notedTemplate'] < 0 and r['placeholderTemplateId'] < 0
               and r['boughtTemplateId'] < 0 and '59' not in r['params']]
    exact = [i for i in matches if source[i]['name'].casefold() == name.casefold()]
    if len(exact) == 1: matches = exact
    assert len(matches) == 1, (name,matches)
    return source[matches[0]]['notedId'] if noted else matches[0]

text = (SOURCES/'The_Mimic.wiki').read_text(encoding='utf-8')
output = {}
for tier, attempts in [('elite',5),('master',6)]:
    start = text.index(f'=={tier.title()} drops==')
    end = text.index('===Tertiary===',start)
    rows = []
    for number,line in enumerate(text[:end].splitlines(),1):
        if number <= text[:start].count('\n') or not line.startswith('{{DropsLineReward|'): continue
        fields = dict(p.split('=',1) for p in split_template(line[2:-2])[1:] if '=' in p)
        match = re.fullmatch(r'(\d+)(?:-(\d+))?( \(noted\))?',fields['quantity'])
        assert match,fields
        first = probability(fields['rarity']); later = probability(fields['altrarity'])
        rows.append(dict(osrsId=identity(fields['name'], bool(match[3])),name=fields['name'],
                         maximum=int(match[2] or match[1]), scales='-' in fields['quantity'],
                         firstNumerator=first.numerator,firstDenominator=first.denominator,
                         laterNumerator=later.numerator,laterDenominator=later.denominator,
                         sourceLine=number))
    assert len(rows) == 29,(tier,len(rows))
    for key in ['first','later']:
        assert sum((Fraction(r[key+'Numerator'],r[key+'Denominator']) for r in rows),Fraction()) == 1
    output[tier] = dict(attempts=attempts,guaranteed=identity('Mahogany plank'),case=30926,outcomes=rows)
    print(tier,'29 exact bonus outcomes; first/later sum=1')
(OUT/'mimic-rewards.json').write_text(json.dumps(output,indent=2)+'\n',encoding='utf-8')
