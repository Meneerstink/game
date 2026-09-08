package gg.rsmod.game.tools.importer

import com.google.gson.Gson
import java.io.File

/**
 * Read-only census of one modern OSRS map square (JS5 archive 5) from the pinned upstream cache,
 * for cross-revision world imports such as Ferox Enclave.
 *
 * Formats implemented from the RuneLite cache loaders (`MapLoader`, `LocationsLoader`,
 * `ObjectLoader`, `UnderlayLoader`, `OverlayLoader`) for the modern format (tile attribute and
 * overlay id are 16-bit since the 2022 map-format change). Every decoder here hard-fails on an
 * unknown opcode or on a tile stream that does not consume its buffer exactly, so a wrong format
 * assumption is loud rather than silently producing a plausible-looking region.
 *
 * Loc groups are XTEA-encrypted; keys come from an OpenRS2 `keys.json` export (fields
 * `mapsquare`, `key`). The reader never writes anything.
 *
 * Usage:
 *   `./gradlew :game:runModernRegionProbeTool --args="<modernCacheDir> <keysJson> <regionId> [minX minY maxX maxY] [outFile]"`
 * The optional absolute-tile bounding box restricts the loc/tile census to a footprint.
 */
object ModernRegionProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) { "Usage: <modernCacheDir> <keysJson> <regionId> [minX minY maxX maxY] [outFile]" }
        val cacheDir = File(args[0])
        val keys = loadKeys(File(args[1]))
        val regionId = args[2].toInt()
        val box = if (args.size >= 7) IntArray(4) { args[3 + it].toInt() } else null
        val out = StringBuilder()
        ModernCacheReader(cacheDir).use { reader ->
            val region = ModernRegion.load(reader, regionId, keys[regionId])
            out.appendLine("REGION=$regionId rx=${region.rx} rz=${region.rz} base=(${region.rx * 64},${region.rz * 64}) locKey=${keys[regionId] != null}")
            out.appendLine("TILE_STREAM_BYTES=${region.tileBytes} LOC_STREAM_BYTES=${region.locBytes} LOC_COUNT=${region.locs.size}")

            val inBox = { x: Int, z: Int -> box == null || (x in box[0]..box[2] && z in box[1]..box[3]) }

            // ---- tiles ----
            val underlays = sortedMapOf<Int, Int>()
            val overlays = sortedMapOf<Int, Int>()
            val flags = sortedMapOf<Int, Int>()
            var tilesInBox = 0
            for (plane in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
                val x = region.rx * 64 + lx
                val z = region.rz * 64 + lz
                if (!inBox(x, z)) continue
                val t = region.tiles[plane][lx][lz]
                tilesInBox++
                if (t.underlayId > 0) underlays.merge(t.underlayId, 1, Int::plus)
                if (t.overlayId != 0) overlays.merge(t.overlayId, 1, Int::plus)
                if (t.settings != 0) flags.merge(t.settings, 1, Int::plus)
            }
            out.appendLine("TILES_IN_BOX=$tilesInBox TRAILING_TILE_BYTES=${trailingTileBytes.toList()}")
            val underlayDefs = reader.files(2, 1)
            val overlayDefs = reader.files(2, 4)
            underlays.forEach { (id, n) ->
                val def = underlayDefs[id]?.let { ModernFloorDefs.decodeUnderlay(it) }
                out.appendLine("UNDERLAY id=$id tiles=$n rgb=${def?.rgb?.let { "%06x".format(it) } ?: "MISSING"}")
            }
            overlays.forEach { (id, n) ->
                val def = overlayDefs[id]?.let { ModernFloorDefs.decodeOverlay(it) }
                out.appendLine(
                    "OVERLAY id=$id tiles=$n rgb=${def?.rgb?.let { "%06x".format(it) } ?: "MISSING"} " +
                        "texture=${def?.texture ?: -1} secondary=${def?.secondaryRgb?.let { "%06x".format(it) } ?: "-"} hideUnderlay=${def?.hideUnderlay}",
                )
            }
            flags.forEach { (f, n) -> out.appendLine("TILEFLAG settings=$f tiles=$n") }

            // ---- locs ----
            val locDefs = reader.files(2, 6)
            val byId = sortedMapOf<Int, MutableList<ModernLoc>>()
            region.locs.filter { inBox(region.rx * 64 + it.localX, region.rz * 64 + it.localZ) }.forEach {
                byId.getOrPut(it.id) { mutableListOf() }.add(it)
            }
            val modelIds = sortedSetOf<Int>()
            out.appendLine("UNIQUE_LOC_IDS_IN_BOX=${byId.size} PLACEMENTS_IN_BOX=${byId.values.sumOf { it.size }}")
            byId.forEach { (id, placements) ->
                val raw = locDefs[id]
                val def = raw?.let { ModernObjectDef.decode(id, it) }
                if (def != null) modelIds.addAll(def.models)
                val types = placements.map { it.type }.distinct().sorted()
                out.appendLine(
                    "LOC id=$id n=${placements.size} types=$types name='${def?.name ?: "MISSING"}' " +
                        "size=${def?.sizeX}x${def?.sizeY} interact=${def?.interactType} blocksProjectile=${def?.blocksProjectile} " +
                        "wallOrDoor=${def?.wallOrDoor} anim=${def?.animationId} models=${def?.models} modelTypes=${def?.modelTypes} " +
                        "options=${def?.options?.toList()} varbit=${def?.varbitId} varp=${def?.varpId} transforms=${def?.transforms?.toList()} " +
                        "recolours=${def?.recolourFind?.size ?: 0} retextures=${def?.retextureFind?.size ?: 0} " +
                        "mapScene=${def?.mapSceneId} unknownOps=${def?.unknownOpcodes}",
                )
                placements.forEach { p ->
                    out.appendLine("  AT ${region.rx * 64 + p.localX},${region.rz * 64 + p.localZ},${p.plane} type=${p.type} rot=${p.rotation}")
                }
            }
            // ---- models ----
            var textured = 0
            var missing = 0
            var totalBytes = 0
            modelIds.forEach { modelId ->
                val data = reader.file(7, modelId, 0)
                if (data == null) {
                    missing++
                    out.appendLine("MODEL id=$modelId MISSING")
                    return@forEach
                }
                totalBytes += data.size
                val decoded = runCatching { ModernModelDecoder.decode(data) }
                val model = decoded.getOrNull()
                if (model == null) {
                    out.appendLine("MODEL id=$modelId bytes=${data.size} DECODE_FAILED=${decoded.exceptionOrNull()?.message}")
                    return@forEach
                }
                val texFaces = model.faceTexture?.count { it.toInt() != -1 } ?: 0
                if (texFaces > 0) textured++
                out.appendLine(
                    "MODEL id=$modelId bytes=${data.size} v=${model.vertexCount} f=${model.faceCount} texFaces=$texFaces " +
                        "textures=${model.faceTexture?.filter { it.toInt() != -1 }?.distinct()?.sorted() ?: emptyList<Short>()} " +
                        "animaya=${model.droppedAnimayaSkinning} zoff=${model.droppedFaceZOffsets}",
                )
            }
            out.appendLine("MODELS_UNIQUE=${modelIds.size} TEXTURED=$textured MISSING=$missing TOTAL_BYTES=$totalBytes")
        }
        print(out)
        val outFile = if (args.size >= 8) args[7] else if (args.size == 4) args[3] else null
        if (outFile != null) File(outFile).writeText(out.toString())
    }

    fun loadKeys(file: File): Map<Int, IntArray> {
        val entries = Gson().fromJson(file.readText(), Array<KeyEntry>::class.java) ?: emptyArray()
        return entries.associate { it.mapsquare to it.key }
    }

    private class KeyEntry(
        val mapsquare: Int = 0,
        val key: IntArray = IntArray(4),
    )
}

