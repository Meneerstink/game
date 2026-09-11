package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.setVarbit

/**
 * God Wars Dungeon faction model (2011): each faction npc counts towards a per-god kill count
 * shown on overlay interface 601, boss chamber doors consume 40 kills (20 for Zaros in the
 * Ancient Prison with the 667 client's 40 kill requirement), and wearing an item aligned with a
 * god makes that god's followers non-aggressive.
 *
 * Kill count varbits and object ids come from the Void donor data (634 cache, identical in 667);
 * Zaros kill count varbit 8725 from the Novite donor. Faction npc lists match the Void
 * god_wars npc definitions and the Novite faction classes.
 */
object GodWars {
    /** Whole multi-combat dungeon (all planes). */
    val DUNGEON_X = 2816..2943
    val DUNGEON_Z = 5248..5375

    val ALTAR_RECHARGE_TICKS = 1000 // 10 minutes

    val ALTAR_RECHARGE = AttributeKey<Int>()
    val PROTECTED_GODS = AttributeKey<Set<God>>()

    enum class God(
        val displayName: String,
        val varbit: Int,
        val killCount: AttributeKey<Int>,
        val keywords: List<String>,
        val excludedKeywords: List<String>,
        val chamberX: IntRange,
        val chamberZ: IntRange,
        val chamberHeight: Int,
        val chamberEntry: Tile,
        val chamberExit: Tile,
        val doorId: Int,
        val altarId: Int,
        val npcs: Set<Int>,
    ) {
        BANDOS(
            displayName = "Bandos",
            varbit = 3941,
            killCount = AttributeKey(persistenceKey = "gwd_bandos_kc"),
            keywords = listOf("bandos", "ancient mace", "granite mace", "book of war"),
            excludedKeywords = emptyList(),
            chamberX = 2864..2876,
            chamberZ = 5351..5369,
            chamberHeight = 2,
            chamberEntry = Tile(2864, 5354, 2),
            chamberExit = Tile(2862, 5357, 2),
            doorId = 26425,
            altarId = 26289,
            npcs = setOf(
                Npcs.GENERAL_GRAARDOR, Npcs.SERGEANT_STRONGSTACK, Npcs.SERGEANT_STEELWILL, Npcs.SERGEANT_GRIMSPIKE,
                6271, 6272, 6273, 6274, 6268, 6269, 6270, 6275, 6279, 6280, 6281, 6282, 6283, 374, 9184, 9185,
                6276, 6277, 6278,
            ),
        ),
        ARMADYL(
            displayName = "Armadyl",
            varbit = 3939,
            killCount = AttributeKey(persistenceKey = "gwd_armadyl_kc"),
            keywords = listOf("armadyl", "book of law"),
            excludedKeywords = listOf("pendant"),
            chamberX = 2824..2842,
            chamberZ = 5296..5308,
            chamberHeight = 2,
            chamberEntry = Tile(2839, 5296, 2),
            chamberExit = Tile(2835, 5294, 2),
            doorId = 26426,
            altarId = 26288,
            npcs = setOf(
                Npcs.KREEARRA, Npcs.WINGMAN_SKREE, Npcs.FLOCKLEADER_GEERIN, Npcs.FLIGHT_KILISA,
                6232, 6233, 6234, 6235, 6236, 6237, 6238, 6239, 6240, 6241, 6242, 6243, 6244, 6245, 6246,
                6229, 6230, 6231, 6255, 6256, 6257,
            ),
        ),
        SARADOMIN(
            displayName = "Saradomin",
            varbit = 3938,
            killCount = AttributeKey(persistenceKey = "gwd_saradomin_kc"),
            keywords = listOf("saradomin", "holy book", "holy symbol", "monk's robe", "citharede"),
            excludedKeywords = listOf("brew"),
            chamberX = 2895..2907,
            chamberZ = 5258..5272,
            chamberHeight = 0,
            chamberEntry = Tile(2907, 5265, 0),
            chamberExit = Tile(2909, 5265, 0),
            doorId = 26427,
            altarId = 26287,
            npcs = setOf(
                Npcs.COMMANDER_ZILYANA, Npcs.STARLIGHT, Npcs.GROWLER, Npcs.BREE,
                6254, 6258, 6259, 6255, 6256, 6257,
            ),
        ),
        ZAMORAK(
            displayName = "Zamorak",
            varbit = 3942,
            killCount = AttributeKey(persistenceKey = "gwd_zamorak_kc"),
            keywords = listOf("zamorak", "unholy book", "unholy symbol", "book of chaos"),
            excludedKeywords = listOf("brew"),
            chamberX = 2918..2936,
            chamberZ = 5318..5331,
            chamberHeight = 2,
            chamberEntry = Tile(2925, 5331, 2),
            chamberExit = Tile(2925, 5333, 2),
            doorId = 26428,
            altarId = 26286,
            npcs = setOf(
                Npcs.KRIL_TSUTSAROTH, Npcs.TSTANON_KARLAK, Npcs.ZAKLN_GRITCH, Npcs.BALFRUG_KREEYATH,
                6210, 6211, 6212, 6213, 6214, 6215, 6216, 6218, 3406, 6219, 6220, 6221,
            ),
        ),
        ZAROS(
            displayName = "Zaros",
            varbit = 8725,
            killCount = AttributeKey(persistenceKey = "gwd_zaros_kc"),
            keywords = listOf("ancient", "torva", "pernix", "virtus", "zaryte"),
            excludedKeywords = listOf("ancient staff", "ancient mace", "ancient book", "ancient effigy", "ancient statuette", "ancient emblem", "ancient totem", "ancient medallion"),
            chamberX = 2910..2938,
            chamberZ = 5190..5222,
            chamberHeight = 0,
            chamberEntry = Tile(2900, 5203, 0),
            chamberExit = Tile(2899, 5203, 0),
            doorId = 57258,
            altarId = -1,
            npcs = setOf(
                Npcs.NEX, Npcs.FUMUS, Npcs.UMBRA, Npcs.CRUOR, Npcs.GLACIES,
                13456, 13457, 13458, 13459,
            ),
        ),
        ;

        fun inChamber(tile: Tile): Boolean = tile.height == chamberHeight && tile.x in chamberX && tile.z in chamberZ

        /** Whether [name] (lower-case item name) is aligned with this god. */
        fun protects(name: String): Boolean =
            keywords.any { name.contains(it) } && excludedKeywords.none { name.contains(it) }

        companion object {
            private val byNpc = HashMap<Int, God>().apply {
                values().forEach { god -> god.npcs.forEach { put(it, god) } }
            }

            fun forNpc(npc: Npc): God? = byNpc[npc.id]

            fun forNpcId(id: Int): God? = byNpc[id]
        }
    }

