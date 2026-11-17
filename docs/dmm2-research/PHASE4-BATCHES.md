# DMM2 fase 4(a): echte benoemde importbatches

De zes bestaande importtools bevatten nu de batches uit de fase-3 bundeluitkomsten. Geen nieuwe importer en geen
reflectie/in-memory registry-aanpassing nodig. De bron is de gepinde OpenRS2-cache 2686. Gebruik alleen Claude's
werkelijke apply-uitkomsten voor lokale runtime-IDs.

|Bestaande tool|Native argumenten zonder apply|Inhoud|
|---|---|---|
|OsrsItemImportTool|`dmm2-items`|51 ouders, exacte volgorde van `preflight-output/bundle-item.txt`, ieder `Spec(noted=true)`; 82 records inclusief notes/counts|
|OsrsNpcImportTool|`dmm2-npcs`|6516,6485,8391,8390,15569,15570; volgorde `bundle-npc-classic`|
|OsrsFxImportTool|`dmm2-seqs`|22 bronsequences, volgorde `bundle-seq`|
|OsrsFxImportTool|`dmm2-spots`|12 bronspotanims, volgorde `bundle-spotanim`|
|OsrsLocImportTool|`dmm2-locs`|54114,54115,54116; de drie rugdelen|
|OsrsInterfacePortTool|`dmm2`|Uitsluitend UI232; eigen JSON, rapport en `cfg/dmm2/interfaces.tsv`|
|OsrsPetImportTool|`skeletal-npc dmm2-wisp`|15571; beweging uit de brondefinitie, geen verzonnen extra sequences|

UI230 is uitgesloten: OSRS-font645 heeft geen bewezen 667-mapping. UI232 is de inventory-zijbalk met drie componenten;
dit is **geen volledige safebox-UI**. De eigen decode `dmm2-interfaces.json` hernoemt alleen schema-eigenschappen van de
eerder vastgelegde cachedecoder; verwijzingen en waarden blijven gelijk. ToA en CoX behouden hun bestaande route.

Wisp vereist na de skeletal-import de bestaande `SkeletalSidecarExportTool cache apply` met zijn echte owner/seq-IDs,
bronmodellen49222,49218,49221,49224 en bronsequences10230/10233 (rig2138, clips212:0/210:0). De klassieke bake is een fallback;
het testclientbeeld moet de echte sidecar afspelen. Klassieke `dmm2-npcs` bevat daarom geen Wisp.

Eigen validatie: `Dmm2ImportBatchTests` drie tests groen via JUnit4, actuele zes toolbronnen plus de test gecompileerd
met Kotlin1.8.0 binnen het centrale Gradle-bouwslot, `--priority low`. Het betreft bronvolgorde/notes, skeletal-scheiding
en namespace/font-grenzen. Named native PLANs en cachehashbewijs volgen apart in fase4(b).
