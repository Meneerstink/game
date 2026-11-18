package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.areas.wilderness.npcs.WildernessNpcDrops
import gg.rsmod.plugins.content.drops.DropTableBuilder
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.DropTableType
import gg.rsmod.plugins.content.drops.TableBuilder
import java.io.File

/** Private exact shared tables: the unrelated legacy Gems table is not OSRS-correct. */
class ClueGuardianDropCatalogue private constructor(private val data: JsonObject) {
    val sources: Set<Int> = data["guardians"].asJsonObject.keySet().map(String::toInt).toSet()
    private fun local(row: JsonObject): Int? = ClueItems.local(row["osrs"].asInt) ?:
        row["local"]?.takeUnless { it.isJsonNull }?.asInt

    fun available(world: World, source: Int): Boolean {
        val profile = data["guardians"].asJsonObject[source.toString()]?.asJsonObject ?: return false
        fun present(row: JsonObject): Boolean = when (row["kind"].asString) {
            "nothing" -> true
            "table" -> data["shared"].asJsonObject[row["table"].asString].asJsonObject["entries"].asJsonArray.all { present(it.asJsonObject) }
            "talisman" -> data["talismans"].asJsonObject.entrySet().all { present(it.value.asJsonObject) }
            "item" -> local(row)?.let { world.definitions.getNullable(ItemDef::class.java, it) != null } == true
            else -> false
        }
        return profile["guaranteed"].asJsonArray.all { present(it.asJsonObject) } &&
            listOf("main", "pre").all { key -> profile[key].takeUnless { it.isJsonNull }?.asJsonObject?.get("entries")
                ?.asJsonArray?.all { present(it.asJsonObject) } != false }
    }

    fun table(source: Int): DropTableBuilder.() -> Unit = {
        val wealth = WildernessNpcDrops.wealth(player)
        val legends = WildernessNpcDrops.legends(player)
        apply(table(source, wealth, legends, player.tile))
    }

    /** Explicit context also makes the actual viewer/roll tree inspectable without random sampling. */
    fun table(source: Int, wealth: Boolean, legends: Boolean, tile: Tile): DropTableBuilder.() -> Unit {
        val profile = data["guardians"].asJsonObject[source.toString()]?.asJsonObject
            ?: error("Unknown clue guardian $source")
        val talisman = data["talismans"].asJsonObject[
            if (tile.x in 1920..3903 && tile.z in 9474..11263) "Chaos talisman" else "Nature talisman"].asJsonObject
        fun TableBuilder.item(row: JsonObject, slots: Int) {
            val id = local(row) ?: error("CLUE missing guardian loot asset ${row["osrs"]}")
            val low = row["minimum"]?.asInt ?: 1
            val high = row["maximum"]?.asInt ?: low
            if (low == high) obj(id, low, slots) else obj(id, low..high, slots)
        }
        fun shared(name: String): DropTableBuilder.() -> Unit {
            val definition = data["shared"].asJsonObject[name].asJsonObject
            return {
                main {
                    val removed = if (wealth && name in setOf("gems", "mega")) definition["entries"].asJsonArray
                        .filter { it.asJsonObject["kind"].asString == "nothing" }.sumOf { it.asJsonObject["slots"].asInt } else 0
                    total(definition["total"].asInt - removed)
                    for (entry in definition["entries"].asJsonArray) {
                        val row = entry.asJsonObject
                        val slots = row["slots"].asInt
                        when (row["kind"].asString) {
                            "nothing" -> if (!wealth || name !in setOf("gems", "mega")) nothing(slots)
                            "talisman" -> item(talisman, slots)
                            "item" -> item(row, slots)
                            "table" -> if (name == "gems" && row["table"].asString == "mega" && !legends) item(talisman, slots)
                                else table(shared(row["table"].asString), slots, label(row["table"].asString))
                            else -> error("Unknown guardian shared loot entry")
                        }
                    }
                }
            }
        }
        fun TableBuilder.fill(definition: JsonObject) {
            total(definition["total"].asInt)
            for (entry in definition["entries"].asJsonArray) {
                val row = entry.asJsonObject
                val slots = row["slots"].asInt
                when (row["kind"].asString) {
                    "item" -> item(row, slots)
                    "nothing" -> nothing(slots)
                    "table" -> table(shared(row["table"].asString), slots, label(row["table"].asString))
                    else -> error("Unknown guardian main loot entry")
                }
            }
        }
        return {
            guaranteed { profile["guaranteed"].asJsonArray.forEach { item(it.asJsonObject, 1) } }
            profile["main"].takeUnless { it.isJsonNull }?.asJsonObject?.let { definition -> main { fill(definition) } }
            // Wiki Mod Ash4-Jul2018: 1/1m pre-roll, then either item equally.
            profile["pre"].takeUnless { it.isJsonNull }?.asJsonObject?.let { definition -> table("pre_roll") { fill(definition) } }
        }
    }

    private fun label(key: String): String = when (key) {
        "herbs" -> "Herb drop table"
        "seeds" -> "Allotment seed drop table"
        "gems" -> "Gem drop table"
        "mega" -> "Mega-rare drop table"
        else -> error("Unknown guardian nested table $key")
    }

    companion object {
        private val registered = HashSet<Int>()
        fun playerDependent(id: Int): Boolean = id in registered
        fun register(world: World, source: Int, id: Int): Boolean {
            if (!current.available(world, source)) return false
            DropTableFactory.register(current.table(source), id)
            registered.add(id)
            return true
        }
        /** Personal clue encounters cannot send their loot to a nearby loot-share party. */
        fun drop(world: World, owner: Player, id: Int, tile: Tile) {
            val items = DropTableFactory.getDrop(owner, id, DropTableType.KILL, DropTableFactory.killRate(owner)) ?: return
            DropTableFactory.giveDrop(world, owner, id, tile, items, allowLootShare = false)
        }
        val current by lazy { parse(File(RspsPaths.data("cfg/clue/guardian-drops.json")).readText()) }
        fun parse(text: String) = ClueGuardianDropCatalogue(JsonParser().parse(text).asJsonObject)
    }
}
