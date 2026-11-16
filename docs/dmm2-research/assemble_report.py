"""Assemble research appendices without changing any game/runtime files."""
import hashlib
from pathlib import Path
import re

ROOT = Path(__file__).parent
report = ROOT/'REPORT.md'
main = report.read_text(encoding='utf-8').split('\n<!-- DMM2_APPENDICES -->')[0]
appendices = ['\n<!-- DMM2_APPENDICES -->', '\n## Bijlage A — wiki-revisies\n',
              (ROOT/'wiki-revisions.md').read_text(encoding='utf-8'),
              '\n## Bijlage B — cache-items, modellen en vormen\n',
              (ROOT/'cache-item-matrix.md').read_text(encoding='utf-8')]

appendices.append('\n## Bijlage C — letterlijke besluiten, bronregels en ouderdom\n')
appendices.append('Dit zijn broncitaten, geen nieuwe goedkeuring. Nieuwere D1-D20 gaan voor oudere doodregels; DMM2-opdracht gaat voor de oude trinket-exclusie. Historische statuslabels zijn niet opnieuw getest.\n')
literal=[]
def capture(path, start, end):
    p=Path(path)
    raw=p.read_bytes()
    lines=raw.decode('utf-8-sig').splitlines()
    literal.extend([f'\n### {p} L{start}-L{end}', 'SHA256 '+hashlib.sha256(raw).hexdigest(), '```text', *lines[start-1:end], '```'])

p=Path(r'C:\RSPS\HOOFDLIJST.txt')
lines=p.read_text(encoding='utf-8-sig').splitlines()
section=next(i for i,l in enumerate(lines) if l.startswith('6. '))
end=next((i for i,l in enumerate(lines[section+1:],section+1) if l.startswith('7. ')),len(lines))
starts=[i for i in range(section,end) if lines[i].startswith('- ')]
pat=re.compile(r'Deadman|breach|trinket|N457|N540|N561|looting bag|D1-D20|DOOD|ornament|Perdu|Home Teleport|N100|N92|corrupt|points|DP|N235',re.I)
for a,b in zip(starts,starts[1:]+[end]):
    block='\n'.join(lines[a:b])
    if pat.search(block):capture(p,a+1,b)

p=Path(r'C:\RSPS\team\dossiers\GOEDGEKEURDE-ONTWERPEN.txt')
lines=p.read_text(encoding='utf-8-sig').splitlines()
# All current approved designs through the death block are relevant to drop/UI/burden/death integration.
end=next((i for i,l in enumerate(lines) if l.startswith('78 CLUE +')),len(lines))
capture(p,1,end)

p=Path(r'C:\RSPS\team\dossiers\BESLUITEN-OKTOBER-2026.txt')
for i,line in enumerate(p.read_text(encoding='utf-8-sig').splitlines(),1):
    if re.search(r'Deadman|Breach|N100|N92|N106|N111|POH exit|POH.*bank|PvP|teleport',line,re.I):capture(p,i,i)

p=Path(r'C:\RSPS\team\dossiers\DOOD-OVERZICHT-PER-GROEP-2026-10-24.txt')
lines=p.read_text(encoding='utf-8-sig').splitlines()
capture(p,1,11)
for i,line in enumerate(lines):
    if line.startswith(('A. ','B. ','C. ','D. ','E. ','F. ','G. ')):
        nxt=next((j for j in range(i+1,len(lines)) if re.match(r'^[A-Z]\. ',lines[j])),len(lines))
        # Preserve rules verbatim; massive catalogues of unrelated item names stay in the existing full snapshot.
        literal.append('\nGroep '+line[:line.find(' - ')]+'; volledige itemopsomming in owner-decisions-literal.txt.')
        if i+1<nxt:capture(p,i+2,nxt)

for path, spans in [
    (r'C:\RSPS\team\dossiers\DMM-PAKKET.txt',[(36,73),(99,120),(198,213),(217,231)]),
    (r'C:\RSPS\team\dossiers\N104.txt',[(1,11)]),
    (r'C:\RSPS\team\dossiers\N540.txt',[(1,6)]),
]:
    n=len(Path(path).read_text(encoding='utf-8-sig').splitlines())
    for a,b in spans:
        if a<=n:capture(path,a,min(b,n))

literal.append('\nN457: de oorspronkelijke losse dossierfile ontbreekt; de DMM2-taakkaart/het dossier onderscheidt expliciet native Ancient Warriors-corrupt en DMM corrupted weapons. RemovedItems.kt L21 en DeadmanGear.kt documenteren de oude verwijderregel. Dit is afgeleide bevestiging, geen teruggevonden letterlijke eigenaarschat. N457 niet uitbreiden naar nieuwe OSRS-ids.')
(ROOT/'owner-decisions-current-literal.md').write_text('\n'.join(literal)+'\n',encoding='utf-8')
appendices.append('\n'.join(literal))

appendices.append('\n## Bijlage D — volledige wiki-bonussen van alle corrupted vormen\n')
appendices.append('Aanval en defence: stab/slash/crush/magic/ranged; daarna melee strength/ranged strength/magic damage/prayer. Expliciete wikiwaarden, geen algemene 80%-berekening.\n')
appendices.append('|Item/toestand|A stab|slash|crush|magic|ranged|D stab|slash|crush|magic|ranged|Str|Rstr|Mdmg|Prayer|\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|')
names=['Corrupted_voidwaker','Corrupted_dragon_claws','Corrupted_armadyl_godsword','Corrupted_twisted_bow','Corrupted_Scythe_of_Vitur',"Corrupted_tumeken's_shadow",'Corrupted_dark_bow','Corrupted_Volatile_Nightmare_staff']
keys=['astab','aslash','acrush','amagic','arange','dstab','dslash','dcrush','dmagic','drange','str','rstr','mdmg','prayer']
for name in names:
    s=(ROOT/'wiki'/(name+'.wiki.txt')).read_text(encoding='utf-8')
    bonuses=s.split('{{Infobox Bonuses',1)[1].split('}}',1)[0]
    vals=dict(re.findall(r'^\|\s*(\w+)\s*=\s*([^\n]+)',bonuses,re.M))
    variants=[('1','uncharged'),('2','charged')] if name in names[4:6] else [('', '')]
    for v,label in variants:
        entries=[vals.get(k+v,vals.get(k,'MISSING')).strip() for k in keys]
        appendices.append('|'+ '|'.join([name.replace('_',' ')+(' '+label if label else ''),*entries])+'|')
appendices.append('\nDonors: GEEN donorimplementatie gebruikt in fase 1. Bestaande lokale special-helpers zijn alleen als mogelijke hergebruikroute onderzocht. Bronnen voor getallen blijven de wiki/cache; AV- en overige brongaten staan in V8/V12.\n')
report.write_text(main+'\n'+'\n'.join(appendices),encoding='utf-8')
print('Report assembled:',len(report.read_text(encoding='utf-8').splitlines()),'lines')
