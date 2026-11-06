package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * One transaction that moves every already imported OSRS worn mesh onto the 667 player body ([OsrsBodyFrame]) and gives every
 * imported item its OSRS worn offset as opcode 125/126 (owner 2026-09-29: all OSRS gear and weapons clip into walls, floors
 * and ceilings). New imports get both from [OsrsItemImportTool] itself; this tool fixes what was imported before.
 *
 * Scope: the local items of the import asset map ([OsrsItemImportTool.ASSET_MAP]) whose OSRS wear slot is drawn on the player,
 * and their male/female worn meshes that are still version 12. A mesh is refused (reported, not touched) when anything else
 * uses it - another item (worn or inventory), a spotanim, an npc or a loc - or when two items wear it in different slots,
 * because moving it would move that other use too. A version-13 mesh is already converted and skipped. The old importer wrote
 * 125/126 as offset / 4; those payloads are rewritten to the OSRS offset itself and missing ones are added, so a second run
 * finds nothing left to change.
 *
 * Usage: `./gradlew :game:runOsrsBodyFrameTool --args="plan|apply"`
 */
object OsrsBodyFrameTool {
    private const val INDEX_LOC = 16
    private const val INDEX_NPC = 18
    private const val INDEX_ITEM = 19
    private const val INDEX_SPOTANIM = 21
    private const val INDEX_MODEL = 7

    private val WORN_OPCODES = setOf(23, 24, 25, 26, 78, 79)
    private val FEMALE_WORN_OPCODES = setOf(25, 26, 79)
    private val OTHER_MODEL_OPCODES = setOf(1, 90, 91, 92, 93)

    /** `opcode -> value` of every two-byte item opcode, from [ItemDefCodec.describeOpcodes]. */
    private fun shortOpcodes(bytes: ByteArray): Map<Int, Int> =
        ItemDefCodec.describeOpcodes(bytes).mapNotNull { entry ->
            val (op, value) = entry.split('=', limit = 2)
            value.toIntOrNull()?.let { op.toInt() to it }
        }.toMap()

    private fun forEachFile(
        library: CacheLibrary,
        index: Int,
        idOf: (Int, Int) -> Int,
        action: (Int, ByteArray) -> Unit,
    ) {
        val idx = library.index(index)
        idx.archiveIds().forEach { group ->
            val archive = idx.archive(group) ?: return@forEach
            archive.fileIds().forEach { file ->
                val data = library.data(index, group, file) ?: return@forEach
                action(idOf(group, file), data)
            }
        }
    }

