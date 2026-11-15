package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute

/** Fixed source question, carried paper, no penalty on a wrong answer (Wiki Challenge scroll). */
object TreasureTrailNumberChallenge {
    // This world uses one physical paper per tier plus per-item step identity, like its base clues.
    val sources = mapOf(ClueScrollTier.MEDIUM to 2842, ClueScrollTier.HARD to 7269, ClueScrollTier.ELITE to 12139)
    data class Assignment(val contract: ClueStepContract, val source: TreasureTrailStepCatalogue.Step, val first: Boolean)
    enum class Answer { CORRECT, WRONG, STALE }

    fun current(player: Player, step: TreasureTrailSourceStep): Assignment? {
        val source = step.source
        if (source.challenge != TreasureTrailStepCatalogue.Challenge.NUMBER) return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        if (contract.scroll.id != sources[source.tier]?.let(ClueItems::local) ||
            contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY) != source.id) return null
        return Assignment(contract, source, false)
    }

    fun begin(player: Player, step: TreasureTrailSourceStep): Assignment? {
        current(player, step)?.let { return it }
        if (step.source.challenge != TreasureTrailStepCatalogue.Challenge.NUMBER) return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        if (contract.scroll.id != step.tier.scrollId || contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY) != null) return null
        val id = sources[step.tier]?.let(ClueItems::local) ?: return null
        if (player.world.definitions.getNullable(ItemDef::class.java, id) == null) return null
        val slot = player.inventory.rawItems.indexOfFirst { it === contract.scroll }
        if (slot < 0 || !contract.valid(player)) return null
        player.inventory[slot] = Item(id).copyAttr(contract.scroll).putAttr(ItemAttribute.CLUE_TASK_KEY, step.persistenceId)
        return current(player, step)?.copy(first = true)
    }

    fun answer(player: Player, assignment: Assignment, answer: Int): Answer {
        val step = assignment.contract.step as? TreasureTrailSourceStep ?: return Answer.STALE
        val current = current(player, step) ?: return Answer.STALE
        if (!assignment.contract.valid(player) || current.contract.scroll !== assignment.contract.scroll) return Answer.STALE
        if (current.source !== assignment.source || answer !in current.source.answers) return Answer.WRONG
        return if (assignment.contract.advance(player)) Answer.CORRECT else Answer.STALE
    }

    fun read(player: Player, step: ClueStep): Boolean {
        val source = step as? TreasureTrailSourceStep ?: return false
        val assignment = current(player, source) ?: return false
        ClueInterfaces.text(player, assignment.source.question!!)
        return true
    }
}
