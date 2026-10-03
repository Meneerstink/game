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
 * The climb is the Trollheim rocks' own (ShortcutSupport.climbCliff, 2026-10-03): the map dump of each placement shows the loc filling
 * a gap between low and high ground, so the crossing is a fixed straight lane through the loc across that height step - walk to
 * the tile in front, climb down with human_climbing_down 1148 or up with the "climbing" render (BAS 0, Void Trollheim). The old
 * computed landing slid from wherever the click left the player, over 2-6 blocked tiles and up to two lanes sideways (conform
 * traversal 2026-10-03: noclip through the neighbouring cliff pieces and walls).
 */

data class Cliff(
    val level: Int,
    val xp: Double,
    val oneWayFromWilderness: Boolean = false,
)

val CLIFFS =
    mapOf(
        // Owner decision 2026-09-30: climbable from both sides here (OSRS/RS make it one-way into the Wilderness; ADAPTED on request).
        Triple(Objs.CLIFFSIDE_34878, 2951, 3681) to Cliff(64, 8.0),
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
        obstacle { target ->
            // human_climbing_down 1148, the Trollheim rocks' own (Void Trollheim.kt).
            if (climbCliff(player, target, 1148)) player.addXp(Skills.AGILITY, cliff.xp)
        }
    }
}
