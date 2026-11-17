"""Turn the native dry-run output into an auditable census/old-to-new handoff; no cache access."""
import csv
import json
from pathlib import Path

DOC = Path(__file__).parent
OUT = DOC/'preflight-output'

def main():
    coverage = json.loads((OUT/'coverage.json').read_text())
    results = json.loads((OUT/'results.json').read_text())
    unchanged = json.loads((OUT/'unchanged.json').read_text())
    commands = json.loads((DOC.parents[1]/'build/dmm2/cache-plan/commands.json').read_text())
    assert len(coverage) == 195 and len({(r['kind'],r['source']) for r in coverage}) == 195
    assert {r['name'] for r in results} == {s['name'] for s in commands}, 'Unexecuted plan step'
    assert len(results) == len(commands)
    assert unchanged['pass_'] and not unchanged['changed']
    summary = []
    for r in results:
        body = (OUT/r['log']).read_text(encoding='utf-8')
        assert 'DRY_RUN_ONLY' in body and '--apply' not in body
        census = [line for line in body.splitlines() if line.startswith(('CENSUS ', 'LOCTYPES='))]
        plans = [line for line in body.splitlines() if line.startswith('PLAN ')]
        lost = [line for line in body.splitlines() if line.startswith('DROPPED ')]
        reason = next((line for line in body.splitlines() if line.startswith(('Exception in thread', 'Caused by:'))), '')
        if r['exit'] != 0: assert reason, 'Nonzero importer without identified exception'
        summary.append(dict(r, census=census, plans=plans, dropped=lost, reason=reason))
    (OUT/'summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    good = sum(r['exit'] == 0 for r in results)
    head = f'''# DMM2 — PREFLIGHT-ronde voor Claude

Alle 195 unieke regels uit `PREFLIGHT-DMM2-NIGHT.tsv` zijn geclassificeerd in `preflight-output/coverage.json`.
{len(results)} daadwerkelijke native tool-aanroepen: {good} geslaagde plannen, {len(results)-good} expliciet beschreven toolbeperkingen.
Dit is PLAN-bewijs, geen geslaagde import. Alle 78 bestanden in TWEE fysieke cachekopieën zijn byte-identiek gebleven;
SHA256 vóór/na in `preflight-output/before.json`/`after.json`, resultaat in `unchanged.json`.
Geen apply, geen originele server-/fileservercache als mutatiedoel. Bron=OpenRS2 2686.

`PREFLIGHT-RONDE.sh` bevat het uitvoerbare ingangspunt en alle exacte Java-aanroepen, inclusief de echte classpath en IDs.
De Gradle-taak draait compile + plannen opeenvolgend binnen het machinebrede bouwslot, `--priority low`.
De Java-adapter gebruikt de BESTAANDE importers met een uitsluitend in-memory batch; geen native toolbron gewijzigd.
Herhalen bouwt nieuwe kopieën. De onderbroken eerste poging is hersteld met het openbare petsroster in de sandbox;
de definitieve logbestanden hieronder bevatten de echte uitkomsten na dat herstel.

## Allocaties en gebruik

Losse item-/NPC-/FX-plannen zijn onafhankelijke diagnoses op DEZELFDE ongewijzigde snapshot; IDs mogen daar dus terugkeren.
Gebruik de `bundle-*`-uitkomsten voor coherente allocatie PER TOOL. Ook die zijn geen gereserveerde/finale lokale IDs:
de echte gecombineerde Claude-cachetransactie moet één verse census gebruiken voor alle tools samen.
Elke native PLAN-regel bevat bron→voorlopig lokaal en model/seq-afhankelijkheden; hieronder en in `summary.json`.
Modelverlies (face-z-offsets/stretch flags/onbekende opcodes) staat per stap in de volledige uitvoer en mag niet als visuele PASS tellen.

## Geen blinde import voor overige bronregels

- Lil Wisp15571: klassieke NPC-import faalt op animaya13893632. Het bestaande `OsrsPetImportTool skeletal-npc`-plan slaagt,
  evenals `SkeletalSidecarExportTool cache plan` voor de ECHTE rig2138/clips212:0 en210:0. Het sidecar-plan gebruikt uitsluitend
  lokale owner-/seq-ids uit hetzelfde native Wisp-plan. De klassieke bake is een fallback, geen visuele OSRS-PASS.
- UI232 slaagt apart. UI230 stopt op OSRS-font645; de bestaande interfaceporter kent uitsluitend494..497.
  Claude moet de bronfont/glyphs en een echte mapping bewijzen; niet645 blind vervangen door p11/p12.
- Count-items13308..13316 komen via Blood money13307; notes via de ouder met `Spec(noted=true)`.
- OSRS-placeholders (opcode149) hebben geen bewezen667-placeholderconverter. Niet als bruikbare items invoeren.
- Bestaande fairies/undead/avarice/inactivefortuity blijven hun lokale mappings houden. Een bijbehorende OSRS-note is NIET vanzelf
  geïmporteerd als de ouder wordt hergebruikt; Claude controleert de bestaande note-links en plant ontbrekende vormen expliciet.
- Sprites170/828/829/830/1041/1042 zijn UI-afhankelijkheden. Het UI-plan is de importproef; bij een vroege UI-fout zijn die nog NIET geport.
  Standalone1524/1681 zijn semantisch onbewezen Nigel-iconkandidaten. Dumpen is geen import; geen bewezen standalone importbatch.
- Nieuw bronbewijs item34607 staat in `PHASE3-SOURCES.md`: ontbreekt in2686, vereist een nieuwere gepinde broncache.
  Dit is een aanvullende IDS-NODIG-regel, buiten de oorspronkelijke195. Normal34633/34635 en fabric34641 hergebruiken MA2.

## Tooluitkomsten

'''
    for r in summary:
        head += f'### {r["name"]} — exit{r["exit"]}\n\n'
        head += f'Volledige uitvoer: [preflight-output/{r["log"]}](preflight-output/{r["log"]}).\n\n'
        detail = r['census'] + r['plans'] + ([r['reason']] if r['reason'] else [])
        head += '```text\n'+'\n'.join(detail)+'\n```\n\n'
        if r['dropped']: head += f'{len(r["dropped"])} expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.\n\n'
    (DOC/'PREFLIGHT-RESULTAAT.md').write_text(head.rstrip()+'\n',encoding='utf-8')
    print(f'{len(coverage)} coverage rows, {len(results)} complete tool outcomes, {good} plans OK; caches unchanged')

if __name__ == '__main__': main()
