package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** N254 (owner 2026-10-23: no corked potions; answer A: every potion, the OSRS-imported ones too, on the 2011 open vial). */
class PotionVialToolTests {
    private val caches = listOf(Paths.get("..", "data", "cache").toFile(), Paths.get("..", "..", "..", "file-server", "cache").toFile())

    private val DOSE = Regex("""^.+?\s?\(([1-4])\)$""")

    @Test
    fun `in both caches no potion dose sits on the OSRS corked vial, and every dose is on the vial of its dose`() {
        val vialOfDose = PotionVialTool.OSRS_TO_667.entries.associate { (osrs, vial) -> PotionVialTool.DOSE_OF.getValue(osrs) to vial }
        caches.forEach { cache ->
            val library = CacheLibrary(cache.toString())
            try {
                var onVials = 0
                for (id in CacheItemProbeTool.allItemIds(library)) {
                    val bytes = CacheItemProbeTool.itemData(library, id) ?: continue
                    val model = OsrsLookTool.inventoryModel(bytes) ?: continue
                    val name = ItemDefCodec.readName(bytes) ?: continue
                    // Only potion doses: a poultice or a flask that happens to use an OSRS vial mesh is not a potion.
                    val dose = DOSE.matchEntire(name)?.groupValues?.get(1)?.toInt() ?: continue
                    assertTrue(model !in PotionVialTool.OSRS_TO_667.keys, "$cache item $id '$name' still has the corked OSRS vial $model")
                    if (model in vialOfDose.values) {
                        assertEquals(vialOfDose.getValue(dose), model, "$cache item $id '$name'")
                        onVials++
                    }
                }
                assertTrue(onVials > 116, "the 116 classic doses and the imported ones ($onVials) are on the 2011 vials")
            } finally {
                library.close()
            }
        }
    }

    @Test
    fun `the classic potions are back exactly as before N242`() {
        val library = CacheLibrary(caches[0].toString())
        try {
            // Ranging potion (4): the 2011 vial, colour 61 -> 36680, no lighting, as the N242 journal recorded it.
            val ranging = CacheItemProbeTool.itemData(library, 2444)!!
            val payloads = OsrsLookTool.payloads(ranging)
            assertEquals(2789, OsrsLookTool.inventoryModel(ranging))
            assertTrue(113 !in payloads && 114 !in payloads)
        } finally {
            library.close()
        }
    }

    @Test
    fun `uncorking swaps only the vial and refuses a dose on the wrong vial`() {
        val library = CacheLibrary(caches[0].toString())
        try {
            val ranging4 = CacheItemProbeTool.itemData(library, 2444)!!
            val ranging3 = CacheItemProbeTool.itemData(library, 169)!!
            assertNull(PotionVialTool.uncorked(ranging4), "already on the 2011 vial")
            val corked = ItemDefCodec.cloneWithOverrides(ranging4, emptyMap(), shortOverrides = mapOf(1 to 47509))
            val back = PotionVialTool.uncorked(corked)!!
            assertEquals(2789, OsrsLookTool.inventoryModel(back))
            assertEquals(OsrsLookTool.payloads(ranging4)[40], OsrsLookTool.payloads(back)[40], "the liquid colour stays")
            val wrong = ItemDefCodec.cloneWithOverrides(ranging3, emptyMap(), shortOverrides = mapOf(1 to 47509))
            assertFailsWith<IllegalStateException> { PotionVialTool.uncorked(wrong) }
        } finally {
            library.close()
        }
    }
}
