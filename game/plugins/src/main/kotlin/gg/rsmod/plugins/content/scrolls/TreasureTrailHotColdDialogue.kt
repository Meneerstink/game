package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.QuestStatus

/** Pinned Transcript:Jorral Treasure Trails branches. Ordinary quest/shop dialogues keep their handlers. */
object TreasureTrailHotColdDialogue {
    fun talk(player: Player, npc: Npc, option: Int): Boolean {
        val offer = TreasureTrailHotCold.current.offer(player) ?: return false
        val row = (offer.contract.step as TreasureTrailSourceStep).source
        if (npc.id !in row.existingNpcs || !TreasureTrailNpcSteps.matchesActor(npc,row)) return false
        val def = npc.world.definitions.get(NpcDef::class.java,npc.id)
        if (def.name != "Jorral" || !def.options.getOrNull(option-1).equals("Talk-to",true)) return false
        player.queue {
            suspend fun say(text: String): Boolean {
                if (terminated || !offer.contract.valid(player)) return false
                chatNpc(text,npc=npc.id,wrap=true)
                return !terminated && offer.contract.valid(player)
            }
            if (!QuestStatus.isCompleted(player,"Making History")) {
                chatPlayer("Hello there, I have this master clue he...",wrap=true)
                if (!say("Stop! I can't help you unless you help me out first.")) return@queue
                chatPlayer("Okay then, moving on...",wrap=true)
                return@queue
            }
            val id = ClueItems.local(19939) ?: return@queue
            if (player.inventory.contains(id)) {
                chatPlayer("Hey Jorral, I have one of those clue scrolls again, do you have any advice?",wrap=true)
                say("That strange device will guide you in the same way the key did. Feel it, but be careful as it is very powerful. Good luck.")
                return@queue
            }
            chatPlayer("Hi Jorral, I have come to ask you to help with something.",wrap=true)
            if (!say("What can I do for you ${player.username}?")) return@queue
            messageBox("You show Jorral your clue scroll.")
            if (!say("Ah! Remember that enchanted key you had to use previously?")) return@queue
            when (options("Yes, I do.","No, I don't.")) {
                1 -> { if (!offer.contract.valid(player)) return@queue; chatPlayer("Yes, I do.",wrap=true) }
                2 -> {
                    if (!offer.contract.valid(player)) return@queue
                    chatPlayer("No, I don't.",wrap=true)
                    if (!say("Ah, well the enchanted key can lead you to treasure. The magical nature of the key heats it up or cools it down, depending on your proximity to the treasure.")) return@queue
                }
                else -> return@queue
            }
            if (!say("Well this should function much the same, the deeper magic of the key itself is stored within this strange device.")) return@queue
            if (!say("You should feel it, and it will guide you to where you need to go.")) return@queue
            when (TreasureTrailHotCold.current.issue(player,offer)) {
                TreasureTrailHotCold.Issue.GIVEN -> {
                    messageBox("Jorral hands you the strange device.")
                    say("Be careful with it, the raw magic can damage you unexpectedly when you feel it.")
                }
                TreasureTrailHotCold.Issue.FULL -> say("You should make some room for it before I can give you it.")
                else -> Unit
            }
        }
        return true
    }
}
