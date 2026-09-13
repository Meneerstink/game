package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.plugins.api.cfg.Npcs

/**
 * RCV-011 Q-043-a root cause: every general's three bodyguards were independent world spawns with their own respawn
 * timers (150 or 60 ticks, depending on the bulk table), so a chamber refilled guard by guard while the general was
 * still dead, and killing a general never brought his room back as one encounter.
 *
 * Both donors tie the bodyguards to the general instead: Void `GeneralGraardor`/`KreeArra`/`CommanderZilyana`/
 * `KrilTsutsaroth` add each missing bodyguard when the general spawns; Novite 667 `GodWarMinion.setRespawnTask`
 * never respawns a minion on its own and the general's respawn calls `GodWarsBosses.respawn<faction>Minions`.
 * Tiles are Void's (heights: Saradomin's chamber is on plane 0, as the general's spawn). This roster serves all four
 * chambers; `godwars_generals.plugin.kts` spawns from it on every general spawn and respawn.
 *
 * SOURCE_BLOCKED: neither donor resets a chamber when it becomes empty.
 */
object GodWarsBodyguards {
    data class Bodyguard(
        val id: Int,
        val x: Int,
        val z: Int,
        val height: Int,
    )

    val BY_GENERAL: Map<Int, List<Bodyguard>> =
        mapOf(
            Npcs.GENERAL_GRAARDOR to
                listOf(
                    Bodyguard(Npcs.SERGEANT_STRONGSTACK, 2866, 5358, 2),
                    Bodyguard(Npcs.SERGEANT_STEELWILL, 2872, 5352, 2),
                    Bodyguard(Npcs.SERGEANT_GRIMSPIKE, 2868, 5362, 2),
                ),
            Npcs.KREEARRA to
                listOf(
                    Bodyguard(Npcs.FLIGHT_KILISA, 2833, 5297, 2),
                    Bodyguard(Npcs.WINGMAN_SKREE, 2840, 5303, 2),
                    Bodyguard(Npcs.FLOCKLEADER_GEERIN, 2828, 5299, 2),
                ),
            Npcs.COMMANDER_ZILYANA to
                listOf(
                    Bodyguard(Npcs.STARLIGHT, 2903, 5260, 0),
                    Bodyguard(Npcs.BREE, 2902, 5270, 0),
                    Bodyguard(Npcs.GROWLER, 2898, 5262, 0),
                ),
            Npcs.KRIL_TSUTSAROTH to
                listOf(
                    Bodyguard(Npcs.BALFRUG_KREEYATH, 2921, 5319, 2),
                    Bodyguard(Npcs.TSTANON_KARLAK, 2932, 5328, 2),
                    Bodyguard(Npcs.ZAKLN_GRITCH, 2919, 5327, 2),
                ),
        )

    /** Walk radius the previous independent spawns used; kept unchanged. */
    const val WALK_RADIUS = 5

    /** The bodyguards of [generalId] that are not currently in the world and must be added now. */
    fun missing(
        generalId: Int,
        present: (Int) -> Boolean,
    ): List<Bodyguard> = BY_GENERAL[generalId].orEmpty().filterNot { present(it.id) }
}
