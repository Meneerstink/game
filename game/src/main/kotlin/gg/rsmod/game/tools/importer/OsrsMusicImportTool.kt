package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Owner answer Q11 option (a) (2026-09-14): import the OSRS music track "The Enclave" (OSRS Wiki: number 642, cacheid 680, "This track
 * unlocks in Ferox Enclave") into the 667 music system, played on the 667 instruments (ADAPTED: OSRS re-made the patches; evidence in
 * HANDOFF_CURRENT.md "DECISION 3c FEASIBILITY").
 *
 * One [CacheTransaction] on both caches: the song bytes as-is in index 6 (the 667 `MidiSong` decoder reads the same encoding), enum 1345
 * (music index -> name) and enum 1351 (music index -> song group) extended with [MUSIC_INDEX]. Existing enum entries are kept byte for byte;
 * the current enum bytes are pinned by sha1.
 *
 * Usage: `plan|apply`.
 */
object OsrsMusicImportTool {
    const val SOURCE_CACHE = "C:\\RSPS\\import-source\\openrs2-2686\\cache"
    const val MUSIC_INDEX_ID = 6
    const val ENUM_INDEX_ID = 17
    const val NAMES_ENUM = 1345
    const val GROUPS_ENUM = 1351

    const val UPSTREAM_SONG = 680
    const val LOCAL_SONG_GROUP = 1010
    const val MUSIC_INDEX = 1016
    const val NAME = "The Enclave"

    /** One enum entry value: a string (opcode 5 / 7) or an int (opcode 6 / 8), kept as its encoded bytes. */
    /** Opcodes before the entry table ([header]) and after it ([trailer], without the final 0) are kept byte for byte. */
    class EnumData(
        val header: ByteArray,
        val opcode: Int,
        val arraySize: Int,
        val entries: java.util.LinkedHashMap<Int, ByteArray>,
        val trailer: ByteArray,
    )

    fun decode(bytes: ByteArray): EnumData {
        var pos = 0
        val before = ByteArrayOutputStream()
        val after = ByteArrayOutputStream()
        var table: Triple<Int, Int, java.util.LinkedHashMap<Int, ByteArray>>? = null
        fun u8() = bytes[pos++].toInt() and 0xFF
        fun u16() = (u8() shl 8) or u8()
        while (true) {
            val start = pos
            val op = u8()
            val sink = if (table == null) before else after
            when (op) {
                0 -> {
                    check(pos == bytes.size) { "bytes after the enum terminator" }
                    val (tableOp, size, entries) = table ?: error("enum without an entry table")
                    return EnumData(before.toByteArray(), tableOp, size, entries, after.toByteArray())
                }
                1, 2 -> { u8(); sink.write(bytes, start, pos - start) }
                3 -> { while (bytes[pos].toInt() != 0) pos++; pos++; sink.write(bytes, start, pos - start) }
                4 -> { pos += 4; sink.write(bytes, start, pos - start) }
                7, 8 -> {
                    check(table == null) { "two entry tables" }
                    val size = u16()
                    val count = u16()
                    val entries = java.util.LinkedHashMap<Int, ByteArray>()
                    repeat(count) {
                        val key = u16()
                        val valueStart = pos
                        if (op == 7) { while (bytes[pos].toInt() != 0) pos++; pos++ } else pos += 4
                        entries[key] = bytes.copyOfRange(valueStart, pos)
                    }
                    table = Triple(op, size, entries)
                }
                else -> error("enum opcode $op not handled (the music enums use 1-4 and 7 / 8)")
            }
        }
    }

