package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import java.io.File

/** Pinned Transcript:Uri, excluding historical phrases, and dual-cache native identity audit. */
class TreasureTrailUri private constructor(
    val phrases: List<String>, val playerReply: String, val refusal: String, val examine: String,
    private val actors: Map<ClueScrollTier, Int>, private val native: Map<Int, Actor>,
) {
    data class Actor(val bas: Int, val heads: List<Int>)
    fun actor(world: World, tier: ClueScrollTier): Int? = actors[tier]?.takeIf { id ->
        world.definitions.getNullable(NpcDef::class.java, id)?.let {
            it.name == "Uri" && it.combatLevel.coerceAtLeast(0) == 0 && it.size == 1 && it.basId == native.getValue(id).bas &&
                it.options.map { option -> option?.takeUnless(String::isBlank) } == listOf("Talk-to", null, null, null, null) &&
                it.chatheadModels?.toList() == native.getValue(id).heads
        } == true
    }
    fun install(world: World) {
        actors.keys.mapNotNull { actor(world, it) }.distinct().forEach { id ->
            world.definitions.get(NpcDef::class.java, id).examine = examine
        }
    }
    companion object {
        val current by lazy { parse(File(RspsPaths.data("cfg/clue/uri.json")).readText()) }
        fun parse(json: String): TreasureTrailUri {
            val root = JsonParser().parse(json).asJsonObject
            val phrases = root["phrases"].asJsonArray.map { it.asString }
            require(phrases.size == 25 && phrases.distinct().size == 25 && phrases.all(String::isNotBlank))
            val tiers = root["tiers"].asJsonObject.entrySet().associate { (key, value) ->
                val row = value.asJsonObject
                require(row["sourceSha256"].asString.matches(Regex("[0-9a-f]{64}")))
                ClueScrollTier.valueOf(key.uppercase()) to row["local"].asInt
            }
            require(tiers.keys == ClueScrollTier.values().toSet())
            val native = root["native"].asJsonObject.entrySet().associate { (key, value) ->
                val row = value.asJsonObject
                require(row["sha256"].asString.matches(Regex("[0-9a-f]{64}")))
                val definition = row["definition"].asJsonObject
                key.toInt() to Actor(definition["bas"].asInt, definition["heads"].asJsonArray.map { it.asInt })
            }
            require(tiers.values.all { native.containsKey(it) })
            return TreasureTrailUri(phrases, root["player"].asString, root["refusal"].asString, root["examine"].asString, tiers, native)
        }
    }
}