    fun inDungeon(tile: Tile): Boolean = tile.x in DUNGEON_X && tile.z in DUNGEON_Z

    fun getKillCount(player: Player, god: God): Int = player.attr[god.killCount] ?: 0

    fun setKillCount(player: Player, god: God, amount: Int) {
        player.attr[god.killCount] = amount.coerceAtLeast(0)
        player.setVarbit(god.varbit, amount.coerceAtLeast(0))
    }

    fun incrementKillCount(player: Player, god: God) = setKillCount(player, god, getKillCount(player, god) + 1)

    fun refreshKillCounts(player: Player) {
        God.values().forEach { player.setVarbit(it.varbit, getKillCount(player, it)) }
    }

    fun resetKillCounts(player: Player) {
        God.values().forEach { setKillCount(player, it, 0) }
    }

    /** Recomputes which gods the player is protected from, based on worn equipment names. */
    fun refreshProtection(player: Player): Set<God> {
        val gods = HashSet<God>()
        for (slot in 0 until player.equipment.capacity) {
            val item = player.equipment[slot] ?: continue
            val name = item.getName(player.world.definitions).lowercase()
            God.values().forEach { god -> if (god.protects(name)) gods.add(god) }
        }
        player.attr[PROTECTED_GODS] = gods
        return gods
    }

    fun isProtected(player: Player, god: God): Boolean =
        (player.attr[PROTECTED_GODS] ?: refreshProtection(player)).contains(god)

    /**
     * Faction aggression: followers attack every player inside the dungeon who does not wear
     * an item of their god, regardless of combat level (2011 behaviour), and never attack
     * outside of it beyond default rules.
     */
    val factionAggro: (Npc, Player) -> Boolean = { npc, player ->
        val god = God.forNpc(npc)
        god == null || !isProtected(player, god)
    }
}
