package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.message

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Area
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.instance.InstancedMapAttribute
import gg.rsmod.game.model.instance.InstancedMapConfiguration
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.cfg.Objs

/**
 * Owner request 2026-09-19: a player-owned house reached with a house teleport tab, holding a rejuvenation pool, a
 * combat dummy, a mounted amulet of glory and a prayer altar; follow-up "make the house a bit nicer, more options".
 * Owner 2026-09-21: "We want whitewashed for every player ... we want ornate rejuvenation pool we want nexus teleport
 * and every teleport available in the house put it in spirit tree obelisk fairy ring we want a gilded alter with
 * marble incese burners barrows armour stand make the house beautifull every player that logs in need to have this
 * automatically", and the house must be a safe zone like the OSRS Deadman Mode house.
 *
 * WHAT A HOUSE IS HERE. Two real revision-667 POH rooms plus a garden, private per player, allocated on arrival and
 * released on logout. Nothing is built by the player: every house is fully furnished the moment it is entered, which
 * is what "every player that logs in need to have this automatically" means. The only thing a player chooses is the
 * STYLE, through the Estate agent.
 *
 * THE STYLE IS THE ROOM'S OWN MAP DATA, NOT A RESKIN. Region 7503 holds four house styles, one per plane, and region
 * 7759 holds two more - the same room laid out identically on each, with that style's own walls, doors and window
 * hotspots ([Style]). Every id below was read out of this cache with the map-square loc decoder, not guessed: plane 0
 * walls are 13098, plane 1 1902, plane 2 1415, plane 3 13111, 7759 plane 0 13011 and 7759 plane 1 13116. Building a
 * style is therefore just reading the room chunks from that style's region and plane.
 *
 * WHY SPOTS ARE MATCHED BY NAME. The build-mode spots differ per style: the door hotspot is 15313/15314 on basic
 * wood but 15309/15310 on whitewashed stone, and each style has its own "Window space" id. Matching on the spot's
 * NAME ("... space", "Door hotspot") instead of on a per-style id table is what makes one build routine serve all six
 * styles. Where a spot's id IS shared across styles (rug, statue, altar, lamp, icon, portal, centrepiece), it is
 * keyed by id in [SPOT_REPLACEMENTS].
 *
 * THE ROOM'S OWN HOTSPOTS PLACE THE FURNITURE. The chapel's altar spot becomes the gilded altar, its two lamp spots
 * become lit marble incense burners, its icon spot the icon of Saradomin, its statue spots large statues and its rug
 * spots the opulent rug - so everything lands exactly where Construction would have put it. Only what has no hotspot
 * (the pool, the armour repair stand, the jewellery box, the exit portal, the combat dummy and the whole garden) is
 * placed by hand.
 *
 * OWNER 2026-10-10: "alleen dingen die teleporteren en functies hebben voor pvp pvm ... zorg voor goede inrichting".
 * The costume room's storage and the whole study are gone (stored costume items go to the bank, [PohStorage]); the
 * pieces that stayed or came in are OSRS ones where OSRS has them - pool, Portal Nexus, Altar of the Occult, Ornate
 * Jewellery Box, Spiritual Fairy Tree and the ornate combat dummy - placed where OSRS's own room templates put them
 * (OSRS cache 2499, region 7534/7535: Superior Garden teleport space 3,3 and pool space 6,0; Portal Nexus room nexus
 * 3,3 and amulet spaces on the south wall; Achievement Gallery altar 6,3 and jewellery box 6,1).
 *
 * SAFE ZONE. [isSafeTile] reports every tile of every live house, and `GuardedZones.contains` folds it into the one
 * predicate the PvP gate, the guards and the HUD already share, so a house counts as a guarded city everywhere at
 * once. The PK skull already stops counting down inside an instance
 * ([gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.tickPauseTracking] pauses on `tile.x >= 6400`), which is exactly
 * the OSRS Deadman behaviour the owner asked for, so no separate skull rule is needed here.
 */
object PlayerHouse {

    /**
     * A house style: which map square and plane its rooms are read from, the wall that replaces a door hotspot that
     * leads nowhere, and the stained-glass window that fills its window hotspots.
     *
     * Levels and prices are OSRS/2009scape's (`HousingStyle`), kept so the Estate agent can charge for a change of
     * style. [WHITEWASHED_STONE] is what every player starts with (owner 2026-09-21), regardless of Construction
     * level - the level only gates changing to a fancier style.
     */
    enum class Style(
        val displayName: String,
        private val squareX: Int,
        private val squareZ: Int,
        val plane: Int,
        val wall: Int,
        val stainedGlass: Int,
        val level: Int,
        val cost: Int,
    ) {
        BASIC_WOOD("Basic wood", 1856, 5056, 0, 13098, Objs.STAINEDGLASS_WINDOW_13255, 1, 5_000),
        BASIC_STONE("Basic stone", 1856, 5056, 1, 1902, Objs.STAINEDGLASS_WINDOW_13228, 10, 5_000),
        WHITEWASHED_STONE("Whitewashed stone", 1856, 5056, 2, 1415, Objs.STAINEDGLASS_WINDOW_13237, 20, 7_500),
        FREMENNIK_WOOD("Fremennik-style wood", 1856, 5056, 3, 13111, Objs.STAINEDGLASS_WINDOW_13246, 30, 10_000),
        TROPICAL_WOOD("Tropical wood", 1920, 5056, 0, 13011, Objs.STAINEDGLASS_WINDOW, 40, 15_000),
        FANCY_STONE("Fancy stone", 1920, 5056, 1, 13116, Objs.STAINEDGLASS_WINDOW_13264, 50, 25_000),
        ;

        /** The lawn (chunk 1,0), which fills every instance chunk the house's rooms do not. */
        val lawn: Tile get() = Tile(squareX + 1 * 8, squareZ + 0 * 8, plane)

        /** The Superior Garden (chunk 0,1): the hub, and the only POH room with a door on all four walls. */
        val garden: Tile get() = Tile(squareX + 0 * 8, squareZ + 1 * 8, plane)

        /** The chapel (chunk 2,5): gilded altar, marble burners, armour stand, jewellery box, glory, dummy. */
        val chapel: Tile get() = Tile(squareX + 2 * 8, squareZ + 5 * 8, plane)

        /** The portal chamber (chunk 1,4): three marble portals and the teleport nexus. */
        val portalChamber: Tile get() = Tile(squareX + 1 * 8, squareZ + 4 * 8, plane)

        /**
         * The PvP/PvM room (chunk 6,1): the costume room's walls, door and windows with its storage spots left empty
         * (owner 2026-10-10) - Altar of the Occult, Ornate Jewellery Box and the combat dummy ([furnishPvmRoom]).
         */
        val pvmRoom: Tile get() = Tile(squareX + 6 * 8, squareZ + 1 * 8, plane)

        /**
         * The dining room (chunk 4,7; Darkan `HouseRooms.DINING_ROOM` 236,639 - doors east, south and west; table space 2,3 4x2,
         * read from this cache). Owner 2026-10-10 night run: Construction training on its table space, every other spot empty.
         */
        val diningRoom: Tile get() = Tile(squareX + 4 * 8, squareZ + 7 * 8, plane)
    }

