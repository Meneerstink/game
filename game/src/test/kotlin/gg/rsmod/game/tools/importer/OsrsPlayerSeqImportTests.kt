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
    fun `a 667-only label joins the OSRS group of the same body part and an OSRS-articulated label keeps the OSRS grouping`() {
        val wholeBody = (1..120).toList()
        val osrs = osrsBase(1 to wholeBody, 2 to listOf(35, 36, 37), 2 to listOf(31, 32, 168), 0 to listOf(63))
        val local = base667(1 to wholeBody + 240, 2 to listOf(35, 36, 37, 168, 236), 2 to listOf(31, 32, 217), 0 to listOf(156))
        val merged = labels(OsrsFxImportTool.mergePlayerBase(osrs, local))
        assertEquals(wholeBody + 240, merged[0], "whole-body group also moves the 667-only label")
        // 236 is 667-only -> follows the arm; 168 is articulated by OSRS in group 2 -> OSRS grouping wins, it is not added here.
        assertEquals(listOf(35, 36, 37, 236), merged[1])
        assertEquals(listOf(31, 32, 168, 217), merged[2])
        // Group 3 diverged (no shared label): the OSRS group is kept as it is.
        assertEquals(listOf(63), merged[3])
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
