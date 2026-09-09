package gg.rsmod.plugins.content.areas.godwars

/**
 * Binds the four generals' combat scripts (see [GodWarsGenerals]) and spawns their bodyguards.
 *
 * Bodyguard combat stats and drops come from the bulk data tables (OSRS/2007-era values matched by
 * name + combat level). Placement: the cache carries no npc spawns, so each bodyguard is placed
 * two tiles from its general's spawn tile inside the chamber, on the same plane, with the same
 * walk radius the general uses.
 */
on_npc_combat(*GodWarsGenerals.Graardor.ids) {
    npc.queue { GodWarsGenerals.Graardor.handleSpecialCombat(this) }
}

on_npc_combat(*GodWarsGenerals.Zilyana.ids) {
    npc.queue { GodWarsGenerals.Zilyana.handleSpecialCombat(this) }
}

on_npc_combat(*GodWarsGenerals.Kril.ids) {
    npc.queue { GodWarsGenerals.Kril.handleSpecialCombat(this) }
}

on_npc_combat(*GodWarsGenerals.Kreearra.ids) {
    npc.queue { GodWarsGenerals.Kreearra.handleSpecialCombat(this) }
}

data class Bodyguards(
    val generalX: Int,
    val generalZ: Int,
    val height: Int,
    val npcs: List<Int>,
)

listOf(
    // General Graardor (2872, 5358, 2): Sergeant Strongstack, Steelwill, Grimspike
    Bodyguards(2872, 5358, 2, listOf(Npcs.SERGEANT_STRONGSTACK, Npcs.SERGEANT_STEELWILL, Npcs.SERGEANT_GRIMSPIKE)),
    // Kree'arra (2832, 5302, 2): Wingman Skree, Flockleader Geerin, Flight Kilisa
    Bodyguards(2832, 5302, 2, listOf(Npcs.WINGMAN_SKREE, Npcs.FLOCKLEADER_GEERIN, Npcs.FLIGHT_KILISA)),
    // Commander Zilyana (2900, 5268, 0): Starlight, Growler, Bree
    Bodyguards(2900, 5268, 0, listOf(Npcs.STARLIGHT, Npcs.GROWLER, Npcs.BREE)),
    // K'ril Tsutsaroth (2926, 5322, 2): Tstanon Karlak, Zakl'n Gritch, Balfrug Kreeyath
    Bodyguards(2926, 5322, 2, listOf(Npcs.TSTANON_KARLAK, Npcs.ZAKLN_GRITCH, Npcs.BALFRUG_KREEYATH)),
).forEach { group ->
    val offsets = listOf(-2 to 2, 2 to 2, 0 to -2)
    group.npcs.forEachIndexed { index, id ->
        val (dx, dz) = offsets[index]
        spawn_npc(
            npc = id,
            x = group.generalX + dx,
            z = group.generalZ + dz,
            height = group.height,
            walkRadius = 5,
            direction = Direction.NORTH,
            static = false,
        )
    }
}
