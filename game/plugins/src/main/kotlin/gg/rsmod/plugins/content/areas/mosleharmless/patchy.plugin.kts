package gg.rsmod.plugins.content.areas.mosleharmless

import gg.rsmod.game.model.queue.QueueTask

// Native 667 SMW (905/916); use the existing make window and its cancel/logout handling.
suspend fun QueueTask.sewing() {
    val npc = player.getInteractingNpc()
    val separate = options("Sew", "Separate", "No, thank you.")
    if (separate !in 1..2) return
    val selections = Patchy.RECIPES.associate { it.product to Patchy.select(player, it, separate == 2) }
    produceItemBox(
        *Patchy.RECIPES.map { it.product }.toIntArray(),
        option = SkillDialogueOption.SELECT,
        maxItems = 1,
        extraNames = Array(Patchy.RECIPES.size) { "${if (separate == 2) Patchy.SEPARATE_PRICE else Patchy.SEW_PRICE} coins" },
    ) { product, _ ->
        val selection = selections[product] ?: return@produceItemBox
        if (tile.isWithinRadius(npc.tile, 2) && npc.isSpawned()) Patchy.exchange(this, selection)
    }
}

on_npc_option(Npcs.PATCHY, "Sew") { player.queue { sewing() } }

on_npc_option(Npcs.PATCHY, "Talk-to") {
    player.queue {
        chatNpc("Hello there! Can I sew ye somethin' tegether?")
        when (options("What do you mean?", "Let me see what you can do.", "No, thank you.")) {
            1 -> {
                chatPlayer("What do you mean?")
                chatNpc("Well, ye see it works like this: I can sew yer eye patches onto yer pirate hats or pirate bandanas. Just pirate bandanas mind ye, I hate the feel of snakeskin.")
                messageBox("Patchy shudders at the thought of touching snakeskin.")
                chatNpc("I also sew crab claw gauntlets on to pirate hooks, highwayman masks to black cavaliers, mime masks to black berets, partyhats to glasses and monocles to top hats.")
                chatNpc("It costs 500 coins te do it, and 600 coins te get them separated again afterwards if ye change yer mind.")
                when (options("Why does it cost more to get them separated again?", "Let me see what you can do.", "No, thank you.")) {
                    1 -> { chatPlayer("Why does it cost more to get them separated again?"); chatNpc("Wear and tear on scissors. So, do ye have anything ye wants doin?"); sewing() }
                    2 -> sewing()
                    3 -> chatNpc("Fair enough. See you around!")
                }
            }
            2 -> { chatPlayer("Let me see what you can do."); sewing() }
            3 -> { chatPlayer("No, thank you."); chatNpc("Fair enough. See you around!") }
        }
    }
}
