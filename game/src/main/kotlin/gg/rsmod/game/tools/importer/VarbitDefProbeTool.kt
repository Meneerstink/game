package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef

/**
 * Read-only diagnostic: dumps [VarbitDef.varp]/[startBit]/[endBit] for one or more varbit ids via
 * the production `DefinitionSet`/`VarbitDef` path - the bit range a varbit occupies within its
 * parent varp, which tells you how many discrete values it can hold (`2^(endBit-startBit+1)`).
 *
 * Usage: `./gradlew :game:runVarbitDefProbeTool --args="<cachePath> <varbitId> [varbitId ...]"`
 */
object VarbitDefProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <cachePath> <varbitId> [varbitId ...]" }
        val library = CacheLibrary(args[0])
        try {
            val definitions = DefinitionSet()
            definitions.load(library, VarbitDef::class.java)
            if (args[1] == "byvarp") {
                val targets = args.drop(2).map { it.toInt() }.toSet()
                @Suppress("UNCHECKED_CAST")
                val all = definitions.getAll(VarbitDef::class.java) as Map<Int, VarbitDef>
                all.entries.filter { it.value.varp in targets }.sortedBy { it.key }.forEach { (id, def) ->
                    val states = 1 shl (def.endBit - def.startBit + 1)
                    println("VARBIT_$id varp=${def.varp} bits=${def.startBit}..${def.endBit} states=$states")
                }
            } else {
                args.drop(1).map { it.toInt() }.forEach { id ->
                    val def = definitions.get(VarbitDef::class.java, id)
                    val states = 1 shl (def.endBit - def.startBit + 1)
                    println("VARBIT_$id varp=${def.varp} bits=${def.startBit}..${def.endBit} states=$states")
                }
            }
        } finally {
            library.close()
        }
    }
}
