package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.scrolls.ClueContainers
import gg.rsmod.plugins.content.scrolls.ClueScrollTier

/** Transcript:Watson, pinned in docs/CLUE/sources. The owner's tier name is 78. */
object WatsonDialogue {
    fun open(player: Player) { player.queue { talk(this) } }

    /** Same stored data for the right-click Check and Astral Contact. 667 chatbox presentation. */
    fun check(player: Player) {
        val stored = WatsonClues.stored(player)
        player.queue {
            messageBox(WatsonClues.TIERS.joinToString("<br>") {
                "${it.displayName}: ${if (it in stored) "Yes" else "No"}"
            })
        }
    }

    suspend fun talk(task: QueueTask) {
        if (task.player.attr[WatsonClues.INTRODUCED] != true) {
            introduction(task)
            if (task.terminated) return
            task.player.attr[WatsonClues.INTRODUCED] = true
        } else task.chatNpc("Hello there ${task.player.username}.", npc = Npcs.WATSON, wrap = true)
        if (WatsonClues.stored(task.player).size == WatsonClues.TIERS.size) {
            offer(task, emptySet())
            return
        }
        menu(task)
    }

    private suspend fun menu(task: QueueTask) {
        while (!task.terminated) {
            val actions = mutableListOf("Who are you?", "Have you heard of Sherlock?", "So what are master clue scrolls anyway?")
            if (ClueScrollTier.values().any { task.player.inventory.contains(it.scrollId) }) actions += "I have something for you."
            actions += listOf("What is that noticeboard for?", "Can you tell me about scroll boxes?", "Goodbye.")
            val selected = task.options(*actions.toTypedArray())
            when (actions.getOrNull(selected - 1)) {
                "Who are you?" -> introduction(task)
                "Have you heard of Sherlock?" -> {
                    task.chatPlayer("Have you heard of Sherlock?", wrap = true)
                    say(task, "Never heard of the bloke. I haven't a clue who he is.")
                    task.chatPlayer("He also claims to be the master of all clue scrolls.", wrap = true)
                    say(task, "As I said, I've never heard of him. If he was such a big deal, I would have heard of him...")
                    task.chatPlayer("Something tells me you're lying! I bet you're just his underling aren't you.", wrap = true)
                }
                "So what are master clue scrolls anyway?" -> {
                    task.chatPlayer("So what are master clue scrolls anyway?", wrap = true)
                    say(task, "Master clue scrolls are the most challenging of all clue scrolls. They will test you in ways you have not been tested before.")
                    say(task, "You will be led all over Gielinor with your strengths, your weaknesses, your mental strength and your reactions all scrutinised.")
                    task.chatPlayer("I assume there is treasure to be found?", wrap = true)
                    say(task, "That is for you to find out, ${task.player.username}.")
                }
                "I have something for you." -> { chooseOffer(task); return }
                "What is that noticeboard for?" -> { blueprint(task); return }
                "Can you tell me about scroll boxes?" -> { boxes(task); return }
                "Goodbye." -> { task.chatPlayer("Goodbye.", wrap = true); return }
                else -> return
            }
        }
    }

    private suspend fun introduction(task: QueueTask) {
        task.chatPlayer("Who are you?", wrap = true)
        say(task, "I am Watson, the master of clue scrolls. I have lived a long life here in Kourend. The lands are beautiful here.")
        task.chatPlayer("You're the master are you?", wrap = true)
        say(task, "Ha! It is not so much due to my ego, but the fact that I give out master tier clue scrolls.")
        task.chatPlayer("Interesting. Do you get many treasure hunters here in Kourend?", wrap = true)
        say(task, "I have seen many, but few are good enough. I only give out my clue scrolls to the very best.")
        task.chatPlayer("Ah! I am just the adventurer you are looking for. If you hand me a clue, I'll be on my way.", wrap = true)
        say(task, "Not so fast ${task.player.username}. I require payment. You must bring me a clue scroll of all lower tiers.")
        say(task, "I will remember which ones you have given me though. I expect you'll find that useful.")
    }

    suspend fun chooseOffer(task: QueueTask, only: ClueScrollTier? = null) {
        if (only == ClueScrollTier.MASTER) {
            say(task, "Sorry ${task.player.username}. That scroll is for you, not me."); return
        }
        val available = WatsonClues.TIERS.filter {
            task.player.inventory.contains(it.scrollId) && it !in WatsonClues.stored(task.player) && (only == null || it == only)
        }
        if (available.isEmpty()) { offer(task, emptySet()); return }
        val labels = available.map { "Hand over ${it.name.lowercase()} clue." } +
            if (available.size > 1) listOf("Hand over all clues.") else listOf("Cancel.")
        val selected = task.options(*labels.toTypedArray())
        if (selected in 1..available.size) offer(task, setOf(available[selected - 1]))
        else if (available.size > 1 && selected == labels.size) offer(task, available.toSet())
    }

