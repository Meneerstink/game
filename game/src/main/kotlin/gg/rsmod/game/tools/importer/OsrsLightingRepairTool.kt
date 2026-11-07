package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * Puts the lighting of every already imported OSRS item (opcodes 113/114) and npc (100/101) on the 667 meaning
 * (owner 2026-09-30: "Blighted super restore verschijnt zwart in inventory"; ROOT_CAUSES #104).
 *
 * The importers copied the OSRS ambient byte, which OSRS adds to the directional light but every 667 toolkit multiplies
 * the colour with, and divided the contrast byte by 5 although both clients decode `byte * 5`
 * ([OsrsItemImportTool.rev667Ambient], [OsrsItemImportTool.rev667Contrast]). This tool recomputes both bytes from the pinned
 * OSRS definition of every item (`upstream_item_id`) and npc (`fx_kind: npc` / `npc_clone`) in the asset map.
 *
 * Only a byte that still holds exactly what the old importer wrote (the raw OSRS ambient, the contrast / 5) is rewritten;
 * any other value was set on purpose later and is reported (KEPT) instead of overwritten. Everything else in the
 * definition is copied byte for byte and re-checked field by field. Both caches change together through [CacheTransaction].
 *
 * Usage: `[--apply]` - without it only plan + preflight run.
 */
object OsrsLightingRepairTool {
    const val INDEX_ITEM = 19
    const val INDEX_NPC = OsrsNpcImportTool.INDEX_NPC

    /** The old importer's contrast byte (OSRS byte / 5, rounded) - the only value this tool treats as its own. */
    fun legacyContrast(osrs: Int): Int = (osrs / 5.0).roundToInt().coerceIn(-128, 127)

    class Plan(val ambient: Int?, val contrast: Int?, val notes: List<String>)

    /**
     * The target (ambient, contrast) bytes for a definition that currently holds [currentAmbient]/[currentContrast] (0 when
     * absent), or null per field when that field must not change. [base] is [OsrsItemImportTool.ITEM_CONTRAST_BASE] or
     * [OsrsItemImportTool.NPC_CONTRAST_BASE].
     */
    fun plan(
        osrsAmbient: Int,
        osrsContrast: Int,
        currentAmbient: Int,
        currentContrast: Int,
        base: Int,
    ): Plan {
        val notes = ArrayList<String>()
        val wantAmbient = OsrsItemImportTool.rev667Ambient(osrsAmbient, osrsContrast, base)
        val wantContrast = OsrsItemImportTool.rev667Contrast(osrsContrast)
        val ambient =
            when (currentAmbient) {
                wantAmbient -> null
                osrsAmbient -> wantAmbient
                else -> null.also { notes += "ambient KEPT $currentAmbient (OSRS $osrsAmbient, converted $wantAmbient)" }
            }
        val contrast =
            when (currentContrast) {
                wantContrast -> null
                legacyContrast(osrsContrast) -> wantContrast
                else -> null.also { notes += "contrast KEPT $currentContrast (OSRS $osrsContrast)" }
            }
        return Plan(ambient, contrast, notes)
    }

    /** Opcode -> (segment start incl. opcode byte, segment end exclusive) for an item definition. */
    private fun itemSegments(bytes: ByteArray): List<Triple<Int, Int, Int>> {
        val offsets = ItemDefCodec.payloadOffsets(bytes)
        val terminator = bytes.size - 1
        check(bytes[terminator].toInt() == 0) { "item definition does not end with the terminator" }
        return offsets.mapIndexed { i, (op, payload) ->
            Triple(op, payload - 1, if (i + 1 < offsets.size) offsets[i + 1].second - 1 else terminator)
        }
    }

    /** Opcode -> (segment start, segment end exclusive) for a 667 npc definition (field widths as [NpcRenameTool.rename]). */
    private fun npcSegments(bytes: ByteArray): List<Triple<Int, Int, Int>> {
        val out = ArrayList<Triple<Int, Int, Int>>()
        var pos = 0
        fun u8(): Int = bytes[pos++].toInt() and 0xFF

        // Reads the count first, then moves (an in-place `pos +=` of a count would add to the position before the count byte).
        fun skip(n: Int) {
            pos += n
        }
        fun str() {
            while (bytes[pos] != 0.toByte()) pos++
            pos++
        }
        while (true) {
            val start = pos
            val opcode = u8()
            if (opcode == 0) break
            when (opcode) {
                1 -> skip(u8() * 2)
                2 -> str()
                12 -> skip(1)
                in 30 until 35, in 150 until 155 -> str()
                40, 41 -> skip(u8() * 4)
                42 -> skip(u8())
                60, 160 -> skip(u8() * 2)
                241, 242 -> skip(u8() * 4) // N462: wide model / chathead ids (archive 37)
                93, 99, 107, 109, 111, 158, 159, 162 -> {}
                95, 97, 98, 102, 103, 122, 123, 137, 138, 139, 142, 127 -> skip(2)
                100, 101, 125, 128, 140, 163, 165, 168, 119 -> skip(1)
                106, 118 -> {
                    skip(4)
                    if (opcode == 118) skip(2)
                    skip((u8() + 1) * 2)
                }
                113, 164, 155 -> skip(4)
                114, 115 -> skip(2)
                121 -> skip(u8() * 3)
                134 -> skip(9)
                135, 136 -> skip(3)
                249 -> repeat(u8()) {
                    val isString = u8() == 1
                    skip(3)
                    if (isString) str() else skip(4)
                }
                else -> error("unknown NPCType opcode $opcode at offset $start - refusing to re-encode")
            }
            out += Triple(opcode, start, pos)
        }
        check(pos == bytes.size) { "trailing ${bytes.size - pos} byte(s) after the terminator" }
        return out
    }

    /** Signed value of the one-byte [opcode] in [bytes], 0 when absent. */
    private fun byteField(
        bytes: ByteArray,
        segments: List<Triple<Int, Int, Int>>,
        opcode: Int,
    ): Int = segments.firstOrNull { it.first == opcode }?.let { bytes[it.second + 1].toInt() } ?: 0

    /**
     * [bytes] with the one-byte opcodes in [values] replaced (value 0 removes the field, absent fields are added before the
     * terminator); every other segment is copied unchanged and in order.
     */
    fun rewrite(
        bytes: ByteArray,
        segments: List<Triple<Int, Int, Int>>,
        values: Map<Int, Int>,
    ): ByteArray {
        val out = ByteArrayOutputStream(bytes.size + 4)
        val written = HashSet<Int>()
        for ((op, start, end) in segments) {
            val value = values[op]
            if (value == null) {
                out.write(bytes, start, end - start)
            } else if (value != 0 && written.add(op)) {
                out.write(op)
                out.write(value and 0xFF)
            }
        }
        values.forEach { (op, value) ->
            if (value != 0 && written.add(op)) {
                out.write(op)
                out.write(value and 0xFF)
            }
        }
        out.write(0)
        return out.toByteArray()
    }

    /** (upstream, local) pairs of every npc the npc import tool recorded, clones included. */
    fun importedNpcs(assetMap: File): List<Pair<Int, Int>> {
        val root = ObjectMapper(YAMLFactory()).readTree(assetMap) ?: return emptyList()
        return root.path("imports")
            .filter { it.path("fx_kind").asText() in setOf("npc", "npc_clone") && it.path("upstream_fx_id").isInt && it.path("local_fx_id").isInt }
            .map { it.path("upstream_fx_id").asInt() to it.path("local_fx_id").asInt() }
            .distinct()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        val kept = ArrayList<String>()
        try {
            val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
            for ((upstream, local) in OsrsFxImportTool.importedItems(assetMap).entries.sortedBy { it.value }) {
                val source = itemFiles[upstream] ?: error("OSRS item $upstream missing")
                val osrs = ModernItemDefDecoder.decode(upstream, source)
                val current = library.data(INDEX_ITEM, local ushr 8, local and 0xFF) ?: continue
                val segments = itemSegments(current)
                val plan = plan(osrs.ambient, osrs.contrast, byteField(current, segments, 113), byteField(current, segments, 114), OsrsItemImportTool.ITEM_CONTRAST_BASE)
                plan.notes.forEach { kept += "item $local (${osrs.name}): $it" }
                val values = buildMap { plan.ambient?.let { put(113, it) }; plan.contrast?.let { put(114, it) } }
                if (values.isEmpty()) continue
                val repaired = rewrite(current, segments, values)
                val before = ItemDefCodec.describeOpcodes(current).filterNot { it.startsWith("113=") || it.startsWith("114=") }
                val after = ItemDefCodec.describeOpcodes(repaired).filterNot { it.startsWith("113=") || it.startsWith("114=") }
                check(before == after) { "item $local: re-encode changed other fields" }
                println("PLAN item $local ${osrs.name}: ambient ${byteField(current, segments, 113)} -> ${plan.ambient ?: "="}, contrast ${byteField(current, segments, 114)} -> ${plan.contrast ?: "="}")
                mutations += CacheMutation(INDEX_ITEM, local ushr 8, local and 0xFF, repaired, "lighting item $local ${osrs.name}", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
            }
            val npcFiles = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
            for ((upstream, local) in importedNpcs(assetMap).sortedBy { it.second }) {
                val osrs = OsrsNpcProbeTool.decodeOsrs(upstream, npcFiles[upstream] ?: error("OSRS npc $upstream missing"))
                val current = library.data(INDEX_NPC, local ushr 7, local and 0x7F) ?: continue
                val segments = npcSegments(current)
                val plan = plan(osrs.ambient, osrs.contrast, byteField(current, segments, 100), byteField(current, segments, 101), OsrsItemImportTool.NPC_CONTRAST_BASE)
                plan.notes.forEach { kept += "npc $local (${osrs.name}): $it" }
                val values = buildMap { plan.ambient?.let { put(100, it) }; plan.contrast?.let { put(101, it) } }
                if (values.isEmpty()) continue
                val repaired = rewrite(current, segments, values)
                val strip = { b: ByteArray -> npcSegments(b).filter { it.first != 100 && it.first != 101 }.map { b.copyOfRange(it.second, it.third).toList() } }
                check(strip(current) == strip(repaired)) { "npc $local: re-encode changed other fields" }
                check(byteField(repaired, npcSegments(repaired), 100) == (plan.ambient ?: byteField(current, segments, 100)))
                println("PLAN npc $local ${osrs.name}: ambient ${byteField(current, segments, 100)} -> ${plan.ambient ?: "="}, contrast ${byteField(current, segments, 101)} -> ${plan.contrast ?: "="}")
                mutations += CacheMutation(INDEX_NPC, local ushr 7, local and 0x7F, repaired, "lighting npc $local ${osrs.name}", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
            reader.close()
        }
        kept.forEach { println("KEPT $it") }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
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
        val verifyProblems = transaction.verify()
        if (verifyProblems.isNotEmpty()) {
            verifyProblems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