    /**
     * The rooms of the house plan, by their chunk offset from the garden, the rotation the plan gives the template
     * ([baseRot]: the portal chamber is turned 180 degrees to face the garden) and the side the room's doorway to the
     * garden must be on ([gardenSide], wall rotation: 0 west, 1 north, 2 east, 3 south; -1 for the garden itself).
     *
     * OSRS room rotation (owner 2026-10-10 night run: rooms rotatable in build mode): a player's own rotation is added on top
     * of [baseRot] and persisted ([ROOM_ROTATIONS_ATTR]); like OSRS (2009scape `BuildingUtils.getAvailableRotations`) only
     * the rotations that keep a doorway facing the garden are offered ([allowedRotations]).
     */
    enum class Room(val dx: Int, val dz: Int, val baseRot: Int, val gardenSide: Int, val displayName: String) {
        GARDEN(0, 0, 0, -1, "garden"),
        PVM_ROOM(0, 1, 0, 3, "PvP/PvM room"),
        PORTAL_CHAMBER(0, -1, 2, 1, "portal chamber"),
        CHAPEL(1, 0, 0, 0, "chapel"),
        DINING_ROOM(-1, 0, 0, 2, "dining room"),
        ;

        fun template(style: Style): Tile =
            when (this) {
                GARDEN -> style.garden
                PVM_ROOM -> style.pvmRoom
                PORTAL_CHAMBER -> style.portalChamber
                CHAPEL -> style.chapel
                DINING_ROOM -> style.diningRoom
            }
    }

    /** Each room's own rotation (0-3 clockwise quarter turns), two bits per [Room] ordinal. */
    val ROOM_ROTATIONS_ATTR = AttributeKey<Int>(persistenceKey = "poh_room_rotations")

    fun roomRotation(
        player: Player,
        room: Room,
    ): Int = ((player.attr[ROOM_ROTATIONS_ATTR] ?: 0) shr (room.ordinal * 2)) and 3

    fun setRoomRotation(
        player: Player,
        room: Room,
        rotation: Int,
    ) {
        val bits = (player.attr[ROOM_ROTATIONS_ATTR] ?: 0) and (3 shl (room.ordinal * 2)).inv()
        player.attr[ROOM_ROTATIONS_ATTR] = bits or ((rotation and 3) shl (room.ordinal * 2))
    }

    /**
     * The rotations of [room] that keep a doorway facing the garden: the template's door hotspots (read from the map, plane of
     * the player's style) turned by the plan rotation and then by each candidate. The garden has a doorway on every side.
     */
    fun allowedRotations(
        world: World,
        style: Style,
        room: Room,
    ): List<Int> {
        if (room.gardenSide < 0) return listOf(0, 1, 2, 3)
        val template = room.template(style)
        val sides =
            world.chunks.get(template.chunkCoords, createIfNeeded = true)!!
                .getEntities<StaticObject>(EntityType.STATIC_OBJECT)
                .filter { it.tile.height == template.height && world.definitions.get(ObjectDef::class.java, it.id).name.equals("door hotspot", true) }
                .map { (it.rot + room.baseRot) and 3 }
                .toSet()
        return (0..3).filter { r -> sides.any { ((it + r) and 3) == room.gardenSide } }
    }

    /**
     * A room's placement frame: local coordinates as the room is laid out by the plan (its [Room.baseRot] applied), turned by
     * the player's own rotation exactly as the allocator turns the room's map objects (`InstancedMapAllocator`: the object's
     * rotation plus the chunk's, the tile through [Tile.transformAndRotate] with the object's rotated size).
     */
    class RoomFrame(
        private val world: World,
        val corner: Tile,
        val rotation: Int,
    ) {
        fun tile(
            x: Int,
            z: Int,
            width: Int = 1,
            length: Int = 1,
        ): Tile = corner.transformAndRotate(x, z, rotation, width, length)

        fun spawn(
            id: Int,
            type: Int,
            rot: Int,
            x: Int,
            z: Int,
        ): DynamicObject {
            val def = world.definitions.get(ObjectDef::class.java, id)
            val width = if (rot and 1 == 1) def.length else def.width
            val length = if (rot and 1 == 1) def.width else def.length
            val obj = DynamicObject(id, type, (rot + rotation) and 3, tile(x, z, width, length))
            world.spawn(obj)
            return obj
        }
    }

