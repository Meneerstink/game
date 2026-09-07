package gg.rsmod.game.fs.def

import gg.rsmod.game.fs.Definition
import gg.rsmod.util.io.BufferUtils.readString
import io.netty.buffer.ByteBuf

/**
 * @author Tom <rspsmods@gmail.com>
 */
class NpcDef(
    override val id: Int,
) : Definition(id) {
    var name = ""
    var size = 1
    /**
     * The npc's body-animation-set id ("render animation"), NPCType opcode 127. Not an animation
     * id: it keys BASType (config group 32), which is where the real ready/walk/run/crawl
     * sequences live. Decoded as `walkAnim` until 2026-09-07, which is why nothing could find a
     * familiar's idle animation - see FamiliarRenderProbeTool.
     */
    var basId = -1
    var render3 = -1
    var render4 = -1
    var render5 = -1
    var render6 = -1
    var render7 = -1
    var visibleMapDot = true
    var combatLevel = -1
    var width = -1
    var length = -1
    var render = false
    var headIcon = -1
    var varp = -1
    var varbit = -1
    var interactable = true
    var pet = false
    var options: Array<String?> = Array(5) { "" }
    var transforms: Array<Int>? = null
    var walkMask = -1

    var examine: String? = null

    /**
     * Ambient movement sound ids (opcode 134), -1 when none. Purely diagnostic on the server -
     * the real client (`NPCEntity.currentSound()` in `2011scape-client`) reads these fields
     * directly out of its own local copy of this same definition and autonomously plays the
     * matching sound based on the npc's current animation state; the server sends no packet for
     * this and never needs to. Stored (rather than discarded, as this decoder used to do) so
     * `NpcDefProbeTool` can report whether a given npc's cache definition actually carries sound
     * data at all, which is the real question for any "npc X is silent" report.
     */
    var readySound = -1
    var walkSound = -1
    var runSound = -1
    var crawlSound = -1

    fun isAttackable(): Boolean = options.any { it == "Attack" }

    override fun decode(
        buf: ByteBuf,
        opcode: Int,
    ) {
        when (opcode) {
            1 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedShort()
                }
            }
            2 -> name = buf.readString()
            12 -> size = buf.readUnsignedByte().toInt()
            in 30 until 35 -> {
                options[opcode - 30] = buf.readString()
                if (options[opcode - 30]?.lowercase() == "null") {
                    options[opcode - 30] = null
                }
            }
            40 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedShort()
                    buf.readUnsignedShort()
                }
            }
            41 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedShort()
                    buf.readUnsignedShort()
                }
            }
            42 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readByte()
                }
            }
            60, 160 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedShort()
                }
            }
            93 -> visibleMapDot = false
            95 -> combatLevel = buf.readUnsignedShort()
            97 -> width = buf.readUnsignedShort()
            98 -> headIcon = buf.readUnsignedShort()
            99 -> render = true
            100 -> buf.readByte()
            101 -> buf.readByte()
            102 -> headIcon = buf.readUnsignedShort()
            103 -> buf.readUnsignedShort()
            106, 118 -> {
                varbit = buf.readUnsignedShort()
                varp = buf.readUnsignedShort()

                if (varbit == 65535) {
                    varbit = -1
                }
                if (varp == 65535) {
                    varp = -1
                }

                if (opcode == 118) {
                    buf.readUnsignedShort()
                }

                val count = buf.readUnsignedByte()

                transforms = Array(count.toInt() + 1) { 0 }
                for (i in 0..count) {
                    val transform = buf.readUnsignedShort()
                    transforms!![i] = if (transform == 65535) -1 else transform
                }
            }
            107 -> interactable = false
            113 -> {
                buf.readUnsignedShort()
                buf.readUnsignedShort()
            }
            114 -> {
                buf.readByte()
                buf.readByte()
            }
            115 -> {
                buf.readByte()
                buf.readByte()
            }
            119 -> walkMask = buf.readByte().toInt()
            121 -> {
                val length = buf.readUnsignedByte().toInt()
                repeat(length) {
                    buf.readUnsignedByte()
                    buf.readByte()
                    buf.readByte()
                }
            }
            122, 123, 137, 138, 139, 142 -> buf.readUnsignedShort()
            125, 128, 140, 163, 165, 168 -> buf.readByte()
            127 -> basId = buf.readUnsignedShort().let { if (it == 65535) -1 else it }
            134 -> {
                readySound = buf.readUnsignedShort().let { if (it == 65535) -1 else it }
                crawlSound = buf.readUnsignedShort().let { if (it == 65535) -1 else it }
                walkSound = buf.readUnsignedShort().let { if (it == 65535) -1 else it }
                runSound = buf.readUnsignedShort().let { if (it == 65535) -1 else it }
                buf.readUnsignedByte()
            }
            135, 136 -> {
                buf.readUnsignedByte()
                buf.readUnsignedShort()
            }
            in 150 until 155 -> {
                options[opcode - 150] = buf.readString()
                if (options[opcode - 150]?.lowercase() == "null") {
                    options[opcode - 150] = null
                }
            }
            155 -> {
                buf.readByte()
                buf.readByte()
                buf.readByte()
                buf.readByte()
            }
            158, 159, 162 -> {}
            164 -> {
                buf.readShort()
                buf.readShort()
            }
            249 -> readParams(buf)
        }
    }
}
