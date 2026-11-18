package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import kotlin.random.Random

/** Wiki Falo: show an unnoted requested object; keep the object and advance only this held clue. */
object TreasureTrailFalo {
    data class Offer(val contract: ClueStepContract, val task: TreasureTrailTaskCatalogue.Falo, val first: Boolean)
    enum class Match { CORRECT, WRONG, STALE }

    fun offer(player: Player, random: Random = Random.Default): Offer? {
        val step = ClueScrollManager.activeStep(player, ClueScrollTier.MASTER) as? TreasureTrailSourceStep ?: return null
        if (step.source.action != TreasureTrailStepCatalogue.Action.FALO) return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        val tasks = TreasureTrailTaskCatalogue.current.falo
        val saved = contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY)
        val task = if (saved == null) tasks.random(random) else tasks.singleOrNull { it.id == saved } ?: run {
            println("PROBLEM CLUE unknown saved Falo task $saved; scroll retained"); return null
        }
        if (saved == null) {
            contract.scroll.putAttr(ItemAttribute.CLUE_TASK_KEY, task.id)
            player.inventory.dirty = true
        }
        return Offer(contract, task, saved == null)
    }

    fun match(player: Player, offer: Offer, shown: Item): Match {
        if (!offer.contract.valid(player) || offer.contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY) != offer.task.id ||
            !player.inventory.any { it === shown }) return Match.STALE
        return if (!shown.getDef(player.world.definitions).noted && offer.task.accepts(shown.id)) Match.CORRECT else Match.WRONG
    }

    fun finish(player: Player, offer: Offer, shown: Item, messages: Pair<String, String>? = null): Boolean =
        match(player, offer, shown) == Match.CORRECT && offer.contract.advance(player, messages = messages)
}
