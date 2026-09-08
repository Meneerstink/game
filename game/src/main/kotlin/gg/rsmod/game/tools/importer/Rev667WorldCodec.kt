package gg.rsmod.game.tools.importer

import java.io.ByteArrayOutputStream

/**
 * Revision-667 world formats, transcribed from the deobfuscated `2011scape-client`
 * (`Class306.decodeMapSquare`/`decodeTile`, `MapRegion.loadLocations`, `loctype/LocType.java`,
 * `flutype/FloorUnderlayType.java`, `flotype/FloorOverlayType.java`). Read widths are the
 * client's; every decoder hard-fails on an unknown opcode so a format drift is loud.
 *
 * Tile group (`m<rx>_<rz>`, archive 5, unencrypted): for level 0..3, x 0..63, z 0..63 one record:
 *   0 end (no height byte), 1 + u8 height then end, 2..49 overlay (i8 id, shape=(code-2)/4,
 *   rotation=(code-2)&3), 50..81 flags=code-49, >=82 underlay=code-81. Trailing bytes after the
 *   4*64*64 records are optional environment records the client reads until EOF.
 * Loc group (`l<rx>_<rz>`, archive 5, XTEA; an all-zero key means plain): extended-smart id delta,
 *   then smart position deltas (+1 biased), packed byte shape<<2|rotation. Identical to modern.
 */
class Rev667Tile {
    /** -1 = no explicit height byte (client derives it). */
    var height = -1
    var overlayId = 0
    var overlayShape = 0
    var overlayRotation = 0
    var flags = 0
    var underlayId = 0

    fun copyFrom(o: Rev667Tile) {
        height = o.height
        overlayId = o.overlayId
        overlayShape = o.overlayShape
        overlayRotation = o.overlayRotation
        flags = o.flags
        underlayId = o.underlayId
    }
}

class Rev667TileMap(
    val tiles: Array<Array<Array<Rev667Tile>>> = Array(4) { Array(64) { Array(64) { Rev667Tile() } } },
    var trailing: ByteArray = ByteArray(0),
)

object Rev667TileCodec {
    fun decode(data: ByteArray): Rev667TileMap {
        val buf = RegionBuffer(data)
        val map = Rev667TileMap()
        for (level in 0 until 4) for (x in 0 until 64) for (z in 0 until 64) {
            val t = map.tiles[level][x][z]
            while (true) {
                val code = buf.u8()
                if (code == 0) break
                if (code == 1) {
                    t.height = buf.u8()
                    break
                }
                if (code <= 49) {
                    t.overlayId = buf.i8()
                    t.overlayShape = (code - 2) / 4
                    t.overlayRotation = (code - 2) and 3
                } else if (code <= 81) {
                    t.flags = code - 49
                } else {
                    t.underlayId = code - 81
                }
            }
        }
        map.trailing = data.copyOfRange(buf.position, data.size)
        return map
    }

    fun encode(map: Rev667TileMap): ByteArray {
        val out = ByteArrayOutputStream()
        for (level in 0 until 4) for (x in 0 until 64) for (z in 0 until 64) {
            val t = map.tiles[level][x][z]
            // Jagex record order (proven by byte-identical round-trip of real regions): overlay, flags, underlay, height.
            if (t.overlayId != 0) {
                require(t.overlayId in -128..255) { "overlay ${t.overlayId} not encodable in one byte (client reads it as an unsigned byte id)." }
                out.write(2 + t.overlayShape * 4 + (t.overlayRotation and 3))
                out.write(t.overlayId and 0xFF)
            }
            if (t.flags != 0) {
                require(t.flags in 1..32) { "tile flags ${t.flags} not encodable (1..32)." }
                out.write(49 + t.flags)
            }
            if (t.underlayId != 0) {
                require(t.underlayId in 1..174) { "underlay ${t.underlayId} not encodable in rev-667 (1..174)." }
                out.write(81 + t.underlayId)
            }
            if (t.height != -1) {
                out.write(1)
                out.write(t.height and 0xFF)
            } else {
                out.write(0)
            }
        }
        out.write(map.trailing)
        return out.toByteArray()
    }
}

class Rev667Loc(
    val id: Int,
    val localX: Int,
    val localZ: Int,
    val plane: Int,
    val type: Int,
    val rotation: Int,
) {
    val packedPosition: Int get() = (plane shl 12) or (localX shl 6) or localZ
}

