package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.QuestStatus

/** Pinned Transcript:Falo the Bard. Same username adaptation as existing Olaf: no Fremennik-name system here. */
object TreasureTrailFaloDialogue {
    fun talk(player: Player, npc: Npc) {
        val offer = TreasureTrailFalo.offer(player)
        player.queue {
            if (offer == null) { standard(this, npc); return@queue }
            suspend fun say(text: String): Boolean {
                if (terminated || !offer.contract.valid(player)) return false
                chatNpc(text, npc = npc.id, wrap = true)
                return !terminated && offer.contract.valid(player)
            }
            if (offer.first) {
                if (!say("Thank you for coming ${player.username}, I need your help.")) return@queue
                if (!say("I have one of my old songs stuck in my head, but I cannot remember what the object it refers to is. I am so forgetful. I'll sing it to you.")) return@queue
                if (!say("Here goes... ${offer.task.lyric}")) return@queue
                chatPlayer("What a beautiful song!", wrap = true)
                say("You have very kind words. I would be extremely grateful if you could remind me which object the song was about.")
            } else {
                if (!say("I really hope you can help me. Please show me the object my song is about!")) return@queue
                chatPlayer("Can you sing me the song again?", wrap = true)
                say("Okay, here goes... ${offer.task.lyric}")
            }
        }
    }

    fun show(player: Player, npc: Npc, shown: Item) {
        val offer = TreasureTrailFalo.offer(player) ?: return
        val match = TreasureTrailFalo.match(player, offer, shown)
        if (match == TreasureTrailFalo.Match.STALE) return
        player.queue {
            if (TreasureTrailFalo.match(player, offer, shown) != match) return@queue
            messageBox("You show your object to Falo.")
            if (TreasureTrailFalo.match(player, offer, shown) != match) return@queue
            messageBox(if (match == TreasureTrailFalo.Match.CORRECT) "Falo's eyes light up." else "Falo's eyes fail to light up.")
            if (TreasureTrailFalo.match(player, offer, shown) != match) return@queue
            if (match == TreasureTrailFalo.Match.WRONG) {
                chatNpc("No no no! That's not it. Please have another think about it, ${player.username}.", npc = npc.id, wrap = true)
                return@queue
            }
            chatNpc("Yes yes yes! That is it, thank you so much ${player.username}. The memories have come flooding back. I get why the lyrics are what they are now.", npc = npc.id, wrap = true)
            if (terminated) return@queue
            if (!TreasureTrailFalo.finish(player, offer, shown)) {
                if (offer.contract.valid(player)) println("PROBLEM CLUE Falo next clue/casket unavailable; held items retained")
                return@queue
            }
            chatPlayer("I'm glad to have helped, hopefully you'll never forget again!", wrap = true)
        }
    }

    private suspend fun standard(task: QueueTask, npc: Npc) {
        task.chatPlayer("Hi there! Who are you?", wrap = true)
        if (QuestStatus.state(task.player, gg.rsmod.plugins.content.quests.QuestList.ENTRIES.first { it.name == "The Fremennik Trials" }.slot) == QuestStatus.State.NOT_STARTED) {
            task.chatNpc("Get away! I do not speak to outlanders.", npc = npc.id, wrap = true); return
        }
        task.chatNpc("Hi there ${task.player.username}. I am Falo the bard! Like my brother Olaf, who lives in Rellekka, I also write music.", npc = npc.id, wrap = true)
        task.chatPlayer("Do you write about anything in particular?", wrap = true)
        task.chatNpc("Oh most certainly! I write about all the objects in the lands. From weapons and armour to palm trees and sands. That's why I wander far from Rellekka to seek inspiration.", npc = npc.id, wrap = true)
        task.chatNpc("I have one problem though. I am very forgetful, I can never remember which particular object a song was about.", npc = npc.id, wrap = true)
        task.chatPlayer("Surely the lyrics for such a song would be enough to help you remember?", wrap = true)
        task.chatNpc("I am afraid not. It saddens me deeply, so I often send out messages to adventurers to help me discreetly.", npc = npc.id, wrap = true)
        task.chatPlayer("I'll keep a look out for them! Goodbye Falo.", wrap = true)
    }
}
