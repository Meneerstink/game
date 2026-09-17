package gg.rsmod.game.tools.importer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner buglist 2026-09-17c: OSRS player sequences are imported on a human rig merged with the 667 rig, and the imported weapons get
 * their own stances. These tests pin the merge rule, the hand-item conversion and the BASType rewrite on synthetic data.
 */
class OsrsPlayerSeqImportTests {
    /** OSRS framemap: count, types, sizes, labels. */
    private fun osrsBase(vararg groups: Pair<Int, List<Int>>): ByteArray =
        (listOf(groups.size) + groups.map { it.first } + groups.map { it.second.size } + groups.flatMap { it.second }).map { it.toByte() }.toByteArray()

    /** 667 AnimBase: count, types, count booleans, count u16 masks, sizes, labels. */
    private fun base667(vararg groups: Pair<Int, List<Int>>): ByteArray =
        (
            listOf(groups.size) + groups.map { it.first } + List(groups.size) { 0 } + List(groups.size * 2) { 0xFF } +
                groups.map { it.second.size } + groups.flatMap { it.second }
        ).map { it.toByte() }.toByteArray()

    private fun labels(base: ByteArray): List<List<Int>> {
        val count = base[0].toInt() and 0xFF
        var pos = 1 + count * 4
        val sizes = List(count) { base[pos++].toInt() and 0xFF }
        return sizes.map { size -> List(size) { base[pos++].toInt() and 0xFF } }
    }

    @Test
    fun `a 667-only label rides with its companions wherever the OSRS rig put them and an OSRS-known label keeps the OSRS grouping`() {
        val wholeBody = (1..120).toList()
        // The OSRS limb groups sit at OTHER indexes than the 667 ones (only ~70 of 205 groups line up by index in the real rigs).
        val osrs = osrsBase(1 to wholeBody, 0 to listOf(63), 2 to listOf(31, 32, 168), 2 to listOf(35, 36, 37), 5 to listOf(3, 18))
        val local = base667(1 to wholeBody + 240, 2 to listOf(35, 36, 37, 168, 236), 2 to listOf(31, 32, 217), 0 to listOf(156), 5 to listOf(3, 250))
        val merged = labels(OsrsFxImportTool.mergePlayerBase(osrs, local))
        assertEquals(wholeBody + listOf(217, 236, 240), merged[0], "whole-body groups move every 667-only vertex label")
        assertEquals(listOf(63), merged[1], "origin (pivot) groups keep the OSRS labels")
        // 217's companions {31, 32} are all here; 236's companions {35, 36, 37, 168} are not (only 168) -> not added.
        assertEquals(listOf(31, 32, 168, 217), merged[2])
        // 236 joins the group with more than half of its companions; 168 is OSRS-known and stays where OSRS put it.
        assertEquals(listOf(35, 36, 37, 236), merged[3])
        assertEquals(listOf(3, 18), merged[4], "alpha groups hold face labels and are never touched (250 is not added)")
    }

    @Test
    fun `an OSRS pivot gains the labels the 667 rig uses for the same pivot`() {
        // Owner screenshots 2026-09-17c: OSRS pivots the hand weapon on label 27, the 667 rig on 196 / 200 / 27 (HD body kits carry the
        // pivot on 196 / 200). The 667 pivot is found through the limb group it serves, wherever that group sits.
        val osrs = osrsBase(0 to listOf(27), 2 to listOf(50, 51, 52), 0 to listOf(9), 2 to listOf(12, 13))
        val local = base667(2 to listOf(12, 13, 14), 0 to listOf(196, 200, 27), 2 to listOf(50, 51, 52, 53), 0 to listOf(77), 2 to listOf(90, 91))
        val merged = labels(OsrsFxImportTool.mergePlayerBase(osrs, local))
        assertEquals(listOf(27, 196, 200), merged[0], "the weapon-hand pivot")
        assertEquals(listOf(9), merged[2], "a 667 limb without an origin before it, or an origin sharing no label, adds nothing")
    }

