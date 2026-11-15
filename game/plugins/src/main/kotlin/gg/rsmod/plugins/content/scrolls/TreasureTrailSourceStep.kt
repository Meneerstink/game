package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player

/** Explicit Wiki tier and stable source key; subtype handlers own completion. */
class TreasureTrailSourceStep(val source: TreasureTrailStepCatalogue.Step) : ClueStep {
    override val tier get() = source.tier
    override val hint get() = source.hint
    override val persistenceId get() = source.id
    override fun onAttempt(player: Player) = false
}
