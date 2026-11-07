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
        val proposals = JsonParser.parseString(File("tools/n601_material_proposals.json").readText())
            .asJsonObject.getAsJsonObject("object_texture_proposals")
        val keys = ModernRegionProbeTool.loadKeys(File(RspsPaths.root("import-source/openrs2-2686/keys.json")))
        val raid = OsrsRegionImportTool.BATCHES.getValue("toa-map-hd").squares
        val squares = raid + listOf(13097, 13098, 13353, 13354, 13355, 13609, 13610, 13611)
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            val locs = squares.flatMap { square ->
                val key = keys[square] ?: error("Missing OSRS key for $square; refusing incomplete object plan")
                ModernRegion.load(reader, square, key).locs.map { it.id }
            }.toSortedSet()
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
                }
                def.transforms?.forEach { report(it) }
            }
            locs.forEach { report(it) }
            println("OBJECT_PLAN_ONLY squares=${squares.size} locs=${visited.size} texturedRows=$rows; no cache mutations; untextured Ba-Ba/Wardens floors require separate review")
        }
    }

    internal fun objectRows(loc: Int, model: Int, textures: Set<Int>, proposals: JsonObject): List<String> =
        textures.sorted().map { texture ->
            val proposal = proposals.getAsJsonObject(texture.toString())
            val target = proposal?.get("target")?.takeUnless { it.isJsonNull }?.asInt
            val basis = proposal?.get("basis")?.asString ?: "No proposal; manual material review required"
            "OBJECT_PLAN osrsLoc=$loc osrsModel=$model sourceTexture=$texture -> 667Texture=${target ?: "UNRESOLVED"}; $basis"
        }
}
