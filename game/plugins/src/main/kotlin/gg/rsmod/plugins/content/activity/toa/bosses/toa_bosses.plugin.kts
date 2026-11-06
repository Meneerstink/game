package gg.rsmod.plugins.content.activity.toa.bosses

// No ambient spawns: the raid adapter or ::toaboss owns encounter creation and membership.
listOf(AkkhaCombat).forEach { script ->
    script.ids.forEach { id ->
        set_combat_def(id, AkkhaCombat.STATS.definition(object : ToaEncounterContext {
            override val players = emptyList<gg.rsmod.game.model.entity.Player>()
            override val partySize = 1
            override val raidLevel = 0
            override val pathLevel = 0
            override val invocations = emptySet<String>()
        }))
        on_npc_combat(npc = id) { npc.queue { script.handleSpecialCombat(this) } }
        on_npc_pre_death(id) { ToaEncounter.stop(npc) }
    }
}
