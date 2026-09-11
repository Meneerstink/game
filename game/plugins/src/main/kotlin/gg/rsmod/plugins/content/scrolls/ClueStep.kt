package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player

/**
 * A single Treasure Trail step (dig-tile, emote-at-location, etc). Concrete step
 * implementations are registered per [tier] with [ClueScrollManager.registerStep]
 * and are ported incrementally, tier by tier, from the Novite donor's
 * `player/content/scrolls/impl` step classes.
 */
interface ClueStep {
    val tier: ClueScrollTier

    /**
     * Text shown to the player when they read a scroll that is on this step.
     */
    val hint: String

    /**
     * If this step is a map clue, the real cache interface id of the map graphic to show
     * instead of [hint] when the scroll is read. Null for hint-text/emote-based steps.
     */
    val mapInterfaceId: Int?
        get() = null

    /**
     * Called when the player performs the action this step expects (dig, emote, etc).
     * Returns true if the step was completed.
     */
    fun onAttempt(player: Player): Boolean
}
