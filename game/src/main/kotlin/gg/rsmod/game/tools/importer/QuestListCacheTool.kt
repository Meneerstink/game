package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The revision-667 quest list for the new-player foundation (owner 2026-09-26): ten OSRS quests the cache never had, and
 * unfinished quests on top. Found in the cache (2026-09-26 probe):
 *
 *  - Clientscript 2162 builds the list from struct 508 (enum 169 key 1): its param 61 is enum 2252, quest-list slot ->
 *    quest struct (178 entries, dense, keys 1-184). A quest struct carries 845 name, 846 sort name, 847 slot, 848
 *    difficulty (enum 2251: 0 Novice .. 4 Grandmaster, 5 Special), 850 map-hint coordinate, 856 members.
 *  - Its progress is clientscript 2193, one switch case per slot: push the quest varp/varbit, push the "complete"
 *    value, gosub 2157 (0 not started, 1 in progress, 2 complete), return. Slots without a case are "not started".
 *  - Grouping mode 1 ("Progress", varbit 4536) draws the groups in the order of enum 2250 (position -> group): the
 *    cache had In progress, Complete, Not started.
 *
 * This tool adds the ten quest structs (new files of config group 26), their slots 185-194 to enum 2252, their progress
 * cases (varps 1910-1919, see the server's OsrsQuestVarps) to clientscript 2193 - appended after the script's last
 * instruction, so no existing branch offset moves - and reorders enum 2250 to In progress, Not started, Complete. One
 * [CacheTransaction] over both production caches (preflight, journal, verify); running it again is a no-op.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.QuestListCacheTool plan|apply`
 */
object QuestListCacheTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"

    const val CONFIG_INDEX = 2
    const val STRUCT_GROUP = 26
    const val ENUM_INDEX = 17
    const val CLIENTSCRIPT_INDEX = 12
    const val QUEST_ENUM = 2252
    const val PROGRESS_ORDER_ENUM = 2250
    const val PROGRESS_SCRIPT = 2193
    const val PROGRESS_HELPER = 2157

    /** Enum 2250 after the change: position -> group (enum 2249: 0 Not started, 1 In progress, 2 Complete). */
    val PROGRESS_ORDER = linkedMapOf(0 to 1, 1 to 0, 2 to 2)

    class NewQuest(val slot: Int, val name: String, val sortName: String, val difficulty: Int, val x: Int, val z: Int, val varp: Int, val complete: Int)

    private const val GE_X = 3164
    private const val GE_Z = 3491

    /** OSRS Wiki difficulties; start coordinates are where the server's version starts (the Grand Exchange hall unless noted). */
    val QUESTS =
        listOf(
            NewQuest(185, "Dragon Slayer II", "Dragon Slayer II (sort)", 4, GE_X, GE_Z, 1910, 10),
            NewQuest(186, "Song of the Elves", "Song of the Elves (sort)", 4, 2568, 3334, 1911, 10),
            NewQuest(187, "Desert Treasure II", "Desert Treasure II (sort)", 4, GE_X, GE_Z, 1912, 10),
            NewQuest(188, "Monkey Madness II", "Monkey Madness II (sort)", 4, GE_X, GE_Z, 1913, 10),
            NewQuest(189, "Sins of the Father", "Sins of the Father (sort)", 3, GE_X, GE_Z, 1914, 1),
            NewQuest(190, "Children of the Sun", "Children of the Sun (sort)", 0, GE_X, GE_Z, 1915, 1),
            NewQuest(191, "Secrets of the North", "Secrets of the North (sort)", 3, GE_X, GE_Z, 1916, 1),
            NewQuest(192, "Bone Voyage", "Bone Voyage (sort)", 1, GE_X, GE_Z, 1917, 1),
            NewQuest(193, "Mage Arena II", "Mage Arena II (sort)", 5, GE_X, GE_Z, 1918, 1),
            NewQuest(194, "Beneath Cursed Sands", "Beneath Cursed Sands (sort)", 3, GE_X, GE_Z, 1919, 1),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val mutations = plan(GAME_CACHE)
        if (mutations.isEmpty()) {
            println("QUEST_LIST already applied")
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
        println("QUEST_LIST transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    /** The mutations still needed against [cache]; empty once everything is in place. */
    fun plan(cache: String): List<CacheMutation> {
        val library = CacheLibrary(cache)
        try {
            val mutations = mutableListOf<CacheMutation>()
            val questEnumBytes = requireNotNull(library.data(ENUM_INDEX, QUEST_ENUM ushr 8, QUEST_ENUM and 0xFF)) { "enum $QUEST_ENUM missing" }
            val questEnum = EnumCodec.decode(questEnumBytes)
            val existingStructs = library.index(CONFIG_INDEX).archive(STRUCT_GROUP)!!.fileIds()
            var nextStruct = existingStructs.maxOrNull()!! + 1
            val structIds = linkedMapOf<Int, Int>()
            QUESTS.forEach { quest ->
                val known = questEnum.values[quest.slot]
                val id = known ?: nextStruct++
                structIds[quest.slot] = id
                val bytes = encodeStruct(quest)
                val current = library.data(CONFIG_INDEX, STRUCT_GROUP, id)
                if (current == null || !current.contentEquals(bytes)) {
                    check(current == null) { "struct $id exists with other content" }
                    mutations += CacheMutation(CONFIG_INDEX, STRUCT_GROUP, id, bytes, "quest struct ${quest.name} (slot ${quest.slot})")
                }
            }
            val newEnum = questEnum.copyWith(structIds)
            val newEnumBytes = EnumCodec.encode(newEnum)
            if (!newEnumBytes.contentEquals(questEnumBytes)) {
                mutations +=
                    CacheMutation(
                        ENUM_INDEX, QUEST_ENUM ushr 8, QUEST_ENUM and 0xFF, newEnumBytes,
                        "enum $QUEST_ENUM: quest slots ${QUESTS.first().slot}-${QUESTS.last().slot}",
                        expectedCurrentSha1 = CacheItemProbeTool.sha1(questEnumBytes),
                    )
            }
            val orderBytes = requireNotNull(library.data(ENUM_INDEX, PROGRESS_ORDER_ENUM ushr 8, PROGRESS_ORDER_ENUM and 0xFF))
            val newOrder = EnumCodec.encode(EnumCodec.decode(orderBytes).copyWith(PROGRESS_ORDER, replace = true))
            if (!newOrder.contentEquals(orderBytes)) {
                mutations +=
                    CacheMutation(
                        ENUM_INDEX, PROGRESS_ORDER_ENUM ushr 8, PROGRESS_ORDER_ENUM and 0xFF, newOrder,
                        "enum $PROGRESS_ORDER_ENUM: In progress, Not started, Complete",
                        expectedCurrentSha1 = CacheItemProbeTool.sha1(orderBytes),
                    )
            }
            val script = requireNotNull(library.data(CLIENTSCRIPT_INDEX, PROGRESS_SCRIPT, 0))
            val patched = patchProgressScript(script)
            if (!patched.contentEquals(script)) {
                mutations +=
                    CacheMutation(
                        CLIENTSCRIPT_INDEX, PROGRESS_SCRIPT, 0, patched, "clientscript $PROGRESS_SCRIPT: progress of slots 185-194",
                        expectedCurrentSha1 = CacheItemProbeTool.sha1(script),
                    )
            }
            return mutations
        } finally {
            library.close()
        }
    }

    fun encodeStruct(quest: NewQuest): ByteArray {
        val out = ByteArrayOutputStream()
        val params =
            listOf<Pair<Int, Any>>(
                847 to quest.slot,
                845 to quest.name,
                846 to quest.sortName,
                848 to quest.difficulty,
                850 to ((quest.x shl 14) or quest.z),
                856 to 1,
            )
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
                writeInt(out, value as Int)
            }
        }
        out.write(0)
        return out.toByteArray()
    }

    // ------------------------------------------------------------------------------------ clientscript 2193

    private const val PUSH_INT = 0
    private const val PUSH_VARP = 1
    private const val RETURN = 21
    private const val GOSUB = 40
    private const val SWITCH = 51

    /** Appends a progress case per new quest; the input unchanged when every slot already has one. */
    fun patchProgressScript(data: ByteArray): ByteArray {
        val instructions = ProductionTabClientScriptPatchTool.decode(data)
        val footerLength = readUnsignedShort(data, data.size - 2)
        val metadataOffset = data.size - footerLength - 2 - 16
        val switchStart = metadataOffset + 16
        val switchCount = data[switchStart].toInt() and 0xFF
        check(switchCount == 1) { "clientscript $PROGRESS_SCRIPT has $switchCount switch tables, expected 1" }
        val caseCount = readUnsignedShort(data, switchStart + 1)
        val cases = linkedMapOf<Int, Int>()
        for (i in 0 until caseCount) {
            val p = switchStart + 3 + i * 8
            cases[readInt(data, p)] = readInt(data, p + 4)
        }
        val switchIndex = instructions.indexOfFirst { it.opcode == SWITCH }
        check(switchIndex >= 0) { "clientscript $PROGRESS_SCRIPT has no switch" }
        val missing = QUESTS.filter { it.slot !in cases }
        if (missing.isEmpty()) {
            QUESTS.forEach { quest ->
                val target = switchIndex + cases.getValue(quest.slot) + 1
                val push = instructions[target]
                check(push.opcode == PUSH_VARP && push.intOperand == quest.varp) { "slot ${quest.slot} case does not read varp ${quest.varp}" }
            }
            return data
        }
        val body = ByteArrayOutputStream()
        var index = instructions.size
        missing.forEach { quest ->
            cases[quest.slot] = index - switchIndex - 1
            writeInstruction(body, PUSH_VARP, quest.varp)
            writeInstruction(body, PUSH_INT, quest.complete)
            writeInstruction(body, GOSUB, PROGRESS_HELPER)
            body.write(RETURN ushr 8)
            body.write(RETURN)
            body.write(0)
            index += 4
        }
        val out = ByteArrayOutputStream(data.size + body.size() + missing.size * 8)
        out.write(data, 0, metadataOffset)
        out.write(body.toByteArray())
        writeInt(out, index)
        out.write(data, metadataOffset + 4, 12)
        val switches = ByteArrayOutputStream()
        switches.write(1)
        switches.write(cases.size ushr 8)
        switches.write(cases.size)
        cases.forEach { (key, offset) ->
            writeInt(switches, key)
            writeInt(switches, offset)
        }
        out.write(switches.toByteArray())
        out.write(switches.size() ushr 8)
        out.write(switches.size())
        val patched = out.toByteArray()
        check(ProductionTabClientScriptPatchTool.decode(patched).size == index) { "patched instruction count mismatch" }
        return patched
    }

    private fun writeInstruction(out: ByteArrayOutputStream, opcode: Int, operand: Int) {
        out.write(opcode ushr 8)
        out.write(opcode)
        writeInt(out, operand)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write(value ushr 24)
        out.write(value ushr 16)
        out.write(value ushr 8)
        out.write(value)
    }

    private fun readUnsignedShort(data: ByteArray, offset: Int): Int = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun readInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)

    // --------------------------------------------------------------------------------------------- enums

    /**
     * Rev-667 EnumType, only as far as the quest enums need it: key/value type chars (1, 2), int default (4) and the int
     * maps - sparse (6) or dense (8: array size, count, u16 index + int value). Anything else is refused.
     */
    object EnumCodec {
        class IntEnum(val keyType: Int, val valueType: Int, val default: Int?, val dense: Boolean, val values: Map<Int, Int>) {
            fun copyWith(extra: Map<Int, Int>, replace: Boolean = false): IntEnum =
                IntEnum(keyType, valueType, default, dense, if (replace) LinkedHashMap(extra) else LinkedHashMap(values).apply { putAll(extra) })
        }

        fun decode(data: ByteArray): IntEnum {
            var p = 0
            var keyType = 'i'.code
            var valueType = 'i'.code
            var default: Int? = null
            var dense = false
            val values = linkedMapOf<Int, Int>()
            while (true) {
                val op = data[p++].toInt() and 0xFF
                when (op) {
                    0 -> break
                    1 -> keyType = data[p++].toInt() and 0xFF
                    2 -> valueType = data[p++].toInt() and 0xFF
                    4 -> {
                        default = readInt(data, p)
                        p += 4
                    }
                    6 -> {
                        val n = readUnsignedShort(data, p)
                        p += 2
                        repeat(n) {
                            values[readInt(data, p)] = readInt(data, p + 4)
                            p += 8
                        }
                    }
                    8 -> {
                        dense = true
                        p += 2
                        val n = readUnsignedShort(data, p)
                        p += 2
                        repeat(n) {
                            values[readUnsignedShort(data, p)] = readInt(data, p + 2)
                            p += 6
                        }
                    }
                    else -> error("enum opcode $op is not supported by QuestListCacheTool")
                }
            }
            return IntEnum(keyType, valueType, default, dense, values)
        }

        fun encode(value: IntEnum): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(1)
            out.write(value.keyType)
            out.write(2)
            out.write(value.valueType)
            if (value.dense) {
                out.write(8)
                val size = (value.values.keys.maxOrNull() ?: -1) + 1
                out.write(size ushr 8)
                out.write(size)
                out.write(value.values.size ushr 8)
                out.write(value.values.size)
                value.values.forEach { (k, v) ->
                    out.write(k ushr 8)
                    out.write(k)
                    writeInt(out, v)
                }
            } else {
                out.write(6)
                out.write(value.values.size ushr 8)
                out.write(value.values.size)
                value.values.forEach { (k, v) ->
                    writeInt(out, k)
                    writeInt(out, v)
                }
            }
            value.default?.let {
                out.write(4)
                writeInt(out, it)
            }
            out.write(0)
            return out.toByteArray()
        }
    }
}