/** Jagex/Java string hash used for JS5 group names. */
fun js5NameHash(name: String): Int {
    var h = 0
    for (c in name) h = 31 * h + c.code
    return h
}

class ModernTile {
    var height: Int = -1
    var overlayId: Int = 0
    var overlayPath: Int = 0
    var overlayRotation: Int = 0
    var settings: Int = 0
    var underlayId: Int = 0
}

class ModernLoc(
    val id: Int,
    val localX: Int,
    val localZ: Int,
    val plane: Int,
    val type: Int,
    val rotation: Int,
)

/** Bytes after the 4x64x64 tile records (the client reads optional trailing environment records until EOF). */
var trailingTileBytes: ByteArray = ByteArray(0)

class ModernRegion(
    val rx: Int,
    val rz: Int,
    val tiles: Array<Array<Array<ModernTile>>>,
    val locs: List<ModernLoc>,
    val tileBytes: Int,
    val locBytes: Int,
) {
    companion object {
        fun load(
            reader: ModernCacheReader,
            regionId: Int,
            locKey: IntArray?,
        ): ModernRegion {
            val rx = regionId shr 8
            val rz = regionId and 0xFF
            val index = reader.index(5)
            val mapGroup = index.groups.values.firstOrNull { it.nameHash == js5NameHash("m${rx}_$rz") }
                ?: error("Modern cache has no map group m${rx}_$rz (hash ${js5NameHash("m${rx}_$rz")}); index5 groups=${index.groups.size} hasNames=${index.hasNames} sampleHashes=${index.groups.values.take(5).map { it.nameHash }} keyHashFor_l=${js5NameHash("l${rx}_$rz")}")
            val locGroup = index.groups.values.firstOrNull { it.nameHash == js5NameHash("l${rx}_$rz") }
                ?: error("Modern cache has no loc group l${rx}_$rz.")
            val mapData = reader.decompress(reader.readContainer(5, mapGroup.id) ?: error("Empty map container."))
            val tiles = decodeTiles(mapData)
            val locs =
                if (locKey == null) {
                    emptyList()
                } else {
                    val container = reader.readContainer(5, locGroup.id) ?: error("Empty loc container.")
                    val decrypted = Xtea.decryptContainer(container, locKey)
                    decodeLocs(reader.decompress(decrypted))
                }
            val locBytes = if (locKey == null) -1 else -2
            return ModernRegion(rx, rz, tiles, locs, mapData.size, locBytes)
        }

        /** RuneLite `MapLoader.loadTerrain` (16-bit attribute/overlay, post-2022 format). */
        fun decodeTiles(data: ByteArray): Array<Array<Array<ModernTile>>> {
            val buf = RegionBuffer(data)
            val tiles = Array(4) { Array(64) { Array(64) { ModernTile() } } }
            for (z in 0 until 4) for (x in 0 until 64) for (y in 0 until 64) {
                val tile = tiles[z][x][y]
                while (true) {
                    val attribute = buf.u16()
                    if (attribute == 0) {
                        break
                    } else if (attribute == 1) {
                        tile.height = buf.u8()
                        break
                    } else if (attribute <= 49) {
                        tile.overlayId = buf.u16().toShort().toInt()
                        tile.overlayPath = (attribute - 2) / 4
                        tile.overlayRotation = (attribute - 2) and 3
                    } else if (attribute <= 81) {
                        tile.settings = attribute - 49
                    } else {
                        tile.underlayId = attribute - 81
                    }
                }
            }
            check(data.size - buf.position <= 1) {
                "Tile stream consumed ${buf.position} of ${data.size} bytes - format assumption wrong."
            }
            trailingTileBytes = data.copyOfRange(buf.position, data.size)
            return tiles
        }

        /** RuneLite `LocationsLoader`. */
        fun decodeLocs(data: ByteArray): List<ModernLoc> {
            val buf = RegionBuffer(data)
            val locs = ArrayList<ModernLoc>()
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
                    val localY = position and 0x3F
                    val localX = (position shr 6) and 0x3F
                    val plane = (position shr 12) and 0x3
                    val attributes = buf.u8()
                    locs.add(ModernLoc(id, localX, localY, plane, attributes shr 2, attributes and 3))
                }
            }
            check(buf.position == data.size) {
                "Loc stream consumed ${buf.position} of ${data.size} bytes - wrong key or format."
            }
            return locs
        }
    }
}

