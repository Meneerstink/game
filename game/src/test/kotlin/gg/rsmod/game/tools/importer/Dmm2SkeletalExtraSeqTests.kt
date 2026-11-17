package gg.rsmod.game.tools.importer

import java.io.ByteArrayOutputStream
import kotlin.test.*

class Dmm2SkeletalExtraSeqTests {
    @Test fun `extra sequence asset keys cannot collide with movement slots or other NPCs`() {
        val keys = (15569..15571).flatMap { npc -> (10227..10262).map { seq -> OsrsPetImportTool.extraSeqKey(npc, seq) } }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it > 15571 * 64 + 63 })
        assertFailsWith<IllegalArgumentException> { OsrsPetImportTool.extraSeqKey(32768, 1) }
        assertFailsWith<IllegalArgumentException> { OsrsPetImportTool.extraSeqKey(15571, -1) }
    }

    private fun base(types: List<Int>, labels: List<List<Int>>): ByteArray = ByteArrayOutputStream().also { b ->
        b.write(types.size); types.forEach { b.write(it) }
        repeat(types.size) { b.write(0) }; repeat(types.size) { b.write(255); b.write(255) }
        labels.forEach { b.write(it.size) }; labels.flatten().forEach { b.write(it) }
    }.toByteArray()

    @Test fun `frozen base preserves rotation translation and optional scale positions`() {
        val b = base(listOf(0,3,2,1,2,1), listOf(emptyList(),listOf(0),listOf(0),listOf(0),listOf(1),listOf(1)))
        val frozen = SkeletalBakeTool.frozenGroups(b, 2)
        assertContentEquals(intArrayOf(1,-1), frozen.scale)
        assertContentEquals(intArrayOf(2,4), frozen.rotate)
        assertContentEquals(intArrayOf(3,5), frozen.translate)
        assertFailsWith<IllegalArgumentException> { SkeletalBakeTool.frozenGroups(b, 3) }
        val bad = base(listOf(0,2,2,1), listOf(emptyList(),listOf(0),listOf(0),listOf(0)))
        assertFailsWith<IllegalArgumentException> { SkeletalBakeTool.frozenGroups(bad, 1) }
    }

    @Test fun `extra sidecar creates clip reuses skin and rig and only appends the shared index`() {
        val oldIndex = "# keep exactly\r\nseq.10=idle|rig_2138|skin_15320|0|100||10230\r\n".toByteArray()
        val files = linkedMapOf("rig_2138" to byteArrayOf(1), "skin_15320" to byteArrayOf(2), "clip_212_9" to byteArrayOf(3))
        val line = "seq.20=clip_212_9|rig_2138|skin_15320|0|60||10241"
        val existing = mapOf("skel_rig_2138" to (5 to byteArrayOf(1)), "skel_skin_15320" to (6 to byteArrayOf(2)), "skel_index" to (7 to oldIndex))
        val export = SkeletalSidecarExportTool.Export(files, listOf(line))
        val changes = SkeletalSidecarExportTool.appendOnlyMutations(export, 100) { existing[it] }
        assertEquals(2, changes.size)
        assertEquals("skel_clip_212_9", changes[0].groupName)
        assertEquals(100, changes[0].groupId)
        assertEquals(7, changes[1].groupId)
        assertContentEquals(oldIndex, changes[1].newBytes!!.take(oldIndex.size).toByteArray())
        assertEquals(CacheItemProbeTool.sha1(oldIndex), changes[1].expectedCurrentSha1)
        val after = existing + ("skel_clip_212_9" to (100 to byteArrayOf(3))) + ("skel_index" to (7 to changes[1].newBytes!!))
        assertTrue(SkeletalSidecarExportTool.appendOnlyMutations(export, 101) { after[it] }.isEmpty())
    }

    @Test fun `extra sidecar refuses changes to shared skin rig or existing sequence entry`() {
        val export = SkeletalSidecarExportTool.Export(linkedMapOf("rig_2138" to byteArrayOf(1)), listOf("seq.20=new"))
        assertFailsWith<IllegalStateException> {
            SkeletalSidecarExportTool.appendOnlyMutations(export, 100) { 5 to byteArrayOf(9) }
        }
        assertFailsWith<IllegalStateException> {
            SkeletalSidecarExportTool.appendOnlyMutations(SkeletalSidecarExportTool.Export(linkedMapOf(), listOf("seq.20=new")), 100) {
                if (it == "skel_index") 5 to "seq.20=old\n".toByteArray() else null
            }
        }
    }
}
