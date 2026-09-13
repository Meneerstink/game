package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Tile
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RCV-011: Ferox Enclave behaves like OSRS (owner 2026-09-13) where a source says how. */
class FeroxEnclaveTests {
    private val content = File("src/main/kotlin/gg/rsmod/plugins/content")

    @Test
    fun `pool applies every OSRS Pool of Refreshment effect and never special attack`() {
        val pool = File(content, "areas/home/home_pool.plugin.kts").readText()
        listOf(
            "player.heal(9999)", "player.restorePrayer(9999)", "player.runEnergy = 100.0", "player.skills.restoreAll()",
            "Poison.cure(player)", "Venom.cure(player, immunityTicks = 0, announce = false)", "Prayers.deactivateAll(player)",
            "AncientCurses.deactivateAllCurses(player)", "player.message(BountyHunterHome.POOL_MESSAGE)",
        ).forEach { assertTrue(pool.contains(it), "pool lacks $it") }
        assertFalse(Regex("(?i)special|specialAttack|SPECIAL_ATTACK").containsMatchIn(pool.substringAfter("on_obj_option")), "pool must not touch special attack")
        assertEquals("You feel reinvigorated after drinking from the pool.", BountyHunterHome.POOL_MESSAGE)
    }

    @Test
    fun `every poison cure goes through Poison cure`() {
        val offenders =
            content.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) && it.name != "Poison.kt" }
                .filter { Regex("""attr\.remove\(POISON_TICKS_LEFT_ATTR\)""").containsMatchIn(it.readText()) }
                .map { it.name }.toList()
        assertEquals(emptyList(), offenders, "files clearing poison by hand")
    }

    @Test
    fun `tele-blocked players cannot enter through a Wilderness barrier, everything else passes`() {
        val home = Tile(3137, 3629, 0)
        BountyHunterHome.gates(home).forEach { gate ->
            for (outward in listOf(true, false)) {
                for (teleblocked in listOf(true, false)) {
                    val expected = teleblocked && !outward && gate.exitsToWilderness
                    assertEquals(expected, BountyHunterHome.barrierRefusesEntry(outward, gate, teleblocked), "$gate outward=$outward tb=$teleblocked")
                }
            }
        }
        assertTrue(File(content, "areas/home/bounty_hunter_home.plugin.kts").readText().contains("BountyHunterHome.barrierRefusesEntry("))
    }

    /** Ray casting over Void's integer polygon. */
    private fun inPolygon(x: Double, y: Double, xs: List<Int>, ys: List<Int>): Boolean {
        var inside = false
        var j = xs.size - 1
        for (i in xs.indices) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]).toDouble() + xs[i]) inside = !inside
            j = i
        }
        return inside
    }

    @Test
    fun `region 12344 is single-way by Void 2011 and OSRS, 12600 keeps the recorded 2011 multi`() {
        val yml = File("../../data/cfg/areas/multi.yml").readLines()
        val regions = yml.takeWhile { !it.startsWith("chunks:") }.mapNotNull { Regex("""^- (\d+)""").find(it)?.groupValues?.get(1)?.toInt() }.toSet()
        val chunks = yml.dropWhile { !it.startsWith("chunks:") }.mapNotNull { Regex("""^- (\d+)""").find(it)?.groupValues?.get(1)?.toInt() }.toSet()
        assertFalse(12344 in regions, "12344 removed")
        assertTrue(12600 in regions, "12600 kept (SOURCE_CONFLICT recorded)")
        for (x in 3072..3135) for (z in 3584..3647) {
            assertFalse(Tile(x, z, 0).chunkCoords.hashCode() in chunks, "chunk of $x,$z listed as multi")
        }

        val toml = Paths.get("..", "..", "..", "..", "Donors", "void", "data", "area", "wilderness", "wilderness.areas.toml").toFile()
        assertTrue(toml.isFile, "Void wilderness areas not found at ${toml.absolutePath}")
        val text = toml.readText()
        val block = text.substringAfter("[wilderness_main_multi_area]").substringBefore("\n[")
        fun ints(key: String) = Regex("""$key = \[([^\]]*)]""").find(block)!!.groupValues[1].split(",").map { it.trim().toInt() }
        val xs = ints("x")
        val ys = ints("y")
        val bandit = text.substringAfter("[wilderness_bandit_camp_multi_area]").substringBefore("\n[")
        val bx = Regex("""x = \[(\d+), (\d+)]""").find(bandit)!!.groupValues.drop(1).map { it.toInt() }
        for (x in 3072..3135) for (z in 3584..3647) {
            assertFalse(inPolygon(x + 0.5, z + 0.5, xs, ys), "Void main multi area covers $x,$z")
            assertFalse(x in bx[0]..bx[1], "Void bandit camp covers $x,$z")
        }
        for (x in 3140..3190 step 5) for (z in 3590..3640 step 5) {
            assertTrue(inPolygon(x + 0.5, z + 0.5, xs, ys), "Void main multi area should cover $x,$z (region 12600)")
        }
    }
}
