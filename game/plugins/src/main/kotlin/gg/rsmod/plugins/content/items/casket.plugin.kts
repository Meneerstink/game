package gg.rsmod.plugins.content.items

import gg.rsmod.plugins.content.drops.DropTableBuilder
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.DropTableType
import gg.rsmod.util.Misc.formatWithIndefiniteArticle

/**
 * @author Alycia <https://github.com/alycii>
 */

// Shared Treasure Trail "junk" pool, ported from Novite's ClueScrollManager.JUNK_ITEMS (recounted
// directly from the donor's array literal: 34 entries, not the "33" an earlier note assumed -
// same eyeball-miscount pattern already seen on the Easy/Medium/Hard/Elite reward-table counts).
// All 34 ids cross-checked against this project's own Items.kt named constants; quantities/ranges
// taken verbatim from the donor's Utils.random(...) calls. Rolled uniformly (1/34 each), matching
// the donor's own `JUNK_ITEMS[Utils.random(JUNK_ITEMS.length - 1)]` selection.
val treasureTrailJunk: DropTableBuilder.() -> Unit = {
    main {
        total(34)
        obj(Items.AIR_RUNE, quantityRange = 500..1000, slots = 1)
        obj(Items.COINS_995, quantityRange = 100..15000, slots = 1)
        obj(Items.RUNE_PLATELEGS, slots = 1)
        obj(Items.RUNE_PLATEBODY, slots = 1)
        obj(Items.RUNE_CROSSBOW, slots = 1)
        obj(Items.RUNE_PICKAXE, slots = 1)
        obj(Items.PRAYER_POTION_4_NOTED, quantityRange = 5..15, slots = 1)
        obj(Items.MITHRIL_PICKAXE, slots = 1)
        obj(Items.BRONZE_LONGSWORD, slots = 1)
        obj(Items.BLACK_DAGGER, slots = 1)
        obj(Items.PURPLE_SWEETS_10476, quantityRange = 30..100, slots = 1)
        obj(Items.WHITE_FIRELIGHTER, quantityRange = 10..100, slots = 1)
        obj(Items.MAGIC_SHORTBOW, slots = 1)
        obj(Items.RUNE_ARROW, quantityRange = 1..15, slots = 1)
        obj(Items.BRONZE_ARROW, quantityRange = 1..100, slots = 1)
        obj(Items.RAW_TROUT_NOTED, quantityRange = 0..10, slots = 1)
        obj(Items.BODY_TALISMAN, slots = 1)
        obj(Items.FIRE_TALISMAN, slots = 1)
        obj(Items.NATURE_RUNE, quantityRange = 20..50, slots = 1)
        obj(Items.BLACK_DHIDE_BODY, slots = 1)
        obj(Items.BLOOD_RUNE, quantityRange = 20..50, slots = 1)
        obj(Items.AMULET_OF_STRENGTH, slots = 1)
        obj(Items.AMULET_OF_ACCURACY, slots = 1)
        obj(Items.AMULET_OF_MAGIC, slots = 1)
        obj(Items.SHORTBOW, slots = 1)
        obj(Items.WILLOW_SHORTBOW, slots = 1)
        obj(Items.MIND_RUNE, quantityRange = 20..200, slots = 1)
        obj(Items.SHARK_NOTED, quantity = 10, slots = 1)
        obj(Items.RAW_SALMON_NOTED, quantityRange = 1..100, slots = 1)
        obj(Items.STAFF_OF_AIR, slots = 1)
        obj(Items.STAFF_OF_WATER, slots = 1)
        obj(Items.COIF, slots = 1)
        obj(Items.MAGIC_LONGBOW_NOTED, quantityRange = 10..30, slots = 1)
        obj(Items.BURNT_MONKFISH, slots = 1)
    }
}

on_item_option(item = Items.CASKET, option = "open") {
    if (player.inventory
            .remove(
                player.getInteractingItem(),
                beginSlot = player.getInteractingItemSlot(),
            ).hasSucceeded()
    ) {
        val drop =
            DropTableFactory.createDropInventory(player, Items.CASKET, DropTableType.CHEST) ?: return@on_item_option
        val item = drop[0]
        player.queue {
            val name =
                world.definitions
                    .get(ItemDef::class.java, item.id)
                    .name
                    .lowercase()
            itemMessageBox(
                "You open the casket. Inside you find ${formatWithIndefiniteArticle(name)}.",
                item = item.id,
                amountOrZoom = item.amount,
            )
        }
    }
}

