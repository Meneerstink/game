package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.plugins.content.quests.QuestList
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.content.quests.foundation.FoundationQuests
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestBrackets
import gg.rsmod.plugins.content.quests.foundation.QuestChoices

/*
 * PROJECT48 F6: test commands of the bracket mini-quests - TEST SERVER ONLY (same guard as ::armageddonquests).
 *   ::p48 status <slot>         state, step, requirements met/missing
 *   ::p48 step <slot> <n>       sets the step (n = steps+1 completes the list entry without a reward)
 *   ::p48 reset <slot>          back to not started, reward record removed (the quest can be played and rewarded again)
 *   ::p48 cohort <legacy|choice|armageddon>
 *   ::p48 req <slot>            gives the quest's skill levels and completes its prerequisite quests
 *   ::p48 list                  slots of all registered bracket quests
 */
on_command("p48", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    if (!gg.rsmod.util.io.SharedDataFiles.testServer()) return@on_command player.message("Only on the test server.")
    val args = player.getCommandArgs()
    val sub = args.getOrNull(0) ?: return@on_command player.message("p48 status|step|reset|cohort|req|list ...")
    if (sub == "cohort") {
        val cohort = args.getOrNull(1)
        if (cohort !in listOf(QuestChoices.LEGACY, QuestChoices.CHOICE, QuestChoices.ARMAGEDDON)) return@on_command player.message("p48 cohort legacy|choice|armageddon")
        player.attr[QuestChoices.COHORT] = cohort!!
        QuestStatus.sync(player)
        return@on_command player.message("p48: cohort = $cohort")
    }
    if (sub == "list") {
        return@on_command player.message("p48 quests: " + BracketQuests.ALL.joinToString(", ") { "${it.slot}=${it.name}" }.take(480))
    }
    val slot = args.getOrNull(1)?.toIntOrNull() ?: return@on_command player.message("p48 $sub <slot>")
    val quest = BracketQuests.bySlot(slot) ?: return@on_command player.message("p48: slot $slot is not a bracket quest (p48 list).")
    when (sub) {
        "status" -> {
            val lines = quest.needs.lines(player).joinToString("; ") { (if (it.met) "ok " else "MISSING ") + it.text }
            player.message(
                "p48 ${quest.name}: state=${QuestStatus.state(player, quest)} step=${quest.stage(player)}/${quest.steps.size} " +
                    "rewarded=${FoundationRewards.isRewarded(player, quest)} cohort=${QuestChoices.cohort(player)} req=[$lines]",
            )
        }
        "step" -> {
            val n = args.getOrNull(2)?.toIntOrNull() ?: return@on_command player.message("p48 step <slot> <n>")
            quest.setStage(player, n)
            QuestStatus.sync(player)
            player.message("p48 ${quest.name}: step set to $n (now ${quest.stage(player)}).")
        }
        "reset" -> {
            player.attr.remove(FoundationRewards.rewardedKey(quest))
            quest.setStage(player, 0)
            QuestStatus.sync(player)
            player.message("p48 ${quest.name}: reset (state=${QuestStatus.state(player, quest)}).")
        }
        "req" -> {
            quest.needs.skills.forEach { (skill, level) -> if (player.skills.getMaxLevel(skill) < level) player.skills.setBaseLevel(skill, level) }
            quest.needs.quests.forEach { name -> QuestList.ENTRIES.firstOrNull { it.name == name }?.progress?.complete?.invoke(player) }
            if (quest.needs.questPoints > 0) QuestList.ENTRIES.filter { it.points > 0 }.forEach { if (QuestStatus.points(player) < quest.needs.questPoints) it.progress.complete(player) }
            QuestStatus.sync(player)
            player.message("p48 ${quest.name}: requirements given (met=${quest.needs.met(player)}).")
        }
        else -> player.message("p48 status|step|reset|cohort|req|list ...")
    }
}
