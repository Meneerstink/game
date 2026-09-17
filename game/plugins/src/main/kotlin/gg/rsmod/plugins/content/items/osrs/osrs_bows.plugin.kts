package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.item.Item

/**
 * OSRS-IMPORT batch bows: charging, Check/Uncharge, Dismantle and creation (rules and sources in [RevenantBows], [VenatorBow],
 * [Tonalztics], [CrystalEquipment]). ADAPTED: every message except the sourced Scorching bow lines (no sourced wording). Options are
 * bound only when the imported definition carries them.
 */

fun bowHasOption(
    itemId: Int,
    option: String,
    worn: Boolean,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, itemId)
    return (if (worn) def.equipmentMenu else def.inventoryMenu).any { it.equals(option, ignoreCase = true) }
}

fun usedSlot(
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

fun bindCheck(
    itemId: Int,
    status: (Item) -> String,
) {
    if (bowHasOption(itemId, "Check", worn = false)) {
        on_item_option(item = itemId, option = "Check") {
            val item = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
            player.message(status(item))
        }
    }
    if (bowHasOption(itemId, "Check", worn = true)) {
        on_equipment_option(item = itemId, option = "Check") {
            val item = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id == itemId } ?: return@on_equipment_option
            player.message(status(item))
        }
    }
}

// Craw's bow / Webweaver bow: revenant ether (1,000 to activate, then up to 16,000 ammo ether), Check, Uncharge.
RevenantBows.ALL.forEach { bow ->
    on_item_on_item(item1 = Items.REVENANT_ETHER, item2 = bow) {
        val slot = usedSlot(player, bow) ?: return@on_item_on_item
        val item = player.inventory[slot] ?: return@on_item_on_item
        val taken = RevenantBows.etherToTake(item, player.inventory.getItemCount(Items.REVENANT_ETHER))
        if (taken <= 0) {
            player.message(if (bow in RevenantBows.CHARGED_FOR) "You need 1,000 revenant ether to activate the bow." else "Your bow cannot hold any more revenant ether.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.REVENANT_ETHER, taken)
        val charged = RevenantBows.charge(item, taken)
        player.inventory[slot] = charged
        player.message("Revenant ether: ${RevenantBows.ether(charged)}")
    }
}

RevenantBows.UNCHARGED_FOR.keys.forEach { bow ->
    bindCheck(bow) { "Revenant ether: ${RevenantBows.ether(it)}" }
    if (bowHasOption(bow, "Uncharge", worn = false)) {
        on_item_option(item = bow, option = "Uncharge") {
            val slot = player.getInteractingItemSlot()
            val item = player.inventory[slot]?.takeIf { it.id == bow } ?: return@on_item_option
            val (uncharged, ether) = RevenantBows.uncharge(item)
            if (!player.inventory.add(Items.REVENANT_ETHER, ether, assureFullInsertion = true).hasSucceeded()) {
                player.message("You don't have enough inventory space to do that.")
                return@on_item_option
            }
            player.inventory[slot] = uncharged
        }
    }
}

// "Players can also dismantle an uncharged bow to receive 7,500 revenant ether."
if (bowHasOption(Items.CRAWS_BOW_U, "Dismantle", worn = false)) {
    on_item_option(item = Items.CRAWS_BOW_U, option = "Dismantle") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != Items.CRAWS_BOW_U) return@on_item_option
        player.inventory[slot] = null
        if (!player.inventory.add(Items.REVENANT_ETHER, RevenantBows.CRAWS_DISMANTLE_ETHER, assureFullInsertion = true).hasSucceeded()) {
            player.inventory[slot] = Item(Items.CRAWS_BOW_U)
            player.message("You don't have enough inventory space to do that.")
        }
    }
}

// Webweaver bow (u): Craw's bow (u) + Fangs of Venenatis, 85 Fletching (boostable), 0 experience.
on_item_on_item(item1 = Items.FANGS_OF_VENENATIS, item2 = Items.CRAWS_BOW_U) {
    if (player.skills.getCurrentLevel(Skills.FLETCHING) < RevenantBows.WEBWEAVER_FLETCHING) {
        player.message("You need a Fletching level of ${RevenantBows.WEBWEAVER_FLETCHING} to do that.")
        return@on_item_on_item
    }
    if (!player.inventory.contains(Items.FANGS_OF_VENENATIS) || !player.inventory.contains(Items.CRAWS_BOW_U)) return@on_item_on_item
    player.inventory.remove(Items.FANGS_OF_VENENATIS, 1)
    player.inventory.remove(Items.CRAWS_BOW_U, 1)
    player.inventory.add(Items.WEBWEAVER_BOW_U, 1)
}

