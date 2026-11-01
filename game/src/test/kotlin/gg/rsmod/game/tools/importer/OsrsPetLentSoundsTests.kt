package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N88 part 2 (owner 2026-10-17 "OSRS PETS DO NOT MAKE ANY SOUND", Council N88 point 2): every follower pet whose Jagex twin or parent
 * creature walks with OSRS frame sounds carries those sounds on its own movement sequence, in both caches.
 */
class OsrsPetLentSoundsTests {
    @Test
    fun `a lent sound lands on the frame showing the same moment of the cycle, two never share a frame`() {
        // Source 100 cycles: sounds at 0, 50 and 51; target 4 frames of 10 cycles (40 cycles).
        val events = listOf(intArrayOf(50, 7, 1, 15), intArrayOf(0, 6, 1, 15), intArrayOf(51, 8, 1, 15), intArrayOf(10, 9, 0, 15))
        val placed = OsrsPetImportTool.lentFrames(events, 100, intArrayOf(10, 10, 10, 10)).map { it.first[1] to it.second }
        assertEquals(listOf(6 to 0, 7 to 2, 8 to 3), placed, "loops 0 is no sound; the second sound on frame 2 moves to frame 3")
    }

    @Test
    fun `the sound block decodes as a 667 sequence and keeps the frames`() {
        val bytes = byteArrayOf(1, 0, 2, 0, 5, 0, 5, 0, 1, 0, 2, 0, 9, 0, 9, 0)
        val seq = OsrsPetImportTool.read667Seq(bytes)
        val out = OsrsPetImportTool.with667Sounds(bytes, seq, mapOf(1 to ((300 shl 8) or (1 shl 5) or 15)))
        assertEquals(OsrsFxImportTool.decode667SeqFrames(bytes).toList(), OsrsFxImportTool.decode667SeqFrames(out).toList())
        assertTrue(OsrsPetImportTool.read667Seq(out).hasSounds)
    }

