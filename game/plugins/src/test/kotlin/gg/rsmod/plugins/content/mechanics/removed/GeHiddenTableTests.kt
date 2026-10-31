package gg.rsmod.plugins.content.mechanics.removed

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N464 (owner 2026-10-30): every removed or unobtainable item is out of the client's Grand Exchange search - the cache tool drops
 * their GE flag from the ids in data/cfg/removed/ge_hidden.txt, which must stay equal to [RemovedItems.geHiddenIds].
 * Regenerate: RSPS_WRITE_GE_HIDDEN=1, run this test, then RemovedContentCacheTool apply (servers stopped).
 */
class GeHiddenTableTests {
    private val file = File("../../data/cfg/removed/ge_hidden.txt")

    @Test
    fun `the GE-hidden table lists exactly the removed and unobtainable items`() {
        val ids = RemovedItems.geHiddenIds
        if (System.getenv("RSPS_WRITE_GE_HIDDEN") == "1") {
            file.parentFile.mkdirs()
            file.writeText(
                "# Generated from RemovedItems.geHiddenIds (GeHiddenTableTests) - do not edit. Read by RemovedContentCacheTool:\n" +
                    "# these items lose the cache's Grand Exchange flag (opcode 65), so the client's GE search no longer lists them.\n" +
                    ids.joinToString("\n", postfix = "\n"),
            )
        }
        val table = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim().toInt() }.toSortedSet()
        assertEquals(ids, table, "ge_hidden.txt is out of date: regenerate it (see the class comment)")
        listOf(Items.ZARYTE_BOW, Items.ZARYTE_BOW_20173, Items.ZARYTE_BOW_BROKEN, Items.PERNIX_BODY, Items.VIRTUS_MASK, Items.ARMADYL_RUNE, Items.CORRUPT_DRAGON_DAGGER, Items.C_DRAGON_DAGGER_DEG, Items.CORRUPT_VESTAS_CHAINBODY_DEG).forEach {
            assertTrue(it in table, "$it hidden")
        }
        listOf(Items.ZARYTE_CROSSBOW, Items.TORVA_PLATEBODY, Items.ABYSSAL_WHIP, Items.VESTAS_LONGSWORD, Items.DRAGON_DAGGER).forEach { assertFalse(it in table, "$it stays on the GE") }
    }
}
