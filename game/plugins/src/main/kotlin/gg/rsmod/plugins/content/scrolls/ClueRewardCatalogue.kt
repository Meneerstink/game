package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/** Wiki row probabilities are marginal per reward roll, except explicitly grouped multi-item outcomes. */
class ClueRewardCatalogue private constructor(val tiers: Map<ClueScrollTier, List<Row>>) {
    data class Row(
        val osrsId: Int,
        val name: String,
        val minimum: Int,
        val maximum: Int,
        val numerator: Long,
        val denominator: Long,
        val bundle: String?,
        val sourceLine: Int,
        val shopOnly: Boolean,
        val section: String,
    ) {
        val probability: Double get() = numerator.toDouble() / denominator
    }

    data class Reward(val osrsId: Int, val amount: Int, val megaRare: Boolean = false)
    data class Outcome(val probability: Double, val rows: List<Row>)

    val sourceOutcomes: Map<ClueScrollTier, List<Outcome>> = tiers.mapValues { (_, rows) ->
        rows.groupBy { it.bundle ?: "item:${it.osrsId}:${it.sourceLine}" }.values.map { group ->
            val chance = group.first().probability
            require(group.all { it.probability == chance }) { "Different probabilities inside one clue outcome" }
            require(chance > 0.0 && chance <= 1.0)
            require(group.all { it.minimum > 0 && it.maximum >= it.minimum })
            Outcome(chance, group)
        }.also { require(abs(it.sumOf { outcome -> outcome.probability } - 1.0) < 1e-12) { "Clue outcomes do not sum to one" } }
    }

    /** Owner 14-11 22:1x: shop-only kits reroll over the rest of the weighted table. */
    val outcomes: Map<ClueScrollTier, List<Outcome>> = sourceOutcomes.mapValues { (_, entries) ->
        val allowed = entries.filter { entry -> entry.rows.none { it.shopOnly } }
        val total = allowed.sumOf { it.probability }
        require(total > 0.0)
        allowed.map { Outcome(it.probability / total, it.rows) }
    }

    /** Select a single event; a hard combat-potion event awards all three potions together. */
    fun roll(tier: ClueScrollTier, random: Random): List<Reward> {
        var choice = random.nextDouble()
        val entries = outcomes.getValue(tier)
        val chosen = entries.firstOrNull { entry -> choice -= entry.probability; choice < 0.0 } ?: entries.last()
        return chosen.rows.map { row -> Reward(row.osrsId, random.nextInt(row.minimum, row.maximum + 1), row.section.contains("mega-rare", ignoreCase = true)) }
    }

    /** Actual probability of at least one of an item across the tier's equally likely casket lengths. */
    fun casketChance(tier: ClueScrollTier, osrsId: Int): Double {
        val perRoll = outcomes.getValue(tier).filter { entry -> entry.rows.any { it.osrsId == osrsId } }.sumOf { it.probability }
        return tier.rewardRolls.map { 1.0 - Math.pow(1.0 - perRoll, it.toDouble()) }.average()
    }

    fun missing(tier: ClueScrollTier, local: (Int) -> Int?): Set<Int> = outcomes.getValue(tier).flatMap { it.rows }.map { it.osrsId }.filter { local(it) == null }.toSet()

    companion object {
        val current by lazy { load(File(RspsPaths.data("cfg/clue/rewards.json"))) }
        val entrana by lazy { load(File(RspsPaths.data("cfg/clue/rewards-entrana.json"))) }

        fun load(file: File): ClueRewardCatalogue = fromJson(file.readText())

        fun fromJson(text: String): ClueRewardCatalogue {
            val root = JsonParser().parse(text).asJsonObject
            val tiers = ClueScrollTier.values().filter { root.has(it.name.lowercase()) }.associateWith { tier ->
                root.getAsJsonArray(tier.name.lowercase()).map { value ->
                    val obj = value.asJsonObject
                    Row(obj["osrsId"].asInt, obj["name"].asString, obj["minimum"].asInt, obj["maximum"].asInt,
                        obj["numerator"].asLong, obj["denominator"].asLong, obj["bundle"]?.asString, obj["sourceLine"].asInt,
                        obj["ownerExcluded"]?.asBoolean ?: false, obj["section"]?.asString ?: "")
                }
            }
            return ClueRewardCatalogue(tiers)
        }
    }
}
