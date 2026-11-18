package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.File

/** Source322: static frame/labels plus native ports of the source's grid/button CS2. Default PREFLIGHT. */
object ClueLightBoxCacheTool {
    val SCRIPTS = listOf(134, 135, 208, 98, 6388, 1335, 1336, 1337, 1339, 1338)
    val SPRITES = (913..936).toSet() + setOf(297, 1040, 310, 311, 312, 313, 172, 315, 314, 173, 2546, 535, 536)
    private val BRANCHES = setOf(6, 7, 8, 9, 10, 31, 32)

    fun script(id: Int, source: JsonNode, template: ByteArray, scripts: Map<Int, Int>,
               sprites: Map<Int, Int>, items: Map<Int, Int>, control: Int, pressSound: Int): ByteArray {
        val out = ClientScriptEditor.decode(template)
        check(out.switchTables().isEmpty())
        out.code.clear()
        out.intLocals = source["localIntCount"].asInt(); out.stringLocals = source["localStringCount"].asInt()
        out.intArgs = source["intStackCount"].asInt(); out.stringArgs = source["stringStackCount"].asInt()
        out.longLocals = 0; out.longArgs = 0
        if (id == 1338) {
            // Redraw after native resize too, retaining source1339's integer margins/item icon path.
            out.code += listOf(ClientScriptEditor.Ins(0, scripts.getValue(1339)), ClientScriptEditor.Ins(33, 0),
                ClientScriptEditor.Ins(3, string = "i"), ClientScriptEditor.Ins(33, 0), ClientScriptEditor.Ins(2427),
                ClientScriptEditor.Ins(33, 0), ClientScriptEditor.Ins(40, scripts.getValue(1339)), ClientScriptEditor.Ins(21))
        } else {
            val ops = source["instructions"].map { it.asInt() }
            val operands = source["intOperands"].map { it.asInt() }
            require(ops.size == operands.size && source["stringOperands"].size() == ops.size)
            val removed = ops.indices.filter { ops[it] == 100 }.map { i ->
                // rev237 creates with four ints; native667 CC_CREATE150 consumes three.
                require(i > 0 && ops[i - 1] == 0 && operands[i - 1] == 0)
                i - 1
            }.toSet()
            val instructions = linkedMapOf<Int, ClientScriptEditor.Ins>()
            ops.indices.filter { it !in removed }.forEach { i ->
                val op = ops[i]
                var value = operands[i]
                if (op == 40) value = scripts.getValue(value)
                if (op == 1) { require(id == 1339 && value == 1356); value = control }
                if (op == 0) {
                    if (id in setOf(134, 135, 208, 98, 6388) && value in SPRITES) value = sprites.getValue(value)
                    if (id == 1339 && value in setOf(20356, 20357)) value = items.getValue(value)
                    if (id == 1336 && value == 1337) value = scripts.getValue(1337)
                    if (id == 1336 && value == 576) value = pressSound
                }
                instructions[i] = ClientScriptEditor.Ins(when (op) { 100 -> 150; 102 -> 152; else -> op }, value,
                    if (op == 3) source["stringOperands"][i].asText() else null)
            }
            instructions.forEach { (i, instruction) ->
                if (ops[i] in BRANCHES) instruction.target = instructions.getValue(i + 1 + operands[i])
            }
            out.code += instructions.values
        }
        return out.encode().also { check(ClientScriptEditor.decode(it).encode().contentEquals(it)) }
    }

    /** Append only the proven native hook suffix; the shared IF3 encoder remains unchanged. */
    fun encode(c: OsrsInterfacePortTool.C, hooks: Map<Int, List<Int>>, triggers: List<Int> = emptyList()): ByteArray {
        require(c.mouseOver.isEmpty() && c.mouseLeave.isEmpty() && hooks.keys.all { it in 0..19 })
        val raw = OsrsInterfacePortTool.encode(c)
        check(raw.takeLast(25).all { it == 0.toByte() })
        val bytes = ByteArrayOutputStream(); bytes.write(raw, 0, raw.size - 25)
        fun p4(value: Int) { repeat(4) { shift -> bytes.write(value ushr (24 - shift * 8) and 255) } }
        repeat(20) { slot ->
            val args = hooks[slot].orEmpty(); bytes.write(args.size)
            args.forEach { bytes.write(0); p4(it) }
        }
        bytes.write(triggers.size); triggers.forEach(::p4)
        repeat(4) { bytes.write(0) }
        return bytes.toByteArray()
    }

