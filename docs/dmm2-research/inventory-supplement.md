# DMM2 exhaustive lexical inventory supplement

Scope: main and test Kotlin, commands.yml and new_player.yml only. Includes comments; a lexical hit is not a call edge.
No .codegraph directory in this worktree; no graph generated or stale main-worktree graph used.

## game/plugins/src/main/kotlin/gg/rsmod/plugins/api/cfg/Items.kt
SHA256 a6c343d115f72c2b2773bd1364c747628b0db8efeacd63dfbc2cbb64efea8c92

1360 [combat-family]     const val SCYTHE = 1419
1661 [combat-family]     const val AMULET_OF_MAGIC = 1727
1662 [combat-family]     const val AMULET_OF_MAGIC_NOTED = 1728
6026 [items-and-npcs]     const val CRYSTAL_TRINKET = 6653
9224 [combat-family]     const val AMULET_OF_MAGIC_T = 10366
9225 [combat-family]     const val AMULET_OF_MAGIC_T_NOTED = 10367
9581 [combat-family]     const val SCYTHE_10735 = 10735
9584 [combat-family]     const val AMULET_OF_MAGIC_T_10738 = 10738
10400 [combat-family]     const val SCYTHE_SHARPENER = 11786
19308 [combat-family]     const val TWISTED_BOW = 22326
19313 [starter]     const val OCCULT_NECKLACE_OR = 22330
19314 [starter]     const val OCCULT_ORNAMENT_KIT = 22331
19316 [starter]     const val AVERNIC_DEFENDER_HILT = 22333
19318 [starter]     const val AVERNIC_DEFENDER = 22335
19320 [starter]     const val BELLES_FOLLY = 22337
19884 [combat-family]     const val VOLATILE_NIGHTMARE_STAFF = 23084
20276 [items-and-npcs]     const val TRINKET_OF_FAIRIES = 23822
20277 [items-and-npcs]     const val TRINKET_OF_AVARICE = 23823
20278 [items-and-npcs]     const val TRINKET_OF_UNDEAD = 23824
20279 [items-and-npcs]     const val TRINKET_OF_FORTUITY_INACTIVE = 23825

## game/plugins/src/main/kotlin/gg/rsmod/plugins/api/cfg/Npcs.kt
SHA256 9d54036daaa25acded784a0ebd9240f06e86d8b17cfe435d69b48fb4221aad5a

3364 [items-and-npcs]     const val NIGEL = 3681

## game/plugins/src/main/kotlin/gg/rsmod/plugins/api/cfg/Objs.kt
SHA256 9a90bdaf32a0e3363679da93a794b1f3064cab0670c4a85c8e4648869a4d9412

15156 [starter]     const val EVIL_TURNIP_PATCH_28555 = 28555
15158 [starter]     const val SHAKING_BOX_28557 = 28557
15160 [starter]     const val NET_TRAP_28559 = 28559
15370 [items-and-npcs]     const val TRINKET_OF_SEALIFICATION = 29000
18866 [items-and-npcs]     const val TRINKET_CHEST = 39242

## game/plugins/src/main/kotlin/gg/rsmod/plugins/api/cfg/Sfx.kt
SHA256 07595632481f59a45534f9be1248453281b003287ec319c0369b2390f32fea12

1243 [combat-family]     const val ROGUE_SCYTHE_AVOID = 1239
2526 [combat-family]     const val SCYTHE_DOUBLE = 2522
2527 [combat-family]     const val SCYTHE_HIT = 2523
2528 [combat-family]     const val SCYTHE_SLASH = 2524
2529 [combat-family]     const val SCYTHE_STAB = 2525

## game/plugins/src/main/kotlin/gg/rsmod/plugins/api/ext/PlayerExt.kt
SHA256 a1e46c16d1d5b403bed841e7bbe78dbc9c2e3f18de3128bc8b327bda2500c8bb

1512 [deaths-and-persistence] ): Boolean = containers.values.firstOrNull { container -> container.getItemCount(item) >= amount } != null

## game/plugins/src/main/kotlin/gg/rsmod/plugins/api/WeaponType.kt
SHA256 29513c941f3a664728f88c2f3e4bcbe577a9a29419503f3f6caff48a55d2f9e2

31 [combat-family]     SCYTHE(id = 22),

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/activity/pest_control/VoidDeflectorMigration.kt
SHA256 e0aac9bcd2292fb06901dd2ada53e75feb5c51ff4ce74df78666ba01f793dc61

28 [deaths-and-persistence]             addAll(player.containers.values)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/activity/toa/ToaSupplies.kt
SHA256 bcb851c00e1fa995d7e9918ccdd2108ab973eb6971e32b9d7340ac4a011fe30b

