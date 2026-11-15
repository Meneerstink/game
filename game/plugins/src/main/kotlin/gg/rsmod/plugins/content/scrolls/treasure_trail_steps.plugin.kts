package gg.rsmod.plugins.content.scrolls

// A subtype is enabled only after its handler and source/native assets exist.
TreasureTrailStepCatalogue.current.rows.filter { TreasureTrailBasicSteps.available(world, it) }.forEach {
    ClueScrollManager.registerStep(TreasureTrailSourceStep(it))
}
on_world_init {
    world.plugins.bindNpcOverride { player, npc, option -> TreasureTrailBasicSteps.talk(player, npc, option) }
    world.plugins.bindObjectOverride { player, obj, option -> TreasureTrailSearchTargets.attempt(player, obj, option) }
}
on_npc_killed { player, npc -> TreasureTrailBasicSteps.killed(player, npc) }
