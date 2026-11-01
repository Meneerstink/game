package gg.rsmod.plugins.content.mechanics.prayer

on_item_option(item = Items.RUINOUS_POWERS, option = "read") {
    player.queue {
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
