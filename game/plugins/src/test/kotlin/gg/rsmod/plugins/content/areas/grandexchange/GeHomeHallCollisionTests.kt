package gg.rsmod.plugins.content.areas.grandexchange

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.decor.HomeDecorBake
import gg.rsmod.game.model.decor.HomeDecorFile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.tools.importer.Rev667TileCodec
import gg.rsmod.game.tools.importer.Rev667TileMap
import gg.rsmod.game.tools.importer.RoyalHallLocTool
import gg.rsmod.game.tools.importer.RoyalHallMapTool
import gg.rsmod.util.ServerProperties
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Royal Hall as one system (owner B5 / C5 / E15 / E20 / F11, 09-26 .. 10-03: holes, walls you can see through, noclip):
 * the real map of the game cache with the hall dressed exactly as the server dresses it - home_decor.txt (removals, then
 * placements) and then the owner's own developer-mode edits in world_edits.json on top (WorldEdits.underEdits) - and the
 * server's own collision ([World.collision]). Every storey is walked from its way in (the south entrance, the staircases)
 * and every step taken is checked against what the client draws on that edge.
 *
 * A separate [World] (not RealMapWorld's shared one): the dressing must not leak into other map tests.
 */
class GeHomeHallCollisionTests {
    companion object {
        private val CACHE = Paths.get("..", "..", "data", "cache").toFile().toString()
        private val BOX_X = 3148..3182
        private val BOX_Z = 3474..3510

        /** Where the walk of each storey may go: the hall, its porticoes and balconies, a ring of ground round it. */
        private val WALK_X = 3150..3180
        private val WALK_Z = 3476..3508

        lateinit var world: World
        lateinit var map: Rev667TileMap
        lateinit var edits: List<JsonNode>
        lateinit var design: HomeDecorFile.Design

        fun inBox(t: Tile) = t.x in BOX_X && t.z in BOX_Z

        fun objectsAt(t: Tile): List<GameObject> =
            world.chunks.getOrCreate(t).getEntities<GameObject>(t, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).toList()

        @BeforeClass
        @JvmStatic
        fun dress() {
            val w = World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true))
            w.filestore = CacheLibrary(CACHE)
            w.definitions.loadAll(w.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), w, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(w) as MutableList<Service>
            services += xteas
            world = w
            // Region 12598 (terrain, locs and their collision) loads with its first chunk, before any dressing.
            w.chunks.getOrCreate(Tile(3164, 3491, 0))
            map = Rev667TileCodec.decode(w.filestore.data(5, w.filestore.index(5).archive("m49_54")!!.id, 0)!!)

            // home_decor_live.plugin.kts applyDesign: the del lines, then the placements beyond the bake (the baked ones are
            // in the map already; World.spawn replaces the loc of the same client layer on the tile) and the baked locs the
            // file dropped taken out.
            design = HomeDecorFile.load(Paths.get("..", "..", "data", "cfg", "home_decor.txt"))
            design.removals.filter { inBox(it.tile) }.forEach { r ->
                objectsAt(r.tile).filter { r.type == null || it.type == r.type }.forEach { w.remove(it) }
            }
            val bake = HomeDecorBake.diff(design, HomeDecorBake.loadManifest(Paths.get("..", "..", "data", "cfg", "home_decor.baked.txt")))
            bake.missing.filter { inBox(it.tile) }.forEach { p ->
                objectsAt(p.tile).filter { it.id == p.id && it.type == p.type && it.rot == p.rot }.forEach { w.remove(it) }
            }
            bake.extra.filter { inBox(it.tile) }.forEach { w.spawn(DynamicObject(it.id, it.type, it.rot, it.tile)) }

            // WorldEdits.reload on top (ROOT_CAUSES #144: the owner's edits win over the dressing).
            edits = ObjectMapper().readTree(File("../../data/world_edits.json"))["edits"].toList()
            edits.forEach { e ->
                val tile = Tile(e["x"].asInt(), e["z"].asInt(), e["level"].asInt())
                if (!inBox(tile)) return@forEach
                when (e["op"].asText()) {
                    "remove" -> objectsAt(tile).filter { it.id == e["id"].asInt() }.forEach { w.remove(it) }
                    "spawn" -> w.spawn(DynamicObject(e["id"].asInt(), e["type"]?.asInt() ?: 10, (e["rot"]?.asInt() ?: 0) and 3, tile))
                }
            }
        }
    }

    private val floorX = RoyalHallMapTool.FLOOR_MIN_X..RoyalHallMapTool.FLOOR_MAX_X
    private val floorZ = RoyalHallMapTool.FLOOR_MIN_Z..RoyalHallMapTool.FLOOR_MAX_Z

    /** The ways into each storey: the south entrance's path, the landings of both staircases. */
    private fun entrances(level: Int): List<Tile> =
        when (level) {
            0 -> listOf(Tile(3164, 3480, 0))
            1 -> GeHomeHall.STAIRS.map { it.gallery }
            else -> GeHomeHall.STAIRS.map { it.landing }
        }

    private data class Step(val from: Tile, val dir: Direction)

    /** Every tile a player reaches on [level] from its [entrances] with the server's own step rule, and every step taken. */
    private fun walk(level: Int): Pair<Set<Tile>, List<Step>> {
        val seen = HashSet<Tile>()
        val steps = ArrayList<Step>()
        val queue = ArrayDeque<Tile>()
        entrances(level).forEach { if (seen.add(it)) queue += it }
        while (queue.isNotEmpty()) {
            val t = queue.removeFirst()
            for (dir in Direction.RS_ORDER) {
                val next = t.step(dir)
                if (next.x !in WALK_X || next.z !in WALK_Z) continue
                if (!world.collision.canStep(t, dir)) continue
                steps += Step(t, dir)
                if (seen.add(next)) queue += next
            }
        }
        return seen to steps
    }

    /** Locs the client draws on the [dir] edge of [tile]: walls (0), L corners (2), wall decorations (4, 5 - railings, parapets). */
    private fun drawnOnEdge(
        tile: Tile,
        dir: Direction,
    ): List<GameObject> {
        fun on(
            t: Tile,
            d: Direction,
        ) = objectsAt(t).filter { o ->
            when (o.type) {
                0, 4, 5 -> Direction.WNES[o.rot] == d
                2 -> d in Direction.WNES_DIAGONAL[o.rot].getDiagonalComponents()
                else -> false
            }
        }
        return on(tile, dir) + on(tile.step(dir), dir.getOpposite())
    }

    /** The edges a step passes: its own for a straight step, the four round the corner it cuts for a diagonal one. */
    private fun edges(step: Step): List<Pair<Tile, Direction>> {
        if (!step.dir.isDiagonal()) return listOf(step.from to step.dir)
        val (a, b) = step.dir.getDiagonalComponents()
        return listOf(step.from to a, step.from to b, step.from.step(a) to b, step.from.step(b) to a)
    }

    private fun name(o: GameObject) = "${o.id} '${world.definitions.get(ObjectDef::class.java, o.id).name}' type ${o.type} rot ${o.rot} at ${o.tile.x},${o.tile.z},${o.tile.height}"

    @Test
    fun `no walkable step anywhere in the hall passes through a wall, railing or parapet, on any storey`() {
        assertTrue(edits.none { it["op"].asText() == "passable" && inBox(Tile(it["x"].asInt(), it["z"].asInt(), it["level"].asInt())) }, "a hall loc made passable")
        val problems = ArrayList<String>()
        for (level in 0..2) {
            val (_, steps) = walk(level)
            steps.forEach { step ->
                val drawn = edges(step).flatMap { (t, d) -> drawnOnEdge(t, d) }.distinct()
                if (drawn.isNotEmpty()) problems += "level $level ${step.from.x},${step.from.z} ${step.dir}: walks through ${drawn.joinToString { name(it) }}"
            }
        }
        assertTrue(problems.isEmpty(), "noclip through drawn walls:\n" + problems.joinToString("\n"))
    }

    @Test
    fun `every storey is reachable from its way in - rooms, both staircases and every balcony`() {
        val wanted =
            mapOf(
                0 to listOf(Tile(3164, 3488, 0), Tile(3160, 3495, 0), Tile(3169, 3495, 0)) + GeHomeHall.STAIRS.map { it.floor },
                1 to listOf(Tile(3164, 3488, 1), Tile(3164, 3483, 1), Tile(3164, 3500, 1), Tile(3155, 3491, 1), Tile(3174, 3491, 1)),
                2 to listOf(Tile(3164, 3488, 2), Tile(3159, 3490, 2), Tile(3170, 3490, 2), Tile(3164, 3484, 2), Tile(3164, 3499, 2), Tile(3156, 3491, 2), Tile(3173, 3491, 2)),
            )
        wanted.forEach { (level, tiles) ->
            val (reached, _) = walk(level)
            tiles.forEach { assertTrue(it in reached, "storey $level: $it cannot be reached from ${entrances(level)}") }
        }
    }

    @Test
    fun `every tile a player reaches upstairs has a floor under him and belongs to the hall or a balcony`() {
        val problems = ArrayList<String>()
        for (level in 1..2) {
            val (reached, _) = walk(level)
            val balconies = if (level == 1) RoyalHallMapTool.BALCONIES else RoyalHallMapTool.BALCONIES_2
            reached.forEach { t ->
                val ground = map.tiles[level][t.x - 49 * 64][t.z - 54 * 64]
                if (ground.overlayId == 0) problems += "level $level ${t.x},${t.z}: walkable air (no floor)"
                val hall = t.x in floorX && t.z in floorZ
                val balcony = balconies.any { t.x in it.xs && t.z in it.zs }
                // The upper doorways' wall-ring tiles lead onto the balconies (and are part of them).
                if (!hall && !balcony) problems += "level $level ${t.x},${t.z}: walkable outside the hall and its balconies"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `no furniture of the hall stands inside a corner turret`() {
        val turrets = RoyalHallMapTool.TURRETS.flatMap { (x, z) -> listOf(Tile(x, z), Tile(x + 1, z), Tile(x, z + 1), Tile(x + 1, z + 1)) }.map { it.x to it.z }.toSet()
        val problems = ArrayList<String>()
        for (level in 0..3) for (x in BOX_X) for (z in BOX_Z) {
            objectsAt(Tile(x, z, level)).filter { it.type in 10..11 && it.id !in RoyalHallLocTool.TURRET_STOREY_LOCS }.forEach { o ->
                val def = world.definitions.get(ObjectDef::class.java, o.id)
                if (!def.solid) return@forEach
                val w = if (o.rot and 1 == 1) def.length else def.width
                val d = if (o.rot and 1 == 1) def.width else def.length
                val inside = (0 until w).any { dx -> (0 until d).any { dz -> (x + dx to z + dz) in turrets } }
                if (inside) problems += name(o)
            }
        }
        assertTrue(problems.isEmpty(), "hidden inside a turret: $problems")
    }

    /**
     * Owner 2026-10-06 (OWNER_QUESTIONS Q12, A): the summoning obelisk he moved to 3156,3482,0 stood on a tile whose level-1
     * record carries the bridge flag (the client draws and walks that column one level down, DefinitionSet), so it looked
     * sunk and the client walked through it - move it one tile. Guard: no ground loc the owner's world edits place in the
     * hall area stands on a bridge tile; the message lists the plain neighbours to move it to.
     */
    @Test
    fun `no ground loc placed by the world edits stands on a bridge tile`() {
        fun bridged(x: Int, z: Int) = map.tiles[1][x - 3136][z - 3456].flags and 0x2 != 0
        // Every tile the loc covers (its cache size, turned by its rotation) must be plain.
        fun covers(id: Int, rot: Int, x: Int, z: Int): List<Pair<Int, Int>> {
            val def = world.definitions.get(ObjectDef::class.java, id)
            val w = if (rot and 1 == 1) def.length else def.width
            val d = if (rot and 1 == 1) def.width else def.length
            return (0 until w).flatMap { dx -> (0 until d).map { dz -> x + dx to z + dz } }
        }
        // Only locs still standing once the file is replayed (WorldEdits.reload): a later "remove" of the same loc takes it out.
        fun removedLater(i: Int) =
            edits.drop(i + 1).any { r ->
                r["op"].asText() == "remove" && r["id"].asInt() == edits[i]["id"].asInt() && r["x"].asInt() == edits[i]["x"].asInt() &&
                    r["z"].asInt() == edits[i]["z"].asInt() && r["level"].asInt() == edits[i]["level"].asInt()
            }
        val problems =
            edits.filterIndexed { i, it -> it["op"].asText() == "spawn" && it["level"].asInt() == 0 && (it["type"]?.asInt() ?: 10) in 10..11 && !removedLater(i) }
                .filter { inBox(Tile(it["x"].asInt(), it["z"].asInt())) }
                .filter { e -> covers(e["id"].asInt(), e["rot"]?.asInt() ?: 0, e["x"].asInt(), e["z"].asInt()).any { (a, b) -> bridged(a, b) } }
                .map { e ->
                    val x = e["x"].asInt()
                    val z = e["z"].asInt()
                    val free = (-2..2).flatMap { dx -> (-2..2).map { dz -> x + dx to z + dz } }
                        .filter { (a, b) -> covers(e["id"].asInt(), e["rot"]?.asInt() ?: 0, a, b).none { (c, d) -> bridged(c, d) } }
                        .sortedBy { (a, b) -> Math.abs(a - x) + Math.abs(b - z) }
                    "loc ${e["id"].asInt()} at $x,$z size ${covers(e["id"].asInt(), e["rot"]?.asInt() ?: 0, x, z).size} (group ${e["group"]?.asInt()}): plain spots nearest first $free"
                }
        assertTrue(problems.isEmpty(), "on a bridge tile:\n" + problems.joinToString("\n"))
    }

    /**
     * Owner 2026-10-09 20:09 ("je kunt tussen de muren kijken op elke verdieping"): the storey wall 33868 has no end faces, so a
     * wall that simply stopped at a doorway showed its open inside. Jagex closes such an end with 33873 / 33874 (Lumbridge castle's
     * courtyard entrance); every end of a hall wall, on every storey, meets another wall or is that end piece's closed end.
     */
    @Test
    fun `every hall wall ends against another wall or in a Jagex end piece, on every storey`() {
        val hallWalls = setOf(RoyalHallLocTool.TALL_WALL, RoyalHallLocTool.TALL_WINDOW_WALL, RoyalHallLocTool.TALL_WALL_END_Z0, RoyalHallLocTool.TALL_WALL_END_Z1)
        val connectors = hallWalls + RoyalHallLocTool.BALCONY_PARAPET

        // The two corner points of a straight wall: model z0 end first (rot 0 south, 1 west, 2 north, 3 east).
        fun ends(o: GameObject): List<Pair<Int, Int>> {
            val x = o.tile.x
            val z = o.tile.z
            return when (o.rot) {
                0 -> listOf(x to z, x to z + 1)
                1 -> listOf(x to z + 1, x + 1 to z + 1)
                2 -> listOf(x + 1 to z + 1, x + 1 to z)
                else -> listOf(x + 1 to z, x to z)
            }
        }

        fun points(o: GameObject): List<Pair<Int, Int>> =
            when (o.type) {
                0 -> ends(o)
                2 -> {
                    val x = o.tile.x
                    val z = o.tile.z
                    // L piece: the two edges of [Direction.WNES_DIAGONAL] (rot 0 west + north, 1 north + east, 2 east + south, 3 south + west)
                    val edges = listOf(listOf(x to z, x to z + 1, x + 1 to z + 1), listOf(x to z + 1, x + 1 to z + 1, x + 1 to z), listOf(x + 1 to z + 1, x + 1 to z, x to z), listOf(x + 1 to z, x to z, x to z + 1))
                    edges[o.rot]
                }
                else -> emptyList()
            }
        val problems = ArrayList<String>()
        for (level in 0..3) {
            val pieces = ArrayList<GameObject>()
            for (x in BOX_X) for (z in BOX_Z) pieces += objectsAt(Tile(x, z, level)).filter { it.id in connectors && it.type in setOf(0, 2) }
            val degree = HashMap<Pair<Int, Int>, Int>()
            pieces.forEach { o ->
                val pts = points(o)
                // an L piece's middle point is its own corner; only its two outer points meet other walls
                val outer = if (o.type == 2) listOf(pts.first(), pts.last()) else pts
                outer.forEach { degree.merge(it, 1, Int::plus) }
            }
            pieces.filter { it.type == 0 && it.id in hallWalls }.forEach { o ->
                ends(o).forEachIndexed { i, p ->
                    val capped = (i == 0 && o.id == RoyalHallLocTool.TALL_WALL_END_Z0) || (i == 1 && o.id == RoyalHallLocTool.TALL_WALL_END_Z1)
                    if ((degree[p] ?: 0) < 2 && !capped) problems += "level $level ${name(o)}: open end at ${p.first},${p.second}"
                }
            }
        }
        assertTrue(problems.isEmpty(), "walls you can look into: " + problems.joinToString("; "))
    }

    /**
     * Owner 2026-10-06 (OWNER_QUESTIONS Q10 = A): the hall's sconces, paintings, shields and lanterns hang on the inside face of the
     * outer walls (x 3157 / 3172, z 3485 / 3498), never outside and never on a corner-turret tile.
     */
    @Test
    fun `the hall's wall decorations hang inside, not on the outer face or in a turret`() {
        val turrets = RoyalHallMapTool.TURRETS.flatMap { (x, z) -> listOf(x to z, x + 1 to z, x to z + 1, x + 1 to z + 1) }.toSet()
        val problems =
            design.placements.filter { it.type == 4 && it.id in setOf(15545, 15560, 24200, 9145) && inBox(it.tile) }.filter {
                val t = it.tile
                t.x !in 3158..3171 || t.z !in 3486..3497 || (t.x to t.z) in turrets
            }.map { "${it.id} at ${it.tile.x},${it.tile.z},${it.tile.height} rot ${it.rot}" }
        assertTrue(problems.isEmpty(), "outside or in a turret: $problems")
    }
}
