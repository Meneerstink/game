package gg.rsmod.plugins.content.activity.sorceress_garden

import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer

// Real 2011-era minigame, ported from Void's `content/minigame/sorceress_garden`. See
// SorceressGardenData.kt / SorceressGardenHandler.kt for the sourced ids, tables and mechanics.

GardenSeason.values().forEach { season ->
    on_obj_option(obj = season.gateObj, option = "Open") {
        SorceressGardenHandler.enter(player, season)
    }
    on_obj_option(obj = season.treeObj, option = "Pick-fruit") {
        SorceressGardenHandler.pickFruit(player, season)
    }
    on_obj_option(obj = season.herbObj, option = "Pick") {
        SorceressGardenHandler.pickHerb(player, season)
    }
    on_item_option(item = season.sqirkJuiceItem, option = "Drink") {
        if (player.inventory.remove(season.sqirkJuiceItem).hasSucceeded()) {
            SorceressGardenHandler.drinkSqirkJuice(player, season)
        }
    }
}

on_obj_option(obj = GardenSeason.FOUNTAIN_OBJ, option = "Drink-from") {
    SorceressGardenHandler.leave(player)
}

val GARDEN_PATROL_TIMER =
    TimerKey(
        persistenceKey = "sorceress_garden_patrol",
        tickOffline = true,
        resetOnDeath = false,
        tickForward = false,
        removeOnZero = true,
    )

// Patrol pacing (3 ticks/step) is a reasonable provisional interval, not sourced from either
// donor (Void's own client-driven walk speed doesn't map 1:1 to this engine's timer ticks) - the
// real, sourced content is the waypoint coordinates themselves (GardenElementals.ALL).
val PATROL_STEP_TICKS = 3

GardenElementals.ALL.keys.forEach { id ->
    on_npc_spawn(id) {
        npc.timers[GARDEN_PATROL_TIMER] = PATROL_STEP_TICKS
    }
}

on_timer(GARDEN_PATROL_TIMER) {
    if (GardenElementals.ALL.containsKey(npc.id)) {
        SorceressGardenHandler.patrolTick(npc)
        npc.timers[GARDEN_PATROL_TIMER] = PATROL_STEP_TICKS
    }
}

// Del Monty dialogue, ported verbatim (flavor text only, no mechanical effect) from Void's
// DelMonty.kt.
on_npc_option(Npcs.DELMONTY, "Talk-to") {
    player.queue {
        chatNpc("Hello, no-fur. What are you doing in my mistress's garden?")
        delMontyMain(this)
    }
}

suspend fun delMontyMain(it: gg.rsmod.game.model.queue.QueueTask) {
    when (
        it.options(
            "Looking for sq'irks.",
            "Talking to cats.",
            "Nothing much.",
        )
    ) {
        1 -> {
            it.chatNpc("If it's sq'irks you're after then you've come to the right place. We've got four seasons' worth of them.")
            it.chatPlayer("I've a couple of questions.")
            it.chatNpc("I'd be happy to help a friend of the feline.")
            it.chatPlayer("What do you mean?")
            it.chatNpc("The Sphinx gave you that amulet, so she must hold you in high regard.")
            it.chatPlayer("If I remember correctly, it was the High Priest of Sophanem who gave it to me.")
            it.chatNpc("He and the Sphinx are as thick as thieves, but regardless, I think you've a bit of a cat feel about you.")
            it.chatNpc("Now what are these questions?")
            delMontyQuestions(it)
        }
        2 -> {
            it.chatNpc("A noble and rewarding past-time.")
            delMontyIdentity(it)
        }
        3 -> it.chatNpc("Yawn! Nice talking to you then, no-fur.")
    }
}

suspend fun delMontyQuestions(it: gg.rsmod.game.model.queue.QueueTask) {
    when (
        it.options(
            "What are the creatures inside the gardens?",
            "How do you get into the seasonal gardens?",
            "How is it that the gardens are in different seasons?",
            "How do I get out of here?",
        )
    ) {
        1 -> {
            it.chatNpc("Oh, you mean the gardeners?")
            it.chatPlayer("The strange, floaty creatures.")
            it.chatNpc(
                "Yes, the gardeners. Don't let them see you or they'll teleport you out here. They're very protective of their crops.",
            )
        }
        2 -> {
            it.chatNpc("I get in through gaps in the hedge. You are a little too big to squeeze through. I think you'll have to use the gates.")
            it.chatNpc("I think the gates are locked, so you'll have to have good Thieving skills to get in.")
        }
        3 -> {
            it.chatNpc("How did you get here?")
            it.chatPlayer("Magic?")
            it.chatNpc("Exactly. The Sorceress likes to have a good supply of in-season sq'irks and herbs at all times.")
        }
        4 -> it.chatNpc("Take a drink from the fountain.")
    }
    if (it.options("Thanks, I have another question though.", "Thanks for your help.") == 1) {
        delMontyQuestions(it)
    }
}

suspend fun delMontyIdentity(it: gg.rsmod.game.model.queue.QueueTask) {
    when (
        it.options(
            "How did you get here?",
            "What are you doing here?",
            "Who are you?",
        )
    ) {
        1 -> {
            it.chatNpc("Every time I play with spiders in the Sorceress's house, her silly apprentice completely freaks out and teleports me here!")
            it.chatPlayer("So you've been stuck here since?")
            it.chatNpc("No, silly! I drink from the fountain whenever I want to leave.")
        }
        2 -> {
            it.chatNpc("I get this strange urge for sq'irks. It's quite peculiar. I think I may be addicted.")
            it.chatPlayer("I think I know someone else who may be in a similar position.")
            it.chatNpc("Don't tell me. Osman, right?")
            it.chatPlayer("You know Osman?")
            it.chatNpc("Oh, he used to come here all the time. Then one day, he just stopped.")
        }
        3 -> {
            it.chatNpc("Del-Monty the cat, at your service.")
            it.chatPlayer("Are you a famous adventurer who was turned into a cat by a vindictive mage?")
            it.chatNpc("No; as I said, I'm Del-Monty the cat, connoisseur of exotic fruits.")
            it.chatPlayer("In that case, can you tell me anything about sq'irks?")
            it.chatNpc(
                "But of course! Their juice is an excellent source of energy for runners, and in the riper varieties they are known to heighten one's Thieving abilities.",
            )
        }
    }
    if (it.options("Thanks, I have another question though.", "Thanks for your help.") == 1) {
        delMontyIdentity(it)
    }
}
