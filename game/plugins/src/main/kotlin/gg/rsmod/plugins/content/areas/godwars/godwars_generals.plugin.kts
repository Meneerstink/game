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
// no audio fields, so this pre-death hook restores only the unambiguous death events without
// replacing the generic attack path. Missing source keys (Steelwill, Starlight and Tstanon) stay
// source-blocked rather than being filled with a guessed generic sound.
val BODYGUARD_DEATH_SOUNDS = mapOf(
    Npcs.SERGEANT_STRONGSTACK to 471, // goblin_death
    Npcs.SERGEANT_GRIMSPIKE to 471, // goblin_death
    Npcs.WINGMAN_SKREE to 3854, // aviansie_death
    Npcs.FLOCKLEADER_GEERIN to 3854, // aviansie_death
    Npcs.FLIGHT_KILISA to 3854, // aviansie_death
    Npcs.GROWLER to 3867,
    Npcs.BREE to 3827,
    Npcs.BALFRUG_KREEYATH to 403,
    Npcs.ZAKLN_GRITCH to 403,
)

BODYGUARD_DEATH_SOUNDS.forEach { (id, sound) ->
    on_npc_pre_death(id) {
        npc.world.spawn(gg.rsmod.game.model.entity.AreaSound(tile = npc.tile, id = sound, radius = 10, volume = 1))
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
