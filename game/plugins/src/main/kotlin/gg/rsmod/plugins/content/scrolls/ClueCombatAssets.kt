package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.model.World
import java.io.File

/** Applied source-sequence identities only. Allocator proposals must never become runtime IDs. */
object ClueCombatAssets {
    val sources=setOf(376,378,390,424,426,427,727,836,1162,1167,4320,4321,4322,6957,6958,6959)
    fun parse(text: String): Map<Int,Int> {
        require(text.lineSequence().firstOrNull()?.endsWith("applied=true")==true)
        val rows=text.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val f=line.split('\t');require(f.size==3 && f[0]=="seq");f[1].toInt() to f[2].toInt()
        }.toList()
        require(rows.map { it.first }.toSet()==sources && rows.map { it.first }.distinct().size==rows.size)
        require(rows.map { it.second }.distinct().size==rows.size && rows.all { it.second in 0..65534 })
        return rows.toMap()
    }
    private val mappings by lazy {
        File(RspsPaths.data("cfg/clue/combat-assets.tsv")).takeIf(File::isFile)?.readText()?.let(::parse).orEmpty()
    }
    fun seq(world: World,source: Int): Int? = mappings[source]?.takeIf {
        world.definitions.getNullable(AnimDef::class.java,it)!=null
    }
}
