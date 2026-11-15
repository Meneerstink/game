package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

/** Cache 2686 groups 73/203, including the reward inventory's script-created slots.
 * Default is PREFLIGHT. Source JSON comes from tools/clue/ClueWidgetProbe.java.
 * No IDs are reserved or published until both caches have passed verification.
 */
object ClueInterfaceImportTool {
    const val REWARD_FIRST = 5
    const val REWARD_SLOTS = 12 // cache 2686 config 2/5/141 = [2, 0, 12, 0]
    val GROUPS = listOf(73, 203)


    fun components(group: Int, node: JsonNode, fonts: Map<Int, Int>, models: Map<Int, Int>, sprites: Map<Int, Int>): List<OsrsInterfacePortTool.C> {
        require(group in GROUPS)
        fun JsonNode.int(key: String, default: Int = 0) = get(key)?.asInt() ?: default
        fun JsonNode.flag(key: String) = get(key)?.asBoolean() ?: false
        fun JsonNode.hover(key: String): List<Int> {
            val hook = get(key)?.takeUnless { it.isNull }?.map { it.asInt() }.orEmpty()
            if (hook.isEmpty()) return emptyList()
            require(hook.size == 3 && hook[0] == 44 && hook[1] == -2147483645) { "Unsupported source hover: $hook" }
            return listOf(44, hook[1], sprites.getValue(hook[2]))
        }
        val ids = node.fieldNames().asSequence().map(String::toInt).sorted().toList()
        require(ids == (0 until if (group == 73) 5 else 3).toList())
        val out = ids.map { id ->
            val n = node[id.toString()]
            val type = n.int("type")
            require(type in setOf(0, 4, 5, 6))
            OsrsInterfacePortTool.C(id, type, n.int("originalX"), n.int("originalY"), n.int("originalWidth"), n.int("originalHeight"),
                n.int("parentId", -1).let { if (it < 0) -1 else it and 0xffff },
                n.int("widthMode"), n.int("heightMode"), n.int("xPositionMode"), n.int("yPositionMode"), n.flag("isHidden"),
                scrollW = n.int("scrollWidth"), scrollH = n.int("scrollHeight"), noClickThrough = n.flag("noClickThrough"),
                sprite = if (type == 5) sprites.getValue(n.int("spriteId")) else -1, tiling = n.flag("spriteTiling"), opacity = n.int("opacity"),
                font = if (type == 4) fonts.getValue(n.int("fontId")) else -1, text = n["text"]?.asText().orEmpty(),
                lineHeight = n.int("lineHeight"), alignX = n.int("xTextAlignment"), alignY = n.int("yTextAlignment"), shadow = n.flag("textShadowed"),
                colour = n.int("textColor"), ops = n["actions"]?.map { it.asText() }.orEmpty(), opBase = n["name"]?.asText().orEmpty(),
                model = if (type == 6) models.getValue(n.int("modelId")) else -1,
                originX = n.int("offsetX2d"), originY = n.int("offsetY2d"), // RuneLite names the raw second/third angles Z/Y; 667 reads them as yan/zan.
                xan = n.int("rotationX"), yan = n.int("rotationZ"), zan = n.int("rotationY"),
                zoom = n.int("modelZoom"), ortho = n.flag("orthogonal"),
                mouseOver = n.hover("onMouseOverListener"), mouseLeave = n.hover("onMouseLeaveListener"))
        }.toMutableList()
        if (group == 73) {
            // Source proc 2090: 36x32 items; three columns with left/centre/right anchoring.
            // Row stride = 32 + (container_width - 36*3)/2 = 34 for the 112px source container.
            val parent = out.single { it.id == 3 }
            val stride = 32 + (parent.w - 36 * 3) / 2
            repeat(REWARD_SLOTS) { slot ->
                out += OsrsInterfacePortTool.C(REWARD_FIRST + slot, 5, 0, slot / 3 * stride, 36, 32, 3,
                    0, 0, slot % 3, 0, true, ops = listOf("Examine"))
            }
        }
        return out
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 3) { "Usage: <source-json> <mapping-tsv> <plan-tsv> [--apply]" }
        val sourceJson = File(args[0]); val mappingFile = File(args[1]); val planFile = File(args[2])
        val apply = "--apply" in args
        val root = ObjectMapper().readTree(sourceJson)
        val old = if (mappingFile.isFile) mappingFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }.associate { (it[0] to it[1].toInt()) to it[2].toInt() } else emptyMap()
        val mapping = LinkedHashMap<Pair<String, Int>, Int>()
        val libraries = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        val mutations = mutableListOf<CacheMutation>()
        try {
            val local = libraries.first()
            // Both caches already contain this exact two-argument IF_SETGRAPHIC script.
            // Pin bytes rather than assuming an OSRS script ID means the same operation in 667.
            val hover = ObjectMapper().readTree(File(sourceJson.parentFile, "hover.json"))
            libraries.forEach { cache ->
                check(CacheItemProbeTool.sha1(cache.data(12, 44, 0) ?: error("Close hover script 44 missing")) == hover["targetSha1"].asText()) {
                    "Close hover script 44 differs from reviewed source-compatible bytecode"
                }
            }
            fun next(index: Int) = libraries.maxOf { (it.index(index).archiveIds().maxOrNull() ?: -1) + 1 }
            var nextInterface = next(3); var nextSprite = next(8)
            fun allocate(kind: String, id: Int, fresh: () -> Int): Int = mapping.getOrPut(kind to id) { old[kind to id] ?: fresh() }
            fun add(index: Int, group: Int, file: Int, bytes: ByteArray, description: String) {
                val actualIndex = ModelArchive.indexOf(index, group)
                val actualGroup = ModelArchive.groupOf(index, group)
                val before = local.data(actualIndex, actualGroup, file)
                mutations += CacheMutation(index, group, file, bytes, description,
                    before?.takeIf { !it.contentEquals(bytes) }?.let { CacheItemProbeTool.sha1(it) })
            }
            val census = ModelNamespaceCensusTool.census(OsrsItemImportTool.TARGETS[0], OsrsItemImportTool.TARGETS[1], File(OsrsItemImportTool.ASSET_MAP))
            // Archive 7 is full; the reviewed CLUE client patch adds wide IF3 model references.
            // Claude must install that patch before applying this cache batch.
            val candidates = OsrsItemImportTool.meshModelCandidates(census)
            val reserved = mutableSetOf<Int>()
            val fonts = mapOf(494 to "p11_full", 495 to "p12_full", 496 to "b12_full", 497 to "q8_full").mapValues { (_, name) ->
                local.index(8).archive(name)?.id ?: error("Target font $name missing")
            }
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
                check(source.file(2, 5, 141)!!.contentEquals(byteArrayOf(2, 0, REWARD_SLOTS.toByte(), 0)))
                val modelIds = GROUPS.flatMap { group -> root[group.toString()].elements().asSequence().filter { it["type"].asInt() == 6 }.map { it["modelId"].asInt() }.toList() }.distinct()
                val decoded = modelIds.associateWith { id ->
                    val bytes = source.file(7, id, 0) ?: error("Source model $id missing")
                    runCatching { ModernModelDecoder.decode(bytes, false) }.getOrElse { Rev667ModelDecoder.decode(bytes, false) }
                }
                val textures = decoded.values.flatMap { model -> model.faceTexture?.map { it.toInt() and 0xffff }?.filter { it != 65535 }.orEmpty() }.distinct()
                // OSRS reward chest uses one static, opaque sprite texture. Preserve its pixels.
                // 667 template 40 supplies the existing sprite-op program; source speed zero means no scrolling.
                var materials = OsrsTextureImportTool.Materials.decode(local.data(26, 0, 0) ?: error("Target materials missing"))
                for (id in textures) {
                    val tex = OsrsTextureImportTool.readOsrsTexture(source.file(9, 0, id) ?: error("Source texture $id missing"))
                    check(tex.speed == 0) { "Texture $id requires an animated adapter" }
                    val spriteId = allocate("texture-sprite", tex.sprite) { nextSprite++ }
                    check(spriteId in 0..65535) { "Texture sprite $spriteId exceeds the 667 sprite-op field" }
                    val frame = FeroxMapSpriteProbeTool.decodeSprites(source.file(8, tex.sprite, 0) ?: error("Texture sprite missing")).single()
                    check(frame.argb.all { it ushr 24 == 255 }) { "Texture $id has transparent pixels; an alpha adapter is required" }
                    val image = java.awt.image.BufferedImage(frame.w, frame.h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    for (y in 0 until frame.h) for (x in 0 until frame.w) image.setRGB(x, y, frame.argb[x + y * frame.w] or (0xff shl 24))
                    val spriteBytes = OsrsTextureImportTool.opaqueSprite(image)
                    check(FeroxMapSpriteProbeTool.decodeSprites(spriteBytes).single().argb.contentEquals(frame.argb)) {
                        "Texture ${tex.sprite} pixels changed during conversion"
                    }
                    add(8, spriteId, 0, spriteBytes, "CLUE texture sprite ${tex.sprite}")
                    val textureId = allocate("texture", id) { materials.present.size }
                    val template = materials.rows[40] ?: error("Sprite-op texture template 40 missing")
                    val row = Array(template.size) { template[it].copyOf() }
                    row[OsrsTextureImportTool.COL_SPEED_U][0] = 0; row[OsrsTextureImportTool.COL_SPEED_V][0] = 0
                    row[7] = byteArrayOf((tex.colour ushr 8).toByte(), tex.colour.toByte())
                    val present = materials.present.copyOf(maxOf(materials.present.size, textureId + 1))
                    val rows = materials.rows.copyOf(present.size)
                    present[textureId] = true; rows[textureId] = row
                    materials = OsrsTextureImportTool.Materials(present, rows)
                    val program = local.data(9, 40) ?: error("Texture template program missing")
                    val offset = OsrsTextureImportTool.spriteParamOffset(program)
                    program[offset] = (spriteId ushr 8).toByte(); program[offset + 1] = spriteId.toByte()
                    add(9, textureId, 0, program, "CLUE static OSRS texture $id")
                }
                if (textures.isNotEmpty()) add(26, 0, 0, materials.encode(), "CLUE static texture material")
                val textureMap = mapping.filterKeys { it.first == "texture" }.mapKeys { it.key.second }
                for ((id, model) in decoded) {
                    val localId = allocate("model", id) {
                        ModelIdAllocator.resolveFromCandidates("clue_ui:$id", "interface", emptyMap(), candidates, reserved).also { reserved += it }
                    }
                    model.faceTexture?.let { faces -> faces.indices.forEach { i ->
                        if (faces[i].toInt() != -1) faces[i] = textureMap.getValue(faces[i].toInt() and 0xffff).toShort()
                    } }
                    val bytes = Rev667ModelEncoder.encode(model)
                    check(ModelConvertTool.compare(model, Rev667ModelDecoder.decode(bytes)).isEmpty())
                    add(7, localId, 0, bytes, "CLUE OSRS UI model $id")
                }
                val sprites = GROUPS.flatMap { group -> root[group.toString()].elements().asSequence().flatMap { n ->
                    sequence {
                        if (n["type"].asInt() == 5) yield(n["spriteId"].asInt())
                        listOf("onMouseOverListener", "onMouseLeaveListener").forEach { hook ->
                            n[hook]?.takeUnless { it.isNull }?.let { yield(it[2].asInt()) }
                        }
                    }
                }.toList() }.distinct()
                sprites.forEach { id -> add(8, allocate("sprite", id) { nextSprite++ }, 0, source.file(8, id, 0) ?: error("Sprite $id missing"), "CLUE OSRS sprite $id") }
                val modelMap = mapping.filterKeys { it.first == "model" }.mapKeys { it.key.second }
                val spriteMap = mapping.filterKeys { it.first == "sprite" }.mapKeys { it.key.second }
                GROUPS.forEach { group ->
                    val target = allocate("interface", group) { nextInterface++ }
                    components(group, root[group.toString()], fonts, modelMap, spriteMap).forEach { component ->
                        add(3, target, component.id, OsrsInterfacePortTool.encode(component), "CLUE OSRS interface $group:${component.id}")
                    }
                }
            }
            val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
            val preflight = tx.preflight()
            check(tx.blockingErrors(preflight).isEmpty()) { "CLUE interface preflight blocked: ${tx.blockingErrors(preflight)}" }
            val text = mapping.entries.joinToString("\n", prefix = "# CLUE ${tx.id}; applied=$apply\n", postfix = "\n") { "${it.key.first}\t${it.key.second}\t${it.value}" }
            planFile.parentFile?.mkdirs(); planFile.writeText(text)
            println("PREFLIGHT ${tx.id} mutations=${mutations.size} ${mapping.entries.joinToString { "${it.key}=${it.value}" }}")
            if (!apply) { println("PLAN_ONLY; cache and runtime mapping unchanged"); return }
            val result = tx.apply(preflight)
            val errors = tx.verify()
            if (errors.isNotEmpty()) {
                tx.rollback()
                error("CLUE interface verification failed and rolled back: $errors; journal=${result.journalDir}")
            }
            mappingFile.parentFile?.mkdirs(); mappingFile.writeText(text)
            println("APPLIED ${tx.id} writes=${result.applied} VERIFY_OK")
        } finally { libraries.forEach { it.close() } }
    }
}
