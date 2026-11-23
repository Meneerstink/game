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
    /**
     * PROJECT48 F4 (step kind Z): a search step has no npc ([npc] = -1) - the step is done when the player stands within
     * [QuestSearch.radius] of [QuestSearch.tile] (give [location] the same tile so the trip offer and the Quest Helper point there).
     */
    val search: QuestSearch? = null,
    val talk: suspend QueueTask.() -> Boolean = { true },
)

/**
 * The search of a Z step: arriving at [tile] (within [radius], same floor, not in a quest fight, carrying [needs]) finds [text]
 * and hands out [gives]. [missing] is the hint when [needs] are not carried yet.
 */
class QuestSearch(
    val tile: Tile,
    val text: String,
    val radius: Int = 3,
    val gives: List<Pair<Int, Int>> = emptyList(),
    val needs: List<Pair<Int, Int>> = emptyList(),
    val missing: String = "You search the area, but you do not have what you need yet.",
    /** Tools that must be carried but are not consumed by this search. */
    val tools: List<Pair<Int, Int>> = emptyList(),
)

/**
 * The foe of a fight step: a real cache npc with its own combat definition (data/cfg/npcs/combat-defs.json), spawned for
 * the player alone (an owned npc, invisible to everybody else) on [tile], a floor tile the tests check on the real map.
 * [shout] is its overhead line when it appears; [level] is its combat level, shown in the journal so players can prepare.
 */
/** N211: the use-item-on-object that summons a fight step's foe, and the line that tells the player to do it. */
class FightTrigger(val item: Int, val obj: Int, val hint: String)

