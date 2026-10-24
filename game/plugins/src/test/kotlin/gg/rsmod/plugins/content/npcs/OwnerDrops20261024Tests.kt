package gg.rsmod.plugins.content.npcs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-10-24 (chat): Nex drops the Zaryte crossbow whole (N269, the OSRS Nihil horn's 1/258), the WildyWyrm no Statius's
 * equipment but ordinary boss loot (N270), the Glacor none of the 2011 Ragefire / Steadfast / Glaiven boots (N271).
 * The Drop Viewer and the Collection Log read these same tables, so they follow by themselves.
 */
class OwnerDrops20261024Tests {
    private fun plugin(path: String) = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/$path").readText()

    /** The slots of one `main { total(N) ... }` table: every `slots = n` and the `nothing(n)` tail. */
    private fun mainSlots(text: String): Pair<Int, Int> {
        val main = text.substring(text.indexOf("main {"))
        val total = Regex("total\\((\\d+)\\)").find(main)!!.groupValues[1].toInt()
        val used = Regex("slots = (\\d+)").findAll(main).sumOf { it.groupValues[1].toInt() } +
            Regex("nothing\\((\\d+)\\)").findAll(main).sumOf { it.groupValues[1].toInt() }
        return total to used
    }

    @Test
    fun `Nex drops the Zaryte crossbow at 15 of 3840 and every other row keeps its rate`() {
        val nex = plugin("godwars/nex.plugin.kts")
        assertTrue("obj(Items.ZARYTE_CROSSBOW, slots = 15)" in nex)
        assertEquals(258.0, 3840.0 / 15, 2.5, "about the OSRS Nihil horn's 1/258")
        listOf("TORVA_FULL_HELM", "TORVA_PLATEBODY", "TORVA_PLATELEGS", "ZARYTE_BOW").forEach {
            assertTrue("obj(Items.$it, slots = 10)" in nex, "$it keeps 1/384")
        }
        val (total, used) = mainSlots(nex)
        assertEquals(3840, total)
        assertTrue(used <= total, "the rows fit the roll ($used of $total)")
    }

    @Test
    fun `the WildyWyrm drops no Statius's equipment, big bones every kill and a full boss table`() {
        val wyrm = plugin("other/wildywyrm_drops.plugin.kts")
        assertFalse(Regex("Items\\.(CORRUPT_)?STATIUS").containsMatchIn(wyrm), "no Statius's piece")
        assertTrue("guaranteed { obj(Items.BIG_BONES) }" in wyrm)
        assertTrue("Rare.rareTable" in wyrm)
        val (total, used) = mainSlots(wyrm)
        assertEquals(total, used, "every slot of the roll is declared")
    }

    @Test
    fun `the Glacor drops none of the 2011 boots and its roll stays 512`() {
        val glacor = plugin("other/glacor.plugin.kts")
        assertFalse(Regex("obj\\(Items\\.(RAGEFIRE|STEADFAST|GLAIVEN)_BOOTS").containsMatchIn(glacor))
        val (total, used) = mainSlots(glacor)
        assertEquals(512, total)
        // The boots' 3 slots joined the "nothing" tail: the other rows keep their rate (the table never filled all 512).
        assertEquals(472, used)
    }
}
