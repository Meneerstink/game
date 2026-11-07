package gg.rsmod.plugins.content.activity.toa.bosses

// No ambient spawns: the raid adapter or ::toaboss owns encounter creation and membership.
val encounters = listOf(
    Triple("akkha", AkkhaCombat, ToaEncounter.Definition(ToaBossNpcs.AKKHA, AkkhaCombat.STATS, AkkhaCombat::Session)),
    Triple("baba", BabaCombat, ToaEncounter.Definition(ToaBossNpcs.BABA, BabaCombat.STATS, BabaCombat::Session)),
    Triple("zebak", ZebakCombat, ToaEncounter.Definition(ToaBossNpcs.ZEBAK, ZebakCombat.STATS, ZebakCombat::Session)),
)
encounters.forEach { (name, script, definition) ->
    ToaEncounter.definitions[name] = definition
    script.ids.distinct().forEach { id ->
        set_combat_def(id, definition.stats.definition(object : ToaEncounterContext {
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

// Their encounter drives minions/hazards; suppress the generic NPC auto-retaliation loop.
listOf(ToaBossNpcs.AKKHA_SHADOW, ToaBossNpcs.UNSTABLE_ORB, 11800, 11801, 11802, 11803,
    ToaBossNpcs.BABOON, 11782, 11783, 11784,
    ToaBossNpcs.ZEBAK_TAIL, 11735, 11736, 11737, 11738, 11741, 11742, 11743).mapNotNull(ToaBossNpcs::local).distinct().forEach { id ->
    on_npc_combat(npc = id) { }
}

ToaBossFx.loc(45509)?.let { id -> on_obj_option(obj = id, option = "Climb-up") {
    (ToaEncounter.forPlayer(player) as? ZebakCombat.Session)?.climbUp(player)
} }

ToaBossNpcs.local(11735)?.let { id ->
    listOf("Push", "Pull", "Hit").forEach { option -> on_npc_option(npc = id, option = option) {
        val session = npc.attr[ToaEncounter.MINION_SESSION] as? ZebakCombat.Session
        if (session != null && session.eligible(player) && npc in session.jugs) {
            if (option == "Hit") session.shatterJug(npc)
            else {
                val direction = gg.rsmod.game.model.Direction.between(player.tile, npc.tile)
                // ONZEKER T18: published2022 change says2tiles further; rolling6tiles uses4+2fallback.
                session.moveJug(npc, if (option == "Pull") direction.getOpposite() else direction, 6)
            }
        }
    } }
}
