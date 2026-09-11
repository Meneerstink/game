package gg.rsmod.plugins.content.activity.sorceress_garden

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Objs

/**
 * Sorceress's Garden, ported from Void's `content/minigame/sorceress_garden` (SorceressGarden.kt,
 * Elementals.kt, DelMonty.kt, SqirkJuice.kt) plus its data tomls under
 * `data/minigame/sorceress's_garden/`.
 *
 * Every id below is cross-checked against this project's own generated Items.kt/Npcs.kt/Objs.kt
 * (identical numbers to Void, confirming native 667-cache content) and the gate/herb/tree/fountain
 * object ids are additionally confirmed physically placed in region 11605 via
 * `runObjectPlacementProbeTool` - real static map content, not an instanced area, so this project's
 * documented "no instance/zone manager" limit (Dungeoneering, Construction, Clan Wars full rules)
 * does not apply here.
 *
 * Patrol waypoints are Void's own `sorceress_garden.patrols.toml` coordinates, name-matched to each
 * npc's clone-suffix ordering in `sorceress_garden.npcs.toml` (id order 5533.. = autumn, 5539.. =
 * spring, 5547.. = summer, 5553.. = winter, each ascending id matching "_2", "_3", ... in the
 * patrol file) - sourced, not invented.
 */
enum class GardenSeason(
    val gateObj: Int,
    val herbObj: Int,
    val treeObj: Int,
    val sqirkItem: Int,
    val sqirkJuiceItem: Int,
    val thievingLevelReq: Int,
    val thievingXp: Double,
    /** Landing tile after opening the gate - the season's first elemental's first patrol point (a verified walkable tile inside the garden), not an invented coordinate. */
    val entryTile: Tile,
    /** clean herb item id to relative chance-weight, out of [herbTableTotal] - verbatim from Void's *_herbs_drop_table. */
    val herbTable: List<Pair<Int, Int>>,
    val herbTableTotal: Int,
    val farmingXp: Double,
) {
    WINTER(
        gateObj = Objs.GATE_21709,
        herbObj = Objs.HERBS_21671,
        treeObj = Objs.SQIRK_TREE_21769,
        sqirkItem = Items.WINTER_SQIRK,
        sqirkJuiceItem = Items.WINTER_SQIRKJUICE,
        thievingLevelReq = 0,
        thievingXp = 30.0,
        entryTile = Tile(2899, 5468),
        herbTable =
            listOf(
                Items.CLEAN_GUAM to 16,
                Items.CLEAN_MARRENTILL to 14,
                Items.CLEAN_TARROMIN to 13,
                Items.CLEAN_HARRALANDER to 11,
            ),
        herbTableTotal = 54,
        farmingXp = 25.0,
    ),
    SPRING(
        gateObj = Objs.GATE_21753,
        herbObj = Objs.HERBS_21668,
        treeObj = Objs.SQIRK_TREE_21767,
        sqirkItem = Items.SPRING_SQIRK,
        sqirkJuiceItem = Items.SPRING_SQIRKJUICE,
        thievingLevelReq = 25,
        thievingXp = 40.0,
        entryTile = Tile(2922, 5459),
        herbTable =
            listOf(
                Items.CLEAN_GUAM to 16,
                Items.CLEAN_MARRENTILL to 14,
                Items.CLEAN_TARROMIN to 13,
                Items.CLEAN_HARRALANDER to 12,
                Items.CLEAN_GUAM to 11,
                Items.CLEAN_AVANTOE to 9,
            ),
        herbTableTotal = 75,
        farmingXp = 25.0,
    ),
    AUTUMN(
        gateObj = Objs.GATE_21731,
        herbObj = Objs.HERBS_21670,
        treeObj = Objs.SQIRK_TREE_21768,
        sqirkItem = Items.AUTUMN_SQIRK,
        sqirkJuiceItem = Items.AUTUMN_SQIRKJUICE,
        thievingLevelReq = 45,
        thievingXp = 50.0,
        entryTile = Tile(2908, 5460),
        herbTable =
            listOf(
                Items.CLEAN_GUAM to 16,
                Items.CLEAN_MARRENTILL to 14,
                Items.CLEAN_TARROMIN to 13,
                Items.CLEAN_HARRALANDER to 12,
                Items.CLEAN_GUAM to 11,
                Items.CLEAN_AVANTOE to 10,
                Items.CLEAN_RANARR to 9,
                Items.CLEAN_KWUARM to 8,
                Items.CLEAN_CADANTINE to 6,
            ),
        herbTableTotal = 99,
        farmingXp = 25.0,
    ),
    SUMMER(
        gateObj = Objs.GATE_21687,
        herbObj = Objs.HERBS_21669,
        treeObj = Objs.SQIRK_TREE,
        sqirkItem = Items.SUMMER_SQIRK,
        sqirkJuiceItem = Items.SUMMER_SQIRKJUICE,
        thievingLevelReq = 65,
        thievingXp = 60.0,
        entryTile = Tile(2907, 5483),
        herbTable =
            listOf(
                Items.CLEAN_GUAM to 16,
                Items.CLEAN_MARRENTILL to 14,
                Items.CLEAN_TARROMIN to 13,
                Items.CLEAN_HARRALANDER to 12,
                Items.CLEAN_GUAM to 11,
                Items.CLEAN_AVANTOE to 10,
                Items.CLEAN_RANARR to 9,
                Items.CLEAN_KWUARM to 8,
                Items.CLEAN_CADANTINE to 7,
                Items.CLEAN_LANTADYME to 6,
                Items.CLEAN_DWARF_WEED to 5,
            ),
        herbTableTotal = 111,
        farmingXp = 25.0,
    ),
    ;

    companion object {
        /** Fountain: teleport-out point for every season, and the hub outside all four gates. */
        val FOUNTAIN_OBJ = Objs.FOUNTAIN_21764
        val EXIT_TILE = Tile(2911, 5470)

        fun bySqirk(item: Int) = values().first { it.sqirkItem == item }
    }
}

