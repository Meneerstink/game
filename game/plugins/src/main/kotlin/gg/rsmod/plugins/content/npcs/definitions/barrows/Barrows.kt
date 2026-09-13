package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.getCombatTarget
import java.lang.ref.WeakReference

/**
 * Barrows (2011). Dig on a mound with a spade to drop into that brother's crypt; searching the
 * sarcophagus wakes the brother ("You dare disturb my rest!"), except for the one brother whose
 * crypt hides the tunnel to the catacombs. In the tunnels a random corner rope is the exit, the
 * four inner doors are locked by a puzzle, and the central chest holds the reward; opening it
 * wakes the hidden brother if he is still alive. Prayer drains every 18 seconds while
 * underground. Rewards scale with brothers killed (armour pieces) and the total combat level of
 * everything killed (runes, coins, bolt racks, key halves, dragon med helm).
 *
 * Ported from the Void donor (BarrowsMounds/BarrowsCrypts/BarrowsPuzzle/BarrowsChest and the
 * barrows_brothers data files, 634 ids identical in 667).
 */
object Barrows {
    const val OVERLAY_INTERFACE = 24
    const val PUZZLE_INTERFACE = 25
    const val BROTHER_HEAD_VARBIT = 1043
    const val KILLS_VARBIT = 463
    const val KILLED_MONSTERS_VARBIT = 464
    const val IN_TUNNEL_VARBIT = 5028
    const val PRAYER_DRAIN_TICKS = 30

    val SURFACE_X = 3520..3583
    val SURFACE_Z = 3264..3327
    val UNDERGROUND_X = 3520..3583
    val UNDERGROUND_Z = 9664..9727
    val INNER_ROOM_X = 3541..3562
    val INNER_ROOM_Z = 9684..9705
    val CHEST_TILES = listOf(Tile(3551, 9693), Tile(3550, 9694), Tile(3553, 9694), Tile(3552, 9693))

    val SELECTED_BROTHER = AttributeKey<String>(persistenceKey = "barrows_selected_brother")
    val KILLS = AttributeKey<Int>(persistenceKey = "barrows_kills")
    val KILLED_MONSTERS = AttributeKey<Int>(persistenceKey = "barrows_killed_monsters")
    val KILL_LEVELS = AttributeKey<Int>(persistenceKey = "barrows_kill_levels")
    val LOOTED = AttributeKey<Boolean>(persistenceKey = "barrows_looted")
    val EXIT_CORNER = AttributeKey<String>(persistenceKey = "barrows_exit_corner")
    /** RCV-010 C1: letters (a..p) of the tunnel doors locked for this run (Void `barrows_door_*` = true). */
    val LOCKED_DOORS = AttributeKey<String>(persistenceKey = "barrows_locked_doors")
    const val CHEST_VARBIT = 1394
    val PRAYER_DRAIN_STEP = AttributeKey<Int>()
    val CHEST_OPEN = AttributeKey<Boolean>()
    val COLLAPSE_TICKS = AttributeKey<Int>()
    val SPAWNED_BROTHERS = AttributeKey<MutableMap<String, WeakReference<Npc>>>()
    val PUZZLE_ANSWER = AttributeKey<Int>()

