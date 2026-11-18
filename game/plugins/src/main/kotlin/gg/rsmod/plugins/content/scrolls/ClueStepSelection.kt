package gg.rsmod.plugins.content.scrolls

import kotlin.random.Random

/** Wiki Full guide/Master, pinned revision15384814: category first, equal master category weights. */
object ClueStepSelection {
    fun choose(available: List<ClueStep>, random: Random = Random.Default): ClueStep {
        require(available.isNotEmpty())
        val tier = available.first().tier
        require(available.all { it.tier == tier })
        if (tier != ClueScrollTier.MASTER) return available[random.nextInt(available.size)]
        val categories = available.groupBy { step ->
            when (step) {
                is TreasureTrailThreePartStep -> "CRYPTIC"
                is TreasureTrailSourceStep -> step.source.kind
                else -> "legacy:${step.persistenceId}"
            }
        }.values.toList()
        val category = categories[random.nextInt(categories.size)]
        return category[random.nextInt(category.size)]
    }
}
