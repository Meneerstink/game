package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.item.Item

/**
 * OSRS import batch "demonbane": Arclight creation, charging and Check; Emberlight and Purging staff creation; Revert for every
 * tormented synapse product; Burning claws assembly. Rules and sources in [Demonbane].
 *
 * ADAPTED_TO_667 (recorded in OSRS_IMPORT_MASTER.yml):
 * - Arclight is made "by using Darklight on the altar in the centre of the Catacombs of Kourend while three ancient shards are in the
 *   inventory"; the Catacombs of Kourend do not exist in the 667 map, so every prayer altar of this server stands in for that altar.
 *   The sourced message is used when the Darklight is combined with shards elsewhere.
 * - Emberlight needs "having read Duradel's notes after completion of While Guthix Sleeps": quests are parked, the gate is not
 *   enforced (same treatment as the owner decision for Ferocious gloves / Dragon Slayer II).
 * - Check wording and failure messages other than the quoted ones are not sourced.
 */

val ANVILS = intArrayOf(Objs.ANVIL, Objs.ANVIL_12692, Objs.ANVIL_2783, Objs.ANVIL_24744, Objs.BARBARIAN_ANVIL)

val PRAYER_ALTARS =
    intArrayOf(
        Objs.ALTAR_27661, Objs.CHAOS_ALTAR, Objs.ALTAR_24343, Objs.ALTAR_2640, Objs.ALTAR, Objs.ALTAR_19145, FeroxAltar.id,
        Objs.ALTAR_36972, Objs.ALTAR_OF_GUTHIX, Objs.ALTAR_34616, Objs.ALTAR_18254, Objs.ALTAR_39842,
    )

object FeroxAltar {
    val id: Int get() = gg.rsmod.plugins.content.areas.home.FeroxObjects.ALTAR
}

/** Account-wide "first synapse product" flags: 730 experience the first time, 73 after (item pages). */
val EMBERLIGHT_CREATED = AttributeKey<Boolean>(persistenceKey = "osrs_emberlight_created")
val PURGING_STAFF_CREATED = AttributeKey<Boolean>(persistenceKey = "osrs_purging_staff_created")

fun slotOf(
    player: Player,
    itemId: Int,
): Int? {
    val first = player.attr[INTERACTING_ITEM_SLOT] ?: return null
    val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return null
    return when (itemId) {
        player.inventory[first]?.id -> first
        player.inventory[second]?.id -> second
        else -> null
    }
}

// Arclight creation: Darklight on a prayer altar with three ancient shards in the inventory.
PRAYER_ALTARS.forEach { altar ->
    on_item_on_obj(obj = altar, item = Items.DARKLIGHT) {
        if (player.inventory.getItemCount(Items.ANCIENT_SHARD) < Demonbane.SHARDS_TO_CREATE) {
            player.message("You need three ancient shards to empower Darklight.")
            return@on_item_on_obj
        }
        val slot = player.inventory.getItemIndex(Items.DARKLIGHT, skipAttrItems = false)
        if (slot < 0) return@on_item_on_obj
        player.inventory.remove(Items.ANCIENT_SHARD, Demonbane.SHARDS_TO_CREATE)
        player.inventory[slot] = Demonbane.created()
        player.animate(Anims.ALTAR_PRAY)
        player.message("The ancient shards dissolve into Darklight, creating Arclight.")
    }
}

on_item_on_item(item1 = Items.ANCIENT_SHARD, item2 = Items.DARKLIGHT) {
    player.message(Demonbane.ALTAR_MESSAGE)
}