object Rev667LocCodec {
    fun decode(data: ByteArray): List<Rev667Loc> {
        val buf = RegionBuffer(data)
        val locs = ArrayList<Rev667Loc>()
        var id = -1
        while (true) {
            val idOffset = buf.uSmartExtended()
            if (idOffset == 0) break
            id += idOffset
            var position = 0
            while (true) {
                val positionOffset = buf.uSmart()
                if (positionOffset == 0) break
                position += positionOffset - 1
                val localZ = position and 0x3F
                val localX = (position shr 6) and 0x3F
                val plane = (position shr 12) and 0x3
                val attributes = buf.u8()
                locs.add(Rev667Loc(id, localX, localZ, plane, attributes shr 2, attributes and 3))
            }
        }
        check(buf.position == data.size) { "Loc stream consumed ${buf.position} of ${data.size} bytes." }
        return locs
    }

    fun encode(locs: List<Rev667Loc>): ByteArray {
        val out = ByteArrayOutputStream()
        val byId = locs.groupBy { it.id }.toSortedMap()
        var previousId = -1
        byId.forEach { (id, placements) ->
            writeSmartExtended(out, id - previousId)
            previousId = id
            var previousPosition = 0
            placements.sortedBy { it.packedPosition }.forEach { loc ->
                val delta = loc.packedPosition - previousPosition + 1
                require(delta in 1..32767) { "loc $id position delta $delta out of range." }
                writeSmart(out, delta)
                previousPosition = loc.packedPosition
                out.write((loc.type shl 2) or (loc.rotation and 3))
            }
            writeSmart(out, 0)
        }
        writeSmart(out, 0)
        return out.toByteArray()
    }

    private fun writeSmart(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        require(value in 0..32767) { "smart $value out of range." }
        if (value < 128) {
            out.write(value)
        } else {
            out.write(((value + 32768) shr 8) and 0xFF)
            out.write((value + 32768) and 0xFF)
        }
    }

    private fun writeSmartExtended(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        var remaining = value
        while (remaining >= 32767) {
            writeSmart(out, 32767)
            remaining -= 32767
        }
        writeSmart(out, remaining)
    }
}

class Rev667UnderlayDef(val rgb: Int, val texture: Int)

class Rev667OverlayDef(
    val rgb: Int,
    val texture: Int,
    val occludes: Boolean,
    val blendRgb: Int,
)

/** `FloorUnderlayType`/`FloorOverlayType` decoders (client opcode tables). */
object Rev667FloorCodec {
    fun decodeUnderlay(data: ByteArray): Rev667UnderlayDef {
        val buf = RegionBuffer(data)
        var rgb = 0
        var texture = -1
        while (true) {
            when (val op = buf.u8()) {
                0 -> return Rev667UnderlayDef(rgb, texture)
                1 -> rgb = buf.u24()
                2 -> texture = buf.u16().let { if (it == 0xFFFF) -1 else it }
                3 -> buf.u16()
                4, 5 -> {}
                else -> error("Unknown rev-667 underlay opcode $op")
            }
        }
    }

    fun decodeOverlay(data: ByteArray): Rev667OverlayDef {
        val buf = RegionBuffer(data)
        var rgb = 0
        var texture = -1
        var occludes = true
        var blend = -1
        while (true) {
            when (val op = buf.u8()) {
                0 -> return Rev667OverlayDef(rgb, texture, occludes, blend)
                1 -> rgb = buf.u24()
                2 -> texture = buf.u8()
                3 -> texture = buf.u16().let { if (it == 0xFFFF) -1 else it }
                5 -> occludes = false
                7 -> blend = buf.u24()
                8, 12 -> {}
                9 -> buf.u16()
                10 -> {}
                11 -> buf.u8()
                13 -> buf.u24()
                14 -> buf.u8()
                16 -> buf.u8()
                else -> error("Unknown rev-667 overlay opcode $op")
            }
        }
    }

    /** Squared RGB distance used for nearest-colour floor mapping. */
    fun colourDistance(
        a: Int,
        b: Int,
    ): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }
}

/**
 * Rev-667 `LocType` (archive 16, group = id ushr 8, file = id and 0xFF). [decode] walks the full
 * client opcode table and retains only what the import/census needs (models, name, sizes);
 * [encodeFromModern] writes the subset of opcodes a modern OSRS loc can be expressed with.
 */
