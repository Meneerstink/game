package gg.rsmod.game.tools.importer

import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.varp.VarpSet
import io.netty.buffer.Unpooled
import kotlin.test.*

class ClueStashImportTests {
    @Test fun `legacy and wide scenery transform codecs preserve every variant fallback and following opcode`() {
        val tables = mapOf("legacy" to intArrayOf(60001, 60002, -1), "legacy-default" to intArrayOf(60001, -1, 60003),
            "wide" to intArrayOf(66313, 66314, -1), "wide-default" to intArrayOf(66313, -1, 66315))
        for ((name, table) in tables) {
            val source = ModernObjectDef(28968).also { it.name = "STASH codec fixture" }
            val bytes = Rev667LocType.encodeFromModern(source, { error("No fixture model") }, mutableListOf(), 14000 to table)
            val buf = Unpooled.wrappedBuffer(bytes)
            val def = ObjectDef(66300).also { it.decode(buf) }
            assertEquals(0, buf.readableBytes())
            assertEquals(table.toList(), def.transforms!!.toList())
            assertEquals(14000, def.varbit)
            assertEquals(-1, def.varp)
            assertEquals("STASH codec fixture", def.name)
            val census = Rev667LocType.decode(66300, bytes)
            assertEquals("STASH codec fixture", census.name)
            assertEquals(if (name.startsWith("wide")) 242 else if (table.last() == -1) 77 else 92,
                census.opcodes.single { it in setOf(77, 92, 242) })
            System.getProperty("clue.fixtures")?.let { path -> java.io.File(path).apply { mkdirs() }.resolve("$name.bin").writeBytes(bytes) }
        }
    }
    @Test fun `private STASH selectors remain independent and survive the real varp codec`() {
        val varps = VarpSet(setOf(3200))
        val selectors = (0 until 23).map { bit -> VarbitDef(15000 + bit).also {
            it.decode(Unpooled.wrappedBuffer(ClueStashCacheTool.selector(3200, bit)))
        } }
        listOf(0, 8, 16, 22).forEach { bit -> selectors[bit].let { varps.setBit(it.varp, it.startBit, it.endBit, 1) } }
        selectors.forEachIndexed { bit, selector ->
            assertEquals(if (bit in setOf(0, 8, 16, 22)) 1 else 0, varps.getBit(selector.varp, selector.startBit, selector.endBit))
        }
        assertEquals((1 shl 0) or (1 shl 8) or (1 shl 16) or (1 shl 22), varps.getState(3200))
    }

    @Test fun `selector construction refuses nontransmitted controls and invalid bit positions`() {
        assertFailsWith<IllegalArgumentException> { ClueStashCacheTool.selector(10000, 0) }
        assertFailsWith<IllegalArgumentException> { ClueStashCacheTool.selector(3200, 32) }
        assertFailsWith<IllegalArgumentException> { ClueStashCacheTool.selector(-1, 0) }
    }

    @Test fun `mapping publication cannot alias a duplicate source identity`() {
        assertFailsWith<IllegalArgumentException> { ClueStashCacheTool.decodeMapping("loc\t28968\t64000\nloc\t28968\t64001\n") }
        assertFailsWith<IllegalArgumentException> { ClueStashCacheTool.decodeMapping("unknown\t1\t2\n") }
        assertEquals(mapOf(("loc" to 28968) to 64000), ClueStashCacheTool.decodeMapping("# reviewed mapping\nloc\t28968\t64000\n"))
    }
}
