package gg.rsmod.plugins.content.npcs.definitions.barrows

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.tools.importer.ObjectPlacementProbeTool
import gg.rsmod.plugins.api.ext.setVarbit
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-010 C1 (owner live: player gets stuck in the Barrows crypts/tunnels, sarcophagus does nothing).
 *
 * Reads the real 667 map placements of the Barrows region (tunnels plane 0, crypts plane 3) and every
 * object definition involved, and checks that each object id the Barrows plugin binds is actually
 * reachable: placed on the map, or a varbit/varp transform child of a placed object.
 */
class BarrowsMapBindingTests {
    private data class Placement(val id: Int, val name: String, val tile: String, val type: Int, val rot: Int)

    private fun placements(cache: String, xtea: String, plane: Int): List<Placement> {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer))
        try {
            ObjectPlacementProbeTool.main(arrayOf(cache, xtea, "tile", "3551", "9695", plane.toString(), "32"))
        } finally {
            System.setOut(original)
        }
        val line = Regex("""PLACEMENT id=(\d+) name='([^']*)' options=.* tile=([\d,]+) type=(\d+) orientation=(\d+)""")
        return buffer.toString().lines().mapNotNull { line.find(it) }.map {
            Placement(it.groupValues[1].toInt(), it.groupValues[2], it.groupValues[3], it.groupValues[4].toInt(), it.groupValues[5].toInt())
        }
    }

    @Test
    fun `every object the Barrows plugin binds is reachable on the 667 map`() {
        val cache = Paths.get("..", "..", "data", "cache").toFile().toString()
        val xtea = File("../../data").walkTopDown().maxDepth(3).firstOrNull { it.isFile && it.name.contains("xtea", ignoreCase = true) }
        assertTrue(xtea != null, "no xtea key file under ../../data")
        val defs = DefinitionSet().also { it.loadAll(CacheLibrary(cache)) }
        fun def(id: Int) = defs.getNullable(ObjectDef::class.java, id)

        val interesting = Regex("(?i)door|sarcoph|stair|chest|rope|tunnel")
        val report = StringBuilder()
        val reachable = mutableSetOf<Int>()
        listOf(0, 3).forEach { plane ->
            val found = placements(cache, xtea!!.path, plane)
            reachable += found.map { it.id }
            found.filter { interesting.containsMatchIn(it.name) || interesting.containsMatchIn(def(it.id)?.transforms?.mapNotNull { c -> if (c >= 0) def(c)?.name else null }?.joinToString() ?: "") }
                .groupBy { it.id }
                .forEach { (id, list) ->
                    val d = def(id)
                    val children = d?.transforms?.filter { it >= 0 }?.distinct()?.joinToString { c ->
                        val cd = def(c)
                        "$c'${cd?.name}' opts=${cd?.options?.filterNotNull()?.filter { o -> o.isNotBlank() }} solid=${cd?.solid}"
                    }
                    d?.transforms?.filter { it >= 0 }?.let { reachable += it }
                    report.append("plane $plane id=$id '${d?.name}' x${list.size} opts=${d?.options?.filterNotNull()?.filter { it.isNotBlank() }} " +
                        "varbit=${d?.varbit} varp=${d?.varp} solid=${d?.solid} type=${list.first().type} tiles=${list.take(3).map { it.tile }} children=[$children]\n")
                }
        }
        println("BarrowsMapBindingTests (xtea=${xtea?.path})\n$report")

        val bound = Barrows.Brother.values().flatMap { listOf(it.sarcophagus, it.stairs) } +
            Barrows.TUNNEL_DOORS.toList() + Barrows.PUZZLE_DOORS.toList() + listOf(6774, 6775, 6708)
        val unreachable = bound.distinct().filter { it !in reachable }.map { "$it '${def(it)?.name}'" }
        assertTrue(unreachable.isEmpty(), "Barrows plugin binds object ids that are not on the 667 map:\n" + unreachable.joinToString("\n"))

        // Every placed tunnel door multiloc: varbit in a..p, child 0 opens, child 1 has no option (locked).
        val doorParents = listOf(0).flatMap { placements(cache, xtea!!.path, it) }.map { it.id }.distinct()
            .mapNotNull { id -> def(id)?.takeIf { d -> d.varbit in 469..484 && d.transforms != null }?.let { id to it } }
        assertTrue(doorParents.size == 32, "expected 32 tunnel door multilocs, found ${doorParents.size}")
        val badDoors = doorParents.filter { (_, d) ->
            val open = d.transforms!![0]
            val locked = d.transforms!![1]
            def(open)?.options?.any { it.equals("Open", true) } != true || def(locked)?.options?.any { it.equals("Open", true) } == true
        }.map { it.first }
        assertTrue(badDoors.isEmpty(), "door multilocs whose varbit 0/1 children are not open/locked: $badDoors")
        val puzzleVarbits = Barrows.PUZZLE_DOORS.map { def(it)!!.varbit }.toSet()
        assertTrue(puzzleVarbits == Barrows.PUZZLE_LETTERS.map { Barrows.doorVarbit(it) }.toSet(), "puzzle door varbits $puzzleVarbits")
        Barrows.Corner.values().forEach { corner ->
            val rope = listOf(6709, 6710, 6711, 6712).first { def(it)!!.varbit == corner.ropeVarbit }
            assertTrue(def(rope)!!.transforms!!.contains(6708), "${corner.key} rope varbit ${corner.ropeVarbit}")
        }
        assertTrue(def(10284)!!.varbit == Barrows.CHEST_VARBIT && def(10284)!!.transforms!!.toList().containsAll(listOf(6774, 6775)))
    }

    @Test
    fun `every shuffle leaves exactly one open door per exit room and one open puzzle door`() {
        io.mockk.mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        try { repeat(400) {
            val attrs = gg.rsmod.game.model.attr.AttributeMap()
            val varbits = HashMap<Int, Int>()
            val player = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
            io.mockk.every { player.attr } returns attrs
            io.mockk.every { player.tile } returns gg.rsmod.game.model.Tile(3551, 9695, 0)
            // Extension mocks receive the receiver as the first argument: (player, id, value).
            io.mockk.every { player.setVarbit(any(), any()) } answers { varbits[secondArg<Int>()] = thirdArg<Int>(); Unit }
            val previousOpenPuzzle = if (it % 2 == 1) { Barrows.shufflePuzzle(player); Barrows.PUZZLE_LETTERS.first { l -> varbits[Barrows.doorVarbit(l)] == 0 } } else null
            Barrows.shufflePuzzle(player, incorrect = previousOpenPuzzle != null)
            val corner = attrs[Barrows.EXIT_CORNER]!!
            val open = Barrows.DOOR_LETTERS.filter { l -> varbits[Barrows.doorVarbit(l)] == 0 }
            assertTrue(Barrows.CORNER_DOORS.getValue(corner).count { l -> l in open } == 1, "corner $corner open=$open")
            assertTrue(Barrows.PUZZLE_LETTERS.count { l -> l in open } == 1, "puzzle open=$open")
            assertTrue(Barrows.DOOR_LETTERS.count { l -> l !in open } == 6, "locked count open=$open")
            if (previousOpenPuzzle != null) assertTrue(previousOpenPuzzle !in open, "wrong answer re-picked $previousOpenPuzzle")
            Barrows.Corner.values().forEach { c -> assertTrue(varbits[c.ropeVarbit] == (if (c.key == corner) 1 else 0), "rope ${c.key}") }
        } } finally {
            io.mockk.unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        }
    }

    @Test
    fun `puzzle is required only while entering the central room and door crossing stays bounded`() {
        val outside = gg.rsmod.game.model.Tile(3534, 9711, 0)
        val inside = gg.rsmod.game.model.Tile(3551, 9695, 0)
        assertTrue(Barrows.shouldSolvePuzzle(outside, Barrows.PUZZLE_DOORS.first()))
        assertTrue(!Barrows.shouldSolvePuzzle(inside, Barrows.PUZZLE_DOORS.first()))
        assertTrue(!Barrows.shouldSolvePuzzle(outside, Barrows.TUNNEL_DOORS.first()))

        val door = gg.rsmod.game.model.Tile(3541, 9695, 0)
        assertTrue(Barrows.isAtDoor(door.transform(0, 1), door))
        assertTrue(!Barrows.isAtDoor(door.transform(0, 3), door))
    }
}