    private suspend fun offer(task: QueueTask, offered: Set<ClueScrollTier>) {
        when (WatsonClues.offer(task.player, offered)) {
            WatsonClues.Result.NOTHING_NEW -> say(task, "You need to bring me clue scroll you haven't already given me ${task.player.username}.")
            WatsonClues.Result.STORED -> {
                task.itemMessageBox(if (offered.size > 1) "You hand over the clue scrolls to Watson." else "You hand over the clue scroll to Watson.",
                    item = ClueScrollTier.MASTER.scrollId)
                say(task, "Thank you very much.")
            }
            WatsonClues.Result.MASTER_HELD -> say(task, "I have a master scroll box for you, but you already have too many of them! Finish them or discard them, I don't care which.")
            WatsonClues.Result.NO_SPACE -> say(task, "You don't have enough space for it.")
            WatsonClues.Result.MASTER_GIVEN -> {
                say(task, "Nice work ${task.player.username}, I've had one of each lower tier clue scroll from you.")
                task.itemMessageBox(if (ClueContainers.enabled(task.player)) "Watson hands you a master scroll box." else "Watson hands you a master clue scroll.",
                    item = if (ClueContainers.enabled(task.player)) gg.rsmod.plugins.content.scrolls.ClueItems.local(24366)!! else ClueScrollTier.MASTER.scrollId)
            }
            WatsonClues.Result.MISSING_IMPORT -> {
                println("PROBLEM CLUE Watson reward import missing; no clues consumed")
                task.player.message("This service is temporarily unavailable.")
            }
        }
    }

    private suspend fun blueprint(task: QueueTask) {
        task.chatPlayer("What is that noticeboard for?", wrap = true)
        say(task, "It lets you view any of your S.T.A.S.H units that you have built around Gielinor.")
        task.chatPlayer("Oh that seems really useful! It's a bit of a shame I need to come here everytime to check though.", wrap = true)
        say(task, "Well it's just a board with some notes on it. I'm sure you could figure something out.")
        task.chatPlayer("I suppose you're right, I don't suppose you have a spare one I could buy off you to save me the hassle?", wrap = true)
        say(task, "No, but I could write down the instructions to make one. Then all you'd need is a house with a study to build it in, and a bolt of cloth.")
        when (task.options("Yes", "No", title = "Pay Watson 500 coins for a S.T.A.S.H blueprint?")) {
            1 -> when (WatsonClues.buyBlueprint(task.player)) {
                WatsonClues.Purchase.GIVEN -> say(task, "Here you go.")
                WatsonClues.Purchase.NO_COINS -> say(task, "I'm not giving it away for free, 500 coins is just covering my expenses!")
                WatsonClues.Purchase.NO_SPACE -> say(task, "You don't have enough space for it.")
                WatsonClues.Purchase.MISSING_IMPORT -> {
                    println("PROBLEM CLUE Watson blueprint 25612 not imported; no coins consumed")
                    task.player.message("This service is temporarily unavailable.")
                }
            }
            2 -> say(task, "Alright, feel free to carry on using mine.")
        }
    }

    private suspend fun boxes(task: QueueTask) {
        task.chatPlayer("Can you tell me about scroll boxes?", wrap = true)
        say(task, "Well, the long and short of it is that scroll boxes contain clue scrolls, but in a much easier to carry form factor.")
        say(task, "Now, just because they're easier to carry, it doesn't mean you can take as many as you want. You'll need scroll cases to be able to store them.")
        say(task, "You can get scroll cases by completing clues. You can get up to two extra scroll cases for each clue tier by doing that kind of clue.")
        say(task, "I can give you the exact numbers, but I wouldn't worry too much about them. You'll get them eventually.")
        say(task, "If you want to help deal with the strange casket I have upstairs, I think it also has some scroll cases as well.")
        task.chatPlayer("So how many scrolls can I carry right now?", wrap = true)
        say(task, "You'll find that information written on the statistics board by the door.")
    }

    private suspend fun say(task: QueueTask, text: String) = task.chatNpc(text, npc = Npcs.WATSON, wrap = true)
}
