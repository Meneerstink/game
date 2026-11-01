package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.content.quests.Quest
import gg.rsmod.plugins.content.quests.QuestList
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.content.quests.buildQuestOverview
import gg.rsmod.plugins.content.quests.foundation.FoundationQuests
import gg.rsmod.plugins.content.quests.foundation.QuestGuideUi

/** N500: resolve display aliases once, then use the same live Quest as the classic list's overview. */
object QuestTabDetails {
    private fun canonical(name: String): String = when (name) {
        "Monkey Madness I" -> "Monkey Madness"
        "Mourning's End Part II" -> "Mourning's Ends Part II"
        else -> name
    }

    fun quest(name: String): Quest? =
        QuestGuideUi.NAMES.entries.firstOrNull { it.value == name }?.key
            ?: Quest.getQuest(canonical(name))
            ?: (FoundationQuests.SHORT + FoundationQuests.COMPLETED).firstOrNull { it.name == canonical(name) }

    fun open(player: Player, name: String) {
        quest(name)?.let {
            player.buildQuestOverview(it)
            return
        }
        // Most OSRS prerequisite names are references, not playable server quests. QuestStatus completes every
        // non-choice list entry. Do not invent OSRS start locations, requirements or rewards for absent server code.
        val entry = QuestList.ENTRIES.firstOrNull { it.name == canonical(name) }
        player.setComponentText(178, 14, name)
        player.setComponentSprite(178, 79, 0)
        player.setComponentText(178, 52, if (entry != null) "Already completed on this server; no playable start location." else "No playable server implementation.")
        player.setComponentText(178, 55, "No requirements enforced on this server.")
        player.setComponentText(178, 59, "<br>No required items on this server.")
        player.setComponentText(178, 62, "No quest combat on this server.")
        player.setComponentText(178, 66, if (entry != null) "<br>${entry.points} quest points in the server quest list." else "<br>No server rewards defined.")
        player.setComponentText(178, 70, entry?.let { QuestStatus.state(player, it.slot).name.lowercase().replaceFirstChar(Char::uppercaseChar) } ?: "Reference only")
        player.setComponentHidden(178, 27, true)
        player.setComponentHidden(178, 28, false)
        player.openInterface(178, InterfaceDestination.MAIN_SCREEN)
    }
}
