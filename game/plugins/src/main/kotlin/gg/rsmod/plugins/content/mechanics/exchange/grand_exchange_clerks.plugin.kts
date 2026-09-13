package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.model.queue.QueueTask

/**
 * RCV-010 C3: Grand Exchange clerk and tutor. Options come from the 667 cache (clerks 1419/2240/2241/2593 carry
 * Talk-to, Exchange, History, Sets — `GrandExchangeInterfaceTests`); dialogue text is Void `GrandExchangeClerk.kt` /
 * `GrandExchangeTutor.kt`. Chathead expressions: SOURCE_BLOCKED (Void's expression ids are not in its data), so the
 * repo default is used. Sets stays in inter/ge. RCV-011: History opens interface 643 ([GrandExchangeHistory]) from the
 * History option and from the Void dialogue line "Can I see a history of my offers?".
 */
val CLERKS = listOf(Npcs.GRAND_EXCHANGE_CLERK, Npcs.GRAND_EXCHANGE_CLERK_2240, Npcs.GRAND_EXCHANGE_CLERK_2241, Npcs.GRAND_EXCHANGE_CLERK_2593)

CLERKS.forEach { clerk ->
    if (if_npc_has_option(clerk, "Exchange")) {
        on_npc_option(npc = clerk, option = "Exchange", lineOfSightDistance = 2) { GrandExchangeInterface.openMain(player) }
    }
    if (if_npc_has_option(clerk, "History")) {
        on_npc_option(npc = clerk, option = "History", lineOfSightDistance = 2) { GrandExchangeHistory.open(player) }
    }
    if (if_npc_has_option(clerk, "Talk-to")) {
        on_npc_option(npc = clerk, option = "Talk-to", lineOfSightDistance = 2) {
            player.queue { clerkDialogue(this) }
        }
    }
}

suspend fun clerkDialogue(task: QueueTask) {
    task.chatNpc("Welcome to the Grand Exchange. Would you like to trade now, or exchange item sets?", wrap = true)
    clerkMenu(task, explained = false)
}

suspend fun clerkMenu(task: QueueTask, explained: Boolean) {
    val first = if (explained) "" else "How do I use the Grand Exchange?"
    val choices =
        listOf(first, "I'd like to set up trade offers please.", "Can I see a history of my offers?", "Can you help me with item sets?", "I'm fine, thanks.")
            .filter { it.isNotEmpty() }
    when (choices.getOrNull(task.options(*choices.toTypedArray()) - 1)) {
        "How do I use the Grand Exchange?" -> {
            task.chatNpc("My colleague and I can let you set up trade offers. You can offer to Sell items or Buy items.", wrap = true)
            task.chatNpc("When you want to sell something, you give us the items and tell us how much money you want for them.", wrap = true)
            task.chatNpc("We'll look for someone who wants to buy those items at your price, and we'll perform the trade. You can then collect the cash here, or at any bank.", wrap = true)
            task.chatNpc("When you want to buy something, you tell us what you want, and give us the cash you're willing to spend on it.", wrap = true)
            task.chatNpc("We'll look for someone who's selling those items at your price, and we'll perform the trade. You can then collect the items here, or at any bank, along with any left-over cash.", wrap = true)
            task.chatNpc("Sometimes it takes a while to find a matching trade offer. If you change your mind, we'll let you cancel your trade offer, and we'll return your unused items and cash.", wrap = true)
            task.chatNpc("That's all the essential information you need to get started. Would you like to trade now, or exchange item sets?", wrap = true)
            clerkMenu(task, explained = true)
        }
        "I'd like to set up trade offers please." -> GrandExchangeInterface.openMain(task.player)
        "Can I see a history of my offers?" -> {
            task.chatNpc("If that is your wish.")
            GrandExchangeHistory.open(task.player)
        }
        "Can you help me with item sets?" -> task.player.message("Use the Sets option on a clerk to exchange item sets.")
        else -> {}
    }
}

if (if_npc_has_option(Npcs.GRAND_EXCHANGE_TUTOR, "Talk-to")) {
    on_npc_option(npc = Npcs.GRAND_EXCHANGE_TUTOR, option = "Talk-to") {
        player.queue {
            chatNpc("How can I help?")
            when (options("Can you teach me about the Grand Exchange again?", "Where can I find out more info?", "I'm okay thanks.")) {
                1 -> {
                    chatNpc("Of course.")
                    chatNpc("The building you see here is the Grand Exchange. You can simply tell us what you want to buy or sell and for how much, and we'll pair you up with another player and make the trade for you!", wrap = true)
                    chatNpc("Buying and selling is done in a very similar way. Let me describe it in five steps.", wrap = true)
                    chatNpc("<col=800000>Step 1</col>: You decide what to buy or sell and come here with the items to sell or the money to buy with.", wrap = true)
                    chatNpc("<col=800000>Step 2</col>: Speak with one of the clerks, behind the desk in the middle of the building and they will guide you through placing the bid and the finer details of what you are looking for.", wrap = true)
                    chatNpc("<col=800000>Step 3</col>: The clerks will take the items or money off you and look for someone to complete the trade.", wrap = true)
                    chatNpc("<col=800000>Step 4</col>: You then need to wait perhaps a matter of moments or maybe days until someone is looking for what you have offered.", wrap = true)
                    chatNpc("<col=800000>Step 5</col>: When the trade is complete, we will let you know with a message and you can pick up your winnings by talking to the clerks or by visiting any banker.", wrap = true)
                    chatNpc("There's a lot more information about the Grand Exchange, all of which you can find out from Brugsen Bursen, the guy with the megaphone. I would suggest you speak to him to fully get to grips with the Grand Exchange. Good luck!", wrap = true)
                }
                2 -> chatNpc("Go and speak to Brugsen who's standing over there, closer to the building. He'll help you out.", wrap = true)
                3 -> chatNpc("Fair enough.")
            }
        }
    }
}
