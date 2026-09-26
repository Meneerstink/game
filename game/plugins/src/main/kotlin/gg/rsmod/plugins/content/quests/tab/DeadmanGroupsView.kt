package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.quests.foundation.ChoiceGroup
import gg.rsmod.plugins.content.quests.foundation.QuestChoices
import gg.rsmod.plugins.content.quests.foundation.QuestGuideUi
import gg.rsmod.plugins.content.quests.foundation.ShortQuest

/**
 * What the quest tab's Deadman Groups panel shows (owner mission 2026-09-26): per group (Magic, Prayer, Gear) the chosen
 * option marked with its quest and unlock, every other option "Unlocked" when its quest is finished, else
 * "Locked - complete <quest>". The groups, their options, names and unlock texts are the existing choice data
 * ([ChoiceGroup], [QuestChoices], [QuestGuideUi]) - nothing is kept twice.
 */
object DeadmanGroupsView {
    const val CHOOSE_NOTE = "Choose your Deadman groups at the Quest Tutor in the mansion."

    enum class State { CHOSEN, UNLOCKED, LOCKED }

    class Option(val quest: ShortQuest, val state: State, val name: String, val status: String)

    class Group(val label: String, val options: List<Option>)

    class View(val showNote: Boolean, val groups: List<Group>)

    /**
     * The one read of a quest's status for this panel. Part 2 of the quest tab points this at the central quest status;
     * nothing else here asks a quest whether it is finished.
     */
    fun questFinished(player: Player, quest: ShortQuest): Boolean = quest.isFinished(player)

    fun name(quest: ShortQuest): String = QuestGuideUi.NAMES[quest] ?: quest.name

    fun unlock(quest: ShortQuest): String = (QuestGuideUi.UNLOCKS[quest] ?: "").replace("<br>", " ")

    fun view(player: Player): View {
        // Accounts from before the choices ("legacy") never choose: every option is already theirs, so there is nothing to
        // point them to.
        val legacy = QuestChoices.isLegacy(player)
        val groups =
            QuestGuideUi.ROWS.map { group ->
                val chosen = QuestChoices.chosen(player, group)
                Group(
                    group.label,
                    group.quests.map { quest ->
                        val name = name(quest)
                        when {
                            quest == chosen -> Option(quest, State.CHOSEN, name, "Chosen - ${unlock(quest)}")
                            questFinished(player, quest) -> Option(quest, State.UNLOCKED, name, "Unlocked")
                            else -> Option(quest, State.LOCKED, name, "Locked - complete $name")
                        }
                    },
                )
            }
        val showNote = !legacy && QuestGuideUi.ROWS.any { QuestChoices.chosen(player, it) == null }
        return View(showNote, groups)
    }

    /** Group order on screen (same as the Quest Tutor's window). */
    val ORDER: List<ChoiceGroup> get() = QuestGuideUi.ROWS
}
