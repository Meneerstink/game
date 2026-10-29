package gg.rsmod.game.fs.def

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * N422: the worn equipment slot (clientscript 1612) shows params 528-531 as ops 2-5 and param 1211 as op 6. The server read only
 * 528-535, so a sixth worn option in 1211 (Games necklace "Burgh De Rott", Explorer's ring 4 "Superheat") could not be bound.
 */
class ItemDefWornMenuTests {
    private fun def(vararg params: Pair<Int, String>): ItemDef {
        val buf = Unpooled.buffer()
        buf.writeByte(249)
        buf.writeByte(params.size)
        params.forEach { (id, text) ->
            buf.writeByte(1) // string
            buf.writeMedium(id)
            buf.writeBytes(text.toByteArray(Charsets.ISO_8859_1))
            buf.writeByte(0)
        }
        buf.writeByte(0)
        return ItemDef(1).also { it.decode(buf) }
    }

    @Test
    fun `param 1211 is the sixth worn option, in the slot of 532`() {
        val d = def(528 to "Burthorpe", 529 to "Barbarian Outpost", 530 to "Gamers' Grotto", 531 to "Corporeal Beast", 1211 to "Burgh De Rott")
        assertEquals(listOf("Burthorpe", "Barbarian Outpost", "Gamers' Grotto", "Corporeal Beast", "Burgh De Rott"), d.equipmentMenu.filterNotNull())
        assertEquals("Burgh De Rott", d.equipmentMenu[4])
    }

    @Test
    fun `param 532 still wins over 1211`() {
        val d = def(532 to "Five", 1211 to "Six")
        assertEquals("Five", d.equipmentMenu[4])
    }
}
