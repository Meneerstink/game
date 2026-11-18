package gg.rsmod.plugins.content.scrolls

/** Explicit Wiki tier and stable source key; subtype handlers own completion. */
class TreasureTrailSourceStep(val source: TreasureTrailStepCatalogue.Step) : ClueStep {
    override val tier get() = source.tier
    override val hint get() = source.hint
    override val mapInterfaceId get() = TreasureTrailMaps.interfaceOf(source)
    override val persistenceId get() = source.id
}