/** RS XTEA (32 rounds, big-endian blocks), as used for JS5 map containers. */
object Xtea {
    private const val DELTA = -0x61c88647
    private const val ROUNDS = 32

    /** Decrypts the payload of a JS5 container in place-copy: bytes after the 5-byte header, for `len` (+4 if compressed). */
    fun decryptContainer(
        container: ByteArray,
        key: IntArray,
    ): ByteArray {
        if (key.all { it == 0 }) return container
        val type = container[0].toInt() and 0xFF
        val len = ((container[1].toInt() and 0xFF) shl 24) or ((container[2].toInt() and 0xFF) shl 16) or
            ((container[3].toInt() and 0xFF) shl 8) or (container[4].toInt() and 0xFF)
        val encryptedLength = if (type == 0) len else len + 4
        val out = container.copyOf()
        decrypt(out, 5, encryptedLength, key)
        return out
    }

    fun encryptContainer(
        container: ByteArray,
        key: IntArray,
    ): ByteArray {
        if (key.all { it == 0 }) return container
        val type = container[0].toInt() and 0xFF
        val len = ((container[1].toInt() and 0xFF) shl 24) or ((container[2].toInt() and 0xFF) shl 16) or
            ((container[3].toInt() and 0xFF) shl 8) or (container[4].toInt() and 0xFF)
        val encryptedLength = if (type == 0) len else len + 4
        val out = container.copyOf()
        encrypt(out, 5, encryptedLength, key)
        return out
    }

