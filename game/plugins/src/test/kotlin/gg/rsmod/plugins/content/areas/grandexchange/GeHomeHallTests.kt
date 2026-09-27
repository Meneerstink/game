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
 * casino in the four upper corners, and the roof logic that hides the roof only for a player inside the walls.
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
        // The two owners of their own npc: Skully (with his chest) and the Quest Guide stand in the hall once.
        val skully = SkullyRoster.SITES.single { it.label == "Grand Exchange" }
        assertTrue(skully.exact && skully.wanted.x in floorX && skully.wanted.z in floorZ)
        assertTrue(posts.none { it.id == skully.npcId }, "Skully is posted twice")
        assertTrue(posts.none { it.id == NewPlayerConfig.DEFAULTS.choiceNpc }, "the Quest Guide is posted twice")
        assertEquals(NewPlayerConfig.DEFAULTS.choiceNpcTile, NewPlayerConfig.load(File("../../data/cfg/new_player.yml")).choiceNpcTile)
    }

    @Test
    fun `every npc stands on free floor of its own level, clear of objects, stairs and each other`() {
        val solid = solidTiles()
        val stairs = GeHomeHall.STAIRS.flatMap { s -> footprint(s.base, 2, 2) + footprint(Tile(s.base.x, s.base.z, 1), 2, 2) }.toSet()
        val landings = GeHomeHall.STAIRS.flatMap { listOf(it.floor, it.gallery) }.toSet()
        val skully = SkullyRoster.SITES.single { it.label == "Grand Exchange" }
        val all = hallPosts().filter { it.id in GeHomeHall.HALL_NPCS } +
            Post(skully.npcId, skully.wanted, 0) + Post(NewPlayerConfig.DEFAULTS.choiceNpc, NewPlayerConfig.DEFAULTS.choiceNpcTile, 0)
        val taken = HashMap<Tile, Int>()
        all.forEach { post ->
            val n = size(post.id)
            footprint(post.tile, n, n).forEach { t ->
                assertTrue(t.x in floorX && t.z in floorZ && t.height in 0..1, "${post.id} leaves the floor at $t")
                assertFalse(t in solid, "${post.id} stands in an object at $t")
                assertFalse(t in stairs || t in landings, "${post.id} blocks a staircase at $t")
                assertEquals(null, taken.put(t, post.id), "${post.id} shares $t")
            }
        }
        // The chest beside Skully is on free floor too.
        assertFalse(skully.chest!! in solid || skully.chest!! in taken)
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
        val npcs = hallPosts().filter { it.tile.height == 0 && (it.tile.x to it.tile.z) in arms }
        assertTrue(npcs.isEmpty(), "npcs on the carpet cross: $npcs")
    }

    @Test
    fun `the casino is one table with its dealer and named sign in each upper corner and nowhere else`() {
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
        assertTrue(tables.all { it.tile.height == 1 && it.tile.x in floorX && it.tile.z in floorZ })
        val corners = tables.map { (it.tile.x < GeHomeHall.X + GeHomeHall.WIDTH / 2) to (it.tile.z < GeHomeHall.Z + GeHomeHall.DEPTH / 2) }.toSet()
        assertEquals(4, corners.size, "one table per corner")
        val dealers = hallPosts().filter { it.id in GeHomeHall.CASINO_DEALERS }
        assertEquals(4, dealers.size)
        tables.forEach { table ->
            val cover = footprint(table.tile, 3, 2).map { it.x to it.z }
            fun near(t: Tile) = cover.any { (x, z) -> maxOf(kotlin.math.abs(x - t.x), kotlin.math.abs(z - t.z)) in 1..2 }
            assertEquals(1, dealers.count { it.tile.height == 1 && near(it.tile) }, "table ${table.tile} has no dealer of its own")
            assertEquals(1, placed.count { it.id in RoyalHallLocTool.CASINO_SIGNS && it.tile.height == 1 && near(it.tile) }, "table ${table.tile} has no sign")
        }
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
                val ring = (x == GeHomeHall.X - 1 || x == GeHomeHall.X + GeHomeHall.WIDTH) && z in GeHomeHall.Z - 1..GeHomeHall.Z + GeHomeHall.DEPTH ||
                    (z == GeHomeHall.Z - 1 || z == GeHomeHall.Z + GeHomeHall.DEPTH) && x in GeHomeHall.X - 1..GeHomeHall.X + GeHomeHall.WIDTH
                // The wall ring's upper walls and parapet stay drawn from inside (ZERO_LEVEL); the upper doorways hide with
                // their balcony (owner 2026-09-27: "elke balkon kan ik vanaf de begane grond doorheen kijken").
                val upperDoorway = ring && flags(1, x, z) == RoyalHallMapTool.BELOW_BUILDING
                if (ring) for (level in 1..3) if (!(level == 1 && upperDoorway)) assertTrue(flags(level, x, z) and 8 != 0, "ring $x,$z level $level")
                // Every walkable balcony tile and upper doorway is "part of the building below" (flag 0x20), nothing else.
                val walkableBalcony = RoyalHallMapTool.BALCONIES.any { x in it.xs && z in it.zs } && flags(1, x, z) and 1 == 0
                assertEquals(walkableBalcony, flags(1, x, z) == RoyalHallMapTool.BELOW_BUILDING, "balcony flag $x,$z")
                // No orange "gold trim" (overlay 157, texture 61) round the mansion any more.
                assertNotEquals(157, tiles.tiles[0][x - 49 * 64][z - 54 * 64].overlayId and 0xFF, "orange trim at $x,$z")
            }
        } finally {
            library.close()
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
        }
        assertEquals(RoyalHallMapTool.DEATHS_DOMAIN_ROT, gg.rsmod.game.tools.importer.AfkBasementMapTool.ENTRANCE_ROTATION)
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
