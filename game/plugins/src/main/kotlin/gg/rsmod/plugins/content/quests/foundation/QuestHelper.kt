package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.message.impl.HintArrowMessage
import gg.rsmod.game.message.impl.HintArrowTileMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * A Quest Helper for the short quests (owner 2026-09-26: "er moet ook een questhelper achtig iets zijn"). The tracked
 * quest's next step is marked with a hint arrow - over its npc or its foe when they are near, otherwise over the step's
 * place (the minimap edge arrow shows the way) - and the step is written in the chatbox whenever it changes. Starting a
 * quest tracks it; "Toggle Map Hint" in the quest list, or ::questhelper, switches it on and off.
 *
 * Owner 2026-10-07: the helper is a plugin of the 78 Plugins sidebar like the others ([PLUGIN] in ClientHudConfig, the
 * RuneLite Quest Helper's own config items): switched off, no arrow, panel or chat line is shown and starting a quest
 * tracks nothing; each part can also be switched off on its own. Fight steps still bring their foe (quest logic, not help).
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

    private val FLOORS = listOf("ground floor", "first floor", "second floor", "third floor")

    /** Staircases the helper sends a player to for an npc on another floor: the Grand Exchange hall's two (GeHomeHall). */
    private val STAIRS: List<Tile> by lazy { gg.rsmod.plugins.content.areas.grandexchange.GeHomeHall.STAIRS.map { it.floor } }

    /** Hint arrow slot (the gravestone uses 0). */
    const val SLOT = 1
    const val INTERVAL = 3

    /** How far an npc of the step may be for the arrow to sit on it instead of on the step's place. */
    const val NPC_RANGE = 30

    const val PLUGIN = "questhelper"

    private fun setting(player: Player, key: String): Boolean =
        gg.rsmod.plugins.content.mechanics.hud.ClientHudConfig.bool(player, "$PLUGIN.$key")

    /** The plugin's main switch (78 Plugins sidebar, `hud questhelper on|off`). */
    fun enabled(player: Player): Boolean = setting(player, "enabled")

    /** A plugin switch changed: clear what is shown and draw it again on the next tick with the new settings. */
    fun refresh(player: Player) {
        player.attr.remove(LAST_STEP)
        player.attr.remove(LAST_TARGET)
        clearArrow(player)
        Panel.close(player)
        if (needsTimer(player)) {
            start(player)
            tick(player)
        }
    }

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
        Panel.close(player)
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

    /** A quest was started: track it unless the player switched the helper (or its auto start) off. */
    fun onStarted(player: Player, quest: ShortQuest) {
        if (player.attr[OFF] != true && enabled(player) && setting(player, "autoStartQuests")) track(player, quest) else start(player)
    }

    fun start(player: Player) {
        player.timers[TIMER] = INTERVAL
    }

    fun needsTimer(player: Player): Boolean =
        tracked(player) != null || FoundationQuests.SHORT.any { FoundationQuests.inProgress(player, it) }

    private fun clearArrow(player: Player) {
        player.write(HintArrowMessage(slot = SLOT, type = HintArrowMessage.TYPE_CLEAR))
    }

    /** The step's npc near the player, on any floor (the Grand Exchange hall's quest npcs stand on its first floor). */
    private fun nearestNpc(player: Player, id: Int): Npc? {
        var best: Npc? = null
        var bestDistance = Int.MAX_VALUE
        player.world.npcs.forEach { npc ->
            if (npc.id != id || (npc.owner != null && npc.owner != player)) return@forEach
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
        if (!enabled(player)) {
            // Plugin off: nothing on screen; the tracked quest is kept for when it is switched on again.
            if (player.attr[LAST_TARGET] != "off") {
                player.attr[LAST_TARGET] = "off"
                player.attr.remove(LAST_STEP)
                clearArrow(player)
                Panel.close(player)
            }
            return
        }
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
            if (setting(player, "showStepMessages")) player.message("<col=0b6fb8>Quest Helper (${quest.name}):</col> ${lines.joinToString(" ")}")
        }
        val step = quest.steps.getOrNull(stage - 1)
        val foe = QuestFights.active(player)
        val found = foe ?: nearestNpc(player, npcId)
        // Owner 2026-10-07 ("quest helper stays stuck"): an npc on another floor (the hall's first floor) is reached by the
        // stairs - the arrow goes to the nearest staircase and the panel names the floor, never to an empty tile below it.
        val goal = found?.tile ?: place
        val otherFloor = goal != null && goal.height != player.tile.height && goal.isWithinRadius(Tile(player.tile.x, player.tile.z, goal.height), NPC_RANGE)
        val floorHint = if (otherFloor) " - ${FLOORS.getOrElse(goal!!.height) { "level ${goal.height}" }} (take the stairs ${if (goal.height > player.tile.height) "up" else "down"})" else ""
        if (setting(player, "showOverlayPanel")) {
            val placeName = (if (stage <= 0) quest.startPlace else step?.place)?.let { it + floorHint }
            Panel.show(player, quest, stage, lines.joinToString(" "), placeName, place, step?.tip)
        } else {
            Panel.close(player)
        }
        if (!setting(player, "showMiniMapArrow")) {
            if (player.attr[LAST_TARGET] != "none") {
                player.attr[LAST_TARGET] = "none"
                clearArrow(player)
            }
            return
        }
        val npc = found?.takeIf { it.tile.height == player.tile.height }
        val key: String
        if (otherFloor) {
            val stairs = STAIRS.map { Tile(it.x, it.z, player.tile.height) }.minByOrNull { it.getDistance(player.tile) }
            if (stairs != null && stairs.isWithinRadius(player.tile, NPC_RANGE)) {
                player.attr[LAST_TARGET] = "stairs:${stairs.x},${stairs.z}"
                player.write(HintArrowTileMessage(slot = SLOT, x = stairs.x, z = stairs.z, level = stairs.height, height = 0, drawDistance = 0))
                return
            }
        }
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

    /**
     * The panel in the top-left corner of the game view (interface 1166, QuestHelperPanelImportTool): quest, "Step n of N"
     * with a progress bar, the full instruction, where to go (red when the place is Dangerous) and the step's tip. It
     * lives in the main-screen overlay slot and steps aside while another overlay (a dark cave's light, a minigame) needs
     * that slot; the next helper tick puts it back.
     */
    object Panel {
        private const val ID = gg.rsmod.game.tools.importer.QuestHelperPanelImportTool.INTERFACE_ID
        private val SHOWN = AttributeKey<String>()

        private fun slot(player: Player): Pair<Int, Int> {
            val mode = player.interfaces.displayMode
            val parent = gg.rsmod.plugins.api.getDisplayComponentId(mode)
            val child = gg.rsmod.plugins.api.getChildId(gg.rsmod.plugins.api.InterfaceDestination.MAIN_SCREEN_OVERLAY, mode)
            return parent to child
        }

        fun show(player: Player, quest: ShortQuest, stage: Int, instruction: String, placeName: String?, place: Tile?, tip: String?) {
            val (parent, child) = slot(player)
            val there = player.interfaces.getInterfaceAt(parent, child)
            if (there != -1 && there != ID) {
                player.attr.remove(SHOWN)
                return
            }
            val dangerous = place != null && gg.rsmod.plugins.content.mechanics.pvp.AreaState.isDangerous(place)
            val total = quest.steps.size
            val done = if (stage <= 0) 0 else stage - 1
            val key = "${quest.slot}:$stage:$dangerous:${player.interfaces.displayMode}:$placeName"
            if (there == ID && player.attr[SHOWN] == key) return
            if (there != ID) player.openInterface(ID, gg.rsmod.plugins.api.InterfaceDestination.MAIN_SCREEN_OVERLAY)
            player.attr[SHOWN] = key
            val L = gg.rsmod.game.tools.importer.QuestHelperPanelImportTool
            player.setComponentText(ID, L.TITLE, quest.name.substringBefore(" - "))
            player.setComponentText(ID, L.STEP, if (stage <= 0) "Not started" else "Step $stage of $total")
            val filled = if (total == 0) 0 else (L.SEGMENTS * done + total / 2) / total
            for (i in 0 until L.SEGMENTS) player.setComponentHidden(ID, L.SEGMENT_FIRST + i, hidden = i >= filled)
            player.setComponentText(ID, L.INSTRUCTION, instruction)
            val where = placeName?.replaceFirstChar { it.uppercase() } ?: "-"
            player.setComponentText(ID, L.GOTO, "Go to: $where" + if (dangerous) " <col=ff3030>(Dangerous)</col>" else "")
            player.setComponentText(ID, L.TIP, tip?.let { "Tip: $it" } ?: "")
        }

        fun close(player: Player) {
            player.attr.remove(SHOWN)
            val (parent, child) = slot(player)
            if (player.interfaces.getInterfaceAt(parent, child) == ID) player.closeInterface(ID)
        }
    }

    /** After a region rebuild the client forgets tile arrows: send the current one again. */
    fun resend(player: Player) {
        player.attr.remove(LAST_TARGET)
    }
}
