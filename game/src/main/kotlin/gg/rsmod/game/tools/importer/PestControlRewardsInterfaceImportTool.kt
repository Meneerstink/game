package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Owner 2026-10-10 ("put void elite in the same shop as normal pest control shop ... sell it with the void knight", chose "edit
 * interface 267"): the Void Knights' reward screen (667 interface 267, "Exchange" on the outpost Void Knights) gets two more item
 * rows under the four Void rows: Elite void top / Elite void robe and the Void Knight deflector (RuneScape Wiki "Void Knight
 * deflector": 150 commendation points).
 *
 * 267 is a scroll layer (267:87, scrollbar 267:88 by clientscript 63 on load) with the Void rows at y 281 / 320 / 358 / 396
 * (title text, an "op" text 16 px below it, the item model left of the title), a separator (267:119 / 120) at y 439 and the
 * four charm rows from y 453 (probe of the 667 cache 2026-10-10, `runInterfaceHookProbeTool layout 267`). The tool:
 *
 *  - moves the separator, the charm texts 71..86 and the charm models 89..92 down by [SHIFT] and makes layer 87's scroll
 *    height [SHIFT] taller;
 *  - adds nine components 121..129, every one a byte copy of a 667 Void row component ([GeHistoryInterfaceImportTool.relocate])
 *    with only its box changed, plus the item model / rotation / zoom of a MODEL copy and the op label of a button copy.
 *
 * The server picks the layout by the component count of 267 in its cache (`PestControlRewards`), so a build against a cache
 * without this tool keeps the 2009 screen.
 *
 * Usage: `./gradlew :game:runPestControlRewardsInterfaceImportTool --args="[--apply]"` with the servers stopped. Without
 * `--apply` it only prints the preflight. A cache that already has [COMPONENT_COUNT] components is left alone.
 */
object PestControlRewardsInterfaceImportTool {
    const val INTERFACE_ID = 267
    const val INDEX_INTERFACES = 3
    const val LEGACY_COMPONENT_COUNT = 121

    const val SCROLL_LAYER = 87

    /** Two rows of 38 px, the Void rows' own pitch (281 -> 320 -> 358 -> 396 -> 434). */
    const val ROW_PITCH = 38
    const val SHIFT = 2 * ROW_PITCH
    const val ROW_ELITE_Y = 434
    const val ROW_DEFLECTOR_Y = ROW_ELITE_Y + ROW_PITCH

    /** Everything at or below the separator under the Void rows. */
    val SHIFTED = listOf(119, 120) + (71..86) + (89..92)

    // ---- new components ----
    const val ELITE_TOP_MODEL = 121
    const val ELITE_TOP_TITLE = 122
    const val ELITE_TOP_BUTTON = 123
    const val ELITE_ROBE_MODEL = 124
    const val ELITE_ROBE_TITLE = 125
    const val ELITE_ROBE_BUTTON = 126
    const val DEFLECTOR_MODEL = 127
    const val DEFLECTOR_TITLE = 128
    const val DEFLECTOR_BUTTON = 129
    const val COMPONENT_COUNT = 130

    /** Item inventory look (667 item defs, `runCacheItemProbeTool` 2026-10-10: op1 model, op4 zoom, op5 xan, op6 yan, op7/8 offsets). */
    class Look(val model: Int, val zoom: Int, val xan: Int, val yan: Int, val offsetX: Int, val offsetY: Int)

    val VOID_TOP = Look(15282, 919, 459, 0, 0, 3) // 8839, the model 267:18 shows
    val VOID_ROBE = Look(15281, 2022, 408, 0, 0, 0) // 8840, the model 267:19 shows
    val ELITE_TOP = Look(60846, 976, 486, 105, 1, 0) // 19785
    val ELITE_ROBE = Look(60839, 1744, 357, 1865, 0, -3) // 19786
    val DEFLECTOR = Look(59990, 720, 282, 1892, -12, 72) // 19711

    /** One new reward slot: the 667 components it copies, where it goes and what it shows. */
    class Slot(
        val model: Int,
        val title: Int,
        val button: Int,
        val modelTemplate: Int,
        val templateLook: Look,
        val look: Look,
        val titleTemplate: Int,
        val buttonTemplate: Int,
        val templateOp: String,
        val op: String,
        val left: Boolean,
        val rowY: Int,
    )

    val SLOTS =
        listOf(
            // Left column copies the Void Knight Mace row (28 / 41), right column the Top row (29 / 42); models the Top (18) and Robe (19).
            Slot(ELITE_TOP_MODEL, ELITE_TOP_TITLE, ELITE_TOP_BUTTON, 18, VOID_TOP, ELITE_TOP, 28, 41, "Void Knight Mace", "Elite Void Top", true, ROW_ELITE_Y),
            Slot(ELITE_ROBE_MODEL, ELITE_ROBE_TITLE, ELITE_ROBE_BUTTON, 19, VOID_ROBE, ELITE_ROBE, 29, 42, "Void Knight Top", "Elite Void Robe", false, ROW_ELITE_Y),
            Slot(DEFLECTOR_MODEL, DEFLECTOR_TITLE, DEFLECTOR_BUTTON, 18, VOID_TOP, DEFLECTOR, 28, 41, "Void Knight Mace", "Void Knight Deflector", true, ROW_DEFLECTOR_Y),
        )

    // Column geometry inside layer 87 (the Void rows: models x 10..13 / 232..237, titles and buttons x 46 / 264, button +16 px).
    private const val LEFT_MODEL_X = 10
    private const val RIGHT_MODEL_X = 234
    private const val LEFT_TEXT_X = 46
    private const val RIGHT_TEXT_X = 264
    private const val MODEL_DY = 3
    private const val BUTTON_DY = 16

    private const val TYPE_LAYER = 0
    private const val TYPE_TEXT = 4
    private const val TYPE_MODEL = 6

    // Legacy component body offsets after the 19-byte header (Component.decode, version 255).
    private const val OFFSET_SCROLL_HEIGHT = 21
    private const val OFFSET_MODEL = 19
    private const val OFFSET_MODEL_FLAGS = 21
    private const val OFFSET_ORIGIN_X = 22
    private const val OFFSET_ORIGIN_Y = 24
    private const val OFFSET_XAN = 26
    private const val OFFSET_YAN = 28
    private const val OFFSET_ZOOM = 32

    private fun u2(
        bytes: ByteArray,
        at: Int,
    ): Int = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun s2(
        bytes: ByteArray,
        at: Int,
    ): Int = u2(bytes, at).let { if (it > Short.MAX_VALUE) it - 65536 else it }

    private fun put2(
        bytes: ByteArray,
        at: Int,
        value: Int,
    ) {
        bytes[at] = (value shr 8).toByte()
        bytes[at + 1] = value.toByte()
    }

    /**
     * A MODEL(6) copy showing [look]: the template's own interface rotation/zoom relate to its item's inventory look, so the same
     * relation is applied to the new item (rotation turned by the item rotations' difference, zoom scaled by the item zooms, origin
     * moved by the offsets). The 667 screen does not show the plain inventory angle (267:18 xan 375 / yan 3 for an item at 459 / 0).
     */
    fun withLook(
        template: ByteArray,
        templateLook: Look,
        look: Look,
    ): ByteArray {
        require((template[OFFSET_MODEL_FLAGS].toInt() and 0x1) == 1) { "template is not an ortho-2d model component" }
        require(u2(template, OFFSET_MODEL) == templateLook.model) { "template model ${u2(template, OFFSET_MODEL)} != ${templateLook.model}" }
        val out = template.copyOf()
        put2(out, OFFSET_MODEL, look.model)
        // N459 (2026-10-30): the origin is the component's 2D projection centre in pixels (client Component.decode: modelOriginX/Y,
        // InterfaceManager adds it to the box centre), NOT the item's 3D inventory offset (xof2d/yof2d). Adding the deflector's
        // offsets (-12, 72) drew the shield 68 px lower and 12 px left - at the left edge of the Spinner charm row, its own
        // box empty. Every copy keeps the template's centre.
        put2(out, OFFSET_ORIGIN_X, s2(template, OFFSET_ORIGIN_X))
        put2(out, OFFSET_ORIGIN_Y, s2(template, OFFSET_ORIGIN_Y))
        put2(out, OFFSET_XAN, Math.floorMod(u2(template, OFFSET_XAN) - templateLook.xan + look.xan, 2048))
        put2(out, OFFSET_YAN, Math.floorMod(u2(template, OFFSET_YAN) - templateLook.yan + look.yan, 2048))
        put2(out, OFFSET_ZOOM, u2(template, OFFSET_ZOOM) * look.zoom / templateLook.zoom)
        return out
    }

    /** [bytes] with its one zero-terminated string [old] replaced by [new] (the op label of a button copy). */
    fun replaceString(
        bytes: ByteArray,
        old: String,
        new: String,
    ): ByteArray {
        val needle = old.toByteArray(Charsets.ISO_8859_1) + 0
        val hits = (0..bytes.size - needle.size).filter { i -> needle.indices.all { bytes[i + it] == needle[it] } }
        require(hits.size == 1) { "'$old' found ${hits.size} times" }
        val at = hits.single()
        return bytes.copyOfRange(0, at) + new.toByteArray(Charsets.ISO_8859_1) + bytes.copyOfRange(at + old.length, bytes.size)
    }

    fun build(current: Map<Int, ByteArray>): Map<Int, ByteArray> {
        require(current.size == LEGACY_COMPONENT_COUNT) { "interface $INTERFACE_ID has ${current.size} components, not the 667 layout ($LEGACY_COMPONENT_COUNT)" }
        fun template(
            id: Int,
            type: Int,
        ): ByteArray {
            val bytes = current[id] ?: error("$INTERFACE_ID:$id missing")
            require(GeHistoryInterfaceImportTool.isLegacyUnnamed(bytes) && GeHistoryInterfaceImportTool.typeOf(bytes) == type) {
                "$INTERFACE_ID:$id is not a legacy type-$type component"
            }
            return bytes
        }
        val out = sortedMapOf<Int, ByteArray>()
        val layer = template(SCROLL_LAYER, TYPE_LAYER).copyOf()
        put2(layer, OFFSET_SCROLL_HEIGHT, u2(layer, OFFSET_SCROLL_HEIGHT) + SHIFT)
        out[SCROLL_LAYER] = layer
        for (id in SHIFTED) {
            val bytes = current[id] ?: error("$INTERFACE_ID:$id missing")
            require(GeHistoryInterfaceImportTool.parent(bytes) == SCROLL_LAYER) { "$INTERFACE_ID:$id is not in the scroll layer" }
            out[id] = GeHistoryInterfaceImportTool.relocate(bytes, y = GeHistoryInterfaceImportTool.y(bytes) + SHIFT)
        }
        for (s in SLOTS) {
            val modelX = if (s.left) LEFT_MODEL_X else RIGHT_MODEL_X
            val textX = if (s.left) LEFT_TEXT_X else RIGHT_TEXT_X
            out[s.model] =
                GeHistoryInterfaceImportTool.relocate(withLook(template(s.modelTemplate, TYPE_MODEL), s.templateLook, s.look), x = modelX, y = s.rowY + MODEL_DY)
            out[s.title] = GeHistoryInterfaceImportTool.relocate(template(s.titleTemplate, TYPE_TEXT), x = textX, y = s.rowY, width = 170)
            out[s.button] =
                GeHistoryInterfaceImportTool.relocate(replaceString(template(s.buttonTemplate, TYPE_TEXT), s.templateOp, s.op), x = textX, y = s.rowY + BUTTON_DY, width = 120)
        }
        val missing = (LEGACY_COMPONENT_COUNT until COMPONENT_COUNT).filter { it !in out }
        check(missing.isEmpty()) { "new components without bytes: $missing" }
        return out
    }

    /**
     * N459: a cache built before the origin fix has the new model slots' projection centre moved by the item offsets (the deflector
     * 267:127 at -12,68 - drawn outside its box). Returns the slots whose origin differs from their template's, with only the
     * origin set back; empty when the cache is right.
     */
    fun repairOrigins(current: Map<Int, ByteArray>): Map<Int, ByteArray> {
        require(current.size == COMPONENT_COUNT) { "interface $INTERFACE_ID has ${current.size} components, not $COMPONENT_COUNT" }
        val out = sortedMapOf<Int, ByteArray>()
        for (s in SLOTS) {
            val template = current[s.modelTemplate] ?: error("$INTERFACE_ID:${s.modelTemplate} missing")
            val bytes = current[s.model] ?: error("$INTERFACE_ID:${s.model} missing")
            require(u2(bytes, OFFSET_MODEL) == s.look.model) { "$INTERFACE_ID:${s.model} model ${u2(bytes, OFFSET_MODEL)} != ${s.look.model}" }
            val x = s2(template, OFFSET_ORIGIN_X)
            val y = s2(template, OFFSET_ORIGIN_Y)
            if (s2(bytes, OFFSET_ORIGIN_X) == x && s2(bytes, OFFSET_ORIGIN_Y) == y) continue
            val fixed = bytes.copyOf()
            put2(fixed, OFFSET_ORIGIN_X, x)
            put2(fixed, OFFSET_ORIGIN_Y, y)
            out[s.model] = fixed
        }
        return out
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val current: Map<Int, ByteArray>
        try {
            val files = library.index(INDEX_INTERFACES).archive(INTERFACE_ID)?.fileIds()?.toList() ?: error("interface $INTERFACE_ID missing")
            current = files.associateWith { f -> library.data(INDEX_INTERFACES, INTERFACE_ID, f) ?: error("$INTERFACE_ID:$f unreadable") }
        } finally {
            library.close()
        }
        listOf(18, 19).forEach { id ->
            val b = current.getValue(id)
            println(
                "  267:$id model=${u2(b, OFFSET_MODEL)} flags=${b[OFFSET_MODEL_FLAGS]} origin=${s2(b, OFFSET_ORIGIN_X)},${s2(b, OFFSET_ORIGIN_Y)} " +
                    "xan=${u2(b, OFFSET_XAN)} yan=${u2(b, OFFSET_YAN)} zoom=${u2(b, OFFSET_ZOOM)}",
            )
        }
        val built: Map<Int, ByteArray>
        if (current.size == COMPONENT_COUNT) {
            built = repairOrigins(current)
            if (built.isEmpty()) {
                println("NO_OP: interface $INTERFACE_ID already has $COMPONENT_COUNT components with the template origins")
                return
            }
            built.keys.forEach { id ->
                val b = current.getValue(id)
                println("REPAIR 267:$id origin ${s2(b, OFFSET_ORIGIN_X)},${s2(b, OFFSET_ORIGIN_Y)} -> ${s2(built.getValue(id), OFFSET_ORIGIN_X)},${s2(built.getValue(id), OFFSET_ORIGIN_Y)}")
            }
        } else {
            val layer = current.getValue(SCROLL_LAYER)
            println("TEMPLATES scrollHeight=${u2(layer, OFFSET_SCROLL_HEIGHT)} -> ${u2(layer, OFFSET_SCROLL_HEIGHT) + SHIFT}")
            built = build(current)
        }
        val mutations =
            built.map { (id, bytes) ->
                CacheMutation(
                    INDEX_INTERFACES,
                    INTERFACE_ID,
                    id,
                    bytes,
                    "interface $INTERFACE_ID:$id",
                    expectedCurrentSha1 = current[id]?.let(CacheItemProbeTool::sha1),
                )
            }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE_ID (${current.size} -> $COMPONENT_COUNT components)")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) {
            println("VERIFY_OK transaction=${transaction.id}")
        } else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
