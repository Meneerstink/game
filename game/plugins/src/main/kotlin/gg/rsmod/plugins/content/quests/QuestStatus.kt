package gg.rsmod.plugins.content.quests

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.quests.foundation.BeneathCursedSands
import gg.rsmod.plugins.content.quests.foundation.ChoiceGroup
import gg.rsmod.plugins.content.quests.foundation.FoundationQuests
import gg.rsmod.plugins.content.quests.foundation.QuestChoices
import gg.rsmod.plugins.content.quests.foundation.ShortQuest

/**
 * The one quest status of the server (owner 2026-09-27, quest list part 2). Everything that asks whether a quest is done
 * asks here: the quest list, Quest Points, quest requirements of items and unlocks ([finishedQuest], [QuestRequirement]),
 * the quest point cape and the quest tab's Deadman Groups panel.
 *
 * The rule: every quest of the list is completed, except the eight quests of the permanent quest choices (Magic: Desert
 * Treasure, Lunar Diplomacy, Desert Treasure II; Prayer: The Temple at Senntisten, King's Ransom; Gear: Dragon Slayer
 * II, Song of the Elves, Monkey Madness II). Those are completed only when the account picked them at the Quest Tutor or
 * played the short quest (their stage, stored in the quest's own varp/varbit, reached its end); a started short quest is
 * in progress. Accounts from before the choices ("legacy") have all eight completed ([QuestChoices.applyLegacy]).
 *
 * The client only shows: [sync] writes, for every completed quest, the varp/varbit value clientscript 2193 reads as
 * complete ([QuestList]), and Quest Points (varp 101) / the maximum (varp 904) as sums over the list. Values are only
 * ever raised, so no account loses progress.
 */
object QuestStatus {
    enum class State { NOT_STARTED, IN_PROGRESS, COMPLETED }

    /** The quests that are not simply completed, by list slot: the eight choice quests and (N462) the short Beneath Cursed Sands. */
    val CHOICE_QUESTS: Map<Int, ShortQuest> by lazy { (ChoiceGroup.values().flatMap { it.quests } + BeneathCursedSands).associateBy { it.slot } }

    /** The quest points of the whole list: the quest tab's maximum and the quest point cape's requirement. */
    val MAX_POINTS: Int get() = QuestList.ENTRIES.sumOf { it.points }

    fun state(player: Player, slot: Int): State {
        val choice = CHOICE_QUESTS[slot] ?: return State.COMPLETED
        val stage = choice.stage(player)
        return when {
            stage >= choice.completedValue -> State.COMPLETED
            stage > 0 -> State.IN_PROGRESS
            else -> State.NOT_STARTED
        }
    }

    fun state(player: Player, quest: Quest): State = state(player, quest.slot)

    fun isCompleted(player: Player, quest: Quest): Boolean = state(player, quest) == State.COMPLETED

    fun isStarted(player: Player, quest: Quest): Boolean = state(player, quest) != State.NOT_STARTED

    /**
     * Whether the list quest named [name] is completed: the unlock check of quests without server code (While Guthix
     * Sleeps, Recipe for Disaster, Monkey Madness...). An unknown name throws, so a typo cannot silently lock content.
     */
    fun isCompleted(player: Player, name: String): Boolean =
        state(player, QuestList.ENTRIES.first { it.name == name }.slot) == State.COMPLETED

    /** Current Quest Points: the points of every completed quest of the list. */
    fun points(player: Player): Int = QuestList.ENTRIES.filter { state(player, it.slot) == State.COMPLETED }.sumOf { it.points }

    fun hasAllQuestPoints(player: Player): Boolean = points(player) >= MAX_POINTS

    /**
     * Login: legacy accounts get their eight choice quests (once), then the client is brought in line with the status.
     */
    fun login(player: Player) {
        // N462 owner E2: the old always-complete Beneath Cursed Sands entry stays completed (once, before anything reads it).
        BeneathCursedSands.migrate(player)
        if (QuestChoices.isLegacy(player)) QuestChoices.applyLegacy(player)
        sync(player)
        // N501: a finished Desert Treasure II without the Ring of shadows anywhere gets it once.
        gg.rsmod.plugins.content.quests.foundation.RingOfShadowsReclaim.onLogin(player)
    }

    /**
     * Writes the status to the client: completion values for completed quests (never lowering one), Quest Points and
     * the maximum, then the quest list refresh so colours and order follow at once. Call after any status change.
     */
    fun sync(player: Player) {
        QuestList.ENTRIES.forEach { entry ->
            if (state(player, entry.slot) == State.COMPLETED && entry.progress.read(player) != COMPLETE) entry.progress.complete(player)
        }
        val points = points(player)
        if (player.getVarp(Varps.QUEST_POINTS) != points) player.setVarp(Varps.QUEST_POINTS, points)
        val max = MAX_POINTS
        if (player.getVarp(Varps.MAX_QUEST_POINTS) != max) player.setVarp(Varps.MAX_QUEST_POINTS, max)
        FoundationQuests.refreshQuestList(player)
    }

    /** Clientscript 2193's "complete" (2157/2158 return value). */
    const val COMPLETE = 2
}
