package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/** DecisionB native material restoration on CURRENT ToA loc models only. All source textures on a mesh are handled together.
 * 8 leaves /18 sacks /29 grass /30 ferns: approved material matches in tools/n601_material_proposals.json.
 * all:96/97 hieroglyphs and116 skulls use their actual2686 sprites, on native cutout/opaque material templates.
 * Usage: plan|apply [native|all]. Claude owns real cache apply. A divergent/edited/unmapped mesh refuses before the transaction.
 */
object ToaObjectTexturePatchTool {
    val NATIVE = mapOf(8 to 8, 18 to 18, 29 to 29, 30 to 30)
    private val SOURCE_TEXTURES = listOf(96, 97, 116)

    internal fun spriteProgram(template: ByteArray, sprite: Int): ByteArray {
        require(sprite in 0..65535)
        val at = OsrsTextureImportTool.spriteParamOffset(template)
        return template.copyOf().also { it[at] = (sprite ushr 8).toByte(); it[at + 1] = sprite.toByte() }
    }

    internal fun stillMaterial(template: Array<ByteArray>, averageHsl: Int): Array<ByteArray> =
        Array(template.size) { template[it].copyOf() }.also {
            it[7] = byteArrayOf((averageHsl ushr 8).toByte(), averageHsl.toByte())
            it[OsrsTextureImportTool.COL_SPEED_U][0] = 0
            it[OsrsTextureImportTool.COL_SPEED_V][0] = 0
        }

    /** Sprite archive8 is named; archive9 is not. Recover the texture by its exact program and material (no ledger timing gap). */
    private fun stageTextures(game: CacheLibrary, files: CacheLibrary, reader: ModernCacheReader,
                              mutations: MutableList<CacheMutation>, records: MutableMap<String, Int>): Map<Int, Int> {
        val original = game.data(26, 0, 0) ?: error("materials missing")
        check(original.contentEquals(files.data(26, 0, 0))) { "materials diverge" }
        val materials = OsrsTextureImportTool.Materials.decode(original)
        val rows = materials.rows.toMutableList()
        val mappings = linkedMapOf<Int, Int>()
        var nextSprite = maxOf(game.index(8).archiveIds().maxOrNull() ?: -1, files.index(8).archiveIds().maxOrNull() ?: -1) + 1
        var nextTexture = maxOf(rows.size, (game.index(9).archiveIds().maxOrNull() ?: -1) + 1,
            (files.index(9).archiveIds().maxOrNull() ?: -1) + 1)
        for (source in SOURCE_TEXTURES) {
            val tex = OsrsTextureImportTool.readOsrsTexture(reader.file(9, 0, source) ?: error("source texture $source missing"))
            check(tex.direction == 0 && tex.speed == 0) { "source texture $source is animated; static template is not appropriate" }
            val sprite = reader.file(8, tex.sprite, 0) ?: error("source sprite ${tex.sprite} missing")
            val frame = FeroxMapSpriteProbeTool.decodeSprites(sprite).single()
            check(frame.w == 128 && frame.h == 128) { "source sprite canvas changed" }
            // Source byte4=false means cutout; true means opaque (OSRS TextureLoader). Never replace native96/97/116.
            val template = if (tex.transparent) 40 else 8
            val row = stillMaterial(materials.rows[template] ?: error("template $template missing"), tex.colour)
            val programTemplate = game.data(9, template, 0) ?: error("template program $template missing")
            check(programTemplate.contentEquals(files.data(9, template, 0))) { "template program differs" }
            val name = "n601_toa_texture_sprite_$source"
            val previous = game.index(8).archive(name)?.id
            check(previous == files.index(8).archive(name)?.id) { "named source sprite differs between caches" }
            val localSprite = previous ?: nextSprite++
            val program = spriteProgram(programTemplate, localSprite)
            val localTexture: Int
            if (previous == null) {
                check(game.data(8, localSprite, 0) == null && files.data(8, localSprite, 0) == null)
                localTexture = nextTexture++
                require(localTexture < 32768) { "texture id exceeds signed mesh texture field" }
                while (rows.size <= localTexture) rows.add(null)
                rows[localTexture] = row
                mutations += CacheMutation(8, localSprite, 0, sprite, "ToA source sprite ${tex.sprite} for texture$source", groupName = name)
                mutations += CacheMutation(9, localTexture, 0, program, "ToA texture$source ->$localTexture source sprite${tex.sprite}")
            } else {
                check(sprite.contentEquals(game.data(8, localSprite, 0)) && sprite.contentEquals(files.data(8, localSprite, 0))) { "source sprite $source was edited" }
                val matches = materials.rows.indices.filter { i ->
                    val current = materials.rows[i]
                    current != null && current.indices.all { current[it].contentEquals(row[it]) } &&
                        program.contentEquals(game.data(9, i, 0))
                }
                check(matches.size == 1) { "source sprite$source has ${matches.size} matching textures; inspect transaction journal" }
                localTexture = matches.single()
                check(program.contentEquals(files.data(9, localTexture, 0))) { "texture differs" }
            }
            mappings[source] = localTexture
            records["toa_texture:$source"] = localTexture
            records["toa_texture_sprite:$source"] = localSprite
            println("TEXTURE_RESTORE osrs=$source sourceSprite=${tex.sprite} canvas=128x128 -> texture=$localTexture sprite=$localSprite template=$template cutout=${!tex.transparent}")
        }
        val encoded = OsrsTextureImportTool.Materials(BooleanArray(rows.size) { rows[it] != null }, rows.toTypedArray()).encode()
        if (!encoded.contentEquals(original)) mutations += CacheMutation(26, 0, 0, encoded, "ToA source glyph/skull materials", CacheItemProbeTool.sha1(original))
        return mappings
    }

