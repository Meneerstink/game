package gg.rsmod.plugins.content.areas.grandexchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.tools.importer.ModernRegionProbeTool
import gg.rsmod.game.tools.importer.Rev667LocCodec
import gg.rsmod.game.tools.importer.Rev667TileCodec
import gg.rsmod.game.tools.importer.RoyalHallLocTool
import gg.rsmod.game.tools.importer.RoyalHallMapTool
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.mechanics.pvp.SkullyRoster
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Royal Hall's layout (owner 2026-09-27): one source per npc, everything on free floor, the carpet cross free, the
 * casino in the middle room of the second upper floor, staircases through all three floors, and the roof logic that hides
 * the roof only for a player inside the walls.
 */
class GeHomeHallTests {
    companion object {
        private val CACHE_PATH = Paths.get("..", "..", "data", "cache").toFile().toString()
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(CACHE_PATH))
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }

    private val floorX = GeHomeHall.X until GeHomeHall.X + GeHomeHall.WIDTH
    private val floorZ = GeHomeHall.Z until GeHomeHall.Z + GeHomeHall.DEPTH

    /** The hall and its balconies and porticoes, where world_edits.json lines count as hall lines. */
    private fun nearHall(x: Int, z: Int) = x in 3150..3180 && z in 3478..3506

    private data class Post(val id: Int, val tile: Tile, val rot: Int)

    private fun worldEdits(): List<com.fasterxml.jackson.databind.JsonNode> =
        com.fasterxml.jackson.databind.ObjectMapper().readTree(File("../../data/world_edits.json"))["edits"].toList()

    /** The npcs world_edits.json leaves standing near the hall after replaying every npc_spawn / npc_remove. */
    private fun hallPosts(): List<Post> {
        val standing = ArrayList<Post>()
        worldEdits().forEach { e ->
            val tile = Tile(e["x"].asInt(), e["z"].asInt(), e["level"].asInt())
            if (!nearHall(tile.x, tile.z)) return@forEach
            when (e["op"].asText()) {
                "npc_spawn" -> standing += Post(e["id"].asInt(), tile, e["rot"].asInt())
                "npc_remove" -> standing.removeIf { it.id == e["id"].asInt() && it.tile == tile }
            }
        }
        return standing
    }

    private fun size(npc: Int) = GeHomeHall.HALL_NPCS[npc] ?: DEFINITIONS.get(NpcDef::class.java, npc).size

    private fun footprint(tile: Tile, w: Int, d: Int) = (0 until w).flatMap { dx -> (0 until d).map { dz -> Tile(tile.x + dx, tile.z + dz, tile.height) } }

    /** Every tile a solid loc covers near the hall: the map's own locs plus the decor file as the owner's edits leave it. */
    private fun solidTiles(): Set<Tile> {
        val tiles = HashSet<Tile>()
        fun add(id: Int, tile: Tile, type: Int, rot: Int) {
            if (type !in 9..11) return
            val def = DEFINITIONS.get(ObjectDef::class.java, id)
            if (def.solid == false) return
            val w = if (rot and 1 == 1) def.length else def.width
            val d = if (rot and 1 == 1) def.width else def.length
            tiles += footprint(tile, w, d)
        }
        val library = CacheLibrary(CACHE_PATH)
        try {
            val keys = ModernRegionProbeTool.loadKeys(File("../../data/xteas/xteas.json"))
            val archive = library.index(5).archive("l49_54", keys[(49 shl 8) or 54])!!
            Rev667LocCodec.decode(archive.file(0)!!.data!!).forEach { add(it.id, Tile(49 * 64 + it.localX, 54 * 64 + it.localZ, it.plane), it.type, it.rotation) }
        } finally {
            library.close()
        }
        val placements = HomeDecorFile.load(Paths.get("..", "..", "data", "cfg", "home_decor.txt")).placements.map { Triple(it.id, it.tile, it.type to it.rot) }.toMutableList()
        worldEdits().forEach { e ->
            val tile = Tile(e["x"].asInt(), e["z"].asInt(), e["level"].asInt())
            when (e["op"].asText()) {
                "remove" -> placements.removeIf { it.first == e["id"].asInt() && it.second == tile }
                "spawn" -> placements += Triple(e["id"].asInt(), tile, e["type"].asInt() to e["rot"].asInt())
            }
        }
        placements.filter { nearHall(it.second.x, it.second.z) }.forEach { (id, tile, tr) -> add(id, tile, tr.first, tr.second) }
        return tiles
    }

    @Test
    fun `every hall npc is posted exactly once, by world_edits json and nothing else`() {
        val posts = hallPosts()
        GeHomeHall.HALL_NPCS.keys.forEach { id -> assertEquals(1, posts.count { it.id == id }, "npc $id posts: ${posts.filter { it.id == id }}") }
        val removed = posts.filter { it.id in GeHomeHall.REMOVED }
        assertTrue(removed.isEmpty(), "removed npcs back in the hall: $removed")
        // No code spawns a hall npc any more.
        val root = File("src/main/kotlin/gg/rsmod/plugins/content")
        assertFalse("spawn_npc(" in File(root, "areas/grandexchange/ge_home_hall.plugin.kts").readText())
        assertFalse("spawn_npc(npc = BREACH_TRADER" in File(root, "mechanics/pvp/breach/breach_shop.plugin.kts").readText())
        assertFalse("spawnAll" in File(root, "mechanics/store/StoreNpcs.kt").readText())
        val casino = File(root, "activity/casino/casino_grandexchange.plugin.kts").readText()
        assertFalse("spawn_npc(" in casino || "spawn_obj(" in casino, "the old Grand Exchange casino pit is still spawned")
        // The two owners of their own npc: Skully (with his chest, SkullyRoster) and the Quest Guide stand once. Owner 2026-09-30
        // (world edit group 580): Skully stands outside the hall, south of it at 3160,3481, facing south - posted by the roster
        // only, never by a world_edits.json line as well.
        val skully = SkullyRoster.SITES.single { it.label == "Grand Exchange" }
        assertEquals(Tile(3160, 3481, 0), skully.wanted)
        assertTrue(skully.exact && skully.face == gg.rsmod.game.model.Direction.SOUTH)
        assertTrue(posts.none { it.id == skully.npcId }, "Skully is posted twice")
        assertTrue(posts.none { it.id == NewPlayerConfig.DEFAULTS.choiceNpc }, "the Quest Guide is posted twice")
        assertEquals(NewPlayerConfig.DEFAULTS.choiceNpcTile, NewPlayerConfig.load(File("../../data/cfg/new_player.yml")).choiceNpcTile)
    }

    @Test
    fun `every npc stands on free floor of its own level, clear of objects, stairs and each other`() {
        val solid = solidTiles()
        val stairs = GeHomeHall.STAIRS.flatMap { s -> (0..2).flatMap { level -> footprint(Tile(s.base.x, s.base.z, level), 2, 2) } }.toSet()
        val landings = GeHomeHall.STAIRS.flatMap { listOf(it.floor, it.gallery, it.landing) }.toSet()
        // Skully stands outside the hall since 2026-09-30 (see above), so only the Quest Guide joins the hall posts.
        val all = hallPosts().filter { it.id in GeHomeHall.HALL_NPCS } + Post(NewPlayerConfig.DEFAULTS.choiceNpc, NewPlayerConfig.DEFAULTS.choiceNpcTile, 0)
        val taken = HashMap<Tile, Int>()
        all.forEach { post ->
            val n = size(post.id)
            footprint(post.tile, n, n).forEach { t ->
                // Owner 2026-09-29: the balconies are floor too (he posted the Archaeologist on the west balcony), but not
                // their wall-ring row, which is the doorway.
                val ring = t.x == floorX.first - 1 || t.x == floorX.last + 1 || t.z == floorZ.first - 1 || t.z == floorZ.last + 1
                val balcony = !ring && RoyalHallMapTool.BALCONIES.any { b -> t.height == b.level && t.x in b.xs && t.z in b.zs }
                assertTrue((t.x in floorX && t.z in floorZ && t.height in 0..2) || balcony, "${post.id} leaves the floor at $t")
                assertFalse(t in solid, "${post.id} stands in an object at $t")
                assertFalse(t in stairs || t in landings, "${post.id} blocks a staircase at $t")
                val other = taken.put(t, post.id)
                // Owner 2026-09-30 in developer mode (groups 552, 570): Azzanadra (2 x 2) at 3162,3490 on level 1 takes in the
                // Oneiromancer's tile 3163,3491 - his own placement, reported to him on 2026-10-01; no other npc may share a tile.
                val ownerOverlap = setOf(post.id, other) == setOf(Npcs.AZZANADRA, Npcs.ONEIROMANCER) && t == Tile(3163, 3491, 1)
                if (!ownerOverlap) assertEquals(null, other, "${post.id} shares $t")
            }
        }
    }

    @Test
    fun `the carpet cross on the ground floor stays a free walkway`() {
        val solid = solidTiles()
        val arms = (GeHomeHall.X + GeHomeHall.SOUTH_DOOR.first..GeHomeHall.X + GeHomeHall.SOUTH_DOOR.last).flatMap { x -> floorZ.map { z -> x to z } } +
            floorX.flatMap { x -> (GeHomeHall.Z + GeHomeHall.SIDE_DOOR.first..GeHomeHall.Z + GeHomeHall.SIDE_DOOR.last).map { z -> x to z } }
        // The fountain in the middle, and the summoning obelisk the owner himself put beside the north door (world_edits.json).
        val fountain = (3163..3166).flatMap { x -> (3490..3493).map { z -> x to z } }.toSet() + (3163 to 3497)
        val blocked = arms.filter { it !in fountain && Tile(it.first, it.second, 0) in solid }
        assertTrue(blocked.isEmpty(), "objects on the carpet cross: $blocked")
        // Owner 2026-09-30 in developer mode put these on the carpet himself (world edit groups 575, 576) - no one else may.
        val ownerPlaced = setOf(Post(GeHomeHall.DONATOR_STORE, Tile(3166, 3489, 0), 6), Post(GeHomeHall.DEADMAN_STORE, Tile(3159, 3492, 0), 6))
        val npcs = hallPosts().filter { it.tile.height == 0 && (it.tile.x to it.tile.z) in arms && it !in ownerPlaced }
        assertTrue(npcs.isEmpty(), "npcs on the carpet cross: $npcs")
    }

    @Test
    fun `the casino is one table with its dealer and named sign in each quarter of the top floor's middle room`() {
        // The decor file as the owner's own world edits leave it (he moves tables and signs himself in developer mode).
        val placed = HomeDecorFile.load(Paths.get("..", "..", "data", "cfg", "home_decor.txt")).placements.toMutableList()
        worldEdits().forEach { e ->
            val tile = Tile(e["x"].asInt(), e["z"].asInt(), e["level"].asInt())
            when (e["op"].asText()) {
                "remove" -> placed.removeIf { it.id == e["id"].asInt() && it.tile == tile }
                "spawn" -> placed += HomeDecorFile.Placement(e["id"].asInt(), tile, e["type"].asInt(), e["rot"].asInt())
            }
        }
        val tables = placed.filter { it.id == 12975 }
        assertEquals(4, tables.size)
        // Owner 2026-09-27: the casino moved up to the big middle room of the second upper floor (x 3162-3167), the card
        // tables (3 x 2) turned lengthwise so the aisle from the north to the south doorway (x 3164-3165) stays free.
        val room = 3162..3167
        val aisle = 3164..3165
        fun cover(p: HomeDecorFile.Placement) = if (p.rot and 1 == 1) footprint(p.tile, 2, 3) else footprint(p.tile, 3, 2)
        assertTrue(tables.all { t -> t.tile.height == 2 && cover(t).all { it.x in room && it.x !in aisle && it.z in floorZ } }, "tables $tables")
        val quarters = tables.map { (it.tile.x < aisle.first) to (it.tile.z < GeHomeHall.Z + GeHomeHall.DEPTH / 2) }.toSet()
        assertEquals(4, quarters.size, "one table per quarter")
        val dealers = hallPosts().filter { it.id in GeHomeHall.CASINO_DEALERS }
        assertEquals(4, dealers.size)
        assertTrue(dealers.all { it.tile.height == 2 && it.tile.x in room && it.tile.x !in aisle }, "dealers $dealers")
        tables.forEach { table ->
            val covered = cover(table).map { it.x to it.z }
            fun near(t: Tile) = covered.any { (x, z) -> maxOf(kotlin.math.abs(x - t.x), kotlin.math.abs(z - t.z)) in 1..2 }
            assertEquals(1, dealers.count { near(it.tile) }, "table ${table.tile} has no dealer of its own")
            assertEquals(1, placed.count { it.id in RoyalHallLocTool.CASINO_SIGNS && it.tile.height == 2 && near(it.tile) }, "table ${table.tile} has no sign")
        }
        assertTrue(placed.none { it.id in RoyalHallLocTool.CASINO_SIGNS && it.tile.height != 2 }, "a casino sign left behind")
        assertEquals(RoyalHallLocTool.CASINO_SIGNS.toSet(), placed.filter { it.id in RoyalHallLocTool.CASINO_SIGNS }.map { it.id }.toSet())
        // Each dealer carries the owner's name for his game.
        val names = worldEdits().filter { it["op"].asText() == "npc_name" && it["id"].asInt() in GeHomeHall.CASINO_DEALERS }.map { it["note"].asText() }.toSet()
        assertEquals(setOf("Blackjack Gambler", "Dice Gambler", "Flower Poker Gambler", "Mines Gambler"), names)
        // The one minimap star is the map marker, not a dealer.
        GeHomeHall.CASINO_DEALERS.forEach { assertEquals(-1, DEFINITIONS.get(NpcDef::class.java, it).mapElement, "dealer $it still carries a map icon") }
    }

    @Test
    fun `the roof hides only for a player inside the walls`() {
        val library = CacheLibrary(CACHE_PATH)
        try {
            val tiles = Rev667TileCodec.decode(library.data(5, library.index(5).archive("m49_54")!!.id, 0)!!)
            fun flags(level: Int, x: Int, z: Int) = tiles.tiles[level][x - 49 * 64][z - 54 * 64].flags
            for (x in 3148..3182) for (z in 3476..3508) {
                val inside = x in floorX && z in floorZ
                // Flag 4 ("remove roof"): exactly the floor inside the walls, on both storeys - never the wall ring, a
                // threshold, a portico or a balcony (owner 2026-09-27, fotos mansion_bug1/3/6/7).
                assertEquals(inside, flags(0, x, z) and 4 != 0, "ground $x,$z")
                assertEquals(inside, flags(1, x, z) and 4 != 0, "upper $x,$z")
                assertEquals(inside, flags(2, x, z) and 4 != 0, "top $x,$z")
                val ring = (x == GeHomeHall.X - 1 || x == GeHomeHall.X + GeHomeHall.WIDTH) && z in GeHomeHall.Z - 1..GeHomeHall.Z + GeHomeHall.DEPTH ||
                    (z == GeHomeHall.Z - 1 || z == GeHomeHall.Z + GeHomeHall.DEPTH) && x in GeHomeHall.X - 1..GeHomeHall.X + GeHomeHall.WIDTH
                // Every balcony tile - its wall-ring row included - is "part of the building below" (0x20): from inside a
                // lower storey it hides with the roof (owner 2026-09-29: "op de begane grond zie je alle balkons dit hoort
                // niet"); nothing else carries 0x20. The rest of the wall ring stays drawn from inside (ZERO_LEVEL).
                for (level in 1..2) {
                    val balconies = if (level == 1) RoyalHallMapTool.BALCONIES else RoyalHallMapTool.BALCONIES_2
                    val balcony = balconies.any { x in it.xs && z in it.zs }
                    assertEquals(balcony, flags(level, x, z) == RoyalHallMapTool.BELOW_BUILDING, "balcony flag $x,$z level $level")
                }
                for (level in listOf(0, 3)) assertEquals(0, flags(level, x, z) and 0x20, "0x20 at $x,$z level $level")
                if (ring) for (level in 1..3) if (flags(level, x, z) != RoyalHallMapTool.BELOW_BUILDING) assertTrue(flags(level, x, z) and 8 != 0, "ring $x,$z level $level")
                // The client reads 0x20 on the level above the player's, so from inside the ground floor a level-2 balcony
                // hides only with the level-1 balcony under it: every one of its tiles must stand over one.
                if (RoyalHallMapTool.BALCONIES_2.any { x in it.xs && z in it.zs }) assertEquals(RoyalHallMapTool.BELOW_BUILDING, flags(1, x, z), "no balcony under $x,$z")
                // No hole instead: every hidden wall-ring tile gets its facade per player (GeHomeHallCurtains), per storey.
                if (ring) for (level in 1..2) {
                    val pieces = GeHomeHallCurtains.PIECES.count { it.tile.x == x && it.tile.z == z && it.storey == level }
                    assertEquals(if (flags(level, x, z) == RoyalHallMapTool.BELOW_BUILDING) 1 else 0, pieces, "facade piece $x,$z storey $level")
                }
                // No orange "gold trim" (overlay 157, texture 61) round the mansion any more.
                assertNotEquals(157, tiles.tiles[0][x - 49 * 64][z - 54 * 64].overlayId and 0xFF, "orange trim at $x,$z")
            }
            // Every storey is 360 high (owner 2026-09-29 for the top storey, 2026-10-02 for all: "het plafond van de hele
            // mansion is veel te laag"): levels 1, 2 and 3 (roof, parapet) each stand 45 x 8 above the one below over the whole
            // hall, walls and balconies included.
            assertEquals(listOf(45, 45, 45), RoyalHallMapTool.STOREY_STEPS.toList())
            for (x in RoyalHallMapTool.FLOOR_MIN_X - 1..RoyalHallMapTool.FLOOR_MAX_X + 1) for (z in RoyalHallMapTool.FLOOR_MIN_Z - 1..RoyalHallMapTool.FLOOR_MAX_Z + 1) {
                for (level in 1..3) assertEquals(45, tiles.tiles[level][x - 49 * 64][z - 54 * 64].height, "level $level height at $x,$z")
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `the facade over the balconies closes only for a player inside below it`() {
        assertEquals(40, GeHomeHallCurtains.PIECES.size)
        assertTrue(GeHomeHallCurtains.PIECES.all { it.tile.height == 0 })
        // The piece faces like the storey wall of its side (home_decor.txt: the level-1 wall on the same tile).
        val decor = HomeDecorFile.load(Paths.get("..", "..", "data", "cfg", "home_decor.txt")).placements
        var matched = 0
        GeHomeHallCurtains.PIECES.forEach { p ->
            val wall = decor.firstOrNull { it.tile == Tile(p.tile.x, p.tile.z, p.storey) && it.type == 0 } ?: return@forEach
            assertEquals(wall.rot, p.rot, "rotation of ${p.tile} storey ${p.storey}")
            matched++
        }
        assertEquals(16, matched, "the walled ring tiles at both ends of every balcony row")
        val mid = Tile(3164, 3491, 0)
        assertEquals(0b110, GeHomeHallCurtains.wanted(mid))
        assertEquals(0b100, GeHomeHallCurtains.wanted(mid.transform(1)))
        assertEquals(0, GeHomeHallCurtains.wanted(mid.transform(2)))
        // Outside - on a threshold, under or on a balcony - the doorways stay open.
        assertEquals(0, GeHomeHallCurtains.wanted(Tile(3164, 3485, 0)))
        assertEquals(0, GeHomeHallCurtains.wanted(Tile(3164, 3483, 1)))
        // The two facade locs: lifted to their storey, not solid, the plain storey wall's name.
        listOf(RoyalHallLocTool.CURTAIN_1, RoyalHallLocTool.CURTAIN_2).forEach { id ->
            val def = DEFINITIONS.get(ObjectDef::class.java, id)
            assertEquals(DEFINITIONS.get(ObjectDef::class.java, 33868).name, def.name)
            assertFalse(def.solid, "loc $id is solid")
        }
    }

    @Test
    fun `both marble staircases are the bottom piece without the flight down`() {
        val bottom = DEFINITIONS.get(ObjectDef::class.java, GeHomeHall.STAIRS_BOTTOM)
        assertEquals(listOf("Climb-up"), bottom.options.filterNotNull().filter { it.isNotBlank() })
        // 62779 shows the set's bottom model (34871), not the middle one (34872) whose steps went through the floor.
        assertEquals(DEFINITIONS.get(ObjectDef::class.java, 34871).name, bottom.name)
        val source = File("../src/main/kotlin/gg/rsmod/game/tools/importer/RoyalHallLocTool.kt").readText()
        assertTrue("Variant(62779, 34871," in source)
        GeHomeHall.STAIRS.forEach { s ->
            assertEquals(1, s.gallery.height)
            assertEquals(0, s.floor.height)
            assertEquals(2, s.landing.height)
        }
        // Owner 2026-09-27: both staircases continue to the second upper floor - the middle piece on level 1 leads both ways.
        val middle = DEFINITIONS.get(ObjectDef::class.java, GeHomeHall.STAIRS_MIDDLE)
        assertEquals(listOf("Climb-up", "Climb-down"), middle.options.filterNotNull().filter { it.isNotBlank() })
        val decor = HomeDecorFile.load(Paths.get("..", "..", "data", "cfg", "home_decor.txt")).placements
        GeHomeHall.STAIRS.forEach { s ->
            listOf(GeHomeHall.STAIRS_BOTTOM, GeHomeHall.STAIRS_MIDDLE, GeHomeHall.STAIRS_TOP).forEachIndexed { level, id ->
                assertEquals(listOf(id), decor.filter { it.tile == Tile(s.base.x, s.base.z, level) }.map { it.id }, "staircase at ${s.base} level $level")
            }
        }
        assertEquals(RoyalHallMapTool.DEATHS_DOMAIN_ROT, gg.rsmod.game.tools.importer.AfkBasementMapTool.ENTRANCE_ROTATION)
    }

    @Test
    fun `the roof and parapet stand on level 3 above the second upper storey`() {
        val decor = HomeDecorFile.load(Paths.get("..", "..", "data", "cfg", "home_decor.txt")).placements.filter { nearHall(it.tile.x, it.tile.z) }
        val roof = setOf(11831, 62773, RoyalHallLocTool.ROOF_RING2, 62754, 62755, 62756, 62757)
        assertTrue(decor.filter { it.id in roof }.all { it.tile.height == 3 }, "roof or parapet below level 3")
        assertTrue(decor.any { it.id == RoyalHallLocTool.ROOF_RING2 } && decor.any { it.id == 62773 })
        // The second upper storey repeats the first one's outer walls, plus the wall beside each of its narrower doorways -
        // all as the 1.5x high copies (360), like every other storey since 2026-10-02 - none of the 240 originals anywhere.
        val walls = setOf(33868, 33877)
        val tall = setOf(RoyalHallLocTool.TALL_WALL, RoyalHallLocTool.TALL_WINDOW_WALL)
        fun ring(level: Int) = decor.filter { (it.id in walls || it.id in tall) && it.type == 0 && it.tile.height == level && (it.tile.x !in floorX || it.tile.z !in floorZ) }
        assertEquals(ring(1).size + 8, ring(2).size)
        assertTrue(ring(1).all { w -> ring(2).any { it.tile.x == w.tile.x && it.tile.z == w.tile.z && it.rot == w.rot } })
        assertTrue(decor.none { it.id in walls }, "a 240-high wall left in the hall")
        (0..2).forEach { level -> assertTrue(ring(level).isNotEmpty() && ring(level).all { it.id in tall }, "storey $level wall ring") }
        // Three rooms: an inner wall on x 3162 and x 3167 with a two-tile opening (z 3491-3492) in each.
        val inner = decor.filter { it.id in tall && it.tile.height == 2 && it.tile.x in floorX && it.tile.z in floorZ }
        listOf(3162 to 0, 3167 to 2).forEach { (x, rot) ->
            assertEquals(floorZ.filter { it !in 3491..3492 }.toSet(), inner.filter { it.tile.x == x && it.rot == rot }.map { it.tile.z }.toSet())
        }
        // Every level-2 balcony's parapet runs on to the building: a wall-decoration piece on the open edge of each wall-ring
        // tile beside its doorway (owner 2026-09-29: "de balkon sluit niet goed aan").
        val sides = decor.filter { it.id == RoyalHallLocTool.BALCONY_PARAPET_SIDE }
        assertTrue(sides.all { it.tile.height == 2 && it.type == 4 })
        RoyalHallMapTool.BALCONIES_2.forEach { b ->
            val ringTiles = b.xs.flatMap { x -> b.zs.map { z -> x to z } }.filter { (x, z) ->
                (x == GeHomeHall.X - 1 || x == GeHomeHall.X + GeHomeHall.WIDTH || z == GeHomeHall.Z - 1 || z == GeHomeHall.Z + GeHomeHall.DEPTH) &&
                    x in GeHomeHall.X - 1..GeHomeHall.X + GeHomeHall.WIDTH && z in GeHomeHall.Z - 1..GeHomeHall.Z + GeHomeHall.DEPTH
            }
            // The two ring tiles at the balcony's ends (not the doorway itself) each carry one side piece.
            val ends = ringTiles.filter { (x, z) -> ringTiles.count { (ox, oz) -> kotlin.math.abs(ox - x) + kotlin.math.abs(oz - z) == 1 } == 1 }
            assertEquals(2, ends.size, "balcony ends $ringTiles")
            ends.forEach { (x, z) -> assertEquals(1, sides.count { it.tile.x == x && it.tile.z == z }, "no parapet side at $x,$z") }
        }
        assertEquals(8, sides.size)
        // The three copies exist in the cache (RoyalHallLocTool, both caches) under their Royal Hall names.
        listOf(RoyalHallLocTool.TALL_WALL to 33868, RoyalHallLocTool.TALL_WINDOW_WALL to 33877, RoyalHallLocTool.BALCONY_PARAPET_SIDE to 11831).forEach { (id, source) ->
            assertEquals(DEFINITIONS.get(ObjectDef::class.java, source).name, DEFINITIONS.get(ObjectDef::class.java, id).name, "loc $id")
        }
    }

    @Test
    fun `no npc talk completes a quest or hands out the summoning start any more`() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }
        val offenders =
            sources.filter { f ->
                val text = f.readText()
                Regex("UnlockNpcRewards\\.complete\\w+\\(").containsMatchIn(text) ||
                    (f.name != "quest_foundation.plugin.kts" && f.name != "SummoningKit.kt" && "SummoningKit.claim(" in text)
            }.map { it.name }.toList()
        assertTrue(offenders.isEmpty(), "claim routes left in $offenders")
    }
}
