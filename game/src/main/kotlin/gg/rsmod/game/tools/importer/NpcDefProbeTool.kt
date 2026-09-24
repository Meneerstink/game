package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef

/**
 * Read-only diagnostic: dumps [NpcDef.options] (the real interact-menu strings, cache opcodes
 * 30-34) for one or more npc ids, or searches every npc for an option/name substring - the same
 * "derive the binding set from the cache, don't hand-write an id list" pattern already used by
 * [ItemParamProbeTool] and [ObjectDefProbeTool]. Loaded through the same production
 * [DefinitionSet]/[NpcDef] path the server itself boots with.
 *
 * Usage: `./gradlew :game:runNpcDefProbeTool --args="<cachePath> <npcId> [npcId ...]"`
 *        `./gradlew :game:runNpcDefProbeTool --args="<cachePath> option <substring>"`
 *        `./gradlew :game:runNpcDefProbeTool --args="<cachePath> name <substring>"`
 */
object NpcDefProbeTool {
    /**
     * Walks a rev-667 npc definition far enough to read opcode 1 (the model list), mirroring `NpcDef.decode`'s
     * opcode order. Only the opcodes that appear before or between model data need real handling; the walk stops
     * at the first opcode it cannot size, which is enough because opcode 1 is written first in practice.
     */
    private fun readNpcModels(bytes: ByteArray): List<Int> {
        var p = 0
        fun u8(): Int = bytes[p++].toInt() and 0xFF
        fun u16(): Int = (u8() shl 8) or u8()
        fun str() { while (p < bytes.size && bytes[p].toInt() != 0) p++; p++ }
        while (p < bytes.size) {
            when (val op = u8()) {
                0 -> return emptyList()
                1 -> { val n = u8(); return (0 until n).map { u16() } }
                2 -> str()
                12 -> u8()
                13, 14, 15, 16 -> u16()
                17 -> { u16(); u16(); u16(); u16() }
                in 30..34 -> str()
                40, 41 -> { val n = u8(); repeat(n) { u16(); u16() } }
                60 -> { val n = u8(); repeat(n) { u16() } }
                93 -> u16()
                95, 97, 98 -> u16()
                99, 100, 101 -> if (op == 99 || op == 100) Unit else u8()
                102 -> u16()
                106, 118 -> { u16(); u16(); val n = u8(); repeat(n + 1) { u16() } }
                107, 109 -> Unit
                111, 112, 113, 114 -> Unit
                115 -> { u8(); u8() }
                else -> return emptyList()
            }
        }
        return emptyList()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) {
            "Usage: <cachePath> <npcId> [npcId ...] | <cachePath> option <substring> | " +
                "<cachePath> name <substring> | <cachePath> sounds"
        }
        val cachePath = args[0]

        val library = CacheLibrary(cachePath)
        try {
            val definitions = DefinitionSet()
            definitions.load(library, NpcDef::class.java)
            @Suppress("UNCHECKED_CAST")
            val npcs = definitions.getAll(NpcDef::class.java) as Map<Int, NpcDef>

            when (args[1]) {
                /*
                 * "Why is this npc invisible?" - the models an npc draws are cache opcode 1, which `NpcDef.decode`
                 * reads and throws away (the server never needs them; only the client draws). So an imported npc
                 * can reference a model that was never written into index 7 and nothing server-side notices: the
                 * npc exists, walks, fights and is completely invisible. This mode reads the raw definition, pulls
                 * opcode 1 out of it and reports, per model, whether index 7 actually holds it.
                 */
                "models" -> {
                    require(args.size >= 3) { "Usage: <cachePath> models <npcId> [npcId ...]" }
                    val modelIndex = library.index(7)
                    args.drop(2).map { it.toInt() }.forEach { id ->
                        val raw = library.data(18, id ushr 7, id and 0x7F)
                        if (raw == null) {
                            println("NPC_$id=ABSENT")
                            return@forEach
                        }
                        val models = readNpcModels(raw)
                        val missing = models.filter { modelIndex.archive(it) == null }
                        val name = npcs[id]?.name ?: "?"
                        println(
                            "NPC_$id name=$name models=$models missing=$missing " +
                                if (models.isEmpty()) "VERDICT=NO_MODELS_INVISIBLE" else if (missing.isEmpty()) "VERDICT=OK" else "VERDICT=MISSING_MODELS_INVISIBLE",
                        )
                    }
                }
                "option" -> {
                    require(args.size == 3) { "Usage: <cachePath> option <substring>" }
                    val needle = args[2].lowercase()
                    var hits = 0
                    npcs.keys.sorted().forEach { id ->
                        val def = npcs.getValue(id)
                        val idx = def.options.indexOfFirst { it?.lowercase()?.contains(needle) == true }
                        if (idx != -1) {
                            hits++
                            println("NPC_$id name=${def.name} option${idx + 1}='${def.options[idx]}' options=${def.options.toList()}")
                        }
                    }
                    println("OPTION_SEARCH='$needle' SCANNED=${npcs.size} HITS=$hits")
                }
                "sounds" -> {
                    var hits = 0
                    npcs.keys.sorted().forEach { id ->
                        val def = npcs.getValue(id)
                        if (def.readySound != -1 || def.walkSound != -1 || def.runSound != -1 || def.crawlSound != -1) {
                            hits++
                            println(
                                "NPC_$id name=${def.name} ready=${def.readySound} walk=${def.walkSound} " +
                                    "run=${def.runSound} crawl=${def.crawlSound}",
                            )
                        }
                    }
                    println("SOUNDS_SCAN SCANNED=${npcs.size} HITS=$hits")
                }
                "name" -> {
                    require(args.size == 3) { "Usage: <cachePath> name <substring>" }
                    val needle = args[2].lowercase()
                    var hits = 0
                    npcs.keys.sorted().forEach { id ->
                        val def = npcs.getValue(id)
                        if (def.name.lowercase().contains(needle)) {
                            hits++
                            println("NPC_$id name=${def.name} options=${def.options.toList()} examine=${def.examine}")
                        }
                    }
                    println("NAME_SEARCH='$needle' SCANNED=${npcs.size} HITS=$hits")
                }
                else -> {
                    val npcIds = args.drop(1).map { it.toInt() }
                    for (id in npcIds) {
                        val def = npcs[id]
                        if (def == null) {
                            println("NPC_$id=ABSENT")
                            continue
                        }
                        println(
                            "NPC_$id name=${def.name} size=${def.size} combatLevel=${def.combatLevel} " +
                                "interactable=${def.interactable} transforms=${def.transforms?.toList()} transformVarbit=${def.varbit} transformVarp=${def.varp} " +
                                "basId=${def.basId} walkMask=${def.walkMask} headIcon=${def.headIcon} " +
                                "width=${def.width} length=${def.length}",
                        )
                        println("  OPTIONS=${def.options.toList()}")
                        println("  EXAMINE=${def.examine}")
                        println(
                            "  SOUNDS ready=${def.readySound} walk=${def.walkSound} " +
                                "run=${def.runSound} crawl=${def.crawlSound}",
                        )
                    }
                }
            }
        } finally {
            library.close()
        }
    }
}
