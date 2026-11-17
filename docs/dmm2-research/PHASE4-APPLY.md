# DMM2 fase 4(c): apply-overdracht voor Claude

GPT-A heeft `DMM2-APPLY.sh` geleverd en **niet uitgevoerd**. De scriptinhoud is gecontroleerd met `bash -n`.
De enige cache-acties van GPT-A zijn de aantoonbaar ongewijzigde PLAN-kopieën van fase4(b).

Na merge en compilatie van de zes actuele tools kiest Claude expliciet de juiste paden. Voorbeeldvorm:

```bash
export DMM2_ROOT='C:/RSPS'
export DMM2_DATA='C:/RSPS/work/claude-dmm2/data'
export DMM2_CP='C:/RSPS/work/claude-dmm2/game/build/classes/kotlin/main;C:/RSPS/work/claude-dmm2/game/build/install/game/lib/*'
export DMM2_APPLY_OUT='C:/RSPS/work/claude-dmm2/build/dmm2-apply-YYYYMMDD-HHMMSS'
export DMM2_JAVA='C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe'
bash docs/dmm2-research/DMM2-APPLY.sh
```

Dit zijn aan te vullen paden, geen claim dat deze combinatie al de bedoelde actuele testcaches bevat. De twee targets zijn
`$DMM2_DATA/cache` en `$DMM2_ROOT/file-server/cache`, met de ledger onder `$DMM2_ROOT`. Beide moeten kloppen en de testserver
en fileserver moeten dicht zijn. De wrapper start één Gradle-taak `dmm2ClaudeApply --priority low`, zodat alle native
Java-processen dezelfde machinebrede bouwlock houden. Het script stopt/killt geen andere processen. De officiële tools
blijven de enige cachewriters en gebruiken CacheTransaction.

Volgorde: items → klassieke NPCs → expliciete seqs → spots → locs → UI232 → Wisp skeletal → Wisp echte sidecar → ids.tsv.
Iedere Java-aanroep is volledig uitgeschreven in het script. Itemimport schrijft ook `items.generated.yml` in de nieuwe
apply-outputmap. Dat is de native metadata/rankbasis voor Claude's voorstelpatch; niet rechtstreeks de productieconfig.
Een latere tool ziet de eerdere echte writes/ledger en reserveert zijn eigen vrije IDs; plak dus nooit de PLAN-IDs over.

Elke stap moet exit0 geven én een expliciete voltooide transactie: de itemtool drukt `TRANSACTION tx-… applied=true` af;
de andere tools `APPLIED transaction=tx-…`. De wrapper toont voor beide `transaction=… APPLIED_OK`. Wisp-sidecar vereist
ook `VERIFY_OK both targets hold intended bytes and names`. Stop bij iedere fout; de bestaande journal/rollbacktools
blijven het herstelpad. Dit is een reeks bestaande transacties, geen zelfverzonnen alles-in-één-transactie.

`dmm2_ids_from_apply.py` leest uitsluitend de acht voltooide native uitvoerbestanden van één nieuwe apply-ronde. Het controleert
vereiste item/NPC/seq/spot/loc/UI-mappings, conflicten, integergrenzen, unieke lokale item-IDs en Wisp's bronmodellen/seqs.
Het publiceert daarna atomair `data/cfg/dmm2/ids.tsv`. De vier bestaande trinketaliases zijn uit de eerdere lokale cache/
Items.kt bevestigd (28564→23822,28570→23824,33044→23823,33047→23825); notes daarvan zijn geen onderdeel van deze import.
Een mislukte/PLAN-ronde raakt een bestaand ids.tsv niet.

Zes eigen parser/breektests zijn groen. Ze gebruiken duidelijk synthetische applyfixtures; er is geen echte apply-PASS.
Ze weigeren ook alle acht daadwerkelijk vastgelegde fase3-PLANs, plus partiële/mislukte uitvoer, ontbrekende/conflicterende
IDs, dubbele lokale items, overflow en veranderde Wisp-brondata. Broncode staat naast het script.

Na apply: genereer voorstelpatches opnieuw met de **werkelijke** ids.tsv en `items.generated.yml`; sluit UI/AV/POH aan en
volg `TESTCLIENT-CHECKLIST.md`. Release-/chestvlaggen zijn door GPT-A niet geactiveerd. UI230/font645, icon en34607 blijven
afzonderlijk te bewijzen imports; dit draaiboek vult daarvoor geen fictieve mappings in.