    fun encode(data: EnumData): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(data.header)
        out.write(data.opcode)
        out.write(data.arraySize ushr 8); out.write(data.arraySize and 0xFF)
        out.write(data.entries.size ushr 8); out.write(data.entries.size and 0xFF)
        data.entries.forEach { (key, value) ->
            out.write(key ushr 8); out.write(key and 0xFF)
            out.write(value)
        }
        out.write(data.trailer)
        out.write(0)
        return out.toByteArray()
    }

    /** [current] with [key] -> [value] added; an existing identical entry is kept, a different one is refused. */
    fun withEntry(current: ByteArray, key: Int, value: ByteArray): ByteArray {
        val data = decode(current)
        check(encode(data).contentEquals(current)) { "enum re-encode is not byte-identical" }
        data.entries[key]?.let { check(it.contentEquals(value)) { "enum key $key already holds a different value" }; return current }
        data.entries[key] = value
        return encode(EnumData(data.header, data.opcode, maxOf(data.arraySize, key + 1), data.entries, data.trailer))
    }

    fun stringValue(s: String) = s.toByteArray(Charsets.ISO_8859_1) + 0

    fun intValue(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull() ?: error("Usage: plan|apply")
        val song = ModernCacheReader(File(SOURCE_CACHE)).use { it.file(MUSIC_INDEX_ID, UPSTREAM_SONG, 0) } ?: error("OSRS song $UPSTREAM_SONG missing")
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        val names: ByteArray
        val groups: ByteArray
        try {
            // A re-run finds the imported song already there (the transaction then plans NO_OP); any other content is refused.
            val existing = if (library.index(MUSIC_INDEX_ID).archive(LOCAL_SONG_GROUP) == null) null else library.data(MUSIC_INDEX_ID, LOCAL_SONG_GROUP, 0)
            check(existing == null || existing.contentEquals(song)) { "index 6 group $LOCAL_SONG_GROUP holds a different song" }
            names = library.data(ENUM_INDEX_ID, NAMES_ENUM ushr 8, NAMES_ENUM and 0xFF)!!
            groups = library.data(ENUM_INDEX_ID, GROUPS_ENUM ushr 8, GROUPS_ENUM and 0xFF)!!
        } finally {
            library.close()
        }
        val newNames = withEntry(names, MUSIC_INDEX, stringValue(NAME))
        val newGroups = withEntry(groups, MUSIC_INDEX, intValue(LOCAL_SONG_GROUP))
        // A first run adds exactly one entry to each enum; a re-run finds it and changes nothing.
        check(decode(newNames).entries.size - decode(names).entries.size == (if (newNames === names) 0 else 1))
        check(decode(newGroups).entries.size - decode(groups).entries.size == (if (newGroups === groups) 0 else 1))
        println("SONG upstream=$UPSTREAM_SONG bytes=${song.size} sha1=${CacheItemProbeTool.sha1(song)} -> index $MUSIC_INDEX_ID group $LOCAL_SONG_GROUP")
        println("ENUM $NAMES_ENUM ${names.size} -> ${newNames.size} bytes, ENUM $GROUPS_ENUM ${groups.size} -> ${newGroups.size} bytes, key $MUSIC_INDEX")

        val mutations =
            listOf(
                CacheMutation(MUSIC_INDEX_ID, LOCAL_SONG_GROUP, 0, song, "osrs song $UPSTREAM_SONG '$NAME' -> group $LOCAL_SONG_GROUP"),
                CacheMutation(ENUM_INDEX_ID, NAMES_ENUM ushr 8, NAMES_ENUM and 0xFF, newNames, "enum $NAMES_ENUM + $MUSIC_INDEX '$NAME'", CacheItemProbeTool.sha1(names)),
                CacheMutation(ENUM_INDEX_ID, GROUPS_ENUM ushr 8, GROUPS_ENUM and 0xFF, newGroups, "enum $GROUPS_ENUM + $MUSIC_INDEX -> $LOCAL_SONG_GROUP", CacheItemProbeTool.sha1(groups)),
            )
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes")
        val block =
            "  - name: OSRS music track The Enclave (owner answer Q11 option a)\n" +
                "    transaction: ${tx.id}\n" +
                "    source: OpenRS2 cache 2686 index 6 group $UPSTREAM_SONG (sha1 ${CacheItemProbeTool.sha1(song)})\n" +
                "    music: { upstream_song_group: $UPSTREAM_SONG, local_song_group: $LOCAL_SONG_GROUP, music_index: $MUSIC_INDEX, name: $NAME }\n" +
                "    enums: [$NAMES_ENUM, $GROUPS_ENUM]\n" +
                "    adapted: played on the 667 instrument patches (OSRS patches 0, 48, 52, 60, 72, 73, 176 differ)\n"
        val assetMap = File(FeroxImportTool.ASSET_MAP)
        val text = assetMap.readText()
        assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
        println("ASSET_MAP appended music entry")
    }
}
