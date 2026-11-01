package gg.rsmod.plugins.content.quests

import gg.rsmod.plugins.content.quests.Quest.Companion.getQuest


/*
 * The quest list's status comes from the server's one quest status (QuestStatus, owner 2026-09-27): every quest is
 * completed except the permanent-choice quests the account has not picked or played. QuestStatus.login writes, for each
 * of the 188 list slots, the varp/varbit value clientscript 2193 reads as complete (QuestList), plus Quest Points (varp
 * 101) and the maximum (varp 904) as sums over the list. It replaces the old per-quest block that set about 170
 * "undeveloped" quests here and left the implemented and OSRS ones to other places.
 */
on_login {
    QuestStatus.login(player)
    // N490 V6: legacy quests must be restored before validating the saved Ruinous book.
    gg.rsmod.plugins.content.mechanics.prayer.RuinousUnlocks.login(player)
    player.setEvents(interfaceId = 190, component = 18, to = 399, setting = 30)
}

/*
 * A row of the quest list.
 *
 * Reported as "quest journal entries do nothing when clicked". Root cause: `getQuest(slot)` only
 * finds a quest that is registered in `Quest.quests`, which is the eleven quests under
 * `content/quests/impl`. Every other row - roughly a hundred and seventy of them, completed by
 * QuestStatus - fell out of the handler on the elvis and the click was swallowed in silence. That is
 * unimplemented content rather than a broken wire, but silence is still the wrong answer, so a row
 * without a quest behind it now says so.
 *
 * The two ops come from clientscript 2162, the list builder, which calls `cc_setop` with
 * "View Quest Overview" and "View Quest Journal" - in that order for one branch and the other way
 * round for the rest, always op1 then op2, with op3 "Toggle Map Hint". Which of the two op1 is
 * therefore depends on the quest's state, and picking the page from `startedQuest` (as this handler
 * already did) is what matches it: an unstarted quest offers the overview first, a started one the
 * journal. op2 is always the other page.
 *
 * op3 is deliberately unbound: this server has no quest map-hint system for it to toggle, and the
 * cache does not say which varp would carry one.
 */
on_button(interfaceId = 190, 18) {
    val quest = getQuest(player.getInteractingSlot())
    if (quest == null) {
        player.message("That quest isn't available on this server yet.")
        return@on_button
    }
    val started = player.startedQuest(quest)
    when (player.getInteractingOpcode()) {
        61 -> if (started) player.buildQuestStages(quest) else player.buildQuestOverview(quest)
        64 -> if (started) player.buildQuestOverview(quest) else player.buildQuestStages(quest)
        // op3 "Toggle Map Hint": the Quest Helper's arrow for a short quest (quests/foundation/QuestHelper).
        4 -> {
            val short = quest as? gg.rsmod.plugins.content.quests.foundation.ShortQuest
            if (short == null || short.isFinished(player)) {
                player.message("There is no map hint for that quest.")
            } else {
                gg.rsmod.plugins.content.quests.foundation.QuestHelper.toggle(player, short)
            }
        }
    }
}

on_button(interfaceId = 178, component = 57) {
    player.setComponentHidden(interfaceId = 178, component = 56, hidden = true)
    player.setComponentHidden(interfaceId = 178, component = 58, hidden = false)
}

on_button(interfaceId = 178, component = 30) {
    player.setComponentHidden(interfaceId = 178, component = 64, hidden = true)
    player.setComponentHidden(interfaceId = 178, component = 65, hidden = false)
}