on_item_option(item = Items.CASKET_EASY, option = "open") {
    if (player.inventory
            .remove(
                player.getInteractingItem(),
                beginSlot = player.getInteractingItemSlot(),
            ).hasSucceeded()
    ) {
        val drop =
            DropTableFactory.createDropInventory(player, Items.CASKET_EASY, DropTableType.CHEST)
                ?: return@on_item_option
        val item = drop[0]
        player.queue {
            val name =
                world.definitions
                    .get(ItemDef::class.java, item.id)
                    .name
                    .lowercase()
            itemMessageBox(
                "You open the casket. Inside you find ${formatWithIndefiniteArticle(name)}.",
                item = item.id,
                amountOrZoom = item.amount,
            )
        }
    }
}

val table = DropTableFactory
val casketRewards =
    table.build {
        main {
            total(128)
            obj(Items.COINS_995, quantity = 20, slots = 10)
            obj(Items.COINS_995, quantity = 40, slots = 10)
            obj(Items.COINS_995, quantity = 80, slots = 10)
            obj(Items.COINS_995, quantity = 160, slots = 10)
            obj(Items.COINS_995, quantity = 320, slots = 10)
            obj(Items.COINS_995, quantity = 640, slots = 10)

            obj(Items.UNCUT_SAPPHIRE, slots = 32)
            obj(Items.UNCUT_EMERALD, slots = 16)
            obj(Items.UNCUT_RUBY, slots = 8)
            obj(Items.UNCUT_DIAMOND, slots = 2)

            obj(Items.COSMIC_TALISMAN, slots = 8)
            obj(Items.LOOP_HALF_OF_A_KEY, slots = 1)
            obj(Items.TOOTH_HALF_OF_A_KEY, slots = 1)
        }
    }

table.register(casketRewards, Items.CASKET, type = DropTableType.CHEST)

