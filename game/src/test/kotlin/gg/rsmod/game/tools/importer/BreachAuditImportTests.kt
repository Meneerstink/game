package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Breach audit 2026-09-27: import-side causes A1 (frame-sound radius), C4 (BAS idle turns) and C1-C3 (label coverage). */
class BreachAuditImportTests {
    private fun osrsSeqWithSound(location: Int): ByteArray {
        // opcode 1: one frame (duration 5, frame 7 in frameset 3); opcode 14: frame 0 plays sound 123 once at [location].
        return byteArrayOf(1, 0, 1, 0, 5, 0, 7, 0, 3, 14, 0, 1, 0, 0, 0, 123, 0, 1, location.toByte(), 0, 0)
    }

    @Test
    fun `A1 - the OSRS location survives decode and is packed as the 667 radius`() {
        val seq = OsrsFxImportTool.decodeOsrsSeq(osrsSeqWithSound(15))
        assertEquals(15, seq.soundRadius[0])
        val encoded = OsrsFxImportTool.encode667Seq(seq)
        val at = SeqSoundRadiusRepairTool.soundSectionOffset(encoded)
        // count (2), options (1), packed id<<8 | loops<<5 | radius (3)
        val packed = ((encoded[at + 3].toInt() and 0xFF) shl 16) or ((encoded[at + 4].toInt() and 0xFF) shl 8) or (encoded[at + 5].toInt() and 0xFF)
        assertEquals(123, packed ushr 8)
        assertEquals(1, (packed ushr 5) and 7)
        assertEquals(15, packed and 0x1F, "a radius-0 sound is heard only by the animating player")
        assertEquals(31, OsrsFxImportTool.packedRadius(40), "5-bit field")
    }

    @Test
    fun `A1 - the repair rewrites only the radius bits of an already imported sequence`() {
        val old = OsrsFxImportTool.encode667Seq(OsrsFxImportTool.decodeOsrsSeq(osrsSeqWithSound(0)))
        val patched = SeqSoundRadiusRepairTool.patch(old, mapOf(0 to 10))!!
        val fixed = OsrsFxImportTool.encode667Seq(OsrsFxImportTool.decodeOsrsSeq(osrsSeqWithSound(10)))
        assertContentEquals(fixed, patched)
        assertNull(SeqSoundRadiusRepairTool.patch(patched, mapOf(0 to 10)), "idempotent")
        assertContentEquals(OsrsFxImportTool.decode667SeqFrames(old), OsrsFxImportTool.decode667SeqFrames(patched))
    }

    @Test
    fun `C4 - idle turns are appended before the terminator and never twice`() {
        val bas = byteArrayOf(1, 0x3C, 0x0F, 0x3C, 0x10, 0)
        val out = BasIdleTurnRepairTool.withIdleTurns(bas, 15000, 15001)
        assertContentEquals(byteArrayOf(1, 0x3C, 0x0F, 0x3C, 0x10, 38, 0x3A, 0x98.toByte(), 39, 0x3A, 0x99.toByte(), 0), out)
        assertContentEquals(out, BasIdleTurnRepairTool.withIdleTurns(out, 15000, 15001))
    }

    @Test
    fun `C1-C3 - every imported breach npc sequence moves its model in both caches`() {
        // Stand/walk and combat sequences of the breach roster (BreachMonsters: 14438..14477).
        for (cache in OsrsItemImportTool.TARGETS) {
            val library = CacheLibrary(cache)
            try {
                val definitions = gg.rsmod.game.fs.DefinitionSet()
                definitions.load(library, gg.rsmod.game.fs.def.NpcDef::class.java)
                definitions.load(library, gg.rsmod.game.fs.def.BasDef::class.java)
                val failures = mutableListOf<String>()
                for (npc in 14438..14477) {
                    val labels = AnimCoverage.modelLabels(library, AnimCoverage.npcModels(library, npc))
                    AnimCoverage.basSeqs(definitions, npc).filter { it >= 0 }.forEach { seq ->
                        val r = AnimCoverage.check(library, labels, seq)
                        if (!r.ok) failures += "npc $npc $r"
                    }
                }
                assertTrue(failures.isEmpty(), "$cache:\n" + failures.joinToString("\n"))
            } finally {
                library.close()
            }
        }
    }

    @Test
    fun `E - the breach skeletal animations bake into classic frames that replay the OSRS skinning`() {
        ModernCacheReader(java.io.File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            // Breach spiral (OSRS model 49487, seq 10418) and Boss Spawn (49488, seq 10423).
            for ((model, seq) in listOf(49487 to 10418, 49488 to 10423)) {
                val baked = SkeletalBakeTool.bake(reader, model, seq, 1)
                assertTrue(baked.frames.isNotEmpty() && baked.clusters <= SkeletalBakeTool.MAX_CLUSTERS)
                assertTrue(baked.maxError < 3.0, "model $model replay error ${baked.maxError}")
            }
        }
        assertEquals(0, SkeletalBakeTool.In(byteArrayOf(64)).smart2(), "bone 0 is written as signed smart 64")
    }

    @Test
    fun `E - the baked breach loc, boss spawn and spawn graphics move their models in both caches`() {
        for (cache in OsrsItemImportTool.TARGETS) {
            val library = CacheLibrary(cache)
            try {
                listOf(3993 to 15828, 3994 to 15829, 3993 to 15830, 3993 to 15831).forEach { (model, seq) ->
                    val r = AnimCoverage.check(library, AnimCoverage.modelLabels(library, listOf(model)), seq)
                    assertTrue(r.ok && r.moves, "$cache model $model seq $seq: $r")
                }
                assertEquals(3993 to 15830, AnimCoverage.spotanim(library, 3173))
                assertEquals(3993 to 15831, AnimCoverage.spotanim(library, 3174))
                assertEquals(3994 to 15832, AnimCoverage.spotanim(library, 3175))
                assertEquals(3994 to 15833, AnimCoverage.spotanim(library, 3176))
            } finally {
                library.close()
            }
        }
    }
}