    /** Model ids a spotanim (client `SpotAnimationType.decode`: opcode 1 = model), npc or loc starting with its model opcode uses. */
    private fun otherModelUsers(library: CacheLibrary): Map<Int, String> {
        val users = HashMap<Int, String>()
        forEachFile(library, INDEX_SPOTANIM, { g, f -> (g shl 8) or f }) { id, b ->
            if (b.isNotEmpty() && b[0].toInt() == 1 && b.size >= 3) users[((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF)] = "spotanim $id"
        }
        forEachFile(library, INDEX_NPC, { g, f -> (g shl 7) or f }) { id, b ->
            if (b.isNotEmpty() && b[0].toInt() == 1) {
                val n = b[1].toInt() and 0xFF
                for (i in 0 until n) users[((b[2 + i * 2].toInt() and 0xFF) shl 8) or (b[3 + i * 2].toInt() and 0xFF)] = "npc $id"
            }
        }
        forEachFile(library, INDEX_LOC, { g, f -> (g shl 8) or f }) { id, b ->
            val op = if (b.isNotEmpty()) b[0].toInt() and 0xFF else 0
            if (op == 1 || op == 5) {
                var p = 1
                val kinds = b[p++].toInt() and 0xFF
                repeat(kinds) {
                    p++ // shape
                    val n = b[p++].toInt() and 0xFF
                    repeat(n) {
                        users[((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)] = "loc $id"
                        p += 2
                    }
                }
            }
        }
        return users
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }

        val imported =
            ImportBatchOrchestrator.readExistingMapping(File(OsrsItemImportTool.ASSET_MAP)).itemLocalIdBySourceIdentity
                .filterKeys { it.startsWith("upstream_item:") }
                .mapKeys { it.key.removePrefix("upstream_item:").toInt() }
        println("IMPORTED_ITEMS ${imported.size}")

        val mutations = mutableListOf<CacheMutation>()
        val refused = mutableListOf<String>()
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            // Every model use in the cache, to refuse a mesh that is not only an imported item's worn mesh.
            val itemBytes = HashMap<Int, ByteArray>()
            val wornBy = HashMap<Int, MutableSet<Int>>()
            val otherUse = HashMap<Int, String>(otherModelUsers(library))
            forEachFile(library, INDEX_ITEM, { g, f -> (g shl 8) or f }) { id, b ->
                itemBytes[id] = b
                val ops = runCatching { shortOpcodes(b) }.getOrElse { emptyMap() }
                ops.forEach { (op, model) ->
                    if (op in WORN_OPCODES) wornBy.getOrPut(model) { mutableSetOf() } += id
                    if (op in OTHER_MODEL_OPCODES) otherUse[model] = "item $id opcode $op"
                }
            }
            val importedLocal = imported.values.toSet()

            val slotOfModel = HashMap<Int, MutableSet<Int>>()
            val femaleOfModel = HashMap<Int, MutableSet<Boolean>>()
            val offsets = mutableListOf<Triple<Int, Int, Int>>() // local item, opcode, offset
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
                imported.forEach { (upstream, local) ->
                    val osrs = runCatching { ModernItemDefDecoder.decode(upstream, itemFiles[upstream] ?: return@forEach) }.getOrNull() ?: return@forEach
                    val bytes = itemBytes[local] ?: return@forEach
                    val slot = osrs.wearPos1
                    if (slot !in OsrsBodyFrame.RENDERED_SLOTS) return@forEach
                    shortOpcodes(bytes).filterKeys { it in WORN_OPCODES }.forEach { (op, model) ->
                        slotOfModel.getOrPut(model) { mutableSetOf() } += slot
                        femaleOfModel.getOrPut(model) { mutableSetOf() } += op in FEMALE_WORN_OPCODES
                    }
                    offsets += Triple(local, 125, osrs.maleOffset)
                    offsets += Triple(local, 126, osrs.femaleOffset)
                }
            }

            var converted = 0
            var already = 0
            slotOfModel.toSortedMap().forEach { (modelId, slots) ->
                val other = otherUse[modelId]
                val foreign = wornBy[modelId].orEmpty().filter { it !in importedLocal }
                when {
                    slots.size != 1 -> refused += "model $modelId worn in slots $slots"
                    slots.single() == OsrsBodyFrame.LEGS_SLOT && femaleOfModel.getValue(modelId).size != 1 -> refused += "legs model $modelId worn by both genders"
                    other != null -> refused += "model $modelId also used by $other"
                    foreign.isNotEmpty() -> refused += "model $modelId also worn by non-imported items $foreign"
                    else -> {
                        val current = ModelArchive.read(library, modelId) ?: error("model $modelId missing")
                        val model = Rev667ModelDecoder.decode(current)
                        if (model.version >= OsrsBodyFrame.FRAME_VERSION) {
                            already++
                            return@forEach
                        }
                        val before = Rev667ModelDecoder.decode(current)
                        OsrsBodyFrame.toRev667Frame(model, slots.single(), femaleOfModel.getValue(modelId).single())
                        val updated = Rev667ModelEncoder.encode(model)
                        val reread = Rev667ModelDecoder.decode(updated)
                        val differences = ModelConvertTool.compare(model, reread)
                        check(differences.isEmpty()) { "model $modelId re-read mismatch: $differences" }
                        check(reread.version == OsrsBodyFrame.FRAME_VERSION && reread.vertexCount == before.vertexCount) { "model $modelId version/vertices" }
                        mutations += CacheMutation(INDEX_MODEL, modelId, 0, updated, "osrs body frame model $modelId slot ${slots.single()}", CacheItemProbeTool.sha1(current))
                        converted++
                        val ys = reread.vertexY
                        println("MODEL $modelId slot=${slots.single()} y ${before.vertexY.minOrNull()!! * 4}..${before.vertexY.maxOrNull()!! * 4} -> ${ys.minOrNull()}..${ys.maxOrNull()}")
                    }
                }
            }
            // Worn offsets: an existing 125/126 (written as offset / 4 by the old importer) is rewritten to (0, offset, 0), a missing one
            // is added before the terminator, and one on an item whose OSRS offset is 0 is left as it is.
            var itemsChanged = 0
            offsets.groupBy { it.first }.forEach { (local, list) ->
                val current = itemBytes.getValue(local)
                check(current.last() == 0.toByte()) { "item $local does not end with the terminator" }
                var updated = current.copyOf()
                val positions = ItemDefCodec.payloadOffsets(current).toMap()
                val appended = mutableListOf<Byte>()
                list.forEach { (_, op, offset) ->
                    val payload = OsrsItemImportTool.wornOffsetBytes(offset) ?: return@forEach
                    val at = positions[op]
                    if (at != null) payload.copyInto(updated, at) else appended += listOf(op.toByte()) + payload.toList()
                }
                if (appended.isNotEmpty()) updated = updated.copyOfRange(0, updated.size - 1) + appended.toByteArray() + byteArrayOf(0)
                if (updated.contentEquals(current)) return@forEach
                val reread = ItemDefCodec.payloadOffsets(updated).toMap()
                list.filter { it.third != 0 }.forEach { (_, op, offset) ->
                    val at = reread[op] ?: error("item $local opcode $op not re-read")
                    check(updated[at + 1].toInt() == offset && updated[at].toInt() == 0 && updated[at + 2].toInt() == 0) { "item $local opcode $op payload" }
                }
                mutations += CacheMutation(INDEX_ITEM, local ushr 8, local and 0xFF, updated, "osrs worn offset item $local", CacheItemProbeTool.sha1(current))
                itemsChanged++
                println("ITEM $local ${ItemDefCodec.readName(current)} worn offsets ${list.filter { it.third != 0 }.map { "${it.second}=${it.third}" }}")
            }
            println("SUMMARY models converted=$converted already=$already refused=${refused.size} items with corrected offsets=$itemsChanged")
            refused.forEach { println("REFUSED $it") }
        } finally {
            library.close()
        }

        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${transaction.id} mutations=${mutations.size} (nothing written)")
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