// Treasure Trail casket (Easy tier) reward table, ported from Novite's Treasures.EASY item list
// (all 50 ids cross-checked against this project's own Items.kt - god-set armour pieces, elegant
// clothing, berets and the wizard/studded leather sets, matching authentic easy-clue conventions).
// Junk gate: Novite's ScrollType/Treasures.EASY carries chance=30.0 (30% rare-list hit per roll,
// 70% junk). The 50 rare entries below are scaled x3 (slots=3, sum=150) so a total(500) table
// gives an exact 150/500=30% rare / 350/500=70% junk split matching the donor's real figure.
val easyCasketRewards =
    table.build {
        main {
            total(500)
            obj(Items.BLACK_FULL_HELM_T, slots = 3)
            obj(Items.BLACK_PLATEBODY_T, slots = 3)
            obj(Items.BLACK_PLATELEGS_T, slots = 3)
            obj(Items.BLACK_KITESHIELD_T, slots = 3)
            obj(Items.BLACK_FULL_HELM_G, slots = 3)
            obj(Items.BLACK_PLATEBODY_G, slots = 3)
            obj(Items.BLACK_PLATELEGS_G, slots = 3)
            obj(Items.BLACK_KITESHIELD_G, slots = 3)
            obj(Items.BLUE_BERET, slots = 3)
            obj(Items.BLACK_BERET, slots = 3)
            obj(Items.WHITE_BERET, slots = 3)
            obj(Items.AMULET_OF_MAGIC_T, slots = 3)
            obj(Items.WIZARD_HAT_T, slots = 3)
            obj(Items.WIZARD_ROBE_T, slots = 3)
            obj(Items.BLUE_SKIRT_T, slots = 3)
            obj(Items.WIZARD_HAT_G, slots = 3)
            obj(Items.WIZARD_ROBE_G, slots = 3)
            obj(Items.BLUE_SKIRT_G, slots = 3)
            obj(Items.STUDDED_BODY_T, slots = 3)
            obj(Items.STUDDED_CHAPS_T, slots = 3)
            obj(Items.STUDDED_BODY_G, slots = 3)
            obj(Items.STUDDED_CHAPS_G, slots = 3)
            obj(Items.BLACK_HERALDIC_ARMOUR_SET_1_LG, slots = 3)
            obj(Items.BLACK_HERALDIC_ARMOUR_SET_2_LG, slots = 3)
            obj(Items.BLACK_HERALDIC_ARMOUR_SET_3_LG, slots = 3)
            obj(Items.BLACK_HERALDIC_ARMOUR_SET_4_LG, slots = 3)
            obj(Items.BLACK_HERALDIC_ARMOUR_SET_5_LG, slots = 3)
            obj(Items.RED_ELEGANT_SHIRT, slots = 3)
            obj(Items.RED_ELEGANT_LEGS, slots = 3)
            obj(Items.RED_ELEGANT_BLOUSE, slots = 3)
            obj(Items.RED_ELEGANT_SKIRT, slots = 3)
            obj(Items.BLUE_ELEGANT_SHIRT, slots = 3)
            obj(Items.BLUE_ELEGANT_LEGS, slots = 3)
            obj(Items.BLUE_ELEGANT_BLOUSE, slots = 3)
            obj(Items.BLUE_ELEGANT_SKIRT, slots = 3)
            obj(Items.GREEN_ELEGANT_SHIRT, slots = 3)
            obj(Items.GREEN_ELEGANT_LEGS, slots = 3)
            obj(Items.GREEN_ELEGANT_BLOUSE, slots = 3)
            obj(Items.GREEN_ELEGANT_SKIRT, slots = 3)
            obj(Items.SARADOMIN_ROBE_TOP, slots = 3)
            obj(Items.SARADOMIN_ROBE_LEGS, slots = 3)
            obj(Items.SARADOMIN_STOLE, slots = 3)
            obj(Items.ZAMORAK_ROBE_TOP, slots = 3)
            obj(Items.ZAMORAK_ROBE_LEGS, slots = 3)
            obj(Items.ZAMORAK_CROZIER, slots = 3)
            obj(Items.ZAMORAK_STOLE, slots = 3)
            obj(Items.GUTHIX_ROBE_TOP, slots = 3)
            obj(Items.GUTHIX_ROBE_LEGS, slots = 3)
            obj(Items.GUTHIX_STOLE, slots = 3)
            obj(Items.GUTHIX_CROZIER, slots = 3)
            table(treasureTrailJunk, slots = 350)
        }
    }

table.register(easyCasketRewards, Items.CASKET_EASY, type = DropTableType.CHEST)

on_item_option(item = Items.CASKET_MEDIUM, option = "open") {
    if (player.inventory
            .remove(
                player.getInteractingItem(),
                beginSlot = player.getInteractingItemSlot(),
            ).hasSucceeded()
    ) {
        val drop =
            DropTableFactory.createDropInventory(player, Items.CASKET_MEDIUM, DropTableType.CHEST)
                ?: return@on_item_option
        val item = drop[0]
        player.queue {
            val name =
                world.definitions
                    .get(ItemDef::class.java, item.id)
                    .name
                    .lowercase()
            itemMessageBox(
                "You open the casket. Inside you find ${formatWithIndefiniteArticle(name)}.",
                item = item.id,
                amountOrZoom = item.amount,
            )
        }
    }
}

