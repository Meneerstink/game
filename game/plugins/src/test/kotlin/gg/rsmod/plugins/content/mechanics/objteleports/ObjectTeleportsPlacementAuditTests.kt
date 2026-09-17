package gg.rsmod.plugins.content.mechanics.objteleports

import gg.rsmod.game.tools.importer.ObjectPlacementProbeTool
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-012 B10 (owner live: "some Wilderness dungeon rift portals do not work"). `ObjectTeleports.find` fires only when
 * the clicked object stands on the exact tile recorded in the table, and the table was converted from Void's data for an
 * older map. This audit compares every table entry with the real revision-667 placements (same landscape decode the
 * server uses), and verifies that stale Void records are rejected before they can execute.
 * Read-only, production cache.
 */
class ObjectTeleportsPlacementAuditTests {
    private data class Placement(val id: Int, val x: Int, val z: Int, val height: Int, val options: String)

    @Test
    fun `stale object teleport entries are rejected against the real 667 placements`() {
        val entries = loadEntries()
        val placements = placementsOf(entries.map { it.id }.distinct())
        val byId = placements.groupBy { it.id }

        val unplaced = mutableListOf<String>()
        var exact = 0
        entries.forEach { e ->
            val t = e.tile!!
            val same = byId[e.id].orEmpty()
            if (same.any { it.x == t.x && it.z == t.z && it.height == t.height }) {
                exact++
                return@forEach
            }
            val nearest = same.filter { it.height == t.height }.minByOrNull { maxOf(abs(it.x - t.x), abs(it.z - t.z)) }
            val gap = nearest?.let { maxOf(abs(it.x - t.x), abs(it.z - t.z)) }
            unplaced += "${e.name}(${e.id}) '${e.option}' at ${t.x},${t.z},${t.height} source=${e.source} " +
                "nearest667=${nearest?.let { "${it.x},${it.z},${it.height} gap=$gap" } ?: "none on plane (placed ${same.size}x)"}"
        }

        val rejected = ObjectTeleports.retainPlaced { e ->
            val tile = e.tile!!
            byId[e.id].orEmpty().any { it.x == tile.x && it.z == tile.z && it.height == tile.height }
        }
        println("OBJECT_TELEPORT_AUDIT entries=${entries.size} exact=$exact unplaced=${unplaced.size} rejected=${rejected.size} placementsDecoded=${placements.size}")
        unplaced.sorted().forEach { println("UNPLACED $it") }
        assertTrue(placements.isNotEmpty(), "no placements decoded - wrong cache/xtea path?")
        assertTrue(rejected.size == unplaced.size, "placement filter rejected ${rejected.size}, expected ${unplaced.size}")
        assertTrue(ObjectTeleports.size == exact, "${ObjectTeleports.size} placed entries remain, expected $exact")
    }

    private fun loadEntries(): List<ObjectTeleports.Entry> {
        val path = Paths.get("..", "..", "data", "cfg", "object-teleports", "object-teleports.json")
        ObjectTeleports.load(path)
        val field = ObjectTeleports::class.java.getDeclaredField("byTile").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return (field.get(ObjectTeleports) as Map<Int, List<ObjectTeleports.Entry>>).values.flatten()
    }

    private fun placementsOf(ids: List<Int>): List<Placement> {
        val cache = File("../../data/cache").path
        val xtea = File("../../data").walkTopDown().maxDepth(3).first { it.isFile && it.name.contains("xtea", ignoreCase = true) }.path
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer))
        try {
            ObjectPlacementProbeTool.main(arrayOf(cache, xtea, "id") + ids.map { it.toString() })
        } finally {
            System.setOut(original)
        }
        return Regex("""PLACEMENT id=(\d+) name='[^']*' options=(\[[^\]]*\]) tile=(\d+),(\d+),(\d+)""").findAll(buffer.toString()).map {
            Placement(it.groupValues[1].toInt(), it.groupValues[3].toInt(), it.groupValues[4].toInt(), it.groupValues[5].toInt(), it.groupValues[2])
        }.toList()
    }
}
