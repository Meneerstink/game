package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.setVarbit

/**
 * God Wars Dungeon faction model (2011): each faction npc counts towards a per-god kill count
 * shown on overlay interface 601, boss chamber doors consume 40 kills (the Ancient Prison door
 * included, with the 667 client's 40 kill requirement), and wearing an item aligned with a
 * god makes that god's followers non-aggressive.
 *
 * Kill count varbits and object ids come from the Void donor data (634 cache, identical in 667);
 * Zaros kill count varbit 8725 and the Ancient Prison access rules come from the Novite donor.
 * Faction npc lists match the Void god_wars npc definitions and the Novite faction classes.
 */
object GodWars {
    /** Whole multi-combat dungeon (all planes). */
    val DUNGEON_X = 2816..2943
    val DUNGEON_Z = 5248..5375
    /** Ancient Prison access/chamber area, which sits north of the main dungeon bounds. */
    val ANCIENT_PRISON_X = 2899..2938
    val ANCIENT_PRISON_Z = 5190..5222

    /*
     * RCV-011 Q-043-b altars. Void `GodwarsAltars.kt` + `god_wars_dungeon.vars.toml`: refuse at full Prayer, then while
     * the persisted 10-minute `godwars_altar_recharge` clock runs, then while under attack; restore to maximum plus one
     * point per worn item of the altar's god. The recharge used to be a non-saved world-cycle attribute (lost on
     * relog/restart) and had no full-Prayer refusal. Wait/combat texts: Void and Novite `GodWars.java` agree.
     */
    const val ALTAR_RECHARGE_TICKS = 1000 // 10 minutes

    /** Persisted like Void's clock and counting down while offline. */
    val ALTAR_RECHARGE_TIMER = TimerKey(persistenceKey = "gwd_altar_recharge", tickOffline = true)

    const val MSG_ALTAR_FULL = "You already have full Prayer points."
    const val MSG_ALTAR_WAIT = "You must wait a total of 10 minutes before being able to recharge your prayer points."
    const val MSG_ALTAR_COMBAT = "You cannot recharge your prayer while engaged in combat."
    const val MSG_ALTAR_DONE = "Your prayer points feel rejuvenated."

    /** Void's check order; null when the altar may recharge. */
    fun altarRefusal(
        prayerFull: Boolean,
        recharging: Boolean,
        underAttack: Boolean,
    ): String? =
        when {
            prayerFull -> MSG_ALTAR_FULL
            recharging -> MSG_ALTAR_WAIT
            underAttack -> MSG_ALTAR_COMBAT
            else -> null
        }

    /** Void: one Prayer point above maximum per worn item of [god] (lower-case item names). */
    fun altarBonus(
        god: God,
        wornNames: List<String>,
    ): Int = wornNames.count { god.protects(it) }

    /*
     * RCV-011 Q-043-b Bandos big door (26384). Novite `inBandosPrepare` puts the stronghold at x 2823..2850, west of the
     * door, and walks an entering player to 2850 and a leaving player to 2851; Void `BandosDoor.kt` asks for 70 Strength
     * and a hammer only when `tile.x >= door.x`, i.e. from outside. The port had both reversed (free entry, gated exit).
     */
    fun outsideBandosStronghold(
        playerX: Int,
        doorX: Int,
    ): Boolean = playerX >= doorX

    fun bandosDoorDestination(outside: Boolean): Tile = Tile(if (outside) 2850 else 2851, 5334, 2)

    const val MSG_BANDOS_STRENGTH = "You need to have a Strength level of 70."
    const val MSG_BANDOS_HAMMER = "You need a suitable hammer to ring the gong."

    /*
     * RCV-011 Q-043-b Zamorak river. Void `ZamorakBridge.kt` gates on Constitution 700 life points (70 at this
     * server's 1:1 scale, current level, both directions) with the `Level.has` text; the owner GWD audit names the same
     * 70 Hitpoints rule. SOURCE_CONFLICT recorded: Novite `GodWars.java` uses Agility 70, which the port had copied.
     */
    const val ZAMORAK_RIVER_LIFEPOINTS = 70
    const val MSG_ZAMORAK_RIVER = "You need to have a Constitution level of 70."

    fun canCrossZamorakRiver(currentLifepoints: Int): Boolean = currentLifepoints >= ZAMORAK_RIVER_LIFEPOINTS

    /** Novite PlayerCombat: Kree'arra and all three airborne bodyguards reject melee. */
    fun isFlyingArmadylNpc(id: Int): Boolean =
        id in God.ARMADYL.npcs && id !in 6229..6231

    /**
     * RCV-005 root cause (owner: GWD minions not aggressive). Player aggression used the bulk table's `aggressive`
     * flag and radius 4, which is false for every spiritual creature, aviansie, imp, werewolf and vampyre, so those
     * followers never hunted. Void (rev 634) gives every GWD npc its own hunt mode instead (god_wars *.npcs.toml,
     * entity/npc/hunt_modes.toml, Hunting.kt), which this model follows:
     * - [HuntMode.GENERAL] (`aggressive`): the four generals and their bodyguards attack every player they can see,
     *   god items or not;
     * - [HuntMode.FOLLOWER] (`zamorak_aggressive` / `anti_zamorak_aggressive`, both with `godwars_aggressive`
     *   against players): attack every player they can see who wears no item of their god and no Zaros item;
     * - [HuntMode.COWARDLY] (Ancient Prison `cowardly`): attack a seen player of at most twice their combat level.
     * All modes need line of sight; `check_not_combat` never blocks inside the multi-combat dungeon.
     * SOURCE_CONFLICT (recorded, not ported): Void picks at random per spawn whether a follower hunts players or rival
     * faction npcs; the OSRS reference makes every follower aggressive to players, so followers always hunt players.
     */
    enum class HuntMode { GENERAL, FOLLOWER, COWARDLY }

