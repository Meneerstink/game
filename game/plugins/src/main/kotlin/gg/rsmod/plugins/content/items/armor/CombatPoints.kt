package gg.rsmod.plugins.content.items.armor

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS degradation points (owner 2026-10-04: "all our gear doesn't degrade correctly"; audit agent-handoffs\
 * audit-2026-10-04-degradation.md). Barrows equipment, Moon armour and the Amulet of the damned do not count combat ticks in
 * OSRS but points:
 *  - OSRS Wiki "Barrows equipment": "Each quarter consists of 250 degradable points ... A piece of barrows equipment will
 *    immediately degrade by 1 point when the player enters combat, and deduct one point for every 54 seconds (90 ticks) whilst
 *    still in combat." (100 / 75 / 50 / 25 / 0, 15 hours of combat in all.)
 *  - "Moon equipment": "The status of each item consists of 3000 degradable points ... A piece of Moon equipment will immediately
 *    degrade when the player enters combat, and deduct one more point from the total if the player is still in combat after 90
 *    ticks (54 seconds) have passed."
 *  - "Amulet of the Damned": "It will degrade at the same rate as any other barrows armour, but once it fully degrades, it will
 *    turn to dust."
 * The old per-combat-tick table ([DegradeTable]) never spent the point for entering combat, so the many short fights of PvP /
 * Deadman wore this gear out up to ~90x slower than OSRS. The points live in [ItemAttribute.DEGRADE_POINTS] on the item; an item
 * saved under the old model is read from its remaining stage ticks ([ItemAttribute.CHARGES], 90 ticks a point).
 */
object CombatPoints {
    /** "deduct one point for every 54 seconds (90 ticks) whilst still in combat". */
    const val TICKS_PER_POINT = 90

    const val DESTROY = -1

    class Chain(
        /** The never-used id (tradeable, full points), or -1. */
        val unused: Int,
        /** The used ids from the most to the fewest points, each holding an equal share of [total]. */
        val stages: List<Int>,
        val total: Int,
        /** What the item becomes at 0 points: the broken id or [DESTROY]. */
        val end: Int,
        /** Repair price per spent point (OSRS: Barrows 60 / 90 / 80 / 100 by slot, Moon 500), 0 = no repair. */
        val coinsPerPoint: Int,
        val displayName: String,
        /** Message when the item enters a lower stage (its new id), or null. */
        val stageMessage: (Int) -> String?,
    ) {
        val perStage: Int get() = total / stages.size

        fun ids(): List<Int> = listOfNotNull(unused.takeIf { it >= 0 }) + stages + listOfNotNull(end.takeIf { it >= 0 })

        /** The id an item with [points] left is shown as. */
        fun idFor(points: Int): Int =
            when {
                points <= 0 -> end
                points >= total && unused >= 0 -> stages.first()
                else -> stages[((total - points) / perStage).coerceIn(0, stages.size - 1)]
            }
    }

    val chains: List<Chain> =
        buildList {
            BarrowsPiece.values.forEach { piece ->
                val stages = (BarrowsPiece.FULL until BarrowsPiece.BROKEN).map { piece.idForStage(it) }
                add(
                    Chain(piece.idForStage(BarrowsPiece.PRISTINE), stages, 1000, piece.idForStage(BarrowsPiece.BROKEN), piece.fullRepairCost / 1000, piece.displayName) { next ->
                        if (next == piece.idForStage(BarrowsPiece.BROKEN)) {
                            "<col=ff0000>Your ${piece.displayName} has degraded completely and needs repairing."
                        } else {
                            "<col=ff0000>Your ${piece.displayName} has degraded."
                        }
                    },
                )
            }
            MoonArmour.values().forEach { armour ->
                add(Chain(armour.newId, listOf(armour.degradedId), 3000, armour.brokenId, 500, armour.name) { null })
            }
            add(Chain(Items.AMULET_OF_THE_DAMNED_FULL, listOf(Items.AMULET_OF_THE_DAMNED), 1000, DESTROY, 0, "Amulet of the damned") { null })
        }

    private val byId: Map<Int, Chain> = chains.flatMap { chain -> chain.ids().map { it to chain } }.toMap()

    fun chainFor(id: Int): Chain? = byId[id]

    /** Points left on [item] (its chain must be [chain]). */
    fun pointsOf(
        chain: Chain,
        item: Item,
    ): Int {
        item.attr[ItemAttribute.DEGRADE_POINTS]?.let { return it.coerceIn(0, chain.total) }
        return when (item.id) {
            chain.unused -> chain.total
            chain.end -> 0
            else -> {
                val stage = chain.stages.indexOf(item.id).coerceAtLeast(0)
                val stageTop = chain.total - stage * chain.perStage
                val oldTicks = item.attr[ItemAttribute.CHARGES] ?: return stageTop
                val inStage = ((oldTicks + TICKS_PER_POINT - 1) / TICKS_PER_POINT).coerceIn(1, chain.perStage)
                stageTop - chain.perStage + inStage
            }
        }
    }

    /** Percentage of the points left, for "Check" (1..100 while any point is left). */
    fun percentLeft(item: Item): Int {
        val chain = chainFor(item.id) ?: return 100
        val points = pointsOf(chain, item)
        if (points <= 0) return 0
        return ((points.toLong() * 100 + chain.total - 1) / chain.total).toInt().coerceIn(1, 100)
    }

    /** Repair price of [item]: spent points x the chain's rate (OSRS "(out of 1,000) multiplied by ..."). */
    fun repairCost(item: Item): Int {
        val chain = chainFor(item.id) ?: return 0
        return (chain.total - pointsOf(chain, item)) * chain.coinsPerPoint
    }

    data class Spent(
        /** The item afterwards, or null when it turned to dust. */
        val item: Item?,
        val message: String?,
    )

    /** One point off [item]: its new id when it crosses a stage, dust at 0 for a [DESTROY] chain. Null when it does not degrade. */
    fun spend(item: Item): Spent? {
        val chain = chainFor(item.id) ?: return null
        if (item.id == chain.end) return null
        val points = pointsOf(chain, item) - 1
        val id = chain.idFor(points)
        if (id == DESTROY) return Spent(null, "<col=ff0000>Your ${chain.displayName.lowercase().replaceFirstChar { it.uppercase() }} has crumbled to dust.")
        val next = Item(id, item.amount).copyAttr(item)
        next.attr.remove(ItemAttribute.CHARGES)
        next.attr[ItemAttribute.DEGRADE_POINTS] = points.coerceAtLeast(0)
        val message = if (id != item.id && id != chain.stages.first()) chain.stageMessage(id) else null
        return Spent(next, message)
    }
}
