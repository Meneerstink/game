package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.ByteArrayOutputStream

/**
 * A45 (owner 2026-10-11: "the quests we have in quest tutor need to show in the quest list on top now it shows first all fired up
 * animal magnetism"): the eight Quest Tutor quests stand at the top of the quest list, in the Tutor's order (Magic, Prayer, Gear).
 *
 * The list (clientscript 2162, see [QuestListPart2CacheTool]) draws "Not completed" then "Completed", each sorted by the quest
 * struct's sort name (param 846, lowercased, clientscript 2163). Every quest outside the Tutor's eight is completed for every
 * account, so the "Not completed" part only ever holds Tutor quests; giving the eight sort names "!1 ..".."!8 .." (`!` sorts
 * before every letter and digit) puts the completed ones at the head of the "Completed" part too. The list shows param 845, the
 * real name, so nothing visible changes but the order. One [CacheTransaction] over both caches; running it again is a no-op.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.QuestTutorOrderCacheTool [apply]`
 */
object QuestTutorOrderCacheTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")

    const val SORT_PARAM = 846
    const val NAME_PARAM = 845

    /** Quest-list slots in the Quest Tutor's order (server ChoiceGroup: Magic, Prayer, Gear). */
    val TUTOR_SLOTS = listOf(30, 70, 187, 157, 126, 185, 186, 188)

    fun sortName(position: Int, name: String) = "!${position + 1} $name"

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val mutations = plan(GAME_CACHE)
        if (mutations.isEmpty()) {
            println("QUEST_TUTOR_ORDER already applied")
            return
        }
        val transaction = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = transaction.preflight()
        println(preflight.joinToString("\n"))
        val errors = transaction.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) return
        val result = transaction.apply(preflight)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("QUEST_TUTOR_ORDER transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    fun plan(cache: String): List<CacheMutation> {
        val library = CacheLibrary(cache)
        try {
            val questEnumBytes = requireNotNull(library.data(QuestListCacheTool.ENUM_INDEX, QuestListCacheTool.QUEST_ENUM ushr 8, QuestListCacheTool.QUEST_ENUM and 0xFF))
            val questEnum = QuestListCacheTool.EnumCodec.decode(questEnumBytes)
            return TUTOR_SLOTS.mapIndexedNotNull { position, slot ->
                val struct = requireNotNull(questEnum.values[slot]) { "quest slot $slot has no struct" }
                val bytes = requireNotNull(library.data(QuestListCacheTool.CONFIG_INDEX, QuestListCacheTool.STRUCT_GROUP, struct)) { "struct $struct missing" }
                val params = decodeParams(bytes)
                val name = params[NAME_PARAM] as? String ?: error("struct $struct has no name")
                val wanted = sortName(position, name)
                if (params[SORT_PARAM] == wanted) return@mapIndexedNotNull null
                val patched = encodeParams(params + (SORT_PARAM to wanted))
                CacheMutation(
                    QuestListCacheTool.CONFIG_INDEX, QuestListCacheTool.STRUCT_GROUP, struct, patched,
                    "quest struct $struct ($name, slot $slot): sort name '$wanted'",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(bytes),
                )
            }
        } finally {
            library.close()
        }
    }

    /** A struct config file: opcode 249 (params) then 0; any other opcode is refused (never rewrite what is not understood). */
    fun decodeParams(data: ByteArray): LinkedHashMap<Int, Any> {
        val params = LinkedHashMap<Int, Any>()
        var p = 0
        while (true) {
            val op = data[p++].toInt() and 0xFF
            if (op == 0) break
            require(op == 249) { "struct opcode $op not supported" }
            val count = data[p++].toInt() and 0xFF
            repeat(count) {
                val isString = data[p++].toInt() == 1
                val key = ((data[p].toInt() and 0xFF) shl 16) or ((data[p + 1].toInt() and 0xFF) shl 8) or (data[p + 2].toInt() and 0xFF)
                p += 3
                if (isString) {
                    val start = p
                    while (data[p].toInt() != 0) p++
                    params[key] = String(data, start, p - start, Charsets.ISO_8859_1)
                    p++
                } else {
                    params[key] = ((data[p].toInt() and 0xFF) shl 24) or ((data[p + 1].toInt() and 0xFF) shl 16) or
                        ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
                    p += 4
                }
            }
        }
        require(p == data.size) { "struct has ${data.size - p} trailing bytes" }
        return params
    }

    fun encodeParams(params: Map<Int, Any>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(249)
        out.write(params.size)
        params.forEach { (key, value) ->
            out.write(if (value is String) 1 else 0)
            out.write(key ushr 16)
            out.write(key ushr 8)
            out.write(key)
            if (value is String) {
                out.write(value.toByteArray(Charsets.ISO_8859_1))
                out.write(0)
            } else {
                val v = value as Int
                out.write(v ushr 24)
                out.write(v ushr 16)
                out.write(v ushr 8)
                out.write(v)
            }
        }
        out.write(0)
        return out.toByteArray()
    }
}
