package gg.rsmod.plugins.content.items.birdnests

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N113 (2026-10-22): the bird nest tables were KILL tables under npc ids 10000-10002 (Astea Frostweb): the drop viewer listed her with
 * nest loot and the kill drop boost (N100) applied to nests. They are NEST tables now, registered and rolled as NEST.
 */
class BirdNestTableTypeTests {
    @Test
    fun `every nest table is registered and rolled as NEST`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/birdnests/bird_nest.plugin.kts").readText()
        listOf("register(woodcuttingTable, BIRDS_NEST_DROP_ID", "register(seedNestTable, 10001", "register(ringNestTable, 10002").forEach { reg ->
            val line = plugin.lines().first { reg in it }
            assertTrue("DropTableType.NEST" in line, line)
        }
        assertTrue(plugin.lines().filter { "createDropInventory(player, 1000" in it }.all { "DropTableType.NEST" in it })
        val woodcutting = File("src/main/kotlin/gg/rsmod/plugins/content/skills/woodcutting/Woodcutting.kt").readText()
        assertTrue("getDrop(player, 10_000, gg.rsmod.plugins.content.drops.DropTableType.NEST)" in woodcutting)
    }
}
