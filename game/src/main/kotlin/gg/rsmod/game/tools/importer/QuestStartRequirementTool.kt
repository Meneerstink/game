package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * The client's start requirements of the eight permanent-choice quests (quests run 2026-10-10). Clientscript 2162 builds
 * the quest list and hides a quest that is not started when clientscript 2149 ("can start") fails; 2149 reads the quest
 * struct's requirement params: 2155 the prerequisite quests (859-870), 895 Quest Points, 896 combat level, 2153 the skill
 * requirements (871-894, 897) and 898 / 2194. Desert Treasure, Lunar Diplomacy, King's Ransom and The Temple at Senntisten
 * carry the 2011 requirements, so a new player's quest list did not show them at all - while the server lets anyone start
 * them (owner 2026-10-08, OWNER_QUESTIONS Q17 A: the quests stay as they are, the Tutor only advises training combat).
 * This tool removes exactly those requirement params from the quest structs of the choice slots, so the list shows them to
 * everybody, like the six OSRS-added choice quests (whose structs never had them). Name, sort name, slot, difficulty,
 * map hint and members (845-856) stay. One [CacheTransaction] over both caches; running it again is a no-op.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.QuestStartRequirementTool plan|apply|dump`
 */
object QuestStartRequirementTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"

    /** Quest-list slots of the eight choice quests (QuestList / FoundationQuests). */
    val CHOICE_SLOTS = listOf(30, 70, 126, 157, 185, 186, 187, 188)

    /** The params clientscript 2149 and its helpers 2153 / 2155 / 2194 read. */
    val REQUIREMENT_PARAMS: Set<Int> = (859..898).toSet()

    class Param(val key: Int, val string: String?, val int: Int)

    fun decode(data: ByteArray): List<Param> {
        var p = 0
        fun u8() = data[p++].toInt() and 0xFF
        val out = mutableListOf<Param>()
        while (p < data.size) {
            when (val op = u8()) {
                0 -> return out
                249 -> {
                    repeat(u8()) {
                        val isString = u8() == 1
                        val key = (u8() shl 16) or (u8() shl 8) or u8()
                        if (isString) {
                            val start = p
                            while (data[p].toInt() != 0) p++
                            out += Param(key, String(data, start, p - start, Charsets.ISO_8859_1), 0)
                            p++
                        } else {
                            out += Param(key, null, (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8())
                        }
                    }
                }
                else -> error("struct opcode $op")
            }
        }
        return out
    }

    fun encode(params: List<Param>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(249)
        out.write(params.size)
        params.forEach { param ->
            out.write(if (param.string != null) 1 else 0)
            out.write(param.key ushr 16)
            out.write(param.key ushr 8)
            out.write(param.key)
            if (param.string != null) {
                out.write(param.string.toByteArray(Charsets.ISO_8859_1))
                out.write(0)
            } else {
                out.write(param.int ushr 24)
                out.write(param.int ushr 16)
                out.write(param.int ushr 8)
                out.write(param.int)
            }
        }
        out.write(0)
        return out.toByteArray()
    }

    /** Struct id per choice slot (enum 2252). */
    private fun structs(library: CacheLibrary): Map<Int, Int> {
        val q = QuestListCacheTool.QUEST_ENUM
        val enum = QuestListCacheTool.EnumCodec.decode(requireNotNull(library.data(QuestListCacheTool.ENUM_INDEX, q ushr 8, q and 0xFF)))
        return CHOICE_SLOTS.associateWith { slot -> requireNotNull(enum.values[slot]) { "slot $slot not in enum $q" } }
    }

    fun plan(cache: String): List<CacheMutation> {
        val library = CacheLibrary(cache)
        try {
            val mutations = mutableListOf<CacheMutation>()
            structs(library).forEach { (slot, id) ->
                val data = requireNotNull(library.data(QuestListCacheTool.CONFIG_INDEX, QuestListCacheTool.STRUCT_GROUP, id)) { "struct $id missing" }
                val params = decode(data)
                val kept = params.filter { it.key !in REQUIREMENT_PARAMS }
                if (kept.size == params.size) return@forEach
                val bytes = encode(kept)
                check(decode(bytes).map { it.key } == kept.map { it.key }) { "struct $id re-decode mismatch" }
                val name = params.firstOrNull { it.key == 845 }?.string
                mutations +=
                    CacheMutation(
                        QuestListCacheTool.CONFIG_INDEX, QuestListCacheTool.STRUCT_GROUP, id, bytes,
                        "quest struct $id '$name' (slot $slot): start requirements ${params.filter { it.key in REQUIREMENT_PARAMS }.map { it.key }} removed",
                        expectedCurrentSha1 = CacheItemProbeTool.sha1(data),
                    )
            }
            return mutations
        } finally {
            library.close()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.firstOrNull() == "dump") {
            val library = CacheLibrary(GAME_CACHE)
            structs(library).forEach { (slot, id) ->
                val params = decode(library.data(QuestListCacheTool.CONFIG_INDEX, QuestListCacheTool.STRUCT_GROUP, id)!!)
                println("slot $slot struct $id: " + params.joinToString { "${it.key}=${it.string ?: it.int}" })
            }
            library.close()
            return
        }
        val apply = args.firstOrNull() == "apply"
        val mutations = plan(GAME_CACHE)
        if (mutations.isEmpty()) {
            println("QUEST_START_REQUIREMENTS already applied")
            return
        }
        mutations.forEach { println("PLAN ${it.label}") }
        val transaction = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = transaction.preflight()
        println(preflight.joinToString("\n"))
        val errors = transaction.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) return
        val result = transaction.apply(preflight)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("QUEST_START_REQUIREMENTS transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