    @Test
    fun `a player frame loses its alpha channel and keeps every other value`() {
        // base: group 0 translate, group 1 alpha, group 2 rotation; frame: header base 0, 3 groups, flags x / x / x+y, values 5, 9, 1, 2
        val types = intArrayOf(1, 5, 2)
        val frame = byteArrayOf(0, 0, 3, 1, 1, 3, (5 + 64).toByte(), (9 + 64).toByte(), (1 + 64).toByte(), (2 + 64).toByte())
        val kept = OsrsFxImportTool.convertFrame(frame, types, 7)
        val dropped = OsrsFxImportTool.convertFrame(frame, types, 7, dropAlpha = true)
        assertContentEquals(byteArrayOf(1, 0, 7, 3, 1, 1, 3, (20 + 64).toByte(), (9 + 64).toByte(), (16 + 64).toByte(), (32 + 64).toByte()), kept)
        assertContentEquals(byteArrayOf(1, 0, 7, 3, 1, 0, 3, (20 + 64).toByte(), (16 + 64).toByte(), (32 + 64).toByte()), dropped)
    }

    @Test
    fun `the merged base has the 667 layout and the OSRS group count`() {
        val osrs = osrsBase(1 to listOf(1, 2), 2 to listOf(3))
        val merged = OsrsFxImportTool.mergePlayerBase(osrs, base667(1 to listOf(1, 2, 250)))
        assertEquals(2, merged[0].toInt())
        assertContentEquals(byteArrayOf(1, 2), merged.copyOfRange(1, 3), "types")
        assertContentEquals(byteArrayOf(0, 0), merged.copyOfRange(3, 5), "booleans")
        assertTrue(merged.copyOfRange(5, 9).all { it == 0xFF.toByte() }, "part masks 0xFFFF")
        assertEquals(listOf(listOf(1, 2, 250), listOf(3)), labels(merged))
    }

    @Test
    fun `sequence hand items - OSRS hide becomes the 667 hide value and an imported item its local id`() {
        // opcode 6 value 0 (hide), opcode 7 value 512 + OSRS item 22804, opcode 0
        val bytes = byteArrayOf(6, 0, 0, 7, ((512 + 22804) ushr 8).toByte(), (512 + 22804).toByte(), 0)
        val mapped = OsrsFxImportTool.decodeOsrsSeq(bytes, mapOf(22804 to 22798))
        assertEquals(0xFFFF, mapped.leftHand)
        assertEquals(22798, mapped.rightHand)
        val unmapped = OsrsFxImportTool.decodeOsrsSeq(bytes, emptyMap())
        assertNull(unmapped.rightHand, "an item that was not imported is dropped, never guessed")
        assertTrue(unmapped.dropped.any { "22804" in it })
    }

    @Test
    fun `a stance replaces only the movement sequences of the template and drops its random idle list`() {
        // template: ready 808 walk 819, run 824, random idles (opcode 52, one entry), yaw acceleration (opcode 29)
        val template =
            byteArrayOf(1, 3, 40, 3, 51, 6, 3, 56, 52, 1, 3, 40, 10, 29, 9, 0)
        val stance = OsrsBasImportTool.Stance("test", ready = 7220, walk = 7223, run = 7221, walkBack = 7223)
        val built = OsrsBasImportTool.decode(OsrsBasImportTool.build(template, stance) { it + 10000 })
        fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
        assertEquals(17220, u16(built.getValue(1).single(), 0))
        assertEquals(17223, u16(built.getValue(1).single(), 2))
        assertEquals(17221, u16(built.getValue(6).single(), 0))
        assertEquals(17223, u16(built.getValue(40).single(), 0))
        assertNull(built[52], "the template's random idle list would bring the default stand back")
        assertContentEquals(byteArrayOf(9), built.getValue(29).single(), "everything else of the template is kept")
        // A stance that only changes the stand keeps the template's walk.
        val standOnly = OsrsBasImportTool.decode(OsrsBasImportTool.build(template, OsrsBasImportTool.Stance("idle", ready = 3296)) { it + 10000 })
        assertEquals(13296, u16(standOnly.getValue(1).single(), 0))
        assertEquals(819, u16(standOnly.getValue(1).single(), 2))
    }
}
