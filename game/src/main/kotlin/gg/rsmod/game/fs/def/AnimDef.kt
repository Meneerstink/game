package gg.rsmod.game.fs.def

import gg.rsmod.game.fs.Definition
import io.netty.buffer.ByteBuf

/**
 * @author Tom <rspsmods@gmail.com>
 */
class AnimDef(
    id: Int,
) : Definition(id) {
    private lateinit var frameIds: IntArray
    private lateinit var frameLengths: IntArray
    private var priority = -1

    private var lengthInCycles = 0

    val cycleLength: Int get() = lengthInCycles

    /**
     * True when any frame of this sequence plays a sound (opcode 13, `SeqType.soundInfo`). The client plays those itself, so
     * a server cue for the same action would be heard twice (owner 2026-09-18 duplicate-audio audit).
     */
    var hasFrameSounds = false
        private set

    /**
     * True when a frame sound carries a radius (`soundInfo & 0x1F` > 0): the client then plays it as an area sound that every
     * nearby player hears. A radius-0 frame sound is played only for the local player's own animation, so on an npc it is
     * silent and a server cue must still be sent (breach audit 2026-09-27, A1/A3).
     */
    var hasAudibleFrameSounds = false
        private set

    /**
     * Sound id of the first audible frame sound, -1 without one. Deadman audit 2026-09-28: when an npc's block animation is skipped
     * (an attack animation still protects it) its hit sound - which lives on those frames - is sent by NpcCombatAudio instead.
     */
    var firstAudibleFrameSound = -1
        private set

    /** Sound id of the first frame sound of any radius, -1 without one (radius 0: only the animating local player hears it). */
    var firstFrameSound = -1
        private set

    /** Frame ids (`frameset archive << 16 | frame file`); a frame file's bytes 1-2 name its skeleton (AnimBase). */
    val frames: IntArray get() = if (::frameIds.isInitialized) frameIds else IntArray(0)
    val frameLength: Int get() = if (::frameLengths.isInitialized) frameLengths.sum() else 0

    /**
     * The 667 client's SeqType loop fields (owner priority #1 2026-10-03, noclip / stuck after obstacles). Opcode 2 `loopOffset`:
     * when the last frame ends the client jumps back this many frames and plays on, up to [maxLoops] (opcode 8, default 99)
     * times - a climb such as 740 (16 frames, loopOffset 16) therefore plays ~96 s on the client while [cycleLength] says 2
     * ticks. Opcode 10 `walkingPrecedence` (default 0 without blend flags): at 0 the client does not draw the body along its
     * path while the action animation plays, at 1 a movement cancels the animation, at 2 both run.
     */
    var loopOffset = -1
        private set
    var maxLoops = 99
        private set
    private var hasBlendFlags = false
    private var walkingPrecedenceRaw = -1
    private var animatingPrecedenceRaw = -1

    /** `SeqType.postDecode`: 0 without blend flags (opcode 3), else 2, unless opcode 10 set it. */
    val walkingPrecedence: Int get() = if (walkingPrecedenceRaw != -1) walkingPrecedenceRaw else if (hasBlendFlags) 2 else 0

    val animatingPrecedence: Int get() = if (animatingPrecedenceRaw != -1) animatingPrecedenceRaw else if (hasBlendFlags) 2 else 0

    /** True when the client keeps replaying this sequence after its frames ran out (loopOffset set and more than one loop). */
    val loops: Boolean get() = loopOffset > 0 && maxLoops > 1

    /**
     * How long the client plays this sequence once started, in game cycles, loops included: the server-side [cycleLength] counts
     * one pass only. Null when the sequence loops for good (a looping idle).
     */
    val clientCycleLength: Int
        get() {
            if (!loops || !::frameLengths.isInitialized) return cycleLength
            val count = frameLengths.size
            val loopStart = (count - loopOffset).coerceIn(0, count)
            var total = 0
            for (i in 0 until count) total += frameLengths[i]
            var loopPass = 0
            for (i in loopStart until count) loopPass += frameLengths[i]
            // The client counts the first pass, then maxLoops - 1 further passes over the loop section before `finished`.
            val clientTicks = total + loopPass * (maxLoops - 1)
            return Math.ceil(clientTicks * 20.0 / 600.0).toInt()
        }

    /** True when the client holds the body still while this sequence plays (walking precedence 0, `Static256.movementTick`). */
    val holdsBody: Boolean get() = walkingPrecedence == 0

    override fun decode(
        buf: ByteBuf,
        opcode: Int,
    ) {
        when (opcode) {
            1 -> {
                val frameCount = buf.readUnsignedShort()
                var totalFrameLength = 0
                frameIds = IntArray(frameCount)
                frameLengths = IntArray(frameCount)

                for (i in 0 until frameCount) {
                    frameLengths[i] = buf.readUnsignedShort()

                    /**
                     * The last frame on some animations, such as death animations,
                     * are set to be extremely long in length. We don't want to take
                     * that into account when converting to game cycles, so we
                     * only add frame lengths that are below a certain threshold
                     * or if the frame <strong>isn't</strong> the last frame on the
                     * animation.
                     */
                    if (i < frameCount - 1 || frameLengths[i] < 200) {
                        totalFrameLength += frameLengths[i]
                    }
                }

                for (i in 0 until frameCount) {
                    frameIds[i] = buf.readUnsignedShort()
                }

                for (i in 0 until frameCount) {
                    frameIds[i] += buf.readUnsignedShort() shl 16
                }

                /**
                 * Convert the length of the combined frames into game cycles
                 * (assuming a game cycle is 600ms).
                 */
                lengthInCycles = Math.ceil((totalFrameLength * 20.0) / 600.0).toInt()
            }
            2 -> loopOffset = buf.readUnsignedShort()
            3 -> {
                hasBlendFlags = true
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedByte()
                }
            }
            4 -> {}
            // Opcode 5 is the client's `priority`; the old decoder read opcode 10 (walking precedence) as the priority.
            5 -> priority = buf.readUnsignedByte().toInt()
            6 -> buf.readUnsignedShort()
            7 -> buf.readUnsignedShort()
            8 -> maxLoops = buf.readUnsignedByte().toInt()
            9 -> animatingPrecedenceRaw = buf.readUnsignedByte().toInt()
            10 -> walkingPrecedenceRaw = buf.readUnsignedByte().toInt()
            11 -> buf.readUnsignedByte()
            12 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedShort()
                }
                for (i in 0 until count) {
                    buf.readUnsignedShort()
                }
            }
            13 -> {
                val length = buf.readShort().toInt()
                repeat(length) {
                    val size = buf.readUnsignedByte().toInt()
                    if (size > 0) {
                        hasFrameSounds = true
                        // soundInfo: id << 8 | loops << 5 | radius (rev-667 SeqType; tools/cache667/js5.py decodes the same)
                        val info = buf.readMedium()
                        if (firstFrameSound < 0) firstFrameSound = info ushr 8
                        if (info and 0x1F > 0) {
                            hasAudibleFrameSounds = true
                            if (firstAudibleFrameSound < 0) firstAudibleFrameSound = info ushr 8
                        }
                        for (index in 1 until size) {
                            buf.readShort()
                        }
                    }
                }
            }
            14 -> {}
            15 -> {}
            16 -> {}
            17 -> buf.readUnsignedByte()
            18 -> {}
            19 -> {
                buf.readUnsignedByte()
                buf.readUnsignedByte()
            }
            20 -> {
                buf.readUnsignedByte()
                buf.readUnsignedShort()
                buf.readUnsignedShort()
            }
        }
    }
}