    enum class Brother(
        val key: String,
        val npc: Int,
        val killedVarbit: Int,
        val crypt: Tile,
        val spawn: Tile,
        val hillX: IntRange,
        val hillZ: IntRange,
        val stairs: Int,
        val sarcophagus: Int,
        val headValue: Int,
        val tunnelHeadValue: Int,
    ) {
        AHRIM("ahrim", Npcs.AHRIM_THE_BLIGHTED, 457, Tile(3557, 9703, 3), Tile(3556, 9701, 3), 3562..3567, 3286..3292, 6702, 6821, 4761, 4762),
        DHAROK("dharok", Npcs.DHAROK_THE_WRETCHED, 458, Tile(3556, 9718, 3), Tile(3556, 9716, 3), 3572..3577, 3295..3300, 6703, 6771, 4763, 4764),
        GUTHAN("guthan", Npcs.GUTHAN_THE_INFESTED, 459, Tile(3534, 9704, 3), Tile(3537, 9704, 3), 3574..3579, 3280..3284, 6704, 6773, 4765, 4766),
        KARIL("karil", Npcs.KARIL_THE_TAINTED, 460, Tile(3546, 9684, 3), Tile(3549, 9683, 3), 3563..3569, 3273..3279, 6705, 6822, 4767, 4768),
        TORAG("torag", Npcs.TORAG_THE_CORRUPTED, 461, Tile(3568, 9683, 3), Tile(3568, 9685, 3), 3552..3556, 3281..3284, 6706, 6772, 4769, 4770),
        VERAC("verac", Npcs.VERAC_THE_DEFILED, 462, Tile(3578, 9706, 3), Tile(3576, 9706, 3), 3555..3559, 3295..3299, 6707, 6823, 4771, 4772),
        ;

        val killedAttr = AttributeKey<Boolean>(persistenceKey = "barrows_${key}_killed")

        companion object {
            fun byNpc(id: Int): Brother? = values().firstOrNull { it.npc == id }
            fun byKey(key: String): Brother = values().first { it.key == key }
            fun byHill(tile: Tile): Brother? = values().firstOrNull { tile.x in it.hillX && tile.z in it.hillZ }
            fun bySarcophagus(id: Int): Brother? = values().firstOrNull { it.sarcophagus == id }
            fun byStairs(id: Int): Brother? = values().firstOrNull { it.stairs == id }
        }
    }

    /** Corner rooms of the catacombs; the exit rope appears in one of them. */
    enum class Corner(val key: String, val tile: Tile, val ropeVarbit: Int) {
        // RCV-010 C1: the 667 map's NE rope multiloc 6710 is on varbit 466 (Void barrows_rope_north_east); it was -1,
        // so a run whose exit was north-east never showed a rope.
        NORTH_EAST("north_east", Tile(3568, 9711), 466),
        NORTH_WEST("north_west", Tile(3534, 9711), 465),
        SOUTH_WEST("south_west", Tile(3534, 9677), 467),
        SOUTH_EAST("south_east", Tile(3568, 9677), 468),
    }

    /**
     * RCV-010 C1 root cause of "stuck in the tunnels": every tunnel door on the 667 map is a varbit multiloc
     * (parents 6716-6731 / 6735-6750, varbits 469-484 = Void barrows_door_a..p). Varbit 0 shows the door with
     * "Open" (6714 / 6733), varbit 1 a door with no option. This port set all sixteen to 1, so no door could be
     * opened and the solid walls trapped the player. Void `shufflePuzzle`: all doors open, except three of the
     * exit corner room's four doors and three of the four inner-room puzzle doors.
     */
    fun doorVarbit(letter: Char): Int = 469 + (letter - 'a')

    val DOOR_LETTERS = ('a'..'p').toList()

    /** Void `barrows_doors` table: the four doors of each corner room. */
    val CORNER_DOORS = mapOf(
        "north_east" to listOf('h', 'a', 'g', 'f'),
        "north_west" to listOf('a', 'b', 'd', 'c'),
        "south_west" to listOf('p', 'b', 'n', 'k'),
        "south_east" to listOf('h', 'p', 'm', 'o'),
    )

    /** Void `barrows_doors.puzzles`: the four inner-room doors. */
    val PUZZLE_LETTERS = listOf('i', 'j', 'e', 'l')

    /** Puzzle door map objects (multiloc parents on varbits 473/477/478/480, two halves each). */
    val PUZZLE_DOORS = intArrayOf(6739, 6720, 6725, 6744, 6724, 6743, 6727, 6746)

    /** The door children that carry "Open" (6713/6732 are not placed on the 667 map). */
    val TUNNEL_DOORS = intArrayOf(6714, 6733)

    class Puzzle(val options: IntArray, val choices: IntArray, val answer: Int)

    val PUZZLES = listOf(
        Puzzle(intArrayOf(6722, 6723, 6724), intArrayOf(6719, 6721, 6720), 6719),
        Puzzle(intArrayOf(6716, 6717, 6718), intArrayOf(6715, 6714, 6713), 6713),
        Puzzle(intArrayOf(6728, 6729, 6730), intArrayOf(6727, 6726, 6725), 6725),
        Puzzle(intArrayOf(6734, 6735, 6736), intArrayOf(6732, 6731, 6733), 6731),
    )