    fun decrypt(
        data: ByteArray,
        offset: Int,
        length: Int,
        key: IntArray,
    ) {
        val blocks = length / 8
        for (b in 0 until blocks) {
            val p = offset + b * 8
            var v0 = readInt(data, p)
            var v1 = readInt(data, p + 4)
            var sum = DELTA * ROUNDS
            repeat(ROUNDS) {
                v1 -= (((v0 shl 4) xor (v0 ushr 5)) + v0) xor (sum + key[(sum ushr 11) and 3])
                sum -= DELTA
                v0 -= (((v1 shl 4) xor (v1 ushr 5)) + v1) xor (sum + key[sum and 3])
            }
            writeInt(data, p, v0)
            writeInt(data, p + 4, v1)
        }
    }

    fun encrypt(
        data: ByteArray,
        offset: Int,
        length: Int,
        key: IntArray,
    ) {
        val blocks = length / 8
        for (b in 0 until blocks) {
            val p = offset + b * 8
            var v0 = readInt(data, p)
            var v1 = readInt(data, p + 4)
            var sum = 0
            repeat(ROUNDS) {
                v0 += (((v1 shl 4) xor (v1 ushr 5)) + v1) xor (sum + key[sum and 3])
                sum += DELTA
                v1 += (((v0 shl 4) xor (v0 ushr 5)) + v0) xor (sum + key[(sum ushr 11) and 3])
            }
            writeInt(data, p, v0)
            writeInt(data, p + 4, v1)
        }
    }

    private fun readInt(
        d: ByteArray,
        p: Int,
    ): Int =
        ((d[p].toInt() and 0xFF) shl 24) or ((d[p + 1].toInt() and 0xFF) shl 16) or
            ((d[p + 2].toInt() and 0xFF) shl 8) or (d[p + 3].toInt() and 0xFF)

    private fun writeInt(
        d: ByteArray,
        p: Int,
        v: Int,
    ) {
        d[p] = (v shr 24).toByte()
        d[p + 1] = (v shr 16).toByte()
        d[p + 2] = (v shr 8).toByte()
        d[p + 3] = v.toByte()
    }
}

class ModernUnderlayDef(val rgb: Int)

class ModernOverlayDef(
    val rgb: Int,
    val texture: Int,
    val hideUnderlay: Boolean,
    val secondaryRgb: Int,
)

/** RuneLite `UnderlayLoader` / `OverlayLoader`; unknown opcodes are hard errors. */
object ModernFloorDefs {
    fun decodeUnderlay(data: ByteArray): ModernUnderlayDef {
        val buf = RegionBuffer(data)
        var rgb = 0
        while (true) {
            when (val op = buf.u8()) {
                0 -> return ModernUnderlayDef(rgb)
                1 -> rgb = (buf.u8() shl 16) or (buf.u8() shl 8) or buf.u8()
                else -> error("Unknown underlay opcode $op")
            }
        }
    }

    fun decodeOverlay(data: ByteArray): ModernOverlayDef {
        val buf = RegionBuffer(data)
        var rgb = 0
        var texture = -1
        var hideUnderlay = true
        var secondary = -1
        while (true) {
            when (val op = buf.u8()) {
                0 -> return ModernOverlayDef(rgb, texture, hideUnderlay, secondary)
                1 -> rgb = (buf.u8() shl 16) or (buf.u8() shl 8) or buf.u8()
                2 -> texture = buf.u8()
                5 -> hideUnderlay = false
                7 -> secondary = (buf.u8() shl 16) or (buf.u8() shl 8) or buf.u8()
                else -> error("Unknown overlay opcode $op")
            }
        }
    }
}