    /**
     * The house's town (owner 2026-10-10 night run: every location the 667 cache has a house portal for; Rimmington stays the
     * default). Portal locs, exit tiles, levels and prices: Darkan `EntranceLocations` / 2009scape `HouseLocation`, levels as
     * OSRS (Taverley 10). [offered] is false for Yanille: OSRS Deadman Mode disables the Yanille house portal (handoff 9b).
     */
    enum class Location(
        val displayName: String,
        val portal: Int,
        val exit: Tile,
        val level: Int,
        val cost: Int,
        val offered: Boolean = true,
    ) {
        RIMMINGTON("Rimmington", Objs.PORTAL_15478, Tile(2953, 3224, 0), 1, 5_000),
        TAVERLEY("Taverley", 15477, Tile(2882, 3452, 0), 10, 5_000),
        POLLNIVNEACH("Pollnivneach", 15479, Tile(3340, 3003, 0), 20, 7_500),
        RELLEKKA("Rellekka", 15480, Tile(2670, 3631, 0), 30, 10_000),
        BRIMHAVEN("Brimhaven", 15481, Tile(2757, 3178, 0), 40, 15_000),
        YANILLE("Yanille", 15482, Tile(2544, 3096, 0), 50, 25_000, offered = false),
        ;

        companion object {
            fun ofPortal(portal: Int): Location? = values().firstOrNull { it.portal == portal }
        }
    }

    /** Persisted house location, by [Location] ordinal; absent, unknown or not offered means Rimmington. */
    val LOCATION_ATTR = AttributeKey<Int>(persistenceKey = "poh_location")

    fun locationOf(player: Player): Location =
        Location.values().getOrNull(player.attr[LOCATION_ATTR] ?: -1)?.takeIf { it.offered } ?: Location.RIMMINGTON

    /** Building mode (House Options / the portal's "building mode" entry; client varbit 2176, CS2 2680). Persisted like OSRS. */
    val BUILD_MODE_ATTR = AttributeKey<Boolean>(persistenceKey = "poh_build_mode")
    const val BUILD_MODE_VARBIT = 2176

    fun isBuildMode(player: Player): Boolean = player.attr[BUILD_MODE_ATTR] == true

    /** Dining room table space (667 loc 15298, 4x2) and bell-pull space 15304 with the rope bell-pull 13307 ("Ring"). */
    const val TABLE_SPACE = 15298
    const val BELL_PULL_SPACE = 15304
    const val BELL_PULL = 13307

    /** The dining table built on the table space (its loc id, one of [PohBuild.DINING_TABLES]); absent = none built. */
    val DINING_TABLE_ATTR = AttributeKey<Int>(persistenceKey = "poh_dining_table")

    fun diningTableOf(player: Player): Int? = player.attr[DINING_TABLE_ATTR]?.takeIf { id -> PohBuild.DINING_TABLES.any { it.obj == id } }

    /**
     * The Superior Garden's theme space and the OSRS Otherworldly theme (OsrsLocImportTool batch "poh-theme",
     * tx-20261010-032639): the 2x2 theme space 29130 -> [THEME_SPACE] with Build, its seven ground-edging spaces 29123-29129 ->
     * 62932-62938, the five "Otherworldly garden" edgings 29248-29252 -> 62939-62943 and the "Otherworldly theme" 29253 ->
     * [OTHERWORLDLY_THEME] with Remove.
     */
    const val THEME_SPACE = 62931
    const val OTHERWORLDLY_THEME = 62944

    /**
     * Whether the Otherworldly theme is built (persisted). A23 (owner 2026-10-11: "iedereen krijgt het thema (bestaande + nieuwe
     * accounts, zoals vroeger)"): every house has it until its owner removes it in building mode (false); nothing is written to an
     * account that never touched it.
     */
    val GARDEN_THEME_ATTR = AttributeKey<Boolean>(persistenceKey = "poh_garden_theme")

    fun hasGardenTheme(player: Player): Boolean = player.attr[GARDEN_THEME_ATTR] != false

    /** One theme-edging space of the OSRS Superior Garden (OSRS 2499 region 7534 chunk 5,0, plane 0), in garden-local coordinates. */
    data class ThemeEdge(val space: Int, val x: Int, val z: Int, val rot: Int)

    /**
     * Every edging space as OSRS places it: 29123 the outer corners, 29124 the inner corners, 29125 the outer and inner rings,
     * 29126-29128 the inner ring's three pieces, 29129 the diagonals.
     */
    val THEME_EDGES: List<ThemeEdge> =
        """
        62932:0,0,2 0,7,3 7,0,1 7,7,0
        62933:2,2,0 2,5,1 5,2,3 5,5,2
        62934:0,1,3 0,2,3 0,3,3 0,4,3 0,5,3 0,6,3 1,0,2 1,7,0 2,0,2 2,3,1 2,4,1 2,7,0 3,0,2 3,2,0 3,5,2 3,7,0 4,0,2 4,2,0 4,5,2 4,7,0 5,0,2 5,3,3 5,4,3 5,7,0 6,0,2 6,7,0 7,1,1 7,2,1 7,3,1 7,4,1 7,5,1 7,6,1
        62935:1,2,3 1,5,3 2,6,0 4,1,2 5,6,0 6,3,1
        62936:1,3,3 3,1,2 3,6,0 6,2,1 6,5,1
        62937:1,4,3 2,1,2 4,6,0 5,1,2 6,4,1
        62938:1,1,0 1,6,1 6,1,3 6,6,2
        """.trimIndent().lines().flatMap { line ->
            val (space, tiles) = line.split(':')
            tiles.trim().split(' ').map { t ->
                val (x, z, r) = t.split(',').map(String::toInt)
                ThemeEdge(space.toInt(), x, z, r)
            }
        }

    /**
     * Edging space -> the Otherworldly garden piece built on it. A23 (owner "de eternal garden theme ziet er raar uit"): matched by
     * GEOMETRY, not id order - each OSRS space model and its piece have the same vertex / face count and footprint (OSRS cache
     * 2686, 10-23): outer corner 29123 (32130, 63v) = 29252 (32137), inner corner 29124 (32146, 150v) = 29251 (32141), grass
     * straight 29125 (32145, 72v) = 29250 (32144), the three path straights 29126-29128 (32133, 46v) = 29248 (32134) and the
     * path corner 29129 (32136, 56v) = 29249 (32128). The order-based guess put dirt squares on the inner grass corners and grass
     * on the path ring.
     */
    val OTHERWORLDLY_EDGES: Map<Int, Int> =
        mapOf(62932 to 62943, 62933 to 62942, 62934 to 62941, 62935 to 62939, 62936 to 62939, 62937 to 62939, 62938 to 62940)

