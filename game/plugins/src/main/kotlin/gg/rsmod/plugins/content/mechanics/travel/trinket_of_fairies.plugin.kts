package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * Trinket of fairies (OSRS item 28564, local 23822 from the OSRS import; cache options Fairy-ring, Spirit-tree, Tool-leprechauns,
 * Last-destination). OSRS Wiki: "allows players to remotely access fairy rings, spirit trees, and tool leprechauns ... The trinket cannot
 * be used beyond level 30 Wilderness." Everything goes through the shared services - [FairyRings] (the real dial 734 + travel log 735,
 * the same last destination as the rings) and [SpiritTrees] - with the trinket's own teleport types (TRINKET_FAIRY /
 * TRINKET_SPIRIT_TREE: Wilderness limit 30), so Tele Block, the Deadman countdown and instance / minigame locks apply exactly as for
 * every other teleport. ADAPTED: no Dramen staff is needed with the trinket (the trinket is the fairy magic).
 * Tool-leprechauns (owner decision 2026-09-30, ADAPTED: no destination list is published): the tool leprechauns that actually stand in
 * this world (live npcs named "Tool leprechaun", no instances), one per spot, named after the farming patch next to them (the Patch
 * table's own objects in this cache), in pages of seven.
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

on_item_option(Items.TRINKET_OF_FAIRIES, "tool-leprechauns") {
    if (!trinketUsable(player)) return@on_item_option
    val spots = ToolLeprechauns.spots(world)
    if (spots.isEmpty()) {
        player.message("There are no tool leprechauns to travel to.")
        return@on_item_option
    }
    player.queue {
        var page = 0
        while (true) {
            val slice = spots.drop(page * 7).take(7)
            val more = spots.size > (page + 1) * 7
            val labels = slice.map { it.first } + if (more) listOf("More...") else emptyList()
            val choice = options(*labels.toTypedArray(), title = "Which tool leprechaun?")
            if (choice < 1 || choice > labels.size) return@queue
            if (more && choice == labels.size) {
                page++
                continue
            }
            val target = slice[choice - 1].second
            // Land next to the leprechaun, not on it.
            val landing = listOf(target.transform(1, 0), target.transform(-1, 0), target.transform(0, 1), target.transform(0, -1))
                .firstOrNull { !world.collision.isClipped(it) } ?: target
            player.canTeleport(TeleportType.TRINKET_FAIRY) {
                player.teleport(landing, TeleportType.TRINKET_FAIRY)
            }
            return@queue
        }
    }
}
