package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices

/*
 * OSRS-IMPORT Ava's assembler (and every cape that carries it: Masori assembler, the assembler max capes and their (l) items): the
 * Commune option and the metal attraction it switches (rules, table and sources in AvasAssembler).
 */
val ATTRACTION_TIMER = TimerKey()
val LAST_PICKUP_TILE = AttributeKey<Tile>()

fun assemblerHasOption(
    itemId: Int,
    option: String,
    worn: Boolean,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, itemId)
    return (if (worn) def.equipmentMenu else def.inventoryMenu).any { it.equals(option, ignoreCase = true) }
}

/** The OSRS Commune chatbox flow (plain message -> Yes/No option -> plain message), texts in [AvasAssembler.Commune]. */
fun commune(player: Player) {
    player.queue {
        val stopped = player.attr[AvasAssembler.GATHERING_STOPPED] == true
        val text = AvasAssembler.Commune.forState(stopped)
        messageBox(*text.intro)
        if (options("Yes", "No", title = text.question) != 1) return@queue
        player.attr[AvasAssembler.GATHERING_STOPPED] = !stopped
        messageBox(*text.confirm)
    }
}

// The Accumulator max cape (night run 2026-09-19) carries the same OSRS "Commune" option (inventory and worn, pinned OSRS cache).
AvasDevices.COMMUNE_DEVICES.forEach { device ->
    if (assemblerHasOption(device, "Commune", worn = false)) on_item_option(item = device, option = "Commune") { commune(player) }
    if (assemblerHasOption(device, "Commune", worn = true)) on_equipment_option(item = device, option = "Commune") { commune(player) }
}

on_login {
    player.timers[ATTRACTION_TIMER] = AvasAssembler.INTERVAL_TICKS
}

on_timer(ATTRACTION_TIMER) {
    player.timers[ATTRACTION_TIMER] = AvasAssembler.INTERVAL_TICKS
    if (player.getEquipment(EquipmentType.CAPE)?.id !in AvasDevices.ASSEMBLERS) return@on_timer
    if (player.attr[AvasAssembler.GATHERING_STOPPED] == true || AvasDevices.interferes(player)) return@on_timer
    val last = player.attr[LAST_PICKUP_TILE]
    if (last != null && last.height == player.tile.height && last.getDistance(player.tile) < AvasAssembler.MIN_TILES_MOVED) return@on_timer
    player.attr[LAST_PICKUP_TILE] = player.tile

    val item = AvasAssembler.attracted(world.random(AvasAssembler.TOTAL_WEIGHT - 1))
    // Arrows join a matching (or empty) ammunition slot; everything else, and arrows that do not match, go to the inventory.
    val ammo = player.getEquipment(EquipmentType.AMMO)
    val arrow = item == Items.MITHRIL_ARROW || item == Items.ADAMANT_ARROW
    if (arrow && (ammo == null || (ammo.id == item && ammo.amount < Int.MAX_VALUE))) {
        player.equipment[EquipmentType.AMMO.id] = Item(item, (ammo?.amount ?: 0) + 1)
        return@on_timer
    }
    if (!player.inventory.add(item, 1, assureFullInsertion = true).hasSucceeded()) {
        player.message(AvasAssembler.FULL_MESSAGE)
    }
}