/** RuneLite `ObjectLoader` (rev-220+ sound layout); unknown opcodes are recorded, never skipped silently. */
class ModernObjectDef(val id: Int) {
    var name: String = "null"
    var models: List<Int> = emptyList()
    var modelTypes: List<Int>? = null
    var sizeX = 1
    var sizeY = 1
    var interactType = 2
    var blocksProjectile = true
    var wallOrDoor = -1
    var contouredGround = -1
    var contouredGroundValue = 0
    var mergeNormals = false
    var modelClipped = false
    var animationId = -1
    var decorDisplacement = 16
    var ambient = 0
    var contrast = 0
    val options = arrayOfNulls<String>(5)
    var recolourFind: IntArray? = null
    var recolourReplace: IntArray? = null
    var retextureFind: IntArray? = null
    var retextureReplace: IntArray? = null
    var category = -1
    var rotated = false
    var shadow = true
    var modelSizeX = 128
    var modelSizeHeight = 128
    var modelSizeY = 128
    var mapSceneId = -1
    var blockingMask = 0
    var offsetX = 0
    var offsetHeight = 0
    var offsetY = 0
    var obstructsGround = false
    var hollow = false
    var supportsItems = -1
    var varbitId = -1
    var varpId = -1
    var transforms: IntArray? = null
    var ambientSoundId = -1
    var mapAreaId = -1
    var randomizeAnimStart = false
    var deferAnimChange = false
    var params: Map<Int, Any>? = null
    val unknownOpcodes = mutableListOf<Int>()

    companion object {
        fun decode(
            id: Int,
            data: ByteArray,
        ): ModernObjectDef {
            val def = ModernObjectDef(id)
            val buf = RegionBuffer(data)
            while (true) {
                val op = buf.u8()
                if (op == 0) break
                when (op) {
                    1 -> {
                        val n = buf.u8()
                        val m = ArrayList<Int>(n)
                        val t = ArrayList<Int>(n)
                        repeat(n) {
                            m.add(buf.u16())
                            t.add(buf.u8())
                        }
                        def.models = m
                        def.modelTypes = t
                    }
                    2 -> def.name = buf.string()
                    5 -> {
                        val n = buf.u8()
                        def.models = List(n) { buf.u16() }
                        def.modelTypes = null
                    }
                    6 -> {
                        val n = buf.u8()
                        val m = ArrayList<Int>(n)
                        val t = ArrayList<Int>(n)
                        repeat(n) {
                            m.add(buf.i32())
                            t.add(buf.u8())
                        }
                        def.models = m
                        def.modelTypes = t
                    }
                    7 -> {
                        val n = buf.u8()
                        def.models = List(n) { buf.i32() }
                        def.modelTypes = null
                    }
                    14 -> def.sizeX = buf.u8()
                    15 -> def.sizeY = buf.u8()
                    17 -> {
                        def.interactType = 0
                        def.blocksProjectile = false
                    }
                    18 -> def.blocksProjectile = false
                    19 -> def.wallOrDoor = buf.u8()
                    21 -> def.contouredGround = 0
                    22 -> def.mergeNormals = true
                    23 -> def.modelClipped = true
                    24 -> def.animationId = buf.u16().let { if (it == 0xFFFF) -1 else it }
                    27 -> def.interactType = 1
                    28 -> def.decorDisplacement = buf.u8()
                    29 -> def.ambient = buf.i8()
                    39 -> def.contrast = buf.i8() * 25
                    in 30..34 -> def.options[op - 30] = buf.string()
                    40 -> {
                        val n = buf.u8()
                        def.recolourFind = IntArray(n)
                        def.recolourReplace = IntArray(n)
                        for (i in 0 until n) {
                            def.recolourFind!![i] = buf.u16()
                            def.recolourReplace!![i] = buf.u16()
                        }
                    }
                    41 -> {
                        val n = buf.u8()
                        def.retextureFind = IntArray(n)
                        def.retextureReplace = IntArray(n)
                        for (i in 0 until n) {
                            def.retextureFind!![i] = buf.u16()
                            def.retextureReplace!![i] = buf.u16()
                        }
                    }
                    61 -> def.category = buf.u16()
                    62 -> def.rotated = true
                    64 -> def.shadow = false
                    65 -> def.modelSizeX = buf.u16()
                    66 -> def.modelSizeHeight = buf.u16()
                    67 -> def.modelSizeY = buf.u16()
                    68 -> def.mapSceneId = buf.u16()
                    69 -> def.blockingMask = buf.u8()
                    70 -> def.offsetX = buf.u16().toShort().toInt()
                    71 -> def.offsetHeight = buf.u16().toShort().toInt()
                    72 -> def.offsetY = buf.u16().toShort().toInt()
                    73 -> def.obstructsGround = true
                    74 -> def.hollow = true
                    75 -> def.supportsItems = buf.u8()
                    77, 92 -> {
                        def.varbitId = buf.u16().let { if (it == 0xFFFF) -1 else it }
                        def.varpId = buf.u16().let { if (it == 0xFFFF) -1 else it }
                        var last = -1
                        if (op == 92) last = buf.u16().let { if (it == 0xFFFF) -1 else it }
                        val n = buf.u8()
                        val dest = IntArray(n + 2)
                        for (i in 0..n) dest[i] = buf.u16().let { if (it == 0xFFFF) -1 else it }
                        dest[n + 1] = last
                        def.transforms = dest
                    }
                    78 -> {
                        def.ambientSoundId = buf.u16()
                        buf.u8()
                        buf.u8()
                    }
                    79 -> {
                        buf.u16()
                        buf.u16()
                        buf.u8()
                        buf.u8()
                        val n = buf.u8()
                        repeat(n) { buf.u16() }
                    }
                    81 -> {
                        def.contouredGround = 1
                        def.contouredGroundValue = buf.u8() * 256
                    }
                    82 -> def.mapAreaId = buf.u16()
                    89 -> def.randomizeAnimStart = true
                    90 -> def.deferAnimChange = true
                    91 -> buf.u8()
                    93 -> {
                        buf.u8()
                        buf.u16()
                        buf.u8()
                        buf.u16()
                    }
                    94 -> {}
                    95 -> buf.u8()
                    96 -> buf.u8()
                    249 -> {
                        val n = buf.u8()
                        val map = LinkedHashMap<Int, Any>()
                        repeat(n) {
                            val isString = buf.u8() == 1
                            val key = buf.u24()
                            map[key] = if (isString) buf.string() else buf.i32()
                        }
                        def.params = map
                    }
                    else -> error("Loc $id: unknown modern loc opcode $op at ${buf.position}")
                }
            }
            return def
        }
    }
}

