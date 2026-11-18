package gg.rsmod.plugins.content.areas.watson

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.scrolls.ClueItems
import gg.rsmod.plugins.content.scrolls.ClueScrollTier
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/** Exact, mutually exclusive bonus outcomes, from the pinned Wiki/cache (not a normal clue-roll replacement). */
class MimicRewards private constructor(val tables: Map<ClueScrollTier, Table>) {
    data class Row(val osrs: Int, val name: String, val maximum: Int, val scales: Boolean,
                   val firstN: Int, val firstD: Int, val laterN: Int, val laterD: Int) {
        fun chance(failures: Int): Double = if (failures == 0) firstN.toDouble() / firstD else laterN.toDouble() / laterD
    }
    data class Table(val attempts: Int, val plank: Int, val case: Int, val rows: List<Row>)
    data class Bonus(val osrs: Int, val amount: Int, val rare: Boolean = false)

    fun missing(tier: ClueScrollTier, local: (Int) -> Int? = ClueItems::local): Set<Int> = tables.getValue(tier).let {
        (it.rows.map { row -> row.osrs } + it.plank + it.case).filter { id -> local(id) == null }.toSet()
    }

    fun roll(tier: ClueScrollTier, failures: Int, random: Random = Random.Default): List<Bonus> {
        val table = tables.getValue(tier)
        require(failures in 0 until table.attempts)
        var choice = random.nextDouble()
        val selected = table.rows.firstOrNull { choice -= it.chance(failures); choice < 0.0 } ?: table.rows.last()
        val quantity = if (selected.scales) selected.maximum * (table.attempts - failures) / table.attempts else selected.maximum
        return listOf(Bonus(table.plank, 1), Bonus(selected.osrs, quantity, rare = !selected.scales))
    }

    fun items(tier: ClueScrollTier, failures: Int, random: Random = Random.Default): List<Item>? {
        val missing = missing(tier)
        if (missing.isNotEmpty()) {
            println("PROBLEM CLUE Mimic bonus assets not mapped: ${missing.sorted()}; casket retained")
            return null
        }
        return roll(tier, failures, random).map { Item(ClueItems.local(it.osrs)!!, it.amount) }
    }

    companion object {
        val current by lazy { load(File(RspsPaths.data("cfg/clue/mimic-rewards.json"))) }
        fun load(file: File): MimicRewards {
            val root = JsonParser().parse(file.readText()).asJsonObject
            val tables = listOf(ClueScrollTier.ELITE, ClueScrollTier.MASTER).associateWith { tier ->
                val value = root[tier.name.lowercase()].asJsonObject
                val rows = value["outcomes"].asJsonArray.map { element ->
                    val r = element.asJsonObject
                    Row(r["osrsId"].asInt, r["name"].asString, r["maximum"].asInt, r["scales"].asBoolean,
                        r["firstNumerator"].asInt, r["firstDenominator"].asInt, r["laterNumerator"].asInt, r["laterDenominator"].asInt)
                }
                require(rows.size == 29 && listOf(0,1).all { failures -> abs(rows.sumOf { it.chance(failures) } - 1.0) < 1e-12 })
                require(rows.all { it.maximum > 0 && it.firstN > 0 && it.firstD > 0 && it.laterN > 0 && it.laterD > 0 })
                Table(value["attempts"].asInt, value["guaranteed"].asInt, value["case"].asInt, rows)
            }
            return MimicRewards(tables)
        }
    }
}
