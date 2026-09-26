package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.quests.Quest
import gg.rsmod.plugins.content.quests.QuestStage
import gg.rsmod.plugins.content.quests.getCurrentStage
import gg.rsmod.plugins.content.quests.red
import gg.rsmod.plugins.content.quests.striked

/**
 * One step of a [ShortQuest]: while the quest stage is this step's number the player has to speak to [npc].
 *
 * [journal] is the open quest-journal line for the step, [done] the struck-through line once it is behind the player.
 * [location] (with [place], "the Bandit Camp") is where the quest's own npcs offer to send the player, so a short
 * quest fits in about five minutes. [gives] are the quest items handed out by this step: when a player lost them, the
 * npc of this step hands them out again while the next step is open. [talk] is the step's conversation; it returns true
 * when the step is done (false keeps the stage, e.g. when the player does not carry what the npc needs).
 */
class QuestStep(
    val npc: Int,
    val journal: List<String>,
    val done: List<String>,
    val place: String? = null,
    val location: Tile? = null,
    val gives: List<Pair<Int, Int>> = emptyList(),
    val talk: suspend QueueTask.() -> Boolean,
)

/**
 * A short playable quest (owner 2026-09-26, about five minutes each): start at [startNpc], work through [steps] and
 * finish on the last one. The stage is stored in the quest's own varp or varbit ([questId]) - the value the cache quest
 * list colours by - so it is saved with the account and survives logout and restart:
 *
 *  - 0 not started, 1..steps.size the open step, [stages] (the cache "complete" value, e.g. 15 for Desert Treasure)
 *    once finished. The quest list shows red, yellow and green from exactly those values.
 *
 * Completion always goes through [FoundationRewards.complete], the one place that hands out the quest's unlock, XP and
 * items; the permanent quest choice at the Quest Guide uses the same call.
 */
abstract class ShortQuest(
    name: String,
    startPoint: String,
    rewards: String,
    pointReward: Int,
    questId: Int,
    slot: Int,
    completedValue: Int,
    usesVarbits: Boolean,
    val startNpc: Int,
    val startNpcName: String,
    val startPlace: String,
    val startLocation: Tile?,
) : Quest(
        name = name,
        startPoint = startPoint,
        requirements = emptyList(),
        requiredItems = "None.",
        combat = "None.",
        rewards = rewards,
        pointReward = pointReward,
        questId = questId,
        spriteId = 0,
        slot = slot,
        stages = completedValue,
        usesVarbits = usesVarbits,
    ) {
    abstract val steps: List<QuestStep>

    /** What finishing the quest gives (FoundationRewards.complete); the same for the played quest and the choice. */
    abstract val reward: QuestReward

    /** The start conversation; true when the player takes the quest on. */
    abstract suspend fun QueueTask.intro(): Boolean

    val completedValue: Int get() = stages

    fun stage(player: Player): Int = player.getCurrentStage(this)

    fun isFinished(player: Player): Boolean = stage(player) >= completedValue

    /** Writes [value] and pokes the quest list so the client re-evaluates its colours. */
    fun setStage(player: Player, value: Int) {
        if (usesVarbits) player.setVarbit(questId, value) else player.setVarp(questId, value)
        FoundationQuests.refreshQuestList(player)
    }

    override fun getObjective(player: Player, stage: Int): QuestStage {
        val lines = mutableListOf<String>()
        if (stage <= 0) {
            lines += "I can start this quest by speaking to ${red(startNpcName)}"
            lines += "at $startPlace."
            return QuestStage(lines)
        }
        val finished = stage >= completedValue
        lines += striked("I agreed to help $startNpcName.")
        steps.forEachIndexed { index, step ->
            val number = index + 1
            when {
                finished || number < stage -> step.done.forEach { lines += striked(it) }
                number == stage -> lines += step.journal
            }
        }
        if (finished) {
            lines += ""
            lines += questCompleteLine
        }
        return QuestStage(lines)
    }

    override fun finishQuest(player: Player) {
        FoundationRewards.complete(player, this)
    }

    /** Offers the teleport to [location]; used by the quest's own npcs. */
    suspend fun QueueTask.offerTravel(place: String, location: Tile) {
        if (options("Can you get me to $place?", "I'll find my own way.", title = "Travel") != 1) return
        player.canTeleport(TeleportType.MODERN) { player.teleport(location, TeleportType.MODERN) }
    }


    companion object {
        const val questCompleteLine = "<col=FF0000>QUEST COMPLETE!"
    }
}
