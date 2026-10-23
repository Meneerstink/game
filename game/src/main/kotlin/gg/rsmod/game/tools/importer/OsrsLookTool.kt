package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Gives existing rev-667 items the look of their OSRS counterpart - inventory, worn and chathead meshes, the icon camera,
 * recolours, lighting - and keeps everything else of the 667 item byte for byte (id, name, menu options, cost, params,
 * note/lend links). Stats, equip slot and plugins live in `items.yml` / plugin code and are not touched, except the three
 * hide flags (`remove_head` / `remove_beard` / `remove_arms`), which belong to the look: they follow the OSRS wearPos2/3
 * exactly as [OsrsItemImportTool] writes them for imported items (6 = arms, 8 = hair, 11 = jaw).
 *
 * Why (owner bug list 2026-10-03, measured on both caches 2026-10-04 with tools\cache667 + scratchpad decoders):
 *  * E1 - the plain Max cape 20767 / Max hood 20768 (sold by the Max npc) still drew the 2011 RuneScape max cape (models
 *    65262 / 65300 / 65322, a 667 recolour + retexture table), while every other max cape on the server is the OSRS one. The
 *    OSRS Max cape 13280 / Max hood 13281 meshes (29630 / 29616 / 29624, 29629 / 29614 / 29623 / 29626 / 29628) are ALREADY in
 *    both caches: the importer mapped them for the imbued god max capes (21776 / 21778 -> 23048 / 23049, which are the same
 *    meshes plus a recolour). So the cape needs no model write at all, only the two item definitions.
 *  * B6 / F5 - "OSRS armour invisible at the hands/wrists" with Eclipse moon chestplate 23102 + Barrows gloves 7462. OSRS
 *    splits the arm at mid-forearm: every OSRS body ends there (Eclipse 50901: forearm labels 19/22 end at y -114 = -456
 *    fine, all skin colour 4550) and the HANDS slot draws the lower forearm - the OSRS Barrows gloves mesh 13307 is a tan
 *    gauntlet cuff (colour 8656) over y -456..-420 plus the hand. Rev 667 remodelled the same model id as a wrist ring
 *    (-424..-396) because its arms kit draws the forearm. Under an OSRS body the lower forearm is therefore the bare skin
 *    piece of #106 ([WornForearm], drawn as designed - it is skin, not armour) with a thin 667 cuff at the wrist, where
 *    OSRS shows the gauntlet. Same for every 667-native hands item that exists in OSRS under the same id (the meshes share
 *    the id, OSRS keeps the 2007 mesh); the high-level ones are listed in [LOOKS]. Their OSRS meshes are converted like an
 *    imported glove ([OsrsModelConversion] onto the 667 body, [OsrsWristFit] around the 667 forearm) into NEW model ids -
 *    the 667 ids are shared with other items (13307 is every Recipe for Disaster glove) and stay as they are.
 *
 * Models: an upstream mesh the asset map already maps (role `model`, as the importer writes) is reused as it is in the cache;
 * any other is converted into a census-proven free id and recorded in the asset map. One transaction on both caches;
 * items.yml is rewritten only after the transaction verified. A second run finds nothing to change.
 *
 * Usage: `[--apply]` - without it only the plan and the preflight run.
 */
object OsrsLookTool {
    private const val INDEX_ITEM = 19
    private const val INDEX_MODEL = 7

    /** Asset-map role of a converted upstream mesh - the importer's own role, so both share one mapping per mesh. */
    const val MODEL_ROLE = "model"

    /** [inventoryOnly]: only the inventory icon (mesh, camera, colours, lighting) changes - worn meshes and hide flags stay 667. */
    data class Look(val local: Int, val localName: String, val upstream: Int, val upstreamName: String, val why: String, val inventoryOnly: Boolean = false)

    /**
     * The 667 items that get their OSRS look. Hands items: the high-level 667-native hands items that exist in OSRS under the
     * same id (items.yml equip_slot 9 x OSRS wearPos1 9, same id and name); Dragon gloves 7461 and the lower Recipe for Disaster
     * gloves, Infinity gloves and d'hide vambraces are not high level and are left out (AGENTS.md 1a).
     */
    val LOOKS: List<Look> =
        listOf(
            Look(20767, "Max cape", 13280, "Max cape", "E1: plain max cape drew the 2011 RS max cape"),
            Look(20768, "Max hood", 13281, "Max hood", "E1: max hood of the same set"),
            // Barrows gloves 7462, Void knight gloves 8842, Regen bracelet 11133 had the OSRS look here (B6, 2026-10-03); its converted
            // hand sat inside the 667 forearm (owner 2026-10-06: "when unequiping barrows gloves my hands are visible"). Owner choice
            // 2026-10-06 "Original 667 look": their 667 item definitions are restored (CacheJournalRestoreTool, tx-20261003-235150).
        )