// Treasure Trail casket (Medium tier) reward table, ported from Novite's Treasures.MEDIUM item
// list (all 42 ids cross-checked against this project's own Items.kt - adamant T/G armour set,
// dragonhide T/G body/chaps, ranger/wizard boots, boaters, animal masks, GWD-god robe top/legs
// and adamant heraldic armour lots, matching authentic medium-clue conventions).
// Junk gate: Treasures.MEDIUM chance=20.0 -> 42 rare slots / 210 total = 20% rare, 168 junk = 80%.
val mediumCasketRewards =
    table.build {
        main {
            total(210)
            obj(Items.ADAMANT_FULL_HELM_T, slots = 1)
            obj(Items.ADAMANT_PLATEBODY_T, slots = 1)
            obj(Items.ADAMANT_PLATELEGS_T, slots = 1)
            obj(Items.ADAMANT_KITESHIELD_T, slots = 1)
            obj(Items.ADAMANT_FULL_HELM_G, slots = 1)
            obj(Items.ADAMANT_PLATEBODY_G, slots = 1)
            obj(Items.ADAMANT_PLATELEGS_G, slots = 1)
            obj(Items.ADAMANT_KITESHIELD_G, slots = 1)
            obj(Items.SARADOMIN_CLOAK, slots = 1)
            obj(Items.GUTHIX_CLOAK, slots = 1)
            obj(Items.ZAMORAK_CLOAK, slots = 1)
            obj(Items.SARADOMIN_MITRE, slots = 1)
            obj(Items.GUTHIX_MITRE, slots = 1)
            obj(Items.ZAMORAK_MITRE, slots = 1)
            obj(Items.STRENGTH_AMULET_T_10736, slots = 1)
            obj(Items.ARMADYL_ROBE_TOP, slots = 1)
            obj(Items.ARMADYL_ROBE_LEGS, slots = 1)
            obj(Items.ANCIENT_ROBE_TOP, slots = 1)
            obj(Items.ANCIENT_ROBE_LEGS, slots = 1)
            obj(Items.BANDOS_ROBE_TOP, slots = 1)
            obj(Items.BANDOS_ROBE_LEGS, slots = 1)
            obj(Items.SHEEP_MASK, slots = 1)
            obj(Items.PENGUIN_MASK, slots = 1)
            obj(Items.BAT_MASK, slots = 1)
            obj(Items.CAT_MASK, slots = 1)
            obj(Items.WOLF_MASK, slots = 1)
            obj(Items.DRAGONHIDE_CHAPS_T, slots = 1)
            obj(Items.DRAGONHIDE_BODY_T, slots = 1)
            obj(Items.DRAGONHIDE_BODY_G, slots = 1)
            obj(Items.DRAGONHIDE_CHAPS_G, slots = 1)
            obj(Items.RANGER_BOOTS, slots = 1)
            obj(Items.WIZARD_BOOTS, slots = 1)
            obj(Items.RED_BOATER, slots = 1)
            obj(Items.ORANGE_BOATER, slots = 1)
            obj(Items.GREEN_BOATER, slots = 1)
            obj(Items.BLUE_BOATER, slots = 1)
            obj(Items.BLACK_BOATER, slots = 1)
            obj(Items.ADAMANT_HERALDIC_ARMOUR_SET_1_LG, slots = 1)
            obj(Items.ADAMANT_HERALDIC_ARMOUR_SET_2_LG, slots = 1)
            obj(Items.ADAMANT_HERALDIC_ARMOUR_SET_3_LG, slots = 1)
            obj(Items.ADAMANT_HERALDIC_ARMOUR_SET_4_LG, slots = 1)
            obj(Items.ADAMANT_HERALDIC_ARMOUR_SET_5_LG, slots = 1)
            table(treasureTrailJunk, slots = 168)
        }
    }

table.register(mediumCasketRewards, Items.CASKET_MEDIUM, type = DropTableType.CHEST)

on_item_option(item = Items.CASKET_HARD, option = "open") {
    if (player.inventory
            .remove(
                player.getInteractingItem(),
                beginSlot = player.getInteractingItemSlot(),
            ).hasSucceeded()
    ) {
        val drop =
            DropTableFactory.createDropInventory(player, Items.CASKET_HARD, DropTableType.CHEST)
                ?: return@on_item_option
        val item = drop[0]
        player.queue {
            val name =
                world.definitions
                    .get(ItemDef::class.java, item.id)
                    .name
                    .lowercase()
            itemMessageBox(
                "You open the casket. Inside you find ${formatWithIndefiniteArticle(name)}.",
                item = item.id,
                amountOrZoom = item.amount,
            )
        }
    }
}

