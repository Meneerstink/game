package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import java.io.File

/** OSRS equipped-item opt-in; the existing storage transaction reads this same saved flag. */
object TreasureTrailStashSetting {
    data class Layout(val button: Int, val checkbox: Int, val off: Int, val on: Int)
    const val GROUP = 261
    val layout by lazy {
        val file = File(RspsPaths.data("cfg/clue/stash-setting-mapping.tsv"))
        if (file.isFile) parseMapping(file.readText()) else null
    }

    fun parseMapping(text: String): Layout {
        require(text.lineSequence().firstOrNull()?.endsWith("applied=true") == true)
        val rows = text.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val fields = line.split('\t')
            require(fields.size == 3 && fields[1] == "6245")
            fields[0] to fields[2].toInt()
        }.toList()
        require(rows.map { it.first }.distinct().size == rows.size)
        val map = rows.toMap()
        require(map.keys == setOf("interface", "button", "checkbox", "label", "off", "on"))
        require(map.getValue("interface") == GROUP)
        val button = map.getValue("button")
        require(button in 26..65532 && map.getValue("checkbox") == button + 1 && map.getValue("label") == button + 2)
        require(map.getValue("off") >= 0 && map.getValue("on") >= 0 && map.getValue("off") != map.getValue("on"))
        return Layout(button, button + 1, map.getValue("off"), map.getValue("on"))
    }

    fun sync(player: Player) {
        val row = layout ?: return
        player.setInterfaceEvents(GROUP, row.button, -1..-1, 0x2)
        player.setComponentSprite(GROUP, row.checkbox, if (player.attr[TreasureTrailStashes.TAKE_EQUIPPED] == true) row.on else row.off)
    }

    fun toggle(player: Player) {
        if (layout == null || !player.interfaces.isVisible(GROUP)) return
        player.attr[TreasureTrailStashes.TAKE_EQUIPPED] = player.attr[TreasureTrailStashes.TAKE_EQUIPPED] != true
        sync(player)
    }
}
