package gg.rsmod.plugins.content.newplayer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.skills.summoning.SummoningPouchData
import java.io.File

/**
 * The owner-tunable numbers of the new-player foundation, read from `data/cfg/new_player.yml` (see the comments there).
 * [DEFAULTS] holds the values the file ships with, so unit tests and a missing file behave the same; a test pins the
 * file to [DEFAULTS].
 */
data class NewPlayerConfig(
    val normalXpRate: Double,
    val afkRate: Double,
    val afkXpPerTick: Double,
    val afkLevelCap: Int,
    val summoningTargetLevel: Int,
    val summoningPlan: List<PlanRow>,
    val choiceNpc: Int,
    val choiceNpcTile: Tile,
    val choiceNpcFacing: Direction,
    val starterChestItem: Int,
    val starterChestContents: List<Pair<Int, Int>>,
) {
    /** One row of the Summoning kit plan: [pouch] is used from [fromLevel] until the next row's level. */
    data class PlanRow(val pouch: SummoningPouchData, val fromLevel: Int)

    init {
        require(normalXpRate > 0.0) { "xp.normal_rate must be positive" }
        require(afkRate > 0.0 && afkXpPerTick > 0.0) { "afk_area rate and xp_per_tick must be positive" }
        require(afkLevelCap in 2..99) { "afk_area.level_cap must be 2..99" }
        require(summoningTargetLevel in 2..99) { "summoning_kit.target_level must be 2..99" }
        require(summoningPlan.isNotEmpty() && summoningPlan.first().fromLevel == 1) { "summoning_kit.plan must start at level 1" }
        require(summoningPlan.zipWithNext().all { (a, b) -> b.fromLevel > a.fromLevel }) { "summoning_kit.plan levels must rise" }
        summoningPlan.forEach { row ->
            require(row.pouch.level <= row.fromLevel) { "${row.pouch} needs Summoning ${row.pouch.level}, plan row starts at ${row.fromLevel}" }
        }
    }

    companion object {
        const val PATH = "./data/cfg/new_player.yml"

        val DEFAULTS =
            NewPlayerConfig(
                normalXpRate = 1.0,
                afkRate = 1.0,
                afkXpPerTick = 30.0,
                afkLevelCap = 55,
                summoningTargetLevel = 55,
                summoningPlan =
                    listOf(
                        PlanRow(SummoningPouchData.SPIRIT_WOLF, 1),
                        PlanRow(SummoningPouchData.GRANITE_CRAB, 16),
                        PlanRow(SummoningPouchData.BULL_ANT, 40),
                        PlanRow(SummoningPouchData.SPIRIT_TERRORBIRD, 52),
                    ),
                choiceNpc = Npcs.QUEST_GUIDE,
                choiceNpcTile = Tile(3164, 3486, 1),
                choiceNpcFacing = Direction.NORTH,
                // OSRS item 22330 "Deadman starter pack", imported as 23881 (OsrsItemImportTool batch starter-pack).
                starterChestItem = 23881,
                starterChestContents = listOf(Items.TUNA to 12, Items.DRAMEN_STAFF to 1, Items.DEADMANS_SKULL to 1),
            )

        /** The live values: the config file when the server runs from its root, else [DEFAULTS] (unit tests). */
        @Volatile
        var current: NewPlayerConfig = DEFAULTS
            private set

        fun reload(file: File = File(PATH)): NewPlayerConfig {
            current = if (file.isFile) load(file) else DEFAULTS
            return current
        }

        fun load(file: File): NewPlayerConfig {
            val root = ObjectMapper(YAMLFactory()).readValue(file, Map::class.java)
            fun section(name: String) = root[name] as? Map<*, *> ?: error("$file: missing section '$name'")
            fun Map<*, *>.num(key: String) = (this[key] as? Number) ?: error("$file: missing number '$key'")
            val xp = section("xp")
            val afk = section("afk_area")
            val kit = section("summoning_kit")
            val npc = section("choice_npc")
            val chest = section("starter_chest")
            val plan =
                (kit["plan"] as? List<*> ?: error("$file: summoning_kit.plan missing")).map { row ->
                    row as Map<*, *>
                    val name = row["pouch"]?.toString() ?: error("$file: plan row without pouch")
                    PlanRow(
                        SummoningPouchData.values().firstOrNull { it.name == name } ?: error("$file: unknown pouch $name"),
                        row.num("from_level").toInt(),
                    )
                }
            val contents =
                (chest["contents"] as? List<*> ?: emptyList<Any>()).map { row ->
                    row as Map<*, *>
                    row.num("item").toInt() to row.num("amount").toInt()
                }
            return NewPlayerConfig(
                normalXpRate = xp.num("normal_rate").toDouble(),
                afkRate = afk.num("rate").toDouble(),
                afkXpPerTick = afk.num("xp_per_tick").toDouble(),
                afkLevelCap = afk.num("level_cap").toInt(),
                summoningTargetLevel = kit.num("target_level").toInt(),
                summoningPlan = plan,
                choiceNpc = npc.num("npc").toInt(),
                choiceNpcTile = Tile(npc.num("x").toInt(), npc.num("z").toInt(), npc.num("level").toInt()),
                choiceNpcFacing =
                    when (npc["facing"]?.toString()?.lowercase()) {
                        "e" -> Direction.EAST
                        "s" -> Direction.SOUTH
                        "w" -> Direction.WEST
                        else -> Direction.NORTH
                    },
                starterChestItem = chest.num("item").toInt(),
                starterChestContents = contents,
            )
        }
    }
}
