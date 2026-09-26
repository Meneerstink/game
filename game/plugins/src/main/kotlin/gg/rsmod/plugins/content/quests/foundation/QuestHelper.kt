package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.message.impl.HintArrowMessage
import gg.rsmod.game.message.impl.HintArrowTileMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.message

/**
 * A Quest Helper for the short quests (owner 2026-09-26: "er moet ook een questhelper achtig iets zijn"). The tracked
 * quest's next step is marked with a hint arrow - over its npc or its foe when they are near, otherwise over the step's
 * place (the minimap edge arrow shows the way) - and the step is written in the chatbox whenever it changes. Starting a
 * quest tracks it; "Toggle Map Hint" in the quest list, or ::questhelper, switches it on and off.
 *
 * The same tick spawns a fight step's foe as soon as the player reaches the fight's place, so a boss waits where the
 * quest says it is.
 */
object QuestHelper {
    val TRACKED = AttributeKey<Int>(persistenceKey = "quest_helper_tracked")
    val OFF = AttributeKey<Boolean>(persistenceKey = "quest_helper_off")
    val TIMER = TimerKey()

    private val LAST_TARGET = AttributeKey<String>()
    private val LAST_STEP = AttributeKey<String>()

    /** Hint arrow slot (the gravestone uses 0). */
    const val SLOT = 1
    const val INTERVAL = 3

    /** How far an npc of the step may be for the arrow to sit on it instead of on the step's place. */
    const val NPC_RANGE = 30

    fun tracked(player: Player): ShortQuest? =
        player.attr[TRACKED]?.let { slot -> FoundationQuests.SHORT.firstOrNull { it.slot == slot } }

    fun track(player: Player, quest: ShortQuest) {
        player.attr[TRACKED] = quest.slot
        player.attr.remove(OFF)
        player.attr.remove(LAST_STEP)
        player.attr.remove(LAST_TARGET)
        player.message("<col=0b6fb8>Quest Helper: now tracking ${quest.name}.</col>")
        start(player)
        tick(player)
    }

    fun stop(player: Player, silent: Boolean = false) {
        player.attr.remove(TRACKED)
        player.attr.remove(LAST_STEP)
        player.attr.remove(LAST_TARGET)
        clearArrow(player)
        if (!silent) player.message("<col=0b6fb8>Quest Helper: off.</col>")
    }

    fun toggle(player: Player, quest: ShortQuest) {
        if (tracked(player) == quest) {
            player.attr[OFF] = true
            stop(player)
        } else {
            track(player, quest)
        }
    }

    /** A quest was started: track it unless the player switched the helper off. */
    fun onStarted(player: Player, quest: ShortQuest) {
        if (player.attr[OFF] != true) track(player, quest) else start(player)
    }

    fun start(player: Player) {
        player.timers[TIMER] = INTERVAL
    }

    fun needsTimer(player: Player): Boolean =
        tracked(player) != null || FoundationQuests.SHORT.any { FoundationQuests.inProgress(player, it) }

    private fun clearArrow(player: Player) {
        player.write(HintArrowMessage(slot = SLOT, type = HintArrowMessage.TYPE_CLEAR))
    }

    private fun nearestNpc(player: Player, id: Int): Npc? {
        var best: Npc? = null
        var bestDistance = Int.MAX_VALUE
        player.world.npcs.forEach { npc ->
            if (npc.id != id || npc.tile.height != player.tile.height || (npc.owner != null && npc.owner != player)) return@forEach
            val distance = npc.tile.getDistance(player.tile)
            if (distance <= NPC_RANGE && distance < bestDistance) {
                best = npc
                bestDistance = distance
            }
        }
        return best
    }

    /** One helper tick: fight steps' foes appear on arrival; the tracked quest's arrow and step text are kept current. */
    fun tick(player: Player) {
        FoundationQuests.SHORT.forEach { quest ->
            val stage = quest.stage(player)
            val step = quest.steps.getOrNull(stage - 1) ?: return@forEach
            val fight = step.fight ?: return@forEach
            if (QuestFights.active(player) == null && player.tile.height == fight.tile.height && player.tile.isWithinRadius(fight.tile, QuestFights.REACH)) {
                QuestFights.start(player, quest, stage)
            }
        }
        val quest = tracked(player) ?: return
        if (quest.isFinished(player)) {
            stop(player, silent = true)
            player.message("<col=0b6fb8>Quest Helper: ${quest.name} is complete.</col>")
            return
        }
        val stage = quest.stage(player)
        val lines: List<String>
        var npcId: Int
        var place: Tile?
        if (stage <= 0) {
            lines = listOf("Speak to ${quest.startNpcName} at ${quest.startPlace}.")
            npcId = quest.startNpc
            place = quest.startLocation
        } else {
            val step = quest.steps.getOrNull(stage - 1) ?: return
            lines = step.journal.map { it.replace(Regex("<[^>]+>"), "") }
            npcId = step.npc
            place = step.location
            step.fight?.let { fight ->
                npcId = fight.npc
                place = fight.tile
            }
        }
        val stepKey = "${quest.slot}:$stage"
        if (player.attr[LAST_STEP] != stepKey) {
            player.attr[LAST_STEP] = stepKey
            player.message("<col=0b6fb8>Quest Helper (${quest.name}):</col> ${lines.joinToString(" ")}")
        }
        val foe = QuestFights.active(player)
        val npc = foe ?: nearestNpc(player, npcId)
        val key: String
        if (npc != null) {
            key = "npc:${npc.index}"
            if (player.attr[LAST_TARGET] != key) {
                player.attr[LAST_TARGET] = key
                player.write(HintArrowMessage(slot = SLOT, type = HintArrowMessage.TYPE_NPC, target = npc.index))
            }
            return
        }
        val tile = place ?: return clearArrow(player)
        if (tile.height != player.tile.height) {
            if (player.attr[LAST_TARGET] != "none") {
                player.attr[LAST_TARGET] = "none"
                clearArrow(player)
            }
            return
        }
        // A tile arrow is relative to the client's map base, so it is sent again every tick (a map rebuild moves the base).
        key = "tile:${tile.x},${tile.z}"
        player.attr[LAST_TARGET] = key
        player.write(HintArrowTileMessage(slot = SLOT, x = tile.x, z = tile.z, level = tile.height, height = 0, drawDistance = 0))
    }

    /** After a region rebuild the client forgets tile arrows: send the current one again. */
    fun resend(player: Player) {
        player.attr.remove(LAST_TARGET)
    }
}
