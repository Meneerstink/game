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

// Void 667 supplies these bodyguard death sounds. The shared NPC combat definition currently has
// no audio fields, so the narrow overlay keeps the generic attack path and lifecycle intact.
GodWarsMinionAudio.deathSounds().forEach { (id, _) ->
    on_npc_pre_death(id) {
        GodWarsMinionAudio.playDeath(npc)
    }
}

data class BodyguardSpawn(
    val npc: Int,
    val x: Int,
    val z: Int,
    val height: Int,
)

listOf(
    // Exact Void 667 GWD spawn tiles; these are independent world spawns and do not despawn with a boss.
    BodyguardSpawn(Npcs.SERGEANT_STRONGSTACK, 2866, 5358, 2),
    BodyguardSpawn(Npcs.SERGEANT_STEELWILL, 2872, 5352, 2),
    BodyguardSpawn(Npcs.SERGEANT_GRIMSPIKE, 2868, 5362, 2),
    BodyguardSpawn(Npcs.WINGMAN_SKREE, 2840, 5303, 2),
    BodyguardSpawn(Npcs.FLOCKLEADER_GEERIN, 2828, 5299, 2),
    BodyguardSpawn(Npcs.FLIGHT_KILISA, 2833, 5297, 2),
    BodyguardSpawn(Npcs.STARLIGHT, 2903, 5260, 0),
    BodyguardSpawn(Npcs.BREE, 2902, 5270, 0),
    BodyguardSpawn(Npcs.GROWLER, 2898, 5262, 0),
    BodyguardSpawn(Npcs.BALFRUG_KREEYATH, 2921, 5319, 2),
    BodyguardSpawn(Npcs.TSTANON_KARLAK, 2932, 5328, 2),
    BodyguardSpawn(Npcs.ZAKLN_GRITCH, 2919, 5327, 2),
).forEach { spawn ->
    spawn_npc(
        npc = spawn.npc,
        x = spawn.x,
        z = spawn.z,
        height = spawn.height,
        walkRadius = 5,
        direction = Direction.NORTH,
        static = false,
    )
}
