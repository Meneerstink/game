package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.*

/** The pinned Gunnjorn and Lumbridge Guide transcripts; no automatic clue award on Solve. */
object TreasureTrailPuzzleDialogue {
    fun npcs(row: TreasureTrailStepCatalogue.Step): Set<Int> = row.existingNpcs +
        if (row.key == "Clue scroll (hard) - BLUE GRIM GUIDED") setOfNotNull(ClueNpcs.local(306)) else emptySet()

    fun talk(player: Player, npc: Npc, option: Int): Boolean {
        val step = ClueScrollManager.activeStep(player, ClueScrollTier.HARD) as? TreasureTrailSourceStep ?: return false
        val source = TreasureTrailPuzzleBoxes.source(step) ?: return false
        val definition = npc.world.definitions.get(NpcDef::class.java, npc.id)
        if (npc.id !in npcs(step.source) || !definition.name.equals(step.source.npcName, true) ||
            !definition.options.getOrNull(option - 1).equals("Talk-to", true)) return false
        val contract = ClueStepContract.capture(player, step) ?: return false
        val id = ClueItems.local(source.item) ?: return true
        ClueInterfaces.of(306)?.let { iface ->
            if (player.interfaces.isVisible(iface)) { TreasureTrailPuzzleBoxes.close(player); player.closeInterface(iface) }
        }
        val box = player.inventory.firstOrNull { it?.id == id }
        if (box == null) {
            if (ClueOwnership.stored(player).any { it.id == id }) {
                if (step.source.npcName == "Gunnjorn") player.queue {
                    chatPlayer("I don't have the puzzle box with me.")
                    chatNpc("That's not much use is it, go and fetch it!", npc = npc.id, wrap = true)
                }
                return true
            }
            if (TreasureTrailPuzzleBoxes.issue(player, step) == null) {
                player.message("You don't have enough inventory space to hold that item.")
                return true
            }
            player.queue { chatNpc(if (step.source.npcName == "Gunnjorn")
                "Please solve this for me. I have other things to be thinking about."
            else "I'm supposed to be the local source of knowledge, but I cannot even solve this puzzle! Do me a favour and solve it for me.",
                npc = npc.id, wrap = true) }
            return true
        }
        player.queue {
            if (box.getAttr(ItemAttribute.CLUE_TASK_COMPLETE) != 1) {
                chatNpc("Sorry but you haven't solved the puzzle yet.", npc = npc.id, wrap = true)
            } else {
                chatNpc("Thanks ${player.username}!", npc = npc.id, wrap = true)
                if (!terminated && contract.valid(player)) TreasureTrailPuzzleBoxes.handIn(player, step, box)
            }
        }
        return true
    }
}