    /** Where in the house the player logged out (x shl 8 or z from the house's corner): the next login rebuilds it there. */
    val LOGOUT_OFFSET_ATTR = AttributeKey<Int>(persistenceKey = "poh_logout_offset")

    /**
     * Into the player's house (tab, spell, portal, max/construction cape): a fresh house, the arrival, and OSRS Wiki "Vengeance":
     * "the effect will wear off upon logout or when teleporting into a player-owned house".
     */
    fun enter(player: Player): Boolean {
        val arrival = build(player)
        if (arrival == null) {
            player.message("Your house can't be reached right now. Try again in a moment.")
            return false
        }
        player.moveTo(arrival)
        gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance.clear(player)
        return true
    }

    /** The demon butler waits in the dining room once the owner is inside ([PohServant]). */
    val PENDING_SERVANT_ATTR = AttributeKey<Pair<InstancedMap, Tile>>()

    /** Owner 2026-09-21: "for now every player whitewashed". */
    val DEFAULT_STYLE = Style.WHITEWASHED_STONE

    /** Persisted style choice, by [Style] ordinal; absent or out of range means [DEFAULT_STYLE]. */
    val STYLE_ATTR = AttributeKey<Int>(persistenceKey = "poh_style")

    fun styleOf(player: Player): Style = Style.values().getOrNull(player.attr[STYLE_ATTR] ?: -1) ?: DEFAULT_STYLE

    fun setStyle(
        player: Player,
        style: Style,
    ) {
        player.attr[STYLE_ATTR] = style.ordinal
    }

    /**
     * The house plan, as instance chunks. The Superior Garden is the hub because it is the only POH room with a
     * doorway on all four walls, and every other room was chosen so that the door it already has lines up with the
     * garden's - no room is rotated except the portal chamber, which has a single south door and is turned 180
     * degrees to face north, exactly as it always was:
     *
     * ```
     *                 PvP/PvM room
     *     lawn          GARDEN     <->   chapel
     *                portal chamber
     * ```
     *
     * A door hotspot with no partner across the boundary becomes wall ([furnishRooms]), so the rooms' remaining
     * doorways close themselves off and the house is sealed without a single hard-coded wall tile.
     */
    private const val GARDEN_CHUNK_X = 1
    private const val GARDEN_CHUNK_Z = 1

    /**
     * OSRS loc 29241 "Ornate pool of Rejuvenation" with its own model and water animation (owner 2026-10-10: "de
     * rejuvenation pool model van osrs importeren"; OsrsLocImportTool / OsrsFxImportTool batch "poh-pool",
     * tx-20261010-014802 + tx-20261010-014959). It replaces the 667 ornamental fountain 13480 that stood in for it.
     * 2x2, in the Superior Garden's south-east corner - OSRS's pool space (6,0).
     */
    const val POOL = 62920
    const val PORTAL = 13405
    const val GLORY = Objs.AMULET_OF_GLORY_13523
    const val ALTAR = Objs.ALTAR_13199
    const val BURNER = Objs.INCENSE_BURNER_13213
    const val ARMOUR_STAND = Objs.ARMOUR_REPAIR_STAND

    /*
     * The OSRS pieces of owner 2026-10-10 (OsrsLocImportTool / OsrsFxImportTool batch "poh-house", tx-20261010-021402 +
     * tx-20261010-021327). Construction's Remove/Upgrade and the nexus's Configuration were left out of their menus.
     */

    /** OSRS 37520 "Ornate Jewellery Box" (its last-destination variants are not imported: the menu is "Teleport Menu"). */
    const val JEWELLERY_BOX = 62926

    /** OSRS 33410 crystalline "Portal Nexus", with its animation; Teleport and Teleport Menu open the nexus screen. */
    const val NEXUS = 62925

    /**
     * OSRS 29229 "Spiritual Fairy Tree": a spirit tree and a fairy ring in one (Tree, Ring-Zanaris, Ring-configure,
     * Ring-last-destination). Its 65 OSRS sub-menu entries have no rev-667 equivalent; Tree opens the spirit tree list.
     */
    const val SPIRITUAL_FAIRY_TREE = 62927

    /**
     * OSRS multiloc 29150 "Altar of the Occult": the client shows variant 62921 (standard book), 62922 (ancient) or 62923
     * (lunar) from the spellbook varbit 357, each offering Venerate and the two books the player is not on.
     */
    const val OCCULT_ALTAR = 62924
    val OCCULT_ALTAR_VARIANTS = intArrayOf(62921, 62922, 62923)

    /**
     * The garden's obelisk is a WILDERNESS obelisk (14826), not the Summoning "Small obelisk" (5787).
     *
     * Owner 2026-09-21: "we are missing a fairy ring in our poh a wilderness obelisk and a superior garden with the
     * rej pool". That is also what OSRS's Superior Garden obelisk is - a teleport to the Wilderness obelisk network -
     * while a Summoning obelisk only renews points. 5787 cannot be used here anyway: every "Renew-points" loc in the
     * cache is already bound by `skills/summoning/summoning_obelisks.plugin.kts`, and binding one twice stops the
     * boot. 14826 carries "Activate", which `areas/wilderness/wilderness_obelisk.plugin.kts` already owns; that file
     * now recognises a house obelisk and offers the six destinations instead of doing nothing.
     */
    const val OBELISK = Objs.OBELISK_14826