    val REWARD_ARMOUR = mapOf(
        Brother.AHRIM to intArrayOf(Items.AHRIMS_HOOD, Items.AHRIMS_ROBE_TOP, Items.AHRIMS_ROBE_SKIRT, Items.AHRIMS_STAFF),
        Brother.DHAROK to intArrayOf(Items.DHAROKS_HELM, Items.DHAROKS_PLATEBODY, Items.DHAROKS_PLATELEGS, Items.DHAROKS_GREATAXE),
        Brother.GUTHAN to intArrayOf(Items.GUTHANS_HELM, Items.GUTHANS_PLATEBODY, Items.GUTHANS_CHAINSKIRT, Items.GUTHANS_WARSPEAR),
        Brother.KARIL to intArrayOf(Items.KARILS_COIF, Items.KARILS_TOP, Items.KARILS_SKIRT, Items.KARILS_CROSSBOW),
        Brother.TORAG to intArrayOf(Items.TORAGS_HELM, Items.TORAGS_PLATEBODY, Items.TORAGS_PLATELEGS, Items.TORAGS_HAMMERS),
        Brother.VERAC to intArrayOf(Items.VERACS_HELM, Items.VERACS_BRASSARD, Items.VERACS_PLATESKIRT, Items.VERACS_FLAIL),
    )

    fun onSurface(tile: Tile): Boolean = tile.height == 0 && tile.x in SURFACE_X && tile.z in SURFACE_Z

    fun underground(tile: Tile): Boolean = tile.x in UNDERGROUND_X && tile.z in UNDERGROUND_Z

    fun inTunnels(tile: Tile): Boolean = underground(tile) && tile.height == 0

    fun inInnerRoom(tile: Tile): Boolean = tile.height == 0 && tile.x in INNER_ROOM_X && tile.z in INNER_ROOM_Z

    fun isKilled(player: Player, brother: Brother): Boolean = player.attr[brother.killedAttr] == true

    fun spawned(player: Player): MutableMap<String, WeakReference<Npc>> =
        player.attr[SPAWNED_BROTHERS] ?: HashMap<String, WeakReference<Npc>>().also { player.attr[SPAWNED_BROTHERS] = it }

    fun liveBrother(player: Player, brother: Brother): Npc? {
        val npc = spawned(player)[brother.key]?.get() ?: return null
        if (!npc.isSpawned() || npc.isDead()) {
            spawned(player).remove(brother.key)
            return null
        }
        return npc
    }

    /** Ensures a run is set up: hidden brother chosen and puzzle route rolled. */
    fun ensureRun(player: Player) {
        if (player.attr[SELECTED_BROTHER] == null || player.attr[LOOTED] == true) {
            player.attr.remove(LOOTED)
            player.attr[SELECTED_BROTHER] = Brother.values().random().key
            shufflePuzzle(player)
        }
    }

    /** Void `shufflePuzzle`: pick the exit corner, one open door of its room and one open puzzle door. */
    fun shufflePuzzle(player: Player, incorrect: Boolean = false) {
        val previouslyOpenPuzzle = PUZZLE_LETTERS.firstOrNull { it !in (player.attr[LOCKED_DOORS] ?: "") }
        val corner = Corner.values().random()
        player.attr[EXIT_CORNER] = corner.key
        val cornerDoors = CORNER_DOORS.getValue(corner.key)
        val validCornerDoor = cornerDoors.random()
        // An incorrect answer can't pick the same puzzle door again.
        val puzzleCandidates = if (incorrect && previouslyOpenPuzzle != null) PUZZLE_LETTERS - previouslyOpenPuzzle else PUZZLE_LETTERS
        val openPuzzle = puzzleCandidates.random()
        val locked = (cornerDoors.filter { it != validCornerDoor } + PUZZLE_LETTERS.filter { it != openPuzzle }).toSet()
        player.attr[LOCKED_DOORS] = DOOR_LETTERS.filter { it in locked }.joinToString("")
        refreshVarbits(player)
    }