    @Test
    fun `every pet with a sounding twin or parent walks with those sounds in both caches`() {
        val fx = OsrsFxImportTool.existingFx(File(OsrsItemImportTool.ASSET_MAP))
        val rows = OsrsPetRoster.load().associateBy { it.osrsFollower }
        val silent = mutableListOf<String>()
        var checked = 0
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val npcFiles = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
            for (cache in OsrsItemImportTool.TARGETS) {
                val library = CacheLibrary(cache)
                try {
                    for ((targetNpc, sourceNpc) in OsrsPetImportTool.LENT_SOUNDS) {
                        val row = rows.getValue(targetNpc)
                        val target = OsrsNpcProbeTool.decodeOsrs(targetNpc, npcFiles.getValue(targetNpc))
                        val source = OsrsNpcProbeTool.decodeOsrs(sourceNpc, npcFiles.getValue(sourceNpc))
                        val targetSeqs = OsrsNpcProbeTool.movementSeqs(target)
                        val baked = if (row.skeletal) OsrsPetImportTool.bakedSeqs(target) else emptyList()
                        var sounding = 0
                        for ((move, sourceSeq) in OsrsNpcProbeTool.movementSeqs(source)) {
                            val targetSeq = targetSeqs[move] ?: continue
                            if (OsrsPetImportTool.osrsSeqSounds(reader, sourceSeq).first.isEmpty()) continue
                            val key = if (row.skeletal) "pet_baked_seq:${targetNpc * 16 + baked.indexOf(targetSeq)}" else "seq:$targetSeq"
                            val local = fx.getValue(key)
                            val bytes = library.data(OsrsFxImportTool.INDEX_SEQ, local ushr 7, local and 0x7F)!!
                            if (!OsrsPetImportTool.read667Seq(bytes).hasSounds) silent += "${row.variant} ($targetNpc) $move seq $local in $cache"
                            sounding++
                        }
                        assertTrue(sounding > 0, "${row.variant}: source $sourceNpc lends no sound on a movement the pet has")
                        checked++
                    }
                } finally {
                    library.close()
                }
            }
        }
        assertEquals(OsrsPetImportTool.LENT_SOUNDS.size * 2, checked)
        assertTrue(silent.isEmpty(), "silent pet movement sequences: $silent")
    }
    @Test
    fun `N88 part 3 - Kree'arra Jr, the dark core and the Corporeal Critter move without sounds again, as Jagex left them`() {
        assertTrue(OsrsPetImportTool.JAGEX_SILENCED.none { it in OsrsPetImportTool.LENT_SOUNDS }, "a silenced pet still lends sounds")
        val fx = OsrsFxImportTool.existingFx(File(OsrsItemImportTool.ASSET_MAP))
        val rows = OsrsPetRoster.load().associateBy { it.osrsFollower }
        val sounding = mutableListOf<String>()
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val npcFiles = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
            for (cache in OsrsItemImportTool.TARGETS) {
                val library = CacheLibrary(cache)
                try {
                    for (npc in OsrsPetImportTool.JAGEX_SILENCED) {
                        val row = rows.getValue(npc)
                        val target = OsrsNpcProbeTool.decodeOsrs(npc, npcFiles.getValue(npc))
                        val baked = if (row.skeletal) OsrsPetImportTool.bakedSeqs(target) else emptyList()
                        for ((move, seq) in OsrsNpcProbeTool.movementSeqs(target)) {
                            val key = if (row.skeletal) "pet_baked_seq:${npc * 16 + baked.indexOf(seq)}" else "seq:$seq"
                            val local = fx[key] ?: continue
                            val bytes = library.data(OsrsFxImportTool.INDEX_SEQ, local ushr 7, local and 0x7F) ?: continue
                            if (OsrsPetImportTool.read667Seq(bytes).hasSounds) sounding += "${row.variant} $move seq $local in $cache"
                        }
                    }
                } finally {
                    library.close()
                }
            }
        }
        assertTrue(sounding.isEmpty(), "Jagex-silenced pets still sound: $sounding")
    }

    @Test
    fun `N88 part 3 - every pet has its voice, every voice in both caches`() {
        // N480: the owner's later decision (rsps 'every OSRS pet needs sound', sourced per pet) replaced decision 2's eight silent
        // pets (Jal-Nib-Rek, Mr McGroot, Pet Kraken, Phoenix, Soup, Tangleroot, Youngllef, Bloodhound) and the silent Olmlet forms.
        val voices = OsrsPetImportTool.VOICES.filter { it.line.isEmpty() }
        val missing = mutableListOf<String>()
        OsrsPetRoster.load().forEach { row ->
            val voice = voices.firstOrNull { it.pet == row.pet && it.variant == row.variant } ?: voices.firstOrNull { it.pet == row.pet && it.variant == "*" }
            if (voice == null) missing += "${row.pet} / ${row.variant}"
        }
        // The Bloodhound barks with the 667 dog bark like every dog pet (N296, plugins PetVoices.DOGS / Sfx.PET_DOG_BARK), not through
        // this OSRS import table; any other pet without a voice fails here.
        assertEquals(listOf("Bloodhound / Bloodhound"), missing, "pets without a voice")
        val fx = OsrsFxImportTool.existingFx(File(OsrsItemImportTool.ASSET_MAP))
        val table = File(OsrsPetImportTool.VOICE_TABLE).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('	') }
        assertEquals(OsrsPetImportTool.VOICES.size, table.size, "pet_voices.tsv is the generated table of VOICES")
        for (cache in OsrsItemImportTool.TARGETS) {
            val library = CacheLibrary(cache)
            try {
                OsrsPetImportTool.VOICES.forEach { v ->
                    val local = if (v.osrsSynth >= 0) fx.getValue("synth:${v.osrsSynth}") else v.local667
                    assertTrue(table.any { it[0] == v.pet && it[1] == v.variant && it[3].toInt() == local }, "${v.pet} ${v.variant}: table row with synth $local")
                    assertTrue(library.data(OsrsFxImportTool.INDEX_SYNTH, local, 0) != null, "${v.pet}: synth $local missing in $cache")
                }
            } finally {
                library.close()
            }
        }
    }
}
