package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.fs.def.ObjectDef

/*
 * Fairy rings (rules and sources in [FairyRings]). Every loc named "Fairy ring" whose option is "Use" opens the dial - an object
 * FALLBACK, so the Zanaris ring 12094 keeps its own route to the Lumbridge shed. Owner 2026-09-30: one service ([FairyRings.open] /
 * [FairyRings.travel]) serves the rings and the Trinket of fairies; the travel log 735 opens in the inventory tab beside the dial, the
 * dials start on the last destination, and the last code and the visited codes persist.
 */

val USED_RING = gg.rsmod.game.model.attr.AttributeKey<gg.rsmod.game.model.Tile>()

world.plugins.bindObjectFallback { player, obj, opt ->
    val def = player.world.definitions.get(ObjectDef::class.java, obj.getTransform(player))
    if (!def.name.equals("Fairy ring", ignoreCase = true) || def.options.getOrNull(opt - 1)?.lowercase() != "use") {
        return@bindObjectFallback false
    }
    player.attr[USED_RING] = obj.tile
    val house = gg.rsmod.plugins.content.areas.poh.PlayerHouse.isSafeTile(obj.tile)
    FairyRings.open(player, if (house) FairyRings.Source.HOUSE else FairyRings.Source.RING)
    true
}

// Rotate clockwise / anticlockwise: dial n uses buttons 21 + 2n (clockwise) and 22 + 2n (anticlockwise).
(1..3).forEach { dial ->
    val varbit = FairyRings.DIAL_VARBITS[dial - 1]
    on_button(FairyRings.DIAL_INTERFACE, 21 + 2 * dial) { player.setVarbit(varbit, FairyRings.turn(player.getVarbit(varbit), 1)) }
    on_button(FairyRings.DIAL_INTERFACE, 22 + 2 * dial) { player.setVarbit(varbit, FairyRings.turn(player.getVarbit(varbit), -1)) }
}

on_button(FairyRings.DIAL_INTERFACE, FairyRings.TELEPORT_BUTTON) {
    val code = FairyRings.dialledCode(player)
    val source = player.attr[FairyRings.SOURCE] ?: FairyRings.Source.RING
    player.closeInterface(FairyRings.DIAL_INTERFACE)
    FairyRings.travel(player, code, source)
}

// Travel log "Re-sort list": the log's own client script (957, varp 816 trigger) re-sorts on this varbit.
on_button(FairyRings.TRAVEL_LOG_INTERFACE, FairyRings.RESORT_BUTTON) {
    player.setVarbit(FairyRings.RESORT_VARBIT, 1 - player.getVarbit(FairyRings.RESORT_VARBIT))
}

// Travel log entries 14..77 (only the visited ones are shown): clicking one puts its code on the dials, as Void's travel_log:*.
(FairyRings.LOG_FIRST_COMPONENT..FairyRings.LOG_LAST_COMPONENT).forEach { component ->
    on_button(FairyRings.TRAVEL_LOG_INTERFACE, component) { FairyRings.dialFromLog(player, component) }
}

// Closing the dial gives the inventory tab back (the travel log sat in its place).
on_interface_close(FairyRings.DIAL_INTERFACE) {
    player.attr.remove(FairyRings.SOURCE)
    if (player.interfaces.isVisible(FairyRings.TRAVEL_LOG_INTERFACE)) {
        player.closeInterface(interfaceId = FairyRings.TRAVEL_LOG_INTERFACE)
    }
    player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
    player.inventory.dirty = true
}
