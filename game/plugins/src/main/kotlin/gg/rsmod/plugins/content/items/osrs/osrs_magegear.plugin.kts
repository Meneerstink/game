package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item

/**
 * OSRS-IMPORT magegeara recipes that need more than one skill or a rune cost (CombinationData checks one skill only). Sources: OSRS Wiki
 * "Eternal boots" / "Pegasian boots" / "Primordial boots" (crystal on boots, "level 60 in Runecraft and Magic (boosts cannot be used),
 * granting 200 experience in both skills", "cannot be reversed"), "Avernic treads" ([AvernicTreads]), "Malediction ward" ("It can be
 * reverted, but the kit will not be returned"), "Elidinis' ward (f)" ("combine an arcane sigil with Elidinis' ward", 90 Prayer + 90
 * Smithing, boostable, 10,000 soul runes, 260 experience in each skill; dismantling returns the sigil and ward, "the soul runes will not
 * be returned"), "Magus icon" / "Venator icon" (icon on vestige "with 500 blood runes", 90 Magic (400 XP) and 80 Crafting, boostable).
 * ADAPTED: messages. NOT ENFORCED (owner question): Peer the Seer's "combine ring icons" unlock. BLOCKED: crafting the magus/venator ring
 * at a furnace with three chromium ingots and a ring mould is not bound yet.
 */

fun Player.has(skill: Int, level: Int, boostable: Boolean): Boolean =
    (if (boostable) skills.getCurrentLevel(skill) else skills.getMaxLevel(skill)) >= level

// Boot crystals.
listOf(
    Triple(Items.ETERNAL_CRYSTAL, Items.INFINITY_BOOTS, Items.ETERNAL_BOOTS),
    Triple(Items.PEGASIAN_CRYSTAL, Items.RANGER_BOOTS, Items.PEGASIAN_BOOTS),
    Triple(Items.PRIMORDIAL_CRYSTAL, Items.DRAGON_BOOTS, Items.PRIMORDIAL_BOOTS),
).forEach { (crystal, base, result) ->
    on_item_on_item(item1 = crystal, item2 = base) {
        if (!player.has(Skills.RUNECRAFTING, 60, boostable = false) || !player.has(Skills.MAGIC, 60, boostable = false)) {
            player.message("You need level 60 Runecraft and Magic to do that.")
            return@on_item_on_item
        }
        if (!player.inventory.remove(crystal, 1).hasSucceeded()) return@on_item_on_item
        if (!player.inventory.remove(base, 1).hasSucceeded()) {
            player.inventory.add(crystal, 1)
            return@on_item_on_item
        }
        player.inventory.add(result, 1)
        player.addXp(Skills.RUNECRAFTING, 200.0)
        player.addXp(Skills.MAGIC, 200.0)
    }
}

// Avernic treads upgrades and Dismantle.
AvernicTreads.Boots.values().forEach { boots ->
    AvernicTreads.ALL.forEach { treads ->
        if (AvernicTreads.upgraded(treads, boots) == null) return@forEach
        on_item_on_item(item1 = boots.item, item2 = treads) {
            if (!player.has(Skills.MAGIC, AvernicTreads.MAGIC_LEVEL, boostable = true) || !player.has(Skills.RUNECRAFTING, AvernicTreads.RUNECRAFT_LEVEL, boostable = true)) {
                player.message("You need level ${AvernicTreads.MAGIC_LEVEL} Magic and ${AvernicTreads.RUNECRAFT_LEVEL} Runecraft to do that.")
                return@on_item_on_item
            }
            if (player.inventory.getItemCount(Items.DEMON_TEAR) < AvernicTreads.TEARS_PER_PAIR) {
                player.message("You need ${AvernicTreads.TEARS_PER_PAIR} demon tears to do that.")
                return@on_item_on_item
            }
            val result = AvernicTreads.upgraded(treads, boots) ?: return@on_item_on_item
            if (!player.inventory.remove(treads, 1).hasSucceeded()) return@on_item_on_item
            player.inventory.remove(boots.item, 1)
            player.inventory.remove(Items.DEMON_TEAR, AvernicTreads.TEARS_PER_PAIR)
            player.inventory.add(result, 1)
        }
    }
}

AvernicTreads.UPGRADED.forEach { treads ->
    on_item_option(item = treads, option = "Dismantle") {
        val boots = AvernicTreads.appliedBoots(treads)
        if (player.inventory.freeSlotCount < boots.size) {
            player.message("You need ${boots.size} free inventory spaces to do that.")
            return@on_item_option
        }
        if (!player.inventory.remove(item = treads, beginSlot = player.getInteractingItemSlot()).hasSucceeded()) return@on_item_option
        player.inventory.add(Items.AVERNIC_TREADS, 1)
        boots.forEach { player.inventory.add(it, 1) }
    }
}