// Treasure Trail casket (Hard tier) reward table, ported from Novite's Treasures.HARD item list
// (75 raw entries, 69 unique ids - Novite's flat list repeats an id to weight it since Treasures
// only carries one chance:Double per tier, not a per-item weight; the 6 ids that appear twice
// (rune god armour trimmed/gold full helm/platebody/platelegs/kiteshield) get slots=2 here to
// preserve that weighting, all others slots=1, total=75 matching the donor's raw entry count).
// All 69 unique ids cross-checked against target's own Items.kt: rune platebody/platelegs/full
// helm/kiteshield (trimmed+gold), 3 cavaliers, Zamorak/Guthix untrimmed god plate armour, gilded
// armour set, dragonhide body/chaps (trimmed+gold), enchanted robe/top/hat, pirate hat, robin
// hood hat, 3rd age range/melee/mage pieces, amulet of glory (t), Zamorak/Guthix/Saradomin
// vambraces/body/chaps/coif, god crozier/stole/mitre sets, and green/blue/red dragon masks -
// all authentic OSRS-era hard-clue categories.
// Junk gate: Treasures.HARD chance=15.0 -> 75 rare slots / 500 total = 15% rare, 425 junk = 85%.
val hardCasketRewards =
    table.build {
        main {
            total(500)
            obj(Items.ROBIN_HOOD_HAT, slots = 1)
            obj(Items.RUNE_PLATEBODY_G, slots = 2)
            obj(Items.RUNE_PLATELEGS_G, slots = 2)
            obj(Items.RUNE_FULL_HELM_G, slots = 2)
            obj(Items.RUNE_KITESHIELD_G, slots = 2)
            obj(Items.RUNE_PLATEBODY_T, slots = 2)
            obj(Items.RUNE_PLATELEGS_T, slots = 2)
            obj(Items.RUNE_FULL_HELM_T, slots = 2)
            obj(Items.RUNE_KITESHIELD_T, slots = 2)
            obj(Items.TAN_CAVALIER, slots = 1)
            obj(Items.DARK_CAVALIER, slots = 1)
            obj(Items.BLACK_CAVALIER, slots = 1)
            obj(Items.ZAMORAK_PLATEBODY, slots = 1)
            obj(Items.ZAMORAK_PLATELEGS, slots = 1)
            obj(Items.ZAMORAK_FULL_HELM, slots = 1)
            obj(Items.ZAMORAK_KITESHIELD, slots = 1)
            obj(Items.GUTHIX_PLATEBODY, slots = 1)
            obj(Items.GUTHIX_PLATELEGS, slots = 1)
            obj(Items.GUTHIX_FULL_HELM, slots = 1)
            obj(Items.GUTHIX_KITESHIELD, slots = 1)
            obj(Items.GILDED_PLATEBODY, slots = 1)
            obj(Items.GILDED_PLATELEGS, slots = 1)
            obj(Items.GILDED_PLATESKIRT, slots = 1)
            obj(Items.GILDED_FULL_HELM, slots = 1)
            obj(Items.GILDED_KITESHIELD, slots = 1)
            obj(Items.DRAGONHIDE_BODY_G_7374, slots = 1)
            obj(Items.DRAGONHIDE_BODY_T_7376, slots = 1)
            obj(Items.DRAGONHIDE_CHAPS_G_7382, slots = 1)
            obj(Items.DRAGONHIDE_CHAPS_T_7384, slots = 1)
            obj(Items.ENCHANTED_ROBE, slots = 1)
            obj(Items.ENCHANTED_TOP, slots = 1)
            obj(Items.ENCHANTED_HAT, slots = 1)
            obj(Items.PIRATE_HAT, slots = 1)
            obj(Items.THIRDAGE_RANGE_TOP, slots = 1)
            obj(Items.THIRDAGE_RANGE_LEGS, slots = 1)
            obj(Items.THIRDAGE_RANGE_COIF, slots = 1)
            obj(Items.THIRDAGE_VAMBRACES, slots = 1)
            obj(Items.THIRDAGE_ROBE_TOP, slots = 1)
            obj(Items.THIRDAGE_ROBE, slots = 1)
            obj(Items.THIRDAGE_MAGE_HAT, slots = 1)
            obj(Items.THIRDAGE_PLATELEGS, slots = 1)
            obj(Items.THIRDAGE_PLATEBODY, slots = 1)
            obj(Items.THIRDAGE_FULL_HELMET, slots = 1)
            obj(Items.THIRDAGE_KITESHIELD, slots = 1)
            obj(Items.AMULET_OF_GLORY_T, slots = 1)
            obj(Items.ZAMORAK_VAMBRACES, slots = 1)
            obj(Items.ZAMORAK_BODY, slots = 1)
            obj(Items.ZAMORAK_CHAPS, slots = 1)
            obj(Items.ZAMORAK_COIF, slots = 1)
            obj(Items.GUTHIX_VAMBRACES, slots = 1)
            obj(Items.GUTHIX_BODY, slots = 1)
            obj(Items.GUTHIX_CHAPS, slots = 1)
            obj(Items.GUTHIX_COIF, slots = 1)
            obj(Items.SARADOMIN_VAMBRACES, slots = 1)
            obj(Items.SARADOMIN_BODY, slots = 1)
            obj(Items.SARADOMIN_CHAPS, slots = 1)
            obj(Items.SARADOMIN_COIF, slots = 1)
            obj(Items.SARADOMIN_CROZIER, slots = 1)
            obj(Items.GUTHIX_CROZIER, slots = 1)
            obj(Items.ZAMORAK_CROZIER, slots = 1)
            obj(Items.SARADOMIN_STOLE, slots = 1)
            obj(Items.GUTHIX_STOLE, slots = 1)
            obj(Items.ZAMORAK_STOLE, slots = 1)
            obj(Items.GREEN_DRAGON_MASK, slots = 1)
            obj(Items.BLUE_DRAGON_MASK, slots = 1)
            obj(Items.RED_DRAGON_MASK, slots = 1)
            obj(Items.ARMADYL_MITRE, slots = 1)
            obj(Items.BANDOS_MITRE, slots = 1)
            obj(Items.ANCIENT_MITRE, slots = 1)
            table(treasureTrailJunk, slots = 425)
        }
    }

