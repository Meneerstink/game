package gg.rsmod.plugins.content.combat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Combat run 2026-09-29 (equipment conformance "high armour"): the charged Ancient wyvern shield (OSRS 21633 -> 22952) and
 * Dragonfire ward (22002 -> 22954) carried their uncharged stats, 50 lower on stab, slash, crush and ranged defence (OSRS Wiki
 * infobox, "Charged" tab).
 */
class ChargedShieldStatsTests {
    private fun stats(id: Int): Map<String, Int> {
        val lines = File("../../data/cfg/items.yml").readLines()
        val start = lines.indexOfFirst { it.trim() == "- id: $id" }
        val out = mutableMapOf<String, Int>()
        for (line in lines.drop(start + 1)) {
            if (line.startsWith("- id:")) break
            Regex("""^\s+(defence_\w+): (-?\d+)""").find(line)?.let { out[it.groupValues[1]] = it.groupValues[2].toInt() }
        }
        return out
    }

    @Test
    fun `charged Ancient wyvern shield and Dragonfire ward use their charged OSRS defences`() {
        val aws = stats(22952)
        assertEquals(listOf(72, 80, 75, -5), listOf(aws["defence_stab"], aws["defence_slash"], aws["defence_crush"], aws["defence_ranged"]))
        val ward = stats(22954)
        assertEquals(listOf(25, 30, 28, 68), listOf(ward["defence_stab"], ward["defence_slash"], ward["defence_crush"], ward["defence_ranged"]))
        val uncharged = stats(22953)
        assertEquals(listOf(22, 30, 25, -55), listOf(uncharged["defence_stab"], uncharged["defence_slash"], uncharged["defence_crush"], uncharged["defence_ranged"]))
    }
}
