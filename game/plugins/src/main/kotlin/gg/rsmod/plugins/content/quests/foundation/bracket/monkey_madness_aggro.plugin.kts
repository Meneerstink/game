package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs

/*
 * PROJECT48 Q19 Monkey Madness: the monkey guards and archers of Ape Atoll leave a player alone who wields a monkey greegree (OSRS: the
 * greegree makes you "one of them"). Without this, a player talking to King Awowogei (the quest step that REQUIRES the greegree) is shot
 * by the archers, every hit closes the dialogue and the step can never be completed (found 21-11 in the Q19 scenario: step 5 stuck, 3 runs).
 * npc_aggro's on_global_npc_spawn sets the aggroCheck on the same spawn, so the wrapper is installed one tick later (as the revenant caves do).
 */
val monkeyGreegrees = intArrayOf(Items.MONKEY_GREEGREE, Items.NINJA_MONKEY_GREEGREE, Items.NINJA_MONKEY_GREEGREE_4025)
val atollMonkeys =
    intArrayOf(Npcs.MONKEY_GUARD, Npcs.MONKEY_ARCHER, Npcs.MONKEY_ARCHER_1457, Npcs.MONKEY_ARCHER_1458, Npcs.MONKEY_GUARD_1459, Npcs.MONKEY_GUARD_1460)

atollMonkeys.forEach { monkeyId ->
    on_npc_spawn(monkeyId) {
        npc.queue {
            wait(1)
            val base = npc.aggroCheck
            npc.aggroCheck = { n, p ->
                monkeyGreegrees.none { g -> p.equipment.contains(g) } && (base == null || base(n, p))
            }
        }
    }
}
