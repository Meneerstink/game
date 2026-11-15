package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.util.Base64

/** Source 493 with the actual 703/711 frame and 1478/1482/1483 rows/tabs. Default PLAN/PREFLIGHT. */
object ClueStashChartCacheTool {
    fun hoverScript(template: ByteArray, control: Int): ByteArray {
        require(control in 0 until 10000)
        val script = ClientScriptEditor.decode(template)
        check(script.intArgs == 2 && script.code.map { it.opcode to it.int } == listOf(33 to 1, 33 to 0, 2105 to 0, 21 to 0))
        val end = ClientScriptEditor.Ins(21)
        script.code.clear()
        // Equivalent of source 1482 removing selected-tab hover hooks: selected tab cannot change its sprites.
        script.code += listOf(ClientScriptEditor.Ins(33, 6), ClientScriptEditor.Ins(1, control), ClientScriptEditor.Ins(8, target = end))
        repeat(3) { i -> script.code += listOf(ClientScriptEditor.Ins(33, i * 2 + 1), ClientScriptEditor.Ins(33, i * 2), ClientScriptEditor.Ins(2105)) }
        script.code += end
        script.intArgs = 7; script.intLocals = 7
        return script.encode().also { check(ClientScriptEditor.decode(it).encode().contentEquals(it)) }
    }

