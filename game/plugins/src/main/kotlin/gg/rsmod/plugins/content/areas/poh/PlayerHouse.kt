package gg.rsmod.plugins.content.areas.poh

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
import gg.rsmod.plugins.api.cfg.Npcs
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
 * SAFE ZONE. [isSafeTile] reports every tile of every live house, and `GuardedZones.contains` folds it into the one
 * predicate the PvP gate, the guards and the HUD already share, so a house counts as a guarded city everywhere at
 * once. The PK skull already stops counting down inside an instance
 * ([gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.tickPauseTracking] pauses on `tile.x >= 6400`), which is exactly
 * the OSRS Deadman behaviour the owner asked for, so no separate skull rule is needed here.
 */
object PlayerHouse {
    /** Rimmington house portal exit (2009scape `HouseLocation.RIMMINGTON`: portal loc 15478, exit 2953,3224). */
    val EXIT_TILE = Tile(2953, 3224, 0)
    const val RIMMINGTON_PORTAL = Objs.PORTAL_15478

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

        /** The costume room (chunk 6,1): cape rack, armour case, magic wardrobe, toy box, dress box, chest. */
        val costumeRoom: Tile get() = Tile(squareX + 6 * 8, squareZ + 1 * 8, plane)

        /** The study (chunk 4,5): lectern, globe, crystal ball, wall chart, telescope, bookcase. */
        val study: Tile get() = Tile(squareX + 4 * 8, squareZ + 5 * 8, plane)
    }

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
     *                 costume room
     *   study     <->   GARDEN     <->   chapel
     *                portal chamber
     * ```
     *
     * A door hotspot with no partner across the boundary becomes wall ([furnishRooms]), so the rooms' remaining
     * doorways close themselves off and the house is sealed without a single hard-coded wall tile.
     */
    private const val GARDEN_CHUNK_X = 1
    private const val GARDEN_CHUNK_Z = 1

    const val POOL = Objs.ORNAMENTAL_FOUNTAIN
    const val PORTAL = 13405
    const val GLORY = Objs.AMULET_OF_GLORY_13523
    const val ALTAR = Objs.ALTAR_13199
    const val BURNER = Objs.INCENSE_BURNER_13213
    const val ARMOUR_STAND = Objs.ARMOUR_REPAIR_STAND
    const val JEWELLERY_BOX = Objs.JEWELLERY_BOX
    const val NEXUS = Objs.SCRYING_POOL_13639
    const val SPIRIT_TREE = Objs.SPIRIT_TREE_8355
    const val FAIRY_RING = Objs.FAIRY_RING

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
    const val DUMMY = Npcs.MELEE_DUMMY

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
     * Varrock/Falador/Camelot portals, centrepiece 15409 -> the scrying pool that serves as the teleport nexus.
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
            // Superior Garden. The centrepiece spot is where Construction puts a garden's centrepiece, and it is
            // where the rejuvenation pool belongs (owner 2026-09-21: "a superior garden with the rej pool"). The
            // big-plant spot at the garden's south-east corner is deliberately absent from this map: the spirit
            // tree stands there instead, and it is placed by hand because it is 3x3.
            15361 to POOL,
            15362 to Objs.MAGIC_TREE_13417,
            15363 to Objs.MAGIC_TREE_13424,
            15365 to Objs.TALL_PLANT_13427,
            15366 to Objs.FERN_13433,
            15367 to Objs.FERN_13433,
            // Costume room, top tier of every piece - the containers [PohStorage] fills.
            18810 to Objs.MAGIC_CAPE_RACK,
            18811 to Objs.MAGIC_WARDROBE_18796,
            18812 to Objs.TOY_BOX_18802,
            18813 to Objs.TREASURE_CHEST_18808,
            18814 to Objs.FANCY_DRESS_BOX_18776,
            18815 to Objs.ARMOUR_CASE_18782,
            // Study, top tier of every piece.
            15420 to Objs.LECTERN_13648,
            15421 to Objs.CELESTIAL_GLOBE_13652,
            15422 to Objs.CRYSTAL_OF_POWER_13661,
            15423 to Objs.INFERNAL_CHART_13664,
            15424 to Objs.TELESCOPE_13658,
            15425 to Objs.BOOKCASE_13599,
            48662 to 13282,
        )

    /** Every loc a house places (hotspot replacements and the hand-placed pieces) - the guard test checks each option has a route. */
    val PLACED_OBJECTS: Set<Int>
        get() = SPOT_REPLACEMENTS.values.toSet() + setOf(ARMOUR_STAND, JEWELLERY_BOX, GLORY, SPIRIT_TREE, FAIRY_RING, OBELISK, PORTAL) +
            Style.values().flatMap { listOf(it.wall, it.stainedGlass) }

    val DUMMY_HEAL_TIMER = TimerKey()
    const val DUMMY_HEAL_TICKS = 5

    /**
     * The dummy is spawned a few ticks after the owner arrives (as the Fight Cave spawns its waves), so the client
     * has finished building the instance before the npc is added to its view.
     */
    val DUMMY_SPAWN_TIMER = TimerKey()
    const val DUMMY_SPAWN_DELAY = 3
    val PENDING_DUMMY_ATTR = AttributeKey<Pair<InstancedMap, Tile>>()

    /**
     * Every live house's area, for [isSafeTile]. Instance areas are recycled by the allocator, so a stale entry would
     * make some later, unrelated instance safe; [build] therefore drops every area that no longer belongs to a live
     * map before adding its own.
     */
    private val houseAreas = java.util.concurrent.CopyOnWriteArrayList<Area>()

    /** True when [tile] is inside a live player-owned house. Folded into `GuardedZones.contains`. */
    fun isSafeTile(tile: Tile): Boolean = houseAreas.any { it.contains(tile) }

    /** Allocates a fresh house for [player] and returns the arrival tile, or null when no space is free. */
    fun build(player: Player): Tile? {
        val world = player.world
        val style = styleOf(player)
        val chunks = InstancedChunkSet.Builder()
        for (x in 0 until 8) {
            for (z in 0 until 8) {
                val dx = x - GARDEN_CHUNK_X
                val dz = z - GARDEN_CHUNK_Z
                when {
                    dx == 0 && dz == 0 -> chunks.set(x, z, 0, 0, style.garden)
                    dx == 0 && dz == 1 -> chunks.set(x, z, 0, 0, style.costumeRoom)
                    dx == 0 && dz == -1 -> chunks.set(x, z, 0, 2, style.portalChamber)
                    dx == 1 && dz == 0 -> chunks.set(x, z, 0, 0, style.chapel)
                    dx == -1 && dz == 0 -> chunks.set(x, z, 0, 0, style.study)
                    else -> chunks.set(x, z, 0, 0, style.lawn)
                }
            }
        }
        val config =
            InstancedMapConfiguration.Builder()
                .setOwner(player.uid)
                .setExitTile(EXIT_TILE)
                .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_LOGOUT)
                .setBypassObjectChunkBounds(true)
                .build()
        val map = world.instanceAllocator.allocate(world, chunks.build(), config) ?: return null

        houseAreas.removeIf { world.instanceAllocator.getMap(it.bottomLeft) == null }
        houseAreas.add(map.area)

        // Local coordinates are measured from the GARDEN chunk's south-west corner, so the garden is x/z 0..7, the
        // chapel x 8..15, the study x -8..-1, the costume room z 8..15 and the portal chamber z -8..-1.
        val baseX = map.area.bottomLeftX + GARDEN_CHUNK_X * 8
        val baseZ = map.area.bottomLeftZ + GARDEN_CHUNK_Z * 8
        fun local(
            x: Int,
            z: Int,
        ) = Tile(baseX + x, baseZ + z, 0)

        val roomCorners = listOf(local(0, 0), local(0, 8), local(0, -8), local(8, 0), local(-8, 0))
        furnishRooms(world, style, roomCorners)
        furnishChapel(world) { x, z -> local(8 + x, z) }
        furnishGarden(world) { x, z -> local(x, z) }

        player.attr[PENDING_DUMMY_ATTR] = map to local(8 + 5, 3)
        player.timers[DUMMY_SPAWN_TIMER] = DUMMY_SPAWN_DELAY
        // The player arrives in the garden, in front of the pool, with every room one doorway away.
        return local(2, 2)
    }

    /**
     * Replaces every build-mode spot in the two room chunks, as Construction does: each spot is removed and what
     * belongs there is spawned with the spot's own type and rotation.
     *
     * A door hotspot facing another door hotspot across a chunk boundary stays an open doorway - which is what
     * connects the five rooms - and every other one becomes this style's wall, sealing the house.
     */
    private fun furnishRooms(
        world: World,
        style: Style,
        roomCorners: List<Tile>,
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
            world.remove(obj)
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
                    if (across !in doorTiles) {
                        world.spawn(DynamicObject(style.wall, 0, obj.rot, obj.tile))
                    }
                }
                name == "window space" -> world.spawn(DynamicObject(style.stainedGlass, obj.type, obj.rot, obj.tile))
                else -> SPOT_REPLACEMENTS[obj.id]?.let { world.spawn(DynamicObject(it, obj.type, obj.rot, obj.tile)) }
            }
        }
    }

    /** Everything in the chapel that has no build hotspot of its own. */
    private fun furnishChapel(
        world: World,
        local: (Int, Int) -> Tile,
    ) {
        // The rejuvenation pool moved out to the Superior Garden on 2026-09-21, where OSRS keeps it; the chapel's
        // south-west corner it used to fill is now the armour repair stand's side of the room.
        world.spawn(DynamicObject(ARMOUR_STAND, 10, 0, local(5, 1)))
        world.spawn(DynamicObject(JEWELLERY_BOX, 10, 0, local(6, 3)))
        // Mounted glory on the EAST wall (type 5 is the wall-decoration slot). It used to hang on the west wall,
        // which is now the doorway through to the garden.
        world.spawn(DynamicObject(GLORY, 5, 2, local(7, 4)))
    }

    /**
     * The three things in the Superior Garden that have no build hotspot that fits them.
     *
     * Everything else in the room is built on the garden's own spots ([SPOT_REPLACEMENTS]): the rejuvenation pool on
     * the centrepiece, magic trees on the two tree spots and ferns on the small-plant spots.
     *
     * The spirit tree is placed by hand because of its size. Revision 667's walk-in spirit trees (1293/1294/1295/
     * 1317) are 4x4, and a 4x4 object cannot stand anywhere in an 8x8 POH room without covering one of the four
     * mid-edge doorways - so this uses 8355, the 3x3 spirit tree, in the south-east corner where it blocks nothing.
     * `mechanics/travel/spirit_tree.plugin.kts` binds its Teleport option along with the others.
     *
     * The exit portal stands in the garden rather than in a room, so that leaving is one step from wherever the
     * player is: the garden is the only room every other room opens onto.
     */
    private fun furnishGarden(
        world: World,
        local: (Int, Int) -> Tile,
    ) {
        world.spawn(DynamicObject(SPIRIT_TREE, 10, 0, local(5, 0)))
        world.spawn(DynamicObject(FAIRY_RING, 10, 0, local(0, 6)))
        world.spawn(DynamicObject(OBELISK, 10, 0, local(1, 3)))
        world.spawn(DynamicObject(PORTAL, 10, 0, local(2, 6)))
    }

    /** Spawns the house combat dummy once the owner is inside the house it was built for. */
    fun spawnDummy(player: Player) {
        val (map, tile) = player.attr[PENDING_DUMMY_ATTR] ?: return
        player.attr.remove(PENDING_DUMMY_ATTR)
        if (!map.area.contains(player.tile) || player.world.instanceAllocator.getMap(tile) !== map) {
            return
        }
        val world = player.world
        val dummy = Npc(DUMMY, tile, world)
        dummy.walkRadius = 0
        world.spawn(dummy)
        // A combat dummy never fights back (the generic retaliation needs lock.canAttack()) and never dies.
        dummy.lock = LockState.FULL
        dummy.timers[DUMMY_HEAL_TIMER] = DUMMY_HEAL_TICKS
    }
}