// Venator bow: ancient essence up to 50,000, Check, Uncharge ("returns all essence").
listOf(Items.VENATOR_BOW, Items.VENATOR_BOW_UNCHARGED).forEach { bow ->
    on_item_on_item(item1 = Items.ANCIENT_ESSENCE, item2 = bow) {
        val slot = usedSlot(player, bow) ?: return@on_item_on_item
        val item = player.inventory[slot] ?: return@on_item_on_item
        val added = VenatorBow.essenceToAdd(item, player.inventory.getItemCount(Items.ANCIENT_ESSENCE))
        if (added <= 0) {
            player.message("Your bow cannot hold any more ancient essence.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.ANCIENT_ESSENCE, added)
        val charged = VenatorBow.withEssence(item, VenatorBow.essence(item) + added)
        player.inventory[slot] = charged
        player.message("Ancient essence: ${VenatorBow.essence(charged)}")
    }
}
bindCheck(Items.VENATOR_BOW) { "Ancient essence: ${VenatorBow.essence(it)}" }
if (bowHasOption(Items.VENATOR_BOW, "Uncharge", worn = false)) {
    on_item_option(item = Items.VENATOR_BOW, option = "Uncharge") {
        val slot = player.getInteractingItemSlot()
        val item = player.inventory[slot]?.takeIf { it.id == Items.VENATOR_BOW } ?: return@on_item_option
        if (!player.inventory.add(Items.ANCIENT_ESSENCE, VenatorBow.essence(item), assureFullInsertion = true).hasSucceeded()) {
            player.message("You don't have enough inventory space to do that.")
            return@on_item_option
        }
        player.inventory[slot] = VenatorBow.withEssence(item, 0)
    }
}

// Tonalztics of Ralos: sunfire splinters (1 charge each, up to 20,000), Check, Uncharge.
Tonalztics.ALL.forEach { weapon ->
    on_item_on_item(item1 = Items.SUNFIRE_SPLINTERS, item2 = weapon) {
        val slot = usedSlot(player, weapon) ?: return@on_item_on_item
        val item = player.inventory[slot] ?: return@on_item_on_item
        val added = Tonalztics.splintersToAdd(item, player.inventory.getItemCount(Items.SUNFIRE_SPLINTERS))
        if (added <= 0) {
            player.message("It cannot hold any more charges.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.SUNFIRE_SPLINTERS, added)
        val charged = Tonalztics.withCharges(item, Tonalztics.charges(item) + added)
        player.inventory[slot] = charged
        player.message("Charges: ${Tonalztics.charges(charged)}")
    }
}
bindCheck(Items.TONALZTICS_OF_RALOS) { "Charges: ${Tonalztics.charges(it)}" }
if (bowHasOption(Items.TONALZTICS_OF_RALOS, "Uncharge", worn = false)) {
    on_item_option(item = Items.TONALZTICS_OF_RALOS, option = "Uncharge") {
        val slot = player.getInteractingItemSlot()
        val item = player.inventory[slot]?.takeIf { it.id == Items.TONALZTICS_OF_RALOS } ?: return@on_item_option
        if (!player.inventory.add(Items.SUNFIRE_SPLINTERS, Tonalztics.charges(item), assureFullInsertion = true).hasSucceeded()) {
            player.message("You don't have enough inventory space to do that.")
            return@on_item_option
        }
        player.inventory[slot] = Tonalztics.withCharges(item, 0)
    }
}

// Scorching bow: tormented synapse on a magic longbow (u), 74 Fletching (boostable), 730 experience for the account's first bow, 73 after.
val SCORCHING_BOW_CREATED = gg.rsmod.game.model.attr.AttributeKey<Boolean>(persistenceKey = "osrs_scorching_bow_created")

on_item_on_item(item1 = Items.TORMENTED_SYNAPSE, item2 = Items.MAGIC_LONGBOW_U) {
    if (player.skills.getCurrentLevel(Skills.FLETCHING) < ScorchingBow.FLETCHING_LEVEL) {
        if (player.getCurrentLifepoints() < ScorchingBow.FAIL_MIN_HITPOINTS) {
            player.message(ScorchingBow.INJURED_MESSAGE)
            return@on_item_on_item
        }
        player.message(ScorchingBow.FAIL_MESSAGE)
        player.hit(damage = ScorchingBow.FAIL_DAMAGE)
        return@on_item_on_item
    }
    if (!player.inventory.contains(Items.TORMENTED_SYNAPSE) || !player.inventory.contains(Items.MAGIC_LONGBOW_U)) return@on_item_on_item
    player.inventory.remove(Items.TORMENTED_SYNAPSE, 1)
    player.inventory.remove(Items.MAGIC_LONGBOW_U, 1)
    player.inventory.add(Items.SCORCHING_BOW, 1)
    val first = player.attr[SCORCHING_BOW_CREATED] != true
    player.attr[SCORCHING_BOW_CREATED] = true
    player.addXp(Skills.FLETCHING, if (first) ScorchingBow.FIRST_EXPERIENCE else ScorchingBow.EXPERIENCE)
}

// "The process to create the scorching bow can be reversed via the 'Revert' option, returning only the tormented synapse": bound
// with the sourced confirmation dialogue for every synapse product in demonbane.plugin.kts (Demonbane.SYNAPSE_PRODUCTS).

// Venator bow (uncharged): "made by combining five venator shards" (shard option "Combine").
on_item_option(item = Items.VENATOR_SHARD, option = "Combine") {
    if (player.inventory.getItemCount(Items.VENATOR_SHARD) < VenatorBow.SHARDS_PER_BOW) {
        player.message("You need five venator shards to do that.")
        return@on_item_option
    }
    player.inventory.remove(Items.VENATOR_SHARD, VenatorBow.SHARDS_PER_BOW)
    player.inventory.add(Items.VENATOR_BOW_UNCHARGED, 1)
}

// Crystal bow: "When uncharged, it can also be converted back into a crystal weapon seed" (the charged bow's Revert is not
// sourced). Warns before reverting, per the owner's "crystal items need to give a warning when your try to revert"
// instruction (`cRYSTAL.rtf`, 2026-09-16).
if (bowHasOption(Items.CRYSTAL_BOW_OSRS_INACTIVE, "Revert", worn = false)) {
    on_item_option(item = Items.CRYSTAL_BOW_OSRS_INACTIVE, option = "Revert") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != Items.CRYSTAL_BOW_OSRS_INACTIVE) return@on_item_option
        player.queue {
            val choice = options("Yes, revert it.", "No, cancel.", title = "Revert this item back into a crystal seed?")
            if (choice != 1) return@queue
            if (player.inventory[slot]?.id != Items.CRYSTAL_BOW_OSRS_INACTIVE) return@queue
            player.inventory[slot] = Item(Items.CRYSTAL_SEED)
        }
    }
}

/*
 * Option census 2026-09-17c - bow options that listed no handler.
 */

// Active crystal bow "Revert" (OSRS Wiki "Crystal bow" infobox options; same seed and the same owner-required warning as the inactive bow).
if (bowHasOption(Items.CRYSTAL_BOW_OSRS, "Revert", worn = false)) {
    on_item_option(item = Items.CRYSTAL_BOW_OSRS, option = "Revert") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != Items.CRYSTAL_BOW_OSRS) return@on_item_option
        player.queue {
            val choice = options("Yes, revert it.", "No, cancel.", title = "Revert this item back into a crystal seed? Its charges are lost.")
            if (choice != 1) return@queue
            if (player.inventory[slot]?.id != Items.CRYSTAL_BOW_OSRS) return@queue
            player.inventory[slot] = Item(Items.CRYSTAL_SEED)
        }
    }
}