    fun refreshVarbits(player: Player) {
        Brother.values().forEach { player.setVarbit(it.killedVarbit, if (isKilled(player, it)) 1 else 0) }
        player.setVarbit(KILLS_VARBIT, player.attr[KILLS] ?: 0)
        player.setVarbit(KILLED_MONSTERS_VARBIT, player.attr[KILLED_MONSTERS] ?: 0)
        val corner = player.attr[EXIT_CORNER]
        Corner.values().forEach { player.setVarbit(it.ropeVarbit, if (it.key == corner) 1 else 0) }
        val locked = player.attr[LOCKED_DOORS] ?: ""
        DOOR_LETTERS.forEach { player.setVarbit(doorVarbit(it), if (it in locked) 1 else 0) }
        player.setVarbit(CHEST_VARBIT, if (player.attr[CHEST_OPEN] == true) 1 else 0)
        player.setVarbit(IN_TUNNEL_VARBIT, if (inTunnels(player.tile)) 1 else 0)
    }

    fun openOverlay(player: Player) {
        refreshVarbits(player)
        player.openInterface(dest = InterfaceDestination.PVP_OVERLAY, interfaceId = OVERLAY_INTERFACE)
    }

    fun closeOverlay(player: Player) {
        player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
        player.setVarbit(BROTHER_HEAD_VARBIT, -1)
    }

    fun spawnBrother(player: Player, brother: Brother, tile: Tile): Npc? {
        if (liveBrother(player, brother) != null || isKilled(player, brother)) return null
        val world = player.world
        val npc = Npc(brother.npc, tile, world)
        npc.respawns = false
        npc.walkRadius = 4
        npc.owner = player
        if (!world.spawn(npc)) return null
        spawned(player)[brother.key] = WeakReference(npc)
        npc.forceChat(if (tile.height == 3) "You dare disturb my rest!" else "You dare steal from us!")
        world.queue {
            wait(1)
            if (npc.isSpawned() && !npc.isDead()) npc.attack(player)
            // Brothers give up after a minute out of combat.
            var idle = 0
            while (npc.isSpawned() && !npc.isDead()) {
                wait(10)
                idle = if (npc.getCombatTarget() == null) idle + 10 else 0
                if (idle >= 100 || !player.isOnline || !underground(player.tile)) {
                    if (npc.isSpawned()) world.remove(npc)
                    spawned(player).remove(brother.key)
                    break
                }
            }
        }
        return npc
    }

    fun removeBrothers(player: Player) {
        spawned(player).values.forEach { ref -> ref.get()?.let { if (it.isSpawned()) player.world.remove(it) } }
        spawned(player).clear()
    }

    fun onBrotherDeath(npc: Npc, killer: Player) {
        val brother = Brother.byNpc(npc.id) ?: return
        spawned(killer).remove(brother.key)
        killer.attr[brother.killedAttr] = true
        killer.attr[KILLS] = (killer.attr[KILLS] ?: 0) + 1
        registerMonsterKill(killer, npc)
    }

    fun registerMonsterKill(killer: Player, npc: Npc) {
        killer.attr[KILLED_MONSTERS] = (killer.attr[KILLED_MONSTERS] ?: 0) + 1
        killer.attr[KILL_LEVELS] = (killer.attr[KILL_LEVELS] ?: 0) + npc.def.combatLevel
        refreshVarbits(killer)
    }

    /** Prayer drain while underground: 8 points every 18 seconds, rising by one each time (max 13). */
    fun drainPrayer(player: Player) {
        val brother = Brother.values().random()
        player.setVarbit(BROTHER_HEAD_VARBIT, if (player.tile.height == 0) brother.tunnelHeadValue else brother.headValue)
        val step = (player.attr[PRAYER_DRAIN_STEP] ?: 0) + 1
        player.attr[PRAYER_DRAIN_STEP] = step
        val drain = (8 + step - 1).coerceAtMost(13)
        player.alterPrayerPoints(-drain)
    }