    /*
     * N242 gave the 116 classic potion doses the OSRS corked vial here (potionLooks); the owner reversed it the same evening (N254,
     * "waarom hebben alle potions een kurk nu ineens": every potion on the 2011 open vial). The journal restored those items and
     * PotionVialTool put the imported potions on the 2011 vial too, so this tool no longer relooks potions.
     */

    /** The inventory model (opcode 1) of a raw 667 item definition, or null. */
    fun inventoryModel(bytes: ByteArray): Int? = ItemDefCodec.describeOpcodes(bytes).firstOrNull { it.startsWith("1=") }?.substringAfter('=')?.toIntOrNull()

    /** Rev-667 ObjType model opcodes: inventory, worn male/female 0-2, chathead male/female 0-1. */
    val MODEL_OPCODES = setOf(1, 23, 24, 25, 26, 78, 79, 90, 91, 92, 93)

    /** Inventory icon camera: zoom, x/y angle, x/y offset, z angle. */
    val CAMERA_OPCODES = setOf(4, 5, 6, 7, 8, 95)

    /** Recolour, retexture, resize, ambient, contrast, worn y offsets. */
    val LOOK_DETAIL_OPCODES = setOf(40, 41, 110, 111, 112, 113, 114, 125, 126)

    /** Every opcode a relook may change; all others must come out byte-identical. */
    val LOOK_OPCODES = MODEL_OPCODES + CAMERA_OPCODES + LOOK_DETAIL_OPCODES

    /** Rev-667 opcode -> upstream model id for every mesh of [def]'s look (the same opcodes [OsrsItemImportTool.encodeItem] writes). */
    fun lookModels(def: ModernItemDef): Map<Int, Int> {
        val out = LinkedHashMap<Int, Int>()
        if (def.inventoryModel > 0) out[1] = def.inventoryModel
        listOf(
            23 to def.maleModel0, 24 to def.maleModel1, 25 to def.femaleModel0, 26 to def.femaleModel1,
            78 to def.maleModel2, 79 to def.femaleModel2, 90 to def.maleHeadModel, 91 to def.femaleHeadModel,
            92 to def.maleHeadModel2, 93 to def.femaleHeadModel2,
        ).filter { it.second >= 0 }.forEach { (code, id) -> out[code] = id }
        return out
    }

    /** Icon camera opcodes of [def] as the unsigned shorts the 667 stream carries. */
    fun camera(def: ModernItemDef): Map<Int, Int> {
        val out = linkedMapOf(4 to def.zoom2d, 5 to def.xan2d, 6 to def.yan2d, 7 to def.xOffset2d, 8 to def.yOffset2d)
        if (def.zan2d != 0) out[95] = def.zan2d
        return out.mapValues { it.value and 0xFFFF }
    }

    /** Opcode -> payload as [ItemDefCodec.describeOpcodes] renders it. */
    fun payloads(bytes: ByteArray): Map<Int, String> =
        ItemDefCodec.describeOpcodes(bytes).associate { it.substringBefore('=').toInt() to it.substringAfter('=') }

    /** Every opcode a relook must leave alone, in stream order (the check that nothing else changed). */
    fun untouched(bytes: ByteArray): List<String> = ItemDefCodec.describeOpcodes(bytes).filter { it.substringBefore('=').toInt() !in LOOK_OPCODES }

    /** Opcode 40 payload in [ItemDefCodec.describeOpcodes] form for [def]'s recolour table, or null when it has none. */
    fun recolourPayload(def: ModernItemDef): String? {
        if (def.colorFind.isEmpty()) return null
        val sb = StringBuilder("0x").append("%02x".format(def.colorFind.size))
        def.colorFind.indices.forEach { sb.append("%04x%04x".format(def.colorFind[it] and 0xFFFF, def.colorReplace[it] and 0xFFFF)) }
        return sb.toString()
    }

