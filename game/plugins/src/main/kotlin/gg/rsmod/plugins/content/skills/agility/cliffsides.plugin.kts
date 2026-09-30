package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.Trace
import gg.rsmod.plugins.api.ext.getWildernessLevel

/**
 * The 2011 "Cliffside" locs of this 667 map that had no route (owner 2026-09-30: "2954, 3684, 0 Climb Cliffside" gave "Nothing
 * interesting happens"). Same shortcut system as the donor-sourced Trollheim rocks in sourced_shortcuts.plugin.kts (which keeps
 * 3803 / 3804 / 9303-9306 / 3748); these three ids are not in the 530/634 donors because the rocks were re-placed in 2011.
 * Placements from this cache (ObjectPlacementProbeTool name cliffside):
 *  - 34877 (2876,3619) (2893,3613) and 34878 (2874,3595) north of Death Plateau: 41 Agility, 8 xp - RuneScape Wiki "Cliffside
 *    (Death Plateau)" ("You need an Agility level of 41 in order to climb this cliffside.").
 *  - 34889 (2931,3680, Trollheim side) and 34878 (2951,3681, Wilderness side): 64 Agility, 8 xp, one-way into the Wilderness - RuneScape
 *    Wiki "Cliffside (Trollheim/Wilderness)" ("Climbing up here does not look safe." from the Wilderness), OSRS Wiki "Rocks (Wilderness,
 *    Trollheim)": "the final step of the shortcut is one-way and can not be used to leave the Wilderness".
 * Animations: the Trollheim rocks' own climb up / down (sourced_shortcuts ANIM_ROCKS_CLIMB_DOWN 740 / ANIM_HUMAN_CLIMBING_DOWN 1148).
 * WHERE IT LANDS: no donor has these tiles, so none is typed in: the player crosses the loc straight along the axis they approach it
 * on, over the cliff's blocked tiles in this cache's own collision, to the first walkable tile beyond. None within reach is a PROBLEM.
 */

data class Cliff(
    val level: Int,
    val xp: Double,
    val oneWayFromWilderness: Boolean = false,
)

/** 2009scape TrollheimPlugin CLIMB_UP 740 / CLIMB_DOWN 1148 (the same ids sourced_shortcuts uses for the Trollheim rocks). */
val CLIFF_CLIMB_UP = 740
val CLIFF_CLIMB_DOWN = 1148

val CLIFFS =
    mapOf(
        Triple(Objs.CLIFFSIDE_34878, 2951, 3681) to Cliff(64, 8.0, oneWayFromWilderness = true),
        Triple(Objs.CLIFFSIDE_34889, 2931, 3680) to Cliff(64, 8.0),
        Triple(Objs.CLIFFSIDE_34878, 2874, 3595) to Cliff(41, 8.0),
        Triple(Objs.CLIFFSIDE_34877, 2876, 3619) to Cliff(41, 8.0),
        Triple(Objs.CLIFFSIDE_34877, 2893, 3613) to Cliff(41, 8.0),
    )

listOf(Objs.CLIFFSIDE_34877, Objs.CLIFFSIDE_34878, Objs.CLIFFSIDE_34889).forEach { id ->
    shortcut(id, "Climb") {
        val obj = player.getInteractingGameObj()
        val cliff = CLIFFS[Triple(obj.id, obj.tile.x, obj.tile.z)]
        if (cliff == null) {
            Trace.problem(Trace.Cat.PLUGIN) { "cliffside ${obj.id} at ${obj.tile.x},${obj.tile.z},${obj.tile.height} has no sourced rule" }
            player.message("Nothing interesting happens.")
            return@shortcut
        }
        if (cliff.oneWayFromWilderness && player.tile.getWildernessLevel() > 0) {
            player.message("Climbing up here does not look safe.")
            return@shortcut
        }
        if (!player.hasAgility(cliff.level, "You need an Agility level of ${cliff.level} in order to climb this cliffside.")) return@shortcut
        val dest = crossingLanding(world, obj, player.tile)
        if (dest == null) {
            Trace.problem(Trace.Cat.PLUGIN) { "cliffside ${obj.id} at ${obj.tile.x},${obj.tile.z}: no walkable landing across from ${player.tile.x},${player.tile.z}" }
            player.message("You can't climb the cliffside from here.")
            return@shortcut
        }
        obstacle { target ->
            player.faceTile(target.tile)
            // Into the Wilderness is the way down (the one-way drop); the Death Plateau rocks use the climb-up.
            val anim = if (dest.getWildernessLevel() > 0) CLIFF_CLIMB_DOWN else CLIFF_CLIMB_UP
            player.animate(anim)
            exactMove(player, dest, delay = 120, direction = Direction.between(player.tile, dest))
            player.addXp(Skills.AGILITY, cliff.xp)
        }
    }
}
