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
                                "interactable=${def.interactable} transforms=${def.transforms?.toList()} " +
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
