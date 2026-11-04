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
            // Transcript:Krystilia has six lines; the rev-667 option box holds five (a sixth broke it in the test client, 2026-11-04), so
            // "Er... Nothing..." - which only ends the talk - is left to closing the box.
            else options("I need another assignment.", "Have you any rewards for me, or anything to trade?", "Let's talk about the difficulty of my assignments.",
                "What can you do apart from Slayer Master stuff?", "I love your hat! Where did you get it from?")
        // Transcript:Krystilia: the player says each chosen line (the hat option is spoken as "I love the hat! ...").
        when {
            choice == 1 -> {
                chatPlayer("I need another assignment.", wrap = true)
                assignment(q, id)
            }
            !remote && choice == 2 -> {
                chatPlayer("Have you any rewards for me, or anything to trade?", wrap = true)
                say("I have quite a few rewards you can earn, and a wide variety of Slayer equipment for sale.", id)
                when (options("Look at rewards.", "Look at shop.", "Cancel.")) {
                    1 -> rewards(player)
                    2 -> player.openShop("Slayer Equipment")
                }
            }
            choice == (if (remote) 2 else 3) -> {
                chatPlayer("Let's talk about the difficulty of my assignments.", wrap = true)
                difficulty(q, id)
            }
            !remote && choice == 4 -> {
                chatPlayer("What can you do apart from Slayer Master stuff?", wrap = true)
                say("Quite a lot! Those stupid guards think they've trapped me here for causing trouble, but I can get back out to the Wilderness for some fun anytime I like.", id)
                // Owner 2026-11-04 (N522 V1): her OSRS Edgeville offer at Death's price (RespawnPoints.PRICE, 500k, Death's payment
                // order), the same bought point as at Death. The "back to Lumbridge" branch (respawn already Edgeville) is not built:
                // Lumbridge is a bought point here, not the free default - open question in N541.
                val edgeville = RespawnPoints.Point.EDGEVILLE
                if (RespawnPoints.available(edgeville) && !RespawnPoints.owns(player, edgeville) && RespawnPoints.active(player) != edgeville) {
                    say("Hey, perhaps you'd like me to fix it so that you respawn in Edgeville whenever you die. If you love the Wilderness as much as I do, you might enjoy that.", id)
                    if (options("Tell me more.", "That doesn't interest me.") != 1) {
                        chatPlayer("That doesn't interest me.", wrap = true)
                        say("Maybe you just can't handle Edgeville!", id)
                        return@with
                    }
                    chatPlayer("Tell me more.", wrap = true)
                    say("Alright, here's the deal: Whenever you drop dead, I'll try to make you reappear in Edgeville, down by the southern ruins. You'll lose your items as normal.", id)
                    say("My magic won't affect you if you die in some fancy place like Castle Wars or the Emir's Arena; those places have their own magic trying to make you 'safe'.", id)
                    say("I'll want a one-off payment of ${String.format(java.util.Locale.US, "%,d", RespawnPoints.PRICE)} coins. If you get your respawn changed later, and then need me to set it back to Edgeville, I won't bill you again.", id)
                    if (!RespawnPoints.buy(player, edgeville)) {
                        chatPlayer("I'm not carrying that much money.", wrap = true)
                        say("Scared of dropping it? Hah!", id)
                        return@with
                    }
                    chatPlayer("Okay, switch my spawn to Edgeville.", wrap = true)
                    RespawnPoints.activate(player, edgeville)
                    return@with
                }
                if (RespawnPoints.owns(player, edgeville) && RespawnPoints.active(player) != edgeville) {
                    say("So do you want to respawn in Edgeville in future?", id)
                    if (options("Please switch my respawn back to Edgeville.", "No, I don't want to respawn in Edgeville.") != 1) {
                        chatPlayer("No, I don't want to respawn in Edgeville.", wrap = true)
                        say("Boring!", id)
                        return@with
                    }
                    chatPlayer("Please switch my respawn back to Edgeville.", wrap = true)
                    if (RespawnPoints.active(player) == RespawnPoints.Point.FEROX) {
                        say("Couldn't handle Ferox Enclave? Do you really think you could handle Edgeville?", id)
                    } else {
                        say("Sounds like you're looking for some fun! Are you sure you can handle it?", id)
                    }
                    if (options("Yes, switch my respawn to Edgeville.", "No, maybe another time.") != 1) {
                        chatPlayer("No, maybe another time.", wrap = true)
                        say("Boring!", id)
                        return@with
                    }
                    chatPlayer("Yes, switch my respawn to Edgeville.", wrap = true)
                    RespawnPoints.activate(player, RespawnPoints.Point.EDGEVILLE)
                    say("Done.", id)
                }
            }
            !remote && choice == 5 -> {
                chatPlayer("I love the hat! Where did you get it from?", wrap = true)
                hat(q, id)
            }
            else -> chatPlayer(if (remote) "Err... Nothing..." else "Er... Nothing...", wrap = true)
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
        // OSRS Wiki "Krystilia": "She will refuse to assign tasks to players who are still Level 1 in Slayer, insisting that they first
        // need to speak to the Burthorpe Slayer Master Turael". Her own words are in no source: owner 2026-11-04 (N522 V2) - a short
        // game message that follows the wiki.
        if (player.skills.getMaxLevel(Skills.SLAYER) <= 1) {
            player.message("Krystilia won't assign you a task until you have spoken to Turael, the Slayer Master in Burthorpe, about how Slayer works.")
            return@with
        }
        if (player.attr[Krystilia.WARNED] != true) {
            say("Before I assign you anything, I want to make something clear. My tasks have to be done in the Wilderness. Only kills inside the Wilderness will count.", id)
            say("I don't check combat levels when choosing tasks, either. So even if other masters won't assign you 'tough' monsters, I'll pick anything for which you have the Slayer level, provided you can physically get to it.", id)
            say("You might get a few unusual loot drops while you're on my assignments, if you're lucky. So, do you want me to assign you something?", id)
            if (options("Yes, I understand I must kill it in the Wilderness.", "No thanks, I don't want tasks from you.") != 1) {
                chatPlayer("No thanks, I don't want tasks from you.", wrap = true)
                return@with
            }
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
            1 -> {
                chatPlayer("Got any tips for me?", wrap = true)
                // The per-task Slayer task tip comes first in OSRS (N541 research); then her fixed line.
                say("You've got to do the task in the Wilderness.", id)
                chatPlayer("Great, thanks!", wrap = true)
            }
            2 -> {
                chatPlayer("Okay, great!", wrap = true)
                say("Good luck! Don't forget to come back when you need a new assignment.", id)
            }
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
