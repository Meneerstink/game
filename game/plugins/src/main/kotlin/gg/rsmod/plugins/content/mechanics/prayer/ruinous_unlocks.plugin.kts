package gg.rsmod.plugins.content.mechanics.prayer

on_item_option(item = Items.RUINOUS_POWERS, option = "read") {
    player.queue {
        // N545 (owner 2026-11-02): reading uses the book up, so a player who can unlock is asked first (the owner's words), in the same
        // Yes/No item window as every other irreversible item action (ItemActionGuard).
        if (RuinousUnlocks.wouldUnlock(player) &&
            !confirmItemAction(Items.RUINOUS_POWERS, "You are about to unlock Ruinous prayers, are you sure?", "The book will be used up.")
        ) {
            return@queue
        }
        when (RuinousUnlocks.read(player)) {
            RuinousUnlocks.ReadResult.QUEST_REQUIRED -> messageBox("You must complete Desert Treasure II before you can understand this book.")
            RuinousUnlocks.ReadResult.ALREADY_UNLOCKED -> messageBox("You have already unlocked the Ruinous Powers.")
            RuinousUnlocks.ReadResult.MISSING_BOOK -> Unit
            RuinousUnlocks.ReadResult.UNLOCKED -> {
                player.playSound(Sfx.TRAILBLAZER_UNLOCK_POWER)
                player.message("<col=ef1020>You have unlocked the Ruinous Powers.</col>")
                messageBox("You have unlocked the Ruinous Powers. You can now choose them at a prayer altar or by speaking to Azzanadra.")
            }
        }
    }
}

on_npc_killed { killer, npc ->
    // Breaches award their eligible contributors once in DeadmanBreach.dropLoot.
    if (!gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(npc)) {
        RuinousBookDrops.onKill(killer, npc.id, npc.tile)
    }
}
