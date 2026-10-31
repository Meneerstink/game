package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpellRuneArgsToolTests {
    private fun ints(vararg v: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        v.forEach { x -> out.write(0); out.write(x ushr 24 and 0xFF); out.write(x ushr 16 and 0xFF); out.write(x ushr 8 and 0xFF); out.write(x and 0xFF) }
        return out.toByteArray()
    }

    @Test
    fun `rewrite swaps exactly the eight rune ints and keeps the length`() {
        val old = listOf(556, 7, 565, 1, 560, 1, -1, 0)
        val new = SpellRuneArgsTool.padded(listOf(556 to 7, SpellRuneArgsTool.WRATH_RUNE to 1))
        val component = byteArrayOf(9, 9) + ints(*old.toIntArray()) + byteArrayOf(7)
        val rewritten = SpellRuneArgsTool.rewrite(component, old, new)!!
        assertEquals(component.size, rewritten.size)
        assertContentEquals(byteArrayOf(9, 9) + ints(*new.toIntArray()) + byteArrayOf(7), rewritten)
    }

    @Test
    fun `rewrite refuses a sequence that is missing or not unique`() {
        val old = listOf(1, 2, 3, 4, 5, 6, 7, 8)
        assertNull(SpellRuneArgsTool.rewrite(byteArrayOf(1, 2, 3), old, old))
        assertNull(SpellRuneArgsTool.rewrite(ints(*old.toIntArray()) + ints(*old.toIntArray()), old, old))
    }

    @Test
    fun `rune-free teleports keep non-rune ingredients such as Ape Atoll's banana`() {
        val ape = listOf(554 to 2, 555 to 2, 563 to 2, 1963 to 1)
        assertEquals(listOf(1963 to 1), ape.filter { it.first !in SpellRuneArgsTool.RUNES })
        assertTrue(9075 in SpellRuneArgsTool.RUNES, "astral runes are runes")
    }

    @Test
    fun `N72 every self teleport gets runes back and fits the four rune pairs`() {
        val books = mapOf(192 to SpellRuneArgsTool.STANDARD_FREE_TELEPORT_NAMES, 193 to SpellRuneArgsTool.ANCIENT_FREE_TELEPORT_NAMES, 430 to SpellRuneArgsTool.LUNAR_FREE_TELEPORT_NAMES)
        for ((book, names) in books) {
            for (name in names) {
                val pairs = SpellRuneArgsTool.RESTORED_TELEPORTS[book to name] ?: error("$book $name has no runes to put back")
                assertTrue(pairs.size in 1..4 && pairs.any { it.first in SpellRuneArgsTool.RUNES }, "$book $name: $pairs")
            }
        }
        assertEquals(listOf(554 to 2, 555 to 2, 563 to 2, 1963 to 1), SpellRuneArgsTool.RESTORED_TELEPORTS[192 to "Teleport to Ape Atoll"], "Ape Atoll: runes and its banana")
    }
}
