package gg.rsmod.plugins.content.quests.foundation.extra

import gg.rsmod.plugins.content.quests.foundation.FoundationQuests
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards

spawn_npc(npc = Npcs.VESTRI, x = 2821, z = 3487, direction = Direction.SOUTH)

// Every option below was checked against the revision-667 cache. Existing spawns are reused.
listOf(Npcs.AUSTRI, Npcs.VESTRI, Npcs.BONZO).forEach { id ->
    on_npc_option(id, "Talk-to") {
        player.queue {
            if (!FishingContest.isFinished(player) && FishingContest.stage(player) > 0) {
                if (id == Npcs.AUSTRI || id == Npcs.VESTRI) ExtraQuests.reclaim(player, Items.FISHING_PASS)
                if (id == Npcs.BONZO && FishingContest.stage(player) == 3) ExtraQuests.reclaim(player, Items.FISHING_TROPHY)
            }
            FoundationQuests.talk(this, if (id == Npcs.VESTRI) Npcs.AUSTRI else id)
        }
    }
}
on_npc_option(Npcs.BONZO, "Pay") { player.queue { FishingContest.pay(this) } }

// Repeatable gathering after losing the worm; the automatic search is the short quest's first step.
listOf(58, 2013, 2989, 2990, 2991, 2992, 2993, 2994).filter { if_obj_has_option(it, "Check") }.forEach { vine ->
on_obj_option(vine, "Check") {
    if (!FishingContest.isFinished(player) && FishingContest.stage(player) in 1..2) {
        if (!player.inventory.contains(Items.SPADE)) player.message("You need a spade to dig for red vine worms.")
        else if (FishingContest.stage(player) == 1) FoundationQuests.trySearch(player, FishingContest, 1)
        else ExtraQuests.reclaim(player, Items.RED_VINE_WORM)
    }
}

}