class Rev667LocType(val id: Int) {
    var name = "null"

    /** shape -> model ids, as opcode 1 stores them. */
    val modelsByShape = LinkedHashMap<Int, MutableList<Int>>()
    var sizeX = 1
    var sizeZ = 1
    val options = arrayOfNulls<String>(5)
    var animation = -1
    val allModels: List<Int> get() = modelsByShape.values.flatten()

    companion object {
        fun decode(
            id: Int,
            data: ByteArray,
        ): Rev667LocType {
            val def = Rev667LocType(id)
            val buf = RegionBuffer(data)
            while (true) {
                val op = buf.u8()
                if (op == 0) break
                when (op) {
                    1, 5 -> {
                        // Opcode 5 carries two shape/model lists (main + low-detail), exactly as the
                        // server's ObjectDef.decode skips them; both reference archive-7 model ids.
                        repeat(if (op == 5) 2 else 1) {
                            val shapeCount = buf.u8()
                            repeat(shapeCount) {
                                val shape = buf.i8()
                                val modelCount = buf.u8()
                                val list = def.modelsByShape.getOrPut(shape) { mutableListOf() }
                                repeat(modelCount) { list.add(buf.u16()) }
                            }
                        }
                    }
                    2 -> def.name = buf.string()
                    14 -> def.sizeX = buf.u8()
                    15 -> def.sizeZ = buf.u8()
                    17, 18, 21, 22, 23, 27, 62, 64, 73, 74, 82, 88, 89, 90, 91, 94, 96, 97, 98, 103, 105, 168, 169, 177, 189 -> {}
                    19 -> buf.u8()
                    24 -> def.animation = buf.u16().let { if (it == 0xFFFF) -1 else it }
                    28 -> buf.u8()
                    29 -> buf.i8()
                    39 -> buf.i8()
                    in 30..34 -> def.options[op - 30] = buf.string()
                    40, 41 -> {
                        val n = buf.u8()
                        repeat(n) {
                            buf.u16()
                            buf.u16()
                        }
                    }
                    42 -> {
                        val n = buf.u8()
                        repeat(n) { buf.i8() }
                    }
                    65, 66, 67 -> buf.u16()
                    69 -> buf.u8()
                    70, 71, 72 -> buf.u16()
                    75 -> buf.u8()
                    77, 92 -> {
                        buf.u16()
                        buf.u16()
                        if (op == 92) buf.u16()
                        val n = buf.u8()
                        repeat(n + 1) { buf.u16() }
                    }
                    78 -> {
                        buf.u16()
                        buf.u8()
                    }
                    79 -> {
                        buf.u16()
                        buf.u16()
                        buf.u8()
                        val n = buf.u8()
                        repeat(n) { buf.u16() }
                    }
                    81 -> buf.u8()
                    93 -> buf.u16()
                    95 -> buf.u16()
                    99, 100 -> {
                        buf.u8()
                        buf.u16()
                    }
                    101 -> buf.u8()
                    102 -> buf.u16()
                    104 -> buf.u8()
                    106 -> {
                        val n = buf.u8()
                        repeat(n) {
                            buf.u16()
                            buf.u8()
                        }
                    }
                    107 -> buf.u16()
                    in 150..154 -> def.options[op - 150] = buf.string()
                    160 -> {
                        val n = buf.u8()
                        repeat(n) { buf.u16() }
                    }
                    162 -> buf.i8()
                    163 -> repeat(4) { buf.i8() }
                    164, 165, 166, 167 -> buf.u16()
                    170, 171 -> buf.uSmart()
                    173 -> {
                        buf.u16()
                        buf.u16()
                    }
                    178 -> buf.u8()
                    249 -> {
                        val n = buf.u8()
                        repeat(n) {
                            val isString = buf.u8() == 1
                            buf.u24()
                            if (isString) buf.string() else buf.i32()
                        }
                    }
                    else -> error("Loc $id: unknown rev-667 loc opcode $op at ${buf.position}")
                }
            }
            return def
        }

        /**
         * Encodes a modern loc definition as a rev-667 LocType. Fields with no 667 equivalent or
         * whose ids live in a different table (animations, retextures, map scenes, varbit
         * transforms, ambient sounds, params) are dropped and reported in [dropped].
         */
        fun encodeFromModern(
            src: ModernObjectDef,
            modelRemap: (Int) -> Int,
            dropped: MutableList<String>,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            fun u8(v: Int) = out.write(v and 0xFF)
            fun u16(v: Int) {
                out.write((v shr 8) and 0xFF)
                out.write(v and 0xFF)
            }
            fun str(s: String) {
                s.forEach { c ->
                    val code = c.code
                    require(code in 1..255) { "Non-CP1252 char in '$s'." }
                    out.write(code)
                }
                out.write(0)
            }

            if (src.models.isNotEmpty()) {
                val types = src.modelTypes ?: List(src.models.size) { 10 }
                val byShape = LinkedHashMap<Int, MutableList<Int>>()
                src.models.forEachIndexed { i, m -> byShape.getOrPut(types[i]) { mutableListOf() }.add(modelRemap(m)) }
                u8(1)
                u8(byShape.size)
                byShape.forEach { (shape, models) ->
                    u8(shape)
                    u8(models.size)
                    models.forEach { m ->
                        require(m in 0..0xFFFF) { "model $m not addressable by a rev-667 loc." }
                        u16(m)
                    }
                }
            }
            if (src.name != "null") {
                u8(2)
                str(src.name)
            }
            if (src.sizeX != 1) {
                u8(14)
                u8(src.sizeX)
            }
            if (src.sizeY != 1) {
                u8(15)
                u8(src.sizeY)
            }
            if (src.interactType == 0) {
                u8(17)
            } else if (!src.blocksProjectile) {
                u8(18)
            }
            if (src.interactType == 1) u8(27)
            if (src.wallOrDoor != -1) {
                u8(19)
                u8(src.wallOrDoor)
            }
            if (src.contouredGround == 0) u8(21)
            if (src.contouredGround == 1) {
                u8(81)
                u8((src.contouredGroundValue / 256) and 0xFF)
            }
            if (src.mergeNormals) u8(22)
            if (src.modelClipped) u8(23)
            if (src.animationId != -1) dropped.add("loc ${src.id} '${src.name}': animation ${src.animationId} (modern seq id, not in 667 table)")
            if (src.decorDisplacement != 16) {
                u8(28)
                u8(src.decorDisplacement)
            }
            if (src.ambient != 0) {
                u8(29)
                u8(src.ambient)
            }
            if (src.contrast != 0) {
                u8(39)
                u8(src.contrast / 25)
            }
            src.options.forEachIndexed { i, o ->
                if (o != null) {
                    u8(30 + i)
                    str(o)
                }
            }
            val rf = src.recolourFind
            val rr = src.recolourReplace
            if (rf != null && rr != null && rf.isNotEmpty()) {
                u8(40)
                u8(rf.size)
                for (i in rf.indices) {
                    u16(rf[i])
                    u16(rr[i])
                }
            }
            if (src.retextureFind?.isNotEmpty() == true) dropped.add("loc ${src.id}: retexture table (modern texture ids)")
            if (src.rotated) u8(62)
            if (!src.shadow) u8(64)
            if (src.modelSizeX != 128) {
                u8(65)
                u16(src.modelSizeX)
            }
            if (src.modelSizeHeight != 128) {
                u8(66)
                u16(src.modelSizeHeight)
            }
            if (src.modelSizeY != 128) {
                u8(67)
                u16(src.modelSizeY)
            }
            if (src.mapSceneId != -1) dropped.add("loc ${src.id}: map scene ${src.mapSceneId}")
            if (src.blockingMask != 0) {
                u8(69)
                u8(src.blockingMask)
            }
            if (src.offsetX != 0) {
                u8(70)
                u16(src.offsetX)
            }
            if (src.offsetHeight != 0) {
                u8(71)
                u16(src.offsetHeight)
            }
            if (src.offsetY != 0) {
                u8(72)
                u16(src.offsetY)
            }
            if (src.obstructsGround) u8(73)
            if (src.hollow) u8(74)
            if (src.supportsItems != -1) {
                u8(75)
                u8(src.supportsItems)
            }
            if (src.transforms != null) dropped.add("loc ${src.id}: varbit/varp transform table (base definition used)")
            if (src.ambientSoundId != -1) dropped.add("loc ${src.id}: ambient sound")
            if (src.params != null) dropped.add("loc ${src.id}: params")
            u8(0)
            return out.toByteArray()
        }
    }
}
