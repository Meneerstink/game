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
    /** A fight step: [npc] sends the player into it, killing [fight]'s foe completes the step (QuestFights). */
    val fight: QuestFight? = null,
    /** The Quest Helper's tip for this step: what to bring, or the boss's mechanic. */
    val tip: String? = null,
    val talk: suspend QueueTask.() -> Boolean,
)

/**
 * The foe of a fight step: a real cache npc with its own combat definition (data/cfg/npcs/combat-defs.json), spawned for
 * the player alone (an owned npc, invisible to everybody else) on [tile], a floor tile the tests check on the real map.
 * [shout] is its overhead line when it appears; [level] is its combat level, shown in the journal so players can prepare.
 */
class QuestFight(
    val npc: Int,
    val name: String,
    val level: Int,
    val tile: Tile,
    val shout: String,
    /** Where the player is taken back to after the kill (a fight the step npc sailed or sent them to), or null. */
    val returnTo: Tile? = null,
    /**
     * Foes that follow when this one dies, in order, each with its shout: a second form (Damis) or waves (the eight
     * Knight Waves knights). Only the last kill completes the step.
     */
    val chain: List<Pair<Int, String>> = emptyList(),
    /** Waves appear on the fight tile; a second form where the first one fell. */
    val chainAtTile: Boolean = false,
) {
    /** The foe after [npc] in the fight, or null when [npc] is the last one. */
    fun next(npc: Int): Pair<Int, String>? {
        val order = listOf(this.npc) + chain.map { it.first }
        val index = order.indexOf(npc)
        return if (index < 0) null else chain.getOrNull(index)
    }

    val secondForm: Int? get() = chain.firstOrNull()?.first
}

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

    /** Completed in the server's one quest status (QuestStatus reads this quest's stage against [completedValue]). */
    fun isFinished(player: Player): Boolean = gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(player, this)

    /**
     * The highest-level foe of every fight step, chain foes included (their cache combat level), as name to level, or null
     * for a quest without fights. Owner 2026-10-08 (OWNER_QUESTIONS Q17 A): the Quest Tutor tells a player below it to
     * train combat first.
     */
    fun strongestFoe(player: Player): Pair<String, Int>? {
        val defs = player.world.definitions
        return steps.mapNotNull { it.fight }.flatMap { fight ->
            listOf(fight.name to fight.level) + fight.chain.mapNotNull { (id, _) ->
                defs.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, id)?.let { it.name to it.combatLevel }
            }
        }.maxByOrNull { it.second }
    }

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
    suspend fun QueueTask.offerTravel(place: String, location: Tile, npc: Int? = null) {
        // Already there (a quest that starts where its first step is): nothing to offer.
        if (player.tile.height == location.height && player.tile.isWithinRadius(location, 15)) return
        if (options("Can you get me to $place?", "I'll find my own way.", title = "Travel") != 1) return
        // Owner 2026-09-26/27: a Dangerous destination always asks first (interface 382).
        val landing = FoundationQuests.landing(player.world, npc, location)
        if (!gg.rsmod.plugins.content.mechanics.pvp.DangerWarning.confirmQuestTravel(this, landing)) return
        player.canTeleport(TeleportType.MODERN) { player.teleport(landing, TeleportType.MODERN, exact = landing != location) }
    }


    companion object {
        const val questCompleteLine = "<col=FF0000>QUEST COMPLETE!"
    }
}
