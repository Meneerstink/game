package gg.rsmod.plugins.content.scrolls

// Easy, medium and elite use Uri. Hard/78 double-agent steps remain excluded until their encounter is complete.
TreasureTrailStepCatalogue.current.rows.filter { TreasureTrailEmotes.available(world,it) }.forEach {
    ClueScrollManager.registerStep(TreasureTrailSourceStep(it))
}
val uriCycle = TimerKey()
on_world_init {
    TreasureTrailUri.current.install(world)
    world.plugins.bindNpcOverride { player,npc,option -> TreasureTrailEmotes.talk(player,npc,option) }
    world.timers[uriCycle] = 1
}
on_timer(uriCycle) { TreasureTrailEmotes.cycle(); world.timers[uriCycle] = 1 }
on_logout { TreasureTrailEmotes.remove(player) }
on_player_death { TreasureTrailEmotes.remove(player) }
