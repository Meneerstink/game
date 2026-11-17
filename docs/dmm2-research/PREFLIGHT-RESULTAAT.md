# DMM2 — PREFLIGHT-ronde voor Claude

Alle 195 unieke regels uit `PREFLIGHT-DMM2-NIGHT.tsv` zijn geclassificeerd in `preflight-output/coverage.json`.
105 daadwerkelijke native tool-aanroepen: 102 geslaagde plannen, 3 expliciet beschreven toolbeperkingen.
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

### item-13307 — exit0

Volledige uitvoer: [preflight-output/item-13307.txt](preflight-output/item-13307.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Blood money count 13308' <- upstream_item:13308 bytes=40 models=[upstream_model:2485->65536]
PLAN item 24635 'Blood money count 13309' <- upstream_item:13309 bytes=40 models=[upstream_model:2486->65537]
PLAN item 24636 'Blood money count 13310' <- upstream_item:13310 bytes=40 models=[upstream_model:2487->65538]
PLAN item 24637 'Blood money count 13311' <- upstream_item:13311 bytes=40 models=[upstream_model:2488->65539]
PLAN item 24638 'Blood money count 13312' <- upstream_item:13312 bytes=40 models=[upstream_model:2667->65540]
PLAN item 24639 'Blood money count 13313' <- upstream_item:13313 bytes=40 models=[upstream_model:2825->65541]
PLAN item 24640 'Blood money count 13314' <- upstream_item:13314 bytes=40 models=[upstream_model:2423->65542]
PLAN item 24641 'Blood money count 13315' <- upstream_item:13315 bytes=40 models=[upstream_model:2710->65543]
PLAN item 24642 'Blood money count 13316' <- upstream_item:13316 bytes=40 models=[upstream_model:2775->65544]
PLAN item 24643 'Blood money' <- upstream_item:13307 bytes=92 models=[upstream_model:2484->65545]
```

### item-13317 — exit0

Volledige uitvoer: [preflight-output/item-13317.txt](preflight-output/item-13317.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Deadman's chest' <- upstream_item:13317 bytes=94 models=[upstream_model:29749->65536, upstream_model:29637->65537, upstream_model:29636->65538, upstream_model:29745->65539, upstream_model:29744->65540, upstream_model:151->29538]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-13318 — exit0

Volledige uitvoer: [preflight-output/item-13318.txt](preflight-output/item-13318.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Deadman's legs' <- upstream_item:13318 bytes=78 models=[upstream_model:29752->65536, upstream_model:29020->65537, upstream_model:29743->65538]
```

### item-13319 — exit0

Volledige uitvoer: [preflight-output/item-13319.txt](preflight-output/item-13319.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Deadman's cape' <- upstream_item:13319 bytes=78 models=[upstream_model:29748->65536, upstream_model:29633->65537, upstream_model:29725->65538]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-13660 — exit0

Volledige uitvoer: [preflight-output/item-13660.txt](preflight-output/item-13660.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Chronicle' <- upstream_item:13660 bytes=146 models=[upstream_model:31245->65536, upstream_model:31193->65537, upstream_model:31194->65538]
```

### item-22331 — exit0

Volledige uitvoer: [preflight-output/item-22331.txt](preflight-output/item-22331.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Starter sword' <- upstream_item:22331 bytes=92 models=[upstream_model:49503->65536, upstream_model:49484->65537, upstream_model:49483->65538]
```

### item-22333 — exit0

Volledige uitvoer: [preflight-output/item-22333.txt](preflight-output/item-22333.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Starter bow' <- upstream_item:22333 bytes=105 models=[upstream_model:49505->65536, upstream_model:49486->65537, upstream_model:49482->65538]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-22335 — exit0

Volledige uitvoer: [preflight-output/item-22335.txt](preflight-output/item-22335.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Starter staff' <- upstream_item:22335 bytes=106 models=[upstream_model:49504->65536, upstream_model:49481->65537, upstream_model:49485->65538]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-26149 — exit0

Volledige uitvoer: [preflight-output/item-26149.txt](preflight-output/item-26149.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Tuna' <- upstream_item:26149 bytes=49 models=[upstream_model:2355->66609]
```

### item-28531 — exit0

Volledige uitvoer: [preflight-output/item-28531.txt](preflight-output/item-28531.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted voidwaker' <- upstream_item:28531 bytes=80 models=[upstream_model:49500->65536, upstream_model:49472->65537, upstream_model:49471->65538]
PLAN item 24635 'Corrupted voidwaker (noted)' <- upstream_item:28532 bytes=7 models=[]
```

### item-28534 — exit0

Volledige uitvoer: [preflight-output/item-28534.txt](preflight-output/item-28534.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted dragon claws' <- upstream_item:28534 bytes=83 models=[upstream_model:49496->65536, upstream_model:49468->65537, upstream_model:49467->65538]
PLAN item 24635 'Corrupted dragon claws (noted)' <- upstream_item:28535 bytes=7 models=[]
```

### item-28537 — exit0

Volledige uitvoer: [preflight-output/item-28537.txt](preflight-output/item-28537.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted armadyl godsword' <- upstream_item:28537 bytes=91 models=[upstream_model:49502->65536, upstream_model:49469->65537, upstream_model:49470->65538]
PLAN item 24635 'Corrupted armadyl godsword (noted)' <- upstream_item:28538 bytes=7 models=[]
```

### item-28540 — exit0

Volledige uitvoer: [preflight-output/item-28540.txt](preflight-output/item-28540.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted twisted bow' <- upstream_item:28540 bytes=82 models=[upstream_model:49499->65536, upstream_model:49473->65537, upstream_model:49474->65538]
PLAN item 24635 'Corrupted twisted bow (noted)' <- upstream_item:28541 bytes=7 models=[]
```

### item-28543 — exit0

Volledige uitvoer: [preflight-output/item-28543.txt](preflight-output/item-28543.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted scythe of vitur' <- upstream_item:28543 bytes=111 models=[upstream_model:49497->65536, upstream_model:49478->65537, upstream_model:49476->65538]
```

4 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-28545 — exit0

Volledige uitvoer: [preflight-output/item-28545.txt](preflight-output/item-28545.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted scythe of vitur (uncharged)' <- upstream_item:28545 bytes=98 models=[upstream_model:49501->65536, upstream_model:49475->65537, upstream_model:49477->65538]
PLAN item 24635 'Corrupted scythe of vitur (uncharged) (noted)' <- upstream_item:28546 bytes=7 models=[]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-28547 — exit0

Volledige uitvoer: [preflight-output/item-28547.txt](preflight-output/item-28547.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted tumeken's shadow' <- upstream_item:28547 bytes=123 models=[upstream_model:49498->65536, upstream_model:49479->65537, upstream_model:49480->65538]
```

4 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-28549 — exit0

Volledige uitvoer: [preflight-output/item-28549.txt](preflight-output/item-28549.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted tumeken's shadow (uncharged)' <- upstream_item:28549 bytes=110 models=[upstream_model:49498->65536, upstream_model:49479->65537, upstream_model:49480->65538]
PLAN item 24635 'Corrupted tumeken's shadow (uncharged) (noted)' <- upstream_item:28550 bytes=7 models=[]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-28561 — exit0

Volledige uitvoer: [preflight-output/item-28561.txt](preflight-output/item-28561.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Trinket of vengeance' <- upstream_item:28561 bytes=57 models=[upstream_model:49495->63903]
PLAN item 24635 'Trinket of vengeance (noted)' <- upstream_item:28562 bytes=7 models=[]
```

### item-28567 — exit0

Volledige uitvoer: [preflight-output/item-28567.txt](preflight-output/item-28567.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Trinket of advanced weaponry' <- upstream_item:28567 bytes=68 models=[upstream_model:49493->65536]
PLAN item 24635 'Trinket of advanced weaponry (noted)' <- upstream_item:28568 bytes=7 models=[]
```

### item-29599 — exit0

Volledige uitvoer: [preflight-output/item-29599.txt](preflight-output/item-29599.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted dark bow' <- upstream_item:29599 bytes=79 models=[upstream_model:54205->65536, upstream_model:54213->65537, upstream_model:54212->65538]
PLAN item 24635 'Corrupted dark bow (noted)' <- upstream_item:29600 bytes=7 models=[]
```

### item-29602 — exit0

Volledige uitvoer: [preflight-output/item-29602.txt](preflight-output/item-29602.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Corrupted Volatile Nightmare staff' <- upstream_item:29602 bytes=95 models=[upstream_model:54204->65536, upstream_model:54210->65537, upstream_model:54214->65538]
PLAN item 24635 'Corrupted Volatile Nightmare staff (noted)' <- upstream_item:29603 bytes=7 models=[]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-29605 — exit0

Volledige uitvoer: [preflight-output/item-29605.txt](preflight-output/item-29605.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Armadyl godsword (Deadman)' <- upstream_item:29605 bytes=87 models=[upstream_model:49502->65536, upstream_model:49469->65537, upstream_model:49470->65538]
```

### item-29607 — exit0

Volledige uitvoer: [preflight-output/item-29607.txt](preflight-output/item-29607.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Voidwaker (Deadman)' <- upstream_item:29607 bytes=76 models=[upstream_model:49500->65536, upstream_model:49472->65537, upstream_model:49471->65538]
```

### item-29609 — exit0

Volledige uitvoer: [preflight-output/item-29609.txt](preflight-output/item-29609.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Volatile Nightmare staff (Deadman)' <- upstream_item:29609 bytes=91 models=[upstream_model:54204->65536, upstream_model:54210->65537, upstream_model:54214->65538]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-29611 — exit0

Volledige uitvoer: [preflight-output/item-29611.txt](preflight-output/item-29611.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Dark bow (Deadman)' <- upstream_item:29611 bytes=79 models=[upstream_model:54205->65536, upstream_model:54213->65537, upstream_model:54212->65538]
```

### item-29613 — exit0

Volledige uitvoer: [preflight-output/item-29613.txt](preflight-output/item-29613.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Imbued Zamorak cape (Deadman)' <- upstream_item:29613 bytes=95 models=[upstream_model:54203->65536, upstream_model:54216->65537, upstream_model:54215->65538]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-29615 — exit0

Volledige uitvoer: [preflight-output/item-29615.txt](preflight-output/item-29615.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Imbued Guthix cape (Deadman)' <- upstream_item:29615 bytes=130 models=[upstream_model:54203->65536, upstream_model:54216->65537, upstream_model:54215->65538]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-29617 — exit0

Volledige uitvoer: [preflight-output/item-29617.txt](preflight-output/item-29617.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Imbued Saradomin cape (Deadman)' <- upstream_item:29617 bytes=133 models=[upstream_model:54203->65536, upstream_model:54216->65537, upstream_model:54215->65538]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-29619 — exit0

Volledige uitvoer: [preflight-output/item-29619.txt](preflight-output/item-29619.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Armageddon rug' <- upstream_item:29619 bytes=50 models=[upstream_model:54208->65536]
PLAN item 24635 'Armageddon rug (noted)' <- upstream_item:29620 bytes=7 models=[]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-29622 — exit0

Volledige uitvoer: [preflight-output/item-29622.txt](preflight-output/item-29622.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Armageddon teleport scroll' <- upstream_item:29622 bytes=84 models=[upstream_model:54206->65536, upstream_model:54211->65537]
PLAN item 24635 'Armageddon teleport scroll (noted)' <- upstream_item:29623 bytes=7 models=[]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-29625 — exit0

Volledige uitvoer: [preflight-output/item-29625.txt](preflight-output/item-29625.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Armageddon weapon scroll' <- upstream_item:29625 bytes=71 models=[upstream_model:54207->65536]
PLAN item 24635 'Armageddon weapon scroll (noted)' <- upstream_item:29626 bytes=7 models=[]
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-29628 — exit0

Volledige uitvoer: [preflight-output/item-29628.txt](preflight-output/item-29628.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Armageddon cape fabric' <- upstream_item:29628 bytes=62 models=[upstream_model:54201->65536]
PLAN item 24635 'Armageddon cape fabric (noted)' <- upstream_item:29629 bytes=7 models=[]
```

### item-33012 — exit0

Volledige uitvoer: [preflight-output/item-33012.txt](preflight-output/item-33012.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Annihilation weapon scroll' <- upstream_item:33012 bytes=73 models=[upstream_model:54207->65536]
PLAN item 24635 'Annihilation weapon scroll (noted)' <- upstream_item:33013 bytes=7 models=[]
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33015 — exit0

Volledige uitvoer: [preflight-output/item-33015.txt](preflight-output/item-33015.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Annihilation blueprints' <- upstream_item:33015 bytes=63 models=[upstream_model:28470->65536]
PLAN item 24635 'Annihilation blueprints (noted)' <- upstream_item:33016 bytes=7 models=[]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33018 — exit0

Volledige uitvoer: [preflight-output/item-33018.txt](preflight-output/item-33018.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Annihilation teleport scroll' <- upstream_item:33018 bytes=86 models=[upstream_model:54206->65536, upstream_model:54211->65537]
PLAN item 24635 'Annihilation teleport scroll (noted)' <- upstream_item:33019 bytes=7 models=[]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33021 — exit0

Volledige uitvoer: [preflight-output/item-33021.txt](preflight-output/item-33021.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Bow of Faerdhinen (c)' <- upstream_item:33021 bytes=88 models=[upstream_model:28476->65536, upstream_model:59942->65537, upstream_model:59943->65538]
```

### item-33023 — exit0

Volledige uitvoer: [preflight-output/item-33023.txt](preflight-output/item-33023.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Crystal body' <- upstream_item:33023 bytes=98 models=[upstream_model:59961->65536, upstream_model:59940->65537, upstream_model:59954->65538, upstream_model:151->29538]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33025 — exit0

Volledige uitvoer: [preflight-output/item-33025.txt](preflight-output/item-33025.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Crystal body (inactive)' <- upstream_item:33025 bytes=92 models=[upstream_model:59962->65536, upstream_model:59941->65537, upstream_model:59955->65538, upstream_model:151->29538]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33027 — exit0

Volledige uitvoer: [preflight-output/item-33027.txt](preflight-output/item-33027.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Crystal legs' <- upstream_item:33027 bytes=95 models=[upstream_model:59959->65536, upstream_model:59938->65537, upstream_model:59952->65538]
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33029 — exit0

Volledige uitvoer: [preflight-output/item-33029.txt](preflight-output/item-33029.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Crystal legs (inactive)' <- upstream_item:33029 bytes=89 models=[upstream_model:59960->65536, upstream_model:59939->65537, upstream_model:59953->65538]
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33031 — exit0

Volledige uitvoer: [preflight-output/item-33031.txt](preflight-output/item-33031.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Crystal helm' <- upstream_item:33031 bytes=110 models=[upstream_model:59957->65536, upstream_model:59935->65537, upstream_model:59949->65538, upstream_model:59930->65539, upstream_model:59932->65540]
```

5 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33033 — exit0

Volledige uitvoer: [preflight-output/item-33033.txt](preflight-output/item-33033.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Crystal helm (inactive)' <- upstream_item:33033 bytes=104 models=[upstream_model:59958->65536, upstream_model:59936->65537, upstream_model:59950->65538, upstream_model:59931->65539, upstream_model:59933->65540]
```

5 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33035 — exit0

Volledige uitvoer: [preflight-output/item-33035.txt](preflight-output/item-33035.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Toxic staff (uncharged)' <- upstream_item:33035 bytes=89 models=[upstream_model:28897->65536, upstream_model:59946->65537, upstream_model:59947->65538]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33036 — exit0

Volledige uitvoer: [preflight-output/item-33036.txt](preflight-output/item-33036.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Toxic staff (Deadman)' <- upstream_item:33036 bytes=116 models=[upstream_model:41576->65536, upstream_model:59944->65537, upstream_model:59945->65538]
```

4 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33041 — exit0

Volledige uitvoer: [preflight-output/item-33041.txt](preflight-output/item-33041.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Thunder khopesh' <- upstream_item:33041 bytes=102 models=[upstream_model:55637->65536, upstream_model:55757->65537, upstream_model:55756->65538]
PLAN item 24635 'Thunder khopesh (noted)' <- upstream_item:33042 bytes=7 models=[]
```

### item-33050 — exit0

Volledige uitvoer: [preflight-output/item-33050.txt](preflight-output/item-33050.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Trinket of fortuity (active)' <- upstream_item:33050 bytes=72 models=[upstream_model:28474->65536]
PLAN item 24635 'Trinket of fortuity (active) (noted)' <- upstream_item:33051 bytes=7 models=[]
```

### item-33063 — exit0

Volledige uitvoer: [preflight-output/item-33063.txt](preflight-output/item-33063.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Starter cape' <- upstream_item:33063 bytes=72 models=[upstream_model:37197->65536, upstream_model:37019->65537, upstream_model:37051->65538]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-33122 — exit0

Volledige uitvoer: [preflight-output/item-33122.txt](preflight-output/item-33122.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Annihilation exit portal blueprints' <- upstream_item:33122 bytes=65 models=[upstream_model:28470->65536]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### item-34422 — exit0

Volledige uitvoer: [preflight-output/item-34422.txt](preflight-output/item-34422.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Fire ruby' <- upstream_item:34422 bytes=52 models=[upstream_model:61875->65536]
PLAN item 24635 'Fire ruby (noted)' <- upstream_item:34423 bytes=7 models=[]
```

### item-34425 — exit0

Volledige uitvoer: [preflight-output/item-34425.txt](preflight-output/item-34425.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Amulet of fire' <- upstream_item:34425 bytes=80 models=[upstream_model:61877->65536, upstream_model:61881->65537, upstream_model:61884->65538]
PLAN item 24635 'Amulet of fire (noted)' <- upstream_item:34426 bytes=7 models=[]
```

### item-13658 — exit0

Volledige uitvoer: [preflight-output/item-13658.txt](preflight-output/item-13658.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Teleport card' <- upstream_item:13658 bytes=55 models=[upstream_model:31242->65536]
PLAN item 24635 'Teleport card (noted)' <- upstream_item:13659 bytes=7 models=[]
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### npc-6516 — exit0

Volledige uitvoer: [preflight-output/npc-6516.txt](preflight-output/npc-6516.txt).

```text
CENSUS npc_model_candidates=65100 next npc=15081 bas=2398 seq=16221 frameset=4574 base=3580 synth=10561
PLAN npc 6516 'Financial Wizard' -> 15081 bas=2398 models=[20399, 214, 250, 20405, 3189, 20395, 177, 20402, 181]->[65536, 54914, 54814, 59207, 53718, 59213, 56708, 65537, 58335] heads=[82, 52, 20393]->[54850, 55072, 65538] seqs={808=808, 819=819, 820=820, 821=821, 822=822} ops=[Talk-to, null, Bank, Collect, Deposit-box]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### npc-6485 — exit0

Volledige uitvoer: [preflight-output/npc-6485.txt](preflight-output/npc-6485.txt).

```text
CENSUS npc_model_candidates=65100 next npc=15081 bas=2398 seq=16221 frameset=4574 base=3580 synth=10561
PLAN npc 6485 'Financial Seer' -> 15081 bas=2398 models=[408, 417, 21914, 21899, 10706, 21911, 358, 437]->[65536, 65537, 65538, 65539, 55409, 65540, 61195, 55464] heads=[125, 136]->[65541, 65542] seqs={808=808, 819=819, 820=820, 821=821, 822=822} ops=[Talk-to, null, Bank, Collect, Deposit-box]
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### npc-8391 — exit0

Volledige uitvoer: [preflight-output/npc-8391.txt](preflight-output/npc-8391.txt).

```text
CENSUS npc_model_candidates=65100 next npc=15081 bas=2398 seq=16221 frameset=4574 base=3580 synth=10561
PLAN npc 8391 'Nigel' -> 15081 bas=2398 models=[29473, 29637, 29633, 29636, 29634, 29635, 29638, 29639]->[65536, 65537, 65538, 65539, 65540, 65541, 65542, 65543] heads=[29640]->[65544] seqs={808=808, 819=819, 820=820, 821=821, 822=822} ops=[Talk-to, null, null, Trade, null]
```

4 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### npc-8390 — exit0

Volledige uitvoer: [preflight-output/npc-8390.txt](preflight-output/npc-8390.txt).

```text
CENSUS npc_model_candidates=65100 next npc=15081 bas=2398 seq=16221 frameset=4574 base=3580 synth=10561
PLAN npc 8390 'Nigel' -> 15081 bas=2398 models=[29473, 29637, 29633, 29636, 29634, 29635, 29638, 29639]->[65536, 65537, 65538, 65539, 65540, 65541, 65542, 65543] heads=[29640]->[65544] seqs={808=808, 819=819, 820=820, 821=821, 822=822} ops=[Talk-to, null, Teleport, Trade, null]
```

4 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### npc-15569 — exit0

Volledige uitvoer: [preflight-output/npc-15569.txt](preflight-output/npc-15569.txt).

```text
CENSUS npc_model_candidates=65100 next npc=15081 bas=2398 seq=16221 frameset=4574 base=3580 synth=10561
PLAN npc 15569 'null' -> 15081 bas=2398 models=[34586]->[31511] heads=[]->[] seqs={7975=15874, 7977=15875} ops=[null, null, null, null, null]
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### npc-15570 — exit0

Volledige uitvoer: [preflight-output/npc-15570.txt](preflight-output/npc-15570.txt).

```text
CENSUS npc_model_candidates=65100 next npc=15081 bas=2398 seq=16221 frameset=4574 base=3580 synth=10561
PLAN npc 15570 'null' -> 15081 bas=2398 models=[24602, 24605, 24606]->[31472, 31466, 31461] heads=[]->[] seqs={6236=15878} ops=[null, null, null, null, null]
```

4 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### npc-15571 — exit1

Volledige uitvoer: [preflight-output/npc-15571.txt](preflight-output/npc-15571.txt).

```text
CENSUS npc_model_candidates=65100 next npc=15081 bas=2398 seq=16221 frameset=4574 base=3580 synth=10561
Exception in thread "main" java.lang.IllegalStateException: skeletal (animaya) sequence 13893632 cannot be represented in 667
```

### seq-11444 — exit0

Volledige uitvoer: [preflight-output/seq-11444.txt](preflight-output/seq-11444.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 11444 -> 16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-11810 — exit0

Volledige uitvoer: [preflight-output/seq-11810.txt](preflight-output/seq-11810.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 11810 -> 16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-11811 — exit0

Volledige uitvoer: [preflight-output/seq-11811.txt](preflight-output/seq-11811.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 11811 -> 16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-11812 — exit0

Volledige uitvoer: [preflight-output/seq-11812.txt](preflight-output/seq-11812.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 11812 -> 16221
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-11935 — exit0

Volledige uitvoer: [preflight-output/seq-11935.txt](preflight-output/seq-11935.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 11935 -> 16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-11936 — exit0

Volledige uitvoer: [preflight-output/seq-11936.txt](preflight-output/seq-11936.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 11936 -> 16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-11937 — exit0

Volledige uitvoer: [preflight-output/seq-11937.txt](preflight-output/seq-11937.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 11937 -> 16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-13701 — exit0

Volledige uitvoer: [preflight-output/seq-13701.txt](preflight-output/seq-13701.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13701 -> 16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-13702 — exit0

Volledige uitvoer: [preflight-output/seq-13702.txt](preflight-output/seq-13702.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13702 -> 16221
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-13703 — exit0

Volledige uitvoer: [preflight-output/seq-13703.txt](preflight-output/seq-13703.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13703 -> 16221
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-13704 — exit0

Volledige uitvoer: [preflight-output/seq-13704.txt](preflight-output/seq-13704.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13704 -> 16221
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-13705 — exit0

Volledige uitvoer: [preflight-output/seq-13705.txt](preflight-output/seq-13705.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13705 -> 16221
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-13706 — exit0

Volledige uitvoer: [preflight-output/seq-13706.txt](preflight-output/seq-13706.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13706 -> 16221
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-13707 — exit0

Volledige uitvoer: [preflight-output/seq-13707.txt](preflight-output/seq-13707.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13707 -> 16221
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### seq-13708 — exit0

Volledige uitvoer: [preflight-output/seq-13708.txt](preflight-output/seq-13708.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13708 -> 16221
```

### seq-13709 — exit0

Volledige uitvoer: [preflight-output/seq-13709.txt](preflight-output/seq-13709.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13709 -> 16221
```

### seq-13710 — exit0

Volledige uitvoer: [preflight-output/seq-13710.txt](preflight-output/seq-13710.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13710 -> 16221
```

### seq-13711 — exit0

Volledige uitvoer: [preflight-output/seq-13711.txt](preflight-output/seq-13711.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13711 -> 16221
```

### seq-13712 — exit0

Volledige uitvoer: [preflight-output/seq-13712.txt](preflight-output/seq-13712.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13712 -> 16221
```

### seq-13713 — exit0

Volledige uitvoer: [preflight-output/seq-13713.txt](preflight-output/seq-13713.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13713 -> 16221
```

### seq-13714 — exit0

Volledige uitvoer: [preflight-output/seq-13714.txt](preflight-output/seq-13714.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 13714 -> 16221
```

### seq-14497 — exit0

Volledige uitvoer: [preflight-output/seq-14497.txt](preflight-output/seq-14497.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 14497 -> 16221
```

### spotanim-3030 — exit0

Volledige uitvoer: [preflight-output/spotanim-3030.txt](preflight-output/spotanim-3030.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3030 -> 3220 model 44174->2870 seq 11463->15388
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### spotanim-3031 — exit0

Volledige uitvoer: [preflight-output/spotanim-3031.txt](preflight-output/spotanim-3031.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3031 -> 3220 model 44174->2870 seq 11810->16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### spotanim-3032 — exit0

Volledige uitvoer: [preflight-output/spotanim-3032.txt](preflight-output/spotanim-3032.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3032 -> 3220 model 44174->2870 seq 11810->16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### spotanim-3033 — exit0

Volledige uitvoer: [preflight-output/spotanim-3033.txt](preflight-output/spotanim-3033.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3033 -> 3220 model 44174->2870 seq 11811->16221
```

1 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### spotanim-3565 — exit0

Volledige uitvoer: [preflight-output/spotanim-3565.txt](preflight-output/spotanim-3565.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3565 -> 3220 model 28458->12089 seq 13708->16221
```

### spotanim-3566 — exit0

Volledige uitvoer: [preflight-output/spotanim-3566.txt](preflight-output/spotanim-3566.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3566 -> 3220 model 28458->12089 seq 13709->16221
```

### spotanim-3567 — exit0

Volledige uitvoer: [preflight-output/spotanim-3567.txt](preflight-output/spotanim-3567.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3567 -> 3220 model 28458->12089 seq 13710->16221
```

### spotanim-3568 — exit0

Volledige uitvoer: [preflight-output/spotanim-3568.txt](preflight-output/spotanim-3568.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3568 -> 3220 model 28458->12089 seq 13711->16221
```

### spotanim-3569 — exit0

Volledige uitvoer: [preflight-output/spotanim-3569.txt](preflight-output/spotanim-3569.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3569 -> 3220 model 28458->12089 seq 13712->16221
```

### spotanim-3570 — exit0

Volledige uitvoer: [preflight-output/spotanim-3570.txt](preflight-output/spotanim-3570.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3570 -> 3220 model 28458->12089 seq 13713->16221
```

### spotanim-3571 — exit0

Volledige uitvoer: [preflight-output/spotanim-3571.txt](preflight-output/spotanim-3571.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3571 -> 3220 model 28458->12089 seq 13714->16221
```

### spotanim-4020 — exit0

Volledige uitvoer: [preflight-output/spotanim-4020.txt](preflight-output/spotanim-4020.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 4020 -> 3220 model 3123->4108 seq 699->15740
```

### loc-54114 — exit0

Volledige uitvoer: [preflight-output/loc-54114.txt](preflight-output/loc-54114.txt).

```text
LOCTYPES=63175 failures=0 maxId=63182
PLAN loc 54114 'Rug' -> 63183 models=[54198]->[12089] size=1x1 interact=0 anim=-1 options=[null, null, null, null, Remove]
```

### loc-54115 — exit0

Volledige uitvoer: [preflight-output/loc-54115.txt](preflight-output/loc-54115.txt).

```text
LOCTYPES=63175 failures=0 maxId=63182
PLAN loc 54115 'Rug' -> 63183 models=[54199]->[12089] size=1x1 interact=0 anim=-1 options=[null, null, null, null, Remove]
```

### loc-54116 — exit0

Volledige uitvoer: [preflight-output/loc-54116.txt](preflight-output/loc-54116.txt).

```text
LOCTYPES=63175 failures=0 maxId=63182
PLAN loc 54116 'Rug' -> 63183 models=[54200]->[12089] size=1x1 interact=0 anim=-1 options=[null, null, null, null, Remove]
```

### interfaces-230-232 — exit1

Volledige uitvoer: [preflight-output/interfaces-230-232.txt](preflight-output/interfaces-230-232.txt).

```text
Exception in thread "main" java.lang.IllegalStateException: group 230: OSRS font 645 has no 667 counterpart
```

### bundle-item — exit0

Volledige uitvoer: [preflight-output/bundle-item.txt](preflight-output/bundle-item.txt).

```text
CENSUS proven_free_holes=752 item_candidates=65100 safe_free=64348
PLAN item 24634 'Blood money count 13308' <- upstream_item:13308 bytes=40 models=[upstream_model:2485->65536]
PLAN item 24635 'Blood money count 13309' <- upstream_item:13309 bytes=40 models=[upstream_model:2486->65537]
PLAN item 24636 'Blood money count 13310' <- upstream_item:13310 bytes=40 models=[upstream_model:2487->65538]
PLAN item 24637 'Blood money count 13311' <- upstream_item:13311 bytes=40 models=[upstream_model:2488->65539]
PLAN item 24638 'Blood money count 13312' <- upstream_item:13312 bytes=40 models=[upstream_model:2667->65540]
PLAN item 24639 'Blood money count 13313' <- upstream_item:13313 bytes=40 models=[upstream_model:2825->65541]
PLAN item 24640 'Blood money count 13314' <- upstream_item:13314 bytes=40 models=[upstream_model:2423->65542]
PLAN item 24641 'Blood money count 13315' <- upstream_item:13315 bytes=40 models=[upstream_model:2710->65543]
PLAN item 24642 'Blood money count 13316' <- upstream_item:13316 bytes=40 models=[upstream_model:2775->65544]
PLAN item 24643 'Blood money' <- upstream_item:13307 bytes=92 models=[upstream_model:2484->65545]
PLAN item 24644 'Deadman's chest' <- upstream_item:13317 bytes=94 models=[upstream_model:29749->65546, upstream_model:29637->65547, upstream_model:29636->65548, upstream_model:29745->65549, upstream_model:29744->65550, upstream_model:151->29538]
PLAN item 24645 'Deadman's legs' <- upstream_item:13318 bytes=78 models=[upstream_model:29752->65551, upstream_model:29020->65552, upstream_model:29743->65553]
PLAN item 24646 'Deadman's cape' <- upstream_item:13319 bytes=78 models=[upstream_model:29748->65554, upstream_model:29633->65555, upstream_model:29725->65556]
PLAN item 24647 'Chronicle' <- upstream_item:13660 bytes=146 models=[upstream_model:31245->65557, upstream_model:31193->65558, upstream_model:31194->65559]
PLAN item 24648 'Starter sword' <- upstream_item:22331 bytes=92 models=[upstream_model:49503->65560, upstream_model:49484->65561, upstream_model:49483->65562]
PLAN item 24649 'Starter bow' <- upstream_item:22333 bytes=105 models=[upstream_model:49505->65563, upstream_model:49486->65564, upstream_model:49482->65565]
PLAN item 24650 'Starter staff' <- upstream_item:22335 bytes=106 models=[upstream_model:49504->65566, upstream_model:49481->65567, upstream_model:49485->65568]
PLAN item 24651 'Tuna' <- upstream_item:26149 bytes=49 models=[upstream_model:2355->66609]
PLAN item 24652 'Corrupted voidwaker' <- upstream_item:28531 bytes=80 models=[upstream_model:49500->65569, upstream_model:49472->65570, upstream_model:49471->65571]
PLAN item 24653 'Corrupted voidwaker (noted)' <- upstream_item:28532 bytes=7 models=[]
PLAN item 24654 'Corrupted dragon claws' <- upstream_item:28534 bytes=83 models=[upstream_model:49496->65572, upstream_model:49468->65573, upstream_model:49467->65574]
PLAN item 24655 'Corrupted dragon claws (noted)' <- upstream_item:28535 bytes=7 models=[]
PLAN item 24656 'Corrupted armadyl godsword' <- upstream_item:28537 bytes=91 models=[upstream_model:49502->65575, upstream_model:49469->65576, upstream_model:49470->65577]
PLAN item 24657 'Corrupted armadyl godsword (noted)' <- upstream_item:28538 bytes=7 models=[]
PLAN item 24658 'Corrupted twisted bow' <- upstream_item:28540 bytes=82 models=[upstream_model:49499->65578, upstream_model:49473->65579, upstream_model:49474->65580]
PLAN item 24659 'Corrupted twisted bow (noted)' <- upstream_item:28541 bytes=7 models=[]
PLAN item 24660 'Corrupted scythe of vitur' <- upstream_item:28543 bytes=111 models=[upstream_model:49497->65581, upstream_model:49478->65582, upstream_model:49476->65583]
PLAN item 24661 'Corrupted scythe of vitur (uncharged)' <- upstream_item:28545 bytes=98 models=[upstream_model:49501->65584, upstream_model:49475->65585, upstream_model:49477->65586]
PLAN item 24662 'Corrupted scythe of vitur (uncharged) (noted)' <- upstream_item:28546 bytes=7 models=[]
PLAN item 24663 'Corrupted tumeken's shadow' <- upstream_item:28547 bytes=123 models=[upstream_model:49498->65587, upstream_model:49479->65588, upstream_model:49480->65589]
PLAN item 24664 'Corrupted tumeken's shadow (uncharged)' <- upstream_item:28549 bytes=110 models=[upstream_model:49498->65587, upstream_model:49479->65588, upstream_model:49480->65589]
PLAN item 24665 'Corrupted tumeken's shadow (uncharged) (noted)' <- upstream_item:28550 bytes=7 models=[]
PLAN item 24666 'Trinket of vengeance' <- upstream_item:28561 bytes=57 models=[upstream_model:49495->63903]
PLAN item 24667 'Trinket of vengeance (noted)' <- upstream_item:28562 bytes=7 models=[]
PLAN item 24668 'Trinket of advanced weaponry' <- upstream_item:28567 bytes=68 models=[upstream_model:49493->65590]
PLAN item 24669 'Trinket of advanced weaponry (noted)' <- upstream_item:28568 bytes=7 models=[]
PLAN item 24670 'Corrupted dark bow' <- upstream_item:29599 bytes=79 models=[upstream_model:54205->65591, upstream_model:54213->65592, upstream_model:54212->65593]
PLAN item 24671 'Corrupted dark bow (noted)' <- upstream_item:29600 bytes=7 models=[]
PLAN item 24672 'Corrupted Volatile Nightmare staff' <- upstream_item:29602 bytes=95 models=[upstream_model:54204->65594, upstream_model:54210->65595, upstream_model:54214->65596]
PLAN item 24673 'Corrupted Volatile Nightmare staff (noted)' <- upstream_item:29603 bytes=7 models=[]
PLAN item 24674 'Armadyl godsword (Deadman)' <- upstream_item:29605 bytes=87 models=[upstream_model:49502->65575, upstream_model:49469->65576, upstream_model:49470->65577]
PLAN item 24675 'Voidwaker (Deadman)' <- upstream_item:29607 bytes=76 models=[upstream_model:49500->65569, upstream_model:49472->65570, upstream_model:49471->65571]
PLAN item 24676 'Volatile Nightmare staff (Deadman)' <- upstream_item:29609 bytes=91 models=[upstream_model:54204->65594, upstream_model:54210->65595, upstream_model:54214->65596]
PLAN item 24677 'Dark bow (Deadman)' <- upstream_item:29611 bytes=79 models=[upstream_model:54205->65591, upstream_model:54213->65592, upstream_model:54212->65593]
PLAN item 24678 'Imbued Zamorak cape (Deadman)' <- upstream_item:29613 bytes=95 models=[upstream_model:54203->65597, upstream_model:54216->65598, upstream_model:54215->65599]
PLAN item 24679 'Imbued Guthix cape (Deadman)' <- upstream_item:29615 bytes=130 models=[upstream_model:54203->65597, upstream_model:54216->65598, upstream_model:54215->65599]
PLAN item 24680 'Imbued Saradomin cape (Deadman)' <- upstream_item:29617 bytes=133 models=[upstream_model:54203->65597, upstream_model:54216->65598, upstream_model:54215->65599]
PLAN item 24681 'Armageddon rug' <- upstream_item:29619 bytes=50 models=[upstream_model:54208->65600]
PLAN item 24682 'Armageddon rug (noted)' <- upstream_item:29620 bytes=7 models=[]
PLAN item 24683 'Armageddon teleport scroll' <- upstream_item:29622 bytes=84 models=[upstream_model:54206->65601, upstream_model:54211->65602]
PLAN item 24684 'Armageddon teleport scroll (noted)' <- upstream_item:29623 bytes=7 models=[]
PLAN item 24685 'Armageddon weapon scroll' <- upstream_item:29625 bytes=71 models=[upstream_model:54207->65603]
PLAN item 24686 'Armageddon weapon scroll (noted)' <- upstream_item:29626 bytes=7 models=[]
PLAN item 24687 'Armageddon cape fabric' <- upstream_item:29628 bytes=62 models=[upstream_model:54201->65604]
PLAN item 24688 'Armageddon cape fabric (noted)' <- upstream_item:29629 bytes=7 models=[]
PLAN item 24689 'Annihilation weapon scroll' <- upstream_item:33012 bytes=73 models=[upstream_model:54207->65603]
PLAN item 24690 'Annihilation weapon scroll (noted)' <- upstream_item:33013 bytes=7 models=[]
PLAN item 24691 'Annihilation blueprints' <- upstream_item:33015 bytes=63 models=[upstream_model:28470->65605]
PLAN item 24692 'Annihilation blueprints (noted)' <- upstream_item:33016 bytes=7 models=[]
PLAN item 24693 'Annihilation teleport scroll' <- upstream_item:33018 bytes=86 models=[upstream_model:54206->65601, upstream_model:54211->65602]
PLAN item 24694 'Annihilation teleport scroll (noted)' <- upstream_item:33019 bytes=7 models=[]
PLAN item 24695 'Bow of Faerdhinen (c)' <- upstream_item:33021 bytes=88 models=[upstream_model:28476->65606, upstream_model:59942->65607, upstream_model:59943->65608]
PLAN item 24696 'Crystal body' <- upstream_item:33023 bytes=98 models=[upstream_model:59961->65609, upstream_model:59940->65610, upstream_model:59954->65611, upstream_model:151->29538]
PLAN item 24697 'Crystal body (inactive)' <- upstream_item:33025 bytes=92 models=[upstream_model:59962->65612, upstream_model:59941->65613, upstream_model:59955->65614, upstream_model:151->29538]
PLAN item 24698 'Crystal legs' <- upstream_item:33027 bytes=95 models=[upstream_model:59959->65615, upstream_model:59938->65616, upstream_model:59952->65617]
PLAN item 24699 'Crystal legs (inactive)' <- upstream_item:33029 bytes=89 models=[upstream_model:59960->65618, upstream_model:59939->65619, upstream_model:59953->65620]
PLAN item 24700 'Crystal helm' <- upstream_item:33031 bytes=110 models=[upstream_model:59957->65621, upstream_model:59935->65622, upstream_model:59949->65623, upstream_model:59930->65624, upstream_model:59932->65625]
PLAN item 24701 'Crystal helm (inactive)' <- upstream_item:33033 bytes=104 models=[upstream_model:59958->65626, upstream_model:59936->65627, upstream_model:59950->65628, upstream_model:59931->65629, upstream_model:59933->65630]
PLAN item 24702 'Toxic staff (uncharged)' <- upstream_item:33035 bytes=89 models=[upstream_model:28897->65631, upstream_model:59946->65632, upstream_model:59947->65633]
PLAN item 24703 'Toxic staff (Deadman)' <- upstream_item:33036 bytes=116 models=[upstream_model:41576->65634, upstream_model:59944->65635, upstream_model:59945->65636]
PLAN item 24704 'Thunder khopesh' <- upstream_item:33041 bytes=102 models=[upstream_model:55637->65637, upstream_model:55757->65638, upstream_model:55756->65639]
PLAN item 24705 'Thunder khopesh (noted)' <- upstream_item:33042 bytes=7 models=[]
PLAN item 24706 'Trinket of fortuity (active)' <- upstream_item:33050 bytes=72 models=[upstream_model:28474->65640]
PLAN item 24707 'Trinket of fortuity (active) (noted)' <- upstream_item:33051 bytes=7 models=[]
PLAN item 24708 'Starter cape' <- upstream_item:33063 bytes=72 models=[upstream_model:37197->65641, upstream_model:37019->65642, upstream_model:37051->65643]
PLAN item 24709 'Annihilation exit portal blueprints' <- upstream_item:33122 bytes=65 models=[upstream_model:28470->65605]
PLAN item 24710 'Fire ruby' <- upstream_item:34422 bytes=52 models=[upstream_model:61875->65644]
PLAN item 24711 'Fire ruby (noted)' <- upstream_item:34423 bytes=7 models=[]
PLAN item 24712 'Amulet of fire' <- upstream_item:34425 bytes=80 models=[upstream_model:61877->65645, upstream_model:61881->65646, upstream_model:61884->65647]
PLAN item 24713 'Amulet of fire (noted)' <- upstream_item:34426 bytes=7 models=[]
PLAN item 24714 'Teleport card' <- upstream_item:13658 bytes=55 models=[upstream_model:31242->65648]
PLAN item 24715 'Teleport card (noted)' <- upstream_item:13659 bytes=7 models=[]
```

57 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### bundle-npc — exit1

Volledige uitvoer: [preflight-output/bundle-npc.txt](preflight-output/bundle-npc.txt).

```text
CENSUS npc_model_candidates=65100 next npc=15081 bas=2398 seq=16221 frameset=4574 base=3580 synth=10561
Exception in thread "main" java.lang.IllegalStateException: skeletal (animaya) sequence 13893632 cannot be represented in 667
```

### bundle-seq — exit0

Volledige uitvoer: [preflight-output/bundle-seq.txt](preflight-output/bundle-seq.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN seq 11444 -> 16221
PLAN seq 11810 -> 16222
PLAN seq 11811 -> 16223
PLAN seq 11812 -> 16224
PLAN seq 11935 -> 16225
PLAN seq 11936 -> 16226
PLAN seq 11937 -> 16227
PLAN seq 13701 -> 16228
PLAN seq 13702 -> 16229
PLAN seq 13703 -> 16230
PLAN seq 13704 -> 16231
PLAN seq 13705 -> 16232
PLAN seq 13706 -> 16233
PLAN seq 13707 -> 16234
PLAN seq 13708 -> 16235
PLAN seq 13709 -> 16236
PLAN seq 13710 -> 16237
PLAN seq 13711 -> 16238
PLAN seq 13712 -> 16239
PLAN seq 13713 -> 16240
PLAN seq 13714 -> 16241
PLAN seq 14497 -> 16242
```

21 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### bundle-spotanim — exit0

Volledige uitvoer: [preflight-output/bundle-spotanim.txt](preflight-output/bundle-spotanim.txt).

```text
CENSUS spotanim_model_holes=64591 next spot=3220 seq=16221 frameset=4574 base=3580 synth=10561
PLAN spotanim 3030 -> 3220 model 44174->2870 seq 11463->15388
PLAN spotanim 3031 -> 3221 model 44174->2870 seq 11810->16221
PLAN spotanim 3032 -> 3222 model 44174->2870 seq 11810->16221
PLAN spotanim 3033 -> 3223 model 44174->2870 seq 11811->16222
PLAN spotanim 3565 -> 3224 model 28458->12089 seq 13708->16223
PLAN spotanim 3566 -> 3225 model 28458->12089 seq 13709->16224
PLAN spotanim 3567 -> 3226 model 28458->12089 seq 13710->16225
PLAN spotanim 3568 -> 3227 model 28458->12089 seq 13711->16226
PLAN spotanim 3569 -> 3228 model 28458->12089 seq 13712->16227
PLAN spotanim 3570 -> 3229 model 28458->12089 seq 13713->16228
PLAN spotanim 3571 -> 3230 model 28458->12089 seq 13714->16229
PLAN spotanim 4020 -> 3231 model 3123->4108 seq 699->15740
```

3 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### bundle-loc — exit0

Volledige uitvoer: [preflight-output/bundle-loc.txt](preflight-output/bundle-loc.txt).

```text
LOCTYPES=63175 failures=0 maxId=63182
PLAN loc 54114 'Rug' -> 63183 models=[54198]->[12089] size=1x1 interact=0 anim=-1 options=[null, null, null, null, Remove]
PLAN loc 54115 'Rug' -> 63184 models=[54199]->[12090] size=1x1 interact=0 anim=-1 options=[null, null, null, null, Remove]
PLAN loc 54116 'Rug' -> 63185 models=[54200]->[12091] size=1x1 interact=0 anim=-1 options=[null, null, null, null, Remove]
```

### interface-232 — exit0

Volledige uitvoer: [preflight-output/interface-232.txt](preflight-output/interface-232.txt).

```text
PLAN osrs 232 -> interface 1185: 3 components
```

### npc-skeletal-15571 — exit0

Volledige uitvoer: [preflight-output/npc-skeletal-15571.txt](preflight-output/npc-skeletal-15571.txt).

```text
PLAN skeletal npc 15571 -> 15081 bas=2398 models=[49222, 49218, 49221, 49224]->[65536, 65537, 65538, 65539] heads=[] seqs={10230=16221, 10233=16222} vertices=2289 clusters=352->107 error max=105,6 mean=4,53 far=3207/274680 (667 units)
```

2 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### bundle-npc-classic — exit0

Volledige uitvoer: [preflight-output/bundle-npc-classic.txt](preflight-output/bundle-npc-classic.txt).

```text
CENSUS npc_model_candidates=65100 next npc=15081 bas=2398 seq=16221 frameset=4574 base=3580 synth=10561
PLAN npc 6516 'Financial Wizard' -> 15081 bas=2398 models=[20399, 214, 250, 20405, 3189, 20395, 177, 20402, 181]->[65536, 54914, 54814, 59207, 53718, 59213, 56708, 65537, 58335] heads=[82, 52, 20393]->[54850, 55072, 65538] seqs={808=808, 819=819, 820=820, 821=821, 822=822} ops=[Talk-to, null, Bank, Collect, Deposit-box]
PLAN npc 6485 'Financial Seer' -> 15082 bas=2398 models=[408, 417, 21914, 21899, 10706, 21911, 358, 437]->[65539, 65540, 65541, 65542, 55409, 65543, 61195, 55464] heads=[125, 136]->[65544, 65545] seqs={808=808, 819=819, 820=820, 821=821, 822=822} ops=[Talk-to, null, Bank, Collect, Deposit-box]
PLAN npc 8391 'Nigel' -> 15083 bas=2398 models=[29473, 29637, 29633, 29636, 29634, 29635, 29638, 29639]->[65546, 65547, 65548, 65549, 65550, 65551, 65552, 65553] heads=[29640]->[65554] seqs={808=808, 819=819, 820=820, 821=821, 822=822} ops=[Talk-to, null, null, Trade, null]
PLAN npc 8390 'Nigel' -> 15084 bas=2398 models=[29473, 29637, 29633, 29636, 29634, 29635, 29638, 29639]->[65546, 65547, 65548, 65549, 65550, 65551, 65552, 65553] heads=[29640]->[65554] seqs={808=808, 819=819, 820=820, 821=821, 822=822} ops=[Talk-to, null, Teleport, Trade, null]
PLAN npc 15569 'null' -> 15085 bas=2399 models=[34586]->[31511] heads=[]->[] seqs={7975=15874, 7977=15875} ops=[null, null, null, null, null]
PLAN npc 15570 'null' -> 15086 bas=2400 models=[24602, 24605, 24606]->[31472, 31466, 31461] heads=[]->[] seqs={6236=15878} ops=[null, null, null, null, null]
```

17 expliciete conversiebeperkingen: zie `DROPPED` in de uitvoer.

### wisp-sidecar — exit0

Volledige uitvoer: [preflight-output/wisp-sidecar.txt](preflight-output/wisp-sidecar.txt).

```text

```
