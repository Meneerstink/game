package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import io.netty.buffer.Unpooled

/**
 * Read-only recovery of the sounds a **sequence** carries.
 *
 * ## Why this tool exists
 *
 * Previous runs concluded that familiars are silent in this cache, on the evidence that 77 of the
 * 78 familiar npc definitions report `readySound/walkSound/runSound/crawlSound = -1` (NPCType
 * opcode 134). That is true, and it is not the whole picture: in this revision a sound can also be
 * attached to an **animation frame** rather than to the npc.
 *
 * `SeqType` opcode 13 carries `soundInfo`, one entry per frame. Entry 0 of a frame is a 3-byte
 * packed value and the rest are alternatives picked at random:
 *
 * ```
 * soundId = soundInfo[frame][0] >> 8
 * loops   = (soundInfo[frame][0] >> 5) & 0x7
 * soundInfo[frame][1..] = alternative 2-byte sound ids
 * ```
 *
 * decoded exactly that way by `SoundManager.method4577`, which then routes to `playVorbisSound`
 * when `SeqType` opcode 18 set `vorbisSound`, and to `playSynthSound` otherwise.
 *
 * So a familiar's real audio is reachable by taking the sequences it actually plays - its idle and
 * walk from `BASType` (see [BasTypeProbeTool]), and its attack, block and death from the server's
 * own combat definitions - and reading their frame sounds.
 *
 * Everything here is ported from the revision 667 client in `C:\RSPS\2011scape-client`
 * (`com/jagex/game/runetek6/config/seqtype/SeqType.java` and `SoundManager.java`). No id is
 * guessed; every opcode is consumed at its exact real width, because a wrong width would
 * desynchronise the stream and turn the following bytes into plausible-looking fictional sounds.
 *
 * ## Usage
 *
 * ```
 * ./gradlew :game:runSeqSoundProbeTool --args="<cachePath> <seqId> [seqId ...]"
 * ```
 */
object SeqSoundProbeTool {
    /** Sequences are their own modern, paged index: group = id ushr 7, file = id and 0x7f. */
    private const val INDEX_SEQ = 20

    data class FrameSound(val frame: Int, val soundId: Int, val loops: Int, val alternatives: List<Int>)

    data class Seq(
        val id: Int,
        val frameCount: Int,
        val vorbis: Boolean,
        val sounds: List<FrameSound>,
    ) {
        override fun toString(): String =
            if (sounds.isEmpty()) {
                "SEQ_$id frames=$frameCount SILENT"
            } else {
                "SEQ_$id frames=$frameCount vorbis=$vorbis " +
                    sounds.joinToString(" ") { s ->
                        "frame${s.frame}=sound${s.soundId}(loops=${s.loops}" +
                            (if (s.alternatives.isEmpty()) "" else ",alt=${s.alternatives}") + ")"
                    }
            }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <cachePath> <seqId> [seqId ...]" }
        val library = CacheLibrary(args[0])
        try {
            args.drop(1).map { it.toInt() }.forEach { println(seq(library, it) ?: "SEQ_$it ABSENT") }
        } finally {
            library.close()
        }
    }

    fun seq(
        library: CacheLibrary,
        seqId: Int,
    ): Seq? {
        val data = library.data(INDEX_SEQ, seqId ushr 7, seqId and 0x7f) ?: return null
        val buf = Unpooled.wrappedBuffer(data)
        var frameCount = 0
        var vorbis = false
        val sounds = mutableListOf<FrameSound>()
        while (buf.isReadable) {
            when (val code = buf.readUnsignedByte().toInt()) {
                0 -> return Seq(seqId, frameCount, vorbis, sounds)
                1 -> {
                    val count = buf.readUnsignedShort()
                    frameCount = count
                    // frameDurations, then frames (low short), then frames (high short).
                    buf.skipBytes(count * 2 * 3)
                }
                2 -> buf.skipBytes(2)
                3 -> buf.skipBytes(buf.readUnsignedByte().toInt())
                5, 8, 9, 10, 11 -> buf.skipBytes(1)
                6, 7 -> buf.skipBytes(2)
                12 -> buf.skipBytes(buf.readUnsignedByte().toInt() * 2 * 2)
                13 -> {
                    val count = buf.readUnsignedShort()
                    for (frame in 0 until count) {
                        val options = buf.readUnsignedByte().toInt()
                        if (options <= 0) continue
                        val packed = buf.readUnsignedMedium()
                        val alternatives = (1 until options).map { buf.readUnsignedShort() }
                        val soundId = packed shr 8
                        if (soundId != 0) {
                            sounds += FrameSound(frame, soundId, (packed shr 5) and 0x7, alternatives)
                        }
                    }
                }
                14, 15, 16 -> Unit
                18 -> vorbis = true
                19 -> buf.skipBytes(2)
                20 -> buf.skipBytes(5)
                else -> {
                    println("SEQ_$seqId UNHANDLED_OPCODE=$code (stopped, fields so far are still valid)")
                    return Seq(seqId, frameCount, vorbis, sounds)
                }
            }
        }
        return Seq(seqId, frameCount, vorbis, sounds)
    }
}
