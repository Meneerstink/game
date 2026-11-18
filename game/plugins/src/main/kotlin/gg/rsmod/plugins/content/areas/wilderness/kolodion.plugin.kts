package gg.rsmod.plugins.content.areas.wilderness


val kolodion = Npcs.KOLODION

on_npc_option(kolodion, option = "talk-to") {
    player.queue {
        if (MageArenaII.started(player)) {
            MageArenaIIDialogue.talk(this)
        } else if (MageArena.completed(player)) {
            mageArenaCompletedDialogue(this)
        } else {
            kolodionDialogue(this)
        }
    }
}

suspend fun kolodionDialogue(it: QueueTask) {
    it.chatPlayer("Hello there. What is this place?")
    it.chatNpc(
        "I am the great Kolodion, master of battle magic,",
        " and this is my battle arena. Top wizards travel ",
        "from all over Gielinor to fight here.",
    )
    val option = it.options("Can I fight here?", "What's the point of that?", "That's barbaric!")
    when (option) {
        1 -> canIFightHere(it)
        2 -> whatsThePoint(it)
        3 -> thatsBarbaric(it)
    }
}

suspend fun mageArenaCompletedDialogue(it: QueueTask) {
    if (it.player.attr[gg.rsmod.game.model.attr.prayedAtStatue] == true) {
        it.chatPlayer("Hello, Kolodion.")
        it.chatNpc("Hey there, how are you? Are you enjoying the bloodshed?")
        val options = mutableListOf("I think I've had enough for now.", "How can I use my new spells outside of the arena?")
        if (MageArenaII.assets.ready) options += "Are there any more challenges available?"
        when (it.options(*options.toTypedArray())) {
            1 -> {
                it.chatPlayer("I think I've had enough for now.")
                it.chatNpc("A shame. You're a good battle mage. I hope to see you soon.")
            }
            2 -> {
                it.chatPlayer("How can I use my new spells outside of the arena?")
                it.chatNpc("Experience, my friend, experience. Once you've used the spell enough times in the arena, you'll be able to use them in the rest of Gielinor.")
                it.chatPlayer("Good stuff.")
                it.chatNpc("Not so good for the citizens; they won't stand a chance.")
                it.chatPlayer("How am I doing so far?")
                GodSpellTraining.SPELLS.forEach { training -> it.chatNpc(GodSpellTraining.progress(it.player, training)) }
            }
            3 -> MageArenaIIDialogue.challenge(it)
        }
        return
    }
    it.chatPlayer("Hello, Kolodion.")
    it.chatNpc(
        "Well done, young adventurer;",
        "you truly are a worthy battle mage.",
    )
    it.chatPlayer("What now?")
    it.chatNpc(
        "Step into the magic pool.",
        "It will take you to a chamber. There, you must decide",
        "which god you will represent in the arena.",
    )
    it.chatPlayer("Thanks, Kolodion.")
    it.chatNpc("That's what I'm here for.")
}

suspend fun canIFightHere(it: QueueTask) {
    it.chatPlayer("Can I fight here?")
    it.chatNpc(
        "My arena is open to any high level wizard, but this",
        "is no game. Many wizards fall in this arena, never",
        "to rise again. The strongest mages have been destroyed.",
    )
    it.chatNpc("If you're sure you want in?")
    val option2 = it.options("Yes indeedy.", "No I don't.")
    when (option2) {
        1 -> yesIndeedy(it)
        2 -> noIDont(it)
    }
}

suspend fun yesIndeedy(it: QueueTask) {
    it.chatPlayer("Yes indeedy.")
    it.chatNpc("Good, good. You have a healthy sense of competition.")
    it.chatNpc(
        "Remember, traveller - in my arena, hand-to-hand combat",
        "is useless. Your strength will diminish as you enter the",
        "arena, but the spells you can learn are amongst the most",
        "powerful in all of Gielinor.",
    )
    it.chatNpc("Before I can accept you in, we must duel.")
    val option3 = it.options("Okay, let's fight.", "No thanks.")
    when (option3) {
        1 -> okayLetsFight(it)
        2 -> noThanks(it)
    }
}

suspend fun okayLetsFight(it: QueueTask) {
    if (it.player.skills.getMaxLevel(Skills.MAGIC) < 60) {
        it.chatNpc("Hah! A wizard of your level? Don't be absurd.")
        return
    }
    it.chatPlayer("Okay, let's fight.")
    it.chatNpc("I must first check that you are up to scratch.")
    it.chatPlayer("You don't need to worry about that.")
    it.chatNpc(
        "Not just any magician can enter - only the most powerful",
        "and most feared. Before you can use the power of this arena,",
        "you must prove yourself against me.",
    )
    // The player is teleported to the Mage Arena to fight against Kolodion
    MageArena.start(it.player)
    // (End of dialogue)
}

suspend fun noThanks(it: QueueTask) {
    it.chatPlayer("No thanks.")
    // (End of dialogue)
}

suspend fun noIDont(it: QueueTask) {
    it.chatPlayer("No I don't.")
    it.chatNpc("Your loss.")
    // (End of dialogue)
}

suspend fun whatsThePoint(it: QueueTask) {
    it.chatPlayer("What's the point of that?")
    it.chatNpc(
        "We learn how to use our magic to its fullest and how",
        "to channel the forces of the cosmos into our world...",
    )
    it.chatNpc("But mainly, I just like blasting people into dust.")
    val option4 = it.options("Can I fight here?", "That's barbaric!")
    when (option4) {
        1 -> canIFightHere(it)
        2 -> thatsBarbaric(it)
    }
}

suspend fun thatsBarbaric(it: QueueTask) {
    it.chatPlayer("That's barbaric!")
    it.chatNpc(
        "Nope, it's magic. But I know what you mean.",
        "So do you want to join us?",
    )
    val option5 = it.options("Yes indeedy.", "No I don't.")
    when (option5) {
        1 -> yesIndeedy(it)
        2 -> noIDont(it)
    }
}