    internal fun restore(source: ModelData, current: ByteArray, kept: Map<Int, Int>, colour: (Int) -> Int): ByteArray {
        val fixed = Rev667ModelEncoder.encode(OsrsTextureImportTool.keepTextures(source, kept, colour))
        val flat = Rev667ModelEncoder.encode(stripTextures(source, colour))
        // Different import routes either flattened every texture or already retained the proven native ones.
        val previous = Rev667ModelEncoder.encode(OsrsTextureImportTool.keepTextures(source, OsrsModelConversion.KEPT_TEXTURES, colour))
        val native = Rev667ModelEncoder.encode(OsrsTextureImportTool.keepTextures(source, OsrsModelConversion.KEPT_TEXTURES + NATIVE, colour))
        check(current.contentEquals(flat) || current.contentEquals(previous) || current.contentEquals(native) || current.contentEquals(fixed)) {
            "current loc mesh differs from source import; refusing to overwrite edited geometry/colours/alpha"
        }
        val next = Rev667ModelDecoder.decode(fixed)
        check(ModelConvertTool.compare(OsrsTextureImportTool.keepTextures(source, kept, colour), next).isEmpty()) { "texture restoration round trip differs" }
        return fixed
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull() ?: "plan"
        val scope = args.getOrNull(1) ?: "native"
        require(args.size <= 2 && mode in setOf("plan", "apply") && scope in setOf("native", "all")) { "Usage: plan|apply [native|all]" }
        val targets = listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE)
        val ids = File("data/cfg/toa/ids.tsv").readLines().mapNotNull { line ->
            val p = line.split('\t'); if (p.size >= 3 && p[0] == "loc") p[1].toInt() to p[2].toInt() else null
        }.toMap()
        val assetMap = File(FeroxImportTool.ASSET_MAP)
        val assetMapSha = CacheItemProbeTool.sha1(assetMap.readBytes())
        val pairs = TextureColourRepairTool.modelPairs(assetMap)
        val known = OsrsFxImportTool.existingFx(assetMap)
        val records = linkedMapOf<String, Int>()
        val mutations = ArrayList<CacheMutation>()
        val seen = HashSet<Int>()
        val game = CacheLibrary(targets[0])
        val files = CacheLibrary(targets[1])
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        try {
            val materials = game.data(26, 0, 0) ?: error("materials missing")
            check(materials.contentEquals(files.data(26, 0, 0))) { "material tables differ between caches" }
            val rows = OsrsTextureImportTool.Materials.decode(materials)
            NATIVE.values.forEach { tex ->
                check(rows.rows[tex] != null) { "native texture $tex has no material" }
                val program = game.data(9, tex, 0) ?: error("native texture $tex missing")
                check(program.contentEquals(files.data(9, tex, 0))) { "texture $tex differs between caches" }
            }
            val kept = OsrsModelConversion.KEPT_TEXTURES + NATIVE + if (scope == "all") stageTextures(game, files, reader, mutations, records) else emptyMap()
            records.forEach { (key, local) -> check(known[key] == null || known[key] == local) { "asset map mapping differs: $key" } }
            fun colour(tex: Int) = osrsTextureAverageHsl(reader.file(9, 0, tex) ?: error("source texture $tex missing"))
            for (osrsLoc in ToaObjectTexturePlanTool.sourceLocs(reader)) {
                val sourceDef = ModernObjectDef.decode(osrsLoc, reader.file(2, 6, osrsLoc) ?: error("source loc $osrsLoc missing"))
                val textured = sourceDef.models.associateWith { mid ->
                    ModernModelDecoder.decode(reader.file(7, mid, 0) ?: error("source model $mid missing"))
                }.filterValues { mesh -> mesh.faceTexture?.any { it.toInt() in kept } == true }
                if (textured.isEmpty()) continue
                val localLoc = ids[osrsLoc] ?: error("textured source loc $osrsLoc has no current ToA mapping")
                val defBytes = game.data(16, localLoc ushr 8, localLoc and 255) ?: error("loc $localLoc missing")
                check(defBytes.contentEquals(files.data(16, localLoc ushr 8, localLoc and 255))) { "loc $localLoc differs between caches" }
                val localModels = Rev667LocType.decode(localLoc, defBytes).allModels.distinct()
                val reached = HashSet<Int>()
                localModels.forEach { local ->
                    val upstream = pairs[local] ?: error("current loc $osrsLoc->$localLoc model $local has no model provenance")
                    val raw = textured[upstream] ?: return@forEach
                    reached += upstream
                    if (!seen.add(local)) return@forEach
                    val current = ModelArchive.read(game, local) ?: error("model $local missing")
                    check(current.contentEquals(ModelArchive.read(files, local))) { "model $local differs between caches" }
                    val used = raw.faceTexture!!.map { it.toInt() }.filter { it in kept }.toSet()
                    val fixed = restore(raw, current, kept, ::colour)
                    println("OBJECT_RESTORE osrsLoc=$osrsLoc localLoc=$localLoc osrsModel=$upstream localModel=$local textures=${used.sorted()} sha1=${CacheItemProbeTool.sha1(current)}")
                    mutations += CacheMutation(7, local, 0, fixed, "N601 native ToA loc model $local from$upstream textures${used.sorted()}", CacheItemProbeTool.sha1(current))
                }
                check(reached.containsAll(textured.keys)) { "loc $osrsLoc source models ${textured.keys - reached} have no current model reference" }
            }
        } finally { game.close(); files.close(); reader.close() }
        check(mutations.isNotEmpty()) { "no native-textured ToA models found; refusing incomplete plan" }
        val tx = CacheTransaction(targets, mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        check(tx.blockingErrors(preflight).isEmpty()) { "blocking preflight errors" }
        if (mode == "plan") { println("DRY_RUN models=${mutations.size}; zero loc/terrain/texture/colour writes outside source textured faces"); return }
        check(CacheItemProbeTool.sha1(assetMap.readBytes()) == assetMapSha) { "asset map changed during planning; rerun" }
        val result = tx.apply(preflight)
        println("APPLIED ${tx.id} applied=${result.applied} skipped=${result.skipped}")
        check(tx.verify().isEmpty()) { "verification failed; journal ${tx.journalDir}" }
        println("VERIFY_OK both caches; journal ${tx.journalDir}")
        val added = records.filterKeys { it !in known }
        if (added.isNotEmpty()) {
            val block = buildString {
                append("\n# N601 ToA source textures96/97/116, cache2686, transaction ${tx.id}\n")
                added.forEach { (key, local) ->
                    val (kind, source) = key.split(':')
                    append("  - fx_kind: $kind\n    upstream_fx_id: $source\n    local_fx_id: $local\n    status: IMPORTED_BY_TOA_OBJECT_TEXTURE_TOOL\n    transaction: ${tx.id}\n")
                }
            }
            assetMap.appendText(block)
            println("ASSET_MAP appended ${added.size} source texture/sprite entries; named sprite recovery also supports interrupted ledger writes")
        }
    }
}