    /** Void hunt_mode "aggressive": generals and bodyguards of all four chambers. */
    val GENERAL_HUNTERS = setOf(6203, 6204, 6206, 6208, 6222, 6223, 6225, 6227, 6247, 6248, 6250, 6252, 6260, 6261, 6263, 6265)

    /** Void faction npcs without a hunt_mode (goblin_god_wars_flag). */
    val PASSIVE_FACTION_NPCS = setOf(6281)

    /** Void hunt_mode "cowardly": the Ancient Prison followers. */
    val COWARDLY_HUNTERS = 13456..13459

    /** The hunt mode of a faction npc id, or null when it does not hunt players through this model (Nex and her mages). */
    fun huntMode(id: Int): HuntMode? {
        val god = God.forNpcId(id) ?: return null
        return when {
            id in GENERAL_HUNTERS -> HuntMode.GENERAL
            id in COWARDLY_HUNTERS -> HuntMode.COWARDLY
            god == God.ZAROS || id in PASSIVE_FACTION_NPCS -> null
            else -> HuntMode.FOLLOWER
        }
    }

    /** Void `hunt_range`: default 5 (Hunting.kt), 8 for Graardor and his sergeants (bandos.npcs.toml). */
    fun huntRange(id: Int): Int = if (id in 6260..6265) 8 else 5

    /** Void `check_same_god` (Hunting.wearsGodArmour): an item of the follower's god or of Zaros. */
    fun followerIgnores(player: Player, god: God): Boolean = isProtected(player, god) || isProtected(player, God.ZAROS)

    /**
     * Novite checks all five worn slots by item name. Void supplies the two complete 667 item
     * sets, so keep the slot mapping explicit and accept either set without treating inventory
     * pieces or noted items as ceremonial access.
     */
    fun hasFullAncientCeremonial(player: Player): Boolean {
        val slots = intArrayOf(
            EquipmentType.HEAD.id,
            EquipmentType.CHEST.id,
            EquipmentType.LEGS.id,
            EquipmentType.GLOVES.id,
            EquipmentType.BOOTS.id,
        )
        val sets = arrayOf(
            intArrayOf(20115, 20116, 20117, 20118, 20119),
            intArrayOf(20125, 20127, 20129, 20131, 20133),
        )
        return sets.any { ids ->
            slots.indices.all { index -> player.equipment[slots[index]]?.id == ids[index] }
        }
    }

    /** Novite's 70 Agility obstacle-pipe route across the Ancient Prison approach. */
    fun ancientPrisonObstacleDestination(tile: Tile): Tile {
        val travelingEast = tile.x < 2863
        return Tile(2863 + if (travelingEast) 0 else -3, 5219, 0)
    }

    /** Novite's preparation rectangle on the south side of the Zamorak bridge. */
    fun inZamorakPrepare(tile: Tile): Boolean = tile.x in 2884..2890 && tile.z in 5343..5352

    /** Exact Novite bridge destinations; both exits land on plane 0. */
    fun zamorakBridgeDestination(tile: Tile): Tile =
        Tile(2887, if (inZamorakPrepare(tile)) 5336 else 5346, 0)

    /** Exact orthogonal polygon from Void's `godwars_chill_area` definition. */
    fun inGodWarsChillArea(tile: Tile): Boolean =
        (tile.x in 2839..2943 && tile.z in 3712..3744) ||
            (tile.x in 2816..2943 && tile.z in 3744..3775) ||
            (tile.x in 2816..2879 && tile.z in 3775..3839)

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
            chamberExit = Tile(2863, 5354, 2), // Void god_wars.areas.toml bandos_entrance
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
            chamberExit = Tile(2839, 5295, 2), // Void god_wars.areas.toml armadyl_entrance
            doorId = 26426,
            altarId = 26288,
            npcs = setOf(
                Npcs.KREEARRA, Npcs.WINGMAN_SKREE, Npcs.FLOCKLEADER_GEERIN, Npcs.FLIGHT_KILISA,
                6232, 6233, 6234, 6235, 6236, 6237, 6238, 6239, 6240, 6241, 6242, 6243, 6244, 6245, 6246,
                6229, 6230, 6231,
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
            chamberExit = Tile(2908, 5265, 0), // Void god_wars.areas.toml saradomin_entrance
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
            chamberExit = Tile(2925, 5332, 2), // Void god_wars.areas.toml zamorak_entrance
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

    fun inDungeon(tile: Tile): Boolean =
        (tile.x in DUNGEON_X && tile.z in DUNGEON_Z) ||
            (tile.x in ANCIENT_PRISON_X && tile.z in ANCIENT_PRISON_Z)

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
