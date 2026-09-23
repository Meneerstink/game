package gg.rsmod.game.fs.def

import gg.rsmod.game.fs.Definition
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.util.io.BufferUtils.getSmart
import gg.rsmod.util.io.BufferUtils.readString
import io.netty.buffer.ByteBuf

/**
 * @author Tom <rspsmods@gmail.com>
 */
class ObjectDef(
    override val id: Int,
) : Definition(id) {
    var name = ""
    var width = 1
    var length = 1
    /**
     * The 667 client's LocType.blockwalk: 2 by default, 0 after opcode 17, 1 after opcode 27. Ground decoration only blocks at 1,
     * every other shape at non-zero (MapRegion loc loading).
     */
    var blockwalk = 2

    /** LocType.blockrange: cleared by opcodes 17 and 18. */
    var blockrange = true

    /**
     * Opcode 74. The 667 client's LocTypeList.list clears blockwalk and blockrange for such a loc, but only so its own
     * route finder may plan a path *to* it; movement is server-authoritative. Revision 667 sets it on 671 locs whose
     * placements are almost all real obstacles - 4161 doors, every Dungeoneering door, 125 bank booths, counters,
     * tables, gates and ~1200 wall pieces - so it must never make a loc walkable on the server.
     */
    var breakroutefinding = false

    /**
     * Blocks walking. Owner 2026-09-24 ("noclippen lukt in mijn hele server"): this used to be
     * `blockwalk != 0 && !breakroutefinding`, which made every opcode-74 door, booth, counter, gate and wall piece
     * walk-through world-wide. Only [blockwalk] decides now; ground decoration keeps its own rule ([blocksGroundDecor],
     * so walk-on rope bridges stay walkable).
     */
    var solid: Boolean
        get() = blockwalk != 0
        set(value) {
            blockwalk = if (value) 2 else 0
        }

    val impenetrable: Boolean get() = blockrange && !breakroutefinding

    /** The client's ground decoration rule: only blockwalk 1 adds the GROUND_DECOR flag. */
    val blocksGroundDecor: Boolean get() = blockwalk == 1 && !breakroutefinding
    var interactive = false
    var obstructive = false
    var clipMask = 0
    var varbit = -1
    var varp = -1
    var animation = -1
    var rotated = false
    val options: Array<String?> = Array(5) { "" }
    var transforms: Array<Int>? = null

    var examine: String? = null

    var depleted: Int = -1

    fun getRotatedWidth(obj: GameObject): Int =
        when {
            (obj.rot and 0x1) == 1 -> length
            else -> width
        }

    fun getRotatedLength(obj: GameObject): Int =
        when {
            (obj.rot and 0x1) == 1 -> width
            else -> length
        }

    override fun decode(
        buf: ByteBuf,
        opcode: Int,
    ) {
        when (opcode) {
            1, 5 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readByte()
                    val secondCount = buf.readUnsignedByte()
                    for (j in 0 until secondCount) {
                        buf.readUnsignedShort()
                    }
                }
                if (opcode == 5) {
                    skipReadModelIds(buf)
                }
            }
            2 -> name = buf.readString()
            14 -> width = buf.readUnsignedByte().toInt()
            15 -> length = buf.readUnsignedByte().toInt()
            17 -> {
                blockwalk = 0
                blockrange = false
            }
            18 -> blockrange = false
            19 -> interactive = buf.readUnsignedByte().toInt() == 1
            21 -> {}
            22 -> {}
            23 -> {}
            24 -> {
                animation = buf.readUnsignedShort()
                if (animation == 65535) {
                    animation = -1
                }
            }
            27 -> blockwalk = 1
            28 -> buf.readUnsignedByte()
            29 -> buf.readByte()
            in 30 until 35 -> {
                options[opcode - 30] = buf.readString()
                if (options[opcode - 30]?.lowercase() == "null") {
                    options[opcode - 30] = null
                }
            }
            39 -> buf.readByte()
            40 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedShort() // Recolor src
                    buf.readUnsignedShort() // Recolor dst
                }
            }
            41 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedShort() // Retexture src
                    buf.readUnsignedShort() // Retexture dst
                }
            }
            42 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readByte()
                }
            }
            62 -> rotated = true
            64 -> {}
            65 -> buf.readUnsignedShort()
            66 -> buf.readUnsignedShort()
            67 -> buf.readUnsignedShort()
            69 -> clipMask = buf.readUnsignedByte().toInt()
            70 -> buf.readShort()
            71 -> buf.readShort()
            72 -> buf.readShort()
            74 -> breakroutefinding = true
            73 -> obstructive = true
            75 -> buf.readUnsignedByte()
            77, 92 -> {
                varbit = buf.readUnsignedShort()
                varp = buf.readUnsignedShort()

                if (varbit == 65535) {
                    varbit = -1
                }
                if (varp == 65535) {
                    varp = -1
                }

                if (opcode == 92) {
                    buf.readUnsignedShort()
                }

                val count = buf.readUnsignedByte().toInt()

                transforms = Array(count + 1) { 0 }
                for (i in 0..count) {
                    val transform = buf.readUnsignedShort()
                    transforms!![i] = if (transform == 65535) -1 else transform
                }
            }
            78 -> {
                buf.readUnsignedShort()
                buf.readUnsignedByte()
            }
            79 -> {
                buf.readUnsignedShort()
                buf.readUnsignedShort()
                buf.readUnsignedByte()
                val count = buf.readUnsignedByte().toInt()
                for (i in 0 until count) {
                    buf.readUnsignedShort()
                }
            }
            81 -> buf.readUnsignedByte()
            82 -> {}
            88 -> {}
            89 -> {}
            90 -> {}
            91 -> {}
            93 -> buf.readUnsignedShort()
            94 -> {}
            95 -> buf.readShort()
            96 -> {}
            97 -> {}
            98 -> {}
            99 -> {
                buf.readUnsignedByte()
                buf.readUnsignedShort()
            }
            100 -> {
                buf.readUnsignedByte()
                buf.readUnsignedShort()
            }
            101 -> buf.readUnsignedByte()
            102 -> buf.readUnsignedShort()
            103 -> {}
            104 -> buf.readUnsignedByte()
            105 -> {}
            106 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedShort()
                    buf.readUnsignedByte()
                }
            }
            107 -> buf.readUnsignedShort()
            in 150 until 155 -> {
                options[opcode - 150] = buf.readString()
                if (options[opcode - 150]?.lowercase() == "null") {
                    options[opcode - 150] = null
                }
            }
            160 -> {
                val count = buf.readUnsignedByte()
                for (i in 0 until count) {
                    buf.readUnsignedShort()
                }
            }
            162 -> buf.readByte()
            163 -> {
                buf.readByte()
                buf.readByte()
                buf.readByte()
                buf.readByte()
            }
            164 -> buf.readShort()
            165 -> buf.readShort()
            166 -> buf.readShort()
            167 -> buf.readUnsignedShort()
            168 -> {}
            169 -> {}
            170 -> getSmart(buf)
            171 -> getSmart(buf)
            173 -> {
                buf.readUnsignedShort()
                buf.readUnsignedShort()
            }
            177 -> {}
            178 -> buf.readUnsignedByte()
            189 -> {}
            249 -> readParams(buf)
        }
    }

    private fun skipReadModelIds(buf: ByteBuf) {
        val length = buf.readByte().toInt() and 0xFF
        for (index in 0 until length) {
            buf.readByte()
            val length2 = buf.readByte().toInt() and 0xFF
            for (i in 0 until length2) {
                buf.readShort()
            }
        }
    }
}