    fun components(root: JsonNode, group: Int, fonts: Map<Int, Int>, sprites: Map<Int, Int>, script: Int): List<OsrsInterfacePortTool.C> {
        fun JsonNode.i(key: String, default: Int = 0) = get(key)?.asInt() ?: default
        fun JsonNode.b(key: String) = get(key)?.asBoolean() ?: false
        val nodes = root["components"]
        val tabs = root["controls"]["tiers"].elements().asSequence().mapIndexed { index, node -> node["tab"].asInt() to (index to node) }.toMap()
        val states = root["controls"]["states"]
        val ids = nodes.fieldNames().asSequence().map(String::toInt).sorted().toList()
        require(ids == (0 until 130).toList()) { "STASH chart source component contract changed" }
        return ids.map { id ->
            val n = nodes[id.toString()]; val type = n.i("type")
            require(type in setOf(0, 3, 4, 5))
            fun hook(over: Boolean): List<Int> {
                tabs[id]?.let { (tier, tab) ->
                    val graphics = states[if (over) "selectedSprites" else "unselectedSprites"]
                    return listOf(script) + tab["sprites"].flatMapIndexed { index, c ->
                        listOf((group shl 16) or c.asInt(), sprites.getValue(graphics[index].asInt()))
                    } + tier
                }
                val field = if (over) "sourceMouseOverSprite" else "sourceMouseLeaveSprite"
                return n[field]?.let { listOf(44, -2147483645, sprites.getValue(it.asInt())) }.orEmpty()
            }
            OsrsInterfacePortTool.C(id, type, n.i("originalX"), n.i("originalY"), n.i("originalWidth"), n.i("originalHeight"),
                n.i("parentId", -1).let { if (it < 0) -1 else it and 65535 },
                n.i("widthMode"), n.i("heightMode"), n.i("xPositionMode"), n.i("yPositionMode"), n.b("isHidden"),
                scrollW = n.i("scrollWidth"), scrollH = n.i("scrollHeight"), noClickThrough = n.b("noClickThrough"),
                sprite = if (type == 5) sprites.getValue(n.i("spriteId")) else -1,
                tiling = n.b("spriteTiling"), opacity = n.i("opacity"),
                font = if (type == 4) fonts.getValue(n.i("fontId")) else -1, text = n["text"]?.asText().orEmpty(),
                lineHeight = n.i("lineHeight"), alignX = n.i("xTextAlignment"), alignY = n.i("yTextAlignment"),
                shadow = n.b("textShadowed"), colour = n.i("textColor"), filled = n.b("fill"),
                ops = n["actions"]?.map { it.asText() }.orEmpty(), opBase = n["name"]?.asText().orEmpty(),
                mouseOver = hook(true), mouseLeave = hook(false))
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 3) { "Usage: <baked.json> <runtime-mapping.tsv> <plan.tsv> [--apply]" }
        val sourceJson = File(args[0]); val runtime = File(args[1]); val plan = File(args[2]); val apply = "--apply" in args
        require(runtime.canonicalFile != plan.canonicalFile && runtime.canonicalFile != sourceJson.canonicalFile)
        val mapper = ObjectMapper(); val root = mapper.readTree(sourceJson)
        check(root["sourceGroup"].asInt() == 493)
        val assetMap = File(OsrsItemImportTool.ASSET_MAP); val existing = OsrsFxImportTool.existingFx(assetMap)
        val mapping = linkedMapOf<Pair<String, Int>, Int>(); val records = mutableListOf<Triple<String, Int, Int>>()
        fun allocate(kind: String, source: Int, next: () -> Int): Int = mapping.getOrPut(kind to source) {
            existing["clue_stash_ui_$kind:$source"] ?: next().also { records += Triple(kind, source, it) }
        }
        val caches = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        try {
            fun next(index: Int) = caches.maxOf { (it.index(index).archiveIds().maxOrNull() ?: -1) + 1 }
            var nextSprite = next(8)
            val group = allocate("interface", 493) { next(3) }
            val script = allocate("script", 1483) { next(12) }
            var nextVarp = caches.maxOf { it.index(2).archive(16)!!.fileIds().maxOrNull() ?: -1 } + 1
            caches.forEach { cache -> cache.index(22).archiveIds().forEach { archive -> cache.index(22).archive(archive)!!.fileIds().forEach { file ->
                val selector = gg.rsmod.game.fs.def.VarbitDef((archive shl 10) or file).also {
                    it.decode(io.netty.buffer.Unpooled.wrappedBuffer(cache.data(22, archive, file)!!))
                }
                nextVarp = maxOf(nextVarp, selector.varp + 1)
            } } }
            val control = allocate("control", 493) { nextVarp }
            check(control in 0 until 10000 && group in 0..65534)
            val hover = mapper.readTree(File(sourceJson.parentFile, "hover.json"))
            val template = Base64.getDecoder().decode(hover["targetBytes"].asText())
            caches.forEach { check(it.data(12, 44, 0)?.contentEquals(template) == true) { "Native close-hover template differs" } }
            val mutations = mutableListOf<CacheMutation>()
            fun add(index: Int, archive: Int, file: Int, bytes: ByteArray, kind: String, source: Int) {
                val old = caches.first().data(index, archive, file)
                check(old == null || existing["clue_stash_ui_$kind:$source"] == if (index == 2) file else archive) { "Unowned STASH UI destination" }
                mutations += CacheMutation(index, archive, file, bytes, "STASH chart $kind $source", old?.let(CacheItemProbeTool::sha1))
            }
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
                val ids = root["components"].elements().asSequence().filter { it["type"].asInt() == 5 }.map { it["spriteId"].asInt() }.toMutableSet()
                ids += root["controls"]["states"]["selectedSprites"].map { it.asInt() }
                ids += root["controls"]["states"]["unselectedSprites"].map { it.asInt() }
                root["components"].forEach { node -> listOf("sourceMouseOverSprite", "sourceMouseLeaveSprite").forEach { field -> node[field]?.let { ids += it.asInt() } } }
                for (id in ids.sorted()) {
                    val bytes = source.file(8, id, 0) ?: error("STASH source sprite $id missing")
                    FeroxMapSpriteProbeTool.decodeSprites(bytes) // Refuse an undecodable source group.
                    add(8, allocate("sprite", id) { nextSprite++ }, 0, bytes, "sprite", id)
                }
            }
            val fonts = mapOf(494 to "p11_full", 495 to "p12_full", 496 to "b12_full", 497 to "q8_full").mapValues { (_, name) ->
                val id = caches.first().index(8).archive(name)?.id ?: error("Native font $name missing")
                check(caches.all { it.index(8).archive(name)?.id == id })
                id
            }
            val spriteMap = mapping.filterKeys { it.first == "sprite" }.mapKeys { it.key.second }
            components(root, group, fonts, spriteMap, script).forEach { component ->
                add(3, group, component.id, OsrsInterfacePortTool.encode(component), "interface", 493)
            }
            add(12, script, 0, hoverScript(template, control), "script", 1483)
            add(2, 16, control, byteArrayOf(0), "control", 493)
            val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations); val preflight = tx.preflight()
            check(tx.blockingErrors(preflight).isEmpty()) { "STASH chart preflight blocked: ${tx.blockingErrors(preflight)}" }
            val text = mapping.entries.joinToString("\n", "# STASH chart ${tx.id}; applied=$apply\n", "\n") { "${it.key.first}\t${it.key.second}\t${it.value}" }
            plan.parentFile?.mkdirs(); plan.writeText(text)
            println("PREFLIGHT ${tx.id} mutations=${mutations.size} group=$group script=$script control=$control sprites=${spriteMap.size}")
            if (!apply) { println("PLAN_ONLY; caches/ledger/runtime mapping unchanged"); return }
            val result = tx.apply(preflight); val errors = tx.verify()
            if (errors.isNotEmpty()) { tx.rollback(); error("STASH chart verification rolled back: $errors") }
            if (records.isNotEmpty()) assetMap.appendText(records.joinToString("", "\n") { (kind, source, id) ->
                "  - fx_kind: clue_stash_ui_$kind\n    upstream_fx_id: $source\n    local_fx_id: $id\n    status: IMPORTED_BY_CLUE_STASH_UI_TOOL\n    transaction: ${tx.id}\n"
            })
            runtime.parentFile?.mkdirs(); runtime.writeText(text)
            println("APPLIED ${tx.id} writes=${result.applied} VERIFY_OK")
        } finally { caches.forEach { it.close() } }
    }
}
