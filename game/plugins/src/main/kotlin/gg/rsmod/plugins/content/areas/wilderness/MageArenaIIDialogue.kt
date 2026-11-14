package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.QuestStatus

/** Lines from OSRS Wiki Transcript:Mage Arena II. No donor dialogue or invented boss instructions. */
object MageArenaIIDialogue {
    suspend fun challenge(task: QueueTask) = with(task) {
        chatPlayer("Are there any more challenges available?")
        if (!GodSpellTraining.mastered(player)) {
            chatNpc("You still need to learn how to cast all god spells outside of the arena.")
            chatNpc("I've made a note of the spells you still need to learn in your journal.")
            return@with
        }
        if (player.skills.getMaxLevel(Skills.MAGIC) < 75) {
            chatNpc("You have indeed shown potential, ${player.username}.")
            chatNpc("However, a wizard of your level wouldn't stand a chance.")
            return@with
        }
        chatNpc("I am one of the most powerful mages in existence. But even my power has limitations. There are some beings however that have power exceeding even my own.")
        chatNpc("I have detected three life forces within the Wilderness, each of them giving out a strange magical power, one unlike any I have ever felt before.")
        chatNpc("If we could harness the power of these beings, I could imbue your god cape with that power, greatly increasing its potential.")
        if (options("Great, I've been waiting for an improvement!", "Actually, my current cape is fine.") != 1) {
            chatNpc("I see... If you ever change your mind, you know where to find me.")
            return@with
        }
        chatPlayer("Great, I've been waiting for an improvement!")
        chatPlayer("What do you need me to do?")
        chatNpc("I will need you to kill these three beings and bring me their remains. Even in death, their power should be great enough for me to harness it for our own uses.")
        chatPlayer("Where am I supposed to find these beings?")
        if (giveSymbol(task)) {
            QuestStatus.sync(player)
            talk(task, replaceSymbol = false)
        }
    }

    private suspend fun giveSymbol(task: QueueTask): Boolean = with(task) {
        when (MageArenaII.symbol(player)) {
            MageArenaII.SymbolResult.GRANTED -> {
                chatNpc("Here, take this enchanted symbol of the gods. It will guide you to the creatures.")
                itemMessageBox("Kolodion hands you an enchanted symbol.", MageArenaII.assets.symbol!!)
                true
            }
            MageArenaII.SymbolResult.OWNED -> true
            MageArenaII.SymbolResult.INVENTORY_FULL -> {
                chatNpc("You'll need to free up an inventory space before we continue.")
                false
            }
            // No guessed id and no developer-facing text in the product. Missing imports are reported in the dossier.
            else -> false
        }
    }

    suspend fun talk(task: QueueTask, replaceSymbol: Boolean = true) = with(task) {
        if (replaceSymbol) giveSymbol(task)
        for (god in MageArenaII.God.values()) {
            val remains = MageArenaII.assets.remains(god) ?: continue
            // Show the player's line before taking anything. Recheck the inventory after every suspended dialogue.
            if (!player.inventory.contains(remains)) continue
            chatPlayer("I've got the remains of the ${god.creature}.")
            if (MageArenaII.handIn(player, god) == 0) continue
            QuestStatus.sync(player)
            itemMessageBox(when (god) {
                MageArenaII.God.SARADOMIN -> "Kolodion takes the Justiciar's remains."
                MageArenaII.God.GUTHIX -> "Kolodion takes the Ent's remains."
                MageArenaII.God.ZAMORAK -> "Kolodion takes the Demon's heart."
            }, remains)
            if (!MageArenaII.completed(player)) chatNpc("Excellent work, ${player.username}, but we aren't finished yet.")
        }
        if (!MageArenaII.completed(player)) {
            chatNpc("Bring me the remains of all three magical beings. Do this and I will imbue their combined power into a single god cape of your choice.")
        } else if (player.attr[MageArenaII.FIRST_IMBUE] != true) {
            chatNpc("Excellent work, ${player.username}, now you just need to hand me the god cape you wish to have imbued with great power.")
        } else {
            chatNpc("If you would like me to imbue any more capes, just bring me the remains of the respective creature.")
        }
        advice(task)
    }