table.register(hardCasketRewards, Items.CASKET_HARD, type = DropTableType.CHEST)

/*
 * Owner answer Q9: the elite reward casket works like OSRS (EliteCasketTable: the OSRS Wiki slot tree, 4-6 reward rolls). ADAPTED: the 667
 * client has no clue reward interface (interface 364 is the Barrows chest), so the rewards go to the inventory (ground when full, as
 * before) and are listed in the existing casket message box. Not in the rolls yet: master clue 1/5 (owner question 15), Mimic 1/35 (Q10).
 */
on_item_option(item = Items.CASKET_ELITE, option = "open") {
    if (!player.inventory.remove(player.getInteractingItem(), beginSlot = player.getInteractingItemSlot()).hasSucceeded()) {
        return@on_item_option
    }
    val rolls = kotlin.random.Random.nextInt(EliteCasketTable.MIN_ROLLS, EliteCasketTable.MAX_ROLLS + 1)
    val rewards = mutableListOf<gg.rsmod.game.model.item.Item>()
    repeat(rolls) { DropTableFactory.createDropInventory(player, Items.CASKET_ELITE, DropTableType.CHEST)?.let { rewards += it } }
    if (rewards.isEmpty()) return@on_item_option
    val first = rewards.first()
    player.queue {
        val names =
            rewards.joinToString(", ") { reward ->
                val name = world.definitions.get(ItemDef::class.java, reward.id).name.lowercase()
                if (reward.amount > 1) "${reward.amount} x $name" else formatWithIndefiniteArticle(name)
            }
        itemMessageBox("You open the casket. Inside you find $names.", item = first.id, amountOrZoom = first.amount)
    }
}

/** One OSRS elite reward roll as a drop table, built from [EliteCasketTable] node by node. */
fun eliteCasketTable(node: EliteCasketTable.Node): DropTableBuilder.() -> Unit =
    table.build {
        main {
            total(node.total)
            node.entries.forEach { entry ->
                when (entry) {
                    is EliteCasketTable.Entry.Obj ->
                        if (entry.amount.first == entry.amount.last) {
                            obj(entry.id, quantity = entry.amount.first, slots = entry.slots)
                        } else {
                            obj(entry.id, quantityRange = entry.amount, slots = entry.slots)
                        }
                    is EliteCasketTable.Entry.Table -> table(eliteCasketTable(entry.node), slots = entry.slots)
                    is EliteCasketTable.Entry.Nothing -> nothing(entry.slots)
                }
            }
        }
    }

table.register(eliteCasketTable(EliteCasketTable.ROLL), Items.CASKET_ELITE, type = DropTableType.CHEST)