234 [items-and-npcs]             // Every container, also a beast of burden's or a burden-trinket pet's store (ultrareview #4: supplies left the raid there).
235 [deaths-and-persistence]             (listOf(player.inventory, player.bank) + player.containers.values).forEach { container ->

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/grandexchange/GeHomeHall.kt
SHA256 d37707870968594dd8329d8272a609ec691606b9f385612d2ee2d76328e54587

15 [currency]  * Kuradal, the Wise Old Man, Pikkupstix), north-west "PvP" (Skully, the Quartermaster, Perdu, the Breach Trader,
41 [currency]     /** The Breach Trader (breach_shop.plugin.kts). */

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/grandexchange/grandexchange_unlocks.plugin.kts
SHA256 d9b660e2ae0a4576bfb63f49d3f3497caf15c97aae39aae6c023bc08fdfab585

32 [currency]  * and staff to Eblis"); a coloured sceptre = sceptre + its quartz. The Breach Trader keeps selling the sceptres for points (owner: both).

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/karamja/stiles.plugin.kts
SHA256 4f8979d77fdfc04715b9fbc4b6a49b629bddb7292a4bd36a00f9787484702c1c

30 [items-and-npcs]                     *"Ahhh, when I were a young'un my name were Nigel but, these days, folks mostly call me Stiles."

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/poh/PohStorageSets.kt
SHA256 3836c6006d42380b22aa3a8c9fe47acee8a8ab7b6dcb5df14ca9d90a0d605306

98 [combat-family]             StorageSet("Scythe", PohStorage.Family.TOY_BOX, intArrayOf(1419)),

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/wilderness/MageArenaII.kt
SHA256 ebc72e72344042355163dc23af9fac8502acdfa73dc4b16cc924c32325d5a7e6

87 [deaths-and-persistence]         assets.symbol?.let { id -> player.containers.values.any { it.contains(id) } } == true
116 [deaths-and-persistence]         assets.remains(god)?.let { id -> player.containers.values.any { it.contains(id) } } == true

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/cmd/conformance/DeadmanSuite.kt
SHA256 6c182b5332cb8f11f2ac71e44e4b452b29990bd339b75763b8ee5b13c5cf177f

267 [starter]             val chestDue = gg.rsmod.plugins.content.starter.PvpRespawnChest.eligible(b)
268 [starter]             val chest = gg.rsmod.plugins.content.starter.PvpRespawnChest.ITEM

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/cmd/conformance/EquipmentSuite.kt
SHA256 39c6e1ccfeeb6b97129ae7b06c6a435bfc76ef2abf92bb52a800bb6d6401e74a

147 [combat-family]             "passive effects (scythe, twisted bow, fang, lance, salve, slayer helm, ...)",

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/cmd/conformance/QuestsSuite.kt
SHA256 0ff3e42b76c6008d4ad00f6f37dd1cfc5d4b9985f154490355fa8703b68c8887

1442 [starter]         val pack = gg.rsmod.plugins.content.newplayer.NewPlayerConfig.current.starterChestItem
1444 [starter]         run.expectTrue(entry, "only the Deadman starter pack was added", itemsAfter == expected, "before $itemsBefore after $itemsAfter", rule = "NewPlayerStart.grantStarterPack after the first introduction")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/cmd/conformance/SystemsSuite.kt
SHA256 ec678f9652a7cf9445807ebca5624f2ae0c88ac6d28b37aa2464ac97598eac3b

56 [items-and-npcs]  * special, no guards in a player-owned house, the Trollheim / Wilderness cliffside, fairy rings and the Trinket of fairies, the
96 [items-and-npcs]         scenario("trinket") { trinket(run, arena) }
579 [items-and-npcs]     // ---- 9 / 10. Fairy rings and the Trinket of fairies -------------------------------------------------------------------
621 [items-and-npcs]     private suspend fun QueueTask.trinket(
625 [items-and-npcs]         // N101 (Deadman's Chest): the trinket moved from the Deadman shop into the chest's exclusive loot.
627 [items-and-npcs]             "trinket", "comes from the Deadman's Chest",
628 [items-and-npcs]             gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestLoot.FIXED.any { it.item == Items.TRINKET_OF_FAIRIES }, "DeadmanChestLoot.FIXED",
631 [items-and-npcs]         a.inventory.add(Items.TRINKET_OF_FAIRIES, 1)
633 [items-and-npcs]         val slot = a.inventory.getItemIndex(Items.TRINKET_OF_FAIRIES, false)
636 [items-and-npcs]         run.expectTrue("trinket", "Fairy-ring opens the real dial (no staff)", a.interfaces.isVisible(FairyRings.DIAL_INTERFACE), "734 visible")
641 [items-and-npcs]         run.expectTrue("trinket", "fairy ring teleport (cks)", a.tile.isWithinRadius(FairyRings.CODES.getValue("cks").tile, 2), "at ${a.tile}")
647 [items-and-npcs]         run.expectTrue("trinket", "Last-destination", a.tile.isWithinRadius(FairyRings.CODES.getValue("cks").tile, 2), "at ${a.tile}")
652 [items-and-npcs]         run.expectTrue("trinket", "Spirit-tree opens the destination menu", a.interfaces.isVisible(treeMenu.INTERFACE), "${treeMenu.INTERFACE} visible")
655 [items-and-npcs]         run.expectTrue("trinket", "Spirit-tree teleport", a.tile.isWithinRadius(gg.rsmod.plugins.content.mechanics.travel.SpiritTrees.STATIONS[0].tile, 3), "at ${a.tile}")
658 [items-and-npcs]         run.expectTrue("trinket", "Tool-leprechauns has destinations", spots.isNotEmpty(), "spots=${spots.size} ${spots.take(4)}")
664 [items-and-npcs]             run.expectTrue("trinket", "Tool-leprechauns teleport (${spots[0].first})", a.tile.isWithinRadius(spots[0].second, 2), "at ${a.tile}, target ${spots[0].second}")
672 [items-and-npcs]         run.expectTrue("trinket", "refused above level 30 Wilderness (lvl ${deep.getWildernessLevel()})", a.tile.isWithinRadius(deep, 1), "at ${a.tile}")
678 [items-and-npcs]         run.expectTrue("trinket", "refused under Tele Block", a.tile.isWithinRadius(arena.transform(4, 3), 1), "at ${a.tile}")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/Animations.kt
SHA256 f81d4dc3f85498471e996f67076a69e6fdc2161f302640998d46493f881647ef

96 [combat-family]     SCYTHE(

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/AttackStyleData.kt
SHA256 2e8802dfa512bcd6f788d076eab15453fe751d6abf241d70dbc93612844adbed

132 [combat-family]     SCYTHE(
133 [combat-family]         arrayOf(WeaponType.SCYTHE),

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt
SHA256 b02a4010bc63b7028e6d1aa711d52ccccfbaec13785fc914655d680e14d85eab

306 [combat-family]                 pawn.hasWeaponType(WeaponType.SCYTHE) -> Anims.ATTACK_HALBERD_SWIPE
373 [combat-family]                 pawn.hasWeaponType(WeaponType.SPEAR, WeaponType.HALBERD, WeaponType.SCYTHE) -> Anims.BLOCK_SPEAR

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MagicCombatFormula.kt
SHA256 a25bf29eb30a916710e61417d6ab8d4b001247721fb1f8974e8ca567a48c9dbe

156 [combat-family]             // N462 Tumeken's shadow: the worn magic damage x3 (x4 in ToA), at most 100 % (PoweredStaves.shadowMultiplier).
157 [combat-family]             val shadow = gg.rsmod.plugins.content.items.osrs.PoweredStaves.shadowMultiplier(pawn, target)
226 [combat-family]         val b = getEquipmentAttackBonus(pawn) * gg.rsmod.plugins.content.items.osrs.PoweredStaves.shadowMultiplier(pawn, target)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/formula/TargetModifiers.kt
SHA256 3c95bc503a90463f808c3ad906ec30867ba0163adc9186b4cb0f2aa0945c663d

127 [combat-family]      * S3, 2026-09-03: Twisted bow (`Items.TWISTED_BOW`, local id 22326 - see
155 [combat-family]     private const val TWISTED_BOW_ACCURACY_CAP_PERCENT = 140.0
156 [combat-family]     private const val TWISTED_BOW_DAMAGE_CAP_PERCENT = 250.0
160 [combat-family]     private const val TWISTED_BOW_MAGIC_INPUT_CAP = 250
162 [combat-family]     fun isWieldingTwistedBow(player: Player): Boolean = player.hasEquipped(EquipmentType.WEAPON, Items.TWISTED_BOW)
167 [combat-family]      * "whichever is higher" rule above, capped at [TWISTED_BOW_MAGIC_INPUT_CAP].
177 [combat-family]         return maxOf(magicLevel, magicAccuracyBonus).coerceAtMost(TWISTED_BOW_MAGIC_INPUT_CAP)
184 [combat-family]         val raw = TWISTED_BOW_ACCURACY_CAP_PERCENT + (3.0 * magic - 10.0) / 100.0 - squaredTerm / 100.0
185 [combat-family]         return raw.coerceIn(0.0, TWISTED_BOW_ACCURACY_CAP_PERCENT)
192 [combat-family]         val raw = TWISTED_BOW_DAMAGE_CAP_PERCENT + (3.0 * magic - 14.0) / 100.0 - squaredTerm / 100.0
193 [combat-family]         return raw.coerceIn(0.0, TWISTED_BOW_DAMAGE_CAP_PERCENT)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/magic/Autocast.kt
SHA256 ae7b2cb9ef84a68819ff5ca135c92768d30669d0ea8b80092457cd4d1429c303

60 [combat-family]             Items.NIGHTMARE_STAFF, Items.ELDRITCH_NIGHTMARE_STAFF, Items.VOLATILE_NIGHTMARE_STAFF, Items.BLUE_MOON_SPEAR,

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/scripts/impl/CorporealBeastCombatScript.kt
SHA256 0d2deb7b6e61bbe88c0dd5efebb356ebc8d915729d8486a9cf9b0bee140c7868

89 [items-and-npcs]         "Osmumten's fang", "Thunder khopesh", "King's barrage",

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/nightmare_staves.plugin.kts
SHA256 7be7fac88a7595b000bfe5d83cd62e4aad2a88715888d34465d69b58f3d8568f

54 [combat-family] SpecialAttacks.registerReach(Items.VOLATILE_NIGHTMARE_STAFF, Items.ELDRITCH_NIGHTMARE_STAFF, range = 10)
57 [combat-family] SpecialAttacks.register(NightmareStaves.SPECIAL_ENERGY, Items.VOLATILE_NIGHTMARE_STAFF) {

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/ranged/weapon/BowType.kt
SHA256 34b67ef00ddc1f6e182681b12cf26abceec523fbf04c101abc0179aea819a647

182 [combat-family]     TWISTED_BOW(
183 [combat-family]         item = Items.TWISTED_BOW,
215 [combat-family]     VENATOR_BOW(item = Items.VENATOR_BOW, ammo = TWISTED_BOW.ammo),
216 [combat-family]     VENATOR_BOW_UNCHARGED(item = Items.VENATOR_BOW_UNCHARGED, ammo = TWISTED_BOW.ammo),
217 [combat-family]     SCORCHING_BOW(item = Items.SCORCHING_BOW, ammo = TWISTED_BOW.ammo),

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt
SHA256 c582098a2249d6c8131c97186574689741b71ba94191b284885ed4d332ddf955

103 [combat-family]                     Items.TWISTED_BOW, in Bows.CRYSTAL_BOWS, in Bows.DARK_BOWS -> 10

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/holiday/scythe_drops.plugin.kts
SHA256 c8d2681c548c29d59ece4f77d30c0601cf5e1bed9ed4b8faa45a1fdaf4534f17

3 [combat-family] import gg.rsmod.game.model.attr.SCYTHE
9 [combat-family] set_ground_item_condition(item = Items.SCYTHE) {
10 [combat-family]     val hasItem = player.hasItem(Items.SCYTHE)
13 [combat-family]         player.message("You already have a scythe, you don't need another one.")
16 [combat-family]         player.attr.put(SCYTHE, true)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/items/armor/DragonChainbodyMigration.kt
SHA256 ac7ccdf3927a100769982dbba411130b6dc2340de1768f38e0362f782db2252f

24 [deaths-and-persistence]             addAll(player.containers.values)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/items/casket.plugin.kts
SHA256 826b81733bf70893cd94fbd0b039fb11915ebaec1ff4994c8fc15181faeb99f4

44 [combat-family]         obj(Items.AMULET_OF_MAGIC, slots = 1)
155 [combat-family]             obj(Items.AMULET_OF_MAGIC_T, slots = 3)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/items/combine/CombinationData.kt
SHA256 dff149cf539f178342023171325db4fbd4eae3cc4fcc61203d503159b78892a7

284 [combat-family]     VOLATILE_NIGHTMARE_STAFF(items = intArrayOf(Items.VOLATILE_ORB, Items.NIGHTMARE_STAFF), resultItem = Items.VOLATILE_NIGHTMARE_STAFF, experience = 0.0),

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/items/osrs/OsrsOrnamentKits.kt
SHA256 b5f68756e7b235b13d150943a01d1358fee3cfa1837dbd11d74b5187e3c6eb9d

79 [combat-family]             Ornament(Items.VOLATILE_NIGHTMARE_STAFF, Items.NIGHTMARE_STAFF, Items.VOLATILE_ORB, pvpConvert = true, attachMessage = ORB_ADD, detachMessage = ORB_REMOVE, confirmAttach = false),

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/items/osrs/OsrsSeq.kt
SHA256 cc3dab7177dbf3196c1aac3348b011d7f76524812c7c6667c4312b7a4d6b5f6b

121 [combat-family]     const val HUMAN_SCYTHE_SWEEP = 15790 // OSRS 440

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/items/osrs/OsrsWeaponLooks.kt
SHA256 1f1b43f6cda32f1257ec461b01ca70bef5b8c16808dc2cb8fc1484bf4ecb7f6c

30 [combat-family]     const val HUMAN_SCYTHE_SWEEP = OsrsSeq.HUMAN_SCYTHE_SWEEP
44 [combat-family]         intArrayOf(Items.NIGHTMARE_STAFF, Items.HARMONISED_NIGHTMARE_STAFF, Items.VOLATILE_NIGHTMARE_STAFF, Items.ELDRITCH_NIGHTMARE_STAFF)
115 [combat-family]         // Noxious halberd: Jab / Swipe / Fend = the OSRS halberd pair HUMAN_SPEAR_SPIKE 428 / HUMAN_SCYTHE_SWEEP 440 - the RuneLite
120 [combat-family]         register(Look(intArrayOf(HUMAN_SPEAR_SPIKE, HUMAN_SCYTHE_SWEEP, HUMAN_SPEAR_SPIKE, HUMAN_SPEAR_SPIKE)), Items.NOXIOUS_HALBERD)
154 [combat-family]         // Bladed staves (Staff of the dead, Toxic staff, Staff of balance): every melee style swings HUMAN_SCYTHE_SWEEP 440 (per-item OSRS
158 [combat-family]             Look(all(HUMAN_SCYTHE_SWEEP), block = OsrsSeq.OSRS_DEFEND_STAFF),
210 [combat-family]         // Spears (Lunge stab / Swipe slash / Pound crush / Block stab): HUMAN_SPEAR_SPIKE 428 "Spear stab", HUMAN_SCYTHE_SWEEP 440
213 [combat-family]             Look(intArrayOf(HUMAN_SPEAR_SPIKE, HUMAN_SCYTHE_SWEEP, OsrsSeq.HUMAN_SPEAR_LUNGE, HUMAN_SPEAR_SPIKE), block = OsrsSeq.OSRS_DEFEND_SPEAR),
244 [combat-family]             Items.TWISTED_BOW, Items.BOW_OF_FAERDHINEN, Items.BOW_OF_FAERDHINEN_INACTIVE, Items.BOW_OF_FAERDHINEN_C, Items.MAGIC_SHORTBOW_I,
289 [combat-family]             // scythe_slash 2524 (same ids/names in the 667 sound table). Its sequences 428 / 440 carry no frame sounds.
290 [combat-family]             Items.NOXIOUS_HALBERD -> if (attackAnimation == HUMAN_SCYTHE_SWEEP) gg.rsmod.plugins.api.cfg.Sfx.SCYTHE_SLASH else gg.rsmod.plugins.api.cfg.Sfx.STAFF_STAB

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/items/osrs/PoweredStaves.kt
SHA256 17054e8b305b4141ef88cfd0724cc87e5caf46e2e72555d338afafb80ff11ea8

40 [combat-family] object PoweredStaves {
123 [combat-family]     fun shadowMultiplier(pawn: Pawn, target: Pawn?): Int {

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/magic/jewelleryenchanting/EnchantmentData.kt
SHA256 46856f0b58c8d8edec63eee15b7b6236212725fea0018a83ad6fa490d5b95198

89 [combat-family]         product = Items.AMULET_OF_MAGIC,

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/magic/TeleportType.kt
SHA256 bc3b7aa097a24d4fb8b2fdd2307fdfe845d4765685e1de5e2412937befbc97a1

169 [items-and-npcs]      * Trinket of fairies (OSRS Wiki, Deadman): the same fairy ring / spirit tree travel, but "The trinket cannot be used beyond level
172 [items-and-npcs]     TRINKET_FAIRY(
178 [items-and-npcs]     TRINKET_SPIRIT_TREE(
200 [items-and-npcs]                 FAIRY, TRINKET_FAIRY -> Sfx.FT_FAIRY_TELEPORT

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/death.plugin.kts
SHA256 a0eb9f4f1e8ebfe1f36c53f597540df6f9c0d92c88b7095f765649fe2d1f3088

56 [deaths-and-persistence]     val abuseKill = playerKiller != null && gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill.abuseKill(playerKiller, victim)
114 [items-and-npcs]                 // the carried pets, burden trinkets and kits, and the pet that is out.
140 [starter]         // (78,000 coins + a Deadman's skull), at most one an hour (PvpRespawnChest).
141 [starter]         gg.rsmod.plugins.content.starter.PvpRespawnChest.grant(victim)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/DeathExecutor.kt
SHA256 c91e69e43a1def257d7a30a5d6f46d768b2aecc08866e23eeebcfe2d599f5603

113 [deaths-and-persistence]             // DeadmanEmblem.onPvpDeath (below) with the kill's ValidPkKill verdict - only a valid,

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/DeathResolver.kt
SHA256 06ce8fc5ae2c2746228f1db70f81d3793b1503af1ee25fd7719c29f80cc57a7b

109 [items-and-npcs]                 // Loot keys and Deadman emblems are never kept (RCV-012 3b, owner 2026-09-25/26); pets, trinkets and kits go to the

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/DeathRules.kt
SHA256 110c51dd74aee0a0921728aba05b41f8d68b0315cfcdf5e58908db456647dcf2

64 [items-and-npcs]      * N503 D13/D14 (owner 2026-11-01, replaces N352 "a pet is always kept" and N423 "a trinket is always kept"): pets, burden
65 [items-and-npcs]      * trinkets and ornament kits are never kept and never the killer's - they go to the victim's gravestone on every death that is
70 [items-and-npcs]             gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.isTrinket(itemId) ||

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/ItemsKeptOnDeath.kt
SHA256 1e85e18ad8437bb8a6659b4d1dd4441ca3bee6d4fb5970dda49c6433ee01e839

166 [items-and-npcs]                 // N503 D13/D14: a pet or a burden trinket is never the killer's - it goes to your gravestone.
167 [items-and-npcs]                 if (PvpDeathBreakables.petOrTrinket(slot.item.id)) {

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/PerduLostProperty.kt
SHA256 87093a65779bcf9d5c753b9dcc2de89188d6a32c2d8883a15137c69dad04529e

125 [items-and-npcs]         gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.recordOwned(player)
155 [items-and-npcs]     /** N423 (design v6: a burden trinket is never lost): every trinket kind the player had once and has nowhere now, free. */
156 [items-and-npcs]     fun lostTrinkets(player: Player): List<Entry> =
157 [items-and-npcs]         gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.lost(player).map { Entry(it.item, 0L) }
174 [items-and-npcs]         } + lostPets(player) + lostTrinkets(player)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/PerduReclaim.kt
SHA256 aa464bc7567dd0e07f00361d370172e1707de015a6ed2f19c656d6987ae18948

128 [items-and-npcs]     /** Talk-to: Perdu hands back the player's lost pets and burden trinkets (free), [PER_PAGE] per page; returns whether he had any. */
134 [items-and-npcs]         // N423: a lost burden trinket comes back free too (never lost) - the words fit what he holds, not only pets.

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/PvpDeathBreakables.kt
SHA256 3e1078ce4d3aff51fb8ab5494bc94437f69ea0e71990796a2e56c6b5d4ef42a7

300 [items-and-npcs]      * N503 D13/D14 (owner 2026-11-01): a pet or a burden trinket lost on a PvP death goes to the victim's gravestone as itself (its
303 [items-and-npcs]     fun petOrTrinket(itemId: Int): Boolean =
304 [items-and-npcs]         gg.rsmod.plugins.content.skills.summoning.Pets.isPetItem(itemId) || gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.isTrinket(itemId)
308 [items-and-npcs]         if (petOrTrinket(item.id)) listOf(Item(item)) else VICTIM_GRAVE[item.id]?.map { Item(it, item.amount) } ?: emptyList()
311 [items-and-npcs]         petOrTrinket(itemId) || itemId in PVP_BASE || itemId in PVP_COINS_FROM_PARTS || itemId == Items.VENATOR_BOW || itemId == Items.TONALZTICS_OF_RALOS ||
513 [items-and-npcs]         // N503 D13/D14: a pet or a burden trinket goes to the victim's gravestone ([victimGraveItems]), nothing for the killer.
514 [items-and-npcs]         if (petOrTrinket(id)) return emptyList()

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/UntradeableDeathProtection.kt
SHA256 30ecd3393b3ff530d01b2114c809eef5ea4c1729bedd1af3b05cc51f6d2ae9a4

147 [items-and-npcs]         // N423: a beast of burden trinket is never lost (design v6: kept on every death).
148 [items-and-npcs]         if (gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.isTrinket(item.id)) return UntradeableOutcomeFate(UntradeableFate.UNCHANGED, 0L)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/equipment/EquipSounds.kt
SHA256 0b62710894a51c5e43b247121df2359ae7774f72bcf585bb07bb211dc997f52e

87 [combat-family]             5, 6, 7, 9, 22 -> 2248 // dagger, sword, 2h, claws, scythe: Void metal weapon "equip_sword"

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/exchange/GrandExchangeInterface.kt
SHA256 386c2cdb1df8033a8b817702c2d94fcb3e764d49db9c9f2fe7ffd5ce4fc2a4e7

282 [items-and-npcs]      * or no-longer-obtainable item (Pernix/Virtus, owner 2026-10-06) nor a removed potion / excluded trinket (N474) - the house quote ([GrandExchangeService.houseQuote]) asks this too.

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/moderation/GearPresets.kt
SHA256 a0ecf80bc65022323fd9fd838b5d00a648e909589d1b4828774f94db5fd43f92

74 [combat-family]                 set(Items.MASORI_MASK_F, Items.MASORI_BODY_F, Items.MASORI_CHAPS_F, Items.TWISTED_BOW, Items.VENATOR_RING) + RANGED_ACCESSORIES +
111 [combat-family]                 set(Items.VOLATILE_NIGHTMARE_STAFF) + BARRAGE_RUNES,

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/ownership/Possessions.kt
SHA256 02742d889dab6d9ab3c8e00fe5763d79108ebb555a85aee86703eb1d48a4325d

62 [items-and-npcs]     /** How many of [id] (noted forms count) [player] has in all their containers together (N423: one trinket per bound pet). */
68 [deaths-and-persistence]         return player.containers.values.sumOf { container ->

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/prayer/RuinousBookDrops.kt
SHA256 89ebe63b8262b19d54e8dfc50ac5a853bee29e80cc6a3d3da339c1c4c08e1325

14 [items-and-npcs] import gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
21 [items-and-npcs]     /** Same existing boss classification as burden trinkets, including breach bosses and Zemouregal; Nex has its own rate. */
28 [items-and-npcs]         return when (BurdenTrinketDrops.sourceOf(world, npcId)) {
29 [items-and-npcs]             BurdenTrinketDrops.Source.BOSS -> Source.BOSS
30 [items-and-npcs]             BurdenTrinketDrops.Source.BREACH_MONSTER -> Source.BREACH_MONSTER

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/breach_points.plugin.kts
SHA256 45dbd603166500f5c546b9e9769f070453052e402f460d7ba229e2cb09e330ae

4 [currency]  * Breach Points (owner 2026-09-23, [BreachPoints]): the balance command and the Archaic emblem (tier 5) trade-in at the Breach
9 [currency] /** The Breach Trader npc (clone batch breach-trader); the same id breach_shop.plugin.kts spawns. */
12 [currency] on_command("breachpoints") {
14 [currency]         "Breach Points: ${"%,d".format(BreachPoints.balance(player))}. Earned from breach damage: " +
15 [currency]             "${"%,d".format(BreachPoints.earned(player))} / ${"%,d".format(BreachPoints.DAMAGE_CAP)}.",
22 [currency]             val value = BreachPoints.EMBLEM_TIER_5_VALUE
23 [currency]             chatNpc("I'll give you ${"%,d".format(value)} Breach Points for that emblem. Deal?", wrap = true)
27 [currency]                 BreachPoints.addBalance(player, value)
28 [currency]                 player.message("You trade in the emblem for ${"%,d".format(value)} Breach Points (total ${"%,d".format(BreachPoints.balance(player))}).")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/breach_shop.plugin.kts
SHA256 abcc534598e195d0c28e4e82809f4ff532e8447a1c69a41934cee5b1dc4edb76

8 [currency]  * The Breach Trader (owner 2026-09-23: "Eigen breach-shop NPC"): a clone of the OSRS Emblem Trader 308 - the npc that ran the
9 [currency]  * Deadman: Annihilation points shop - named "Breach Trader" (OsrsNpcImportTool clone batch breach-trader, tx-20260923-192958),
10 [currency]  * in the Grand Exchange home hall beside the 78 Store keepers. Currency: Breach Points ([BreachPoints], earned in breaches, plus
26 [currency] val BREACH_SHOP = "Breach Trader"
28 [currency] /** Stock: item to price in Breach Points. Potions are the (4) doses. */
65 [currency]     currency = PointCurrency("breach point", "breach points", BreachPoints.BALANCE),
77 [currency]             "Breach Points buy my wares. You earn them by damaging breach monsters, and I'll take any Archaic emblem you bring me.",
80 [currency]         when (options("Show me your rewards.", "How many Breach Points do I have?", "Never mind.")) {
83 [currency]                 chatNpc("You have ${"%,d".format(BreachPoints.balance(player))} Breach Points.", wrap = true)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachContribution.kt
SHA256 4f7ee02ac221c7437299c5dad57a8a7c87e4ba8236b8ab7f7b5c9fd22f2ca424

29 [currency]     /** The [BreachPoints.EARNERS] biggest damage dealers with their damage; ties keep first-damage order. */
31 [currency]         damage.entries.sortedByDescending { it.value }.take(BreachPoints.EARNERS).map { it.key to it.value }

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachIds.kt
SHA256 927837b8bef2ba6e1077648feab6ae551995be0ccdda7c2ae68ef48dc3aa8f9a

18 [items-and-npcs]     const val TRINKET_OF_FAIRIES = Items.TRINKET_OF_FAIRIES
19 [items-and-npcs]     const val TRINKET_OF_AVARICE = Items.TRINKET_OF_AVARICE
20 [items-and-npcs]     const val TRINKET_OF_UNDEAD = Items.TRINKET_OF_UNDEAD
21 [items-and-npcs]     const val TRINKET_OF_FORTUITY_INACTIVE = Items.TRINKET_OF_FORTUITY_INACTIVE

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachLoot.kt
SHA256 d8de5c193f2e772c8a1836d1f7e6b802c3fcb68d3a32c0a14219973e058e4a5b

16 [items-and-npcs]  * Owner exclusions (Deadman product decision): no trinkets at all (see [RARE]). Owner 2026-09-23: the Archaic emblem (tier 5)
17 [currency]  * drops for eligible players with [EMBLEM_DAMAGE]+ damage and trades in for [BreachPoints] ([DeadmanBreach.dropLoot]). [ItemIds] holds the ids of the items imported for this table.
28 [items-and-npcs]     /** "Within the trinkets and weapons table, there is a roughly 1/92 chance to receive a piece of equipment." */
52 [items-and-npcs]      * OSRS rare roll: "the listed x/9576 weights sum to 133" (trinkets 60/20/10/10/1 + weapons 32). The roll keeps its OSRS
53 [items-and-npcs]      * trigger chance of 133/9576 even though the trinkets are gone (below).
58 [items-and-npcs]      * Owner 2026-09-19: "we dont want trinkets in our rsps please" - asked how, the owner chose to drop the trinket slots
73 [items-and-npcs]      * The trinket items imported earlier; they must never drop (owner exclusion 2026-09-19). Owner 2026-10-17 (N101) lifted it for the
74 [items-and-npcs]      * Trinket of fairies only: it is part of the chest list, which breach monsters roll on top of their own table ([roll]).
76 [items-and-npcs]     val EXCLUDED_TRINKETS =
77 [items-and-npcs]         setOf(ItemIds.TRINKET_OF_AVARICE, ItemIds.TRINKET_OF_UNDEAD, ItemIds.TRINKET_OF_FORTUITY_INACTIVE)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachPoints.kt
SHA256 1e9a27aef912ea624b4f13589b0ae7e2d7686185418e40f786c15ccb55e54692

8 [currency]  * Breach Points (owner 2026-09-23: a separate currency, never Deadman Points).
16 [currency] object BreachPoints {
22 [currency]     val BALANCE = AttributeKey<Int>("breach_points")
25 [currency]     val EARNED = AttributeKey<Int>("breach_points_earned")
41 [currency]         val capNote = if (earned(player) >= DAMAGE_CAP) " You have reached the ${"%,d".format(DAMAGE_CAP)} breach point limit." else ""
42 [currency]         player.message("You earn ${"%,d".format(given)} Breach Points (total ${"%,d".format(balance(player))}).$capNote")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachSim.kt
SHA256 75216600d875f94158207d06f66abb63b2723371e55f41b837f909c5c63a2317

323 [currency]                     val pointsBefore = BreachPoints.balance(bot)
334 [currency]                     val points = BreachPoints.balance(bot) - pointsBefore

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/DeadmanBreach.kt
SHA256 66bbde26c912758ceb8b2fe01a9e8215cc65e60e5079f7d2f47f3bdaf9d36b2a

979 [currency]      * and Breach Points for the top [BreachPoints.EARNERS]. The monster is
1033 [items-and-npcs]             // N423 deel 5: the burden trinkets - a breach boss at the boss chances, a breach monster at its own (no bracket boost).
1034 [items-and-npcs]             val trinkets = gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
1035 [items-and-npcs]             trinkets.sourceOf(world, npc.id)?.let { kind -> trinkets.roll(world.random, kind) }?.let { tier ->
1036 [items-and-npcs]                 trinkets.award(player, tier, tile, source, npc.name)
1043 [currency]             if (BreachPoints.award(player, damage) > 0) paid++

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/chest/DeadmanChest.kt
SHA256 e607ecd85936ed245a4821d992707b56f4530884d8861e008425961f79467295

246 [items-and-npcs]         // N423 deel 5: 1 in 100 / 200 / 400 chests also hold a Kalphite / Bull ant / Terrorbird burden trinket - its own roll.
247 [items-and-npcs]         gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.roll(Random(), gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.Source.CHEST)?.let {
248 [items-and-npcs]             gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.award(player, it, player.tile, "chest", "a Deadman's Chest")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/chest/DeadmanChestLoot.kt
SHA256 f423959a356634c16ba56d3b8d5cbb9c68d047fb093ff944a9ee2dae9a1def74

73 [items-and-npcs]             // Trinket of fairies (owner 10-17 overrules the 09-19 "no trinkets" exclusion for this one trinket).
74 [items-and-npcs]             Entry(Items.TRINKET_OF_FAIRIES, 1, 8),

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/chest/DeadmanChestWorld.kt
SHA256 6da08e95c49c0f22218399562e18284defcd50193bb877d568410090aa7675b7

283 [deaths-and-persistence]     /** Account + address key + machine id: alts on one address share one cooldown (dossier gap 1, AC link; same sources as ValidPkKill). */

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/DeadmanTrade.kt
SHA256 3559e9223660d1d15263b022e2c587f034457188aee66f70357d65726686fec4

8 [deaths-and-persistence]  * receiver's risk if the giver kills him ([ValidPkKill.noteGift]). Called once per completed trade from
19 [deaths-and-persistence]         ValidPkKill.noteGift(second.username, first.username, gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.riskValue(world, firstReceived))
20 [deaths-and-persistence]         ValidPkKill.noteGift(first.username, second.username, gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.riskValue(world, secondReceived))

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/emblem/DeadmanEmblem.kt
SHA256 89a80e3f190614a1217148d9936f1cdeb3641097c167709941744799cf1303f5

16 [deaths-and-persistence] import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill
27 [deaths-and-persistence]  * Loop: a PvM kill of an NPC with 150+ HP can give a tier 1 emblem -> every valid PK kill ([ValidPkKill]) while the emblem
225 [deaths-and-persistence]      * This is the single place a PvP death is judged: the one [ValidPkKill.evaluate] of the death also drives the
228 [deaths-and-persistence]      * see the same verdict before [ValidPkKill.record] starts the cooldowns. Returns that result.
230 [deaths-and-persistence]     fun onPvpDeath(world: World, victim: Player, killer: Player?, lostTiers: List<Int>, risk: Long): ValidPkKill.Result {
231 [deaths-and-persistence]         val result = ValidPkKill.evaluate(killer, victim, risk)
253 [deaths-and-persistence]             ValidPkKill.record(killer, victim)
399 [deaths-and-persistence]         private val file get() = gg.rsmod.util.io.SharedDataFiles.of(File(ValidPkKill.dataDir, "deadman_emblem_pending.txt").path)
445 [deaths-and-persistence]     private val file get() = gg.rsmod.util.io.SharedDataFiles.of(File(ValidPkKill.dataDir, "logs/deadman_emblem.log").path)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/KillGrace.kt
SHA256 9b1498f6126e104c88196d4f1fb0d9306beb5d67062fc55765b34ffa7ec04051

59 [deaths-and-persistence]     fun earnsGrace(verdict: ValidPkKill.Verdict, self: Boolean, multiCombat: Boolean): Boolean = !verdict.abuse && !self && !multiCombat
62 [deaths-and-persistence]      * Audit D-06: the one production entry point. Call once per resolved PvP death with the kill's [ValidPkKill] verdict
66 [deaths-and-persistence]     fun grantForKill(killer: Player, victim: Player, verdict: ValidPkKill.Verdict, multiCombat: Boolean): Boolean {

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/Killstreaks.kt
SHA256 60f6181f14a27d482470638b52b9d1e1d7e6f03a2414d7d2eae8124188bcaf41

26 [deaths-and-persistence]  * Audit D-08: a kill only raises the streak and pays PK / Deadman Points when [ValidPkKill] judges it
27 [deaths-and-persistence]  * [ValidPkKill.Verdict.VALID] - the same verdict the emblems use: 1M+ risk, no same address/machine, the persisted
29 [deaths-and-persistence]  * every restart) is gone; the persisted [ValidPkKill] cooldowns replace it.
61 [deaths-and-persistence]      * (from `DeadmanEmblem.onPvpDeath`, which owns the single [ValidPkKill.evaluate] of the death). Safe to drop.
71 [deaths-and-persistence]      * Audit D-08: the killstreak and point reward for one resolved PvP death, with the death's single [ValidPkKill]
77 [deaths-and-persistence]         verdict: ValidPkKill.Verdict,
131 [deaths-and-persistence]     ): Boolean = ValidPkKill.sameAddress(a, b)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/ValidPkKill.kt
SHA256 8de7a40d60126e5ee90d40a74145489a589e300ffbc0e0cd05eee76f95ec80c1

29 [deaths-and-persistence] object ValidPkKill {

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/removed/removed_items.plugin.kts
SHA256 9e9ce0beed6fea2c6a7c6af7eb563a7430287c7452c9037cc4c4a72e52298b86

6 [deaths-and-persistence] register_container_key(gg.rsmod.plugins.content.mechanics.pvp.LootingBag.KEY)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/removed/RemovedItems.kt
SHA256 9ef5bf2bc7545ec641ad7a52611a6650bd00eb0e49c091f6063064e545e2e066

99 [items-and-npcs]      * "copies players already own stay where they are but cannot be drunk, mixed or decanted") and the excluded trinkets (owner
100 [items-and-npcs]      * 2026-09-19 "we dont want trinkets in our rsps please"; only the Trinket of fairies came back, N101). Also the ids a login
105 [items-and-npcs]         gg.rsmod.plugins.content.items.potion.RemovedPotions.itemIds + gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot.EXCLUDED_TRINKETS +
146 [deaths-and-persistence]                 addAll(player.containers.values)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/shops/PointCurrency.kt
SHA256 fbe5ed6127dd0af7d169b6578c34d81db2cb022617ef6967e0bc7197217f6e26

165 [currency]     /** "Breach Points" - the currency as a title. */
171 [currency]      * N275 (owner 2026-10-24, the Breach Trader showed no balance): the balance at the top of the shop window, in the title
172 [currency]      * ("Breach Trader - Breach Points: 1,234"), and on the window's help line. Set when the shop opens and after every purchase.
186 [currency]      * Audit E-10: these used to throw. Selling an item to a point shop (Breach Trader) reached buyFromPlayer from the

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/store/store.plugin.kts
SHA256 58c284dd010c909956cebadba4deafc04bffe4829704b8f35cbf99678abd0602

6 [currency] import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
42 [currency]         BreachPoints.addBalance(target, amount)
43 [currency]         val total = BreachPoints.balance(target)
44 [currency]         target.message("You have received ${amount.format()} Breach Points. You now have ${total.format()}.")
45 [currency]         player.message("${target.username} now has ${total.format()} Breach Points.")
54 [currency] /** Owner 2026-09-29: "geef me unlimited deadmanpoints breachpoints loyalty points en donator" - every currency to its maximum. */
63 [currency]     PointBalance.set(target.attr, BreachPoints.BALANCE, PointBalance.MAX.toLong())
64 [currency]     target.message("Donator, Deadman, Loyalty and Breach Points set to ${Int.MAX_VALUE.format()}.")

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/store/StoreCatalogue.kt
SHA256 a7cd3c739a488e14bb29b86038e1b903d52e0d0cfd015832665f13d829af9a7b

84 [items-and-npcs]     /** N423: the Donator store's Beast of burden trinkets - under Pets (they upgrade a pet). */
85 [items-and-npcs]     const val BURDEN_TRINKETS = "Burden trinkets"
87 [items-and-npcs]     private val PET_CATEGORIES = setOf("Pets", StorePets.CATEGORY, BURDEN_TRINKETS)
371 [items-and-npcs]         // Owner 2026-10-17 (N101): the Trinket of fairies left the shop too (chest and breach loot only).
454 [items-and-npcs]         // N423 (owner 2026-10-29 23:5x): the two big Beast of burden trinkets come from the Donator store for now (the three small
456 [items-and-npcs]         list += itemEntry(d, gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.Tier.TORTOISE.item, TIER_PRICE.getValue(Tier.ELITE), BURDEN_TRINKETS, TradeablePolicy.UNTRADEABLE, Tier.ELITE)
457 [items-and-npcs]         list += itemEntry(d, gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.Tier.PACK_YAK.item, TIER_PRICE.getValue(Tier.PRESTIGE), BURDEN_TRINKETS, TradeablePolicy.UNTRADEABLE, Tier.PRESTIGE)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/store/StoreUi.kt
SHA256 aea86caecf8208a11e680afddda3699fa911967a45d6493b9c59053f51b6e21f

475 [items-and-npcs]         val trinket = gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.tier(entry.purchaseItem)
480 [items-and-npcs]                 // N423: a burden trinket upgrades a pet - it does not follow you itself.
481 [items-and-npcs]                 trinket != null -> tier + "Your pet carries ${trinket.slots} items"
690 [items-and-npcs]         // N423 (owner: "alle 5 in de Collection Log"): a bought burden trinket fills its Collection Log slot too and is remembered
692 [items-and-npcs]         if (gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.isTrinket(entry.purchaseItem)) {
693 [items-and-npcs]             gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.recordOwned(player)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/travel/fairy_rings.plugin.kts
SHA256 cf813912377e22af6c129c8a69ca78fd081ffc59a08c8a03d36095147b9dc647

8 [items-and-npcs]  * [FairyRings.travel]) serves the rings and the Trinket of fairies; the travel log 735 opens in the inventory tab beside the dial, the

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/travel/FairyRings.kt
SHA256 922673554f344b4fd2ca5912568e0c0504f885c681183a75f72e71d5ff7a20fc

99 [items-and-npcs]     /** The code last travelled to (rings and the Trinket of fairies share it, as OSRS "Last-destination"). */
105 [items-and-npcs]     /** How the open dial was reached: at a ring, or through the Trinket of fairies (its own Wilderness limit). */
108 [items-and-npcs]         TRINKET(TeleportType.TRINKET_FAIRY),
130 [items-and-npcs]      * Trinket of fairies): the visited codes were stored but never sent, so the log always came up empty.
202 [items-and-npcs]      * The one entry point of the network (owner 2026-09-30: one FairyRingService for rings and the Trinket of fairies): the real dial
227 [items-and-npcs]      * Wilderness limit: 20 for a ring, 30 for the trinket). A code without a destination lands in Zanaris, as Void's network does.

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/travel/SpiritTrees.kt
SHA256 4030824bf201e6f8323bc1e333c71ccc52687f2b6e147fa989125340d1e115f4

18 [items-and-npcs]  * The spirit tree network (Q-017), one list for every route to it: the trees themselves (spirit_tree.plugin.kts) and the Trinket of
19 [items-and-npcs]  * fairies (trinket_of_fairies.plugin.kts). Names and tiles decoded from this 667 cache: `ConfigDefProbeTool enum 2535` (menu text) and
68 [items-and-npcs]      * Opens the destination menu. At a tree the tree's own station is left out (client script 942 via varp 1469); from the trinket
115 [items-and-npcs]     /** Kept for the callers that run inside a queue (tree options, Talk-to, the trinket). */

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/travel/ToolLeprechauns.kt
SHA256 4ceca454415265c49f8f21aa095c58c0649ac4761dfbee38e39961a0a17dbc40

7 [items-and-npcs] /** Trinket of fairies "Tool-leprechauns" destinations (owner decision 2026-09-30, ADAPTED): see trinket_of_fairies.plugin.kts. */

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/travel/trinket_of_fairies.plugin.kts
SHA256 b3ca72378cc64ff2356cba39afd8db0bdcadcb413099b8cde7092e9591358241

10 [items-and-npcs]  * Trinket of fairies (OSRS item 28564, local 23822 from the OSRS import; cache options Fairy-ring, Spirit-tree, Tool-leprechauns,
11 [items-and-npcs]  * Last-destination). OSRS Wiki: "allows players to remotely access fairy rings, spirit trees, and tool leprechauns ... The trinket cannot
13 [items-and-npcs]  * the same last destination as the rings) and [SpiritTrees] - with the trinket's own teleport types (TRINKET_FAIRY /
14 [items-and-npcs]  * TRINKET_SPIRIT_TREE: Wilderness limit 30), so Tele Block, the Deadman countdown and instance / minigame locks apply exactly as for
15 [items-and-npcs]  * every other teleport. ADAPTED: no Dramen staff is needed with the trinket (the trinket is the fairy magic).
21 [items-and-npcs] val TRINKET_WILDERNESS_LIMIT = 30
24 [items-and-npcs] fun trinketUsable(player: Player): Boolean {
25 [items-and-npcs]     if (player.tile.getWildernessLevel() > TRINKET_WILDERNESS_LIMIT) {
27 [items-and-npcs]         player.message("You can't use this teleport after level $TRINKET_WILDERNESS_LIMIT wilderness.")
37 [items-and-npcs] on_item_option(Items.TRINKET_OF_FAIRIES, "fairy-ring") {
38 [items-and-npcs]     if (!trinketUsable(player)) return@on_item_option
39 [items-and-npcs]     FairyRings.open(player, FairyRings.Source.TRINKET)
42 [items-and-npcs] on_item_option(Items.TRINKET_OF_FAIRIES, "spirit-tree") {
43 [items-and-npcs]     if (!trinketUsable(player)) return@on_item_option
44 [items-and-npcs]     player.queue { SpiritTrees.travel(this, player, TeleportType.TRINKET_SPIRIT_TREE, atTree = false) }
47 [items-and-npcs] on_item_option(Items.TRINKET_OF_FAIRIES, "last-destination") {
48 [items-and-npcs]     if (!trinketUsable(player)) return@on_item_option
54 [items-and-npcs]     FairyRings.travel(player, code, FairyRings.Source.TRINKET)
57 [items-and-npcs] on_item_option(Items.TRINKET_OF_FAIRIES, "tool-leprechauns") {
58 [items-and-npcs]     if (!trinketUsable(player)) return@on_item_option
80 [items-and-npcs]             player.canTeleport(TeleportType.TRINKET_FAIRY) {
81 [items-and-npcs]                 player.teleport(landing, TeleportType.TRINKET_FAIRY)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/newplayer/NewPlayerConfig.kt
SHA256 6a52b5e94225cc1970ce654a12151970f6461f5775ac88537675edf97559a97c

26 [starter]     val starterChestItem: Int,
27 [starter]     val starterChestContents: List<Pair<Int, Int>>,
64 [starter]                 // OSRS item 22330 "Deadman starter pack", imported as 23881 (OsrsItemImportTool batch starter-pack).
65 [starter]                 starterChestItem = 23881,
67 [starter]                 starterChestContents =
69 [starter]                         Items.COINS_995 to gg.rsmod.plugins.content.starter.NewPlayerStart.STARTER_CASH,
122 [starter]                 starterChestItem = chest.num("item").toInt(),
123 [starter]                 starterChestContents = contents,

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/objs/web.plugin.kts
SHA256 41a05e353d43ed0305e7775b1e8ea27c10275174e3e4845feb7ccc532fe50141

24 [combat-family]             WeaponType.SCYTHE,

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/foundation/TutorIntro.kt
SHA256 67d67b5d97b52d8e055d8f6db87f0b1a5535b41f246243243238dd3add1afa73

373 [starter]         if (firstTime) gg.rsmod.plugins.content.starter.NewPlayerStart.grantStarterPack(player)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/CollectionLog.kt
SHA256 21d5b65d0483d78f8213c13bb2b7feeeb005e2590b5ed0579a85935929dc9aff

87 [items-and-npcs]                 val shared = CollectionLogPages.all(player).filter { it.key == "pets" || it.key == "burden_trinkets" }

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/CollectionLogPages.kt
SHA256 2ba6e33c51c3a51cf8aad71b8de587a85f69597f18b60b8d918ddc6428769c10

26 [items-and-npcs]  *  - Other: the five Beast of burden trinkets (N423), every pet.
202 [items-and-npcs]         // ---- Other: N423 (owner 2026-10-29 23:4x: "alle 5 in de Collection Log") the five burden trinkets - three drop, two are sold.
211 [items-and-npcs]         pages += Page("burden_trinkets", Category.OTHER, "Burden Trinkets", emptyList(), gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.Tier.values().map { it.item })
235 [items-and-npcs]         // N423: the burden trinkets have their own page (they drop from every boss, not this one's).
237 [items-and-npcs]             !gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.isTrinket(it.item) }

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/DeadmanTasks.kt
SHA256 d743a78e9fddb31cdf14f1969e3d2b2fd0ae667a63820a52bda7aa42f55a6e2c

22 [deaths-and-persistence]  * Abuse rules (H9): a PvP task counts only a kill the one PvP judge called valid ([gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill],

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/DropSources.kt
SHA256 3cc22bb35444013240d13b997cc5354c5ffab74a6f7ff7356f12fd594c5408f4

240 [items-and-npcs]             Kind.GUARDIAN -> View(listOfNotNull(archaic(world, "top damage")), sorted(world, trinketLines(world, source.modelNpc)))
302 [items-and-npcs]         lines += trinketLines(world, source.tableNpc)
337 [items-and-npcs]      * N423 deel 5: the burden trinkets a kill of [npc] can give - the same chance for everyone (no bracket boost), so Your = Normal
338 [items-and-npcs]      * ([gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops]).
340 [items-and-npcs]     private fun trinketLines(world: World, npc: Int): List<Line> {
341 [items-and-npcs]         val drops = gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
343 [items-and-npcs]         return trinketLines(world, kind)
346 [items-and-npcs]     private fun trinketLines(world: World, kind: gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.Source): List<Line> {
347 [items-and-npcs]         val drops = gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
350 [items-and-npcs]             Line("trinket:${t.item}", t.item, itemName(world, t.item), "1", c, c)
461 [items-and-npcs]         lines += trinketLines(world, npc)
466 [items-and-npcs]         lines += Line("chest", Items.TRINKET_OF_FAIRIES, "Chest reward", "1", chestY, chestN, Tag.TABLE, chestLines(world, chestY, chestN, emptySet(), withPets = false), sub = "1 item from the Deadman's Chest")
523 [items-and-npcs]         lines += trinketLines(world, gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.Source.CHEST)
642 [items-and-npcs]         lines += trinketLines(world, source.modelNpc)

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/QuestTabInfo.kt
SHA256 f34d92fa84e45f6cad44040d766942923c106bca10dcbd17ee03876fa6203158

215 [currency]             text = "Monsters you help kill drop loot and give Breach Points for your damage - spend them with the Breach Trader, " +

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/BeastOfBurden.kt
SHA256 73053fce5ea2d43150f30192b015078e1c81bb7548462b8b020125ac865b3256

23 [deaths-and-persistence]  * Reuses the existing generic [Player.containers]/[ContainerKey]/`register_container_key`
97 [items-and-npcs]      * N423: the pack of a pet with a bound burden trinket, one per trinket size (6/9/12/18/30, the familiar it is named after).
98 [items-and-npcs]      * Only the pet that is out carries anything; a bigger trinket moves the pack along ([moveUpgradedCargo]).
100 [items-and-npcs]     val PET_KEYS: Map<BurdenTrinkets.Tier, ContainerKey> =
101 [items-and-npcs]         BurdenTrinkets.Tier.values().associateWith { ContainerKey("bob_pet_${it.slots}", capacity = it.slots, stackType = ContainerStackType.NORMAL) }
105 [items-and-npcs]     /** N503 D17: the largest pack a pet can have (the Pack yak trinket), so every pet's own pack fits any follower pack. */
110 [items-and-npcs]      * base item (one per pet, not per trinket size like [PET_KEYS]). While the pet is out its items are in the follower pack of its
111 [items-and-npcs]      * trinket size; Pick-up, dismiss, Shoo and a monster death put them in here ([stowPetCargo]), and the pet coming out again
119 [items-and-npcs]     /** The follower pack (its trinket size) and the own pack of the pet with item [petItem]; null for a plain pet. */
125 [items-and-npcs]         val tier = BurdenTrinkets.upgradeOf(player, pet) ?: return null
235 [items-and-npcs]     /** N423: the upgraded pet that is out carries like the beast of burden its trinket is named after. */
238 [items-and-npcs]         val tier = BurdenTrinkets.followerTier(player) ?: return null
242 [items-and-npcs]     /** True while an upgraded pet (a bound burden trinket) is following - it carries like a beast of burden. */
246 [items-and-npcs]      * N423: a bigger trinket bound to the pet that is out - the pack moves into the bigger pack (it always fits). Called by
247 [items-and-npcs]      * [BurdenTrinkets.bind] before the binding changes.
251 [items-and-npcs]         from: BurdenTrinkets.Tier,
252 [items-and-npcs]         to: BurdenTrinkets.Tier,
266 [items-and-npcs]      * A pet pack left behind with no upgraded pet to carry it (the trinket or the pet gone while logged out): moved to
345 [items-and-npcs]         // N423: a burden trinket never goes into a pack (it is what makes the pet carry). N433 (owner 2026-10-30: "je kunt nu pets
347 [items-and-npcs]         if (BurdenTrinkets.isTrinket(item.id) || Pets.isPetItem(item.id)) {

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/burden_trinkets.plugin.kts
SHA256 4597f5cf18ec0b564e393af9aacfbeaef7890d2bf46046cc817887c59f54d8b6

10 [items-and-npcs]  * N423 Beast of burden trinkets ([BurdenTrinkets]): Bind / Check / Unbind on the trinket, and the trinket used on your pet (= Bind).
19 [items-and-npcs]     tier: BurdenTrinkets.Tier,
21 [items-and-npcs]     when (val check = BurdenTrinkets.canBind(player, tier)) {
22 [items-and-npcs]         is BurdenTrinkets.BindCheck.Refused -> player.message(check.message)
23 [items-and-npcs]         is BurdenTrinkets.BindCheck.Ok ->
25 [items-and-npcs]                 itemMessageBox(BurdenTrinkets.bindQuestion(tier, check), tier.item)
26 [items-and-npcs]                 if (options("Yes, bind it to my ${check.name}.", "No, not now.") == 1) BurdenTrinkets.bind(player, tier)
31 [items-and-npcs] BurdenTrinkets.Tier.values().forEach { tier ->
36 [items-and-npcs]         player.message(BurdenTrinkets.checkText(player, tier))
39 [items-and-npcs]         val targets = BurdenTrinkets.unbindTargets(player, tier)
41 [items-and-npcs]             targets.isEmpty() -> player.message("Your ${tier.trinketName} is not bound to a pet.")
42 [items-and-npcs]             targets.size == 1 -> BurdenTrinkets.unbind(player, tier, targets[0])
46 [items-and-npcs]                     val choice = options(*shown.map { BurdenTrinkets.upgradedName(BurdenTrinkets.petName(player, it)) }.toTypedArray(), title = "Unbind it from which pet?")
47 [items-and-npcs]                     shown.getOrNull(choice - 1)?.let { BurdenTrinkets.unbind(player, tier, it) }
52 [items-and-npcs]         player.message("You can't drop your ${tier.trinketName}; it is yours to keep.")
55 [items-and-npcs]     // Used on a pet: the follower is the only pet a trinket binds to.
62 [items-and-npcs]                 bindDialogue(player, BurdenTrinkets.tier(player.getInteractingItem().id) ?: tier)
69 [items-and-npcs]     BurdenTrinkets.recordOwned(player)
74 [items-and-npcs] // N423 deel 5: a boss kill rolls the three smaller trinkets (BurdenTrinketDrops); a breach npc rolls per contributor in DeadmanBreach.
78 [items-and-npcs]     BurdenTrinketDrops.onKill(killer, rewardId, npc.tile)
88 [items-and-npcs]     // N436: the "Upgraded" pet item examines as its pet, plus the purple line naming the trinket the player has bound to it.
95 [items-and-npcs]         val tier = variant?.let { BurdenTrinkets.upgradeOf(player, it.pet) }
96 [items-and-npcs]         tier?.let { "<col=${BurdenTrinkets.PURPLE}>This pet has been upgraded with a ${it.trinketName}.</col>" }
100 [items-and-npcs]         val tier = if (Pets.isUpgradedNpc(npc.id) && owner != null && Pets.current(owner) === npc) BurdenTrinkets.followerTier(owner) else null
101 [items-and-npcs]         tier?.let { "<col=${BurdenTrinkets.PURPLE}>This pet has been upgraded with a ${it.trinketName}.</col>" }

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/BurdenTrinketDrops.kt
SHA256 50e08fa14e23de12f6d5eb25939965ca3ccacfe501dc5ac2d35aedd7564a15d4

12 [items-and-npcs]  * N423 deel 5 (owner 2026-10-29 23:5x, BUGLIJST N423 "DROPKANSEN BESLOTEN"): the three smaller burden trinkets drop; Tortoise and
20 [items-and-npcs]  * at most one trinket per kill (the rarest that hit). Every drop is announced to the world in the Deadman's Chest purple and
23 [items-and-npcs] object BurdenTrinketDrops {
27 [items-and-npcs]     val DROPPED = listOf(BurdenTrinkets.Tier.TERRORBIRD, BurdenTrinkets.Tier.BULL_ANT, BurdenTrinkets.Tier.KALPHITE)
32 [items-and-npcs]         tier: BurdenTrinkets.Tier,
41 [items-and-npcs]             BurdenTrinkets.Tier.KALPHITE -> base
42 [items-and-npcs]             BurdenTrinkets.Tier.BULL_ANT -> base * 2
43 [items-and-npcs]             BurdenTrinkets.Tier.TERRORBIRD -> base * 4
50 [items-and-npcs]         tier: BurdenTrinkets.Tier,
57 [items-and-npcs]     ): BurdenTrinkets.Tier? = DROPPED.firstOrNull { random.nextInt(denominator(source, it)) == 0 }
81 [items-and-npcs]         tier: BurdenTrinkets.Tier,
88 [items-and-npcs]             player.message("<col=ef1020>Your inventory is full - your ${tier.trinketName} lies on the ground.</col>")
90 [items-and-npcs]         BurdenTrinkets.recordOwned(player)
91 [items-and-npcs]         player.playSound(BurdenTrinkets.BIND_SOUND)
93 [items-and-npcs]             it.message("<col=${BurdenTrinkets.PURPLE}>${player.username.replaceFirstChar { c -> c.uppercase() }} has received a ${tier.trinketName} from $from!</col>")
96 [items-and-npcs]         gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.SUMMON) { "burden trinket drop ${tier.trinketName} to ${player.username} from $from" }

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/BurdenTrinkets.kt
SHA256 7746727fe834a0f95c87fdc01947e9d517cb1642a1c4737b1ff0a631555f0fd8

11 [items-and-npcs]  * N423 Beast of burden trinkets (owner 2026-10-29, design v6 - team\dossiers\GOEDGEKEURDE-ONTWERPEN.txt punt 8): bound to a pet,
12 [items-and-npcs]  * the pet carries items like the familiar the trinket is named after and is called "Upgraded <name>".
14 [items-and-npcs]  * - The trinket is never used up: it stays an item (bank, inventory), untradeable, kept on every death, free back from Perdu.
16 [items-and-npcs]  *   the items carry no per-copy data. Several trinkets - also two of the same kind - may each be bound to a different pet; a
17 [items-and-npcs]  *   binding counts only while the player still owns enough trinkets of that kind ([effective]).
18 [items-and-npcs]  * - Bind only to the pet that is following (that is also "use the trinket on your pet"), never in combat; a bigger trinket replaces
21 [items-and-npcs] object BurdenTrinkets {
24 [items-and-npcs]         val trinketName: String,
31 [items-and-npcs]          * N435 (owner 2026-10-30, "oplopend zeldzaam"): the bind / unbind glow in the trinket's own colour - 6 grey, 9 green (the
32 [items-and-npcs]          * original 1517), 12 blue, 18 purple, 30 gold (TrinketGlowTool, cache tx-20261030-104152).
36 [items-and-npcs]         KALPHITE(24232, "Kalphite burden trinket", 6, "Spirit kalphite", "kalphite", 3185),
37 [items-and-npcs]         BULL_ANT(24233, "Bull ant burden trinket", 9, "Bull ant", "bull ant", 1517),
38 [items-and-npcs]         TERRORBIRD(24234, "Terrorbird burden trinket", 12, "Spirit terrorbird", "terrorbird", 3186),
39 [items-and-npcs]         TORTOISE(24235, "Tortoise burden trinket", 18, "War tortoise", "war tortoise", 3187),
40 [items-and-npcs]         PACK_YAK(24236, "Pack yak burden trinket", 30, "Pack yak", "pack yak", 3188),
47 [items-and-npcs]     fun isTrinket(item: Int): Boolean = item in byItem
55 [items-and-npcs]     /** A short glow on the pet when the trinket binds (the Bull ant's green; every tier has its own [Tier.glow]). */
59 [items-and-npcs]      * N434 (owner 2026-10-30: "unbind trinket maakt geen geluid en effect net zoals bind"; choice: the same glow, another sound):
64 [items-and-npcs]     /** pet -> trinket item, "pet=item;pet=item" (pet names hold neither ';' nor '='). */
65 [items-and-npcs]     val BOUND_ATTR = AttributeKey<String>(persistenceKey = "burden_trinkets")
67 [items-and-npcs]     /** Every trinket kind the player ever had (item ids), so Perdu can give a lost one back for free. */
68 [items-and-npcs]     val OWNED_ATTR = AttributeKey<String>(persistenceKey = "burden_trinkets_owned")
74 [items-and-npcs]             val item = entry.substring(at + 1).trim().toIntOrNull()?.takeIf { isTrinket(it) } ?: return@mapNotNull null
96 [items-and-npcs]      * The bindings that count: of each kind, as many as the player owns trinkets of it, in pet-name order (a trinket that is
106 [items-and-npcs]     /** The trinket that upgrades [pet] (a [Pets.Variant.pet] key) right now, or null. */
116 [items-and-npcs]     /** The trinket of the pet that is out (its pack size), or null. */
156 [items-and-npcs]             0 -> "Your ${tier.trinketName} is not bound to a pet."
157 [items-and-npcs]             1 -> "Your ${tier.trinketName} is bound to your ${shownName(player, pets[0])} (${tier.slots} storage)."
158 [items-and-npcs]             else -> "Your ${tier.trinketName}s are bound to your ${joinNames(pets.map { shownName(player, it) })} (${tier.slots} storage each)."
167 [items-and-npcs]         /** May bind; [replaces] is the smaller trinket the pet has now. */
182 [items-and-npcs]         if (Pets.current(player) == null || variant == null) return BindCheck.Refused("A burden trinket can only be bound to the pet that is following you.")
186 [items-and-npcs]         if (now == tier) return BindCheck.Refused("Your ${upgradedName(name)} already carries a ${tier.trinketName}.")
187 [items-and-npcs]         if (now != null && now.slots > tier.slots) return BindCheck.Refused("Your ${upgradedName(name)} already carries a bigger trinket: the ${now.trinketName}.")
191 [items-and-npcs]             return BindCheck.Refused("Your ${tier.trinketName} is already bound to your ${shownName(player, inUse.first())}. Unbind it first.")
200 [items-and-npcs]         "Bind this trinket to your ${ok.name}? It will carry ${tier.slots} items for you - just like a ${tier.familiar}. " +
201 [items-and-npcs]             (if (ok.replaces != null) "It replaces the ${ok.replaces.trinketName}, which stays yours. " else "") +
202 [items-and-npcs]             "You keep the trinket and can unbind it at any time."
217 [items-and-npcs]         // A bigger trinket takes the pack along (the smaller pack's items always fit the bigger one).
229 [items-and-npcs]         ok.replaces?.let { player.message("Your ${it.trinketName} is no longer bound; it stays yours.") }
230 [items-and-npcs]         player.message("You bind the ${tier.trinketName} to your ${ok.name}.")
232 [items-and-npcs]         gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.SUMMON) { "burden trinket ${tier.trinketName} bound to ${ok.pet} for ${player.username}" }
272 [items-and-npcs]         player.message("You unbind the ${tier.trinketName} from your ${upgradedName(name)}. It is a plain $name again; the trinket stays yours.")
273 [items-and-npcs]         gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.SUMMON) { "burden trinket ${tier.trinketName} unbound from $pet for ${player.username}" }
277 [items-and-npcs]             // The plain pet the player now sees glows once more in the trinket's colour as it lets go.
286 [items-and-npcs]     fun ownedOnce(player: Player): Set<Int> = player.attr[OWNED_ATTR].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> isTrinket(id) } }.toSet()
288 [items-and-npcs]     /** Records every trinket kind the player has now (login, Perdu, bind). */
296 [items-and-npcs]     /** The trinket kinds the player had once and has nowhere now - Perdu gives each back for free (design v6: never lost). */

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/familiar.plugin.kts
SHA256 94218bf2089371ff3e5a101d5bb71440952d8b4c659b32a41b1538b6579212bb

346 [deaths-and-persistence] BeastOfBurden.allKeys.forEach { register_container_key(it) }
398 [items-and-npcs]     // N423: an upgraded pet's pack with no upgraded pet out to carry it (the trinket or the pet gone meanwhile) goes to Death's

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/FamiliarInventoryInterface.kt
SHA256 cc4e0c2aaa71e864ad91d1412786eb7b64a17cbc264c5219b7c4e06cee1b8d72

82 [items-and-npcs]         // N423: an upgraded pet (a bound burden trinket) opens the same window as a beast of burden.

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/PetMorphs.kt
SHA256 07f0562ff2a4f46c1ebbc0723ec9af08393e65fbc145f5c3cb54de970dc7cf8b

222 [items-and-npcs]             // N436: the new form keeps the pet's "Upgraded" item while a trinket is bound.

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/Pets.kt
SHA256 7607e9a66a8e817897a740921e31fe0a4ef4bd22fcd0fc2856d9a1e0791a9bf3

48 [items-and-npcs]      * N423: base follower npc -> its "Upgraded <name>" copy (BurdenTrinketTool npcs): same models, head, animations and size, a
49 [items-and-npcs]      * Store op. A pet with a bound burden trinket follows as the copy; everything that knows a pet by npc id sees the copy as
176 [items-and-npcs]     /** The npc [variant] follows [player] as: its Upgraded copy while a burden trinket is bound to the pet. */
180 [items-and-npcs]     ): Int = if (BurdenTrinkets.upgradeOf(player, variant.pet) != null) upgradedOf[variant.npc] ?: variant.npc else variant.npc
206 [items-and-npcs]      * "Upgraded" copy while a burden trinket is bound to the pet, else the plain item.
211 [items-and-npcs]     ): Int = if (BurdenTrinkets.upgradeOf(player, variant.pet) != null) upgradedItemOf[variant.item] ?: variant.item else variant.item
412 [items-and-npcs]         // N436: back into the backpack as the version its binding asks for ("Upgraded <name>" while a trinket is bound).
533 [items-and-npcs]      * N423: after a burden trinket is bound or unbound, the follower turns into its Upgraded copy or back where it stands - the
609 [deaths-and-persistence]         player.containers.values.forEach { container ->
709 [items-and-npcs]      * N407 (owner 2026-10-31: "paarse wereldmelding bij een pet-drop: ja"): everyone online reads it, in the purple of the trinket and
839 [items-and-npcs]      * the gravestone as its item (the "Upgraded" item while a trinket is bound), on every death that is not safe. What it carries

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/pets.plugin.kts
SHA256 4eaa5b340d2f2fdb970686c138689d88e7b6886719d7e20582dd293c1b3f8432

32 [deaths-and-persistence] Pets.variants.mapNotNull { Pets.baseItem(it.pet) }.distinct().forEach { register_container_key(BeastOfBurden.petPackKey(it)) }
38 [items-and-npcs]     // N436: the pet's "Upgraded" item copy (a bound burden trinket) does everything the plain item does.
60 [items-and-npcs]     // N423: the Upgraded copy of the follower (a bound burden trinket) answers every op of its pet.
109 [items-and-npcs]         // (the items that change the pet still change it; a burden trinket has its own binding, burden_trinkets.plugin.kts).
243 [items-and-npcs]     // N423 test sweep: `petheads <from> <count> <ticks> up` shows every form as its Upgraded copy (a Pack yak burden trinket bound
247 [items-and-npcs]     if (upgraded && player.inventory.getItemCount(BurdenTrinkets.Tier.PACK_YAK.item) < 1) player.inventory.add(BurdenTrinkets.Tier.PACK_YAK.item, 1)
251 [items-and-npcs]             if (upgraded) player.attr[BurdenTrinkets.BOUND_ATTR] = BurdenTrinkets.encode(mapOf(variant.pet to BurdenTrinkets.Tier.PACK_YAK.item))

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/starter/NewPlayerStart.kt
SHA256 42002f642a1659226784362c89474f33b3ce4f1f3925456386962b2b56608f88

10 [starter] object NewPlayerStart {
18 [starter]     val STARTER_PACK_GIVEN = AttributeKey<Boolean>(persistenceKey = "deadman_starter_pack_given")
21 [starter]      * Owner 2026-10-08: a new player starts with an empty inventory and gets only the Deadman starter pack (OSRS item 22330,
22 [starter]      * here [NewPlayerConfig.starterChestItem]) once the introduction is over - finished, skipped or interrupted.
25 [starter]         if (player.attr[STARTER_PACK_GIVEN] == true) return
26 [starter]         val pack = NewPlayerConfig.current.starterChestItem
28 [starter]         player.attr[STARTER_PACK_GIVEN] = true
31 [starter]     /** "Open": the pack becomes its contents ([NewPlayerConfig.starterChestContents]), all or nothing. */
34 [starter]         if (!player.inventory.contains(config.starterChestItem)) return
36 [starter]             config.starterChestContents.sumOf { (item, amount) ->
44 [starter]         if (!player.inventory.remove(config.starterChestItem, assureFullRemoval = true).hasSucceeded()) return
45 [starter]         config.starterChestContents.forEach { (item, amount) -> player.inventory.add(item, amount) }

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/starter/PvpRespawnChest.kt
SHA256 81220f216044f35083b75083b8fd404d4f353346e5d7b3b9ace5b925627ea20f

12 [starter]  * new-player pack (that one stays 1m coins + 100 house tablets + the skull, [NewPlayerStart]).
22 [starter] object PvpRespawnChest {

## game/plugins/src/main/kotlin/gg/rsmod/plugins/content/starter/starter_kit.plugin.kts
SHA256 3f2946e1ff040efe374e7388716deee0784105a7accfa60e0cb5bb8ed7928b23

18 [starter]     // Deadman starter pack, handed out when the introduction is over (NewPlayerStart.grantStarterPack, TutorIntro.finish).
22 [starter] on_item_option(item = NewPlayerConfig.current.starterChestItem, option = "open") {
23 [starter]     NewPlayerStart.openStarterPack(player)
26 [starter] // N517 / N503 D9: the Deadman chest a PvP death victim receives (PvpRespawnChest).
27 [starter] on_item_option(item = PvpRespawnChest.ITEM, option = "open") {
28 [starter]     PvpRespawnChest.open(player)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/areas/wilderness/WildernessEncounterRewardTests.kt
SHA256 5e88df24e3057a2c2ed1e46cc2f0eb4ea28d9a3c99185c21be84e5deb36f3c08

5 [items-and-npcs] import gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
13 [items-and-npcs]         mockkObject(BurdenTrinketDrops, RuinousBookDrops)
15 [items-and-npcs]             every { BurdenTrinketDrops.onKill(any(), any(), any(), any()) } just Runs
17 [items-and-npcs]             WildernessBossTestSupport.script("gg.rsmod.plugins.content.skills.summoning.Burden_trinkets_plugin", f.repo, f.world)
24 [items-and-npcs]                 verify(exactly = 0) { BurdenTrinketDrops.onKill(any(), normal, any(), any()) }
30 [items-and-npcs]                 verify(exactly = 1) { BurdenTrinketDrops.onKill(f.player, normal, any(), any()) }
33 [items-and-npcs]         } finally { unmockkObject(BurdenTrinketDrops, RuinousBookDrops) }

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/combat/formula/TwistedBowPassiveTests.kt
SHA256 7c7687796fff8f1f86a575bab38c697b6795b1d0662acf829d3dfb4e3287a9bb

34 [combat-family]  * Coverage for S3_TWISTED_BOW_PASSIVE (2026-09-03 autonomous run - see `RSPS_DECISIONS.md` and
105 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
117 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
129 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
148 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
165 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
184 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
193 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
203 [combat-family]         val attacker = newPlayer(weapon = Items.TWISTED_BOW, rangedLevel = 99, attackRangedBonus = 70)
228 [combat-family]         val noStrengthBonus = newPlayer(weapon = Items.TWISTED_BOW, rangedLevel = 99, rangedStrengthBonus = 0)
229 [combat-family]         val withStrengthBonus = newPlayer(weapon = Items.TWISTED_BOW, rangedLevel = 99, rangedStrengthBonus = 20)
253 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
268 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
290 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)
300 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedAttackRangeTests.kt
SHA256 ec4d65fc4c42bbcac8e6add991f35d9f8117e56e49e6a6683ad280e29bf7732e

54 [combat-family]             Items.TWISTED_BOW to 10,

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/combat/strategy/TwistedBowAmmoIntegrationTests.kt
SHA256 029808cb177821551c20d3cd7595d7c1b4eef7c75ca9ccac90861ee2b613b354

25 [combat-family]  * S3 deliberately left the Twisted Bow (local item [Items.TWISTED_BOW]) unregistered in
48 [combat-family]         val bow = BowType.values.firstOrNull { it.item == Items.TWISTED_BOW }
58 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW, ammo = Items.DRAGON_ARROW)
66 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW, ammo = Items.RUNITE_BOLTS)
82 [combat-family]         val player = newPlayer(weapon = Items.TWISTED_BOW, ammo = null)
131 [combat-family]         val twistedBow = BowType.values.first { it.item == Items.TWISTED_BOW }

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/combine/ItemAttachSoundsTests.kt
SHA256 0424a21e3e59c72afe936dbfc115f4581d73f19bca282d7fe1c4467aa5c43eda

44 [combat-family]         listOf(Items.HARMONISED_NIGHTMARE_STAFF, Items.VOLATILE_NIGHTMARE_STAFF, Items.ELDRITCH_NIGHTMARE_STAFF, Items.ARMADYL_BATTLESTAFF)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/osrs/CrystalEquipmentTests.kt
SHA256 0124315e495c905485c8dbcbdfac8f9e5c518ce998e502ee0645309fbc5bbb60

27 [combat-family]         assertFalse(CrystalEquipment.isCrystalBow(Items.TWISTED_BOW), "no bonus on other ranged weapons")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/osrs/OsrsAmmo2ImportTests.kt
SHA256 f0e3723749e9564cf74646d3cfce9cf23243919e16afb823455035023dca4205

41 [combat-family]         assertTrue(Items.SEEKING_DRAGON_ARROW in RangedAmmo.validAmmo(Items.TWISTED_BOW)!!)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/osrs/OsrsBowsImportTests.kt
SHA256 7bb18732f32bee1ed475f32afe6a6624f07f986fe28fa0a1e5a206df39011508

60 [combat-family]         assertTrue(BowType.values.first { it.item == Items.SCORCHING_BOW }.ammo.contentEquals(BowType.TWISTED_BOW.ammo))

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/osrs/OsrsItemsAuditTests.kt
SHA256 faebe774e0a4843d02b84ecd49c25037c6a13358afbc35c51b3a4a776ca3e483

71 [combat-family]         val tbow = player(Items.TWISTED_BOW, Items.DRAGON_ARROW, arrows)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/osrs/OsrsNightmareImportTests.kt
SHA256 e4806b9b09348aaace95b0c0cc81a97637ff34e8f27a64359833f6ed4ec08786

37 [combat-family]         listOf(Items.HARMONISED_NIGHTMARE_STAFF, Items.VOLATILE_NIGHTMARE_STAFF, Items.ELDRITCH_NIGHTMARE_STAFF).forEach {
41 [combat-family]         mapOf(Items.HARMONISED_NIGHTMARE_STAFF to Items.HARMONISED_ORB, Items.VOLATILE_NIGHTMARE_STAFF to Items.VOLATILE_ORB, Items.ELDRITCH_NIGHTMARE_STAFF to Items.ELDRITCH_ORB)
54 [combat-family]         assertTrue("Items.VOLATILE_NIGHTMARE_STAFF" in specials && "Items.ELDRITCH_NIGHTMARE_STAFF" in specials)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/osrs/OsrsWeaponLooksTests.kt
SHA256 b2acb0a699b1a8d677944fc08195855f1fae4733369f6cb1798e3681963d10e7

58 [combat-family]         assertEquals(OsrsSeq.HUMAN_SCYTHE_SWEEP, OsrsWeaponLooks.attackAnimation(gg.rsmod.plugins.api.cfg.Items.TOXIC_STAFF_OF_THE_DEAD, 2, false, false))

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/osrs/OwnerBuglist20260917cTests.kt
SHA256 32cdf15d2665cf86e245f34697b079bb066a6478214f42529119c7514979cafb

66 [combat-family]         assertEquals(listOf(OsrsSeq.HUMAN_SPEAR_SPIKE, OsrsSeq.HUMAN_SCYTHE_SWEEP, OsrsSeq.HUMAN_SPEAR_SPIKE), (0..2).map { OsrsWeaponLooks.attackAnimation(Items.NOXIOUS_HALBERD, it, againstNpc = true, stabStyle = false) })

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/items/osrs/PoweredStavesTests.kt
SHA256 bc14c6abb25fed4afad0f68cee815c472e9b6c2fd85b3f717af8fd7695cd0ccb

12 [combat-family] class PoweredStavesTests {

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/DeathExecutorTests.kt
SHA256 59b8205f306e1ea52a36e36f09054e32afcc5a1993c7696378845a29c752a4d1

362 [deaths-and-persistence]             // PvP deaths reach DeadmanEmblem/ValidPkKill, which persist state and an audit log: never into the plugins' data dir.
363 [deaths-and-persistence]             gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill.dataDir = java.nio.file.Files.createTempDirectory("death-test").toFile()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/DeathItemAuditTests.kt
SHA256 77fbd285da3e799e570d00985b274b5d6f81cb20679dca576f57f67352994621

82 [items-and-npcs]                         gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.isTrinket(itemId) // N423: never lost (design v6)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/death/ItemsKeptOnDeathOwnerGearTests.kt
SHA256 dca58f67fd09c7627348beb0cafce4663829f55c2bb8f6cb2725523a0e24acdd

64 [starter]         mapOf(0 to 23101, 1 to 23059, 2 to 22330, 3 to 23128, 4 to 23102, 7 to 23103, 9 to 22355, 10 to 22911, 12 to 22946)
79 [starter]         assertTrue(lost.none { it in listOf(22353, 22331) }, "kits never go to the killer: $lost")
80 [starter]         assertTrue(ids(pvp, ItemsKeptOnDeath.Section.GRAVESTONE).containsAll(listOf(22353, 22331)), "the kits wait in the gravestone")
81 [starter]         assertTrue(lost.none { it in listOf(23101, 23102, 23103, 23083, 22330, 22355) }, "converting items never show as themselves: $lost")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachPerformanceTests.kt
SHA256 44b4a231a13790f9ad70de6d4448cb67357f7d1aa90a25c616864ed5df1388aa

24 [currency]         assertTrue(loot.size == BreachLoot.ELIGIBLE && points.size == BreachPoints.EARNERS)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/BreachRewardTests.kt
SHA256 951b995d899088b2533bffbcd598ba803209749cfad1a106ec4aba91bc3cf6f8

14 [currency]  * Owner 2026-09-23 breach audit: loot goes to the FIRST 16 damage dealers (order, not amount), Breach Points to the TOP 100 by
88 [currency]         assertEquals(1234, BreachPoints.award(p, 1234))
89 [currency]         assertEquals(1234, BreachPoints.balance(p))
90 [currency]         p.attr[BreachPoints.EARNED] = BreachPoints.DAMAGE_CAP - 10
91 [currency]         assertEquals(10, BreachPoints.award(p, 500))
92 [currency]         assertEquals(0, BreachPoints.award(p, 500))
93 [currency]         assertEquals(BreachPoints.DAMAGE_CAP, BreachPoints.earned(p))
94 [currency]         assertEquals(1244, BreachPoints.balance(p))
100 [currency]         p.attr[BreachPoints.EARNED] = BreachPoints.DAMAGE_CAP
101 [currency]         BreachPoints.addBalance(p, BreachPoints.EMBLEM_TIER_5_VALUE)
102 [currency]         assertEquals(BreachPoints.EMBLEM_TIER_5_VALUE, BreachPoints.balance(p))
103 [currency]         assertEquals(BreachPoints.DAMAGE_CAP, BreachPoints.earned(p))

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/DeadmanBreachTests.kt
SHA256 87f46d22b5b1011ef80ade3e836a8a3419b54b5e087f184436476cce912311cc

165 [items-and-npcs]         // Owner 2026-09-19: no trinkets - their slots are gone, the six weapons (10/10/3/3/3/3) fill the rare table.
167 [items-and-npcs]         val trinkets = BreachLoot.EXCLUDED_TRINKETS
168 [items-and-npcs]         // Owner 2026-10-17 (N101): the Trinket of fairies is part of the Deadman chest list that breach monsters roll on top; avarice / undead / fortuity stay out.
169 [items-and-npcs]         assertEquals(3, trinkets.size)
170 [items-and-npcs]         assertTrue(ItemIds.TRINKET_OF_FAIRIES !in trinkets)
171 [items-and-npcs]         assertTrue(BreachLoot.RARE.none { it.item in trinkets } && BreachLoot.REGULAR.none { it.item in trinkets } && BreachLoot.MEGA_RARE.none { it in trinkets })
172 [items-and-npcs]         val trinketRandom = Random(11)
173 [items-and-npcs]         repeat(200_000) { assertTrue(BreachLoot.roll(trinketRandom).none { it.item in trinkets }, "a trinket dropped") }

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/chest/DeadmanChestTests.kt
SHA256 82331c696c88224eb09f5669f5bb261261a6b90fdf3f238f74b1d3cc73634d6b

34 [items-and-npcs]  * source of the 17 Ancient Warriors pieces, the Trinket of fairies, Elder chaos, Heavy ballista, the Voidwaker parts and every OSRS
137 [items-and-npcs]     fun `the list holds the 17 Ancient Warriors pieces, the trinket, Elder chaos, Heavy ballista and the Voidwaker parts as the NORMAL ids`() {
140 [items-and-npcs]         assertTrue(DeadmanChestLoot.FIXED_IDS.containsAll(listOf(Items.TRINKET_OF_FAIRIES, Items.ELDER_CHAOS_HOOD, Items.ELDER_CHAOS_TOP, Items.ELDER_CHAOS_ROBE, Items.HEAVY_BALLISTA, Items.VOIDWAKER_BLADE, Items.VOIDWAKER_GEM, Items.VOIDWAKER_HILT)))
288 [items-and-npcs]     fun `defaults - no bracket boost, breach monsters roll the chest list on top and the fairies trinket is no longer excluded`() {
290 [items-and-npcs]         assertFalse(Items.TRINKET_OF_FAIRIES in BreachLoot.EXCLUDED_TRINKETS)
291 [items-and-npcs]         assertTrue(Items.TRINKET_OF_AVARICE in BreachLoot.EXCLUDED_TRINKETS && Items.TRINKET_OF_UNDEAD in BreachLoot.EXCLUDED_TRINKETS)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/DeadmanRouteGateTests.kt
SHA256 0e3b31515575c963e7b841febb0b7d4cce6e3fb3bef1e9fe3cf5c9de50cc4171

51 [items-and-npcs]         // (world trees, POH tree 8355, trinket of fairies) ends in SpiritTrees.select -> canTeleport, never the always-7-s
55 [items-and-npcs]         listOf("mechanics/travel/spirit_tree.plugin.kts", "mechanics/travel/trinket_of_fairies.plugin.kts", "areas/poh/poh_furniture.plugin.kts")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/emblem/DeadmanEmblemTests.kt
SHA256 803d67f014c62b0e8252110b7d7105b9525cd73b40bfd88c122f9f7160871d05

29 [deaths-and-persistence] import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill
30 [deaths-and-persistence] import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill.Verdict
94 [deaths-and-persistence]         assertEquals(Verdict.NO_KILLER, ValidPkKill.judge(false, false, false, false, false, 5_000_000))
95 [deaths-and-persistence]         assertEquals(Verdict.SELF, ValidPkKill.judge(true, true, false, false, false, 5_000_000))
96 [deaths-and-persistence]         assertEquals(Verdict.STAFF, ValidPkKill.judge(true, false, true, true, true, 0))
97 [deaths-and-persistence]         assertEquals(Verdict.SAME_ADDRESS, ValidPkKill.judge(true, false, false, true, true, 0))
98 [deaths-and-persistence]         assertEquals(Verdict.REPEAT_VICTIM, ValidPkKill.judge(true, false, false, false, true, 5_000_000))
99 [deaths-and-persistence]         assertEquals(Verdict.LOW_RISK, ValidPkKill.judge(true, false, false, false, false, 999_999))
100 [deaths-and-persistence]         assertEquals(Verdict.VALID, ValidPkKill.judge(true, false, false, false, false, 1_000_000))
102 [deaths-and-persistence]         assertEquals(ValidPkKill.pairKey("Bob", "alice"), ValidPkKill.pairKey("Alice", "bob"), "the cooldown is per pair, either direction")
104 [deaths-and-persistence]         assertEquals(Verdict.DAILY_CAP, ValidPkKill.judge(true, false, false, false, false, 5_000_000, dailyCapReached = true, fedKill = true))
105 [deaths-and-persistence]         assertEquals(Verdict.LOW_RISK, ValidPkKill.judge(true, false, false, false, false, 999_999, fedKill = true))
106 [deaths-and-persistence]         assertEquals(Verdict.FED_KILL, ValidPkKill.judge(true, false, false, false, false, 1_000_000, fedKill = true))
114 [deaths-and-persistence]         assertTrue(ValidPkKill.sameAddress(a, b), "same machine, different IP")
117 [deaths-and-persistence]         assertTrue(ValidPkKill.sameAddress(c, d), "loopback with the same machine id is the same person")
120 [deaths-and-persistence]         assertFalse(ValidPkKill.sameAddress(e, f), "loopback without a machine id stays the owner's local two-client test")
123 [deaths-and-persistence]         assertTrue(ValidPkKill.sameAddress(g, h), "same remote IP")
124 [deaths-and-persistence]         assertFalse(ValidPkKill.sameAddress(a, g), "a blank machine id never matches")
134 [deaths-and-persistence]         assertEquals(Verdict.REPEAT_VICTIM, ValidPkKill.evaluate(second, victim, 2_000_000).verdict)
141 [deaths-and-persistence]         assertEquals(Verdict.FED_KILL, ValidPkKill.evaluate(killer, victim, 5_000_000).verdict)
143 [deaths-and-persistence]         val later = System.currentTimeMillis() + ValidPkKill.FED_KILL_WINDOW_MS + 1
144 [deaths-and-persistence]         assertEquals(Verdict.FED_KILL, ValidPkKill.evaluate(killer, victim, 5_000_000, later).verdict)
145 [deaths-and-persistence]         assertEquals(Verdict.VALID, ValidPkKill.evaluate(killer, victim, 5_000_000).verdict)
151 [deaths-and-persistence]         ValidPkKill.noteGift("GIFT_KILLER", "gift_victim", 1_500_000)
152 [deaths-and-persistence]         assertEquals(Verdict.LOW_RISK, ValidPkKill.evaluate(killer, victim, 2_000_000).verdict, "2M risked, 1.5M of it from the killer")
153 [deaths-and-persistence]         assertEquals(Verdict.VALID, ValidPkKill.evaluate(killer, victim, 2_600_000).verdict)
154 [deaths-and-persistence]         val tomorrow = System.currentTimeMillis() + ValidPkKill.GIFT_WINDOW_MS + 1
155 [deaths-and-persistence]         assertEquals(0L, ValidPkKill.giftedRecently("gift_killer", "gift_victim", tomorrow))
162 [deaths-and-persistence]         repeat(ValidPkKill.DAILY_VALID_KILL_CAP) { i ->
165 [deaths-and-persistence]             assertEquals(Verdict.VALID, ValidPkKill.evaluate(killer, victim, 2_000_000).verdict, "kill ${i + 1}")
166 [deaths-and-persistence]             ValidPkKill.record(killer, victim)
170 [deaths-and-persistence]         assertEquals(Verdict.DAILY_CAP, ValidPkKill.evaluate(killer, one, 2_000_000).verdict)
171 [deaths-and-persistence]         val state = java.io.File(ValidPkKill.dataDir, "deadman_emblem_pairs.txt").readText()
173 [deaths-and-persistence]         val daily = java.io.File(ValidPkKill.dataDir, "deadman_pk_daily.txt").readText()
475 [deaths-and-persistence]             ValidPkKill.dataDir = Files.createTempDirectory("emblem-test").toFile()

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/pvp/KillGraceTests.kt
SHA256 1c36eeaecd3907b0772b15ee050acec90f31f64de66a65eb89f318c9d08bede7

69 [deaths-and-persistence]         for (verdict in ValidPkKill.Verdict.values().filter { it.abuse }) {
73 [deaths-and-persistence]         for (verdict in ValidPkKill.Verdict.values().filter { !it.valid && !it.abuse }) {
77 [deaths-and-persistence]         assertFalse(KillGrace.grantForKill(killer, victim, ValidPkKill.Verdict.VALID, multiCombat = true), "multi-combat")
78 [deaths-and-persistence]         assertFalse(KillGrace.grantForKill(killer, killer, ValidPkKill.Verdict.VALID, multiCombat = false), "self")
81 [deaths-and-persistence]         assertTrue(KillGrace.grantForKill(killer, victim, ValidPkKill.Verdict.VALID, multiCombat = false))

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/removed/CorruptRemovedItemsTests.kt
SHA256 4c758fed436e64058a82d44886783ad29c6811a8c1ae081f2766f16c1b74c99a

94 [deaths-and-persistence]         containers.values.forEach { c ->
104 [deaths-and-persistence]         containers.values.forEach { c ->

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/removed/RemovedPotionsExchangeTests.kt
SHA256 59103a9b8e89bbb9a1f81b04d35d9169faad549ebf6cf7efd24a3c46c5d631e9

14 [items-and-npcs]  * (2026-09-15 / 09-29), and the excluded trinkets (2026-09-19). The GE refuses them now, noted forms too; held copies are NOT taken
15 [items-and-npcs]  * at login (the owner keeps them where they are), and normal potions / the Trinket of fairies stay on the GE.
28 [items-and-npcs]     fun `the GE refuses every removed potion and excluded trinket, noted forms too`() {
29 [items-and-npcs]         val blocked = RemovedPotions.itemIds + BreachLoot.EXCLUDED_TRINKETS
30 [items-and-npcs]         listOf(Items.OVERLOAD_4, Items.EXTREME_ATTACK_3, Items.PRAYER_RENEWAL_4, Items.DIVINE_SUPER_COMBAT_POTION_4, Items.TRINKET_OF_AVARICE).forEach {
55 [items-and-npcs]         (RemovedPotions.itemIds + BreachLoot.EXCLUDED_TRINKETS).forEach { id ->
58 [items-and-npcs]         listOf(Items.SUPER_RESTORE_4, Items.SARADOMIN_BREW_4, Items.SUPER_COMBAT_POTION_4, Items.TRINKET_OF_FAIRIES, Items.ABYSSAL_WHIP).forEach { id ->

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/shops/PointShopBalanceTests.kt
SHA256 fad3002a18ff2ca00f4f53ad9ce777bf452aeee252e3a730b1c169f8eab6d47c

10 [currency] import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
19 [currency]  * N275 (owner 2026-10-24, bug-20261024-102252: "breach trader laat niet zien hoeveel punten een player heeft"): a points shop shows
25 [currency]     fun `the Breach Trader's window title and help line carry the balance, the chat line says it`() {
27 [currency]         attr[BreachPoints.BALANCE] = 12_345
32 [currency]         val currency = PointCurrency("breach point", "breach points", BreachPoints.BALANCE)
33 [currency]         val shop = Shop("Breach Trader", StockType.NORMAL, PurchasePolicy.BUY_NONE, currency, arrayOfNulls(1), arrayOfNulls(1), false)
36 [currency]         assertEquals("Breach Trader - Breach Points: <col=ffffff>12,345</col>", texts[20])
37 [currency]         assertEquals("You currently have 12,345 breach points.", texts[24])
38 [currency]         assertEquals("You have 12,345 Breach Points.", currency.balanceMessage(player))

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/shops/ShopPriceLabelTests.kt
SHA256 537446a289cb30489cdf9c6b1b9e17b6854b5ee1647a9c09ffcf44b4c16ca63c

31 [currency]  * currency: coins, tokkul, the GE-priced Quartermaster, requirement shops (Max cape) and point shops (Breach Trader). And the
96 [currency]         val currency = PointCurrency("breach point", "breach points", key)
105 [currency]         assertEquals(1_495_000, attrs[key], "5,000 points taken - the Breach Trader's slot 0 showed 50")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/store/PointBalanceEdgeTests.kt
SHA256 637240b3ce5b983d0260ea1c742f65cd56f7cfa7a1d822d6cab92ae539a9a1cd

37 [deaths-and-persistence] import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill
38 [currency] import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
164 [deaths-and-persistence]         Killstreaks.onJudgedKill(killer, victim, ValidPkKill.Verdict.VALID)
191 [currency]         target.attr[BreachPoints.BALANCE] = OWNER_EDGE
193 [currency]         assertEquals("Breach +1,000,000 at the edge", MAX, target.attr[BreachPoints.BALANCE])
231 [currency]             before.attr[BreachPoints.BALANCE] = 100 // healthy: untouched
244 [currency]             assertEquals("a healthy Breach balance is untouched", 100, after.attr[BreachPoints.BALANCE])
256 [deaths-and-persistence]             assertEquals("the repair is idempotent", emptyList<PointBalance.Repair>(), PointBalance.repairWrapped(again.attr))
283 [currency]                 listOf(LOYALTY, SLAYER_POINTS, PK_POINTS_ATTR, BreachPoints.BALANCE, PEST_POINTS, gg.rsmod.plugins.content.activity.penguin_hide_and_seek.PenguinPoints)
295 [deaths-and-persistence]         val repairs = PointBalance.repairWrapped(attrs).associate { it.key to it.new }
307 [deaths-and-persistence]         PointBalance.repairWrapped(attrs)
341 [currency]         val keys = StoreCatalogue.Currency.values().map { it.attr } + listOf(BreachPoints.BALANCE, PK_POINTS_ATTR, SLAYER_POINTS)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/travel/FairyRingTravelLogTests.kt
SHA256 1b9a86c90198dbbda49c4a4c2f6bd07973c5c61b0ff157bff94a484586ca4791

24 [items-and-npcs]  * Owner bug 2026-10-04 (bug-20261004-024316-anudd): "when using the trinket of fairy the travel log does not remember the locations
27 [items-and-npcs]  * JsonPlayerSerializer, and opening the dial (ring, house ring or trinket - one [FairyRings.open]) fills the log.
119 [items-and-npcs]     fun `opening the dial through the trinket shows every visited code in the travel log`() {
124 [items-and-npcs]         assertTrue(FairyRings.open(player, FairyRings.Source.TRINKET))
137 [items-and-npcs]         assertTrue(FairyRings.open(player, FairyRings.Source.TRINKET))

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/BeastOfBurdenTests.kt
SHA256 7445229bfe57353d796109e44cb4024a7ea60d045d7dfa9895d19a57c44e0205

53 [items-and-npcs]         // pet, one per burden trinket size.
54 [items-and-npcs]         assertEquals(31 + BurdenTrinkets.Tier.values().size, BeastOfBurden.allKeys.size)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/BurdenTrinketDropsTests.kt
SHA256 06b99c77c4041ef211de65ec2692630fc79723a20e2b6c58146d44a3818664ba

10 [items-and-npcs]  * N423 deel 5 (owner 2026-10-29 23:5x): the three small burden trinkets drop at the decided chances - boss 1/250 / 1/500 / 1/1,000,
13 [items-and-npcs] class BurdenTrinketDropsTests {
14 [items-and-npcs]     private val t = BurdenTrinkets.Tier.values().associateBy { it.slots }
18 [items-and-npcs]         val drops = BurdenTrinketDrops
19 [items-and-npcs]         assertEquals(listOf(250, 500, 1_000), listOf(6, 9, 12).map { drops.denominator(BurdenTrinketDrops.Source.BOSS, t.getValue(it)) })
20 [items-and-npcs]         assertEquals(listOf(1_000, 2_000, 4_000), listOf(6, 9, 12).map { drops.denominator(BurdenTrinketDrops.Source.BREACH_MONSTER, t.getValue(it)) })
21 [items-and-npcs]         assertEquals(listOf(100, 200, 400), listOf(6, 9, 12).map { drops.denominator(BurdenTrinketDrops.Source.CHEST, t.getValue(it)) })
22 [items-and-npcs]         BurdenTrinketDrops.Source.values().forEach { s ->
29 [items-and-npcs]     fun `a roll gives at most one trinket and over many rolls matches the chances`() {
32 [items-and-npcs]         val counts = HashMap<BurdenTrinkets.Tier, Int>()
33 [items-and-npcs]         repeat(n) { BurdenTrinketDrops.roll(random, BurdenTrinketDrops.Source.CHEST)?.let { counts.merge(it, 1, Int::plus) } }
35 [items-and-npcs]         fun near(tier: BurdenTrinkets.Tier, expected: Double) {
37 [items-and-npcs]             assertTrue(got in expected * 0.85..expected * 1.15, "${tier.trinketName}: $got vs $expected")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/BurdenTrinketsTests.kt
SHA256 150d2b2d70b2f1579e6a954d5a109b6e82ac23daec1ea3d15d5051e591f94023

23 [items-and-npcs]  * N423 Beast of burden trinkets (design v6, GOEDGEKEURDE-ONTWERPEN punt 8): the five items in the production cache and items.yml,
26 [items-and-npcs] class BurdenTrinketsTests {
36 [items-and-npcs]         private const val TRINKET_OF_FAIRIES = 23822
40 [items-and-npcs]     fun `the five trinkets are in the cache - name, Bind Check Unbind, no Drop or Destroy, own mesh, fairy-trinket camera`() {
42 [items-and-npcs]         val fairies = defs.get(ItemDef::class.java, TRINKET_OF_FAIRIES)
43 [items-and-npcs]         assertEquals(listOf(24232, 24233, 24234, 24235, 24236), BurdenTrinkets.Tier.values().map { it.item })
44 [items-and-npcs]         assertEquals(listOf(6, 9, 12, 18, 30), BurdenTrinkets.Tier.values().map { it.slots })
48 [items-and-npcs]             BurdenTrinkets.Tier.values().forEach { t ->
50 [items-and-npcs]                 assertEquals(t.trinketName, def.name)
52 [items-and-npcs]                 assertEquals(listOf("Bind", "Check", "Unbind", null, null), ops, "${t.trinketName} ops")
53 [items-and-npcs]                 assertTrue(def.inventoryMenu[4] != null, "${t.trinketName}: op 5 must be set (empty) - unset is the client's default Drop")
54 [items-and-npcs]                 assertTrue(!def.grandExchange, "${t.trinketName} is on the Grand Exchange")
55 [items-and-npcs]                 // The bytes are exactly the tool's: the fairy trinket's icon camera, the trinket's own mesh.
58 [items-and-npcs]                 assertTrue(java.util.Arrays.equals(gg.rsmod.game.tools.importer.BurdenTrinketTool.itemBytes(t.trinketName, mesh, 10_000 * t.slots), bytes), "${t.trinketName} bytes")
59 [items-and-npcs]                 assertNotEquals(gg.rsmod.game.tools.importer.BurdenTrinketTool.TRINKET_OF_FAIRIES_MODEL, mesh, "${t.trinketName} reuses the fairy trinket mesh")
60 [items-and-npcs]                 assertTrue(meshes.add(mesh), "${t.trinketName} shares a mesh")
61 [items-and-npcs]                 assertTrue(raw.data(7, mesh, 0) != null, "${t.trinketName}: mesh $mesh missing")
66 [items-and-npcs]         assertTrue(fairies.name == "Trinket of fairies")
72 [items-and-npcs]         BurdenTrinkets.Tier.values().forEach { t ->
74 [items-and-npcs]             assertTrue("name: \"${t.trinketName}\"" in block, "${t.item} name")
75 [items-and-npcs]             assertTrue("examine: \"${BurdenTrinkets.examine(t)}\"" in block, "${t.item} examine")
78 [items-and-npcs]         assertEquals("The spirit of a pack yak, sealed in stone.", BurdenTrinkets.examine(BurdenTrinkets.Tier.PACK_YAK))
84 [items-and-npcs]         assertEquals(bound, BurdenTrinkets.decode(BurdenTrinkets.encode(bound)))
85 [items-and-npcs]         assertEquals(mapOf("Beef" to 24236), BurdenTrinkets.decode("Beef=24236;Nothing=995;=24232;broken"))
86 [items-and-npcs]         assertEquals(emptyMap(), BurdenTrinkets.decode(null))
90 [items-and-npcs]     fun `a binding counts only while enough trinkets of its kind are owned`() {
92 [items-and-npcs]         assertEquals(bound, BurdenTrinkets.effective(bound) { 2 })
93 [items-and-npcs]         // One Pack yak trinket left: the first pet by name keeps it.
94 [items-and-npcs]         assertEquals(mapOf("Abyssal orphan" to 24236, "Ikkle Hydra" to 24234), BurdenTrinkets.effective(bound) { if (it == 24236) 1 else 1 })
95 [items-and-npcs]         assertEquals(mapOf("Ikkle Hydra" to 24234), BurdenTrinkets.effective(bound) { if (it == 24236) 0 else 1 })
100 [items-and-npcs]         val ok = BurdenTrinkets.BindCheck.Ok("Beef", "Beef", null)
102 [items-and-npcs]             "Bind this trinket to your Beef? It will carry 30 items for you - just like a Pack yak. You keep the trinket and can unbind it at any time.",
103 [items-and-npcs]             BurdenTrinkets.bindQuestion(BurdenTrinkets.Tier.PACK_YAK, ok),
105 [items-and-npcs]         val bigger = BurdenTrinkets.BindCheck.Ok("Beef", "Beef", BurdenTrinkets.Tier.KALPHITE)
106 [items-and-npcs]         assertTrue("It replaces the Kalphite burden trinket, which stays yours." in BurdenTrinkets.bindQuestion(BurdenTrinkets.Tier.PACK_YAK, bigger))
110 [items-and-npcs]     fun `Check names the upgraded pet, or says the trinket is free`() {
113 [items-and-npcs]         assertEquals("Your Pack yak burden trinket is not bound to a pet.", BurdenTrinkets.checkText(p, BurdenTrinkets.Tier.PACK_YAK))
114 [items-and-npcs]         p.attr[BurdenTrinkets.BOUND_ATTR] = "Beef=24236"
115 [items-and-npcs]         assertEquals("Your Pack yak burden trinket is bound to your Upgraded Beef (30 storage).", BurdenTrinkets.checkText(p, BurdenTrinkets.Tier.PACK_YAK))
116 [items-and-npcs]         // The trinket in the bank counts the same.
119 [items-and-npcs]         assertEquals(BurdenTrinkets.Tier.PACK_YAK, BurdenTrinkets.upgradeOf(p, "Beef"))
121 [items-and-npcs]         BurdenTrinkets.recordOwned(p)
123 [items-and-npcs]         assertEquals(null, BurdenTrinkets.upgradeOf(p, "Beef"))
124 [items-and-npcs]         assertEquals(listOf(BurdenTrinkets.Tier.PACK_YAK), BurdenTrinkets.lost(p))
131 [items-and-npcs]         val check = BurdenTrinkets.canBind(p, BurdenTrinkets.Tier.KALPHITE)
132 [items-and-npcs]         assertEquals(BurdenTrinkets.BindCheck.Refused("A burden trinket can only be bound to the pet that is following you."), check)
136 [items-and-npcs]     fun `N503 D14 - a trinket goes to the gravestone on every death, never to the killer`() {
137 [items-and-npcs]         BurdenTrinkets.Tier.values().forEach { t ->
138 [items-and-npcs]             assertTrue(gg.rsmod.plugins.content.mechanics.death.DeathRules.toGrave(t.item), t.trinketName)
139 [items-and-npcs]             assertTrue(gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables.converts(t.item), t.trinketName)
140 [items-and-npcs]             assertEquals(emptyList(), gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables.killerItems(Item(t.item)), t.trinketName)
172 [items-and-npcs]     /** Deel 4: one pack per trinket size, all saved (registered keys), labelled as the pet's for Perdu. */
174 [items-and-npcs]     fun `the pet packs are registered, sized per trinket and named as the pet's`() {
175 [items-and-npcs]         BurdenTrinkets.Tier.values().forEach { t ->
190 [items-and-npcs]     fun `every trinket glows in its own colour and the glow is in the cache`() {
192 [items-and-npcs]         val glows = BurdenTrinkets.Tier.values().map { it.glow }
194 [items-and-npcs]         assertEquals(BurdenTrinkets.BIND_GRAPHIC, BurdenTrinkets.Tier.BULL_ANT.glow)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/N503PetDeathTests.kt
SHA256 1851602bc06a2eebf8817fb6a03dd28fc9ca571aecc10bfe6509d2b2ed784d33

28 [items-and-npcs]  * N503 deel C (owner 2026-11-01): D13 pets, D14 burden trinkets and ornament kits go to the gravestone on every death; D17 an upgraded
34 [items-and-npcs]         private const val PACK_YAK_TRINKET = 24236
44 [items-and-npcs]     fun `D13 D14 - pets, trinkets and kits never take a kept place, even as the most valuable items`() {
47 [items-and-npcs]         listOf(pet, PACK_YAK_TRINKET, kit).forEach { assertTrue(DeathRules.toGrave(it), "item $it goes to the gravestone") }
50 [items-and-npcs]             it[1] = Item(PACK_YAK_TRINKET)
63 [items-and-npcs]         assertEquals(setOf(pet, PACK_YAK_TRINKET, kit), result.lost.map { it.item.id }.toSet())
67 [items-and-npcs]     fun `D13 D14 - on a PvP death a pet or trinket is nothing for the killer and goes to the victim's gravestone as itself`() {
69 [items-and-npcs]         listOf(pet, Item(PACK_YAK_TRINKET)).forEach { item ->
80 [items-and-npcs]         player.inventory.add(PACK_YAK_TRINKET, 1)
81 [items-and-npcs]         player.attr[BurdenTrinkets.BOUND_ATTR] = BurdenTrinkets.encode(mapOf("Beef" to PACK_YAK_TRINKET))
83 [items-and-npcs]         val tier = BurdenTrinkets.tier(PACK_YAK_TRINKET)!!
111 [deaths-and-persistence]         assertTrue("register_container_key(BeastOfBurden.petPackKey(it))" in plugin, "registered, so saved with the account")

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/PetSoundsTests.kt
SHA256 b06d49ec480e63b4a9a6dfa0e4eb19af1990415839cf76eb1fd0ae32184368c7

119 [items-and-npcs]         // Upgraded copies (burden trinket) appear like their pet.

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningInterfaceTests.kt
SHA256 127dcc7e96de18b73088b65c6ec6136408820e4036d9dc5bf990a76bd8cb4451

179 [starter]          * surfaced as an unrelated `NewPlayerStartTests` OOM.

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningTestCache.kt
SHA256 218f298895d23095125216114b88858eb9f0e07753953011da8186f328e799fa

18 [starter]  * added. Adding one more tipped it over: `NewPlayerStartTests` failed with

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/skills/summoning/UpgradedPetItemTests.kt
SHA256 acae9bcc0d00ab9764f75d90b4dd00bc1e4a650040fd53e268eba4dad6632c8e

21 [items-and-npcs]  * trinket is bound to its pet and the plain item otherwise, and the copy is the same pet everywhere (ownership, variant, death).
54 [items-and-npcs]     fun `a bound trinket turns the pet item into its copy and unbinding turns it back, in the backpack and the bank`() {
60 [items-and-npcs]         player.inventory.add(BurdenTrinkets.Tier.PACK_YAK.item, 1)
63 [items-and-npcs]         player.attr[BurdenTrinkets.BOUND_ATTR] = BurdenTrinkets.encode(mapOf(variant.pet to BurdenTrinkets.Tier.PACK_YAK.item))
71 [items-and-npcs]         player.attr.remove(BurdenTrinkets.BOUND_ATTR)

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/starter/NewPlayerStartTests.kt
SHA256 804a3e24217f0a73ce2aceeb39c52c2010aecea7f748409ede7ba32240cc29a5

23 [starter] class NewPlayerStartTests {
29 [starter]         NewPlayerStart.grantStarterPack(player)
30 [starter]         NewPlayerStart.grantStarterPack(player)
31 [starter]         assertEquals(1, player.inventory.getItemCount(config.starterChestItem))
34 [starter]         NewPlayerStart.openStarterPack(player)
35 [starter]         assertEquals(0, player.inventory.getItemCount(config.starterChestItem))
36 [starter]         config.starterChestContents.forEach { (item, amount) -> assertEquals(amount, player.inventory.getItemCount(item)) }
44 [starter]         NewPlayerStart.grantStarterPack(player)
45 [starter]         NewPlayerStart.openStarterPack(player)
46 [starter]         assertEquals(NewPlayerStart.STARTER_CASH, player.inventory.getItemCount(Items.COINS_995))
63 [starter]         val options = DEFINITIONS.get(ItemDef::class.java, NewPlayerConfig.DEFAULTS.starterChestItem).inventoryMenu

## game/plugins/src/test/kotlin/gg/rsmod/plugins/content/starter/PvpRespawnChestTests.kt
SHA256 7fa2c4077909259606541ab87ac136deaadfefa48a12697901e6bb1ccc7efe91

14 [starter] class PvpRespawnChestTests {
22 [starter]         assertEquals(78_000, PvpRespawnChest.COINS)
23 [starter]         assertEquals(24623, PvpRespawnChest.ITEM)
24 [starter]         assertEquals(60L * 60L * 1000L, PvpRespawnChest.COOLDOWN_MS)
31 [starter]         assertTrue(PvpRespawnChest.eligible(p, t))
32 [starter]         p.attr[PvpRespawnChest.LAST_GRANTED] = t
33 [starter]         assertFalse(PvpRespawnChest.eligible(p, t + 1))
34 [starter]         assertFalse(PvpRespawnChest.eligible(p, t + PvpRespawnChest.COOLDOWN_MS - 1))
35 [starter]         assertTrue(PvpRespawnChest.eligible(p, t + PvpRespawnChest.COOLDOWN_MS))
37 [starter]         assertTrue(PvpRespawnChest.eligible(p, t - 5))
42 [starter]         assertEquals("pvp_respawn_chest_at", PvpRespawnChest.LAST_GRANTED.persistenceKey)
51 [starter]         assertEquals(1_000_000, NewPlayerStart.STARTER_CASH)

## game/src/main/kotlin/gg/rsmod/game/fs/NonTransmittedVarbitDefinitionProvider.kt
SHA256 5873faf5e89a867f1de1f26c4491f6e02f5677c3970832e9ad9c671a242a1d88

203 [starter]             VarbitDef(28555).also {
233 [starter]             VarbitDef(28557).also {

## game/src/main/kotlin/gg/rsmod/game/model/attr/Attributes.kt
SHA256 55436ac40b980d268f1cbe04c6893b757cd459994f377bea65296632bedd9ba9

521 [combat-family] val SCYTHE = AttributeKey<Boolean>(persistenceKey = "scythe")

## game/src/main/kotlin/gg/rsmod/game/model/attr/PointBalance.kt
SHA256 7627b8859728308a32424eebcb34661efb4e8ea178b5d78353ce184997fc5a6c

99 [currency]             "breach_points",
122 [deaths-and-persistence]     fun repairWrapped(attrs: AttributeMap): List<Repair> {

## game/src/main/kotlin/gg/rsmod/game/model/combat/DamageMap.kt
SHA256 66e9931c3512edc9fc1d467e5a893a00bfc905f757a5620b32643d446508d9d9

108 [deaths-and-persistence]      * measure in wall-clock time (the D-07 fed-kill check in `ValidPkKill`); prefer [lastHitCycle].

## game/src/main/kotlin/gg/rsmod/game/model/World.kt
SHA256 394eb9512d42ff40dcac31afb1e7ed43ba594143c899ad5996917a07aae5b643

810 [items-and-npcs]     /** N436: a second examine line for an item, per player (the bound burden trinket of an "Upgraded" pet item); null = none. */

## game/src/main/kotlin/gg/rsmod/game/plugin/KotlinPlugin.kt
SHA256 e6b7c2a109fbfa10083369df594b7e8d88788696d903258e69bc05b66add76f8

186 [deaths-and-persistence]     fun register_container_key(key: ContainerKey) {

## game/src/main/kotlin/gg/rsmod/game/service/serializer/json/JsonPlayerSerializer.kt
SHA256 58c6834ca058ba971e4d534c3e5acf07914761f1593e1b62439a57af6c95e626

183 [deaths-and-persistence]             PointBalance.repairWrapped(client.attr).forEach { fix ->

## game/src/main/kotlin/gg/rsmod/game/tools/importer/BurdenTrinketTool.kt
SHA256 406f14909c6a315db79f45765c7d3a5e392f73233f24b42b220ed4e17011e9ca

7 [items-and-npcs]  * N423 Beast of burden trinkets (owner 2026-10-29, design v6 approved): five items that look exactly like the grey Trinket of
11 [items-and-npcs]  *  - `dump <outFile>` - the Trinket of fairies mesh and the five pouch meshes as JSON (vertices, faces, colours, priorities,
12 [items-and-npcs]  *    alpha), read-only, for the composition step (work\claude-scripts\n423-trinkets\compose.py).
14 [items-and-npcs] object BurdenTrinketTool {
15 [items-and-npcs]     const val TRINKET_OF_FAIRIES_MODEL = 63897
43 [items-and-npcs]                         (intArrayOf(TRINKET_OF_FAIRIES_MODEL) + POUCH_MODELS).associateWith { id ->
138 [items-and-npcs]         val header = "# N423: base pet npc -> its Upgraded copy (BurdenTrinketTool npcs). base\tupgraded\tname"
164 [items-and-npcs]     /** One trinket: item name, the pouch whose emblem it carries, its examine (items.yml). */
165 [items-and-npcs]     data class Trinket(val name: String, val pouchModel: Int, val spirit: String, val slots: Int)
167 [items-and-npcs]     val TRINKETS =
169 [items-and-npcs]             Trinket("Kalphite burden trinket", 30800, "kalphite", 6),
170 [items-and-npcs]             Trinket("Bull ant burden trinket", 30827, "bull ant", 9),
171 [items-and-npcs]             Trinket("Terrorbird burden trinket", 30651, "terrorbird", 12),
172 [items-and-npcs]             Trinket("Tortoise burden trinket", 30819, "war tortoise", 18),
173 [items-and-npcs]             Trinket("Pack yak burden trinket", 30837, "pack yak", 30),
221 [items-and-npcs]      * The item: the Trinket of fairies' own icon camera (zoom 1414, xan 324, yan 95, y offset 16), the new mesh, inventory ops
223 [items-and-npcs]      * inventory menu skips an empty verb, so the trinket offers no Drop and no Destroy (design v6: never lost). No op 65: not
262 [items-and-npcs]     /** The first item id the copies may use (the five trinkets end at 24236; checked free before writing). */
311 [items-and-npcs]         val header = "# N436: base pet item -> its Upgraded copy (BurdenTrinketTool items). base\tupgraded\tname"
337 [items-and-npcs]     /** The five trinket items, in [TRINKETS] order (items.yml 24232-24236). */
338 [items-and-npcs]     val TRINKET_ITEMS = intArrayOf(24232, 24233, 24234, 24235, 24236)
342 [items-and-npcs]      * written over the model each trinket item already uses (its item op 1) - no new ids, the items stay as they are.
352 [items-and-npcs]             TRINKETS.forEachIndexed { i, t ->
353 [items-and-npcs]                 val item = TRINKET_ITEMS[i]
392 [items-and-npcs]             TRINKETS.associate { t ->
407 [items-and-npcs]             TRINKETS.map { t ->
408 [items-and-npcs]                 val model = ImportBatchOrchestrator.ModelSource("n423_trinket_mesh:${t.pouchModel}", "model", encoded.getValue(t.name))
409 [items-and-npcs]                 ImportBatchOrchestrator.ItemSource("n423_trinket:${t.slots}", t.name, listOf(model)) { local ->

## game/src/main/kotlin/gg/rsmod/game/tools/importer/ItemDefCodec.kt
SHA256 803b134fa4f36871794e1201327ec87a5b15a4fa38f39d4b3c2561348967cd50

85 [combat-family]      * FINAL_TWISTED_BOW_RUNTIME_VALIDATION_GATE) proved param 686 (weapon-style-category, read by

## game/src/main/kotlin/gg/rsmod/game/tools/importer/ItemParamProbeTool.kt
SHA256 c00d58db9fe2378b325cd239cbd4bc28ccd6518dc14f167f97df20a5dca95a84

14 [combat-family]  * reopening (`RSPS_CURRENT_SPRINT.json` FINAL_TWISTED_BOW_RUNTIME_VALIDATION_GATE): comparing this

## game/src/main/kotlin/gg/rsmod/game/tools/importer/NpcLookCopyTool.kt
SHA256 79b93cf302090e8621b93e0e078ab3524f031c2799a71a9e351f5b63e2ccb369

11 [currency]  * Owner 2026-10-09 201749: "deadmanstore en breach trader gebruiken de zelfde npc verander 1 naar een ander dus uiterlijk" - both are
12 [currency]  * clones of the OSRS Emblem Trader (OsrsNpcImportTool store-npcs / breach-trader). The owner picked the Breach Trader for a fitting

## game/src/main/kotlin/gg/rsmod/game/tools/importer/OsrsFxImportTool.kt
SHA256 39c5057876d8a5daa05eff7043803952bf5008a623a3ac6d763a873283300c7f

377 [combat-family]             // HUMAN_AXE_CHOP 393 (staff bash), HUMAN_STAFF_PUMMEL 414 (wand melee), HUMAN_SPEAR_SPIKE 428, HUMAN_SCYTHE_SWEEP 440,

## game/src/main/kotlin/gg/rsmod/game/tools/importer/OsrsItemImportTool.kt
SHA256 032320dd88a37c1261ec77847f2e1b8d90cac66a3f9b9687a76d48eb68fa69b5

955 [items-and-npcs]             // Trinkets of fairies 28564, avarice 33044, undead 28570 and fortuity (inactive) 33047 (OSRS Wiki infobox ids).
1155 [currency]             // Breach Trader stock (owner 2026-09-23 "doe ... de rest"): the five OSRS halos the Annihilation shop sells that this
1171 [starter]             // "Deadman starter pack": item 22330, untradeable; its contents are data/cfg/new_player.yml starter_chest.
1174 [starter]                     Spec(22330), // Deadman starter pack
1320 [starter]             28555 to "Starter bow: no main item in items.yml",

## game/src/main/kotlin/gg/rsmod/game/tools/importer/OsrsNpcImportTool.kt
SHA256 9311fcf30e35c798451bf7e157bd96fd6b6f8561ef4f598f64b90c92dd260b3b

195 [currency]             // Owner 2026-09-23 (Breach Points shop, own npc): the OSRS Emblem Trader 308 ran the Annihilation points shop.
196 [currency]             "breach-trader" to listOf(308 to "Breach Trader"),

## game/src/main/kotlin/gg/rsmod/game/tools/importer/ShopWindowScriptTool.kt
SHA256 8b9ca980ad31496d3beba62b287045ec52323d1cca7c5eee9db0764894e3370d

12 [currency]  *   (946 + slot) and 99,000 for any skillcape - never what the server charges (Breach Trader 50 shown / 5,000 charged, Max cape

## game/src/main/kotlin/gg/rsmod/game/tools/importer/TrinketGlowTool.kt
SHA256 c6865ca098bd3022cf46481798c6d54d02077f0973e53e84f409c1c10241bacb

7 [items-and-npcs]  * N435 (owner 2026-10-30: "kun je de effecten per trinket andere kleur doen", choice "oplopend zeldzaam": 6 grey, 9 green,
8 [items-and-npcs]  * 12 blue, 18 purple, 30 gold): the burden trinket's bind glow, spotanim 1517 (green, model 31639), gets four recoloured copies.
12 [items-and-npcs]  * Usage: `./gradlew :game:runTrinketGlowTool --args="[--apply]"` (plan by default; CacheTransaction, both caches). Prints the new
13 [items-and-npcs]  * spotanim ids in tier order (BurdenTrinkets.Tier.glow).
15 [items-and-npcs] object TrinketGlowTool {
83 [items-and-npcs]                 mutations += CacheMutation(INDEX_SPOTANIM, id ushr 8, id and 0xFF, def, "N435 burden trinket glow $name")

## game/src/test/kotlin/gg/rsmod/game/tools/importer/ItemDefCodecTests.kt
SHA256 6fc3cf43b317efaedb13db5fa1562a23d2c3893c41c7de909b2a8dd6f06e916e

169 [combat-family]      * FINAL_TWISTED_BOW_RUNTIME_VALIDATION_GATE, 2026-09-04): CS2 script 1136 (interface 884

## game/src/test/kotlin/gg/rsmod/game/tools/importer/ModelConversionTests.kt
SHA256 b2ce391a9dc80e96f84c59c50d348d509e936fa4c826fa098ba8729b5c316129

124 [combat-family]         TWISTED_BOW_MODELS.forEach { (label, modelId) ->
130 [combat-family]         assertEquals("every pinned Twisted Bow model should be exercised", TWISTED_BOW_MODELS.size, checked)
137 [combat-family]         val TWISTED_BOW_MODELS =

## game/src/test/kotlin/gg/rsmod/game/tools/importer/ShopWindowScriptToolTests.kt
SHA256 141b8016aaaddd99b3ef7593e3343f852fc23e07c9bebaf645a71ae77ff16017

142 [currency]             assertEquals(5_000, price(0, 24000, 5_000).returned, "$cache: Breach Trader slot 0 costs 5,000")

## data/cfg/commands.yml
SHA256 f5b87cbf6d9ca82e6805a9faf7f97513d2a3d5dabfa8e60667c75187a61ab439

865 [currency] - name: breachpoints
867 [currency]   usage: "::breachpoints"
868 [currency]   description: "Show your Breach Points and how many you earned from breach damage."
869 [currency]   example: "::breachpoints"

## data/cfg/new_player.yml
SHA256 43abc870ebf40970a4b49ab0bc84d2cae7bfb999cc0abc6601a0fb42fc1cca4e

44 [starter]   # The Deadman Mode starter chest the intro hands out (NewPlayerStart.grantStarterPack). item 23881 = OSRS item 22330 "Deadman
