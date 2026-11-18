package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import java.io.File

/** Restricted source-pinned challenge selection; optional missing alternatives never bypass a requirement. */
class TreasureTrailTaskCatalogue private constructor(val falo: List<Falo>, val sherlock: List<Sherlock>) {
    data class Falo(val key: String, val lyric: String, val items: List<TreasureTrailStepCatalogue.Equipment>) {
        val id get() = key.take(8).toLong(16).toInt()
        fun accepts(id: Int): Boolean = items.any { it.local() == id }
    }
    data class Sherlock(val key: String, val tier: ClueScrollTier, val text: String, val requirements: Map<String, Int>)

    companion object {
        val current by lazy { parse(File(RspsPaths.data("cfg/clue/challenges.json")).readText()) }
        fun parse(json: String): TreasureTrailTaskCatalogue {
            val root = JsonParser().parse(json).asJsonObject
            val falo = root["falo"].asJsonArray.map { value -> value.asJsonObject.let { row ->
                require(row["source"].asJsonObject["url"].asString == "https://oldschool.runescape.wiki/w/Falo_the_Bard")
                Falo(row["key"].asString, row["lyric"].asString, row["items"].asJsonArray.map { item -> item.asJsonObject.let {
                    TreasureTrailStepCatalogue.Equipment(it["osrs"].asInt, it["existingLocal"].takeUnless { n -> n.isJsonNull }?.asInt)
                } }).also { require(it.key.matches(Regex("[0-9a-f]{16}")) && it.lyric.isNotBlank() && it.items.isNotEmpty()) }
            } }
            val sherlock = root["sherlock"].asJsonArray.map { value -> value.asJsonObject.let { row ->
                Sherlock(row["key"].asString, ClueScrollTier.valueOf(row["tier"].asString.uppercase()), row["task"].asString,
                    row["skills"].asJsonArray.associate { it.asJsonObject.let { skill -> skill["skill"].asString to skill["level"].asInt } })
                    .also { require(it.tier in listOf(ClueScrollTier.ELITE, ClueScrollTier.MASTER) && it.text.isNotBlank() &&
                        it.requirements.isNotEmpty() && it.requirements.values.all { level -> level in 1..99 }) }
            } }
            require(falo.isNotEmpty() && falo.map { it.id }.distinct().size == falo.size)
            require(sherlock.distinctBy { it.tier to it.key }.size == sherlock.size)
            require(sherlock.distinctBy { it.tier to it.key.hashCode() }.size == sherlock.size)
            return TreasureTrailTaskCatalogue(falo, sherlock)
        }
    }
}