/** Elemental npc id -> real patrol waypoints (Void `sorceress_garden.patrols.toml`), plus which season it belongs to (for catching/respawn wiring). */
object GardenElementals {
    data class Elemental(val season: GardenSeason, val patrol: List<Tile>)

    val ALL: Map<Int, Elemental> =
        buildMap {
            // Autumn: ids 5533..5538 = autumn_elemental..autumn_elemental_6
            put(Npcs.AUTUMN_ELEMENTAL, Elemental(GardenSeason.AUTUMN, tiles(2908, 5460, 2898, 5460)))
            put(Npcs.AUTUMN_ELEMENTAL_5534, Elemental(GardenSeason.AUTUMN, tiles(2900, 5450, 2900, 5455)))
            put(Npcs.AUTUMN_ELEMENTAL_5535, Elemental(GardenSeason.AUTUMN, tiles(2905, 5449, 2899, 5449)))
            put(
                Npcs.AUTUMN_ELEMENTAL_5536,
                Elemental(GardenSeason.AUTUMN, tiles(2903, 5455, 2905, 5455, 2905, 5451, 2903, 5451)),
            )
            put(Npcs.AUTUMN_ELEMENTAL_5537, Elemental(GardenSeason.AUTUMN, tiles(2917, 5457, 2904, 5457)))
            put(Npcs.AUTUMN_ELEMENTAL_5538, Elemental(GardenSeason.AUTUMN, tiles(2908, 5455, 2917, 5455)))

            // Spring: ids 5539..5546 = spring_elemental..spring_elemental_8
            put(Npcs.SPRING_ELEMENTAL, Elemental(GardenSeason.SPRING, tiles(2922, 5459, 2922, 5471)))
            put(
                Npcs.SPRING_ELEMENTAL_5540,
                Elemental(GardenSeason.SPRING, tiles(2928, 5463, 2928, 5461, 2924, 5461, 2924, 5463)),
            )
            put(
                Npcs.SPRING_ELEMENTAL_5541,
                Elemental(GardenSeason.SPRING, tiles(2926, 5458, 2924, 5458, 2924, 5461, 2926, 5461)),
            )
            put(
                Npcs.SPRING_ELEMENTAL_5542,
                Elemental(GardenSeason.SPRING, tiles(2934, 5458, 2928, 5458, 2928, 5460, 2934, 5460)),
            )
            put(Npcs.SPRING_ELEMENTAL_5543, Elemental(GardenSeason.SPRING, tiles(2935, 5461, 2935, 5468)))
            put(Npcs.SPRING_ELEMENTAL_5544, Elemental(GardenSeason.SPRING, tiles(2935, 5469, 2928, 5469)))
            put(Npcs.SPRING_ELEMENTAL_5545, Elemental(GardenSeason.SPRING, tiles(2925, 5464, 2925, 5475)))
            put(Npcs.SPRING_ELEMENTAL_5546, Elemental(GardenSeason.SPRING, tiles(2931, 5470, 2931, 5477)))

            // Summer: ids 5547..5552 = summer_elemental..summer_elemental_6
            put(Npcs.SUMMER_ELEMENTAL, Elemental(GardenSeason.SUMMER, tiles(2907, 5483, 2907, 5488)))
            put(Npcs.SUMMER_ELEMENTAL_5548, Elemental(GardenSeason.SUMMER, tiles(2907, 5490, 2907, 5495)))
            put(Npcs.SUMMER_ELEMENTAL_5549, Elemental(GardenSeason.SUMMER, tiles(2910, 5487, 2910, 5493)))
            put(
                Npcs.SUMMER_ELEMENTAL_5550,
                Elemental(
                    GardenSeason.SUMMER,
                    tiles(2915, 5485, 2915, 5483, 2918, 5483, 2918, 5485, 2915, 5485, 2915, 5483, 2912, 5483, 2912, 5485),
                ),
            )
            put(
                Npcs.SUMMER_ELEMENTAL_5551,
                Elemental(GardenSeason.SUMMER, tiles(2923, 5486, 2921, 5486, 2923, 5486, 2923, 5490)),
            )
            put(
                Npcs.SUMMER_ELEMENTAL_5552,
                Elemental(GardenSeason.SUMMER, tiles(2921, 5491, 2923, 5491, 2923, 5495, 2921, 5495)),
            )

            // Winter: ids 5553..5558 = winter_elemental..winter_elemental_6
            put(
                Npcs.WINTER_ELEMENTAL,
                Elemental(GardenSeason.WINTER, tiles(2899, 5468, 2897, 5468, 2897, 5466, 2897, 5468, 2899, 5468, 2899, 5466)),
            )
            put(Npcs.WINTER_ELEMENTAL_5554, Elemental(GardenSeason.WINTER, tiles(2897, 5470, 2891, 5470)))
            put(
                Npcs.WINTER_ELEMENTAL_5555,
                Elemental(GardenSeason.WINTER, tiles(2897, 5471, 2899, 5471, 2899, 5478, 2897, 5478)),
            )
            put(
                Npcs.WINTER_ELEMENTAL_5556,
                Elemental(
                    GardenSeason.WINTER,
                    tiles(2898, 5480, 2897, 5481, 2896, 5481, 2896, 5483, 2900, 5483, 2900, 5480),
                ),
            )
            put(
                Npcs.WINTER_ELEMENTAL_5557,
                Elemental(GardenSeason.WINTER, tiles(2896, 5483, 2896, 5481, 2891, 5481, 2891, 5483)),
            )
            put(Npcs.WINTER_ELEMENTAL_5558, Elemental(GardenSeason.WINTER, tiles(2889, 5485, 2900, 5485)))
        }

    private fun tiles(vararg coords: Int): List<Tile> {
        check(coords.size % 2 == 0)
        return coords.toList().chunked(2).map { (x, z) -> Tile(x, z) }
    }
}
