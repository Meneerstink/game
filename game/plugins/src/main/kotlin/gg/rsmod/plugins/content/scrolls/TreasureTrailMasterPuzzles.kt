package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.*
import kotlin.random.Random

/** Pinned Mandrith/Radimus transcripts: find NPC, receive a box, solve it, return it. */
object TreasureTrailMasterPuzzles {
    val sourceItems = listOf(20355, 20280, 20281, 20282, 23417)
    fun supports(row: TreasureTrailStepCatalogue.Step): Boolean = row.tier == ClueScrollTier.MASTER &&
        row.key in setOf("master:mandrith", "master:radimus") && row.action == TreasureTrailStepCatalogue.Action.TALK &&
        row.challenge == TreasureTrailStepCatalogue.Challenge.PUZZLE_CHOICE &&
        row.puzzles == setOf(TreasureTrailStepCatalogue.Challenge.LIGHT_BOX, TreasureTrailStepCatalogue.Challenge.SLIDING_PUZZLE)
    fun available(world: World, row: TreasureTrailStepCatalogue.Step): Boolean = supports(row) &&
        ClueInterfaces.of(306) != null && TreasureTrailLightBoxes.available(world) &&
        sourceItems.all { source -> ClueItems.local(source)?.let { world.definitions.getNullable(ItemDef::class.java, it) } != null } &&
        row.existingNpcs.any { world.definitions.getNullable(NpcDef::class.java, it)?.let { npc ->
            npc.name.equals(row.npcName, true) && npc.options.any { option -> option.equals("Talk-to", true) }
        } == true }
    fun held(player: Player): Item? = player.inventory.firstOrNull { item -> item != null && sourceItems.any { ClueItems.local(it) == item.id } }
    fun stored(player: Player): Boolean = ClueOwnership.stored(player).any { item -> sourceItems.any { ClueItems.local(it) == item.id } }

    fun issue(player: Player, step: TreasureTrailSourceStep, random: Random = Random.Default): Item? {
        if (!supports(step.source)) return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        held(player)?.let { return it }
        if (stored(player) || player.inventory.freeSlotCount == 0) return null
        val saved = contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY)
        val selected = saved ?: if (random.nextBoolean()) 20355 else TreasureTrailPuzzleBoxes.masterSources.random(random).item
        if (selected !in sourceItems) {
            println("PROBLEM CLUE invalid saved master puzzle kind$selected; scroll retained"); return null
        }
        val box = if (selected == 20355) TreasureTrailLightBoxes.issue(player, step, random)
            else TreasureTrailPuzzleBoxes.issue(player, step, TreasureTrailPuzzleBoxes.masterSources.single { it.item == selected }, random)
        if (box != null) {
            contract.scroll.putAttr(ItemAttribute.CLUE_TASK_KEY, selected)
            player.inventory.dirty = true
        }
        return box
    }
    fun solved(box: Item): Boolean = if (ClueItems.local(20355) == box.id) TreasureTrailLightBoxes.restore(box)?.solved == true
        else box.getAttr(ItemAttribute.CLUE_TASK_COMPLETE) == 1
    fun handIn(player: Player, step: TreasureTrailSourceStep, box: Item): Boolean {
        if (!supports(step.source)) return false
        // The source explicitly prevents obtaining another clue while a duplicate puzzle is stored.
        val all = ClueOwnership.stored(player).filter { item -> sourceItems.any { ClueItems.local(it) == item.id } }
        if (all.size != 1 || all.single() !== box) return false
        return if (ClueItems.local(20355) == box.id) TreasureTrailLightBoxes.handIn(player, step, box)
               else TreasureTrailPuzzleBoxes.handIn(player, step, box)
    }
    fun talk(player: Player, npc: Npc, option: Int): Boolean {
        val step = ClueScrollManager.activeStep(player, ClueScrollTier.MASTER) as? TreasureTrailSourceStep ?: return false
        if (!supports(step.source) || npc.id !in step.source.existingNpcs) return false
        val definition = npc.world.definitions.get(NpcDef::class.java, npc.id)
        if (!definition.name.equals(step.source.npcName, true) || !definition.options.getOrNull(option - 1).equals("Talk-to", true)) return false
        val contract = ClueStepContract.capture(player, step) ?: return false
        ClueInterfaces.of(306)?.let { iface -> if (player.interfaces.isVisible(iface)) {
            TreasureTrailPuzzleBoxes.close(player); player.closeInterface(iface)
        } }
        val previous = held(player)
        if (previous == null) {
            if (stored(player)) return true // Go fetch the existing box; never issue a second one.
            if (issue(player, step) == null) {
                if (player.inventory.freeSlotCount == 0) player.message("You don't have enough inventory space to hold that item.")
                return true
            }
            player.queue { chatNpc("Here, solve this.", npc = npc.id, wrap = true) }
            return true
        }
        player.queue {
            if (!solved(previous)) chatNpc("That doesn't look solved to me.", npc = npc.id, wrap = true)
            else {
                chatNpc("Well done ${player.username}. Here you go.", npc = npc.id, wrap = true)
                if (!terminated && contract.valid(player)) handIn(player, step, previous)
            }
        }
        return true
    }
}