class QuestFight(
    val npc: Int,
    val name: String,
    val level: Int,
    val tile: Tile,
    val shout: String,
    /**
     * Foes that follow when this one dies, in order, each with its shout: a second form (Damis) or waves (the eight
     * Knight Waves knights). Only the last kill completes the step.
     */
    val chain: List<Pair<Int, String>> = emptyList(),
    /** Waves appear on the fight tile; a second form where the first one fell. */
    val chainAtTile: Boolean = false,
    /**
     * N211 (owner 2026-10-22): a foe that only appears when the player uses [FightTrigger.item] on [FightTrigger.obj] (Dessous: the
     * blessed pot on the Vampyre tomb) - never by just arriving or talking. The step hands the item out ([FightTrigger.item]).
     */
    val trigger: FightTrigger? = null,
) {
    /** The foe after [npc] in the fight, or null when [npc] is the last one. */
    fun next(npc: Int): Pair<Int, String>? {
        val order = listOf(this.npc) + chain.map { it.first }
        val index = order.indexOf(npc)
        return if (index < 0) null else chain.getOrNull(index)
    }

    val secondForm: Int? get() = chain.firstOrNull()?.first

    /**
     * The foe at the start of a sentence: "The suqah", "The elf warrior" - but a name stays bare ("Sir Bedivere", "Dessous";
     * the messages said "The Sir Bedivere", seen 2026-10-08). [name] is lower case exactly for the common nouns.
     */
    val subject: String get() = if (name.first().isLowerCase()) "The $name" else name
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

    /**
     * PROJECT48 O29: the step the Quest Helper shows for [stage] (arrow, panel, map icon). Normally the stage's own step; a
     * [RouteQuest] (the old 2011Scape quests) can pick it from what the player has done inside one stage (Cook's Assistant: egg,
     * flour, milk). Quest logic (talk, fights, searches) keeps using [steps].
     */
    open fun helperStep(player: Player, stage: Int): QuestStep? = steps.getOrNull(stage - 1)

    /**
     * PROJECT48 F2: what a player needs to PLAY this quest (skills, quests, quest points). Checked by [FoundationQuests] before the
     * start conversation; the combat bracket unlocks the quest without any of it. The old short quests keep [QuestRequirements.NONE].
     */
    open val needs: QuestRequirements get() = QuestRequirements.NONE

    /** The reply of the start npc when something in [needs] is missing, one box per three lines. */
    suspend fun QueueTask.refuseStart(missing: List<String>) {
        chatNpc("I could do with your help, but", "you are not ready for this yet.", facialExpression = gg.rsmod.plugins.api.cfg.FacialExpression.THINKING)
        missing.chunked(3).forEach { chatNpc(*it.toTypedArray(), facialExpression = gg.rsmod.plugins.api.cfg.FacialExpression.THINKING) }
    }

    /** What finishing the quest gives (FoundationRewards.complete); the same for the played quest and the choice. */
    abstract val reward: QuestReward

    /** The start conversation; true when the player takes the quest on. */
    abstract suspend fun QueueTask.intro(): Boolean

    val completedValue: Int get() = stages

    /** PROJECT48: called by [FoundationQuests.completeStep] when step [stage] is done (before the next stage is written). */
    open fun onStepDone(player: Player, stage: Int) {}

    /** Optional quest-specific bookkeeping reset, used only when explicitly resetting the quest to stage zero. */
    open fun onReset(player: Player) {}

    /**
     * PROJECT48 F1: a bracket mini-quest keeps its step in its own persistent attribute ([stepKey]) instead of the cache varp/varbit
     * (many 667 quest variables have too few values for the steps, and in-between values can change the world). The quest list reads
     * the cache variable of its slot, so that stays 0 until the quest is finished ([setStage] then completes the list entry).
     * For such a quest [completedValue] is the internal steps.size + 1. The old short quests keep false: nothing changes for them.
     */
    open val ownSteps: Boolean get() = false

    /** Opt-in for extra quests whose list variable can safely mirror the started state as 1. */
    open val showStartedInList: Boolean get() = false

    private var stepKeyCache: gg.rsmod.game.model.attr.AttributeKey<Int>? = null

    /** The persistent step attribute of this quest (ownSteps quests only). */
    val stepKey: gg.rsmod.game.model.attr.AttributeKey<Int>
        get() = stepKeyCache ?: gg.rsmod.game.model.attr.AttributeKey<Int>(persistenceKey = "sq_step_$slot").also { stepKeyCache = it }

    private fun listEntryRead(player: Player): Int =
        gg.rsmod.plugins.content.quests.QuestList.ENTRIES.firstOrNull { it.slot == slot }?.progress?.read?.invoke(player) ?: 0

    fun stage(player: Player): Int {
        if (!ownSteps) return player.getCurrentStage(this)
        // Finished = the quest list entry of this slot is complete (a played quest, a bracket unlock, a legacy account). Never ask
        // QuestStatus here: it asks this function for the choice quests, and getCurrentStage asks this one again.
        if (listEntryRead(player) == gg.rsmod.plugins.content.quests.QuestStatus.COMPLETE) return completedValue
        return (player.attr[stepKey] ?: 0).coerceIn(0, completedValue - 1)
    }

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

    /**
     * The quest's recommended level (owner 2026-10-10: the OSRS / RuneScape Wiki "Recommended" values), or null when the wiki
     * gives none. One rule for the Quest Tutor's "train first" warning, the quest journal's Combat row, the Quest Helper side
     * panel and its overlay before the quest starts.
     */
    open val recommendation: Recommendation? = null

    /** The recommendation as one line ("Recommended: combat level 70+"), or null without one. */
    fun recommendedLine(): String? = recommendation?.let { "Recommended: ${it.text}" }

    /** The recommendation when [player] does not meet it yet (the Quest Tutor then says to train first), else null. */
    fun trainingAdvice(player: Player): Recommendation? = recommendation?.takeUnless { it.met(player) }

    /** Writes [value] and pokes the quest list so the client re-evaluates its colours. */
    fun setStage(player: Player, value: Int) {
        if (ownSteps) {
            if (value >= completedValue) {
                // Complete the list entry (also the special Shield of Arrav / Underground Pass rules) and drop the step attribute.
                gg.rsmod.plugins.content.quests.QuestList.ENTRIES.firstOrNull { it.slot == slot }?.progress?.complete?.invoke(player)
                player.attr.remove(stepKey)
            } else if (value <= 0) {
                // Back to not started (::questreset / ::p48 reset): the list entry too, so the quest list shows it as open.
                gg.rsmod.plugins.content.quests.QuestList.ENTRIES.firstOrNull { it.slot == slot }?.progress?.reset?.invoke(player)
                player.attr.remove(stepKey)
                onReset(player)
            } else {
                player.attr[stepKey] = value
                if (showStartedInList && listEntryRead(player) == 0) {
                    if (usesVarbits) player.setVarbit(questId, 1) else player.setVarp(questId, 1)
                }
            }
            FoundationQuests.refreshQuestList(player)
            return
        }
        if (usesVarbits) player.setVarbit(questId, value) else player.setVarp(questId, value)
        FoundationQuests.refreshQuestList(player)
    }

    override fun getObjective(player: Player, stage: Int): QuestStage {
        val lines = mutableListOf<String>()
        if (stage <= 0) {
            lines += "I can start this quest by speaking to ${red(startNpcName)}"
            lines += "at $startPlace."
            val needLines = needs.lines(player).filter { it.blocking }
            if (needLines.isNotEmpty()) {
                lines += ""
                lines += "Requirements:"
                needLines.forEach { lines += if (it.met) striked(it.text) else red(it.text) }
            }
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

    /**
     * Offers the teleport to [location]; used by the quest's own npcs and, after a step, by the quest itself ([ask] then reads as
     * the player's own choice, [type] [TeleportType.QUEST_RETURN] after a kill).
     */
    suspend fun QueueTask.offerTravel(
        place: String,
        location: Tile,
        npc: Int? = null,
        type: TeleportType = TeleportType.MODERN,
        ask: String = "Can you get me to $place?",
    ) {
        // Already there (a quest that starts where its first step is): nothing to offer. "There" is also beside the npc the trip
        // would land at: Anna, inside Sinclair Mansion, offered "Can you get me to Sinclair Mansion?" (quests run 2026-10-10).
        if (player.tile.height == location.height && player.tile.isWithinRadius(location, 15)) return
        val near = FoundationQuests.landing(player.world, npc, location)
        if (near != location && player.tile.height == near.height && player.tile.isWithinRadius(near, 15)) return
        // Deadman rule: no teleport within 7 seconds of a hit. Right after a quest fight the offer waits for that, so "Enter"
        // always starts the trip instead of "You must be out of combat ..." and a dead end (conform quests 2026-10-09, Lunar
        // Diplomacy step 5 right after the suqah).
        var waited = 0
        while (gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate.teleportDecision(player) ==
            gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT && waited++ < 12
        ) {
            wait(1)
        }
        if (options(ask, "I'll find my own way.", title = "Travel") != 1) return
        // Owner 2026-09-26/27: a Dangerous destination always asks first (interface 382).
        val landing = near
        if (!gg.rsmod.plugins.content.mechanics.pvp.DangerWarning.confirmQuestTravel(this, landing)) return
        player.canTeleport(type) { player.teleport(landing, type, exact = landing != location) }
    }


    companion object {
        const val questCompleteLine = "<col=FF0000>QUEST COMPLETE!"
    }
}

/** A wiki "Recommended" level of a quest: [text] for the player ("combat level 70+"), [met] whether a player reaches it. */
class Recommendation(val text: String, val met: (Player) -> Boolean) {
    companion object {
        fun combat(level: Int) = Recommendation("combat level $level+") { it.combatLevel >= level }

        /** Base levels of [skills], each at least [level]; [names] as the wiki words them. */
        fun skills(level: Int, names: String, vararg skills: Int) =
            Recommendation("$level $names") { p -> skills.all { p.skills.getMaxLevel(it) >= level } }
    }
}