// Charging: "333 charges for every ancient shard added, or 1,000 if three are added simultaneously", maximum 10,000. An inactive
// Arclight becomes the charged sword again with its infusion kept.
listOf(Items.ARCLIGHT, Items.ARCLIGHT_INACTIVE).forEach { swordId ->
    on_item_on_item(item1 = Items.ANCIENT_SHARD, item2 = swordId) {
        val slot = slotOf(player, swordId) ?: return@on_item_on_item
        val sword = player.inventory[slot] ?: return@on_item_on_item
        val shards = Demonbane.shardsToUse(sword, player.inventory.getItemCount(Items.ANCIENT_SHARD))
        if (shards <= 0) {
            player.message("Your Arclight is already fully charged.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.ANCIENT_SHARD, shards)
        val charged = Demonbane.addShards(sword, shards)
        player.inventory[slot] = charged
        player.message(Demonbane.checkMessage(charged))
    }

    on_item_option(item = swordId, option = "Check") {
        val sword = player.inventory[player.getInteractingItemSlot()]?.takeIf { it.id == swordId } ?: return@on_item_option
        player.message(Demonbane.checkMessage(sword))
    }

    on_equipment_option(item = swordId, option = "Check") {
        val sword = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id == swordId } ?: return@on_equipment_option
        player.message(Demonbane.checkMessage(sword))
    }

    on_item_on_item(item1 = Items.TORMENTED_SYNAPSE, item2 = swordId) {
        player.message(Demonbane.SYNAPSE_ON_SWORD_MESSAGE)
    }
}

/** The inventory slot of the most-progressed Arclight that can be upgraded, or null. */
fun upgradeableArclight(player: Player): Int? =
    (0 until player.inventory.capacity)
        .filter { player.inventory[it]?.let { item -> Demonbane.canUpgrade(item) } == true }
        .maxByOrNull { Demonbane.upgradeProgress(player.inventory[it]!!) }

fun makeEmberlight(player: Player) {
    if (player.skills.getCurrentLevel(Skills.SMITHING) < Demonbane.EMBERLIGHT_SMITHING_LEVEL) {
        player.message("You need a Smithing level of ${Demonbane.EMBERLIGHT_SMITHING_LEVEL} to make Emberlight.")
        return
    }
    if (!player.inventory.contains(Items.HAMMER)) {
        player.message("You need a hammer to work the synapse into the sword.")
        return
    }
    val slot = upgradeableArclight(player)
    if (slot == null) {
        player.message("Your Arclight must be fully charged, infused, or a combination of the two to be upgraded.")
        return
    }
    if (!player.inventory.contains(Items.TORMENTED_SYNAPSE)) return
    player.inventory.remove(Items.TORMENTED_SYNAPSE, 1)
    player.inventory[slot] = Item(Items.EMBERLIGHT)
    player.animate(Anims.SMITH_ANVIL)
    val first = player.attr[EMBERLIGHT_CREATED] != true
    player.attr[EMBERLIGHT_CREATED] = true
    player.addXp(Skills.SMITHING, if (first) Demonbane.SYNAPSE_FIRST_EXPERIENCE else Demonbane.SYNAPSE_EXPERIENCE)
}

/** Purging staff: tormented synapse, iron bar and battlestaff on an anvil with a hammer; 74 Crafting (boostable), 55 Smithing (not). */
fun makePurgingStaff(player: Player) {
    if (player.skills.getCurrentLevel(Skills.CRAFTING) < 74) {
        player.message("You need a Crafting level of 74 to make a purging staff.")
        return
    }
    if (player.skills.getMaxLevel(Skills.SMITHING) < 55) {
        player.message("You need a Smithing level of 55 to make a purging staff.")
        return
    }
    val needed = intArrayOf(Items.TORMENTED_SYNAPSE, Items.IRON_BAR, Items.BATTLESTAFF, Items.HAMMER)
    if (needed.any { !player.inventory.contains(it) }) {
        player.message("You need a tormented synapse, an iron bar, a battlestaff and a hammer to make a purging staff.")
        return
    }
    player.inventory.remove(Items.TORMENTED_SYNAPSE, 1)
    player.inventory.remove(Items.IRON_BAR, 1)
    player.inventory.remove(Items.BATTLESTAFF, 1)
    player.inventory.add(Items.PURGING_STAFF, 1)
    player.animate(Anims.SMITH_ANVIL)
    val first = player.attr[PURGING_STAFF_CREATED] != true
    player.attr[PURGING_STAFF_CREATED] = true
    player.addXp(Skills.CRAFTING, if (first) Demonbane.SYNAPSE_FIRST_EXPERIENCE else Demonbane.SYNAPSE_EXPERIENCE)
    player.addXp(Skills.SMITHING, 13.0)
}

ANVILS.forEach { anvil ->
    on_item_on_obj(obj = anvil, item = Items.TORMENTED_SYNAPSE) {
        val hasArclight = player.inventory.contains(Items.ARCLIGHT) || player.inventory.contains(Items.ARCLIGHT_INACTIVE)
        val hasStaffParts = player.inventory.contains(Items.IRON_BAR) && player.inventory.contains(Items.BATTLESTAFF)
        when {
            hasArclight && hasStaffParts ->
                player.queue {
                    when (options("Emberlight", "Purging staff", title = "What would you like to make?")) {
                        1 -> makeEmberlight(player)
                        2 -> makePurgingStaff(player)
                    }
                }
            hasArclight -> makeEmberlight(player)
            else -> makePurgingStaff(player)
        }
    }
}

// Revert (Transcript:Emberlight / Transcript:Scorching bow, the Purging staff page uses the same wording): only the synapse returns.
Demonbane.SYNAPSE_PRODUCTS.forEach { (productId, name) ->
    gg.rsmod.plugins.content.items.ItemActionGuard.selfConfirming("revert", productId) // own OSRS revert warning below
    on_item_option(item = productId, option = "Revert") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != productId) return@on_item_option
        player.queue {
            messageBox(Demonbane.revertWarning(name))
            if (options("Yes!", "No.", title = Demonbane.revertTitle(name)) != 1) return@queue
            if (player.inventory[slot]?.id != productId) return@queue
            player.inventory[slot] = Item(Items.TORMENTED_SYNAPSE)
            player.message(Demonbane.revertDone(name))
        }
    }
}

// Burning claws: two burning claws combined (4 ticks, no skill requirement on the item page).
on_item_on_item(item1 = Items.BURNING_CLAW, item2 = Items.BURNING_CLAW) {
    if (player.inventory.getItemCount(Items.BURNING_CLAW) < 2) {
        player.message("You need two burning claws to do that.")
        return@on_item_on_item
    }
    player.queue {
        wait(4)
        if (player.inventory.getItemCount(Items.BURNING_CLAW) < 2) return@queue
        player.inventory.remove(Items.BURNING_CLAW, 2)
        player.inventory.add(Items.BURNING_CLAWS, 1)
    }
}
