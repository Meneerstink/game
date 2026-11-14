package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.attr.prayedAtStatue
import gg.rsmod.game.model.attr.receivedStaff
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*

/** MA1 has no classic667 quest slot. The existing server quest panel can show its real state without allocating one. */
object MageArenaJournal {
    fun first(player: Player): List<String> = buildList {
        add("Mage Arena I")
        if (!MageArena.completed(player)) {
            add("Speak to Kolodion at the Mage Arena bank.60 Magic is required and cannot be boosted.")
            add("Defeat Kolodion's five forms using Magic.")
            add("Forms defeated: ${(player.attr[MageArena.FORM] ?: 0).coerceIn(0, 5)} / 5.")
            return@buildList
        }
        add("Kolodion has been defeated.")
        if (player.attr[prayedAtStatue] != true) add("Step into the sparkling pool and pray at a god statue to receive a god cape.")
        if (player.attr[receivedStaff] != true) add("Bring the cape to the chamber guardian for its matching god staff.")
        add("Additional god staves cost80,000 coins each. More god capes are available from the statues.")
        GodSpellTraining.SPELLS.forEach { add(GodSpellTraining.progress(player, it)) }
    }

    /** Same existing journal interface and scroll contract as QuestExt.buildQuestStages. */
    fun open(player: Player, name: String, lines: List<String>) {
        player.setComponentHidden(275, 14, true)
        player.setComponentText(275, 2, name)
        for (i in 16..315) player.setComponentText(275, i, "")
        player.setComponentText(275, 16, "<br>")
        lines.forEachIndexed { index, line ->
            player.runClientScript(4265, 275 shl 16 or (17 + index), 0x000080)
            player.setComponentText(275, 17 + index, line)
        }
        player.runClientScript(1208, 275 shl 16 or 7, 275 shl 16 or 6, lines.size * 25, 0)
        player.openInterface(275, InterfaceDestination.MAIN_SCREEN_FULL)
    }
}
