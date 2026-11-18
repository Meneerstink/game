package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.message.impl.HintArrowMessage
import gg.rsmod.game.message.impl.HintArrowTileMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.quests.foundation.QuestHelper
import java.io.File

/**
 * CLUE-QH (owner 18-11 00:3x 'clue moet ook werken met questhelper', chosen for the night run 02:0x): the RuneLite Clue Scroll
 * plugin inside our Quest Helper. Reading a clue scroll (with the Quest Helper plugin on) shows its solution until the scroll
 * is no longer carried or its step changes to one without help:
 *  - the helper panel (interface 1166, QuestHelper.Panel.showClue): RuneLite's overlay title ("Anagram Clue", ...), the tier,
 *    the solution (npc to speak to / the plugin's solution or location text / the emote(s)), "Go to:" the plugin's area, and
 *    the challenge answer or the items to equip on the tip line;
 *  - the hint arrow over the step's npc when it is near, else over the dig/search/emote tile (minimap edge arrow);
 *  - `qht` = the world-map icon, minimap arrow and npc outline of the client's Quest Helper hints.
 * Texts come from RuneLite's cluescrolls data (data/cfg/clue/helper.json, tools/clue/runelite_helper_data.py) and our own
 * steps.json (npc, points, emotes, equipment); a step RuneLite has no row for shows our hint. While a clue is helped the quest
 * helper steps aside (QuestHelper.tick) and gets its panel and arrow back afterwards.
 */
object ClueHelper {
    val TIMER = TimerKey()
    const val INTERVAL = 3

    /** The tier whose clue is being helped (not saved: reading the scroll again brings the help back). */
    private val TIER = AttributeKey<String>()

    data class Row(
        val title: String,
        val npc: String?,
        val area: String?,
        val solution: String?,
        val answer: String?,
        val emotes: List<String>,
        val items: List<String>,
    )

    val rows: Map<String, Row> by lazy { parse(File(RspsPaths.data("cfg/clue/helper.json")).readText()) }

    fun parse(json: String): Map<String, Row> =
        JsonParser().parse(json).asJsonObject["rows"].asJsonArray.associate { value ->
            val o = value.asJsonObject
            fun s(k: String) = o[k]?.takeIf { !it.isJsonNull }?.asString
            fun l(k: String) = o[k]?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
            s("key")!! to Row(s("title")!!, s("npc"), s("area"), s("solution"), s("answer"), l("emotes"), l("items"))
        }

    fun active(player: Player): Boolean = player.attr[TIER] != null

    /** The scroll was read: help it from now on (Quest Helper plugin on). */
    fun onRead(player: Player, tier: ClueScrollTier) {
        if (!QuestHelper.enabled(player)) return
        player.attr[TIER] = tier.name
        tick(player)
    }

    /** What the panel shows for a step: instruction, "Go to" place, tip line, panel title. */
    data class Help(val title: String, val instruction: String, val where: String?, val tip: String?)

    fun help(step: TreasureTrailStepCatalogue.Step, row: Row?): Help {
        val npc = row?.npc ?: step.npcName
        val title = row?.title ?: "Clue Scroll"
        val instruction =
            when {
                row != null && row.emotes.isNotEmpty() -> "Emote: " + row.emotes.joinToString(", then ")
                step.action.name == "TALK" && npc != null && row?.solution != null -> "Speak to $npc. ${row.solution}"
                npc != null && step.action.name in setOf("TALK", "SHERLOCK", "FALO", "HOT_COLD") -> "Speak to $npc."
                row?.solution != null -> row.solution
                else -> step.hint.replace(Regex("<br>|\n"), " ").replace(Regex("<[^>]+>"), "").trim()
            }
        val tip =
            when {
                row?.answer != null -> "Answer: ${row.answer}"
                row != null && row.items.isNotEmpty() -> "Equip: " + row.items.joinToString(", ")
                else -> null
            }
        return Help(title, instruction, row?.area, tip)
    }

    fun tick(player: Player) {
        val tier = player.attr[TIER]?.let { name -> ClueScrollTier.values().firstOrNull { it.name == name } }
        val step = tier?.let { (ClueScrollManager.activeStep(player, it) as? TreasureTrailSourceStep)?.source }
        if (tier == null || step == null || !QuestHelper.enabled(player)) {
            if (tier != null) end(player)
            return
        }
        val help = help(step, rows[step.key])
        val npc = nearestNpc(player, step)
        val point = step.points.minByOrNull { if (it.height == player.tile.height) it.getDistance(player.tile) else Int.MAX_VALUE }
        val goal = npc?.tile ?: point
        QuestHelper.sendTarget(player, goal, npc?.id ?: -1)
        QuestHelper.Panel.showClue(player, help.title, tier.displayName, help.instruction, help.where, help.tip)
        when {
            npc != null && npc.tile.height == player.tile.height ->
                player.write(HintArrowMessage(slot = QuestHelper.SLOT, type = HintArrowMessage.TYPE_NPC, target = npc.index))
            // A tile arrow is relative to the map base: sent again every helper tick, like the quest helper does.
            goal != null && goal.height == player.tile.height ->
                player.write(HintArrowTileMessage(slot = QuestHelper.SLOT, x = goal.x, z = goal.z, level = goal.height, height = 0, drawDistance = 0))
            else -> QuestHelper.clearArrow(player)
        }
        player.timers[TIMER] = INTERVAL
    }

    /** No helped clue any more: clear it and give the quest helper its panel, arrow and map icon back. */
    fun end(player: Player) {
        player.attr.remove(TIER)
        player.timers.remove(TIMER)
        QuestHelper.clearArrow(player)
        QuestHelper.clearClientHints(player)
        QuestHelper.Panel.close(player)
        QuestHelper.refresh(player)
    }

    /** The step's npc near the player: one of the step's own npc ids, else an npc with the step's npc name. */
    private fun nearestNpc(player: Player, step: TreasureTrailStepCatalogue.Step): Npc? {
        val name = step.npcName ?: return null
        var best: Npc? = null
        var bestDistance = Int.MAX_VALUE
        player.world.npcs.forEach { n ->
            if (n.owner != null && n.owner != player) return@forEach
            val distance = n.tile.getDistance(player.tile)
            if (distance > QuestHelper.NPC_RANGE || distance >= bestDistance) return@forEach
            if (n.id !in step.existingNpcs && !player.world.definitions.get(NpcDef::class.java, n.id).name.equals(name, ignoreCase = true)) return@forEach
            best = n
            bestDistance = distance
        }
        return best
    }
}
