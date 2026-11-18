package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.VarpDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.setVarp
import java.io.File

/** Source 7303 before introduction; 7304 afterwards. One physical NPC, personal client menu. */
object WatsonAppearance {
    data class Layout(val root: Int, val before: Int, val after: Int, val control: Int)
    val layout by lazy {
        File(RspsPaths.data("cfg/clue/watson-mapping.tsv")).takeIf(File::isFile)?.readText()?.let(::parse)
    }
    fun parse(text: String): Layout {
        require(text.lineSequence().first().endsWith("applied=true")) { "Watson mapping was not verified/applied" }
        val rows = text.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val f = line.split('\t'); require(f.size == 3)
            (f[0] to f[1].toInt()) to f[2].toInt()
        }.toList()
        require(rows.size == 4 && rows.map { it.first }.distinct().size == 4)
        val map = rows.toMap()
        return Layout(map.getValue("root" to 7303), map.getValue("npc" to 7303),
            map.getValue("npc" to 7304), map.getValue("control" to 7303)).also {
            require(it.root == Npcs.WATSON && it.before in 0..65534 && it.after in 0..65534 &&
                setOf(it.root, it.before, it.after).size == 3 && it.control in 0 until 10000)
        }
    }

    fun validate(world: World) {
        val row = layout ?: run { println("PROBLEM CLUE Watson post-introduction cache batch missing; Check is not yet available"); return }
        val root = world.definitions.get(NpcDef::class.java, row.root)
        world.definitions.get(VarpDef::class.java, row.control)
        check(root.varbit == -1 && root.varp == row.control && root.transforms?.toList() == listOf(row.before, row.after))
        for ((id, options) in listOf(row.before to listOf("Talk-to", null, null, null, null),
            row.after to listOf("Talk-to", null, "Check", null, null))) {
            val def = world.definitions.get(NpcDef::class.java, id)
            check(def.name == "Watson" && def.transforms == null && def.options.map { it?.takeUnless(String::isBlank) } == options)
        }
    }

    fun sync(player: Player, row: Layout? = layout) {
        row ?: return
        val value = if (player.attr[WatsonClues.INTRODUCED] == true) 1 else 0
        if (player.varps.getState(row.control) != value) player.setVarp(row.control, value)
    }
}
