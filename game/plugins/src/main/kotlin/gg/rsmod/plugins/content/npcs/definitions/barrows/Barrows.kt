package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.content.mechanics.doors.VoidDoors
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.drops.DropBoost
import gg.rsmod.plugins.content.drops.DropTableFactory
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
    /**
     * Owner 2026-10-03 20:49 ("in the crypt there is no animation which lowers your prayer"): Void `barrows.varcs.toml`
     * `barrows_brother_head` id 1043 is a client VARC (map clear -1, ahrims 4761, ahrims_tunnels 4762 ... verac_tunnels 4772) that
     * the overlay interface 24 script reads to show the draining brother's face. It was written as a varbit (a different var
     * space), so the client never saw it and no face ever appeared.
     */
    const val BROTHER_HEAD_VARC = 1043

    /**
     * Owner 2026-10-04 19:12 ("i still see multiple barrows animations with the face ... they continue also outside"): the 667 client
     * script 1715 (varc 1043 transmit) puts each brother's head on its own component of interface 24 (24:8..24:13) with animation
     * 12554 - and returns at once when the varc is -1, so resetting the varc never removed a face. Every brother whose face had
     * shown kept it, and the overlay stays open on the hills. Darkan BarrowsController.process clears the face by sending item -1 to
     * the head component (`setIFItem(24, headComponentId, -1, 0)`); [clearHeads] does that for all six.
     */
    val HEAD_COMPONENTS = 8..13

    fun clearHeads(player: Player) {
        HEAD_COMPONENTS.forEach { player.setComponentItem(OVERLAY_INTERFACE, it, -1, 0) }
        player.setVarc(BROTHER_HEAD_VARC, -1)
    }
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
    val PUZZLE_DOOR = AttributeKey<GameObject>()
    val IN_TUNNEL = AttributeKey<Boolean>()

    /** OSRS chat "Your Barrows chest count is: <col=ff0000>N</col>." (RuneLite ChatCommands kill-count pattern). */
    val CHEST_COUNT = AttributeKey<Int>(persistenceKey = "barrows_chest_count")

    /** Collection log items already obtained from the chest (comma separated item ids). */
    val COLLECTION_LOG = AttributeKey<String>(persistenceKey = "barrows_collection_log")

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

    /**
     * The 32 placed tunnel door multilocs (BarrowsMapBindingTests): left leaves 6716..6731 resolve to 6714 (Open) / 6715 (locked),
     * right leaves 6735..6750 to 6733 / 6734, both on varbits 469..484 = Void `barrows_door_a..p`. Void `barrows.objs.toml`: the
     * swung-open leaves are `barrows_door_left_opened` 6713 and `barrows_door_right_opened` 6732.
     */
    val LEFT_DOOR_PARENTS = 6716..6731
    val RIGHT_DOOR_PARENTS = 6735..6750
    const val LEFT_DOOR_CLOSED = 6714
    const val RIGHT_DOOR_CLOSED = 6733
    const val LEFT_DOOR_OPENED = 6713
    const val RIGHT_DOOR_OPENED = 6732

    /** Void `enterDoor(target, def)` default `ticks = 3`: the door stays open three ticks, then swings shut. */
    const val DOOR_OPEN_TICKS = 3

    /** The door letter (a..p) of a placed tunnel door leaf, or null for anything else. */
    fun doorLetter(objectId: Int): Char? =
        when (objectId) {
            in LEFT_DOOR_PARENTS -> 'a' + (objectId - LEFT_DOOR_PARENTS.first)
            in RIGHT_DOOR_PARENTS -> 'a' + (objectId - RIGHT_DOOR_PARENTS.first)
            else -> null
        }

    /** The closed child a door leaf shows while unlocked (6714 / 6733), the id whose definition names it a "Door". */
    fun closedLeaf(objectId: Int): Int =
        when (objectId) {
            in LEFT_DOOR_PARENTS -> LEFT_DOOR_CLOSED
            in RIGHT_DOOR_PARENTS -> RIGHT_DOOR_CLOSED
            else -> -1
        }

    fun openedLeaf(objectId: Int): Int =
        when (objectId) {
            in LEFT_DOOR_PARENTS -> LEFT_DOOR_OPENED
            in RIGHT_DOOR_PARENTS -> RIGHT_DOOR_OPENED
            else -> -1
        }

    /** True when [objectId] is a tunnel door leaf whose letter is locked in this run ([LOCKED_DOORS]). */
    fun isLockedDoor(player: Player, objectId: Int): Boolean {
        val letter = doorLetter(objectId) ?: return false
        return letter in (player.attr[LOCKED_DOORS] ?: "")
    }

    /**
     * Void `DoubleDoor.open` for a Barrows tunnel door: both leaves of the pair swing to their opened ids (6713 / 6732) on the tiles
     * and rotations Void computes ([VoidDoors.plan]). [objectAt] finds the placed object of a wall type on a tile, [defOf] the
     * definition of an id. Null when [door] is no tunnel door.
     */
    fun doorSwing(
        door: GameObject,
        objectAt: (Tile, Int) -> GameObject?,
        defOf: (Int) -> ObjectDef?,
    ): List<VoidDoors.Replacement>? {
        if (doorLetter(door.id) == null) return null
        val opened = (LEFT_DOOR_PARENTS.associateWith { LEFT_DOOR_OPENED }) + (RIGHT_DOOR_PARENTS.associateWith { RIGHT_DOOR_OPENED })
        return VoidDoors.plan(
            obj = door,
            open = true,
            closedToOpened = opened,
            openedToClosed = emptyMap(),
            objectAt = objectAt,
            // The parents carry no name; their unlocked child is the "Door" Void's DoubleDoor.get looks for.
            defOf = { id -> defOf(if (doorLetter(id) != null) closedLeaf(id) else id) },
            inPlace = { false },
        )
    }

    /** Door tiles currently swung open, so a second click during the swing does not stack replacements. */
    private val swungOpen = HashSet<Tile>()

    /**
     * Owner 2026-10-03 20:48 ("i can noclip through the barrows door ... no open door animation or sound"): the crossing stepped the
     * player through the closed door. Void BarrowsCrypts "Open": `sound("barrows_door_close")` (41), `enterDoor` -> `Door.openDoor`
     * swings the double door open with the material sound `barrows_door_open` (62) and reverts after [DOOR_OPEN_TICKS] ticks with
     * the area sound `barrows_door_close` (41). The leaves are restored as the placed multiloc parents, so every client shows its
     * own varbit state again.
     */
    fun swingOpen(player: Player, door: GameObject) {
        val world = player.world
        player.playSound(Sfx.DOOR_OPEN)
        if (door.tile in swungOpen) return
        val plan =
            doorSwing(
                door,
                objectAt = { tile, type -> ObjectType.values().firstOrNull { it.value == type }?.let { world.getObject(tile, it) } },
                defOf = { world.definitions.getNullable(ObjectDef::class.java, it) },
            ) ?: return
        val originals = plan.map { it.original }
        // A wall-layer object already on a swing tile would be replaced by the opened leaf: keep it and put it back afterwards.
        val displaced =
            plan.mapNotNull { r ->
                ObjectType.values().firstOrNull { it.value == r.original.type }?.let { world.getObject(r.tile, it) }
                    ?.takeIf { existing -> originals.none { it === existing } }
            }
        originals.forEach { swungOpen.add(Tile(it.tile)) }
        originals.forEach { world.remove(it) }
        val spawned = plan.map { DynamicObject(id = it.id, type = it.original.type, rot = it.rot, tile = Tile(it.tile)).also { o -> world.spawn(o) } }
        world.queue {
            wait(DOOR_OPEN_TICKS)
            spawned.forEach { world.remove(it) }
            displaced.forEach { world.spawn(DynamicObject(it)) }
            originals.forEach { world.spawn(DynamicObject(it)) }
            originals.forEach { swungOpen.remove(it.tile) }
            world.spawn(AreaSound(tile = Tile(door.tile), id = Sfx.BARROWS_DOOR_OPEN, radius = 5, volume = 1))
        }
    }

    /** Void `barrows.ifaces.toml` barrows_puzzle (25): option1..3 = components 6/7/8, choice1..3 = buttons 2/3/5. */
    val PUZZLE_OPTION_COMPONENTS = intArrayOf(6, 7, 8)
    val PUZZLE_CHOICE_COMPONENTS = intArrayOf(2, 3, 5)

    /** Void `Player.puzzle()`: the picked choice is right when it is the puzzle's answer model. */
    fun puzzleAnswered(chosenIndex: Int, answerIndex: Int): Boolean = answerIndex >= 0 && chosenIndex == answerIndex

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
    /**
     * The nine tunnel rooms, bounded by the tunnel doors of this map (cache placements of 6716-6731 / 6735-6750: west/east walls at
     * x 3528|3541, 3545|3558, 3562|3575, south/north walls at z 9671|9684, 9688|9701, 9705|9718). Everything else in the tunnels is a
     * corridor.
     */
    private val ROOM_X = listOf(3529..3540, 3546..3557, 3563..3574)
    private val ROOM_Z = listOf(9672..9683, 9689..9700, 9706..9717)

    fun inTunnelRoom(tile: Tile): Boolean = ROOM_X.any { tile.x in it } && ROOM_Z.any { tile.z in it }

    /**
     * Varbit 5028 (varp 1270 bit 0) swaps the tunnel's darkness locs 37736 (37731 / 37732): 0 in a room (the corridors behind its
     * doors stay dark), 1 in a corridor (it is drawn). Darkan BarrowsController flips varp 1270 on every door crossed; one crossing
     * without that door (a teleport, ::noclip, a relog) inverted it for the rest of the run - owner 154436 "when walking in the
     * tunnels u see black sometimes between the doors" (seen in the agent's client: corridor black at 0, drawn at 1). It now follows
     * the player's tile, which is what the flip produces when nothing goes wrong.
     */
    fun syncTunnelDarkness(
        player: Player,
        force: Boolean = false,
    ) {
        val corridor = inTunnels(player.tile) && !inTunnelRoom(player.tile)
        val value = if (corridor) 1 else 0
        if (force || player.attr[DARKNESS_SENT] != value) {
            player.attr[IN_TUNNEL] = corridor
            player.attr[DARKNESS_SENT] = value
            player.setVarbit(IN_TUNNEL_VARBIT, value)
        }
    }

    private val DARKNESS_SENT = gg.rsmod.game.model.attr.AttributeKey<Int>()


    fun inInnerRoom(tile: Tile): Boolean = tile.height == 0 && tile.x in INNER_ROOM_X && tile.z in INNER_ROOM_Z

    /**
     * The puzzle is presented when entering the central room, not after the player is already
     * inside it. This is the Void BarrowsCrypts rule for the 667 map's four puzzle doors.
     */
    fun shouldSolvePuzzle(tile: Tile, objectId: Int): Boolean = objectId in PUZZLE_DOORS && !inInnerRoom(tile)

    /**
     * ObjectPathAction has already routed the player to interaction distance before the plugin
     * runs. A tunnel-door callback may therefore only perform the bounded one-door crossing; it
     * must never become a general collision-free teleport route through the crypts.
     */
    fun isAtDoor(tile: Tile, doorTile: Tile): Boolean = tile.isWithinRadius(doorTile, 1)

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

    /**
     * Owner 2026-10-03 20:50 ("not every npc is aggressive inside the barrows crypt"): OSRS Wiki Crypt rat, "As with all monsters in
     * the crypts, they are aggressive to any player regardless of their combat level." The bulk hunt mode of the crypt monsters is
     * Void's "cowardly" (combat level x2 cap), so a level-138 player was never attacked by a level-43..52 monster. Used by the shared
     * aggro check (npc_aggro.plugin.kts).
     */
    fun aggressiveRegardlessOfLevel(npc: Npc): Boolean = underground(npc.tile) && npc.id in CRYPT_MONSTERS

    /** The crypt / tunnel monsters (Void BarrowsCrypts npcDeath list, 667 ids). */
    val CRYPT_MONSTERS = setOf(Npcs.BLOODWORM, Npcs.CRYPT_RAT, Npcs.GIANT_CRYPT_RAT, Npcs.CRYPT_SPIDER, 2035, 2036, Npcs.SKELETON_2037, 5381, 5422, Npcs.GIANT_CRYPT_RAT_4920, Npcs.GIANT_CRYPT_RAT_4921)

    enum class SearchResult { HIDDEN_TUNNEL, WAKE_BROTHER, NOTHING }

    /**
     * Void BarrowsCrypts "Search" on a sarcophagus (E8, owner 2026-10-03: "the hidden-tunnel brother blocks the other five"): the
     * hidden brother's sarcophagus offers the tunnel; every other one wakes its brother unless he is already up or killed in this run
     * (kills last until the chest is looted).
     */
    fun searchResult(player: Player, brother: Brother): SearchResult =
        when {
            player.attr[SELECTED_BROTHER] == brother.key -> SearchResult.HIDDEN_TUNNEL
            liveBrother(player, brother) == null && !isKilled(player, brother) -> SearchResult.WAKE_BROTHER
            else -> SearchResult.NOTHING
        }

    /** Ensures a run is set up: hidden brother chosen and puzzle route rolled. */
    fun ensureRun(player: Player) {
        if (player.attr[SELECTED_BROTHER] == null || player.attr[LOOTED] == true) {
            player.attr.remove(LOOTED)
            player.attr.remove(KILLS)
            player.attr.remove(KILLED_MONSTERS)
            player.attr.remove(KILL_LEVELS)
            Brother.values().forEach { player.attr.remove(it.killedAttr) }
            player.attr[SELECTED_BROTHER] = Brother.values().random().key
            shufflePuzzle(player)
        } else if (player.attr[LOCKED_DOORS] == null || player.attr[EXIT_CORNER] == null) {
            // E10 (owner 2026-10-03 "alle tunneldeuren kun je standaard openen"): a run started before the locked-door state existed
            // (the owner's save: hidden brother + 4 kills, no barrows_locked_doors) never rolled its doors, so every door was open.
            shufflePuzzle(player)
        }
    }

    /**
     * The tunnel rooms joined by each door varbit letter (469 + index) - Darkan BarrowsPath.DOORS (its "EASTEEDGE" typo for 475 fixed:
     * NORTHEAST - EASTEDGE). Corners NW/NE/SW/SE, edge rooms N/W/E/S, CHEST.
     */
    val DOOR_LINKS =
        mapOf(
            'a' to ("NW" to "NE"), 'b' to ("NW" to "SW"), 'c' to ("NW" to "W"), 'd' to ("NW" to "N"), 'e' to ("N" to "CHEST"),
            'f' to ("NE" to "N"), 'g' to ("NE" to "E"), 'h' to ("NE" to "SE"), 'i' to ("W" to "CHEST"), 'j' to ("E" to "CHEST"),
            'k' to ("SW" to "W"), 'l' to ("S" to "CHEST"), 'm' to ("SE" to "E"), 'n' to ("SW" to "S"), 'o' to ("SE" to "S"),
            'p' to ("SW" to "SE"),
        )
    private val CORNER_ROOM = mapOf("north_west" to "NW", "north_east" to "NE", "south_west" to "SW", "south_east" to "SE")

    /**
     * Darkan BarrowsPath.generateBarrowsPath (owner E10: "a fixed set of doors is locked each run, only one route leads to the
     * chest"): a link keeps the first state it gets. From the start corner one of its four doors opens; the edge rooms beside it open
     * towards their other corners; every other corner reached opens one of its two remaining doors; the chest room opens one of four.
     * Returns the locked letters.
     */
    fun generateLockedDoors(startCorner: String, random: java.util.Random = java.util.Random()): Set<Char> {
        val state = HashMap<Char, Boolean>() // letter -> open
        fun set(letter: Char, open: Boolean) { if (letter !in state) state[letter] = open }
        fun links(room: String) = DOOR_LINKS.filter { (_, ab) -> ab.first == room || ab.second == room }.keys.toList()
        fun other(letter: Char, room: String) = DOOR_LINKS.getValue(letter).let { if (it.first == room) it.second else it.first }
        fun isCorner(room: String) = room.length == 2
        val seen = LinkedHashSet<Char>()
        val start = CORNER_ROOM.getValue(startCorner)
        val paths = links(start)
        set(paths[random.nextInt(paths.size)], true)
        paths.forEach { seen += it; set(it, false) }
        for (l in paths) {
            val o = other(l, start)
            if (!isCorner(o)) {
                for (ll in links(o)) {
                    if (!seen.add(ll)) continue
                    if (isCorner(other(ll, o))) set(ll, true)
                }
            }
        }
        for (l in paths) {
            val o = other(l, start)
            if (isCorner(o)) {
                val toCheck = links(o).filter { seen.add(it) }
                for (l2 in toCheck) {
                    val o2 = other(l2, o)
                    if (!isCorner(o2) && o2 != "CHEST") {
                        for (lll in links(o2)) {
                            if (!seen.add(lll)) continue
                            if (isCorner(other(lll, o2))) set(lll, true)
                        }
                    }
                }
                if (toCheck.isNotEmpty()) set(toCheck[random.nextInt(toCheck.size)], true)
                toCheck.forEach { set(it, false) }
            }
        }
        val chest = links("CHEST")
        set(chest[random.nextInt(chest.size)], true)
        chest.forEach { set(it, false) }
        return DOOR_LETTERS.filter { state[it] == false }.toSet()
    }

    /**
     * Rolls the run's doors: exit corner (also where the hidden tunnel lands) and Darkan's door maze. [incorrect] (a wrong puzzle
     * answer) = Void BarrowsCrypts.shufflePuzzle(true) and OSRS Wiki Barrows/Strategies ("the doors and rooms within the tunnels will
     * shift around, meaning that the exit ladder will be in a different room and the doors that can be opened will be in a different
     * arrangement"): the whole layout is rolled again, and the open puzzle door is never the one that was open before. (Was: only the
     * chest door moved - owner 03-2050 "when u enter wrong puzzle the doors need to reset".)
     */
    fun shufflePuzzle(player: Player, incorrect: Boolean = false) {
        val previouslyOpenPuzzle = (player.attr[LOCKED_DOORS] ?: "").let { locked -> PUZZLE_LETTERS.firstOrNull { it !in locked } }
        var corner: Corner
        var locked: Set<Char>
        var tries = 0
        do {
            corner = Corner.values().random()
            locked = generateLockedDoors(corner.key)
            tries++
        } while (incorrect && previouslyOpenPuzzle != null && previouslyOpenPuzzle !in locked && tries < 64)
        player.attr[EXIT_CORNER] = corner.key
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
        // Void BarrowsCrypts: `barrows_in_tunnel` starts false on entering and toggles with every tunnel door crossed.
        syncTunnelDarkness(player, force = true)
    }

    fun openOverlay(player: Player) {
        refreshVarbits(player)
        player.openInterface(dest = InterfaceDestination.PVP_OVERLAY, interfaceId = OVERLAY_INTERFACE)
    }

    fun closeOverlay(player: Player) {
        clearHeads(player)
        player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
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
            // Darkan BarrowsBrother extends OwnedNPC (owner 03-2046 "Verac loses aggro after one hit and clicking away"): the brother's
            // only possible target is his owner, re-targeted every tick he is out of combat; OwnedNPC.processNPC removes him when the
            // owner is more than 15 tiles away (here also offline / out of the crypts).
            while (npc.isSpawned() && !npc.isDead()) {
                wait(1)
                val gone = !player.isOnline || player.isDead() || !underground(player.tile) ||
                    player.tile.height != npc.tile.height || !player.tile.isWithinRadius(npc.tile, 15)
                if (gone) {
                    if (npc.isSpawned()) world.remove(npc)
                    spawned(player).remove(brother.key)
                    break
                }
                if (npc.getCombatTarget() == null) npc.attack(player)
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

    /**
     * OSRS Wiki Barrows/Strategies: "draining a considerable amount of Prayer points every 18 seconds. The amount of Prayer drained
     * begins at eight Prayer points, and increases by one for each brother killed, for a maximum of fourteen." (Was: +1 per drain.)
     */
    fun drainAmount(brothersKilled: Int): Int = (8 + brothersKilled).coerceAtMost(14)

    /** Void `barrows_brother_head`: the crypt face on the crypt plane (3), the `_tunnels` face in the tunnels (plane 0). */
    fun headValue(brother: Brother, tile: Tile): Int = if (tile.height == 0) brother.tunnelHeadValue else brother.headValue

    /** Darkan BarrowsController.resetHeadTimer: 20 + random(6) ticks between two faces (owner 154404/154545 "too often, 4 at once"). */
    fun nextDrainTicks(): Int = 20 + java.util.concurrent.ThreadLocalRandom.current().nextInt(6)

    /** Darkan BarrowsController: a face stays 3 ticks, then the component is cleared. */
    const val HEAD_SHOW_TICKS = 3

    private val HEAD_INDEX = gg.rsmod.game.model.attr.AttributeKey<Int>()

    /**
     * One prayer drain with its brother face. Darkan BarrowsController.getAndIncreaseHeadIndex walks the brothers in order and shows
     * killed brothers; with none killed yet the next brother in the order shows (OSRS: faces appear from the first drain). The varc is
     * written once per face (the old "-1 then the face in the same tick" made the overlay play twice) and cleared by the caller after
     * [HEAD_SHOW_TICKS]. Drain amount: OSRS (8 + brothers killed, [drainAmount]).
     */
    fun drainPrayer(player: Player) {
        val order = Brother.values()
        val start = player.attr[HEAD_INDEX] ?: 0
        val killedAny = order.any { isKilled(player, it) }
        var index = start
        for (i in order.indices) {
            val candidate = (start + i) % order.size
            if (!killedAny || isKilled(player, order[candidate])) {
                index = candidate
                break
            }
        }
        player.attr[HEAD_INDEX] = (index + 1) % order.size
        player.setVarc(BROTHER_HEAD_VARC, headValue(order[index], player.tile))
        player.alterPrayerPoints(-drainAmount(order.count { isKilled(player, it) }))
    }

    /** OSRS Wiki Barrows/Strategies: "No new monster spawns if a room already contains eleven enemies." */
    const val MAX_ROOM_ENEMIES = 11

    /**
     * Doors in the tunnels (OSRS Wiki Barrows/Strategies): 12/128 a remaining brother, 52/128 skeletons, 32/128 bloodworms,
     * 32/128 crypt rats; after the chest is looted every door "is guaranteed to summon one of the brothers whom you did not
     * kill on that run".
     */
    fun onTunnelDoor(player: Player, spawnTile: Tile) {
        val world = player.world
        // "Eleven enemies": familiars and pets are no enemies (owner bug 162809: his familiar filled the room's count).
        val nearby =
            world.npcs.count { n ->
                n.isSpawned() && !n.isDead() && n.tile.height == spawnTile.height && n.tile.isWithinRadius(spawnTile, 8) && underground(n.tile) &&
                    gg.rsmod.plugins.content.skills.summoning.FamiliarCapabilityTable.forNpc(n.id) == null &&
                    !gg.rsmod.plugins.content.skills.summoning.Pets.isPetNpc(n.id)
            }
        if (nearby >= MAX_ROOM_ENEMIES) return
        val collapsing = player.attr[LOOTED] == true && player.attr[COLLAPSE_TICKS] != null
        val roll = if (collapsing) 0 else world.random(127)
        if (roll < 12) {
            val brother = randomDoorBrother(player, collapsing, world.random(Brother.values().size - 1)) ?: return
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
     * The brother a tunnel door summons (owner bug 162809: "every brother needs to spawn when opening doors, completely random").
     * Darkan BarrowsController.getRandomBrother: a random brother not killed this run and not already up; OSRS Wiki
     * Barrows/Strategies: "any of the remaining Barrows brothers". Owner 2026-10-06: the hidden-tunnel ("last") brother too - he
     * comes from a door or, if still alive, at the chest (Darkan skips him here). [collapsing] is kept for the call site; after
     * looting the same brothers remain. [roll] in 0 until 6 picks among the candidates.
     */
    @Suppress("UNUSED_PARAMETER")
    fun randomDoorBrother(player: Player, collapsing: Boolean, roll: Int): Brother? {
        val candidates = Brother.values().filter { !isKilled(player, it) && liveBrother(player, it) == null }
        if (candidates.isEmpty()) return null
        return candidates[roll.coerceAtLeast(0) % candidates.size]
    }

    /**
     * Reward roll (OSRS-derived algorithm as used by the Void donor since the 2011 formula is
     * unpublished): one armour roll per brother killed with a 1/(450 - 58 * kills) chance each,
     * then a runes/coins roll weighted by the combat levels killed (capped at 1012).
     */
    /** OSRS Wiki Chest (Barrows): R = min(sum of killed monsters' combat levels, 1000) + 2 per brother killed (max 1012). */
    fun rewardPotential(levels: Int, brothersKilled: Int): Int = levels.coerceAtMost(1000) + 2 * brothersKilled.coerceIn(0, 6)

    /** OSRS Wiki Chest (Barrows): one roll, plus one per brother killed, at most seven. */
    fun rollCount(brothersKilled: Int): Int = (1 + brothersKilled.coerceIn(0, 6)).coerceAtMost(7)

    /**
     * The chest's reward for [player]. [rate] is his Annihilation drop rate (N100, owner 2026-10-18 VRAGEN 34 b = B: the Barrows
     * chest gets the boost, "zeldzaam eerst"); 1.0 = the plain OSRS roll. Each roll stays ONE result: the armour roll and the
     * table are laid out as lines of one distribution and [DropBoost] serves the rarest lines first ([chances]).
     */
    fun reward(player: Player, rate: Double = 1.0): List<Item> {
        val world = player.world
        val items = ArrayList<Item>()
        val killed = Brother.values().filter { isKilled(player, it) }
        val kills = killed.size
        val potential = rewardPotential(player.attr[KILL_LEVELS] ?: 0, kills)
        val boosted = if (rate > 1.0) chances(potential, kills, rate) else null
        repeat(rollCount(kills)) {
            if (boosted != null) {
                when (val line = DropBoost.pick(boosted, world.randomDouble())) {
                    -1 -> Unit
                    0 -> items.add(Item(REWARD_ARMOUR.getValue(killed.random()).random()))
                    else -> items.add(TABLE[line - 1].make(world))
                }
                return@repeat
            }
            // Each roll first tries the Barrows equipment: P(N) = 1 / (450 - 58 N) with N brothers killed.
            if (kills > 0 && world.random(450 - 58 * kills - 1) == 0) {
                val brother = killed.random()
                items.add(Item(REWARD_ARMOUR.getValue(brother).random()))
                return@repeat
            }
            rollTable(player, potential)?.let { items.add(it) }
        }
        return items
    }

    /** One line of the rune/coin table: the roll values below [upTo] (above the previous line) give the item [make] builds. */
    class TableLine(val upTo: Int, val item: Int, val min: Int, val max: Int) {
        /** [min]..[max] inclusive (World.random(n) is 0..n). */
        fun make(world: World): Item = Item(item, if (max > min) min + world.random(max - min) else min)

        /** The Drop Viewer's quantity text. */
        val quantity: String get() = if (max > min) "$min-$max" else "$min"
    }

    val TABLE: List<TableLine> =
        listOf(
            TableLine(380, Items.COINS_995, 2, 774),
            TableLine(505, Items.MIND_RUNE, 253, 336),
            TableLine(630, Items.CHAOS_RUNE, 112, 139),
            TableLine(755, Items.DEATH_RUNE, 70, 83),
            TableLine(880, Items.BLOOD_RUNE, 37, 43),
            TableLine(1005, Items.BOLT_RACK, 35, 40),
            TableLine(1008, Items.LOOP_HALF_OF_A_KEY, 1, 1),
            TableLine(1011, Items.TOOTH_HALF_OF_A_KEY, 1, 1),
            TableLine(1012, Items.DRAGON_MED_HELM, 1, 1),
        )

    /**
     * The chance of every result of ONE chest roll at [rate]: index 0 = the armour piece (1 / (450 - 58 N) with N brothers killed,
     * 0 without a brother), then the [TABLE] lines (their share of the 0 until [levels] roll, after the armour miss). The rest is
     * "nothing" (no kill levels). The roll and the future Drop Viewer read this one function.
     */
    fun chances(levels: Int, kills: Int, rate: Double): DoubleArray {
        val armour = if (kills > 0) 1.0 / (450 - 58 * kills) else 0.0
        val base = DoubleArray(TABLE.size + 1)
        base[0] = armour
        var previous = 0
        for ((i, line) in TABLE.withIndex()) {
            val count = (minOf(line.upTo, levels) - previous).coerceAtLeast(0)
            previous = line.upTo
            if (levels > 0) base[i + 1] = (1.0 - armour) * count / levels
        }
        return DropBoost.chances(base, rate)
    }

    private fun rollTable(player: Player, levels: Int): Item? {
        val world = player.world
        if (levels <= 0) return null
        val roll = world.random(levels - 1)
        return (TABLE.firstOrNull { roll < it.upTo } ?: TABLE.last()).make(world)
    }

    fun loot(player: Player) {
        val items = reward(player, DropTableFactory.killRate(player))
        items.forEach { item ->
            val result = player.inventory.add(item)
            if (!result.hasSucceeded()) {
                val left = item.amount - result.completed
                if (left > 0) player.world.spawn(GroundItem(item.id, left, player.tile, player))
            }
        }
        if (items.isEmpty()) player.message("You find nothing of value in the chest.")
        showReward(player, items)
        player.attr[LOOTED] = true
        // Owner 2026-10-03 20:3x (F9): no Barrows kill count after looting.
        val count = (player.attr[CHEST_COUNT] ?: 0) + 1
        player.attr[CHEST_COUNT] = count
        player.message(chestCountMessage(count))
        // N104: the Collection Log (one route for every reward; it sends the OSRS "New item added" line), then the chest's own
        // record, which the log also reads.
        runCatching { gg.rsmod.plugins.content.quests.tab.CollectionLog.record(player, "barrows", items.map { it.id to it.amount }) }
        newCollectionLogItems(player, items)
        runCatching { gg.rsmod.plugins.content.quests.tab.DeadmanTasks.onBarrowsChest(player) }
        // The run's kills stay until the next run starts (ensureRun): while the tunnels collapse every door summons a brother
        // "whom you did not kill on that run" (OSRS Wiki Barrows/Strategies).
        refreshVarbits(player)
        player.attr[COLLAPSE_TICKS] = 0
        player.message("The cave begins to collapse!")
        collapseShake(player, inTunnels(player.tile))
    }

    /** The camera is shaking for the collapse (client state; [collapseShake]). */
    val SHAKING = AttributeKey<Boolean>()

    /**
     * Darkan BarrowsController:297 sendCameraShake(3, 12, 25, 12, 25) - slot 3, jitter 12, amplitude 25, frequency 12, time 25
     * (client CAM_SHAKE: index / center / amplitude / frequency / time). Owner 2026-10-06: the shake (and the falling rocks) end when
     * leaving the tunnels; it starts again on going back in while they still collapse. Called each Barrows tick.
     */
    fun collapseShake(player: Player, inTunnels: Boolean) {
        val shake = inTunnels && player.attr[COLLAPSE_TICKS] != null
        if (shake == (player.attr[SHAKING] == true)) return
        if (shake) {
            player.shakeCamera(frequency = 12, index = 3, time = 25, center = 12, amplitude = 25)
            player.attr[SHAKING] = true
        } else {
            player.resetCamera()
            player.attr.remove(SHAKING)
        }
    }

    /** The 667 Barrows chest reward window (2009scape RewardChest: interface 364, the items in layer 4). */
    const val REWARD_INTERFACE = 364
    const val REWARD_LAYER = 4

    /** Client-only inventory of the reward window's item grid (client script 150; ClientInventory accepts any id). */
    const val REWARD_CLIENT_INV = 30010

    /**
     * Owner 2026-10-06 (bug 162954: "no sound and no loot notification in the chatbox"): OSRS Wiki Chest (Barrows) - the jingle
     * "Grave Robber" (77, Void barrows.jingles.toml barrows_chest) "plays upon opening the chest" and "An interface will appear
     * showing the rewards"; owner choice also a chat line listing the loot.
     */
    fun showReward(player: Player, items: List<Item>) {
        player.playJingle(GRAVE_ROBBER_JINGLE)
        if (items.isEmpty()) return
        val defs = player.world.definitions
        player.message("Barrows chest: " + items.joinToString(", ") { "${"%,d".format(it.amount)} x ${defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, it.id).name}" } + ".")
        player.attr[REWARD_ITEMS] = items.map { Item(it) }
        player.write(gg.rsmod.game.message.impl.UpdateInvFullMessage(containerKey = REWARD_CLIENT_INV, items = items.map { Item(it) }.toTypedArray<Item?>()))
        player.openInterface(REWARD_INTERFACE, InterfaceDestination.MAIN_SCREEN)
        player.runClientScript(150, (REWARD_INTERFACE shl 16) or REWARD_LAYER, REWARD_CLIENT_INV, 4, 3, 0, -1, "Examine")
    }

    const val GRAVE_ROBBER_JINGLE = 77

    /** The items shown in the reward window, for its Examine option. */
    val REWARD_ITEMS = AttributeKey<List<Item>>()

    fun chestCountMessage(count: Int): String = "Your Barrows chest count is: <col=ff0000>$count</col>."

    /** OSRS Wiki "Collection log": the Barrows Chests entry is the 24 brothers' equipment pieces and the Bolt rack. */
    val COLLECTION_LOG_ITEMS: Set<Int> = REWARD_ARMOUR.values.flatMap { it.toList() }.toSet() + Items.BOLT_RACK

    /** Records [items] in the player's Barrows collection log and returns the ids that were new, in reward order. */
    fun newCollectionLogItems(player: Player, items: List<Item>): List<Int> {
        val have = (player.attr[COLLECTION_LOG] ?: "").split(',').mapNotNull { it.toIntOrNull() }.toMutableSet()
        val fresh = items.map { it.id }.filter { it in COLLECTION_LOG_ITEMS }.distinct().filter { have.add(it) }
        if (fresh.isNotEmpty()) player.attr[COLLECTION_LOG] = have.sorted().joinToString(",")
        return fresh
    }

    fun resetRun(player: Player) {
        if (player.attr[SHAKING] == true) player.resetCamera()
        player.attr.remove(SHAKING)
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
    /** Void BarrowsMounds: `delay(2)` between the dig and the drop into the crypt. */
    const val DIG_DELAY_TICKS = 2

    fun digMound(player: Player): Boolean {
        val brother = Brother.byHill(player.tile) ?: return false
        // Owner 2026-10-03 20:3x (F10, "there is no teleport delay in barrows"): Void BarrowsMounds digs, `delay(2)`, then teleports.
        // The first suspension of a fresh queue resumes one cycle early (QueueTask plan 8B, see DoorCrossing.cross), so the old
        // wait(2) moved the player one tick after the click with the dig animation barely started. wait(1) absorbs that cycle.
        player.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
            player.animate(Anims.DIG_SPADE)
            wait(1)
            wait(DIG_DELAY_TICKS)
            player.message("You've broken into a crypt!")
            ensureRun(player)
            player.moveTo(brother.crypt)
            openOverlay(player)
        }
        return true
    }
}
