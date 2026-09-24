package gg.rsmod.plugins.content.mechanics.npcwalk

import gg.rsmod.game.model.timer.TimerKey

/** The random-walk timer of npc_random_walk.plugin.kts, shared so a world edit can start an npc roaming live. */
object NpcRandomWalk {
    val TIMER = TimerKey()
    val DELAY = 15..30
}
