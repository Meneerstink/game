# PREFLIGHT 7.1: bestaande Wisp, alleen extra magic-seq

Bron: cache2686 NPC12157 en15571 hebben exact dezelfde vier modellen, rig2138 en bewegingen.
`phase7-sources/wisp-comparison.json` bevat decoderuitvoer en bronhashes.
Pet-emote10234 is expliciet MELEE. Geen10241..10246 bestaat in de huidige embedded sidecar.
Keuze MAGIC01/10241 is advies op gameval+cacheduur90cycli; de OSRS-DMM-thralltoewijzing is ONZEKER.

Herbruik de bestaande skin/base3845/modellen/NPC/BAS/bewegingen. De extra-modus zit in de bestaande
OsrsPetImportTool, geen afzonderlijke importer. Een bestaande pet-melee-bake inzetten als magieworp
zou het verkeerde aanvalstype geven. Deze modus is daarom behouden uit het onderbroken werk.

## Uitgevoerde opdracht (PLAN op kopie)

Vanuit work/gpt-a, nadat twee fysieke kopieen van de actuele testcache plus de openbare asset map
in `build/dmm2/phase7-plan` zijn gemaakt:

```powershell
$env:RGRADLE_LOCAL='1'
try {
  & C:/RSPS/tools/remote/rgradle.cmd dmm2Phase7WispPlan -I docs/dmm2-research/phase7-wisp-plan.gradle --offline --priority low --no-parallel
} finally { Remove-Item Env:RGRADLE_LOCAL }
```

Lokaal vereist voor deze bronprobe: de OSRS2686-reader en beide fysieke PLAN-kopieen staan hier.
De Gradle-taak compileert de drie actuele tools plus hun vier eigen unit-tests binnen het bouwslot.
De exacte Java-argv, source-cache en beide kopie-targets staan in `phase7-output/wisp-extra-command.json`.
Native aanroep: `OsrsPetImportTool skeletal-npc-extra-seq 15571 10241`, zonder apply-vlag.

## Werkelijke uitvoer

- NPC15571 ->15320 blijft, vier bestaande modellen hergebruikt via ModelArchive (ook archive37).
- Base3845 blijft; nieuwe frameset6016 met45frames; bron10241 -> PLAN-seq17773.
- Native clip224:0 (462628bytes), rig2138 en skin15320 hergebruikt,0..90cycli, twee bron-framegeluiden.
- 50 logische mutaties: 45frames +2synths +1seq +1nativeclip +1indexappend.
- Twee targets:98CREATE,2REPLACE,0CONFLICT. REPLACE is UITSLUITEND skel_index append;
  de volledige bestaande61443bytes zijn exact prefixgelijk behouden, met checksumguard.
- **De kaarteis0 fysieke REPLACE is niet gehaald.** Bestaande client leest een gedeelde indexgroep;
  die groep moet verlengd worden om een nieuwe native seq op te nemen. Geen claim van0REPLACE.
- Classic fallbackbake max118.004/mean5.332667units. Native skeletalclient gebruikt de exacte bronclip;
  geen claim dat de classic fallback pixelgelijk is. Claude moet het model in de client controleren.
- Vier eigen JUnit-tests groen,80/80 cachekopiefiles SHA256 ongewijzigd, journal leeg.
- Eerste mislukte PLAN (oude directe model-indexlookup) bewaard in `first-missing-wide-model/`;
  opgelost met de bestaande ModelArchive.read, definitieve PLAN exit0.

## Claude na review

Geen apply door GPT-A uitgevoerd. Herhaal PLAN op de actuele cache met de nieuwe tools en openbare ledger.
Alleen als de indexappend als minimale uitbreiding aanvaard is: zelfde native aanroep met de apply-vlag
in Claude's eigen cache-ronde. Geen volledige `skeletal-npc dmm2-wisp`-herimport.
Neem de werkelijk gerapporteerde EXTRA_SEQ-local op als `seq<TAB>10241<TAB>local` in ids.tsv;
PLAN-id17773 is geen vast productie-id. Dmm2Boot gebruikt uitsluitend Dmm2Ids.seq(10241), ontbrekend=null.
Controleer daarna Lil Wisp magic op1/6tegels, transparantie, idle/walk,4tickaanvallen, geen misvormde skin.

## Afhandeling door Claude (dossier, 17 november)

- 21:4x: de twee checksum-bewaakte index-appends expliciet goedgekeurd als minimale noodzakelijke uitbreiding.
- 21:51: toegepast door Claude, tx-20261117-205101, 100 writes/VERIFY_OK, werkelijke seq10241 ->17773.
- 22:52: servertrace Wisp15320/anim17773 op cycle588 en592, projectiel3472 + impact3473/delay30 tegen KQ.
- 22:5x: beeld door Claude vergeleken met OSRS-Wisp, doorschijnend paarsblauw/tentakels/cyaangloed,
  idle/walk/volgen zonder misvormde skin. Bewijs: screens/DMM-PAKKET-2/f8-wisp-sheet.png en f8-wisp-zoom.png.

Deze externe voortgang is geen testclientactie van GPT-A. Geen herimport nodig. De hier gepubliceerde
ids.tsv bevat170regels, bytegelijk aan claude/dmm2-integratie, SHA256
`dcd2930d403ceb1d06fcd4d14d6aa60aec6ecf90005283ffe20e455074f461bc`.
Dmm2Boot blijft uitsluitend Dmm2Ids.seq(WISP_MAGIC_ATTACK) gebruiken; unit-test dekt ook ontbrekende mapping.
De oorspronkelijke keuze MAGIC01 blijft gelabeld advies: de echte OSRS-DMM-thrallvariant is niet uit
een OSRS-opname vastgesteld. Claude's beeldbewijs bewijst ons renderpad, niet die laatste brontoewijzing.
