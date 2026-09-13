package gg.rsmod.plugins.content.areas.godwars

/**
 * Installs the four generals' boss mechanics and spawns their bodyguards.
 *
 * RCV-005 root cause: the generals used hand-written attack scripts; they now fight through the shared
 * data-driven attack model (NpcAttacks, Void bandos/saradomin/zamorak/armadyl `*.combat.toml`) in the generic
 * combat cycle, and only their real mechanics are hooks (see [GodWarsGenerals.installAttackHooks]).
 *
 * Bodyguard combat stats and drops come from the bulk data tables (OSRS/2007-era values matched by
 * name + combat level). Placement: the cache carries no npc spawns, so each bodyguard is placed
 * two tiles from its general's spawn tile inside the chamber, on the same plane, with the same
 * walk radius the general uses.
 */
GodWarsGenerals.installAttackHooks()

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
