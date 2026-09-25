package gg.rsmod.plugins.content.mechanics.npcwalk

on_global_npc_spawn {
    NpcPatrol.start(npc)
}

on_timer(NpcPatrol.TIMER) {
    NpcPatrol.tick(npc)
    npc.timers[NpcPatrol.TIMER] = 1
}
