# Source puzzle rendering audit, B16-Nov

`CluePuzzleSourceProbe.java` reads only cache2686; `cache/puzzle-pictures.json`
contains the exact16enums (selectors/forward/inverse),168tile definitions with
raw SHA256,168model SHA256s and the seven complete model lists. It verifies
24pieces per picture, each inverse entry, enum framing and source itemparam186.
No target cache, ledger or runtime data is written by this probe.

Reproduce with Java11 source mode, classpath
`game/build/classes/kotlin/main;game/build/install/game/lib/*`, source cache
`C:/RSPS/import-source/openrs2-2686/cache`, output
`docs/CLUE/cache/puzzle-pictures.json`.

Crucial source finding: source690 instructions168..171 read OC_PARAM186 then
IF_SETMODEL1108. The client does NOT display these pieces by IF_SETOBJECT. Thus
these168blank-name tile items do not need importing into the inventory/GE. Port
their actual models and preserve the source688 model camera0,0,513,0,0,1400.
Source688 builds25click cells49x49 under306:4, and24model layers49x49; parent
251x251 gives integer stride50. Label `<col=ff9040>Sliding piece</col>`, action
`Move` come directly from the source, not invented controls. Source691 plays
1859 on a legal slide and2304 on a rejected slide; source692 positions a piece
at its actual board cell. Sourcevarp261 and varcs81..106 are NOT allocated 667
selectors and must not be copied. A static model atlas plus server hide/position
packets can preserve source presentation without a new wide-model packet.

| Selector | Forward/inverse enums | Source first tile | Model |
|---:|---|---:|---:|
|1|1354/1866|2749|3413|
|2|1355/1867|3619|4156|
|3|1356/1868|3643|4180|
|4|1357/1869|20283|31686|
|5|1358/1870|20307|31723|
|6|1359/1871|20331|31692|
|9|2318/2319|23418|37000|

The JSON is authoritative for every model; verify this summary against it when
building. Selectors7/8/10 are quest puzzles, excluded from Treasure Trails.
SourceUI306static6components already decoded in cache/interfaces.json. Baking
and importing its scripted controls/models, the held-clue puzzle transactions,
NPC dialogue, death/duplicate-box ownership and client acceptance remain open.
Do not infer an NPC's exact box ID solely from adjacent clue/item numbers.
No random picture or sliding/light-box split probability is established here.
Light-box wiring remains a separate unobserved server input; no masks invented.
