package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Comp
import java.io.File

/**
 * DIARIES-OSRS (owner 27-11 12:0x: 'als je op 1 diary klikt opent die niet het scherm van osrs'): the OSRS achievement-diary journal,
 * OSRS interface 741 `journalscroll` (Joshua-F/osrs-dumps interface/journalscroll.if3, cache 2686), as 667 interface [INTERFACE].
 * Component numbers are the OSRS ones, so the server code reads like the OSRS source:
 *  - 0 `universe` 512 x 334, centred (abs_centre / abs_centre);
 *  - 1 `com_1` the parchment scroll MODEL 21821 at (239, 160) 55 x 81, modelangle 510,0,0, zoom 579 (converted to a 667 model);
 *  - 2 `title` q8_full black at (53, 56) 380 x 20;
 *  - 3 `textlayer` (48, 84) 404 x 210, the scroll area;
 *  - 4..203 `qj1`..`qj200`: p12_full black, centred, 20 px apart, full layer width;
 *  - 204 `scrollbar` 16 x 210, 44 from the right edge (the 667 scrollbar script draws it);
 *  - 205 `close` OSRS sprite close_buttons,6 (541) 26 x 23, 45 from the right, y 54, op 'Close'.
 * No text has a drop shadow (OSRS default on parchment). Usage: `bash kc_tool.sh DiaryJournalInterfaceTool [--apply]` (servers down).
 */
object DiaryJournalInterfaceTool {
    const val INTERFACE = 1243
    const val OSRS_SCROLL_MODEL = 21821
    /** OSRS close_buttons,6 / close_buttons,7 (hover). */
    val OSRS_SPRITES = listOf(541, 542)
    const val SPRITE_BASE = 9221

    const val UNIVERSE = 0
    const val MODEL = 1
    const val TITLE = 2
    const val TEXTLAYER = 3
    const val LINE_FIRST = 4
    const val LINES = 200
    const val LINE_H = 20
    const val SCROLLBAR = 204
    const val CLOSE = 205
    const val COUNT = 206

    const val W = 512
    const val H = 334
    const val TEXT_W = 404
    const val TEXT_H = 210

    private const val TYPE_LAYER = 0
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5
    private const val TYPE_MODEL = 6

    fun components(fonts: LootKeyInterfaceImportTool.Fonts, scrollModel: Int): List<Comp> {
        val l = ArrayList<Comp>()
        l += Comp(UNIVERSE, TYPE_LAYER, 0, 0, W, H, -1, reposX = 1, reposY = 1)
        l += Comp(MODEL, TYPE_MODEL, 239, 160, 55, 81, UNIVERSE, model = scrollModel, xan = 510, yan = 0, zan = 0, zoom = 579)
        l += Comp(TITLE, TYPE_TEXT, 53, 56, 380, 20, UNIVERSE, font = fonts.q8, colour = 0x000000, alignX = 0, alignY = 0, textShadow = false)
        l += Comp(TEXTLAYER, TYPE_LAYER, 48, 84, TEXT_W, TEXT_H, UNIVERSE)
        for (i in 0 until LINES) {
            l += Comp(LINE_FIRST + i, TYPE_TEXT, 0, i * LINE_H, 0, LINE_H, TEXTLAYER, resizeX = 1, reposX = 1, font = fonts.p12, colour = 0x000000,
                alignX = 1, alignY = 0, textShadow = false)
        }
        l += Comp(SCROLLBAR, TYPE_LAYER, W - 44 - 16, 84, 16, TEXT_H, UNIVERSE,
            onLoad = listOf(DeathsOfficeInterfaceImportTool.SCROLLBAR_SCRIPT, DeathsOfficeInterfaceImportTool.EVENT_COM, (INTERFACE shl 16) or TEXTLAYER))
        l += Comp(CLOSE, TYPE_GRAPHIC, W - 45 - 26, 54, 26, 23, UNIVERSE, graphic = SPRITE_BASE, ops = listOf("Close"))
        require(l.map { it.id } == (0 until COUNT).toList())
        return l
    }

    /** Offset of a model component's u16 model id: version, type, clientcode, x, y, w, h, 4 resize/repos bytes, parent, hidden. */
    private const val MODEL_ID_OFFSET = 1 + 1 + 2 + 8 + 4 + 2 + 1