// Bow of Faerdhinen (c): "it can be reverted to an uncharged state at any time, but the shards used will be permanently lost in doing so".
if (bowHasOption(Items.BOW_OF_FAERDHINEN_C, "Uncharge", worn = false)) {
    on_item_option(item = Items.BOW_OF_FAERDHINEN_C, option = "Uncharge") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != Items.BOW_OF_FAERDHINEN_C) return@on_item_option
        player.queue {
            val choice =
                options("Yes, uncharge it.", "No, cancel.", title = "Revert the bow to its uncharged state? The crystal shards used are lost.")
            if (choice != 1) return@queue
            if (player.inventory[slot]?.id != Items.BOW_OF_FAERDHINEN_C) return@queue
            player.inventory[slot] = Item(Items.BOW_OF_FAERDHINEN_INACTIVE)
        }
    }
}

// Tonalztics of Ralos "Charge" (infobox options "Wield, Check, Charge, Uncharge"): the same splinter charging as using splinters on it.
Tonalztics.ALL.forEach { weapon ->
    if (bowHasOption(weapon, "Charge", worn = false)) {
        on_item_option(item = weapon, option = "Charge") {
            val slot = player.getInteractingItemSlot()
            val item = player.inventory[slot]?.takeIf { it.id == weapon } ?: return@on_item_option
            val held = player.inventory.getItemCount(Items.SUNFIRE_SPLINTERS)
            if (held <= 0) {
                player.message("You need sunfire splinters to charge it.")
                return@on_item_option
            }
            val added = Tonalztics.splintersToAdd(item, held)
            if (added <= 0) {
                player.message("It cannot hold any more charges.")
                return@on_item_option
            }
            player.inventory.remove(Items.SUNFIRE_SPLINTERS, added)
            val charged = Tonalztics.withCharges(item, Tonalztics.charges(item) + added)
            player.inventory[slot] = charged
            player.message("Charges: ${Tonalztics.charges(charged)}")
        }
    }
}

// Webweaver bow (u) "Dismantle" (OSRS Wiki "Fangs of Venenatis": "The uncharged weapon may be dismantled to separate" the two parts).
if (bowHasOption(Items.WEBWEAVER_BOW_U, "Dismantle", worn = false)) {
    on_item_option(item = Items.WEBWEAVER_BOW_U, option = "Dismantle") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != Items.WEBWEAVER_BOW_U) return@on_item_option
        if (player.inventory.freeSlotCount < 1) {
            player.message("You don't have enough inventory space to do that.")
            return@on_item_option
        }
        player.inventory[slot] = Item(Items.CRAWS_BOW_U)
        player.inventory.add(Items.FANGS_OF_VENENATIS, 1)
    }
}