    fun components(root: JsonNode, fonts: Map<Int, Int>, sprites: Map<Int, Int>): List<OsrsInterfacePortTool.C> {
        val nodes = root["322"]
        require(nodes.fieldNames().asSequence().map(String::toInt).sorted().toList() == (0..43).toList())
        fun JsonNode.i(key: String, default: Int = 0) = get(key)?.asInt() ?: default
        fun JsonNode.b(key: String) = get(key)?.asBoolean() ?: false
        return (0..43).map { id ->
            val n = nodes[id.toString()]; val type = n.i("type")
            require(type in setOf(0, 3, 4, 5))
            OsrsInterfacePortTool.C(id, type, n.i("originalX"), n.i("originalY"), n.i("originalWidth"), n.i("originalHeight"),
                n.i("parentId", -1).let { if (it < 0) -1 else it and 65535 },
                n.i("widthMode"), n.i("heightMode"), n.i("xPositionMode"), n.i("yPositionMode"), n.b("isHidden"),
                scrollW = n.i("scrollWidth"), scrollH = n.i("scrollHeight"), noClickThrough = n.b("noClickThrough"),
                sprite = if (type == 5) sprites.getValue(n.i("spriteId")) else -1, tiling = n.b("spriteTiling"), opacity = n.i("opacity"),
                font = if (type == 4) fonts.getValue(n.i("fontId")) else -1, text = n["text"]?.asText().orEmpty(),
                lineHeight = n.i("lineHeight"), alignX = n.i("xTextAlignment"), alignY = n.i("yTextAlignment"),
                shadow = n.b("textShadowed"), colour = n.i("textColor"), filled = n.b("filled"),
                ops = n["actions"]?.map { it.asText() }.orEmpty(), opBase = n["name"]?.asText().orEmpty())
        }
    }

