package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.areas.deathsoffice.RespawnPoints

/** Transcript:Krystilia (source snapshot build/n522-ab, revisions in the N541 dossier). */
object KrystiliaDialogue {
    private suspend fun QueueTask.say(line: String, id: Int) = chatNpc(line, npc = id, wrap = true)

    fun rewards(player: Player) {
        player.openInterface(164, InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(164, 20, player.getSlayerPointsCount().toString())
    }

    fun open(player: Player, remote: Boolean = false) {
        val id = Krystilia.npcId(player.world) ?: return
        player.queue { talk(this, id, remote) }
    }

    suspend fun talk(q: QueueTask, id: Int, remote: Boolean = false) = with(q) {
        say("Yeah? What do you want?", id)
        val choice = if (remote) options("I need another assignment.", "Let's talk about the difficulty of my assignments.", "Err... Nothing...")
            else options("I need another assignment.", "Have you any rewards for me, or anything to trade?", "Let's talk about the difficulty of my assignments.",
                "What can you do apart from Slayer Master stuff?", "I love your hat! Where did you get it from?", "Er... Nothing...")
        when {
            choice == 1 -> assignment(q, id)
            !remote && choice == 2 -> {
                say("I have quite a few rewards you can earn, and a wide variety of Slayer equipment for sale.", id)
                when (options("Look at rewards.", "Look at shop.", "Cancel.")) {
                    1 -> rewards(player)
                    2 -> player.openShop("Slayer Equipment")
                }
            }
            choice == (if (remote) 2 else 3) -> difficulty(q, id)
            !remote && choice == 4 -> {
                say("Quite a lot! Those stupid guards think they've trapped me here for causing trouble, but I can get back out to the Wilderness for some fun anytime I like.", id)
                // Owner's existing respawn decision wins over the OSRS 5m service: Death sells all points for 500k.
                if (RespawnPoints.owns(player, RespawnPoints.Point.EDGEVILLE)) {
                    say("So do you want to respawn in Edgeville in future?", id)
                    if (options("Please switch my respawn back to Edgeville.", "No, I don't want to respawn in Edgeville.") == 1) {
                        RespawnPoints.activate(player, RespawnPoints.Point.EDGEVILLE)
                        say("Done.", id)
                    }
                } else player.message("Death himself arranges it these days.")
            }
            !remote && choice == 5 -> hat(q, id)
        }
    }

    suspend fun difficulty(q: QueueTask, id: Int) = with(q) {
        say("Other Slayer Masters take your combat level into account when choosing tasks for you, so they won't set you anything too hard. But I don't do that - if you get a task from me, it could be anything for which you", id)
        say("have the Slayer level, provided you can physically get to it.", id)
    }

    suspend fun assignment(q: QueueTask, id: Int) = with(q) {
        val current = player.getSlayerAssignment()
        if (current != null) {
            val remaining = player.attr[SLAYER_AMOUNT] ?: 0
            say(if (Krystilia.active(player)) "You're still meant to be slaying ${current.identifier.lowercase()} in the Wilderness; you have $remaining to go. Come back when you've finished your task."
                else "You're still hunting ${current.identifier.lowercase()}; you have $remaining to go. Come back when you've finished your task.", id)
            return@with
        }
        if (player.skills.getMaxLevel(Skills.SLAYER) == 1 && player.attr[STARTED_SLAYER] != true) {
            // Exact level-1 refusal is missing from the wiki transcript; use its sourced direction as a game message.
            player.message("Speak to the Burthorpe Slayer Master Turael first.")
            return@with
        }
        if (player.attr[Krystilia.WARNED] != true) {
            say("Before I assign you anything, I want to make something clear. My tasks have to be done in the Wilderness. Only kills inside the Wilderness will count.", id)
            say("I don't check combat levels when choosing tasks, either. So even if other masters won't assign you 'tough' monsters, I'll pick anything for which you have the Slayer level, provided you can physically get to it.", id)
            say("You might get a few unusual loot drops while you're on my assignments, if you're lucky. So, do you want me to assign you something?", id)
            if (options("Yes, I understand I must kill it in the Wilderness.", "No thanks, I don't want tasks from you.") != 1) return@with
            player.attr[Krystilia.WARNED] = true
        }
        // Recheck after every suspended dialogue: one active task across both master types.
        if (player.getSlayerAssignment() != null) return@with
        val selected = Krystilia.choose(player) ?: return@with
        val range = if (selected.assignment.identifier in player.attr[Krystilia.EXTENDED].orEmpty()) selected.extended ?: selected.amount else selected.amount
        val amount = if (selected in Krystilia.BOSS_TASKS) inputInt("How many would you like to slay? (3-35)").takeIf { it in range } ?: return@with
            else player.world.random(range)
        Krystilia.assign(player, selected, amount, id)
        say("Your new task is to kill $amount ${selected.assignment.identifier.lowercase()}.", id)
        when (options("Got any tips for me?", "Okay, great!")) {
            1 -> say("You've got to do the task in the Wilderness.", id)
            2 -> say("Good luck! Don't forget to come back when you need a new assignment.", id)
        }
    }

    private suspend fun hat(q: QueueTask, id: Int) = with(q) {
        say("Thank you. I actually made it myself!", id)
        chatPlayer("Oh, I didn't know you enjoyed arts and crafts?", wrap = true)
        say("Oh yes, I put the craft in witchcraft! I have plenty of spare time outside of assigning tasks so I've been making comics and stuffed toys.", id)
        chatPlayer("That's so sweet! I didn't realise Slayer Masters had so much spare time.", wrap = true)
        say("Perhaps I have a little too much spare time! I've made so many things that I'm running out of room.", id)
        chatPlayer("Have you thought about giving them to other people?", wrap = true)
        say("I tried handing out stuffed chinchompas, but the guards mistook them for the real thing and arrested me for smuggling explosives out of my cell.", id)
        chatPlayer("Oh dear. Don't you have any less... dangerous crafts?", wrap = true)
        say("Well, I do make hats too.", id)
        chatPlayer("That's perfect! People love hats.", wrap = true)
    }
}
