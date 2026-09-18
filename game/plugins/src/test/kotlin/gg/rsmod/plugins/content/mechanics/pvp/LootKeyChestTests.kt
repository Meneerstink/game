package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool
import kotlin.test.Test
import kotlin.test.assertEquals

class LootKeyChestTests {
    @Test
    fun `tab value text follows the OSRS k and M abbreviations`() {
        assertEquals("0", LootKeyChest.valueText(0))
        assertEquals("999", LootKeyChest.valueText(999))
        assertEquals("53k", LootKeyChest.valueText(53_400))
        assertEquals("999k", LootKeyChest.valueText(999_999))
        assertEquals("2M", LootKeyChest.valueText(2_600_000))
    }

    @Test
    fun `every loot slot and key tab component maps back to its index and nothing else does`() {
        for (slot in 0 until LootKeyChest.SLOT_COUNT) {
            assertEquals(slot, LootKeyChest.slotOf(LootKeyChest.SLOT_FIRST + slot))
        }
        assertEquals(-1, LootKeyChest.slotOf(LootKeyChest.SLOT_FIRST - 1))
        assertEquals(-1, LootKeyChest.slotOf(LootKeyChest.SLOT_FIRST + LootKeyChest.SLOT_COUNT))
        for (tab in 0 until LootKeys.MAX_KEYS) {
            val base = LootKeyChest.TAB_FIRST + tab * LootKeyChest.TAB_STRIDE
            assertEquals(tab, LootKeyChest.tabOf(base))
            assertEquals(tab, LootKeyChest.tabOf(base + 1))
            assertEquals(tab, LootKeyChest.tabOf(base + 2))
        }
        assertEquals(-1, LootKeyChest.tabOf(LootKeyChest.TAB_FIRST + LootKeys.MAX_KEYS * LootKeyChest.TAB_STRIDE))
    }

    @Test
    fun `the screen addresses exactly the components the import tool builds`() {
        val fonts = LootKeyInterfaceImportTool.Fonts(494, 495, 496, 497)
        val built = LootKeyInterfaceImportTool.components(fonts).associateBy { it.id }
        assertEquals(LootKeyInterfaceImportTool.COMPONENT_COUNT, built.size)
        // Every component the server talks to exists and has the type the packet needs.
        val graphics = listOf(LootKeyChest.CLOSE, LootKeyChest.DESTROY_BUTTON, LootKeyChest.INVENTORY_BUTTON, LootKeyChest.BANK_BUTTON, LootKeyChest.CONFIRM_BUTTON, LootKeyChest.CANCEL_BUTTON) +
            (0 until LootKeyChest.SLOT_COUNT).map { LootKeyChest.SLOT_FIRST + it } +
            (0 until LootKeys.MAX_KEYS).flatMap { listOf(LootKeyChest.TAB_FIRST + it * LootKeyChest.TAB_STRIDE, LootKeyChest.TAB_FIRST + it * LootKeyChest.TAB_STRIDE + 1) }
        graphics.forEach { id -> assertEquals(5, built.getValue(id).type, "component $id must be a graphic") }
        listOf(LootKeyChest.ITEM_LAYER, LootKeyChest.NOTE_LAYER, LootKeyChest.EMPTY_LAYER, LootKeyChest.CONFIRM_LAYER).forEach { id ->
            assertEquals(0, built.getValue(id).type, "component $id must be a layer")
        }
        (0 until LootKeys.MAX_KEYS).forEach { assertEquals(4, built.getValue(LootKeyChest.TAB_FIRST + it * LootKeyChest.TAB_STRIDE + 2).type) }
        assertEquals(4, built.getValue(LootKeyInterfaceImportTool.BANK_USED_TEXT).type)
        assertEquals(4, built.getValue(LootKeyInterfaceImportTool.BANK_SIZE_TEXT).type)
        // Ops the button plugin relies on.
        assertEquals(listOf("Close"), built.getValue(LootKeyChest.CLOSE).ops)
        assertEquals("Withdraw-1", built.getValue(LootKeyChest.SLOT_FIRST).ops[0])
        assertEquals("Examine", built.getValue(LootKeyChest.SLOT_FIRST).ops[9])
        assertEquals(listOf("Item"), built.getValue(LootKeyChest.ITEM_LAYER).ops)
        assertEquals(listOf("Note"), built.getValue(LootKeyChest.NOTE_LAYER).ops)
    }

    @Test
    fun `components encode exactly as the revision-667 client decodes them`() {
        val fonts = LootKeyInterfaceImportTool.Fonts(494, 495, 496, 497)
        LootKeyInterfaceImportTool.components(fonts).forEach { c ->
            val bytes = LootKeyInterfaceImportTool.encode(c)
            val consumed = ClientComponentDecoder.consume(bytes)
            assertEquals(bytes.size, consumed, "component ${c.id} (type ${c.type}) must be consumed exactly by the client decoder")
            assertEquals(c.type, ClientComponentDecoder.type(bytes))
        }
    }

    /** A field-for-field walk of `Component.decode` (2011scape-client) for legacy (version 255) components. */
    private object ClientComponentDecoder {
        fun type(d: ByteArray): Int = d[1].toInt() and 0x7F

        fun consume(d: ByteArray): Int {
            var p = 0
            fun g1(): Int = d[p++].toInt() and 0xFF
            fun g2(): Int { p += 2; return ((d[p - 2].toInt() and 0xFF) shl 8) or (d[p - 1].toInt() and 0xFF) }
            fun g3() { p += 3 }
            fun g4() { p += 4 }
            fun gjstr() { while (d[p] != 0.toByte()) p++; p++ }
            val version = g1().let { if (it == 255) -1 else it }
            var type = g1()
            if (type and 0x80 != 0) { type = type and 0x7F; gjstr() }
            g2(); g2(); g2(); g2(); g2() // clientcode, x, y, w, h
            g1(); g1(); g1(); g1() // resize/repos modes
            g2() // layer
            g1() // flags
            when (type) {
                0 -> { g2(); g2(); if (version < 0) g1() }
                5 -> { g4(); g2(); g1(); g1(); g1(); g4(); g1(); g1(); g4(); if (version >= 3) g1() }
                4 -> { g2(); if (version >= 2) g1(); gjstr(); g1(); g1(); g1(); g1(); g4(); g1(); if (version >= 0) g1() }
                3 -> { g4(); g1(); g1() }
                9 -> { g1(); g4(); g1() }
                else -> error("type $type")
            }
            g3() // events
            var opkeyRate = g1()
            while (opkeyRate != 0) { g1(); g1(); g1(); opkeyRate = g1() }
            gjstr() // opBase
            val opFlags = g1()
            repeat(opFlags and 0xF) { gjstr() }
            val cursorCount = opFlags shr 4
            if (cursorCount > 0) { g1(); g2() }
            if (cursorCount > 1) { g1(); g2() }
            gjstr() // pauseText
            g1(); g1(); g1() // drag
            gjstr() // targetVerb
            // events had no target mask (0), so no target params; version < 0: no mouseOverCursor / params
            val hooks = if (version >= 0) 21 else 20
            repeat(hooks) { val n = g1(); repeat(n) { if (g1() == 0) g4() else gjstr() } }
            repeat(5) { val n = g1(); repeat(n) { g4() } }
            return p
        }
    }
}