    /**
     * The OSRS ornate combat dummy (OsrsNpcImportTool batch "poh-dummy", tx-20261010-021425; Detach/Remove removed): OSRS Wiki
     * "Ornate undead combat dummy" 10507 and the variants its Swap changes to - a max house has every one unlocked. Each counts
     * as what OSRS says it counts as, as far as this server models it (poh_dummy.plugin.kts).
     */
    enum class Dummy(val npc: Int, val label: String) {
        UNDEAD(14664, "Undead"),
        STANDARD(14665, "Standard"),
        WILDERNESS(14666, "Wilderness"),
        KALPHITE(14667, "Kalphite"),
        KURASK(14668, "Kurask"),
        VAMPYRE(14669, "Vampyre"),
        DRAGON(14670, "Dragon"),
        ;

        companion object {
            fun of(npc: Int): Dummy? = values().firstOrNull { it.npc == npc }
        }
    }

    /** The variant the player last swapped to (OSRS keeps the built dummy's variant); absent = the undead dummy. */
    val DUMMY_VARIANT_ATTR = AttributeKey<Int>(persistenceKey = "poh_dummy_variant")

    fun dummyOf(player: Player): Dummy = Dummy.values().getOrNull(player.attr[DUMMY_VARIANT_ATTR] ?: -1) ?: Dummy.UNDEAD

    /** True for the house combat dummy (any variant). */
    fun isCombatDummy(target: gg.rsmod.game.model.entity.Pawn): Boolean = target is Npc && target.attr[COMBAT_DUMMY_ATTR] == true

    /** The ornate wilderness combat dummy counts as a Wilderness npc (OSRS Wiki: revenant weapons work on it). */
    fun isWildernessDummy(target: gg.rsmod.game.model.entity.Pawn): Boolean = isCombatDummy(target) && (target as Npc).id == Dummy.WILDERNESS.npc

    /**
     * Minimap icons (owner 2026-09-29: "er missen iconen op de minimap"). No house loc carries a mapelement in this
     * cache, and neither do the world's own spirit trees, fairy rings and altars: Jagex draws those icons with an
     * invisible type-22 marker loc placed beside the object, and the client collects every loc's mapelement from the
     * scene, spawned ones included. [TRANSPORT_MARKER] (loc 7389, mapelement 612, the orange transport arrow) is the
     * very marker Jagex placed at the Grand Exchange spirit tree; [ALTAR_MARKER] (loc 2752, mapelement 575) is the
     * altar icon. This revision has no separate spirit-tree or fairy-ring icon (every MapElementType was rendered and
     * checked), so the house's teleports all use the transport arrow, as the world's do. [PORTAL_MARKER] (loc 15531,
     * mapelement 621, the purple portal) marks the exit portal and the three marble portals (owner 2026-10-02).
     * 2026-10-02: they only showed once the client redrew its minimap after these spawns (client Static235.affectsMinimap,
     * ROOT_CAUSES #145) - before, the minimap was captured before the house's locs arrived.
     *
     * Owner 2026-10-10 ("controleer ook gelijk de minimap iconen"): Jagex's three elements carry `randomise`, so the client
     * walked every house icon up to 3 tiles away onto a free tile (Minimap.drawLevel) - beside the obelisk, into doorways,
     * into the wrong room. The house now places copies of the three markers whose elements do not wander (PohMinimapIconTool:
     * 2752 -> 62928, 7389 -> 62929, 15531 -> 62930); the rest of the world keeps Jagex's (owner: "Alleen POH vast").
     */
    const val TRANSPORT_MARKER = 62929
    const val ALTAR_MARKER = 62928
    const val PORTAL_MARKER = 62930

    /** Marble teleport portals (2009scape Decoration MARBLE_*_PORTAL). */
    const val VARROCK_PORTAL = 13629
    const val FALADOR_PORTAL = 13631
    const val CAMELOT_PORTAL = 13632

    /**
     * Build-mode spot -> what is built on it, for the spots whose id is the same in every style
     * (2009scape BuildHotspot -> Decoration). Window and door hotspots are style-specific and are handled by name in
     * [build] instead.
     *
     * Rug spots 15273/15274 -> opulent rug end/corner, statue spots 15275 -> large statue, icon spot 15269 -> icon of
     * Saradomin, altar spot 15270 -> gilded altar (13199 is the both-burners-lit variant, which carries "Pray" and no
     * Construction option), lamp spots 15271 -> lit marble incense burner, portal spots 15406/15407/15408 -> marble
     * Varrock/Falador/Camelot portals, centrepiece 15409 -> the Portal Nexus (OSRS: the nexus room's centre, 3,3).
     */
    private val SPOT_REPLACEMENTS =
        mapOf(
            15269 to 13175,
            15270 to ALTAR,
            15271 to BURNER,
            15273 to Objs.RUG_13595,
            15274 to Objs.RUG_13594,
            15275 to 13282,
            15406 to VARROCK_PORTAL,
            15407 to FALADOR_PORTAL,
            15408 to CAMELOT_PORTAL,
            15409 to NEXUS,
            // Superior Garden. A25 (owner 2026-10-11: "the poh exit portal needs to be in the middle of the poh now the spiritual
            // fairy tree is there"): the centrepiece spot (the room's centre, 3,3) holds the exit portal, as the 667 house's own
            // centrepiece hotspot (Darkan HouseBuilds.CENTREPIECE: EXIT_PORTAL first; the default house is a garden with it). The
            // spiritual fairy tree moves to the north-west corner ([furnishGarden]). The tree and plant spots stay empty (owner
            // 2026-09-29: "2 spirit trees", "layout klopt niet").
            15361 to PORTAL,
            // The costume room's storage spots (18810-18815) stay empty: owner 2026-10-10 removed the storage.
            48662 to 13282,
        )

    /** Every loc a house places (hotspot replacements and the hand-placed pieces) - the guard test checks each option has a route. */
    val PLACED_OBJECTS: Set<Int>
        get() = SPOT_REPLACEMENTS.values.toSet() + setOf(ARMOUR_STAND, JEWELLERY_BOX, GLORY, OBELISK, PORTAL, SPIRITUAL_FAIRY_TREE, POOL, OCCULT_ALTAR) +
            Style.values().flatMap { listOf(it.wall, it.stainedGlass) } +
            setOf(TABLE_SPACE, BELL_PULL, THEME_SPACE, OTHERWORLDLY_THEME) + PohBuild.DINING_TABLES.map { it.obj } +
            THEME_EDGES.map { it.space } + OTHERWORLDLY_EDGES.values

