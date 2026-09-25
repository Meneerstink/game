package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Cache side of the owner's removals (2026-09-24): "Verwijder Armadyl runes volledig", "Verwijder aether rune volledig!",
 * "Verwijder uit spellbook en autocast menu spell Storm of Armadyl en Wind Rush volledig".
 *
 * - Items 21773 (Armadyl rune), 23744 (Aether rune), 23745 (Aether catalyst): opcode 65 (the client's Grand Exchange flag) is
 *   dropped, so the client's GE item search no longer lists them. The definitions stay (item ids must stay contiguous).
 * - Standard spellbook 192: Wind Rush (component 98) and Storm of Armadyl (99). The layout script CS2 2059 walks the book's sort
 *   lists (struct 735 param 662 -> enum 2167 -> 2188/2189/2190 -> enums 2191..2195) and only positions/unhides the components listed
 *   there; the category table enum 727 and the autocast list enum 729 also name them. Both spells are taken out of 2191, 2192, 727 and
 *   729, and the two components get their "hidden" flag (component byte 18, bit 0), so nothing ever shows them again.
 *
 * Every enum is decoded and re-encoded byte-identically before it is edited; everything goes through [CacheTransaction].
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.RemovedContentCacheTool plan|apply`
 */
object RemovedContentCacheTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val INDEX_INTERFACES = 3
    private const val INDEX_ENUMS = 17
    private const val INDEX_ITEMS = 19
    private const val GE_FLAG_OPCODE = 65

    val REMOVED_ITEMS = listOf(21773, 23744, 23745)
    const val SPELLBOOK = 192
    val REMOVED_SPELL_COMPONENTS = listOf(98, 99)
    val SPELL_ENUMS = listOf(727, 729, 2191, 2192)
    private const val HIDDEN_BYTE = 18

    /** A rev-667 enum: the opcode stream, with the key/value table editable. */
    class EnumDef(
        val header: List<Pair<Int, ByteArray>>,
        /** Opcodes written after the table (rev-667 enums put opcode 4, the default, last). */
        val trailer: List<Pair<Int, ByteArray>>,
        val tableOpcode: Int,
        val arraySize: Int,
        val entries: LinkedHashMap<Int, Any>,
    ) {
        fun encode(): ByteArray {
            val out = ByteArrayOutputStream()
            fun p1(v: Int) = out.write(v and 0xFF)
            fun p2(v: Int) { p1(v shr 8); p1(v) }
            fun p4(v: Int) { p2(v shr 16); p2(v) }
            header.forEach { (op, payload) -> p1(op); out.write(payload) }
            p1(tableOpcode)
            if (tableOpcode == 7 || tableOpcode == 8) p2(arraySize)
            p2(entries.size)
            entries.forEach { (k, v) ->
                if (tableOpcode == 7 || tableOpcode == 8) p2(k) else p4(k)
                if (v is String) { out.write(v.toByteArray(Charsets.ISO_8859_1)); p1(0) } else p4(v as Int)
            }
            trailer.forEach { (op, payload) -> p1(op); out.write(payload) }
            p1(0)
            return out.toByteArray()
        }

        companion object {
            fun decode(bytes: ByteArray): EnumDef {
                val b = ByteBuffer.wrap(bytes)
                val header = ArrayList<Pair<Int, ByteArray>>()
                val trailer = ArrayList<Pair<Int, ByteArray>>()
                var table = -1
                fun meta() = if (table == -1) header else trailer
                var size = 0
                val entries = LinkedHashMap<Int, Any>()
                while (true) {
                    val op = b.get().toInt() and 0xFF
                    if (op == 0) break
                    when (op) {
                        1, 2 -> meta() += op to byteArrayOf(b.get())
                        3 -> {
                            val start = b.position()
                            while (b.get().toInt() != 0) { }
                            meta() += op to bytes.copyOfRange(start, b.position())
                        }
                        4 -> meta() += op to ByteArray(4).also { b.get(it) }
                        5, 6, 7, 8 -> {
                            check(table == -1) { "two tables" }
                            table = op
                            if (op == 7 || op == 8) size = b.short.toInt() and 0xFFFF
                            val n = b.short.toInt() and 0xFFFF
                            repeat(n) {
                                val k = if (op == 7 || op == 8) b.short.toInt() and 0xFFFF else b.int
                                val v: Any =
                                    if (op == 5 || op == 7) {
                                        val start = b.position()
                                        while (b.get().toInt() != 0) { }
                                        String(bytes, start, b.position() - start - 1, Charsets.ISO_8859_1)
                                    } else b.int
                                entries[k] = v
                            }
                        }
                        else -> error("unknown enum opcode $op")
                    }
                }
                check(table != -1) { "enum without a table" }
                return EnumDef(header, trailer, table, size, entries)
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val removedComponents = REMOVED_SPELL_COMPONENTS.map { (SPELLBOOK shl 16) or it }.toSet()
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            for (item in REMOVED_ITEMS) {
                val bytes = library.data(INDEX_ITEMS, item ushr 8, item and 0xFF) ?: error("item $item missing")
                val edited = ItemDefCodec.cloneWithOverrides(bytes, emptyMap(), removedOpcodes = setOf(GE_FLAG_OPCODE))
                println("ITEM $item ${ItemDefCodec.readName(bytes)} geFlag=${!edited.contentEquals(bytes)}")
                if (!edited.contentEquals(bytes)) {
                    mutations += CacheMutation(INDEX_ITEMS, item ushr 8, item and 0xFF, edited, "item $item: no Grand Exchange flag (removed item)", CacheItemProbeTool.sha1(bytes))
                }
            }
            for (enumId in SPELL_ENUMS) {
                val bytes = library.data(INDEX_ENUMS, enumId ushr 8, enumId and 0xFF) ?: error("enum $enumId missing")
                val def = EnumDef.decode(bytes)
                check(def.encode().contentEquals(bytes)) { "enum $enumId round-trip failed" }
                val before = def.entries.size
                val keys = def.entries.filter { (k, v) -> k in removedComponents || (v is Int && v in removedComponents) }.keys.toList()
                keys.forEach { def.entries.remove(it) }
                println("ENUM $enumId entries $before -> ${def.entries.size} (removed keys $keys)")
                if (keys.isNotEmpty()) {
                    mutations += CacheMutation(INDEX_ENUMS, enumId ushr 8, enumId and 0xFF, def.encode(), "enum $enumId: Wind Rush / Storm of Armadyl removed", CacheItemProbeTool.sha1(bytes))
                }
            }
            for (component in REMOVED_SPELL_COMPONENTS) {
                val bytes = library.data(INDEX_INTERFACES, SPELLBOOK, component) ?: error("192:$component missing")
                check(bytes[0].toInt() and 0xFF == 255 && bytes[1].toInt() and 0x80 == 0) { "192:$component has an unexpected header" }
                val flags = bytes[HIDDEN_BYTE].toInt() and 0xFF
                println("COMPONENT 192:$component flags=$flags")
                if (flags and 1 == 0) {
                    val edited = bytes.copyOf()
                    edited[HIDDEN_BYTE] = (flags or 1).toByte()
                    mutations += CacheMutation(INDEX_INTERFACES, SPELLBOOK, component, edited, "192:$component hidden (spell removed)", CacheItemProbeTool.sha1(bytes))
                }
            }
        } finally {
            library.close()
        }

        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO both caches already hold the removals")
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
