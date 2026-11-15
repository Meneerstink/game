package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute

/** Captures the exact held clue before a dialogue/puzzle suspends. No account-global pending step. */
class ClueStepContract private constructor(
    private val owner: Player,
    val tier: ClueScrollTier,
    val scroll: Item,
    val step: ClueStep,
    private val key: Int,
    private val progress: Int,
) {
    fun valid(player: Player): Boolean = player === owner &&
        player.inventory.any { it === scroll } && scroll.getAttr(ItemAttribute.CLUE_STEP_KEY) == key &&
        ClueScrollManager.activeStep(player, tier) === step && ClueScrollManager.progress(player, tier) == progress

    /** Consume auxiliary items and advance only after the same exact clue has been revalidated. */
    fun advance(player: Player, consume: List<Int> = emptyList()): Boolean = valid(player) &&
        ClueScrollManager.advance(player, tier, scroll, progress, consume)

    companion object {
        fun capture(player: Player, step: ClueStep): ClueStepContract? {
            if (ClueScrollManager.activeStep(player, step.tier) !== step) return null
            val scroll = player.inventory.firstOrNull { it?.id == step.tier.scrollId } ?: return null
            val key = scroll.getAttr(ItemAttribute.CLUE_STEP_KEY) ?: return null
            if (key != step.persistenceId) return null
            return ClueStepContract(player, step.tier, scroll, step, key, ClueScrollManager.progress(player, step.tier))
        }
    }
}
