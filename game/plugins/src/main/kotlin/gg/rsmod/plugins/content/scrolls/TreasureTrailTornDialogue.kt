package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*

/** Pinned Transcript:Abbot Langley and Transcript:Robin; ordinary services fall through. */
object TreasureTrailTornDialogue {
    fun talk(player: Player, npc: Npc, option: Int): Boolean {
        val definition = player.world.definitions.get(NpcDef::class.java,npc.id)
        if (!definition.options.getOrNull(option-1).equals("Talk-to",true)) return false
        val step = ClueScrollManager.activeStep(player,ClueScrollTier.MASTER) as? TreasureTrailThreePartStep ?: return false
        val part = step.components.indexOfFirst { npc.id in it.existingNpcs && definition.name.equals(it.npcName,true) }
        if (part < 0) return false
        val row = step.components[part]
        if (!step.qualifies(player,part)) {
            if (row.npcName != "Robin") return false // Abbot's failed-requirement wording is absent; retain his ordinary dialogue.
            player.queue { chatNpc("As THE Master Bowman, I am quite impressed by your equipment, but it's not the sort I'd recommend myself. Try more conventional forms of accuracy enhancement.",npc=npc.id,wrap=true) }
            return true
        }
        val source = ClueItems.local(TreasureTrailTornParts.SOURCE_IDS[part]) ?: return false
        if (ClueOwnership.stored(player).any { it.id == source }) return true
        val contract = ClueStepContract.capture(player,step) ?: return false
        player.queue {
            if (!contract.valid(player)) return@queue
            if (row.npcName == "Abbot Langley" && player.inventory.freeSlotCount == 0) {
                chatNpc("Hey, I have something for you but you don't have any space for it!",npc=npc.id,wrap=true)
                return@queue
            }
            if (row.npcName == "Robin") {
                chatNpc("As THE Master Bowman, I am most impressed by your equipment, peasant.",npc=npc.id,wrap=true)
                if (terminated || !contract.valid(player)) return@queue
            }
            chatNpc("Here you go.",npc=npc.id,wrap=true)
            if (terminated || !contract.valid(player)) return@queue
            if (TreasureTrailTornParts.obtain(player,step,part) != TreasureTrailTornParts.Obtain.GIVEN) return@queue
            messageBox("You are handed a torn part of a clue scroll.")
            if (row.npcName == "Abbot Langley")
                chatNpc("I must say, I despise the equipment you have chosen to wear.",npc=npc.id,wrap=true)
        }
        return true
    }
}
