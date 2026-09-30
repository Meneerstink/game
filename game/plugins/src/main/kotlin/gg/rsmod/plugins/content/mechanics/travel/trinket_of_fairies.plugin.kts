package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.content.magic.TeleportType

/**
 * Trinket of fairies (OSRS item 28564, local 23822 from the OSRS import; cache options Fairy-ring, Spirit-tree, Tool-leprechauns,
 * Last-destination). OSRS Wiki: "allows players to remotely access fairy rings, spirit trees, and tool leprechauns ... The trinket cannot
 * be used beyond level 30 Wilderness." Everything goes through the shared services - [FairyRings] (the real dial 734 + travel log 735,
 * the same last destination as the rings) and [SpiritTrees] - with the trinket's own teleport types (TRINKET_FAIRY /
 * TRINKET_SPIRIT_TREE: Wilderness limit 30), so Tele Block, the Deadman countdown and instance / minigame locks apply exactly as for
 * every other teleport. ADAPTED: no Dramen staff is needed with the trinket (the trinket is the fairy magic).
 * OPEN: Tool-leprechauns - this server has no tool-leprechaun destination list yet (unhandled, traced as a PROBLEM when clicked).
 */

val TRINKET_WILDERNESS_LIMIT = 30

/** The early refusals, before a menu opens; the teleport itself is checked again by the shared gate. */
fun trinketUsable(player: Player): Boolean {
    if (player.tile.getWildernessLevel() > TRINKET_WILDERNESS_LIMIT) {
        player.message("A mysterious force blocks your teleport spell!")
        player.message("You can't use this teleport after level $TRINKET_WILDERNESS_LIMIT wilderness.")
        return false
    }
    if (player.timers.has(TELEBLOCK_TIMER)) {
        player.message("A magical force has stopped you from teleporting.")
        return false
    }
    return true
}

on_item_option(Items.TRINKET_OF_FAIRIES, "fairy-ring") {
    if (!trinketUsable(player)) return@on_item_option
    FairyRings.open(player, FairyRings.Source.TRINKET)
}

on_item_option(Items.TRINKET_OF_FAIRIES, "spirit-tree") {
    if (!trinketUsable(player)) return@on_item_option
    player.queue { SpiritTrees.travel(this, player, TeleportType.TRINKET_SPIRIT_TREE, atTree = false) }
}

on_item_option(Items.TRINKET_OF_FAIRIES, "last-destination") {
    if (!trinketUsable(player)) return@on_item_option
    val code = player.attr[FairyRings.LAST_CODE]
    if (code == null || FairyRings.CODES[code] == null) {
        player.message("You haven't travelled by fairy ring yet.")
        return@on_item_option
    }
    FairyRings.travel(player, code, FairyRings.Source.TRINKET)
}
