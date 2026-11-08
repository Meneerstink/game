package gg.rsmod.game.tools.importer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import java.io.File

/** N601 decision B: source loc/model -> proposed 667 texture report ONLY. There is deliberately no apply route. */
object ToaObjectTexturePlanTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty() || args.contentEquals(arrayOf("plan"))) { "Usage: plan (object application is deferred)" }
        val proposals = JsonParser().parse(File("tools/n601_material_proposals.json").readText())
            .asJsonObject.getAsJsonObject("object_texture_proposals")
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            val locs = sourceLocs(reader)
            val visited = HashSet<Int>()
            val modelTextures = HashMap<Int, Set<Int>>()
            var rows = 0
            fun report(loc: Int) {
                if (loc == -1 || !visited.add(loc)) return
                val def = ModernObjectDef.decode(loc, reader.file(2, 6, loc) ?: error("OSRS loc $loc missing"))
                check(def.unknownOpcodes.isEmpty()) { "OSRS loc $loc has unknown opcodes ${def.unknownOpcodes}" }
                def.models.distinct().sorted().forEach { model ->
                    val textures = modelTextures.getOrPut(model) {
                        val bytes = reader.file(7, model, 0) ?: error("OSRS model $model missing")
                        ModernModelDecoder.decode(bytes, flattenTextures = true).faceTexture
                            ?.map { it.toInt() and 0xFFFF }?.filter { it != 65535 }?.toSet().orEmpty()
                    }
                    val recoloured = textures.map { texture ->
                        val index = def.retextureFind?.indexOf(texture) ?: -1
                        if (index >= 0) def.retextureReplace!![index] else texture
                    }.toSet()
                    objectRows(loc, model, recoloured, proposals).forEach { println(it); rows++ }
                    if (recoloured.isEmpty()) println("OBJECT_PLAN osrsLoc=$loc osrsModel=$model sourceTexture=NONE -> 667Texture=NOT_APPLICABLE; source face colours, no texture lost")
                }
                def.transforms?.forEach { report(it) }
            }
            locs.forEach { report(it) }
            println("OBJECT_PLAN_ONLY squares=21 static+runtime locs=${visited.size} texturedRows=$rows; no cache mutations; untextured Ba-Ba/Wardens floors require separate review")
        }
    }

    /** All21 source squares AND every runtime loc already mapped for this raid, not only map placements. */
    internal fun sourceLocs(reader: ModernCacheReader): Set<Int> {
        val keys = ModernRegionProbeTool.loadKeys(File(RspsPaths.root("import-source/openrs2-2686/keys.json")))
        val squares = OsrsRegionImportTool.BATCHES.getValue("toa-map-hd").squares +
            listOf(13097, 13098, 13353, 13354, 13355, 13609, 13610, 13611)
        val olderKeys = ModernRegionProbeTool.loadKeys(File(FeroxImportTool.MODERN_KEYS))
        val placed = ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { older ->
            squares.flatMap { square ->
                // Cache 2686 keys.json is empty ([]): its loc files are no longer XTEA-encrypted, so a missing key is the zero key
                // (Xtea.decryptContainer passes zero-key containers through). A wrong key would fail decoding, never silently skip.
                // Squares the 2686 map index does not name (the lobby m52_142) come from cache 2499, the cache they were imported from.
                val locs = runCatching { ModernRegion.load(reader, square, keys[square] ?: IntArray(4)) }
                    .getOrElse { ModernRegion.load(older, square, olderKeys[square] ?: IntArray(4)) }.locs.map { it.id }
                check(locs.isNotEmpty()) { "square $square decoded no locs; refusing incomplete object plan" }
                locs
            }
        }
        val runtime = File("data/cfg/toa/ids.tsv").readLines().mapNotNull { line ->
            val p = line.split('\t'); if (p.size >= 3 && p[0] == "loc") p[1].toInt() else null
        }
        val result = (placed + runtime).toSortedSet()
        val pending = java.util.ArrayDeque(result)
        val skipped = sortedSetOf<Int>()
        while (pending.isNotEmpty()) {
            val loc = pending.removeFirst()
            // A loc whose 2686 definition uses an opcode our decoder does not know (e.g. 100 on 29495) is left exactly as it is
            // now - reported, never guessed and never written.
            val def = runCatching { ModernObjectDef.decode(loc, reader.file(2, 6, loc) ?: error("OSRS loc $loc missing")) }
                .getOrNull()?.takeIf { it.unknownOpcodes.isEmpty() }
            if (def == null) { skipped += loc; continue }
            def.transforms?.filter { it >= 0 }?.forEach { if (result.add(it)) pending.add(it) }
        }
        if (skipped.isNotEmpty()) println("SKIP_UNDECODABLE locs=${skipped.size} ${skipped.joinToString(",")}: unchanged (unknown 2686 loc opcodes)")
        return result - skipped
    }

    internal fun objectRows(loc: Int, model: Int, textures: Set<Int>, proposals: JsonObject): List<String> =
        textures.sorted().map { texture ->
            val proposal = proposals.getAsJsonObject(texture.toString())
            val target = proposal?.get("target")?.takeUnless { it.isJsonNull }?.asInt
            val basis = proposal?.get("basis")?.asString ?: "No proposal; manual material review required"
            "OBJECT_PLAN osrsLoc=$loc osrsModel=$model sourceTexture=$texture -> 667Texture=${target ?: "UNRESOLVED"}; $basis"
        }
}