    private suspend fun advice(task: QueueTask) = with(task) {
        while (true) {
            val fabric = gg.rsmod.plugins.content.items.osrs.AncientCape.assets.fabric
            val offerFabric = MageArenaII.completed(player) && fabric != null
            val choices = mutableListOf("Where can I find these beings?", "Any advice on fighting the beings?", "Tell me more about these beings.")
            if (offerFabric) choices += "Ancient fabric"
            choices += "Thanks, bye."
            when (options(*choices.toTypedArray())) {
                1 -> {
                    chatPlayer("Where can I find these beings?")
                    chatNpc("They can all be found in the Wilderness. The enchanted symbol can track their unique magical energies, leading you to their exact locations.")
                    chatNpc("Be warned though, the symbol will take a blood sacrifice every time it's used.")
                }
                2 -> {
                    chatPlayer("Any advice on fighting the beings?")
                    chatNpc("They can't be harmed by normal magic, melee or ranged attacks, you will need to use the god combat spell corresponding to the creature you are fighting.")
                }
                3 -> {
                    chatPlayer("Tell me more about these beings.")
                    chatNpc("One is a justiciar. According to legend, the justiciars were the elite of Saradomin's forces. Some worked alone, operating as assassins behind enemy lines. Others led entire armies in Saradomin's name.")
                    chatNpc("I couldn't tell you why this one wanders the Wilderness, but I suspect he has good reason.")
                    chatNpc("The second is a Demon of Zamorak. From what I can tell, he is completely mad. He roams the Wilderness, killing all who he comes across without mercy. Even his own kind do not escape his fury.")
                    chatNpc("He's known to battle the justiciar quite regularly, perhaps they are old enemies.")
                    chatNpc("The final being is a Guthixian Ent. Like most followers of Guthix, he will avoid combat if possible. Make no mistake though, he is deadly when threatened.")
                    chatNpc("He seems to take great interest in the other two creatures. Maybe he is trying to keep a balance between them.")
                    chatNpc("All three of them give off a unique magical energy, one unlike any I have ever felt before. The fact that the three of them are so different, yet possess powers so similar, is very odd indeed.")
                    chatNpc("I'd be very interested in learning more about the source of their power.")
                }
                4 -> {
                    if (offerFabric) {
                        when (gg.rsmod.plugins.content.items.osrs.AncientCape.fabric(player)) {
                            gg.rsmod.plugins.content.items.osrs.AncientCape.Result.CONVERTED -> itemMessageBox("Ancient fabric", fabric!!)
                            gg.rsmod.plugins.content.items.osrs.AncientCape.Result.INVENTORY_FULL -> chatNpc("You'll need to free up an inventory space before we continue.")
                            else -> Unit
                        }
                    } else {
                        chatPlayer("Thanks, bye.")
                        chatNpc("Farewell, ${player.username}.")
                        return@with
                    }
                }
                else -> {
                    chatPlayer("Thanks, bye.")
                    chatNpc("Farewell, ${player.username}.")
                    return@with
                }
            }
        }
    }

    suspend fun imbue(task: QueueTask, god: MageArenaII.God, slot: Int) = with(task) {
        if (!MageArenaII.completed(player)) {
            chatNpc("Bring me the remains of all three magical beings. Do this and I will imbue their combined power into a single god cape of your choice.")
            return@with
        }
        if (options("Yes.", "No.", title = "Imbue your ${god.title} cape?") != 1) return@with
        when (MageArenaII.imbue(player, god, slot)) {
            MageArenaII.ImbueResult.IMBUED -> {
                itemMessageBox("Kolodion takes your cape and imbues it with the power of the gods before handing it back.", god.imbued)
                chatNpc("If you would like me to imbue any more capes, just bring me the remains of the respective creature.")
            }
            MageArenaII.ImbueResult.NO_CREDIT -> chatNpc("You'll have to bring me the ${god.title} creature's remains to imbue this cape.")
            else -> Unit
        }
    }
}