// Ward (or) Revert: the kit is not returned.
listOf(Items.MALEDICTION_WARD_OR to Items.MALEDICTION_WARD, Items.ODIUM_WARD_OR to Items.ODIUM_WARD).forEach { (ornamented, base) ->
    on_item_option(item = ornamented, option = "Revert") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != ornamented) return@on_item_option
        player.inventory[slot] = Item(base)
    }
}

// Elidinis' ward (f) and its Dismantle.
on_item_on_item(item1 = Items.ARCANE_SIGIL, item2 = Items.ELIDINIS_WARD) {
    if (!player.has(Skills.PRAYER, 90, boostable = true) || !player.has(Skills.SMITHING, 90, boostable = true)) {
        player.message("You need level 90 Prayer and Smithing to do that.")
        return@on_item_on_item
    }
    if (player.inventory.getItemCount(Items.SOUL_RUNE) < 10_000) {
        player.message("You need 10,000 soul runes to do that.")
        return@on_item_on_item
    }
    if (!player.inventory.remove(Items.ARCANE_SIGIL, 1).hasSucceeded()) return@on_item_on_item
    player.inventory.remove(Items.ELIDINIS_WARD, 1)
    player.inventory.remove(Items.SOUL_RUNE, 10_000)
    player.inventory.add(Items.ELIDINIS_WARD_F, 1)
    player.addXp(Skills.PRAYER, 260.0)
    player.addXp(Skills.SMITHING, 260.0)
}

on_item_option(item = Items.ELIDINIS_WARD_F, option = "Dismantle") {
    if (player.inventory.freeSlotCount < 1) {
        player.message("You need a free inventory space to do that.")
        return@on_item_option
    }
    if (!player.inventory.remove(item = Items.ELIDINIS_WARD_F, beginSlot = player.getInteractingItemSlot()).hasSucceeded()) return@on_item_option
    player.inventory.add(Items.ELIDINIS_WARD, 1)
    player.inventory.add(Items.ARCANE_SIGIL, 1)
}

// Confliction gauntlets (magegearb): "made with level 83 Crafting and level 70 Smithing by attaching the Mokhaiotl cloth ... to a
// tormented bracelet alongside 10,000 demon tears"; "not reversible". NOT ENFORCED: "must be done at a bank" (no bank-location check).
on_item_on_item(item1 = Items.MOKHAIOTL_CLOTH, item2 = Items.TORMENTED_BRACELET) {
    if (!player.has(Skills.CRAFTING, 83, boostable = true) || !player.has(Skills.SMITHING, 70, boostable = true)) {
        player.message("You need level 83 Crafting and 70 Smithing to do that.")
        return@on_item_on_item
    }
    if (player.inventory.getItemCount(Items.DEMON_TEAR) < 10_000) {
        player.message("You need 10,000 demon tears to do that.")
        return@on_item_on_item
    }
    if (!player.inventory.remove(Items.MOKHAIOTL_CLOTH, 1).hasSucceeded()) return@on_item_on_item
    player.inventory.remove(Items.TORMENTED_BRACELET, 1)
    player.inventory.remove(Items.DEMON_TEAR, 10_000)
    player.inventory.add(Items.CONFLICTION_GAUNTLETS, 1)
}

// Magus and venator icons.
listOf(
    Triple(Items.SEERS_ICON, Items.MAGUS_VESTIGE, Items.MAGUS_ICON),
    Triple(Items.ARCHER_ICON, Items.VENATOR_VESTIGE, Items.VENATOR_ICON),
).forEach { (icon, vestige, result) ->
    on_item_on_item(item1 = icon, item2 = vestige) {
        if (!player.has(Skills.MAGIC, 90, boostable = true) || !player.has(Skills.CRAFTING, 80, boostable = true)) {
            player.message("You need level 90 Magic and 80 Crafting to do that.")
            return@on_item_on_item
        }
        if (player.inventory.getItemCount(Items.BLOOD_RUNE) < 500) {
            player.message("You need 500 blood runes to do that.")
            return@on_item_on_item
        }
        if (!player.inventory.remove(icon, 1).hasSucceeded()) return@on_item_on_item
        player.inventory.remove(vestige, 1)
        player.inventory.remove(Items.BLOOD_RUNE, 500)
        player.inventory.add(result, 1)
        player.addXp(Skills.MAGIC, 400.0)
    }
}
