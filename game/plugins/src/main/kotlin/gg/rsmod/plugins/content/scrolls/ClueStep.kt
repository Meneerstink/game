package gg.rsmod.plugins.content.scrolls

/** A registered source step; its subtype validates and commits the player action. */
interface ClueStep {
    val tier: ClueScrollTier

    /** Internal persistence key, not a cache ID. Checked for collisions when steps register. */
    val persistenceId: Int get() = "${tier.name}:$mapInterfaceId:$hint".hashCode()

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
}