    fun hooks(component: Int, group: Int, scripts: Map<Int, Int>, sprites: Map<Int, Int>): Map<Int, List<Int>> {
        fun hash(child: Int) = (group shl 16) or child
        return when (component) {
            3 -> mapOf(0 to listOf(scripts.getValue(1338), hash(3)), 5 to listOf(scripts.getValue(1339), hash(3)))
            in 8..15 -> mapOf(0 to listOf(scripts.getValue(134), hash(component)),
                1 to listOf(scripts.getValue(135), hash(component)), 2 to listOf(scripts.getValue(134), hash(component)),
                9 to listOf(scripts.getValue(1336), hash(component)))
            35 -> mapOf(1 to listOf(44, -2147483645, sprites.getValue(536)), 2 to listOf(44, -2147483645, sprites.getValue(535)))
            in 36..43 -> mapOf(1 to listOf(scripts.getValue(1335), hash(component), -1, 0xffffff),
                2 to listOf(scripts.getValue(1335), hash(component), -1, 0xff981f))
            else -> emptyMap()
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 5) { "Usage: <baked.json> <scripts.json> <item-mapping.tsv> <runtime.tsv> <plan.tsv> [--apply]" }
        val mapper = ObjectMapper(); val root = mapper.readTree(File(args[0])); val sourceScripts = mapper.readTree(File(args[1]))
        val runtime = File(args[3]); val plan = File(args[4]); val apply = "--apply" in args
        require(runtime.canonicalFile != plan.canonicalFile)
        val items = File(args[2]).readLines().filter { it.isNotBlank() && !it.startsWith('#') }.associate {
            val fields = it.split('\t'); fields[0].toInt() to fields[1].toInt()
        }
        check(items.keys.containsAll(setOf(20356, 20357)))
        val assetMap = File(OsrsItemImportTool.ASSET_MAP); val existing = OsrsFxImportTool.existingFx(assetMap)
        if (apply) {
            val ownedItems = ImportBatchOrchestrator.readExistingMapping(assetMap)
            check(listOf(20356, 20357).all { ownedItems.itemLocalIdBySourceIdentity["upstream_item:$it"] == items[it] }) { "Apply requires both applied lamp items, never preflight proposals" }
        }
        val caches = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        try {
            val mapping = linkedMapOf<Pair<String, Int>, Int>(); val records = mutableListOf<Triple<String, Int, Int>>()
            fun next(index: Int) = caches.maxOf { (it.index(index).archiveIds().maxOrNull() ?: -1) + 1 }
            fun allocate(kind: String, source: Int, fresh: () -> Int): Int = mapping.getOrPut(kind to source) {
                existing["clue_lightbox_$kind:$source"] ?: fresh().also { records += Triple(kind, source, it) }
            }
            val group = allocate("interface", 322) { next(3) }
            var nextScript = next(12); var nextSprite = next(8)
            val scriptMap = SCRIPTS.associateWith { id -> allocate("script", id) { nextScript++ } }
            val spriteMap = SPRITES.sorted().associateWith { id -> allocate("sprite", id) { nextSprite++ } }
            var nextVarp = caches.maxOf { it.index(2).archive(16)!!.fileIds().maxOrNull() ?: -1 } + 1
            caches.forEach { cache -> cache.index(22).archiveIds().forEach { archive -> cache.index(22).archive(archive)!!.fileIds().forEach { file ->
                val varbit = gg.rsmod.game.fs.def.VarbitDef((archive shl 10) or file).also {
                    it.decode(io.netty.buffer.Unpooled.wrappedBuffer(cache.data(22, archive, file)!!))
                }
                nextVarp = maxOf(nextVarp, varbit.varp + 1)
            } } }
            val control = allocate("control", 1356) { nextVarp }; check(control in 0 until 10000 && group in 0..65534)
            val template = caches.first().data(12, 44, 0) ?: error("Native IF_SETGRAPHIC template missing")
            check(ClientScriptEditor.decode(template).code.map { it.opcode to it.int } == listOf(33 to 1, 33 to 0, 2105 to 0, 21 to 0))
            val mutations = mutableListOf<CacheMutation>()
            fun add(index: Int, archive: Int, file: Int, bytes: ByteArray, kind: String, source: Int) {
                val old = caches.first().data(index, archive, file)
                check(old == null || existing["clue_lightbox_$kind:$source"] == if (index == 2) file else archive)
                mutations += CacheMutation(index, archive, file, bytes, "CLUE light box $kind $source", old?.let(CacheItemProbeTool::sha1))
            }
            var pressSound = -1
            var completeSound = -1
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
                SPRITES.forEach { id -> add(8, spriteMap.getValue(id), 0, source.file(8, id, 0) ?: error("Missing source sprite$id"), "sprite", id) }
                var nextSound = next(4)
                for (id in listOf(576, 580)) {
                    val sound = source.file(4, id, 0) ?: error("Source light box sound$id missing")
                    val local = if (caches.all { it.data(4, id, 0)?.contentEquals(sound) == true }) id else {
                        allocate("sound", id) { nextSound++ }.also { add(4, it, 0, sound, "sound", id) }
                    }
                    mapping["sound" to id] = local
                    if (id == 576) pressSound = local else completeSound = local
                }
            }
            val fonts = mapOf(494 to "p11_full", 495 to "p12_full", 496 to "b12_full", 497 to "q8_full").mapValues { (_, name) ->
                val id = caches.first().index(8).archive(name)?.id ?: error("Font$name missing")
                check(caches.all { it.index(8).archive(name)?.id == id }); id
            }
            components(root, fonts, spriteMap).forEach { component ->
                add(3, group, component.id, encode(component, hooks(component.id, group, scriptMap, spriteMap),
                    if (component.id == 3) listOf(control) else emptyList()), "interface", 322)
            }
            SCRIPTS.forEach { id -> add(12, scriptMap.getValue(id), 0,
                script(id, sourceScripts[id.toString()], template, scriptMap, spriteMap, items, control, pressSound), "script", id) }
            add(2, 16, control, byteArrayOf(0), "control", 1356)
            mapping["item" to 20356] = items.getValue(20356); mapping["item" to 20357] = items.getValue(20357)
            mapping["sound" to 576] = pressSound
            mapping["sound" to 580] = completeSound
            val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations); val preflight = tx.preflight()
            check(tx.blockingErrors(preflight).isEmpty()) { "Light box preflight blocked: ${tx.blockingErrors(preflight)}" }
            val text = mapping.entries.joinToString("\n", "# CLUE light box ${tx.id}; applied=$apply\n", "\n") { "${it.key.first}\t${it.key.second}\t${it.value}" }
            plan.parentFile?.mkdirs(); plan.writeText(text)
            println("PREFLIGHT ${tx.id} mutations=${mutations.size} group=$group control=$control scripts=${scriptMap.size} sprites=${spriteMap.size}")
            if (!apply) { println("PLAN_ONLY; caches/ledger/runtime mapping unchanged"); return }
            val result = tx.apply(preflight); val errors = tx.verify()
            if (errors.isNotEmpty()) { tx.rollback(); error("Light box verification rolled back: $errors") }
            if (records.isNotEmpty()) assetMap.appendText(records.joinToString("", "\n") { (kind, source, id) ->
                "  - fx_kind: clue_lightbox_$kind\n    upstream_fx_id: $source\n    local_fx_id: $id\n    status: IMPORTED_BY_CLUE_LIGHTBOX_TOOL\n    transaction: ${tx.id}\n"
            })
            runtime.parentFile?.mkdirs(); runtime.writeText(text)
            println("APPLIED ${tx.id} writes=${result.applied} VERIFY_OK")
        } finally { caches.forEach { it.close() } }
    }
}
