package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cache-free regression tests for the death-flow fixes from the 2026-09-25 audit (X-06). The X-13/D-09 recovery deadline and
 * flat fee are gone: Death's Office keeps items without a time limit and charges per item (DeathFeesTests).
 */
class DeathAuditFixTests {
    @Test
    fun `loot key encoding keeps item attributes and still reads the old format`() {
        val charged = Item(4151, 1).also { it.attr[ItemAttribute.CHARGES] = 1234 }
        val decoded = LootKeys.decode(LootKeys.encode(listOf(charged, Item(995, 10))))
        assertEquals(2, decoded.size)
        assertEquals(1234, decoded[0].attr[ItemAttribute.CHARGES])
        assertEquals(10, decoded[1].amount)
        assertEquals(0, decoded[1].attr.size)

        val legacy = LootKeys.decode("4151:1,995:25")
        assertEquals(25, legacy[1].amount)
    }
}