    /**
     * [current] (a rev-667 item stream) with the look of [upstream]: its meshes as [localModel] maps them, its camera, and the
     * 667 look details it does not have removed. Fails instead of guessing where the upstream look needs an opcode this codec
     * cannot add (a recolour table that differs from the 667 one, retexture, resize, non-zero lighting or worn offsets).
     */
    fun relook(
        current: ByteArray,
        upstream: ModernItemDef,
        inventoryOnly: Boolean = false,
        localModel: (Int) -> Int,
    ): ByteArray {
        val present = payloads(current)
        val name = upstream.name
        val shorts = LinkedHashMap<Int, Int>()
        lookModels(upstream).filterKeys { !inventoryOnly || it == 1 }.forEach { (code, model) -> shorts[code] = localModel(model) }
        shorts += camera(upstream)
        val removed = HashSet<Int>()
        (MODEL_OPCODES + CAMERA_OPCODES).filterTo(removed) { it in present && it !in shorts && (!inventoryOnly || it !in MODEL_OPCODES || it == 1) }

        val recolour = recolourPayload(upstream)
        // N242: a recolour table of the same size is rewritten in place (Magic potion: OSRS liquid colour 09dc, 667 9240).
        var recolourBytes: ByteArray? = null
        if (recolour == null) {
            if (40 in present) removed.add(40)
        } else if (present[40] != recolour) {
            check(present[40]?.length == recolour.length) { "$name: OSRS recolour table $recolour differs in size from the 667 one ${present[40]}; not supported" }
            recolourBytes = recolour.removePrefix("0x").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        check(upstream.textureFind.isEmpty()) { "$name: OSRS retexture tables use OSRS texture ids and are not mapped" }
        if (41 in present) removed.add(41)
        check(upstream.resizeX == 128 && upstream.resizeY == 128 && upstream.resizeZ == 128) { "$name: OSRS resize is not supported" }
        listOf(110, 111, 112).filterTo(removed) { it in present }
        check(upstream.maleOffset == 0 && upstream.femaleOffset == 0) { "$name: OSRS worn offsets are not supported" }
        listOf(125, 126).filterTo(removed) { it in present }
        val ambient = OsrsItemImportTool.rev667Ambient(upstream.ambient, upstream.contrast, OsrsItemImportTool.ITEM_CONTRAST_BASE)
        val contrast = OsrsItemImportTool.rev667Contrast(upstream.contrast)
        // N242: a lighting byte the 667 item lacks or has another value is written (one-byte opcodes 113 ambient / 114 contrast).
        val lighting = LinkedHashMap<Int, Int>()
        for ((code, value) in listOf(113 to ambient, 114 to contrast)) {
            if (value != 0) {
                if (present[code] != "0x%02x".format(value and 0xFF)) lighting[code] = value
            } else if (code in present) {
                removed.add(code)
            }
        }

        var out = ItemDefCodec.cloneWithOverrides(current, emptyMap(), shortOverrides = shorts, removedOpcodes = removed)
        lighting.forEach { (code, value) ->
            val at = ItemDefCodec.payloadOffsets(out).singleOrNull { it.first == code }?.second
            out =
                if (at != null) {
                    out.copyOf().also { it[at] = value.toByte() }
                } else {
                    out.copyOf(out.size - 1) + byteArrayOf(code.toByte(), value.toByte(), 0)
                }
            check(payloads(out)[code] == "0x%02x".format(value and 0xFF)) { "$name: lighting $code rewrite failed" }
        }
        recolourBytes?.let { table ->
            val at = ItemDefCodec.payloadOffsets(out).single { it.first == 40 }.second
            table.copyInto(out, at)
            check(payloads(out)[40] == recolour) { "$name: recolour rewrite failed" }
        }
        check(untouched(out) == untouched(current)) { "$name: the relook changed more than the look opcodes" }
        return out
    }

    /** The `items.yml` hide flags [def] has in OSRS (wearPos2/3: 6 = arms, 8 = hair, 11 = jaw), as the importer writes them. */
    fun hideFlags(def: ModernItemDef): Map<String, Boolean> {
        val hidden = setOf(def.wearPos2, def.wearPos3)
        return mapOf("remove_head" to (8 in hidden), "remove_beard" to (11 in hidden), "remove_arms" to (6 in hidden))
    }

    private val YML_ID = Regex("""^- id: (\d+)\s*$""")
    private val YML_FLAG = Regex("""^(\s+)(remove_head|remove_beard|remove_arms): (true|false)\s*$""")

    /** [lines] of items.yml with the hide flags of the items in [wanted] set; returns the lines and "id flag -> value" changes. */
    fun rewriteFlags(
        lines: List<String>,
        wanted: Map<Int, Map<String, Boolean>>,
    ): Pair<List<String>, List<String>> {
        val out = ArrayList<String>(lines.size)
        val changed = ArrayList<String>()
        var current: Int? = null
        for (line in lines) {
            YML_ID.matchEntire(line)?.let { current = it.groupValues[1].toInt() }
            val match = YML_FLAG.matchEntire(line)
            val target = match?.let { m -> current?.let { wanted[it]?.get(m.groupValues[2]) } }
            if (match != null && target != null && match.groupValues[3].toBoolean() != target) {
                out += "${match.groupValues[1]}${match.groupValues[2]}: $target"
                changed += "$current ${match.groupValues[2]} -> $target"
            } else {
                out += line
            }
        }
        return out to changed
    }

    /** The equipment slot a worn upstream mesh is converted for (null: inventory / chathead mesh, geometry kept). */
    fun wornSlot(
        def: ModernItemDef,
        code: Int,
        model: Int,
    ): Int? = if (code in setOf(23, 24, 25, 26, 78, 79) && model in OsrsItemImportTool.wornModels(def)) def.wearPos1 else null

    private class NewModel(val upstream: Int, val local: Int, val bytes: ByteArray)

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val dropped = mutableListOf<String>()
        val mutations = ArrayList<CacheMutation>()
        val newModels = LinkedHashMap<Int, NewModel>()
        val wantedFlags = LinkedHashMap<Int, Map<String, Boolean>>()
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
            val existing = ImportBatchOrchestrator.readExistingMapping(assetMap)
            val imported = OsrsFxImportTool.importedItems(assetMap).values.toSet()
            val pieces by lazy { WornForearm.pieces(library) }
            val candidates by lazy {
                val census = ModelNamespaceCensusTool.census(OsrsItemImportTool.TARGETS[0], OsrsItemImportTool.TARGETS[1], assetMap)
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                check(census.untracedReferenceTypes.isEmpty()) { "untraced model reference types: ${census.untracedReferenceTypes}" }
                OsrsItemImportTool.meshModelCandidates(census)
            }
            val reserved = HashSet<Int>()

            val looks = LOOKS
            looks.forEach { look ->
                check(look.local !in imported) { "${look.localName} ${look.local} is an imported OSRS item; the importer owns its look" }
                val def = ModernItemDefDecoder.decode(look.upstream, itemFiles[look.upstream] ?: error("upstream item ${look.upstream} missing"))
                check(def.name == look.upstreamName) { "upstream ${look.upstream} is '${def.name}', expected '${look.upstreamName}'" }
                val current = CacheItemProbeTool.itemData(library, look.local) ?: error("item ${look.local} missing")
                val currentName = ItemDefCodec.readName(current)
                check(currentName == look.localName) { "item ${look.local} is '$currentName', expected '${look.localName}'" }
                if (!look.inventoryOnly) wantedFlags[look.local] = hideFlags(def)

                fun localModel(code: Int, upstreamModel: Int): Int {
                    existing.modelLocalIdByMappingKey[ModelIdAllocator.mappingKey("upstream_model:$upstreamModel", MODEL_ROLE)]?.let { mapped ->
                        checkNotNull(library.data(INDEX_MODEL, mapped, 0)) { "upstream model $upstreamModel is mapped to $mapped, which is missing" }
                        println("  MODEL op $code upstream $upstreamModel -> REUSE local $mapped (asset map)")
                        return mapped
                    }
                    newModels[upstreamModel]?.let { return it.local }
                    val slot = wornSlot(def, code, upstreamModel)
                    // As the importer: a mesh worn by both genders is fitted to the male forearm.
                    val female = upstreamModel !in setOf(def.maleModel0, def.maleModel1, def.maleModel2)
                    val plain = OsrsModelConversion.convert(reader, upstreamModel, dropped, slot, if (slot == OsrsBodyFrame.LEGS_SLOT) female else null)
                    // An OSRS hands mesh carries the lower forearm; fitted around the 667 forearm of its gender like every
                    // imported glove (OsrsWristFit, OsrsItemImportTool), so the cuff is not hidden inside the skin.
                    val bytes = if (slot == OsrsWristFit.HANDS_SLOT) OsrsWristFit.fitBytes(plain, pieces.first { it.female == female }) else plain
                    val id = ModelIdAllocator.resolveFromCandidates("upstream_model:$upstreamModel", MODEL_ROLE, emptyMap(), candidates, reserved)
                    reserved += id
                    check(library.data(INDEX_MODEL, id, 0) == null) { "census candidate model $id is not empty" }
                    newModels[upstreamModel] = NewModel(upstreamModel, id, bytes)
                    mutations += CacheMutation(INDEX_MODEL, id, 0, bytes, "osrs look: upstream model $upstreamModel (${def.name}${slot?.let { " slot $it" } ?: ""})")
                    println("  MODEL op $code upstream $upstreamModel -> NEW local $id${slot?.let { " (worn slot $it)" } ?: ""}")
                    return id
                }

                println("LOOK item ${look.local} '${look.localName}' <- OSRS ${look.upstream} '${def.name}' (${look.why})")
                val byUpstream = LinkedHashMap<Int, Int>()
                lookModels(def).filterKeys { !look.inventoryOnly || it == 1 }.forEach { (code, model) -> byUpstream.getOrPut(model) { localModel(code, model) } }
                val relooked = relook(current, def, look.inventoryOnly) { upstreamModel -> byUpstream.getValue(upstreamModel) }
                if (relooked.contentEquals(current)) {
                    println("  ALREADY item ${look.local} has the OSRS look")
                    return@forEach
                }
                val before = ItemDefCodec.describeOpcodes(current).filter { it.substringBefore('=').toInt() in LOOK_OPCODES }
                val after = ItemDefCodec.describeOpcodes(relooked).filter { it.substringBefore('=').toInt() in LOOK_OPCODES }
                println("  BEFORE ${before.joinToString(" ")}")
                println("  AFTER  ${after.joinToString(" ")}")
                mutations +=
                    CacheMutation(INDEX_ITEM, look.local ushr 8, look.local and 0xFF, relooked, "osrs look item ${look.local} ${look.localName}", CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
            reader.close()
        }
        dropped.distinct().forEach { println("DROPPED $it") }

        val ymlFile = File(OsrsArmsKitRepairTool.ITEMS_YML)
        val ymlText = ymlFile.readText()
        val newline = if (ymlText.contains("\r\n")) "\r\n" else "\n"
        val (ymlLines, flagChanges) = rewriteFlags(ymlText.split(newline), wantedFlags)
        flagChanges.forEach { println("PLAN items.yml $it") }

        if (mutations.isEmpty() && flagChanges.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        if (mutations.isNotEmpty()) {
            val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
            val plan = transaction.preflight()
            val errors = transaction.blockingErrors(plan)
            println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
            errors.forEach { println("  BLOCKING: $it") }
            check(errors.isEmpty()) { "preflight blocked; nothing written" }
            if (!apply) {
                println("DRY_RUN")
                return
            }
            val applied = transaction.apply(plan)
            val problems = transaction.verify()
            if (problems.isNotEmpty()) {
                problems.forEach { println("  VERIFY_FAILURE: $it") }
                println("ROLLED_BACK ${transaction.rollback()}")
                error("transaction ${transaction.id} failed verification and was rolled back")
            }
            if (newModels.isNotEmpty()) {
                val block = StringBuilder()
                block.append("  - name: OSRS look on existing 667 items (OsrsLookTool - ${LOOKS.joinToString { "${it.local}<-${it.upstream}" }})\n")
                block.append("    status: IMPORTED_BY_LOOK_TOOL\n    transaction: ${transaction.id}\n    models:\n")
                newModels.values.forEach { block.append("      - role: $MODEL_ROLE\n        upstream_id: ${it.upstream}\n        local_id: ${it.local}\n") }
                val text = assetMap.readText()
                assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            }
            println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
        } else if (!apply) {
            println("DRY_RUN")
            return
        }
        if (flagChanges.isNotEmpty()) {
            ymlFile.writeText(ymlLines.joinToString(newline))
            println("APPLIED items.yml ${flagChanges.size} flag(s) in ${OsrsArmsKitRepairTool.ITEMS_YML}")
        }
    }
}
