package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.Gson
import java.io.File

/**
 * Read-only probes of a rev-667 cache's world data, and the codec self-check every world import
 * must pass before it is allowed to write: decode a real region with [Rev667TileCodec] /
 * [Rev667LocCodec], re-encode it, and require byte identity. Codec correctness is thereby proven
 * against real cache bytes rather than assumed.
 *
 * Modes:
 *  * `<cachePath> <xteaDir> roundtrip <regionId> [regionId ...]` - tile + loc round-trip per region.
 *  * `<cachePath> floors` - every underlay/overlay definition (id, rgb, texture) for floor mapping.
 *  * `<cachePath> <xteaDir> locs <regionId> [minX minZ maxX maxZ]` - loc placements (with names) in a region/box.
 *  * `<cachePath> loctypes` - every LocType decoded (id, name, models); also proves the LocType decoder.
 */
object Rev667RegionProbeTool {
    const val MAP_INDEX = 5
    const val LOC_INDEX = 16
    const val CONFIG_INDEX = 2
    const val UNDERLAY_GROUP = 1
    const val OVERLAY_GROUP = 4

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: see KDoc." }
        val cachePath = args[0]
        val library = CacheLibrary(cachePath)
        try {
            when {
                args[1] == "floors" -> floors(library)
                args[1] == "loctypes" -> locTypes(library)
                args.size >= 3 && args[1] == "locbytes" -> args.drop(2).forEach { locBytes(library, it.toInt()) }
                args.size >= 4 && args[1] == "tile" -> tileInfo(library, args[2].toInt(), args[3].toInt())
                args.size >= 4 && args[2] == "roundtrip" -> args.drop(3).forEach { roundTrip(library, loadKeys(File(args[1])), it.toInt()) }
                args.size >= 4 && args[2] == "locs" -> locs(library, loadKeys(File(args[1])), args[3].toInt(), if (args.size >= 8) IntArray(4) { args[4 + it].toInt() } else null)
                else -> error("Unknown mode.")
            }
        } finally {
            library.close()
        }
    }

    fun loadKeys(dir: File): Map<Int, IntArray> {
        val file = if (dir.isDirectory) File(dir, "xteas.json") else dir
        val entries = Gson().fromJson(file.readText(), Array<XteaEntry>::class.java) ?: emptyArray()
        return entries.associate { it.mapsquare to it.key }
    }

    private class XteaEntry(
        val mapsquare: Int = 0,
        val key: IntArray = IntArray(4),
    )

    fun regionGroupData(
        library: CacheLibrary,
        name: String,
        key: IntArray?,
    ): ByteArray? {
        val index = library.index(MAP_INDEX)
        val archive = index.archive(name, key) ?: return null
        return archive.file(0)?.data
    }

    fun roundTrip(
        library: CacheLibrary,
        keys: Map<Int, IntArray>,
        regionId: Int,
    ) {
        val rx = regionId shr 8
        val rz = regionId and 0xFF
        val mapData = regionGroupData(library, "m${rx}_$rz", null)
        if (mapData == null) {
            println("REGION=$regionId map=ABSENT")
        } else {
            val decoded = Rev667TileCodec.decode(mapData)
            val encoded = Rev667TileCodec.encode(decoded)
            if (!encoded.contentEquals(mapData)) {
                val i = mapData.indices.first { it >= encoded.size || mapData[it] != encoded[it] }
                println("  FIRST_DIFF at $i original=${mapData.copyOfRange(maxOf(0, i - 4), minOf(mapData.size, i + 12)).map { it.toInt() and 0xFF }} encoded=${encoded.copyOfRange(maxOf(0, i - 4), minOf(encoded.size, i + 12)).map { it.toInt() and 0xFF }}")
            }
            println(
                "REGION=$regionId map bytes=${mapData.size} trailing=${decoded.trailing.size} " +
                    "roundtrip=${if (encoded.contentEquals(mapData)) "IDENTICAL" else "MISMATCH(${encoded.size}b)"}",
            )
        }
        val key = keys[regionId]
        val locData = regionGroupData(library, "l${rx}_$rz", key)
        if (locData == null) {
            println("REGION=$regionId locs=ABSENT key=${key?.toList()}")
        } else {
            val decoded = Rev667LocCodec.decode(locData)
            val encoded = Rev667LocCodec.encode(decoded)
            println(
                "REGION=$regionId locs bytes=${locData.size} count=${decoded.size} " +
                    "roundtrip=${if (encoded.contentEquals(locData)) "IDENTICAL" else "MISMATCH(${encoded.size}b)"}",
            )
        }
    }

    fun floors(library: CacheLibrary) {
        val index = library.index(CONFIG_INDEX)
        val underlays = index.archive(UNDERLAY_GROUP) ?: error("No underlay group.")
        underlays.fileIds().sorted().forEach { id ->
            val data = underlays.file(id)?.data ?: return@forEach
            val def = Rev667FloorCodec.decodeUnderlay(data)
            println("UNDERLAY id=$id rgb=${"%06x".format(def.rgb)} texture=${def.texture}")
        }
        val overlays = index.archive(OVERLAY_GROUP) ?: error("No overlay group.")
        overlays.fileIds().sorted().forEach { id ->
            val data = overlays.file(id)?.data ?: return@forEach
            val def = Rev667FloorCodec.decodeOverlay(data)
            println("OVERLAY id=$id rgb=${"%06x".format(def.rgb)} texture=${def.texture} occludes=${def.occludes} blend=${if (def.blendRgb == -1) "-" else "%06x".format(def.blendRgb)}")
        }
    }

    /**
     * Raw opcode stream of one LocType, for render-behaviour questions the decoded view hides
     * (shape->model split, ambient/contrast, the boolean render flags at opcodes 17-29, ...).
     */
    fun locBytes(
        library: CacheLibrary,
        id: Int,
    ) {
        val data = library.data(LOC_INDEX, id shr 8, id and 0xFF)
        if (data == null) {
            println("LOC_ ABSENT")
            return
        }
        val decoded = Rev667LocType.decode(id, data)
        println("LOC_ bytes=${data.size} name='${decoded.name}' size=${decoded.sizeX}x${decoded.sizeZ} anim=${decoded.animation}")
        decoded.modelsByShape.forEach { (shape, models) -> println("  shape=$shape models=$models") }
        println("  options=${decoded.options.toList()}")
        println("  opcodes=${opcodeStream(data)}")
    }

    private fun opcodeStream(data: ByteArray): String {
        // Just the leading opcode of each record is unreliable to recover without a full decoder,
        // so print the raw bytes; short enough for a single LocType and unambiguous.
        return data.joinToString(" ") { (it.toInt() and 0xFF).toString() }
    }

    fun locTypes(library: CacheLibrary): Map<Int, Rev667LocType> {
        val index = library.index(LOC_INDEX)
        val result = sortedMapOf<Int, Rev667LocType>()
        var failures = 0
        index.archiveIds().sorted().forEach { group ->
            val archive = index.archive(group) ?: return@forEach
            archive.fileIds().forEach { file ->
                val data = archive.file(file)?.data ?: return@forEach
                val id = (group shl 8) or file
                val decoded = runCatching { Rev667LocType.decode(id, data) }
                decoded.getOrNull()?.let { result[id] = it }
                if (decoded.isFailure) {
                    failures++
                    if (failures <= 10) println("LOCTYPE_DECODE_FAILED id=$id bytes=${data.size} ${decoded.exceptionOrNull()} stream=${data.take(40).map { it.toInt() and 0xFF }}")
                }
            }
        }
        println("LOCTYPES=${result.size} failures=$failures maxId=${result.keys.maxOrNull()}")
        return result
    }

    fun locs(
        library: CacheLibrary,
        keys: Map<Int, IntArray>,
        regionId: Int,
        box: IntArray?,
    ) {
        val rx = regionId shr 8
        val rz = regionId and 0xFF
        val locData = regionGroupData(library, "l${rx}_$rz", keys[regionId]) ?: error("No locs for $regionId")
        val types = locTypes(library)
        Rev667LocCodec.decode(locData).forEach { loc ->
            val x = rx * 64 + loc.localX
            val z = rz * 64 + loc.localZ
            if (box != null && (x !in box[0]..box[2] || z !in box[1]..box[3])) return@forEach
            val def = types[loc.id]
            println("LOC id=${loc.id} '${def?.name}' at $x,$z,${loc.plane} type=${loc.type} rot=${loc.rotation} models=${def?.allModels}")
        }
    }
}

/** Prints the raw tile record (height/flags/overlay/underlay) of one world tile on all four planes. */
fun Rev667RegionProbeTool.tileInfo(
    library: com.displee.cache.CacheLibrary,
    x: Int,
    z: Int,
) {
    val rx = x shr 6
    val rz = z shr 6
    val data = regionGroupData(library, "m${rx}_$rz", null) ?: error("no map m${rx}_$rz")
    val map = Rev667TileCodec.decode(data)
    for (plane in 0 until 4) {
        val t = map.tiles[plane][x and 63][z and 63]
        println("TILE $x,$z,$plane height=${t.height} flags=${t.flags} overlay=${t.overlayId} shape=${t.overlayShape} rot=${t.overlayRotation} underlay=${t.underlayId}")
    }
}
