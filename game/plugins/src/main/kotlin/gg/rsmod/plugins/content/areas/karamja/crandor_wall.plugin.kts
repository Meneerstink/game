package gg.rsmod.plugins.content.areas.karamja

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.quests.foundation.bracket.DragonSlayer
import gg.rsmod.plugins.content.skills.agility.darkanForceMove
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.walkToTile

/*
 * PROJECT48 run 4 O20: the secret wall 2606 between the Karamja Volcano dungeon and Elvarg's side of the Crandor dungeon (2836,9600,
 * a wall on the south edge of that tile). OSRS Wiki 'Wall (Crandor and Karamja Dungeon)': "It cannot be opened until Dragon Slayer I
 * is partially completed after sailing to Crandor for the first time. The wall can then be opened from either the cave under Crandor
 * or from under the volcano on Karamja" (and 'Dragon Slayer I': if you teleport out after sailing, "the shortcut will unlock by
 * itself") - so: open once the Ned voyage step is done (stage 7 = the Elvarg step, or later / completed). This is the only way back
 * to Crandor after the quest (wiki 'Crandor'). Without that the engine's "Nothing interesting happens." (no OSRS line found).
 */
val CRANDOR_WALL = 2606
val WALL_X = 2836
val WALL_Z = 9600

fun sailedToCrandor(player: Player): Boolean = DragonSlayer.stage(player) >= 7

shortcut(CRANDOR_WALL, "Open") {
    if (!sailedToCrandor(player)) {
        player.message("Nothing interesting happens.")
        return@shortcut
    }
    obstacle {
        val fromSouth = player.tile.z < WALL_Z
        val front = Tile(WALL_X, if (fromSouth) WALL_Z - 1 else WALL_Z, 0)
        val landing = Tile(WALL_X, if (fromSouth) WALL_Z else WALL_Z - 1, 0)
        walkToTile(player, front)
        darkanForceMove(player, landing, 25, 45, 819)
    }
}
