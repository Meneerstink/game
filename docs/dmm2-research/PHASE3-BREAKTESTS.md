# Fase 3 — A8/I10-breektests

`Dmm2BreakTests` voegt tests over meerdere services toe aan de eerdere eigen testklassen.
De definitieve aantallen, timestamps en uitslag komen uit verse JUnit-XML in `PHASE3-VALIDATION.json`.
Een geschreven test geldt pas als groen wanneer die run klaar is.

|Systeem|Nieuwe onderbrekings-/herhaalproef|Behoudsinvariant|
|---|---|---|
|Breachmigratie|Vijf overflow-retries, reconnect, daarna vijf herhaalde logins|Origineel BP/earned/audit onveranderd bij falen; één ceil(200:1)-krediet bij slagen|
|Blood money/Nigel armour|Geen geld, alle drie unlocks, reconnect en stale unlock|Geen DP-uitgave; één fysieke betaling/output per eerste unlock|
|Pending money|Inventory en bank vol, coinsstack totMAX, reconnect/retry|Pending blijft tot volledige plaatsing; geen tweede stack of tweede levering|
|Reward Store|Alle zeven producten vijfmaal kopen/sellback met reconnect en oude slots|DP + ongebruikte rewardwaarde constant; oude sellback kan geen tweede refund geven|
|Permanente unlock|Consumeer, reconnect, opnieuw unlock/sellback|Eén flag; geen nieuw verkoopbaar product na consumptie|
|Safebox/active Fortuity|Skull/nietskull × PvM/PvP-resolver, reconnect|Safe kopie heeft geen effect/refund en komt niet in de death-risklijsten|
|Charged scythe|Charge, safebox, JSON-containerreload, withdrawal, well-refund, replay|200 charges blijven bewaard; precies600blood-runes terug, geen tweede refund|
|Starter|Reclaim/reconnect vijfmaal, eenmaligeintroreward/reconnect vijfmaal|Eén ongeopend pack; exact één1M+100tabs; geen cash uit reclaim|
|Alle zes trinkets|Carried én safe kopie, twee deathcontexts × skull/nietskull × vijfretries|Iedere carried kopie eenmaal in risk; iedere safe kopie buiten risk; totaal12|
|Volle safebox/inventory|Elf fysieke munten, tien boxslots, volle inventory, stale withdrawal/reconnect|Elf munten blijven over inventory+bank+box; geen verloren unit/overflowstack|
|Fire-amulet|Volle inventory, echte receptkosten, reconnect en vijf complete-retries|Eén ruby+amulet-of-magic+100fire verbruikt; éénamulet en éénXP-event|
|Advanced weaponry|Vijf onderbroken/obstructed openings met reload, dan volledigeinventory/replay|Eéntrinket wordt éénwapen;28fysiekeitems blijven28; geen RNG bij geweigerde/stale call|
|Undead|Summon, vijf reconnects en andere summonkeuze|Persistent gedeelde17tickcooldown; geen tweede spawn; trinket behouden|
|XP-slot|Combatuit/reconnect, daarnaalleenHPuit/reconnect|Allezevencombatblocked of alleenHPblocked; Summoningvrij; DPbehouden|
|POH-rug|Logout tijdensbouw, herstart, finish, vijfreplays/relogs, verwijderen/replay|Geen kosten vóórfinish; éénrug+4clothverbruikt; éénXP; éénrug terug|
|Echte PvM-executie|Echte DeathResolver→DeathExecutor→Gravestone, zes carried en zes safe kopieën|Zes naar echt in-memorygraf, zes in box, twaalf totaal; vijf executor-replays geven niets extra|

`Dmm2SourcePolicyTests` controleert daarnaast de gekozen Avarice-waardering (Long-overflow/negatief/override), de expliciete
khopesh-default en het nieuw bewezen ancient-cape-recept: ontbrekende fabric, volle inventory, één conversie, geen Nigel-bypass,
geen herhaalde consumptie en dezelfde normale base voor de bestaande doodadapter.

De oude eigen klassen blijven de individuele grenzen afdekken: SafeDepositBoxTests, Dmm2CurrencyTests, Dmm2ItemServicesTests,
NigelRewardStoreTests, NigelHouseRewardsTests, Dmm2CosmeticsTests, starter-/Chronicle-/XP- en NPC/UI-sessietests.
De gerichte run selecteert uitsluitend deze eigen klassen; geen brede death/combat-suite.

## Wat de reloadproef wel en niet uitvoert

Containers gaan door Gson en de echte `JsonPlayerSerializer.PersistentContainer`-DTO. Persistente Int/Boolean-attributen worden
volgens hun bestaande types teruggezet; timers gaan door `TimerMap.toPersistentTimers` en worden met hun bestaande flags herbouwd.
Alle transient access-/bouw-/summonrefs verdwijnen. Het is een in-memory grensproef, geen toegang tot spelersaves en geen echte socketlogin.
De echte PvM-executietest gebruikt tevens de bestaande grave-/officecontainers en telt de inhoud; geen mocks voor de transfer zelf.

De volledige I10-route met echte GE-offers, Perdu, loot keys, offline killer, guard en alle D1–D20-uitkomsten moet Claude daarnaast
in de testclient aftekenen. Voor en na administratieve fixture-/itemsetup worden geen ingameconservatieclaims afgeleid.
`TESTCLIENT-CHECKLIST.md` maakt deze resterende runtimeproeven concreet.
