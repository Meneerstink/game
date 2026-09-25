package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/*
 * Fairy rings (rules and sources in [FairyRings]). Every loc named "Fairy ring" whose option is "Use" opens the dial - an object
 * FALLBACK, so the Zanaris ring 12094 keeps its own route to the Lumbridge shed. The travel log (735) is not opened: it would take the
 * inventory tab's place and only lists codes.
 */

val USED_RING = gg.rsmod.game.model.attr.AttributeKey<gg.rsmod.game.model.Tile>()

fun wieldsFairyMagic(player: Player): Boolean =
    player.getEquipment(EquipmentType.WEAPON)?.id in setOf(Items.DRAMEN_STAFF, Items.LUNAR_STAFF)

world.plugins.bindObjectFallback { player, obj, opt ->
    val def = player.world.definitions.get(ObjectDef::class.java, obj.getTransform(player))
    if (!def.name.equals("Fairy ring", ignoreCase = true) || def.options.getOrNull(opt - 1)?.lowercase() != "use") {
        return@bindObjectFallback false
    }
    if (!wieldsFairyMagic(player)) {
        player.message("The fairy ring only works for those who wield fairy magic.")
        return@bindObjectFallback true
    }
    player.attr[USED_RING] = obj.tile
    FairyRings.DIAL_VARBITS.forEach { player.setVarbit(it, 0) }
    player.openInterface(FairyRings.DIAL_INTERFACE, InterfaceDestination.MAIN_SCREEN)
    true
}

// Rotate clockwise / anticlockwise: dial n uses buttons 21 + 2n (clockwise) and 22 + 2n (anticlockwise).
(1..3).forEach { dial ->
    val varbit = FairyRings.DIAL_VARBITS[dial - 1]
    on_button(FairyRings.DIAL_INTERFACE, 21 + 2 * dial) { player.setVarbit(varbit, FairyRings.turn(player.getVarbit(varbit), 1)) }
    on_button(FairyRings.DIAL_INTERFACE, 22 + 2 * dial) { player.setVarbit(varbit, FairyRings.turn(player.getVarbit(varbit), -1)) }
}

on_button(FairyRings.DIAL_INTERFACE, FairyRings.TELEPORT_BUTTON) {
    val code = FairyRings.code(FairyRings.DIAL_VARBITS.map { player.getVarbit(it) }.toIntArray())
    player.closeInterface(FairyRings.DIAL_INTERFACE)
    if (!wieldsFairyMagic(player)) {
        player.message("The fairy ring only works for those who wield fairy magic.")
        return@on_button
    }
    val destination = FairyRings.CODES[code]?.tile ?: FairyRings.ZANARIS
    player.canTeleport(TeleportType.FAIRY) {
        player.teleport(destination, TeleportType.FAIRY)
    }
}