    /** Doors in the tunnels: 12/128 chance to wake a remaining brother, else a crypt creature. */
    fun onTunnelDoor(player: Player, spawnTile: Tile) {
        val world = player.world
        val roll = world.random(127)
        if (roll < 12) {
            val brother = Brother.values().firstOrNull { !isKilled(player, it) && liveBrother(player, it) == null } ?: return
            spawnBrother(player, brother, spawnTile)
            return
        }
        val id = when {
            roll < 44 -> Npcs.GIANT_CRYPT_RAT
            roll < 76 -> Npcs.BLOODWORM
            else -> Npcs.SKELETON_2037
        }
        val npc = Npc(id, spawnTile, world)
        npc.respawns = false
        npc.walkRadius = 3
        if (world.spawn(npc)) {
            world.queue {
                wait(1)
                if (npc.isSpawned() && !npc.isDead()) npc.attack(player)
                wait(200)
                if (npc.isSpawned() && !npc.isDead()) world.remove(npc)
            }
        }
    }

    /**
     * Reward roll (OSRS-derived algorithm as used by the Void donor since the 2011 formula is
     * unpublished): one armour roll per brother killed with a 1/(450 - 58 * kills) chance each,
     * then a runes/coins roll weighted by the combat levels killed (capped at 1012).
     */
    fun reward(player: Player): List<Item> {
        val world = player.world
        val items = ArrayList<Item>()
        val kills = (player.attr[KILLS] ?: 0).coerceAtMost(6)
        val killed = Brother.values().filter { isKilled(player, it) }
        if (killed.isNotEmpty()) {
            repeat(kills) {
                if (world.random(450 - 58 * kills - 1) == 0) {
                    val brother = killed.random()
                    items.add(Item(REWARD_ARMOUR.getValue(brother).random()))
                }
            }
        }
        val levels = (player.attr[KILL_LEVELS] ?: 0).coerceAtMost(1012)
        if (levels > 0) {
            val roll = world.random(levels - 1)
            val entry = when {
                roll < 380 -> Item(Items.COINS_995, 2 + world.random(772))
                roll < 505 -> Item(Items.MIND_RUNE, 253 + world.random(83))
                roll < 630 -> Item(Items.CHAOS_RUNE, 112 + world.random(27))
                roll < 755 -> Item(Items.DEATH_RUNE, 70 + world.random(13))
                roll < 880 -> Item(Items.BLOOD_RUNE, 37 + world.random(6))
                roll < 1005 -> Item(Items.BOLT_RACK, 35 + world.random(5))
                roll < 1008 -> Item(Items.LOOP_HALF_OF_A_KEY)
                roll < 1011 -> Item(Items.TOOTH_HALF_OF_A_KEY)
                else -> Item(Items.DRAGON_MED_HELM)
            }
            items.add(entry)
        }
        return items
    }

    fun loot(player: Player) {
        val items = reward(player)
        items.forEach { item ->
            val result = player.inventory.add(item)
            if (!result.hasSucceeded()) {
                val left = item.amount - result.completed
                if (left > 0) player.world.spawn(GroundItem(item.id, left, player.tile, player))
            }
        }
        if (items.isEmpty()) player.message("You find nothing of value in the chest.")
        player.attr[LOOTED] = true
        player.attr.remove(KILLS)
        player.attr.remove(KILLED_MONSTERS)
        player.attr.remove(KILL_LEVELS)
        Brother.values().forEach { player.attr.remove(it.killedAttr) }
        refreshVarbits(player)
        player.attr[COLLAPSE_TICKS] = 0
        player.message("The cave begins to collapse!")
    }

    fun resetRun(player: Player) {
        removeBrothers(player)
        player.attr.remove(PRAYER_DRAIN_STEP)
        player.attr.remove(CHEST_OPEN)
        player.attr.remove(COLLAPSE_TICKS)
        player.setVarbit(CHEST_VARBIT, 0)
    }

    fun combatLevelOf(npc: Npc): Int = npc.def.combatLevel

    /**
     * Digs into a Barrows mound with a spade, if [player] is standing on one.
     *
     * Called from the shared spade "dig" handler (gardener.plugin.kts), since the engine only
     * allows one item-option handler per (item, option) pair; that handler dispatches here when
     * the player isn't on the Pirate's Treasure dig tile.
     */
    fun digMound(player: Player): Boolean {
        val brother = Brother.byHill(player.tile) ?: return false
        player.queue {
            player.animate(Anims.DIG_SPADE)
            wait(2)
            player.message("You've broken into a crypt!")
            ensureRun(player)
            player.moveTo(brother.crypt)
            openOverlay(player)
        }
        return true
    }
}