/** Big-endian cursor with the JS5 smart/extended-smart/string helpers the map and config formats need. */
class RegionBuffer(private val data: ByteArray) {
    var position = 0

    val remaining: Int get() = data.size - position

    fun u8(): Int = data[position++].toInt() and 0xFF

    fun i8(): Int = data[position++].toInt()

    fun u16(): Int = (u8() shl 8) or u8()

    fun u24(): Int = (u8() shl 16) or (u8() shl 8) or u8()

    fun i32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

    /** One byte if < 128, otherwise two bytes minus 32768. */
    fun uSmart(): Int = if ((data[position].toInt() and 0xFF) < 128) u8() else u16() - 32768

    /** Sum of smarts while each reads 32767 (client `gExtended1or2`, RuneLite `readUnsignedIntSmartShortCompat`). */
    fun uSmartExtended(): Int {
        var total = 0
        var value = uSmart()
        while (value == 32767) {
            total += 32767
            value = uSmart()
        }
        return total + value
    }

    /** Null-terminated CP-1252 string. */
    fun string(): String {
        val sb = StringBuilder()
        while (true) {
            val c = u8()
            if (c == 0) break
            sb.append(if (c in 128..159) CP1252_HIGH[c - 128] else c.toChar())
        }
        return sb.toString()
    }

    companion object {
        private val CP1252_HIGH = charArrayOf(
            '\u20ac', '\u0080', '\u201a', '\u0192', '\u201e', '\u2026', '\u2020', '\u2021', '\u02c6', '\u2030',
            '\u0160', '\u2039', '\u0152', '\u008d', '\u017d', '\u008f', '\u0090', '\u2018', '\u2019', '\u201c',
            '\u201d', '\u2022', '\u2013', '\u2014', '\u02dc', '\u2122', '\u0161', '\u203a', '\u0153', '\u009d',
            '\u017e', '\u0178',
        )
    }
}
