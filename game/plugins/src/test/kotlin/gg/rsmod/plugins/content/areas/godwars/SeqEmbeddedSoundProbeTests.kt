package gg.rsmod.plugins.content.areas.godwars

import com.displee.cache.CacheLibrary
import org.junit.Test
import java.nio.ByteBuffer

/**
 * RCV-010 revisit of B2 SOURCE_BLOCKED (Nex/Glacor silence) with a different source: the revision-667 animation
 * definitions themselves. Sequence opcode 13 carries per-frame sound effects that the client plays on its own when
 * the animation runs. This probe decodes opcode 13 for the seqs the Nex and Glacor scripts play, in the pristine 667
 * cache and the production cache.
 */
class SeqEmbeddedSoundProbeTests {
    // Nex (NexEncounter) then Glacor (GlacorCombatScript melee/ranged/magic).
    private val seqs = listOf(6355, 6987, 6986, 6984, 6948, 6321, 6951, 9955, 9968, 9967)

    @Test
    fun `probe embedded seq sounds`() {
        listOf("reference667" to "C:/RSPS/reference/openrs2_667/cache", "production" to "../../data/cache").forEach { (label, path) ->
            val library = CacheLibrary(path)
            (seqs + extra()).distinct().forEach { id ->
                val data = library.data(20, id ushr 7, id and 0x7F)
                if (data == null) {
                    println("SEQ_SOUND $label $id ABSENT")
                    return@forEach
                }
                println("SEQ_SOUND $label $id ${decodeSounds(data)}")
            }
            library.close()
        }
    }

    /** Positive control: the decoder must find embedded sounds in some 667 seqs, otherwise an empty result proves nothing. */
    @Test
    fun `control - embedded sounds exist in the 667 seq archive`() {
        val library = CacheLibrary("C:/RSPS/reference/openrs2_667/cache")
        var withSounds = 0
        var decoded = 0
        var errors = 0
        val examples = mutableListOf<String>()
        for (id in 0 until 20000) {
            val data = library.data(20, id ushr 7, id and 0x7F) ?: continue
            decoded++
            val result = decodeSounds(data)
            if (result.contains("ERR") || result.contains("UNKNOWN_OP")) errors++
            if (!result.contains("sounds=[]")) {
                withSounds++
                if (examples.size < 3) examples += "$id $result".take(200)
            }
        }
        library.close()
        println("SEQ_SOUND_CONTROL decoded=$decoded withSounds=$withSounds errors=$errors examples=$examples")
        kotlin.test.assertTrue(withSounds > 0, "decoder found no embedded sounds anywhere - probe result would be meaningless")
    }

    private fun extra(): List<Int> = System.getProperty("seq.extra")?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

    /** Mirrors `AnimDef` decode, keeping opcode 13's frame -> (sound id, extra) entries. */
    private fun decodeSounds(bytes: ByteArray): String {
        val buf = ByteBuffer.wrap(bytes)
        val sounds = mutableListOf<String>()
        var frames = 0
        fun u8() = buf.get().toInt() and 0xFF
        fun u16() = buf.short.toInt() and 0xFFFF
        fun u24() = (u8() shl 16) or (u8() shl 8) or u8()
        try {
            while (buf.hasRemaining()) {
                when (val op = u8()) {
                    0 -> return "frames=$frames sounds=$sounds"
                    1 -> {
                        frames = u16()
                        repeat(frames) { u16() }
                        repeat(frames) { u16() }
                        repeat(frames) { u16() }
                    }
                    2 -> u16()
                    3 -> repeat(u8()) { u8() }
                    4 -> {}
                    5, 8, 9, 10, 11, 17 -> u8()
                    6, 7 -> u16()
                    12 -> {
                        val n = u8()
                        repeat(n) { u16() }
                        repeat(n) { u16() }
                    }
                    13 -> {
                        val n = u16()
                        repeat(n) { frame ->
                            val size = u8()
                            if (size > 0) {
                                val packed = u24()
                                val rest = (1 until size).map { u16() }
                                // Client SoundManager: id = value >> 8, loops field = value >> 5 & 7; extras are alternative ids.
                                sounds += "f$frame:id=${packed shr 8} v=${packed shr 5 and 7} alt=$rest"
                            }
                        }
                    }
                    14, 15, 16, 18 -> {}
                    19 -> {
                        u8()
                        u8()
                    }
                    20 -> {
                        u8()
                        u16()
                        u16()
                    }
                    249 -> repeat(u8()) {
                        val string = u8() == 1
                        u24()
                        if (string) while (buf.get().toInt() != 0) {} else buf.int
                    }
                    else -> return "frames=$frames sounds=$sounds UNKNOWN_OP=$op"
                }
            }
        } catch (e: Exception) {
            return "frames=$frames sounds=$sounds DECODE_ERR=${e.javaClass.simpleName}"
        }
        return "frames=$frames sounds=$sounds"
    }
}
