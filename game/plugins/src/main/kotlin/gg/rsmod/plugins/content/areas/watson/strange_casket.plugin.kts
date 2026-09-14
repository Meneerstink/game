package gg.rsmod.plugins.content.areas.watson

/** Owner answer Q10: Strange casket dialogue, OSRS Wiki Transcript:Strange casket verbatim (see [StrangeCasket]). */
on_obj_option(obj = StrangeCasket.LOC, option = "search") {
    player.queue { strangeCasket(this) }
}

// The Mimic casket item: the fight is not built yet (owner question 17: arena square), so opening it always gives the wiki's
// "before the fight" message; the post-kill reward and the in-arena message follow with the fight.
on_item_option(item = Items.MIMIC, option = "open") {
    player.message(StrangeCasket.OPEN_BEFORE_FIGHT)
}

suspend fun casketSays(it: QueueTask, text: String) {
    it.itemMessageBox("${StrangeCasket.NAME} $text", item = Items.CASKET_EASY)
}

suspend fun strangeCasket(it: QueueTask) {
    if (it.player.attr[StrangeCasket.MIMIC_CHALLENGES] == true) {
        casketSays(it, "Do you... tire of the challenge?")
        when (it.options("I don't want Mimic challenges anymore.", "What are you?", "No, I want to keep getting mimic challenges.")) {
            1 -> optOut(it)
            2 -> {
                whatAreYou(it)
                when (it.options("I don't want Mimic challenges anymore.", "No, I want to keep getting mimic challenges.")) {
                    1 -> optOut(it)
                    2 -> keepChallenges(it)
                }
            }
            3 -> keepChallenges(it)
        }
        return
    }
    casketSays(it, "Do you... seek a challenge? If you do, the Mimic will test your mettle.")
    when (it.options("Tell me about the Mimic.", "What are you?", "I don't think I want that.")) {
        1 -> tellAboutMimic(it)
        2 -> {
            whatAreYou(it)
            when (it.options("Tell me about the Mimic.", "I don't think I want that.")) {
                1 -> tellAboutMimic(it)
                2 -> declineChallenge(it)
            }
        }
        3 -> declineChallenge(it)
    }
}

suspend fun tellAboutMimic(it: QueueTask) {
    it.chatPlayer("Tell me about the Mimic.", wrap = true)
    casketSays(it, "The Mimic is... a casket. When you find treasure, perhaps you will realise your casket is the Mimic.")
    casketSays(it, "If so, the Mimic will reveal itself, and summon you to its arena, to fight.")
    casketSays(it, "If you are victorious, the Mimic will reward you with... things.")
    casketSays(it, "So, would you like the chance to face the Mimic? The rewards are... special.")
    when (it.options("Yes, I'd like to get Mimic challenges.", "No, I don't think I want that.")) {
        1 -> {
            it.chatPlayer("Yes, I'd like to get Mimic challenges.", wrap = true)
            it.player.attr[StrangeCasket.MIMIC_CHALLENGES] = true
            casketSays(it, "The Mimic is... pleased.")
        }
        2 -> {
            it.chatPlayer("No, I don't think I want that.", wrap = true)
            casketSays(it, "You may find me here if you change your mind. The Mimic will wait.")
        }
    }
}

suspend fun whatAreYou(it: QueueTask) {
    it.chatPlayer("What are you?", wrap = true)
    casketSays(it, "As you see, I am... a casket. Some of us are not mere vessels of wealth.")
    casketSays(it, "Some of us speak. Some can walk. And some, like the Mimic... can fight.")
}

suspend fun declineChallenge(it: QueueTask) {
    it.chatPlayer("I don't think I want that.", wrap = true)
    casketSays(it, "The Mimic is... disappointed.")
}

suspend fun optOut(it: QueueTask) {
    it.chatPlayer("I don't want Mimic challenges anymore.", wrap = true)
    it.player.attr.remove(StrangeCasket.MIMIC_CHALLENGES)
    casketSays(it, "Very well. You shall not be challenged by the Mimic again. Return to me if you change your mind.")
}

suspend fun keepChallenges(it: QueueTask) {
    it.chatPlayer("No, I want to keep getting mimic challenges.", wrap = true)
    casketSays(it, "The Mimic is... pleased.")
}
