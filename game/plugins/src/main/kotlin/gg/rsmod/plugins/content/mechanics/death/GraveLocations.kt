package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.areas.godwars.GodWars

/**
 * Where a PvM death's gravestone appears (OSRS "Death Changes", 25 June 2020):
 *  - "In instanced areas, the Gravestone will aim to appear outside the instance where its owner can loot it" - the
 *    instance's exit tile, the same tile the player respawns at when they die inside it;
 *  - "if you die inside one of the four God Wars Dungeon boss rooms, your Grave will appear outside" - the tile in front
 *    of the chamber door;
 *  - anywhere else, where the player died.
 */
object GraveLocations {
    private val GWD_BOSS_ROOMS =
        GodWars.God.values().filter { it.displayName in setOf("Bandos", "Armadyl", "Saradomin", "Zamorak") }

    fun graveTile(player: Player): Tile {
        val tile = player.tile
        player.world.instanceAllocator.getMap(tile)?.let { return it.exitTile }
        GWD_BOSS_ROOMS.firstOrNull { it.inChamber(tile) }?.let { return it.chamberExit }
        return tile
    }
}
