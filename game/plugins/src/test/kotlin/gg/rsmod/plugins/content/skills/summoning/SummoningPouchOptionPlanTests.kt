package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.tools.importer.ItemDefCodec
import io.netty.buffer.Unpooled
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N453: validates Claude's cache change in memory against a read-only snapshot; never opens or writes a cache. */
class SummoningPouchOptionPlanTests {
    private data class Pouch(val id: Int, val supported: Boolean, val note: Int?, val raw: ByteArray, val noteRaw: ByteArray?)

    private val pouches: List<Pouch> by lazy {
        val stream = javaClass.getResourceAsStream("/summoning/N453-pouch-options.tsv")!!
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                val cells = line.split('\t')
                Pouch(cells[0].toInt(), cells[2].toBoolean(), cells[3].toIntOrNull(), hex(cells[4]),
                    cells[5].takeUnless { it == "-" }?.let(::hex))
            }.toList()
        }
    }

    @Test
    fun `the plan includes all 78 executable pouches and every other cache pouch with Summon`() {
        assertEquals(146, pouches.size)
        assertEquals(146, pouches.map { it.id }.toSet().size)
        assertEquals(SummoningPouchData.values().map { it.pouch }.toSet(), pouches.filter { it.supported }.map { it.id }.toSet())
        assertEquals(78, pouches.count { it.supported })
        assertEquals(144, pouches.count { it.note != null })
        for (pouch in pouches) {
            val before = decode(pouch.id, pouch.raw)
            assertEquals(listOf(null, null, null, "Summon", if (pouch.id == 12526) "Destroy" else null), before.inventoryMenu.toList())
            assertFalse(before.noted)
        }
    }

    @Test
    fun `moving Summon to slot one preserves every other byte and the Destroy exception`() {
        for (pouch in pouches) {
            // Proposed transformation ONLY IN MEMORY: opcode 35 = inventory slot 1, opcode 38 = slot 4.
            val proposed = ItemDefCodec.cloneWithOverrides(pouch.raw, mapOf(35 to "Summon"), removedOpcodes = setOf(38))
            val after = decode(pouch.id, proposed)
            assertEquals("Summon", after.inventoryMenu[0], "${pouch.id}: first action")
            assertEquals(null, after.inventoryMenu[3], "${pouch.id}: no duplicate Summon")
            assertEquals(1, after.inventoryMenu.count { it == "Summon" })
            assertEquals(if (pouch.id == 12526) "Destroy" else null, after.inventoryMenu[4])
            val withoutActions = setOf(35, 38)
            assertTrue(ItemDefCodec.cloneWithOverrides(pouch.raw, emptyMap(), removedOpcodes = withoutActions).contentEquals(
                ItemDefCodec.cloneWithOverrides(proposed, emptyMap(), removedOpcodes = withoutActions)), "${pouch.id}: unrelated bytes preserved")
        }
    }

    @Test
    fun `all 144 notes stay notes without a Summon option`() {
        for (pouch in pouches.filter { it.note != null }) {
            val note = decode(pouch.note!!, pouch.noteRaw!!)
            assertTrue(note.noted, "${pouch.note}: note template")
            assertEquals(pouch.id, note.noteLinkId)
            assertFalse(note.inventoryMenu.any { it == "Summon" })
        }
    }

    @Test
    fun `the server binds all pouch Summon actions by name and the new slot resolves to option one`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/familiar.plugin.kts").readText()
        assertTrue("SummoningPouchData.values().forEach" in source)
        assertTrue("on_item_option(item = data.pouch, option = \"summon\")" in source)
        for (pouch in pouches.filter { it.supported }) {
            val proposed = decode(pouch.id, ItemDefCodec.cloneWithOverrides(pouch.raw, mapOf(35 to "Summon"), removedOpcodes = setOf(38)))
            // KotlinPlugin.on_item_option uses this exact name lookup, so no numeric-slot server patch is needed.
            assertEquals(1, proposed.inventoryMenu.indexOfFirst { it?.lowercase() == "summon" } + 1)
        }
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun decode(id: Int, bytes: ByteArray): ItemDef {
        val buffer = Unpooled.wrappedBuffer(bytes)
        return try { ItemDef(id).also { it.decode(buffer) } } finally { buffer.release() }
    }
}
