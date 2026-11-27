package gg.rsmod.plugins.content.areas.poh

/**
 * CONSTRUCTION-99 (owner 2026-11-26 "construction volledig werkend ... lvl 1 tot 99 volledig alles van construction"): the furniture
 * catalogue read from the generated [PohFurnitureData] - every build hotspot of the 667 POH rooms with the pieces it offers, each
 * piece with its OSRS Wiki level, experience and materials.
 *
 * Hotspot kinds as 2009scape `BuildHotspotType`: INDIVIDUAL builds one spot; RECURSIVE builds every spot of the same hotspot in the
 * room at once (curtains, bookcases, wall decorations); LINKED builds every spot of a linked group at once, each with the piece of
 * the same index in its own list (the three rug pieces, the combat ring, the hedges, a workbench and its stool). STAIRCASE hotspots
 * build like INDIVIDUAL ones and join the levels ([PohLevels]); the throne room's and treasure room's crest spaces build like
 * RECURSIVE ones (deel 7: their pieces show the owner's heraldic crest, [PohHeraldry]).
 */
object PohFurniture {
    /** One piece: [objs] has one loc id, or one per house style ([PlayerHouse.Style] ordinal; the Wilderness theme uses whitewashed). */
    class Deco(
        val key: String,
        val objs: IntArray,
        val level: Int,
        val xp: Double,
        val materials: List<Pair<Int, Int>>,
        val tools: String,
        val name: String,
        /** The flatpack item a workbench makes of this piece (2009scape `Decoration` item), or -1. */
        val flatpack: Int = -1,
    ) {
        /** Deel 7: a crest piece ([PohHeraldry.CREST_PIECES]) has one loc per heraldic crest - [crest] is the owner's crest index. */
        val crestPiece: Boolean get() = key in PohHeraldry.CREST_PIECES

        fun obj(
            style: PlayerHouse.Style,
            crest: Int = PohHeraldry.DEFAULT_CREST,
        ): Int =
            when {
                objs.size == 1 -> objs[0]
                crestPiece -> objs[crest.coerceIn(0, objs.size - 1)]
                else -> objs[styleIndex(style).coerceAtMost(objs.size - 1)]
            }

        val watering: Boolean get() = 'W' in tools
    }

    class Hotspot(
        val key: String,
        val ids: IntArray,
        val type: String,
        val anim: Int,
        val decos: List<Deco?>,
    )

    val DECOS: Map<String, Deco> =
        PohFurnitureData.DECOS.associate { line ->
            val p = line.split('|')
            val mats =
                if (p[4].isEmpty()) emptyList() else p[4].split(',').map { m -> m.substringBefore(':').toInt() to m.substringAfter(':').toInt() }
            p[0] to Deco(p[0], p[1].split(',').map(String::toInt).toIntArray(), p[2].toInt(), p[3].toDouble(), mats, p[5], p[6], p.getOrNull(7)?.toIntOrNull() ?: -1)
        }

    private val ANIMS = mapOf("BUILD_LOW_ANIM" to 3683, "BUILD_MID_ANIM" to 3676, "BUILD_HIGH_ANIM" to 3684, "PLANT_ANIM" to 3691)

    val HOTSPOTS: List<Hotspot> =
        PohFurnitureData.HOTSPOTS.map { line ->
            val p = line.split('|')
            Hotspot(p[0], p[1].split(',').map(String::toInt).toIntArray(), p[2], ANIMS[p[3]] ?: 3676, p[4].split(',').filter { it.isNotEmpty() }.map { DECOS[it] })
        }

    /**
     * Buildable hotspots (INDIVIDUAL / RECURSIVE / LINKED / STAIRCASE) with at least one piece that has an OSRS source.
     * 2009scape's STAIRWAYS_DUNGEON repeats the halls' staircase hotspots (15380/15390) per style; the halls' own entries serve.
     */
    val BUILDABLE: List<Hotspot> =
        HOTSPOTS.filter { it.type in setOf("INDIVIDUAL", "RECURSIVE", "LINKED", "STAIRCASE", "HABITAT") && it.key != "STAIRWAYS_DUNGEON" && it.decos.any { d -> d != null } }

    /**
     * Deel 6 (menagerie): a HABITAT hotspot is the room's 66 floor tiles (space 44843+i); one build fills them all, each tile with
     * the habitat's own loc i ([habitatObj]); the saved value is "KEY#i".
     */
    val HABITAT_DECOS: Set<String> = HOTSPOTS.filter { it.type == "HABITAT" }.flatMap { h -> h.decos.mapNotNull { it?.key } }.toSet()

    fun habitatObj(
        h: Hotspot,
        deco: Deco,
        loc: Int,
    ): Int = deco.objs.getOrNull(h.ids.indexOf(loc)) ?: -1

    /**
     * Nachtrun 27-11 (sweep): the Wilderness theme's template (OSRS region 8302, DMM2 import) carries the parlour's and costume
     * room's hotspots on renumbered ids 67005-67021 (same tiles and rotations as 15410-15419 / 18810-18815, checked with
     * `condev tmpl <style> <x> <z> pos`). They build as the 2006 hotspot and save under its id ([canonical]), so a house keeps its
     * furniture when the style changes.
     */
    val ALIASES: Map<Int, Int> =
        mapOf(
            67005 to 15410, 67006 to 15411, 67007 to 15412, 67008 to 15413, 67009 to 15414, 67010 to 15415, 67011 to 15416,
            67012 to 15418, 67013 to 15419,
            67016 to 18810, 67017 to 18811, 67018 to 18812, 67019 to 18813, 67020 to 18814, 67021 to 18815,
            // the chapel's window space (whitewashed id: the Wilderness theme builds whitewashed pieces)
            67074 to 13732,
        )

    fun canonical(loc: Int): Int = ALIASES[loc] ?: loc

    private val BY_OBJ: Map<Int, Hotspot> = BUILDABLE.flatMap { h -> h.ids.distinct().map { it to h } }.toMap()

    fun hotspotOf(obj: Int): Hotspot? = BY_OBJ[canonical(obj)]

    /** Every hotspot loc id a house may show in building mode. */
    val HOTSPOT_IDS: Set<Int> = BY_OBJ.keys + ALIASES.keys

    private val GROUP_OF: Map<String, List<Hotspot>> =
        PohFurnitureData.LINKED.flatMap { line ->
            val group = line.split(',').mapNotNull { k -> HOTSPOTS.firstOrNull { it.key == k } }
            group.map { it.key to group }
        }.toMap()

    /** The hotspots a build on [h] fills together (itself for INDIVIDUAL / RECURSIVE). */
    fun group(h: Hotspot): List<Hotspot> = if (h.type == "LINKED") GROUP_OF[h.key] ?: listOf(h) else listOf(h)

    /** Every loc a built piece can be (all styles), mapped back to its deco. */
    val PIECE_IDS: Map<Int, Deco> = DECOS.values.flatMap { d -> d.objs.filter { it > 0 }.map { it to d } }.toMap()

    /** 2009scape `HousingStyle` order: basic wood, basic stone, whitewashed, fremennik, tropical, fancy; Wilderness theme = whitewashed. */
    fun styleIndex(style: PlayerHouse.Style): Int = if (style == PlayerHouse.Style.WILDERNESS) 2 else style.ordinal
}
