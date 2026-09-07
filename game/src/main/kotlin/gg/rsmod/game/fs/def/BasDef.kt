package gg.rsmod.game.fs.def

import gg.rsmod.game.fs.Definition
import io.netty.buffer.ByteBuf

/**
 * One body animation set ("render animation"): the table that holds an npc's or player's real
 * idle, walk, run and crawl sequences.
 *
 * An npc does not carry its own animation ids. [NpcDef.basId] (NPCType opcode 127) points here,
 * and this type's opcode 1 carries [ready] and [walk] as a pair. That indirection is why several
 * earlier runs concluded a familiar's idle animation was unrecoverable: the server decoded opcode
 * 127 into a field named `walkAnim` and never followed it through.
 *
 * Ported field-for-field from the revision 667 client's
 * `com.jagex.game.runetek6.config.bastype.BASType` (`C:\RSPS\2011scape-client`). Only the fields
 * the server actually uses are kept; every other opcode is still consumed at its exact real width,
 * because a wrong width would desynchronise the stream and turn the following bytes into
 * plausible-looking but fictional animation ids.
 */
class BasDef(
    override val id: Int,
) : Definition(id) {
    /** The idle sequence. -1 when this set instead varies its idle through [readyAnimations]. */
    var ready = -1

    var walk = -1
    var run = -1
    var crawl = -1

    /**
     * A weighted pool of idle sequences, used instead of [ready] by sets that vary their idle
     * (opcode 52). Parallel to [readyAnimationWeights]; the client picks one at random by weight.
     */
    var readyAnimations: IntArray = IntArray(0)
    var readyAnimationWeights: IntArray = IntArray(0)

    /**
     * The idle sequence to render, resolving the [readyAnimations] pool to its first entry so a
     * caller that needs one deterministic id (the Follower Details panel, which renders a single
     * static pose) always has a real, sourced animation rather than -1.
     */
    fun idleAnimation(): Int =
        when {
            ready != -1 -> ready
            readyAnimations.isNotEmpty() -> readyAnimations[0]
            else -> -1
        }

    override fun decode(
        buf: ByteBuf,
        opcode: Int,
    ) {
        when (opcode) {
            1 -> {
                ready = buf.readUnsignedShort().let { if (it == 65535) -1 else it }
                walk = buf.readUnsignedShort().let { if (it == 65535) -1 else it }
            }
            2 -> crawl = buf.readUnsignedShort()
            6 -> run = buf.readUnsignedShort()
            3, 4, 5, 7, 8, 9 -> buf.skipBytes(2)
            26 -> buf.skipBytes(2)
            27 -> buf.skipBytes(1 + 12)
            28 -> buf.skipBytes(buf.readUnsignedByte().toInt())
            29, 31, 34, 37 -> buf.skipBytes(1)
            30, 32, 33, 35, 36, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51 ->
                buf.skipBytes(2)
            52 -> {
                val count = buf.readUnsignedByte().toInt()
                readyAnimations = IntArray(count)
                readyAnimationWeights = IntArray(count)
                for (i in 0 until count) {
                    readyAnimations[i] = buf.readUnsignedShort()
                    readyAnimationWeights[i] = buf.readUnsignedByte().toInt()
                }
            }
            53 -> Unit
            54 -> buf.skipBytes(2)
            55 -> buf.skipBytes(3)
            56 -> buf.skipBytes(1 + 6)
        }
    }
}
