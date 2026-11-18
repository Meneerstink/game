package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.JsonParser
import gg.rsmod.game.fs.NonTransmittedVarbitDefinitionProvider
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.fs.def.VarbitDef
import io.netty.buffer.Unpooled
import java.io.File

/** Exact selected STASH scenery and private selectors. Default: PLAN/PREFLIGHT only. */
object ClueStashCacheTool {
    data class Unit(val source: Int, val forms: List<Int>, val sourceVarbit: Int)

    fun selector(varp: Int, bit: Int): ByteArray {
        require(varp in 0 until 10000 && bit in 0..31)
        return byteArrayOf(1, (varp ushr 8).toByte(), varp.toByte(), bit.toByte(), bit.toByte(), 0)
    }

    fun decodeMapping(text: String): Map<Pair<String, Int>, Int> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val fields = line.split('\t')
            require(fields.size == 3 && fields[0] in setOf("loc", "model", "varbit", "control", "texture", "sprite")) { "Invalid STASH mapping row" }
            (fields[0] to fields[1].toInt()) to fields[2].toInt()
        }.toList().let { rows ->
            require(rows.map { it.first }.distinct().size == rows.size) { "Duplicate STASH mapping identity" }
            rows.toMap()
        }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 3) { "Usage: <stashes.json> <runtime-mapping.tsv> <plan.tsv> [--apply]" }
        val catalogue = File(args[0]); val runtime = File(args[1]); val plan = File(args[2])
        require(catalogue.canonicalFile != runtime.canonicalFile && runtime.canonicalFile != plan.canonicalFile)
        val apply = "--apply" in args
        val root = JsonParser().parse(catalogue.readText()).asJsonObject
        val units = root["units"].asJsonArray.map { value -> value.asJsonObject.let {
            Unit(it["osrsId"].asInt, listOf(it["unbuiltId"].asInt, it["builtId"].asInt), it["sourceVarbit"].asJsonObject["id"].asInt)
        } }.sortedBy { it.source }
        require(units.size in 1..32 && units.map { it.source }.distinct().size == units.size && units.map { it.sourceVarbit }.distinct().size == units.size)
        val old = if (runtime.isFile) decodeMapping(runtime.readText()) else emptyMap()
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val existing = OsrsFxImportTool.existingFx(assetMap)
        val mapping = linkedMapOf<Pair<String, Int>, Int>()
        val records = mutableListOf<Triple<String, Int, Int>>()
        fun allocate(kind: String, source: Int, next: () -> Int): Int = mapping.getOrPut(kind to source) {
            val ledger = existing["clue_stash_$kind:$source"]
            val previous = old[kind to source]
            require(previous == null || ledger == previous) { "STASH mapping/ledger mismatch for $kind:$source" }
            ledger ?: next().also { records += Triple(kind, source, it) }
        }
        val libraries = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        val mutations = mutableListOf<CacheMutation>()
        try {
            fun highest(index: Int, shift: Int): Int = libraries.maxOf { cache ->
                cache.index(index).archiveIds().maxOfOrNull { group ->
                    cache.index(index).archive(group)!!.fileIds().maxOfOrNull { (group shl shift) or it } ?: -1
                } ?: -1
            }
            var nextLoc = highest(16, 8) + 1
            var nextVarbit = highest(22, 10) + 1
            val reservedVarbits = NonTransmittedVarbitDefinitionProvider.varbits.map { it.id }.toSet()
            var nextVarp = libraries.maxOf { it.index(2).archive(16)!!.fileIds().maxOrNull() ?: -1 } + 1
            // Include every physical selector's reference, even a malformed one beyond current config capacity.
            libraries.forEach { cache -> cache.index(22).archiveIds().forEach { group ->
                val archive = cache.index(22).archive(group)!!
                archive.fileIds().forEach { file ->
                    val bytes = archive.file(file)!!.data ?: error("Missing local varbit bytes")
                    val def = VarbitDef((group shl 10) or file).also { it.decode(Unpooled.wrappedBuffer(bytes)) }
                    nextVarp = maxOf(nextVarp, def.varp + 1)
                }
            } }
            check(nextVarp < 10000) { "No append-only transmitted varp available" }
            val controlSource = units.first().source // Stable namespace key, not a copied OSRS varp identity.
            val control = allocate("control", controlSource) { nextVarp }
            check(control in 0 until 10000)
            fun add(index: Int, group: Int, file: Int, bytes: ByteArray, detail: String) {
                mutations.firstOrNull { it.indexId == index && it.groupId == group && it.fileId == file }?.let {
                    check(it.newBytes?.contentEquals(bytes) == true) { "Conflicting STASH writes for $index:$group:$file" }
                    return
                }
                val current = libraries[0].data(index, group, file)
                val occupied = current != null
                val owned = when (index) {
                    16 -> existing.any { (key, id) -> key.startsWith("clue_stash_loc:") && id == (group shl 8 or file) }
                    22 -> existing.any { (key, id) -> key.startsWith("clue_stash_varbit:") && id == (group shl 10 or file) }
                    2 -> existing["clue_stash_control:$controlSource"] == file
                    8 -> existing.any { (key, id) -> key.startsWith("clue_stash_sprite:") && id == group }
                    9 -> existing.any { (key, id) -> key.startsWith("clue_stash_texture:") && id == group }
                    26 -> group == 0 && file == 0 // Existing rows are preserved; only owned/appended rows change below.
                    7, 37 -> existing.any { (key, id) -> key.startsWith("clue_stash_model:") &&
                        ModelArchive.indexOf(7, id) == index && ModelArchive.groupOf(7, id) == group }
                    else -> false
                }
                check(!occupied || owned) { "Refusing to overwrite unowned $index:$group:$file" }
                mutations += CacheMutation(index, group, file, bytes, detail, current?.let(CacheItemProbeTool::sha1))
            }
            add(2, 16, control, byteArrayOf(0), "STASH private control varp $control")
            units.forEachIndexed { bit, unit ->
                val id = allocate("varbit", unit.sourceVarbit) { while (nextVarbit in reservedVarbits) nextVarbit++; nextVarbit++ }
                check(id in 0..65534) { "STASH selector exceeds the loc transform u16" }
                val bytes = selector(control, bit)
                val decoded = VarbitDef(id).also { it.decode(Unpooled.wrappedBuffer(bytes)) }
                check(decoded.varp == control && decoded.startBit == bit && decoded.endBit == bit)
                add(22, id ushr 10, id and 1023, bytes, "STASH source selector ${unit.sourceVarbit} -> $id control=$control bit=$bit")
            }
            val sourceLocs = (units.flatMap { it.forms } + units.map { it.source }).distinct()
            sourceLocs.forEach { source -> allocate("loc", source) { nextLoc++ } }
            val census = ModelNamespaceCensusTool.census(OsrsItemImportTool.TARGETS[0], OsrsItemImportTool.TARGETS[1], assetMap)
            val candidates = OsrsItemImportTool.meshModelCandidates(census)
            val reservedModels = mutableSetOf<Int>()
            val dropped = mutableListOf<String>()
            var nextSprite = libraries.maxOf { it.index(8).archiveIds().maxOrNull() ?: -1 } + 1
            var materials = OsrsTextureImportTool.Materials.decode(libraries[0].data(26, 0, 0) ?: error("Materials table missing"))
            var texturesChanged = false
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
                fun texture(id: Int): Int {
                    mapping["texture" to id]?.let { return it }
                    val tex = OsrsTextureImportTool.readOsrsTexture(source.file(9, 0, id) ?: error("Source texture $id missing"))
                    check(tex.speed == 0) { "STASH texture $id needs an animated adapter" }
                    val frame = FeroxMapSpriteProbeTool.decodeSprites(source.file(8, tex.sprite, 0) ?: error("Source texture sprite missing")).single()
                    check(frame.argb.all { it == 0 || it ushr 24 == 255 && it and 0xffffff != 0 }) {
                        "STASH texture $id needs fractional alpha/opaque-black support"
                    }
                    val image = java.awt.image.BufferedImage(frame.w, frame.h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    for (y in 0 until frame.h) for (x in 0 until frame.w) image.setRGB(x, y, frame.argb[x + y * frame.w])
                    val sprite = OsrsTextureImportTool.opaqueSprite(image)
                    check(FeroxMapSpriteProbeTool.decodeSprites(sprite).single().argb.contentEquals(frame.argb)) { "STASH texture pixels changed" }
                    args.firstOrNull { it.startsWith("--fixtures-out=") }?.substringAfter('=')?.let { path ->
                        val fixture = File(path).apply { mkdirs() }.resolve("texture-$id.sprite")
                        fixture.writeBytes(sprite)
                        java.io.DataOutputStream(fixture.resolveSibling(fixture.name + ".argb").outputStream()).use { stream ->
                            frame.argb.forEach { stream.writeInt(it) }
                        }
                    }
                    val spriteId = allocate("sprite", tex.sprite) { nextSprite++ }
                    check(spriteId in 0..65535)
                    add(8, spriteId, 0, sprite, "STASH source texture sprite ${tex.sprite} -> $spriteId")
                    val textureId = allocate("texture", id) { materials.present.size }
                    val template = materials.rows[40] ?: error("Sprite texture template missing")
                    val row = Array(template.size) { template[it].copyOf() }
                    row[OsrsTextureImportTool.COL_SPEED_U][0] = 0; row[OsrsTextureImportTool.COL_SPEED_V][0] = 0
                    // Js5TextureSource column 18 / JavaToolkit: 1 is binary cutout.
                    // Texture.method8951 preserves palette-zero holes; no alpha plane is needed.
                    row[18][0] = if (frame.argb.any { it == 0 }) 1 else 0
                    row[7] = byteArrayOf((tex.colour ushr 8).toByte(), tex.colour.toByte())
                    val present = materials.present.copyOf(maxOf(materials.present.size, textureId + 1))
                    val rows = materials.rows.copyOf(present.size)
                    present[textureId] = true; rows[textureId] = row
                    materials = OsrsTextureImportTool.Materials(present, rows)
                    val program = libraries[0].data(9, 40, 0)?.copyOf() ?: error("Sprite texture program missing")
                    val offset = OsrsTextureImportTool.spriteParamOffset(program)
                    program[offset] = (spriteId ushr 8).toByte(); program[offset + 1] = spriteId.toByte()
                    add(9, textureId, 0, program, "STASH source texture $id -> $textureId")
                    texturesChanged = true
                    return textureId
                }
                sourceLocs.forEach { id ->
                    val def = ModernObjectDef.decode(id, source.file(2, 6, id) ?: error("Source STASH loc $id missing"))
                    check(def.animationId == -1) { "STASH animation ${def.animationId} requires an explicit adapter" }
                    def.models.distinct().forEach { modelId ->
                        if (mapping.containsKey("model" to modelId)) return@forEach
                        val localId = allocate("model", modelId) {
                            ModelIdAllocator.resolveFromCandidates("clue_stash:$modelId", "scenery", emptyMap(), candidates, reservedModels).also { reservedModels += it }
                        }
                        val bytes = source.file(7, modelId, 0) ?: error("Missing STASH model $modelId")
                        val mesh = runCatching { ModernModelDecoder.decode(bytes) }.getOrElse { Rev667ModelDecoder.decode(bytes) }
                        mesh.faceTexture?.let { faces -> faces.indices.forEach { i ->
                            if (faces[i].toInt() != -1) faces[i] = texture(faces[i].toInt() and 65535).toShort()
                        } }
                        val encoded = Rev667ModelEncoder.encode(mesh)
                        check(ModelConvertTool.compare(mesh, Rev667ModelDecoder.decode(encoded)).isEmpty())
                        add(ModelArchive.indexOf(7, localId), ModelArchive.groupOf(7, localId), 0, encoded, "STASH source model $modelId -> $localId")
                    }
                    val unit = units.firstOrNull { it.source == id }
                    val multiloc = unit?.let {
                        check(def.varbitId == it.sourceVarbit && def.transforms?.toList() == it.forms + -1) { "Source STASH selector/forms drifted" }
                        mapping.getValue("varbit" to it.sourceVarbit) to (it.forms.map { form -> mapping.getValue("loc" to form) } + -1).toIntArray()
                    }
                    val encoded = Rev667LocType.encodeFromModern(def, { mapping.getValue("model" to it) }, dropped, multiloc)
                    val localId = mapping.getValue("loc" to id)
                    check(localId >= 0)
                    val decoded = ObjectDef(localId).also { it.decode(Unpooled.wrappedBuffer(encoded)) }
                    check(decoded.name == def.name.takeUnless { it == "null" }.orEmpty() && decoded.width == def.sizeX && decoded.length == def.sizeY) {
                        "STASH loc $id identity/size mismatch: '${def.name}' ${def.sizeX}x${def.sizeY} -> '${decoded.name}' ${decoded.width}x${decoded.length}"
                    }
                    check(decoded.options.map { it?.takeUnless(String::isBlank) } == def.options.toList())
                    if (multiloc != null) check(decoded.varbit == multiloc.first && decoded.transforms?.toList() == multiloc.second.toList())
                    add(16, localId ushr 8, localId and 255, encoded, "STASH source loc $id -> $localId")
                }
            }
            if (texturesChanged) add(26, 0, 0, materials.encode(), "STASH static texture materials (all pre-existing rows retained)")
            check(dropped.isEmpty()) { "Unsupported STASH fields (no silent loss): ${dropped.distinct()}" }
            val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
            val preflight = tx.preflight()
            check(tx.blockingErrors(preflight).isEmpty()) { "STASH preflight blocked: ${tx.blockingErrors(preflight)}" }
            val text = mapping.entries.joinToString("\n", prefix = "# STASH ${tx.id}; applied=$apply\n", postfix = "\n") { "${it.key.first}\t${it.key.second}\t${it.value}" }
            plan.parentFile?.mkdirs(); plan.writeText(text)
            println("PREFLIGHT ${tx.id} units=${units.size} mutations=${mutations.size} control=$control records=${records.size}")
            if (!apply) { println("PLAN_ONLY; both caches, import ledger and runtime mapping unchanged"); return }
            val result = tx.apply(preflight)
            val errors = tx.verify()
            if (errors.isNotEmpty()) { tx.rollback(); error("STASH verify failed and rolled back: $errors; journal=${result.journalDir}") }
            if (records.isNotEmpty()) assetMap.appendText(records.joinToString("", prefix = "\n") { (kind, id, localId) ->
                "  - fx_kind: clue_stash_$kind\n    upstream_fx_id: $id\n    local_fx_id: $localId\n    status: IMPORTED_BY_CLUE_STASH_TOOL\n    transaction: ${tx.id}\n"
            })
            runtime.parentFile?.mkdirs(); runtime.writeText(text)
            println("APPLIED ${tx.id} writes=${result.applied} VERIFY_OK")
        } finally { libraries.forEach { it.close() } }
    }
}
