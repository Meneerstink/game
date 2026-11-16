# DMM2 code inventory (read-only)

Scope: Kotlin main/tests + commands.yml/new_player.yml. No saves, logs, data/ge or game.yml read.


## game/plugins/src/main/kotlin/gg/rsmod/plugins/api/cfg/Items.kt
SHA256 a6c343d115f72c2b2773bd1364c747628b0db8efeacd63dfbc2cbb64efea8c92 | 20510 lines
### dmm-items
20276:     const val TRINKET_OF_FAIRIES = 23822
20277:     const val TRINKET_OF_AVARICE = 23823
20278:     const val TRINKET_OF_UNDEAD = 23824
20279:     const val TRINKET_OF_FORTUITY_INACTIVE = 23825
### starter
20492:     const val DEADMAN_CHEST = 24623

## game/plugins/src/main/kotlin/gg/rsmod/plugins/api/cfg/Objs.kt
SHA256 9a90bdaf32a0e3363679da93a794b1f3064cab0670c4a85c8e4648869a4d9412 | 26763 lines
### dmm-items
15370:     const val TRINKET_OF_SEALIFICATION = 29000

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/activity/pest_control/VoidDeflectorMigration.kt
SHA256 e0aac9bcd2292fb06901dd2ada53e75feb5c51ff4ce74df78666ba01f793dc61 | 70 lines
### safe-deposit-integration
21:         // Every container the save loads (beast-of-burden cargo, gift, ... - Player.containers), as RemovedItems.purge walks them.
28:             addAll(player.containers.values)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/activity/toa/ToaSupplies.kt
SHA256 bcb851c00e1fa995d7e9918ccdd2108ab973eb6971e32b9d7340ac4a011fe30b | 240 lines
### safe-deposit-integration
235:             (listOf(player.inventory, player.bank) + player.containers.values).forEach { container ->

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/grandexchange/GeHomeHall.kt
SHA256 d37707870968594dd8329d8272a609ec691606b9f385612d2ee2d76328e54587 | 92 lines
### breach-points
15:  * Kuradal, the Wise Old Man, Pikkupstix), north-west "PvP" (Skully, the Quartermaster, Perdu, the Breach Trader,
41:     /** The Breach Trader (breach_shop.plugin.kts). */

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/grandexchange/grandexchange_unlocks.plugin.kts
SHA256 d9b660e2ae0a4576bfb63f49d3f3497caf15c97aae39aae6c023bc08fdfab585 | 350 lines
### breach-points
32:  * and staff to Eblis"); a coloured sceptre = sceptre + its quartz. The Breach Trader keeps selling the sceptres for points (owner: both).

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/wilderness/MageArenaII.kt
SHA256 ebc72e72344042355163dc23af9fac8502acdfa73dc4b16cc924c32325d5a7e6 | 164 lines
### safe-deposit-integration
87:         assets.symbol?.let { id -> player.containers.values.any { it.contains(id) } } == true
116:         assets.remains(god)?.let { id -> player.containers.values.any { it.contains(id) } } == true

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/cmd/conformance/DeadmanSuite.kt
SHA256 6c182b5332cb8f11f2ac71e44e4b452b29990bd339b75763b8ee5b13c5cf177f | 2192 lines
### starter
267:             val chestDue = gg.rsmod.plugins.content.starter.PvpRespawnChest.eligible(b)
268:             val chest = gg.rsmod.plugins.content.starter.PvpRespawnChest.ITEM

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/cmd/conformance/QuestsSuite.kt
SHA256 0ff3e42b76c6008d4ad00f6f37dd1cfc5d4b9985f154490355fa8703b68c8887 | 2114 lines
### starter
1442:         val pack = gg.rsmod.plugins.content.newplayer.NewPlayerConfig.current.starterChestItem
1444:         run.expectTrue(entry, "only the Deadman starter pack was added", itemsAfter == expected, "before $itemsBefore after $itemsAfter", rule = "NewPlayerStart.grantStarterPack after the first introduction")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/cmd/conformance/SystemsSuite.kt
SHA256 ec678f9652a7cf9445807ebca5624f2ae0c88ac6d28b37aa2464ac97598eac3b | 1274 lines
### dmm-items
628:             gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestLoot.FIXED.any { it.item == Items.TRINKET_OF_FAIRIES }, "DeadmanChestLoot.FIXED",
631:         a.inventory.add(Items.TRINKET_OF_FAIRIES, 1)
633:         val slot = a.inventory.getItemIndex(Items.TRINKET_OF_FAIRIES, false)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/items/armor/DragonChainbodyMigration.kt
SHA256 ac7ccdf3927a100769982dbba411130b6dc2340de1768f38e0362f782db2252f | 66 lines
### safe-deposit-integration
17:         // Every container the save loads (beast-of-burden cargo, gift, ... - Player.containers), as RemovedItems.purge walks them.
24:             addAll(player.containers.values)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/death.plugin.kts
SHA256 a0eb9f4f1e8ebfe1f36c53f597540df6f9c0d92c88b7095f765649fe2d1f3088 | 175 lines
### starter
140:         // (78,000 coins + a Deadman's skull), at most one an hour (PvpRespawnChest).
141:         gg.rsmod.plugins.content.starter.PvpRespawnChest.grant(victim)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/ownership/Possessions.kt
SHA256 02742d889dab6d9ab3c8e00fe5763d79108ebb555a85aee86703eb1d48a4325d | 94 lines
### safe-deposit-integration
14:  * Every container the player has ([Player.containers]: inventory, equipment, bank, gravestone, Death's Office,
36:         return player.containers.entries.map { it.key to it.value }
68:         return player.containers.values.sumOf { container ->

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/LootingBag.kt
SHA256 81f81f58f3fcc8312dde583f05cc4037f187d67cdc07251c4ea92b37fb8d40f1 | 55 lines
### safe-deposit-integration
29:         val bag: ItemContainer = player.containers[KEY] ?: return 0

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachContribution.kt
SHA256 4f7ee02ac221c7437299c5dad57a8a7c87e4ba8236b8ab7f7b5c9fd22f2ca424 | 32 lines
### breach-points
29:     /** The [BreachPoints.EARNERS] biggest damage dealers with their damage; ties keep first-damage order. */
31:         damage.entries.sortedByDescending { it.value }.take(BreachPoints.EARNERS).map { it.key to it.value }

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachIds.kt
SHA256 927837b8bef2ba6e1077648feab6ae551995be0ccdda7c2ae68ef48dc3aa8f9a | 22 lines
### dmm-items
18:     const val TRINKET_OF_FAIRIES = Items.TRINKET_OF_FAIRIES
19:     const val TRINKET_OF_AVARICE = Items.TRINKET_OF_AVARICE
20:     const val TRINKET_OF_UNDEAD = Items.TRINKET_OF_UNDEAD
21:     const val TRINKET_OF_FORTUITY_INACTIVE = Items.TRINKET_OF_FORTUITY_INACTIVE

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachLoot.kt
SHA256 d8de5c193f2e772c8a1836d1f7e6b802c3fcb68d3a32c0a14219973e058e4a5b | 229 lines
### breach-points
17:  * drops for eligible players with [EMBLEM_DAMAGE]+ damage and trades in for [BreachPoints] ([DeadmanBreach.dropLoot]). [ItemIds] holds the ids of the items imported for this table.
### dmm-items
76:     val EXCLUDED_TRINKETS =
77:         setOf(ItemIds.TRINKET_OF_AVARICE, ItemIds.TRINKET_OF_UNDEAD, ItemIds.TRINKET_OF_FORTUITY_INACTIVE)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachPoints.kt
SHA256 1e9a27aef912ea624b4f13589b0ae7e2d7686185418e40f786c15ccb55e54692 | 57 lines
### breach-points
8:  * Breach Points (owner 2026-09-23: a separate currency, never Deadman Points).
16: object BreachPoints {
22:     val BALANCE = AttributeKey<Int>("breach_points")
25:     val EARNED = AttributeKey<Int>("breach_points_earned")
42:         player.message("You earn ${"%,d".format(given)} Breach Points (total ${"%,d".format(balance(player))}).$capNote")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachSim.kt
SHA256 75216600d875f94158207d06f66abb63b2723371e55f41b837f909c5c63a2317 | 347 lines
### breach-points
323:                     val pointsBefore = BreachPoints.balance(bot)
334:                     val points = BreachPoints.balance(bot) - pointsBefore

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/breach_points.plugin.kts
SHA256 45dbd603166500f5c546b9e9769f070453052e402f460d7ba229e2cb09e330ae | 32 lines
### breach-points
4:  * Breach Points (owner 2026-09-23, [BreachPoints]): the balance command and the Archaic emblem (tier 5) trade-in at the Breach
9: /** The Breach Trader npc (clone batch breach-trader); the same id breach_shop.plugin.kts spawns. */
12: on_command("breachpoints") {
14:         "Breach Points: ${"%,d".format(BreachPoints.balance(player))}. Earned from breach damage: " +
15:             "${"%,d".format(BreachPoints.earned(player))} / ${"%,d".format(BreachPoints.DAMAGE_CAP)}.",
22:             val value = BreachPoints.EMBLEM_TIER_5_VALUE
23:             chatNpc("I'll give you ${"%,d".format(value)} Breach Points for that emblem. Deal?", wrap = true)
27:                 BreachPoints.addBalance(player, value)
28:                 player.message("You trade in the emblem for ${"%,d".format(value)} Breach Points (total ${"%,d".format(BreachPoints.balance(player))}).")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/breach_shop.plugin.kts
SHA256 abcc534598e195d0c28e4e82809f4ff532e8447a1c69a41934cee5b1dc4edb76 | 93 lines
### breach-points
8:  * The Breach Trader (owner 2026-09-23: "Eigen breach-shop NPC"): a clone of the OSRS Emblem Trader 308 - the npc that ran the
9:  * Deadman: Annihilation points shop - named "Breach Trader" (OsrsNpcImportTool clone batch breach-trader, tx-20260923-192958),
10:  * in the Grand Exchange home hall beside the 78 Store keepers. Currency: Breach Points ([BreachPoints], earned in breaches, plus
26: val BREACH_SHOP = "Breach Trader"
28: /** Stock: item to price in Breach Points. Potions are the (4) doses. */
65:     currency = PointCurrency("breach point", "breach points", BreachPoints.BALANCE),
77:             "Breach Points buy my wares. You earn them by damaging breach monsters, and I'll take any Archaic emblem you bring me.",
80:         when (options("Show me your rewards.", "How many Breach Points do I have?", "Never mind.")) {
83:                 chatNpc("You have ${"%,d".format(BreachPoints.balance(player))} Breach Points.", wrap = true)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/DeadmanBreach.kt
SHA256 66bbde26c912758ceb8b2fe01a9e8215cc65e60e5079f7d2f47f3bdaf9d36b2a | 1073 lines
### breach-points
979:      * and Breach Points for the top [BreachPoints.EARNERS]. The monster is
1043:             if (BreachPoints.award(player, damage) > 0) paid++

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/chest/DeadmanChestLoot.kt
SHA256 f423959a356634c16ba56d3b8d5cbb9c68d047fb093ff944a9ee2dae9a1def74 | 139 lines
### dmm-items
74:             Entry(Items.TRINKET_OF_FAIRIES, 1, 8),

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/removed/RemovedItems.kt
SHA256 9ef5bf2bc7545ec641ad7a52611a6650bd00eb0e49c091f6063064e545e2e066 | 193 lines
### dmm-items
105:         gg.rsmod.plugins.content.items.potion.RemovedPotions.itemIds + gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot.EXCLUDED_TRINKETS +
### safe-deposit-integration
134:      * the retired looting bag, every beast-of-burden cargo - the save loads each one into [Player.containers]) and the player's own
146:                 addAll(player.containers.values)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/shops/PointCurrency.kt
SHA256 fbe5ed6127dd0af7d169b6578c34d81db2cb022617ef6967e0bc7197217f6e26 | 212 lines
### breach-points
165:     /** "Breach Points" - the currency as a title. */
171:      * N275 (owner 2026-10-24, the Breach Trader showed no balance): the balance at the top of the shop window, in the title
172:      * ("Breach Trader - Breach Points: 1,234"), and on the window's help line. Set when the shop opens and after every purchase.
186:      * Audit E-10: these used to throw. Selling an item to a point shop (Breach Trader) reached buyFromPlayer from the

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/store/store.plugin.kts
SHA256 58c284dd010c909956cebadba4deafc04bffe4829704b8f35cbf99678abd0602 | 105 lines
### breach-points
6: import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
42:         BreachPoints.addBalance(target, amount)
43:         val total = BreachPoints.balance(target)
44:         target.message("You have received ${amount.format()} Breach Points. You now have ${total.format()}.")
45:         player.message("${target.username} now has ${total.format()} Breach Points.")
54: /** Owner 2026-09-29: "geef me unlimited deadmanpoints breachpoints loyalty points en donator" - every currency to its maximum. */
63:     PointBalance.set(target.attr, BreachPoints.BALANCE, PointBalance.MAX.toLong())
64:     target.message("Donator, Deadman, Loyalty and Breach Points set to ${Int.MAX_VALUE.format()}.")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/travel/SpiritTrees.kt
SHA256 4030824bf201e6f8323bc1e333c71ccc52687f2b6e147fa989125340d1e115f4 | 123 lines
### dmm-items
19:  * fairies (trinket_of_fairies.plugin.kts). Names and tiles decoded from this 667 cache: `ConfigDefProbeTool enum 2535` (menu text) and

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/travel/ToolLeprechauns.kt
SHA256 4ceca454415265c49f8f21aa095c58c0649ac4761dfbee38e39961a0a17dbc40 | 38 lines
### dmm-items
7: /** Trinket of fairies "Tool-leprechauns" destinations (owner decision 2026-09-30, ADAPTED): see trinket_of_fairies.plugin.kts. */

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/travel/trinket_of_fairies.plugin.kts
SHA256 b3ca72378cc64ff2356cba39afd8db0bdcadcb413099b8cde7092e9591358241 | 86 lines
### dmm-items
37: on_item_option(Items.TRINKET_OF_FAIRIES, "fairy-ring") {
42: on_item_option(Items.TRINKET_OF_FAIRIES, "spirit-tree") {
47: on_item_option(Items.TRINKET_OF_FAIRIES, "last-destination") {
57: on_item_option(Items.TRINKET_OF_FAIRIES, "tool-leprechauns") {

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/newplayer/NewPlayerConfig.kt
SHA256 6a52b5e94225cc1970ce654a12151970f6461f5775ac88537675edf97559a97c | 127 lines
### starter
26:     val starterChestItem: Int,
27:     val starterChestContents: List<Pair<Int, Int>>,
65:                 starterChestItem = 23881,
67:                 starterChestContents =
69:                         Items.COINS_995 to gg.rsmod.plugins.content.starter.NewPlayerStart.STARTER_CASH,
122:                 starterChestItem = chest.num("item").toInt(),
123:                 starterChestContents = contents,

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/foundation/TutorIntro.kt
SHA256 67d67b5d97b52d8e055d8f6db87f0b1a5535b41f246243243238dd3add1afa73 | 378 lines
### starter
373:         if (firstTime) gg.rsmod.plugins.content.starter.NewPlayerStart.grantStarterPack(player)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/DropSources.kt
SHA256 3cc22bb35444013240d13b997cc5354c5ffab74a6f7ff7356f12fd594c5408f4 | 766 lines
### dmm-items
466:         lines += Line("chest", Items.TRINKET_OF_FAIRIES, "Chest reward", "1", chestY, chestN, Tag.TABLE, chestLines(world, chestY, chestN, emptySet(), withPets = false), sub = "1 item from the Deadman's Chest")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/KillCounts.kt
SHA256 10f4a8a4801dde9ded52252ad8790893fe3347df5ee86e8dd67aab8f9eaf6f4b | 59 lines
### starter
17:     val CHESTS = AttributeKey<Int>(persistenceKey = "deadman_chests_opened")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/QuestTabInfo.kt
SHA256 f34d92fa84e45f6cad44040d766942923c106bca10dcbd17ee03876fa6203158 | 323 lines
### breach-points
215:             text = "Monsters you help kill drop loot and give Breach Points for your damage - spend them with the Breach Trader, " +

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/BeastOfBurden.kt
SHA256 73053fce5ea2d43150f30192b015078e1c81bb7548462b8b020125ac865b3256 | 588 lines
### safe-deposit-integration
23:  * Reuses the existing generic [Player.containers]/[ContainerKey]/`register_container_key`
26:  * [Player] - the same pattern [gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY] already
154:         val held = player.containers[follower] ?: return 0
166:         val stored = player.containers[own] ?: return 0
181:         return player.containers[petPackKey(base)]?.rawItems?.count { it != null } ?: 0
189:         val stowed = player.containers.entries.count { (key, held) -> key.name.startsWith(PET_PACK_PREFIX) && held.rawItems.any { it != null } }
272:             val held = player.containers[key] ?: return@forEach
318:     ): ItemContainer = player.containers.getOrPut(key) { ItemContainer(player.world.definitions, key) }
488:      *   ([gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY]), so the cargo survives logout and

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/Pets.kt
SHA256 7607e9a66a8e817897a740921e31fe0a4ef4bd22fcd0fc2856d9a1e0791a9bf3 | 852 lines
### safe-deposit-integration
609:         player.containers.values.forEach { container ->

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningSpecialMoves.kt
SHA256 3ba5cde93916777a030b7615005e4184e9eef8ade349b328f561d6fbaec31f32 | 1786 lines
### safe-deposit-integration
828:                 val bobContainer = player.containers.getOrPut(key) { ItemContainer(player.world.definitions, key) }

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/starter/NewPlayerStart.kt
SHA256 42002f642a1659226784362c89474f33b3ce4f1f3925456386962b2b56608f88 | 47 lines
### starter
10: object NewPlayerStart {
18:     val STARTER_PACK_GIVEN = AttributeKey<Boolean>(persistenceKey = "deadman_starter_pack_given")
22:      * here [NewPlayerConfig.starterChestItem]) once the introduction is over - finished, skipped or interrupted.
25:         if (player.attr[STARTER_PACK_GIVEN] == true) return
26:         val pack = NewPlayerConfig.current.starterChestItem
28:         player.attr[STARTER_PACK_GIVEN] = true
31:     /** "Open": the pack becomes its contents ([NewPlayerConfig.starterChestContents]), all or nothing. */
34:         if (!player.inventory.contains(config.starterChestItem)) return
36:             config.starterChestContents.sumOf { (item, amount) ->
44:         if (!player.inventory.remove(config.starterChestItem, assureFullRemoval = true).hasSucceeded()) return
45:         config.starterChestContents.forEach { (item, amount) -> player.inventory.add(item, amount) }

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/starter/PvpRespawnChest.kt
SHA256 81220f216044f35083b75083b8fd404d4f353346e5d7b3b9ace5b925627ea20f | 80 lines
### starter
12:  * new-player pack (that one stays 1m coins + 100 house tablets + the skull, [NewPlayerStart]).
22: object PvpRespawnChest {
24:     const val ITEM = Items.DEADMAN_CHEST

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/starter/starter_kit.plugin.kts
SHA256 3f2946e1ff040efe374e7388716deee0784105a7accfa60e0cb5bb8ed7928b23 | 100 lines
### starter
18:     // Deadman starter pack, handed out when the introduction is over (NewPlayerStart.grantStarterPack, TutorIntro.finish).
22: on_item_option(item = NewPlayerConfig.current.starterChestItem, option = "open") {
23:     NewPlayerStart.openStarterPack(player)
26: // N517 / N503 D9: the Deadman chest a PvP death victim receives (PvpRespawnChest).
27: on_item_option(item = PvpRespawnChest.ITEM, option = "open") {
28:     PvpRespawnChest.open(player)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/api/ext/HoldingRouteAttributeTests.kt
SHA256 2b7b8a2054c3eacade20e395a65bbcb6d0fe8a89e675b67450d54882790827a5 | 152 lines
### safe-deposit-integration
44:         every { player.containers } returns HashMap<ContainerKey, ItemContainer>()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/activity/pest_control/VoidDeflectorMigrationTests.kt
SHA256 64a391e98fd3fe90c5048eec8abcb69f592316a5218c7bacb15e2a6d5c9909ca | 72 lines
### safe-deposit-integration
34:         every { player.containers } returns hashMapOf(gg.rsmod.game.model.container.key.ContainerKey("bob", 30, gg.rsmod.game.model.container.ContainerStackType.NORMAL) to cargo)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/areas/burthorpe/WarriorsGuildDefendersTests.kt
SHA256 6d1240f55425956d17d02399a78001052ecad6572b86be9dd8d2f79b7c22bf61 | 66 lines
### safe-deposit-integration
7: import gg.rsmod.game.model.container.key.BANK_KEY
53:         every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/areas/deathsoffice/DeathCollectGravestoneTests.kt
SHA256 17e42bfa79fa50eb2ecf2022374f8688a525f63ff9a9068eeaa0aad03e0f9889 | 73 lines
### safe-deposit-integration
58:         every { p.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
59:         every { p.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
61:         every { p.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/areas/wilderness/MageArenaIITests.kt
SHA256 e97dd92a2ff60e412ffe2aee608d87d11bfe8b47e56ada2a2c37b6c0b0f45330 | 211 lines
### safe-deposit-integration
8: import gg.rsmod.game.model.container.key.BANK_KEY
35:         val bank = ItemContainer(defs, BANK_KEY)
44:             every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, BANK_KEY to bank, EQUIPMENT_KEY to equipment)
187:         a.player.containers[key] = cargo

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/inter/bank/BankBehaviourTests.kt
SHA256 b78f38ba529a3e5ae3a8bfd277a316491a2d3f553c9750629ab1e8e728bd603c | 482 lines
### safe-deposit-integration
13: import gg.rsmod.game.model.container.key.BANK_KEY
45:     private fun newPlayer(bank: ItemContainer = ItemContainer(DEFINITIONS, BANK_KEY)): Player {

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/armor/DeadmanGearTests.kt
SHA256 c5af17fc591e936f8894b23b0fe6f9ecca6d2dcd22c4f57c552d4e9787922e80 | 185 lines
### safe-deposit-integration
8: import gg.rsmod.game.model.container.key.BANK_KEY
9: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
11: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
157:         val bank = ItemContainer(DEFINITIONS, BANK_KEY)
158:         val gravestone = ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
159:         val death = ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/osrs/LocatorOrbTests.kt
SHA256 6c17266cf35fbf55fcef5633b30bd0acaa23647870a0643b7d1c0f95c845431c | 314 lines
### safe-deposit-integration
68:             every { player.containers } returns containers

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/DeathExecutorTests.kt
SHA256 59b8205f306e1ea52a36e36f09054e32afcc5a1993c7696378845a29c752a4d1 | 369 lines
### safe-deposit-integration
12: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
14: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
317:         every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
318:         every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/DeathRecoveryPersistenceArchitectureTests.kt
SHA256 abbb3a4427b5de0d1094355db6da031d04b3380bb0bb0b877faca6695d2a57df | 80 lines
### safe-deposit-integration
12: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
13: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
46:         assertEquals("death_recovery", DEATH_RECOVERY_KEY.name, "the save key of Death's Office stays the old one: saved items keep loading")
47:         assertEquals(gg.rsmod.game.model.container.key.DEATH_RECOVERY_CAPACITY, DEATH_RECOVERY_KEY.capacity, "owner 2026-09-26: Death's Office is unlimited")
48:         assertTrue(DEATH_RECOVERY_KEY.capacity >= 4_000)
49:         assertEquals(ContainerStackType.NORMAL, DEATH_RECOVERY_KEY.stackType)
50:         assertEquals("gravestone", GRAVESTONE_KEY.name)
51:         assertEquals(120, GRAVESTONE_KEY.capacity, "OSRS Wiki Grave: a gravestone functions similarly to a bank with 120 slots")
52:         assertEquals(ContainerStackType.NORMAL, GRAVESTONE_KEY.stackType, "unstackables take a slot each (the 28 / 56 rules)")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/DeathsDomainTests.kt
SHA256 a3aa91f7d76564f94de990b22c7e6d319652b8245312e5d7cd0191f73fd9e4a4 | 393 lines
### safe-deposit-integration
15: import gg.rsmod.game.model.container.key.BANK_KEY
16: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
18: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
64:         val office = ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
355:         every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
356:         every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
357:         every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/ItemsKeptOnDeathOwnerGearTests.kt
SHA256 dca58f67fd09c7627348beb0cafce4663829f55c2bb8f6cb2725523a0e24acdd | 98 lines
### safe-deposit-integration
10: import gg.rsmod.game.model.container.key.BANK_KEY
11: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
13: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
57:         every { player.bank } returns ItemContainer(defs, BANK_KEY)
58:         every { player.deathRecovery } returns ItemContainer(defs, DEATH_RECOVERY_KEY)
59:         every { player.gravestone } returns ItemContainer(defs, GRAVESTONE_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/ItemsKeptOnDeathTests.kt
SHA256 869490a2ee6ee6a5c851e1b7b0f3ab5e54f7353de13a1f260a0fce7d38796b95 | 154 lines
### safe-deposit-integration
10: import gg.rsmod.game.model.container.key.BANK_KEY
11: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
13: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
135:         every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
136:         every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
137:         every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/PerduLostPropertyTests.kt
SHA256 9d7af20d4ecd2a62599c669ddb7c4f25294f9e302a403f66d0eb302f66fead5e | 226 lines
### safe-deposit-integration
9: import gg.rsmod.game.model.container.key.BANK_KEY
138:         player.containers[gg.rsmod.plugins.content.skills.summoning.BeastOfBurden.PACK_YAK_KEY] = pack
200:         every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
201:         every { player.gravestone } returns ItemContainer(DEFINITIONS, gg.rsmod.game.model.container.key.GRAVESTONE_KEY)
202:         every { player.deathRecovery } returns ItemContainer(DEFINITIONS, gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY)
204:             INVENTORY_KEY to player.inventory, EQUIPMENT_KEY to player.equipment, BANK_KEY to player.bank,
205:             gg.rsmod.game.model.container.key.GRAVESTONE_KEY to player.gravestone, gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY to player.deathRecovery,
207:         every { player.containers } returns containers

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/PvpDeathBreakablesTests.kt
SHA256 68428fc5a2e87be2c1b718fa47576b6d8ca84f49859100b1f9122337133e91be | 500 lines
### safe-deposit-integration
10: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
11: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
485:         every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
486:         every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/QuiverDeathRulesTests.kt
SHA256 b603de74cf35f04118ef35dd2a0a5d33a8635a8f9a67201a497dafc1c11b6a68 | 124 lines
### safe-deposit-integration
10: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
11: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
107:         every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
108:         every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/UntradeableDeathReportTests.kt
SHA256 f9de4803061a5b715281a1c17bcb1cfc1d4de6a9c2b114825f847d504330b39d | 147 lines
### safe-deposit-integration
10: import gg.rsmod.game.model.container.key.BANK_KEY
11: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
13: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
139:         every { player.bank } returns ItemContainer(defs, BANK_KEY)
140:         every { player.deathRecovery } returns ItemContainer(defs, DEATH_RECOVERY_KEY)
141:         every { player.gravestone } returns ItemContainer(defs, GRAVESTONE_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/exchange/GrandExchangeOsrsButtonsTests.kt
SHA256 df14441b5626d64810a045f02a385767ed17fd74cbcd659c1dded76860629d56 | 439 lines
### safe-deposit-integration
17: import gg.rsmod.game.model.container.key.BANK_KEY
86:         val bank = ItemContainer(DEFINITIONS, BANK_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/prayer/RuinousUnlocksTests.kt
SHA256 777ebd053a0eaf55f9931f1bbcb51866666e2d3b43cb873231380c92f4832ecb | 312 lines
### safe-deposit-integration
62:             listOf(INVENTORY_KEY, BANK_KEY, EQUIPMENT_KEY, GRAVESTONE_KEY, DEATH_RECOVERY_KEY).forEach {
69:             every { player.containers } returns containers
71:             every { player.bank } returns containers.getValue(BANK_KEY)
213:         for (key in listOf(INVENTORY_KEY, BANK_KEY, GRAVESTONE_KEY, DEATH_RECOVERY_KEY)) {

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/DeadmanRouteGateTests.kt
SHA256 0e3b31515575c963e7b841febb0b7d4cce6e3fb3bef1e9fe3cf5c9de50cc4171 | 118 lines
### dmm-items
55:         listOf("mechanics/travel/spirit_tree.plugin.kts", "mechanics/travel/trinket_of_fairies.plugin.kts", "areas/poh/poh_furniture.plugin.kts")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachPerformanceTests.kt
SHA256 44b4a231a13790f9ad70de6d4448cb67357f7d1aa90a25c616864ed5df1388aa | 39 lines
### breach-points
24:         assertTrue(loot.size == BreachLoot.ELIGIBLE && points.size == BreachPoints.EARNERS)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachRewardTests.kt
SHA256 951b995d899088b2533bffbcd598ba803209749cfad1a106ec4aba91bc3cf6f8 | 139 lines
### breach-points
14:  * Owner 2026-09-23 breach audit: loot goes to the FIRST 16 damage dealers (order, not amount), Breach Points to the TOP 100 by
88:         assertEquals(1234, BreachPoints.award(p, 1234))
89:         assertEquals(1234, BreachPoints.balance(p))
90:         p.attr[BreachPoints.EARNED] = BreachPoints.DAMAGE_CAP - 10
91:         assertEquals(10, BreachPoints.award(p, 500))
92:         assertEquals(0, BreachPoints.award(p, 500))
93:         assertEquals(BreachPoints.DAMAGE_CAP, BreachPoints.earned(p))
94:         assertEquals(1244, BreachPoints.balance(p))
100:         p.attr[BreachPoints.EARNED] = BreachPoints.DAMAGE_CAP
101:         BreachPoints.addBalance(p, BreachPoints.EMBLEM_TIER_5_VALUE)
102:         assertEquals(BreachPoints.EMBLEM_TIER_5_VALUE, BreachPoints.balance(p))
103:         assertEquals(BreachPoints.DAMAGE_CAP, BreachPoints.earned(p))

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/DeadmanBreachTests.kt
SHA256 87f46d22b5b1011ef80ade3e836a8a3419b54b5e087f184436476cce912311cc | 278 lines
### dmm-items
167:         val trinkets = BreachLoot.EXCLUDED_TRINKETS
170:         assertTrue(ItemIds.TRINKET_OF_FAIRIES !in trinkets)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/chest/DeadmanChestTests.kt
SHA256 82331c696c88224eb09f5669f5bb261261a6b90fdf3f238f74b1d3cc73634d6b | 348 lines
### dmm-items
140:         assertTrue(DeadmanChestLoot.FIXED_IDS.containsAll(listOf(Items.TRINKET_OF_FAIRIES, Items.ELDER_CHAOS_HOOD, Items.ELDER_CHAOS_TOP, Items.ELDER_CHAOS_ROBE, Items.HEAVY_BALLISTA, Items.VOIDWAKER_BLADE, Items.VOIDWAKER_GEM, Items.VOIDWAKER_HILT)))
290:         assertFalse(Items.TRINKET_OF_FAIRIES in BreachLoot.EXCLUDED_TRINKETS)
291:         assertTrue(Items.TRINKET_OF_AVARICE in BreachLoot.EXCLUDED_TRINKETS && Items.TRINKET_OF_UNDEAD in BreachLoot.EXCLUDED_TRINKETS)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/chest/DeadmanChestWorldTests.kt
SHA256 67c5e89657428d9f2cd0a06febe4147a0cf446d30803d69789d408e990cae953 | 819 lines
### starter
799:             bindScript(world, "gg.rsmod.plugins.content.mechanics.pvp.chest.Deadman_chest_plugin")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/emblem/DeadmanEmblemTests.kt
SHA256 803d67f014c62b0e8252110b7d7105b9525cd73b40bfd88c122f9f7160871d05 | 481 lines
### safe-deposit-integration
10: import gg.rsmod.game.model.container.key.BANK_KEY
11: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
12: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
453:         every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
454:         every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
455:         every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/removed/CorruptRemovedItemsTests.kt
SHA256 4c758fed436e64058a82d44886783ad29c6811a8c1ae081f2766f16c1b74c99a | 166 lines
### safe-deposit-integration
85:         val keys = listOf(INVENTORY_KEY, EQUIPMENT_KEY, BANK_KEY, GRAVESTONE_KEY, DEATH_RECOVERY_KEY,
90:         every { player.bank } returns containers.getValue(BANK_KEY)
91:         every { player.gravestone } returns containers.getValue(GRAVESTONE_KEY)
92:         every { player.deathRecovery } returns containers.getValue(DEATH_RECOVERY_KEY)
93:         every { player.containers } returns HashMap(containers)
128:         every { player.containers } returns hashMapOf(INVENTORY_KEY to empty)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/removed/DungeoneeringRemovedTests.kt
SHA256 e944a1870ecb515b332b9ff32a93a882a3df62341a3488e173755841481fa63e | 261 lines
### safe-deposit-integration
10: import gg.rsmod.game.model.container.key.BANK_KEY
12: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
14: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
221:         val bank = ItemContainer(DEFINITIONS, BANK_KEY)
222:         val gravestone = ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
223:         val death = ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
229:         every { player.containers } returns
233:                 put(BANK_KEY, bank)
234:                 put(GRAVESTONE_KEY, gravestone)
235:                 put(DEATH_RECOVERY_KEY, death)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/removed/LootingBagRemovedTests.kt
SHA256 e5b9aeb1f20401552abebba4923ca5917de404140f0ae15a7c152199d44d7de9 | 148 lines
### safe-deposit-integration
9: import gg.rsmod.game.model.container.key.BANK_KEY
11: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
13: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
108:         val bank = ItemContainer(DEFINITIONS, BANK_KEY)
109:         val gravestone = ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
110:         val death = ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
116:         every { player.containers } returns
120:                 put(BANK_KEY, bank)
121:                 put(GRAVESTONE_KEY, gravestone)
122:                 put(DEATH_RECOVERY_KEY, death)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/removed/RemovedPotionsExchangeTests.kt
SHA256 59103a9b8e89bbb9a1f81b04d35d9169faad549ebf6cf7efd24a3c46c5d631e9 | 63 lines
### dmm-items
29:         val blocked = RemovedPotions.itemIds + BreachLoot.EXCLUDED_TRINKETS
30:         listOf(Items.OVERLOAD_4, Items.EXTREME_ATTACK_3, Items.PRAYER_RENEWAL_4, Items.DIVINE_SUPER_COMBAT_POTION_4, Items.TRINKET_OF_AVARICE).forEach {
55:         (RemovedPotions.itemIds + BreachLoot.EXCLUDED_TRINKETS).forEach { id ->
58:         listOf(Items.SUPER_RESTORE_4, Items.SARADOMIN_BREW_4, Items.SUPER_COMBAT_POTION_4, Items.TRINKET_OF_FAIRIES, Items.ABYSSAL_WHIP).forEach { id ->

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/shops/PointShopBalanceTests.kt
SHA256 fad3002a18ff2ca00f4f53ad9ce777bf452aeee252e3a730b1c169f8eab6d47c | 48 lines
### breach-points
10: import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
19:  * N275 (owner 2026-10-24, bug-20261024-102252: "breach trader laat niet zien hoeveel punten een player heeft"): a points shop shows
25:     fun `the Breach Trader's window title and help line carry the balance, the chat line says it`() {
27:         attr[BreachPoints.BALANCE] = 12_345
32:         val currency = PointCurrency("breach point", "breach points", BreachPoints.BALANCE)
33:         val shop = Shop("Breach Trader", StockType.NORMAL, PurchasePolicy.BUY_NONE, currency, arrayOfNulls(1), arrayOfNulls(1), false)
36:         assertEquals("Breach Trader - Breach Points: <col=ffffff>12,345</col>", texts[20])
37:         assertEquals("You currently have 12,345 breach points.", texts[24])
38:         assertEquals("You have 12,345 Breach Points.", currency.balanceMessage(player))

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/shops/ShopPriceLabelTests.kt
SHA256 537446a289cb30489cdf9c6b1b9e17b6854b5ee1647a9c09ffcf44b4c16ca63c | 147 lines
### breach-points
31:  * currency: coins, tokkul, the GE-priced Quartermaster, requirement shops (Max cape) and point shops (Breach Trader). And the
96:         val currency = PointCurrency("breach point", "breach points", key)
105:         assertEquals(1_495_000, attrs[key], "5,000 points taken - the Breach Trader's slot 0 showed 50")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/store/PointBalanceEdgeTests.kt
SHA256 637240b3ce5b983d0260ea1c742f65cd56f7cfa7a1d822d6cab92ae539a9a1cd | 566 lines
### breach-points
38: import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
191:         target.attr[BreachPoints.BALANCE] = OWNER_EDGE
193:         assertEquals("Breach +1,000,000 at the edge", MAX, target.attr[BreachPoints.BALANCE])
231:             before.attr[BreachPoints.BALANCE] = 100 // healthy: untouched
244:             assertEquals("a healthy Breach balance is untouched", 100, after.attr[BreachPoints.BALANCE])
283:                 listOf(LOYALTY, SLAYER_POINTS, PK_POINTS_ATTR, BreachPoints.BALANCE, PEST_POINTS, gg.rsmod.plugins.content.activity.penguin_hide_and_seek.PenguinPoints)
341:         val keys = StoreCatalogue.Currency.values().map { it.attr } + listOf(BreachPoints.BALANCE, PK_POINTS_ATTR, SLAYER_POINTS)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/quests/QuestStatusTests.kt
SHA256 ff55c7a1b1c2bae51cdef2edd911494b4fa6173f9c574c1606b5526b9f7f8c4a | 324 lines
### safe-deposit-integration
10: import gg.rsmod.game.model.container.key.BANK_KEY
78:             every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/quests/foundation/DesertTreasureIIRouteTests.kt
SHA256 30b6c121668f390a16290dab2cb3de384ad38ba9bc500f92250a8de3370c3c9f | 212 lines
### safe-deposit-integration
16: import gg.rsmod.game.model.container.key.BANK_KEY
44:         val bank = ItemContainer(definitions, BANK_KEY)
62:             every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, BANK_KEY to bank)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/quests/foundation/NewPlayerFoundationTests.kt
SHA256 c0d600648e58210f1fe5bd4048e15eef4f312caf095576851f34874aeab131ab | 672 lines
### safe-deposit-integration
10: import gg.rsmod.game.model.container.key.BANK_KEY
58:         val bank = ItemContainer(DEFINITIONS, BANK_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/quests/tab/QuestTabTests.kt
SHA256 2fe6bec79357f635a21d884f76ed2a49a5571cf2d5e8d6fc1e16974ea71875e6 | 487 lines
### safe-deposit-integration
11: import gg.rsmod.game.model.container.key.BANK_KEY
61:         val bank = ItemContainer(DEFINITIONS, BANK_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/BeastOfBurdenTests.kt
SHA256 7445229bfe57353d796109e44cb4024a7ea60d045d7dfa9895d19a57c44e0205 | 303 lines
### safe-deposit-integration
91:         val container = player.containers.getValue(BeastOfBurden.PACK_YAK_KEY)
100:         check(player.containers.getValue(BeastOfBurden.WAR_TORTOISE_KEY).getItemCount(TEST_STACKABLE) == 3)
106:         assertEquals(true, player.containers.getValue(BeastOfBurden.WAR_TORTOISE_KEY).isEmpty)
156:         assertEquals(null, yakPlayer.containers[BeastOfBurden.WAR_TORTOISE_KEY])
157:         assertEquals(2, yakPlayer.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
168:         assertEquals(5, player.containers.getValue(BeastOfBurden.ABYSSAL_TITAN_KEY).getItemCount(Items.PURE_ESSENCE))
190:         assertEquals(3, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
201:         assertEquals(30, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(Items.LOGS))
212:         assertEquals(0, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
223:         assertEquals(2, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(Items.LOGS))
235:         assertEquals(3, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
249:         assertEquals(5, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
261:         assertEquals(5, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
276:         every { player.containers } returns HashMap()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/BurdenTrinketsTests.kt
SHA256 150d2b2d70b2f1579e6a954d5a109b6e82ac23daec1ea3d15d5051e591f94023 | 212 lines
### dmm-items
36:         private const val TRINKET_OF_FAIRIES = 23822
42:         val fairies = defs.get(ItemDef::class.java, TRINKET_OF_FAIRIES)
59:                 assertNotEquals(gg.rsmod.game.tools.importer.BurdenTrinketTool.TRINKET_OF_FAIRIES_MODEL, mesh, "${t.trinketName} reuses the fairy trinket mesh")
### safe-deposit-integration
6: import gg.rsmod.game.model.container.key.BANK_KEY
203:         val bank = ItemContainer(defs, BANK_KEY)
208:         every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, EQUIPMENT_KEY to equipment, BANK_KEY to bank)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/ClassicPetsTests.kt
SHA256 7243b997208037cb91d610977705b6d91ebe8d74d6a08f45d2dd7e40f74ee459 | 267 lines
### safe-deposit-integration
96:         every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, gg.rsmod.game.model.container.key.BANK_KEY to bank)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/FamiliarLifecycleTests.kt
SHA256 ab90f58fcda70d93ed0269b2aee58bd8d5a3f137d730d0bd47fc6ea57210c632 | 240 lines
### safe-deposit-integration
12: import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
13: import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
146:         assertEquals(0, player.containers.getValue(key).getItemCount(Items.COINS_995))
162:         assertEquals(0, player.containers.getValue(key).getItemCount(Items.COINS_995))
181:         assertEquals(0, player.containers.getValue(key).getItemCount(Items.COINS_995))
198:         assertEquals(1, player.containers.getValue(key).getItemCount(Items.RUNE_PLATEBODY))
217:         every { player.containers } returns HashMap()
219:         every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
220:         every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/FamiliarPointsTests.kt
SHA256 14952d77c72297991993b9c91c700938fc19bd7f6634cd8d3478b123da773158 | 238 lines
### safe-deposit-integration
215:         every { player.containers } returns HashMap()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/FamiliarSessionLifecycleTests.kt
SHA256 e198ba08c5cac157b647568d6e9780f82bdd42a2bbd6734cc0b9b88736735f27 | 204 lines
### safe-deposit-integration
181:         every { player.containers } returns HashMap()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/FollowerRecoveryTextTests.kt
SHA256 d38522e72ea916cf3eb1469b1336aa10ddfcab422c6125869bdec3288a44add9 | 107 lines
### safe-deposit-integration
30:             player.containers[key] = pack
48:             player.containers[BeastOfBurden.PACK_YAK_KEY] = pack
62:         player.containers[key] = pack
97:         every { player.containers } returns HashMap()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/ForagerStorageTests.kt
SHA256 353fbb853253094c3b4bd495dc9794f20a31762e3051707ffd16293ab0b35fa3 | 127 lines
### safe-deposit-integration
110:         every { player.containers } returns HashMap()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/N503PetDeathTests.kt
SHA256 1851602bc06a2eebf8817fb6a03dd28fc9ca571aecc10bfe6509d2b2ed784d33 | 129 lines
### safe-deposit-integration
5: import gg.rsmod.game.model.container.key.BANK_KEY
119:         val bank = ItemContainer(defs, BANK_KEY)
120:         val containers = hashMapOf<ContainerKey, ItemContainer>(INVENTORY_KEY to inventory, EQUIPMENT_KEY to equipment, BANK_KEY to bank)
125:         every { player.containers } returns containers

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningInterfaceTests.kt
SHA256 127dcc7e96de18b73088b65c6ec6136408820e4036d9dc5bf990a76bd8cb4451 | 189 lines
### starter
179:          * surfaced as an unrelated `NewPlayerStartTests` OOM.

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningSpecialMoveTests.kt
SHA256 935177ae2beae6d09976578af97ab54c393ea93d0b127ad47eaed092aa9ec83b | 310 lines
### safe-deposit-integration
11: import gg.rsmod.game.model.container.key.BANK_KEY
280:         every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
281:         every { player.containers } returns HashMap()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningSpecialResourceTests.kt
SHA256 9be871284b120be3442a14f75ee21ce561e21890486c310a8dc001c8278ba1f9 | 561 lines
### safe-deposit-integration
8: import gg.rsmod.game.model.container.key.BANK_KEY
418:                 val container = player.containers.getOrPut(key) { ItemContainer(SummoningTestCache.definitions, key) }
457:         every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
458:         every { player.containers } returns HashMap()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningTestCache.kt
SHA256 218f298895d23095125216114b88858eb9f0e07753953011da8186f328e799fa | 56 lines
### starter
18:  * added. Adding one more tipped it over: `NewPlayerStartTests` failed with

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/UpgradedPetItemTests.kt
SHA256 acae9bcc0d00ab9764f75d90b4dd00bc1e4a650040fd53e268eba4dad6632c8e | 92 lines
### safe-deposit-integration
5: import gg.rsmod.game.model.container.key.BANK_KEY
83:         val bank = ItemContainer(defs, BANK_KEY)
88:         every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, EQUIPMENT_KEY to equipment, BANK_KEY to bank)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/starter/NewPlayerStartTests.kt
SHA256 804a3e24217f0a73ce2aceeb39c52c2010aecea7f748409ede7ba32240cc29a5 | 92 lines
### starter
23: class NewPlayerStartTests {
29:         NewPlayerStart.grantStarterPack(player)
30:         NewPlayerStart.grantStarterPack(player)
31:         assertEquals(1, player.inventory.getItemCount(config.starterChestItem))
34:         NewPlayerStart.openStarterPack(player)
35:         assertEquals(0, player.inventory.getItemCount(config.starterChestItem))
36:         config.starterChestContents.forEach { (item, amount) -> assertEquals(amount, player.inventory.getItemCount(item)) }
44:         NewPlayerStart.grantStarterPack(player)
45:         NewPlayerStart.openStarterPack(player)
46:         assertEquals(NewPlayerStart.STARTER_CASH, player.inventory.getItemCount(Items.COINS_995))
63:         val options = DEFINITIONS.get(ItemDef::class.java, NewPlayerConfig.DEFAULTS.starterChestItem).inventoryMenu
### safe-deposit-integration
9: import gg.rsmod.game.model.container.key.BANK_KEY
74:         every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/starter/PvpRespawnChestTests.kt
SHA256 7fa2c4077909259606541ab87ac136deaadfefa48a12697901e6bb1ccc7efe91 | 62 lines
### starter
14: class PvpRespawnChestTests {
22:         assertEquals(78_000, PvpRespawnChest.COINS)
23:         assertEquals(24623, PvpRespawnChest.ITEM)
24:         assertEquals(60L * 60L * 1000L, PvpRespawnChest.COOLDOWN_MS)
31:         assertTrue(PvpRespawnChest.eligible(p, t))
32:         p.attr[PvpRespawnChest.LAST_GRANTED] = t
33:         assertFalse(PvpRespawnChest.eligible(p, t + 1))
34:         assertFalse(PvpRespawnChest.eligible(p, t + PvpRespawnChest.COOLDOWN_MS - 1))
35:         assertTrue(PvpRespawnChest.eligible(p, t + PvpRespawnChest.COOLDOWN_MS))
37:         assertTrue(PvpRespawnChest.eligible(p, t - 5))
42:         assertEquals("pvp_respawn_chest_at", PvpRespawnChest.LAST_GRANTED.persistenceKey)
51:         assertEquals(1_000_000, NewPlayerStart.STARTER_CASH)

## game/src/main/kotlin/gg/rsmod/game/model/attr/PointBalance.kt
SHA256 7627b8859728308a32424eebcb34661efb4e8ea178b5d78353ce184997fc5a6c | 135 lines
### breach-points
99:             "breach_points",

## game/src/main/kotlin/gg/rsmod/game/model/container/key/ContainerKeys.kt
SHA256 ea68c7078d0ffd0e7aaac7210958027da1bb69b4a02881ede4844c132b6034cb | 33 lines
### safe-deposit-integration
15: val BANK_KEY = ContainerKey("bank", capacity = 800, stackType = ContainerStackType.STACK)
27: val DEATH_RECOVERY_KEY = ContainerKey("death_recovery", capacity = DEATH_RECOVERY_CAPACITY, stackType = ContainerStackType.NORMAL)
33: val GRAVESTONE_KEY = ContainerKey("gravestone", capacity = 120, stackType = ContainerStackType.NORMAL)

## game/src/main/kotlin/gg/rsmod/game/model/entity/Player.kt
SHA256 f6ba887c5a5320b859381d98d004b19a8d3a2f4423d39660cd72d7701e85ab85 | 1491 lines
### safe-deposit-integration
133:     val bank = ItemContainer(world.definitions, BANK_KEY)
138:      * Death's Office: the items Death holds for this player. See [gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY].
140:     val deathRecovery = ItemContainer(world.definitions, DEATH_RECOVERY_KEY)
143:      * The items waiting in this player's gravestone. See [gg.rsmod.game.model.container.key.GRAVESTONE_KEY].
145:     val gravestone = ItemContainer(world.definitions, GRAVESTONE_KEY)
166:             put(BANK_KEY, bank)
168:             put(DEATH_RECOVERY_KEY, deathRecovery)
169:             put(GRAVESTONE_KEY, gravestone)

## game/src/main/kotlin/gg/rsmod/game/model/item/ItemAttribute.kt
SHA256 980d4eb81e23115f4393486cb30b36904cb211682ba4d065546d272c30f49836 | 94 lines
### safe-deposit-integration
76:      * "stays free to reclaim"). Only ever set inside [gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY] and removed again

## game/src/main/kotlin/gg/rsmod/game/plugin/KotlinPlugin.kt
SHA256 e6b7c2a109fbfa10083369df594b7e8d88788696d903258e69bc05b66add76f8 | 1064 lines
### safe-deposit-integration
187:         r.containerKeys.add(key)

## game/src/main/kotlin/gg/rsmod/game/plugin/PluginRepository.kt
SHA256 2fc33f94b7d444772824e954e780717630194e45407589370d49f28449a8cb55 | 2314 lines
### safe-deposit-integration
545:     val containerKeys =
549:             add(BANK_KEY)
551:             add(DEATH_RECOVERY_KEY)
552:             add(GRAVESTONE_KEY)

## game/src/main/kotlin/gg/rsmod/game/service/serializer/json/JsonPlayerSerializer.kt
SHA256 58c6834ca058ba971e4d534c3e5acf07914761f1593e1b62439a57af6c95e626 | 392 lines
### safe-deposit-integration
141:                 val key = world.plugins.containerKeys.firstOrNull { other -> other.name == it.name }
147:                     if (client.containers.containsKey(key)) {
148:                         client.containers[key]
150:                         client.containers[key] = ItemContainer(client.world.definitions, key)
151:                         client.containers[key]

## game/src/main/kotlin/gg/rsmod/game/tools/importer/BurdenTrinketTool.kt
SHA256 406f14909c6a315db79f45765c7d3a5e392f73233f24b42b220ed4e17011e9ca | 427 lines
### dmm-items
15:     const val TRINKET_OF_FAIRIES_MODEL = 63897
43:                         (intArrayOf(TRINKET_OF_FAIRIES_MODEL) + POUCH_MODELS).associateWith { id ->

## game/src/main/kotlin/gg/rsmod/game/tools/importer/DeadmanChestImportTool.kt
SHA256 1b58ac683af033a4f9b0a7dfaf630507e9f78c55838a86f3ff74308ce3e5c2a3 | 232 lines
### starter
88:                     allocated.getOrPut("deadman_chest_$kind:$upstream") { allocate().also { records["deadman_chest_$kind:$upstream"] = it } }
177:                 append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $local\n    status: IMPORTED_BY_DEADMAN_CHEST_TOOL\n    transaction: ${tx.id}\n")

## game/src/main/kotlin/gg/rsmod/game/tools/importer/NpcLookCopyTool.kt
SHA256 79b93cf302090e8621b93e0e078ab3524f031c2799a71a9e351f5b63e2ccb369 | 133 lines
### breach-points
11:  * Owner 2026-10-09 201749: "deadmanstore en breach trader gebruiken de zelfde npc verander 1 naar een ander dus uiterlijk" - both are
12:  * clones of the OSRS Emblem Trader (OsrsNpcImportTool store-npcs / breach-trader). The owner picked the Breach Trader for a fitting

## game/src/main/kotlin/gg/rsmod/game/tools/importer/OsrsItemImportTool.kt
SHA256 032320dd88a37c1261ec77847f2e1b8d90cac66a3f9b9687a76d48eb68fa69b5 | 1794 lines
### breach-points
1155:             // Breach Trader stock (owner 2026-09-23 "doe ... de rest"): the five OSRS halos the Annihilation shop sells that this

## game/src/main/kotlin/gg/rsmod/game/tools/importer/OsrsNpcImportTool.kt
SHA256 9311fcf30e35c798451bf7e157bd96fd6b6f8561ef4f598f64b90c92dd260b3b | 649 lines
### breach-points
195:             // Owner 2026-09-23 (Breach Points shop, own npc): the OSRS Emblem Trader 308 ran the Annihilation points shop.
196:             "breach-trader" to listOf(308 to "Breach Trader"),

## game/src/main/kotlin/gg/rsmod/game/tools/importer/ShopWindowScriptTool.kt
SHA256 8b9ca980ad31496d3beba62b287045ec52323d1cca7c5eee9db0764894e3370d | 265 lines
### breach-points
12:  *   (946 + slot) and 99,000 for any skillcape - never what the server charges (Breach Trader 50 shown / 5,000 charged, Max cape

## game/src/test/kotlin/gg/rsmod/game/tools/importer/ShopWindowScriptToolTests.kt
SHA256 141b8016aaaddd99b3ef7593e3343f852fc23e07c9bebaf645a71ae77ff16017 | 218 lines
### breach-points
142:             assertEquals(5_000, price(0, 24000, 5_000).returned, "$cache: Breach Trader slot 0 costs 5,000")

## data/cfg/commands.yml
SHA256 f5b87cbf6d9ca82e6805a9faf7f97513d2a3d5dabfa8e60667c75187a61ab439 | 1537 lines
### breach-points
865: - name: breachpoints
867:   usage: "::breachpoints"
868:   description: "Show your Breach Points and how many you earned from breach damage."
869:   example: "::breachpoints"

## data/cfg/new_player.yml
SHA256 43abc870ebf40970a4b49ab0bc84d2cae7bfb999cc0abc6601a0fb42fc1cca4e | 52 lines
### starter
44:   # The Deadman Mode starter chest the intro hands out (NewPlayerStart.grantStarterPack). item 23881 = OSRS item 22330 "Deadman
