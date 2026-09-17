package gg.rsmod.plugins.content.mechanics.pvp

/**
 * R03.1/R03.3: build [BankZones] once static map objects and plugin-spawned objects (e.g.
 * home's bank chest) are all in [world.chunks] but before players can log in and start
 * fighting. Runs through the same on_world_init hook R04.2's NpcCensus documents as the
 * correctly-ordered startup point.
 */
on_world_init {
    println(BankZones.init(world))
    println(BossAreas.init(world))
    println(DangerSigns.place(world))
}
