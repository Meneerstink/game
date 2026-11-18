package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import java.io.File

/** Exact source/native scene pairs. A nearby same-name cupboard/crate cannot complete the clue. */
object TreasureTrailSearchTargets {
    data class Target(val key: String, val sourceId: Int, val localId: Int, val tile: Tile, val name: String) {
        fun matches(obj: GameObject, option: String, actualName: String): Boolean = obj.id == localId &&
            obj.tile == tile && option.equals("Search", true) && actualName == name
    }
    val current by lazy { parse(File(RspsPaths.data("cfg/clue/search-targets.json")).readText()) }
    fun parse(json: String): List<Target> = JsonParser().parse(json).asJsonArray.map { value -> value.asJsonObject.let { row ->
        require(row["option"].asString == "Search")
        require(row["sourceSha256"].asString.matches(Regex("[0-9a-f]{64}")) &&
            row["nativeSha256"].asString.matches(Regex("[0-9a-f]{64}")))
        val p = row["point"].asJsonArray
        Target(row["key"].asString, row["osrsId"].asInt, row["existingLocal"].asInt,
            Tile(p[0].asInt, p[1].asInt, p[2].asInt), row["name"].asString)
    } }.also { require(it.distinctBy { target -> target.key }.size == it.size) }

    fun attempt(player: Player, obj: GameObject, option: Int): Boolean {
        val definition = player.world.definitions.get(ObjectDef::class.java, obj.id)
        val label = definition.options.getOrNull(option - 1) ?: return false
        for (tier in ClueScrollTier.values()) {
            val step = ClueScrollManager.activeStep(player, tier) as? TreasureTrailSourceStep ?: continue
            if (step.source.action != TreasureTrailStepCatalogue.Action.SEARCH) continue
            val target = current.singleOrNull { it.key == step.source.key } ?: continue
            if (!target.matches(obj, label, definition.name)) continue
            return ClueStepContract.capture(player, step)?.advance(player) ?: false
        }
        return false
    }
}