    val DUMMY_HEAL_TIMER = TimerKey()
    const val DUMMY_HEAL_TICKS = 5

    /**
     * The dummy is spawned a few ticks after the owner arrives (as the Fight Cave spawns its waves), so the client
     * has finished building the instance before the npc is added to its view.
     */
    val DUMMY_SPAWN_TIMER = TimerKey()
    const val DUMMY_SPAWN_DELAY = 3
    val PENDING_DUMMY_ATTR = AttributeKey<Pair<InstancedMap, Tile>>()

    /** The rooms of every house here: garden, PvP/PvM room, portal chamber, chapel and dining room (House Options "Number Of Rooms"). */
    val ROOM_COUNT = Room.values().size

    /** House Options teleport arrival (settings varbit 6450: 1 = at the portal, 0 = in the house; the client's own CS2 2685 values). */
    const val ARRIVAL_VARBIT = 6450

    /** Set once the player picked an arrival, so a fresh account (varbit 0) keeps the portal default. */
    val ARRIVAL_CHOSEN_ATTR = gg.rsmod.game.model.attr.AttributeKey<Boolean>(persistenceKey = "poh_arrival_chosen")

    fun arrivesInHouse(player: Player): Boolean = player.attr[ARRIVAL_CHOSEN_ATTR] == true && player.getVarbit(ARRIVAL_VARBIT) == 0

    /** True when [tile] is inside a live player-owned house. Folded into `GuardedZones.contains`. */
    fun isSafeTile(tile: Tile): Boolean = recordAt(tile) != null

    /**
     * Allocates a fresh house for [player] and returns the arrival tile, or null when no space is free.
     *
     * [keepOffset] (a rebuild: building mode toggled, a room rotated, a login inside the house) is the player's offset from the
     * old house's south-west corner; OSRS reloads the house around the player, so the arrival is that same spot when it is walkable.
     */
    fun build(
        player: Player,
        keepOffset: Pair<Int, Int>? = null,
    ): Tile? {
        val world = player.world
        val style = styleOf(player)
        val buildMode = isBuildMode(player)
        val chunks = InstancedChunkSet.Builder()
        for (x in 0 until 8) {
            for (z in 0 until 8) {
                val room = Room.values().firstOrNull { it.dx == x - GARDEN_CHUNK_X && it.dz == z - GARDEN_CHUNK_Z }
                if (room == null) {
                    chunks.set(x, z, 0, 0, style.lawn)
                } else {
                    chunks.set(x, z, 0, (room.baseRot + roomRotation(player, room)) and 3, room.template(style))
                }
            }
        }
        val config =
            InstancedMapConfiguration.Builder()
                .setOwner(player.uid)
                .setExitTile(locationOf(player).exit)
                .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_LOGOUT)
                .setBypassObjectChunkBounds(true)
                .build()
        val map = world.instanceAllocator.allocate(world, chunks.build(), config) ?: return null

        houses.removeIf { world.instanceAllocator.getMap(it.map.area.bottomLeft) !== it.map }

        // Each room's corner; local coordinates inside a room are measured from it as the plan lays the room out (the garden
        // x/z 0..7, the chapel east of it, the PvP/PvM room north, the portal chamber south, the dining room west).
        val baseX = map.area.bottomLeftX + GARDEN_CHUNK_X * 8
        val baseZ = map.area.bottomLeftZ + GARDEN_CHUNK_Z * 8

        fun corner(room: Room) = Tile(baseX + room.dx * 8, baseZ + room.dz * 8, 0)

        fun frame(room: Room) = RoomFrame(world, corner(room), roomRotation(player, room))

        houses.add(HouseRecord(world, map, player.uid, Room.values().associateWith { frame(it) }))

        furnishRooms(world, style, player, buildMode, Room.values().map { corner(it) }, garden = corner(Room.GARDEN))
        furnishChapel(frame(Room.CHAPEL))
        furnishGarden(player, buildMode, frame(Room.GARDEN))
        furnishPvmRoom(frame(Room.PVM_ROOM))
        furnishPortalChamber(frame(Room.PORTAL_CHAMBER))