    /**
     * [DeathsOfficeInterfaceImportTool.encode], with a wide model (archive 37, id > 65535) written the CLUE way: type | 0x40 and a
     * g4 model id instead of the g2 (OsrsInterfacePortTool.encode does the same).
     */
    fun encode(c: Comp): ByteArray {
        if (c.type != TYPE_MODEL || !ModelArchive.isWide(c.model)) return DeathsOfficeInterfaceImportTool.encode(c)
        val narrow = DeathsOfficeInterfaceImportTool.encode(c.copy(model = -1))
        check(narrow[MODEL_ID_OFFSET] == 0xFF.toByte() && narrow[MODEL_ID_OFFSET + 1] == 0xFF.toByte()) { "model id not at offset $MODEL_ID_OFFSET" }
        val out = ByteArray(narrow.size + 2)
        System.arraycopy(narrow, 0, out, 0, MODEL_ID_OFFSET)
        out[1] = (c.type or 0x40).toByte()
        out[MODEL_ID_OFFSET] = (c.model ushr 24).toByte()
        out[MODEL_ID_OFFSET + 1] = (c.model ushr 16).toByte()
        out[MODEL_ID_OFFSET + 2] = (c.model ushr 8).toByte()
        out[MODEL_ID_OFFSET + 3] = c.model.toByte()
        System.arraycopy(narrow, MODEL_ID_OFFSET + 2, out, MODEL_ID_OFFSET + 4, narrow.size - MODEL_ID_OFFSET - 2)
        return out
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val idxIf = DeathsOfficeInterfaceImportTool.INDEX_INTERFACES
        val idxSpr = DeathsOfficeInterfaceImportTool.INDEX_SPRITES
        val idxMdl = DeathsOfficeInterfaceImportTool.INDEX_MODELS
        val assets = OsrsFxImportTool.existingFx(File(OsrsItemImportTool.ASSET_MAP))
        val records = ArrayList<String>()
        val mutations = ArrayList<CacheMutation>()
        val fonts: LootKeyInterfaceImportTool.Fonts
        val currentIf: Map<Int, String>
        val currentSpr = HashMap<Int, String>()
        val model: Int
        val source = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            currentIf = (library.index(idxIf).archive(INTERFACE)?.fileIds()?.toList() ?: emptyList())
                .associateWith { f -> CacheItemProbeTool.sha1(library.data(idxIf, INTERFACE, f)!!) }
            OSRS_SPRITES.indices.forEach { i ->
                library.data(idxSpr, SPRITE_BASE + i, 0)?.let { currentSpr[SPRITE_BASE + i] = CacheItemProbeTool.sha1(it) }
            }
            val known = assets["iface_model:$OSRS_SCROLL_MODEL"]
            model = if (known != null) known else {
                val census = ModelNamespaceCensusTool.census(OsrsItemImportTool.TARGETS[0], OsrsItemImportTool.TARGETS[1], File(OsrsItemImportTool.ASSET_MAP))
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                // Archive 7 (u16) is full: the scroll goes to archive 37 like the CLUE UI models; the live client reads a wide IF3
                // model id when the component type carries flag 0x40 (client c1de063f, see [encode]).
                ModelIdAllocator.resolveFromCandidates("diary_journal:$OSRS_SCROLL_MODEL", "interface", emptyMap(), OsrsItemImportTool.meshModelCandidates(census))
                    .also { records += "iface_model|$OSRS_SCROLL_MODEL|$it" }
            }
            val dropped = ArrayList<String>()
            mutations += CacheMutation(idxMdl, model, 0, OsrsModelConversion.convert(source, OSRS_SCROLL_MODEL, dropped),
                "diary journal scroll model osrs=$OSRS_SCROLL_MODEL -> $model",
                expectedCurrentSha1 = if (known != null) ModelArchive.read(library, model)?.let { CacheItemProbeTool.sha1(it) } else null)
            dropped.distinct().forEach { println("DROPPED $it") }
            OSRS_SPRITES.forEachIndexed { i, osrs ->
                val bytes = source.file(idxSpr, osrs, 0) ?: error("OSRS sprite $osrs missing")
                mutations += CacheMutation(idxSpr, SPRITE_BASE + i, 0, bytes, "sprite osrs:$osrs -> 667:${SPRITE_BASE + i}", expectedCurrentSha1 = currentSpr[SPRITE_BASE + i])
            }
        } finally {
            library.close()
            source.close()
        }
        val comps = components(fonts, model)
        comps.forEach { c ->
            mutations += CacheMutation(idxIf, INTERFACE, c.id, encode(c), "interface $INTERFACE:${c.id} type=${c.type}",
                expectedCurrentSha1 = currentIf[c.id])
        }
        currentIf.keys.filter { it >= COUNT }.sorted().forEach { f ->
            mutations += CacheMutation(idxIf, INTERFACE, f, null, "interface $INTERFACE:$f (old layout)", expectedCurrentSha1 = currentIf[f])
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} model=$model fonts p12=${fonts.p12} q8=${fonts.q8} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.take(20).forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interface $INTERFACE, model $model, sprites $SPRITE_BASE..${SPRITE_BASE + OSRS_SPRITES.size - 1}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
        if (records.isNotEmpty()) {
            val assetMap = File(OsrsItemImportTool.ASSET_MAP)
            val block = StringBuilder()
            records.forEach { r ->
                val (kind, upstream, localId) = r.split('|')
                block.append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $localId\n    status: IMPORTED_BY_DIARY_JOURNAL_TOOL\n    transaction: ${transaction.id}\n")
            }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            println("ASSET_MAP appended ${records.size} model entries")
        }
    }
}