        player.attr[PENDING_DUMMY_ATTR] = map to frame(Room.PVM_ROOM).tile(2, 3)
        player.attr[PENDING_SERVANT_ATTR] = map to frame(Room.DINING_ROOM).tile(1, 2)
        player.timers[DUMMY_SPAWN_TIMER] = DUMMY_SPAWN_DELAY
        keepOffset?.let { (dx, dz) ->
            val spot = Tile(map.area.bottomLeftX + dx, map.area.bottomLeftZ + dz, 0)
            gg.rsmod.plugins.content.mechanics.pvp.CityGuards.nearestWalkable(world, spot, radius = 3)?.let { return it }
        }
        // House Options "When teleporting, arrive at portal" (the default): the garden, in front of the pool and the exit portal,
        // with every room one doorway away; "arrive in house": inside the chapel.
        if (arrivesInHouse(player)) {
            val chapel = frame(Room.CHAPEL)
            return gg.rsmod.plugins.content.mechanics.pvp.CityGuards.nearestWalkable(world, chapel.tile(3, 3), radius = 3) ?: frame(Room.GARDEN).tile(2, 2)
        }
        return frame(Room.GARDEN).tile(2, 2)
    }

    /** A live house: its map, its owner and each room's placement frame. */
    private class HouseRecord(
        val world: World,
        val map: InstancedMap,
        val owner: Any?,
        val frames: Map<Room, RoomFrame>,
    )

    private val houses = java.util.concurrent.CopyOnWriteArrayList<HouseRecord>()

    /**
     * The live house [tile] is in. A released house's area is recycled by the allocator for any later instance (a Fight Cave, a
     * minigame), so a house only counts while its own map is still the one allocated there - before (night run 2026-10-10 review)
     * a released house kept its area "safe" until the next house was built, which would have made a later instance on the same
     * coordinates a safe zone with safe deaths.
     */
    private fun recordAt(tile: Tile): HouseRecord? = houses.firstOrNull { it.map.area.contains(tile) && it.world.instanceAllocator.getMap(tile) === it.map }

    /** The room [tile] is in, inside a live house. */
    fun roomAt(tile: Tile): Room? {
        val record = recordAt(tile) ?: return null
        return record.frames.entries.firstOrNull { it.value.corner.chunkCoords == tile.chunkCoords }?.key
    }

    /** The garden's placement frame of the house the player stands in. */
    fun gardenFrame(player: Player): RoomFrame? = recordAt(player.tile)?.frames?.get(Room.GARDEN)

    /** True when the player stands in their own house. */
    fun ownsHouseAt(player: Player): Boolean = recordAt(player.tile)?.owner == player.uid

    /** The player's offset from the south-west corner of the live house [tile] is in, or null outside every house. */
    fun offsetInHouse(tile: Tile): Pair<Int, Int>? {
        val area = recordAt(tile)?.map?.area ?: return null
        return (tile.x - area.bottomLeftX) to (tile.z - area.bottomLeftZ)
    }

    /**
     * Logout: the allocator has already released [map] when logout plugins run, so the house is found by that exact map object
     * (INSTANCE_LOGOUT_MAP_ATTR) - never by coordinates, which another instance may hold by now.
     */
    fun offsetInReleasedHouse(
        map: Any?,
        tile: Tile,
    ): Pair<Int, Int>? {
        val area = houses.firstOrNull { it.map === map }?.map?.area ?: return null
        return (tile.x - area.bottomLeftX) to (tile.z - area.bottomLeftZ)
    }

    /**
     * Rebuilds the house the player stands in from their current choices (OSRS: toggling building mode or rotating a room reloads
     * the house around the player) and moves them to the same spot of the new one; the old house is released at once.
     */
    fun rebuild(player: Player): Boolean {
        val world = player.world
        val old = world.instanceAllocator.getMap(player.tile)
        val offset = offsetInHouse(player.tile) ?: return false
        val arrival = build(player, keepOffset = offset) ?: return false
        player.moveTo(arrival)
        if (old != null) world.instanceAllocator.release(world, old)
        return true
    }

    /**
     * Replaces every build-mode spot in the room chunks, as Construction does: each spot is removed and what belongs there is
     * spawned with the spot's own type and rotation.
     *
     * A door hotspot facing another door hotspot across a chunk boundary stays an open doorway - which is what connects the
     * rooms - and every other one becomes this style's wall, sealing the house; in the garden, which has no walls, it simply
     * stays open. In building mode a connecting doorway keeps its hotspot (OSRS shows door hotspots in building mode; its
     * Build rotates the room behind it), the dining room's table space stays until a table is built, and the Superior Garden
     * shows its theme space ([furnishGarden]) - the only things a house here can build (owner 2026-10-10 night run).
     */
    private fun furnishRooms(
        world: World,
        style: Style,
        player: Player,
        buildMode: Boolean,
        roomCorners: List<Tile>,
        garden: Tile,
    ) {
        fun nameOf(id: Int) = world.definitions.get(ObjectDef::class.java, id).name.lowercase()

        val spots =
            roomCorners.flatMap { corner ->
                world.chunks
                    .get(corner.chunkCoords, createIfNeeded = true)!!
                    .getEntities<StaticObject>(EntityType.STATIC_OBJECT)
            }.filter { obj ->
                val name = nameOf(obj.id)
                name.endsWith(" space") || name == "door hotspot"
            }
        val doorTiles = spots.filter { nameOf(it.id) == "door hotspot" }.map { it.tile }.toSet()
        spots.forEach { obj ->
            val name = nameOf(obj.id)
            when {
                name == "door hotspot" -> {
                    // Wall-type rotation: 0 west, 1 north, 2 east, 3 south.
                    val across =
                        when (obj.rot) {
                            0 -> obj.tile.transform(-1, 0)
                            1 -> obj.tile.transform(0, 1)
                            2 -> obj.tile.transform(1, 0)
                            else -> obj.tile.transform(0, -1)
                        }
                    val connects = across in doorTiles
                    if (!(connects && buildMode)) {
                        world.remove(obj)
                    }
                    // A garden has no walls: a doorway of it with no room behind it stays open lawn instead of a lone wall stub.
                    if (!connects && obj.tile.chunkCoords != garden.chunkCoords) {
                        world.spawn(DynamicObject(style.wall, 0, obj.rot, obj.tile))
                    }
                }
                obj.id == TABLE_SPACE -> {
                    val table = diningTableOf(player)
                    when {
                        table != null -> world.spawn(DynamicObject(table, obj.type, obj.rot, obj.tile))
                        !buildMode -> world.remove(obj)
                    }
                }
                // OSRS calls the servant with the dining room's bell-pull; it is the one other dining room piece (owner: a bell-pull
                // when it needs no extra room), fixed like the rest of the house.
                obj.id == BELL_PULL_SPACE -> world.spawn(DynamicObject(BELL_PULL, obj.type, obj.rot, obj.tile))
                else -> {
                    world.remove(obj)
                    when {
                        name == "window space" -> world.spawn(DynamicObject(style.stainedGlass, obj.type, obj.rot, obj.tile))
                        else -> SPOT_REPLACEMENTS[obj.id]?.let { world.spawn(DynamicObject(it, obj.type, obj.rot, obj.tile)) }
                    }
                }
            }
            // The altar's and the nexus's own tiles hold no ground decoration (read from the room map data), so their
            // minimap marker takes that layer without covering a rug.
            when (obj.id) {
                15270 -> world.spawn(DynamicObject(ALTAR_MARKER, 22, 0, obj.tile))
                15409 -> world.spawn(DynamicObject(TRANSPORT_MARKER, 22, 0, obj.tile))
                15406, 15407, 15408 -> world.spawn(DynamicObject(PORTAL_MARKER, 22, 0, obj.tile))
            }
        }
    }

    /** Everything in the chapel that has no build hotspot of its own. */
    private fun furnishChapel(room: RoomFrame) {
        // The rejuvenation pool moved out to the Superior Garden on 2026-09-21, where OSRS keeps it; the chapel's
        // south-west corner it used to fill is now the armour repair stand's side of the room. The jewellery box, the
        // glory and the dummy moved out on 2026-10-10 (PvP/PvM room, portal chamber).
        room.spawn(ARMOUR_STAND, 10, 0, 5, 1)
    }

    /**
     * The PvP/PvM room (owner 2026-10-10), laid out as OSRS's Achievement Gallery: the Altar of the Occult on the east
     * wall at the gallery's altar space (6,3) and the jewellery box at its jewellery box space (6,1), both rotation 1 as
     * in the OSRS map data. The combat dummy stands free on the west side ([build], spawned by [spawnDummy]) so it can be
     * attacked from every side; the doorway (3-4, 0) stays clear.
     */
    private fun furnishPvmRoom(room: RoomFrame) {
        room.spawn(OCCULT_ALTAR, 10, 1, 6, 3)
        room.spawn(JEWELLERY_BOX, 10, 1, 6, 1)
        room.spawn(ALTAR_MARKER, 22, 0, 6, 3)
    }

    /**
     * The portal chamber: the nexus and the three marble portals come from its own spots ([SPOT_REPLACEMENTS]); the
     * mounted glory hangs where OSRS's Portal Nexus room has its amulet space, on the wall opposite the doorway (OSRS 1,0,
     * type-5 wall decoration facing into the room).
     */
    private fun furnishPortalChamber(room: RoomFrame) {
        room.spawn(GLORY, 5, 3, 1, 0)
    }

    /**
     * The Superior Garden (owner 2026-10-10, A25 2026-10-11): the exit portal on the centrepiece spot in the middle (3,3, from
     * [SPOT_REPLACEMENTS]), the spiritual fairy tree in the north-west corner (OSRS's topiary space, unused here), the pool in
     * the south-east corner - OSRS's pool space (6,0) - and OSRS's theme space in the north-east corner (6,6, [THEME_SPACE]):
     *
     * ```
     *   tree     .  N door  .  theme
     *   W door   .  portal  .  E door
     *   obelisk  .  S door  .  pool
     * ```
     *
     * The exit portal stands in the garden rather than in a room, so that leaving is one step from wherever the
     * player is: the garden is the only room every other room opens onto.
     */
    private fun furnishGarden(
        player: Player,
        buildMode: Boolean,
        room: RoomFrame,
    ) {
        room.spawn(POOL, 10, 0, 6, 0)
        room.spawn(OBELISK, 10, 0, 1, 1)
        room.spawn(SPIRITUAL_FAIRY_TREE, 10, 0, 0, 6)
        // Minimap icons: the transport arrow on the tree's and the obelisk's tile (the ground-decoration layer under a
        // scenery loc) and the purple portal on the exit portal's.
        room.spawn(TRANSPORT_MARKER, 22, 0, 0, 6)
        room.spawn(TRANSPORT_MARKER, 22, 0, 1, 1)
        room.spawn(PORTAL_MARKER, 22, 0, 3, 3)
        if (hasGardenTheme(player) || buildMode) spawnTheme(player, room)
    }

    /**
     * The theme space as the player's choices make it: the Otherworldly theme and its edging when built, otherwise (building
     * mode) the theme space and its edging spaces. Spawning over the same tiles replaces what stood there (one loc per layer).
     */
    fun spawnTheme(
        player: Player,
        room: RoomFrame,
    ) {
        val built = hasGardenTheme(player)
        room.spawn(if (built) OTHERWORLDLY_THEME else THEME_SPACE, 10, 0, 6, 6)
        THEME_EDGES.forEach { edge ->
            // A marker tile keeps its minimap icon (one ground decoration per tile); the edging under the obelisk and the
            // exit portal is hidden by them anyway.
            if (edge.x to edge.z in MARKER_TILES) return@forEach
            room.spawn(if (built) OTHERWORLDLY_EDGES.getValue(edge.space) else edge.space, 22, edge.rot, edge.x, edge.z)
        }
    }

    /** Garden tiles holding a minimap marker (exit portal 3,3, obelisk 1,1, tree 0,6). */
    private val MARKER_TILES = setOf(3 to 3, 1 to 1, 0 to 6)

    /** Spawns the house combat dummy once the owner is inside the house it was built for. */
    fun spawnDummy(player: Player) {
        val (map, tile) = player.attr[PENDING_DUMMY_ATTR] ?: return
        player.attr.remove(PENDING_DUMMY_ATTR)
        if (!map.area.contains(player.tile) || player.world.instanceAllocator.getMap(tile) !== map) {
            return
        }
        spawnDummy(player.world, dummyOf(player), tile)
    }

    fun spawnDummy(
        world: World,
        variant: Dummy,
        tile: Tile,
    ): Npc {
        val dummy = Npc(variant.npc, tile, world)
        dummy.walkRadius = 0
        world.spawn(dummy)
        // A combat dummy never fights back (the generic retaliation needs lock.canAttack()) and never dies.
        dummy.lock = LockState.FULL
        dummy.timers[DUMMY_HEAL_TIMER] = DUMMY_HEAL_TICKS
        dummy.attr[COMBAT_DUMMY_ATTR] = true
        return dummy
    }

    /**
     * Owner 2026-09-29: the house dummy is a combat dummy - every attack lands and hits the attacker's max hit with the gear
     * worn (read in `PawnExt.dealHit`). OSRS Wiki "Combat dummy": "no experience will be granted for hitting it" (the four
     * strategies' addCombatXp) and "Any ammunition fired at it will always be lost" (AvasDevices.outcome).
     */
    val COMBAT_DUMMY_ATTR = AttributeKey<Boolean>()
}
