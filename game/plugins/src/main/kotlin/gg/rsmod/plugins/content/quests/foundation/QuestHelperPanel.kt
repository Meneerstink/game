package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.plugins.content.mechanics.pvp.AreaState

/**
 * The Quest Helper's side panel in the 78 Plugins sidebar (owner 2026-10-07 night run: "full RuneLite-style panel", the
 * RuneLite Quest Helper's QuestHelperPanel / QuestOverviewPanel / QuestStepPanel). The client only draws: every line comes
 * from the quest definitions here (FoundationQuests - one source, no quest text in the client), pushed on the HUD side
 * channel (ClientHud, at most [ClientHud.MAX_PAYLOAD] characters a message):
 *
 *  - `qhq|<slot>|<state>|<tracked>|<name>` one row of the quest list per playable quest (state 0 not started, 1 in progress,
 *    2 completed), then `qhqe` (the list is complete)
 *  - `qhb|<slot>|<state>|<stage>|<tracked>|<steps>|<travel>|<name>` an overview begins (stage 0 = not started; travel 1 when
 *    the panel may offer the quest travel), then `qhs|<kind>|<index>|<text>` lines - kind `start`, `req`, `enemy`, `reward`,
 *    `step` (index = step number; a long step is sent in several parts that the client joins), `place`, `danger`, `tip` -
 *    and `qhe` (draw it)
 *  - `qhn` no overview (the list is shown)
 *
 * The client asks with the command `questpanel list | view <slot> | track <slot> | stop | travel <slot>`.
 */
object QuestHelperPanel {
    /** The quest the panel shows when it is not the tracked one (never saved). */
    val VIEW = AttributeKey<Int>()
    private val SIGNATURE = AttributeKey<String>()

    private const val PART = 150

    fun state(player: Player, quest: ShortQuest): Int =
        when {
            quest.isFinished(player) -> 2
            quest.stage(player) > 0 -> 1
            else -> 0
        }

    /** The quest the overview shows: the one the player opened, else the tracked one, else none (the list). */
    fun shown(player: Player): ShortQuest? {
        val view = player.attr[VIEW]?.let { slot -> FoundationQuests.SHORT.firstOrNull { it.slot == slot } }
        return view ?: QuestHelper.tracked(player)
    }

    /**
     * The panel's quest travel follows the Quest Tutor's rule: the Tutor and the hall's quest givers (some on the lounge's
     * balconies, e.g. the Archaeologist at 3156,3492,1) send players on from home - the Grand Exchange, any floor (its
     * Guarded enclosure, GuardedZones' GE edges z 3462..3518, x 3138..3190) - never as a free teleport out of anywhere else.
     */
    fun inHall(player: Player): Boolean {
        val t = player.tile
        return t.x in GE_MIN_X..GE_MAX_X && t.z in GE_MIN_Z..GE_MAX_Z
    }

    private const val GE_MIN_X = 3138
    private const val GE_MAX_X = 3190
    private const val GE_MIN_Z = 3462
    private const val GE_MAX_Z = 3518

    /** Where the quest's next travel goes (place, tile, npc to land beside), or null when it starts in the hall itself. */
    fun travelTarget(player: Player, quest: ShortQuest): Triple<String, Tile, Int?>? {
        if (quest.isFinished(player)) return null
        val stage = quest.stage(player)
        if (stage <= 0) {
            val tile = quest.startLocation ?: return null
            if (quest.startPlace == "the Grand Exchange hall") return null
            return Triple(quest.startPlace, tile, quest.startNpc)
        }
        val step = quest.steps.getOrNull(stage - 1) ?: return null
        val place = step.place ?: return null
        val tile = step.location ?: return null
        if (place == "the Grand Exchange hall") return null
        return Triple(place, tile, step.npc.takeIf { step.fight == null })
    }

    private fun clean(text: String): String = text.replace(Regex("<[^>]+>"), "").replace('|', '/').trim()

    /** Sends the list and the overview again when anything they show changed (or always with [force]). */
    fun push(player: Player, force: Boolean = false) {
        if (!QuestHelper.enabled(player)) return
        val shown = shown(player)
        val signature =
            FoundationQuests.SHORT.joinToString(",") { "${it.slot}:${it.stage(player)}" } +
                "|t${QuestHelper.tracked(player)?.slot}|v${shown?.slot}|h${shown?.let { travelTarget(player, it) != null && inHall(player) }}"
        if (!force && player.attr[SIGNATURE] == signature) return
        player.attr[SIGNATURE] = signature
        val tracked = QuestHelper.tracked(player)
        FoundationQuests.SHORT.forEach { q ->
            ClientHud.send(player, "qhq|${q.slot}|${state(player, q)}|${if (q == tracked) 1 else 0}|${clean(q.name)}")
        }
        ClientHud.send(player, "qhqe")
        if (shown == null) {
            ClientHud.send(player, "qhn")
        } else {
            overview(player, shown, shown == tracked)
        }
    }

    private fun line(
        player: Player,
        kind: String,
        index: Int,
        text: String,
    ) {
        var rest = clean(text)
        if (rest.isEmpty()) return
        while (rest.isNotEmpty()) {
            ClientHud.send(player, "qhs|$kind|$index|${rest.take(PART)}")
            rest = rest.drop(PART)
        }
    }

    private fun overview(
        player: Player,
        quest: ShortQuest,
        tracked: Boolean,
    ) {
        val stage = if (quest.isFinished(player)) quest.steps.size + 1 else quest.stage(player)
        val travel = travelTarget(player, quest) != null && inHall(player)
        ClientHud.send(player, "qhb|${quest.slot}|${state(player, quest)}|$stage|${if (tracked) 1 else 0}|${quest.steps.size}|${if (travel) 1 else 0}|${clean(quest.name)}")
        line(player, "start", 0, "Speak to ${quest.startNpcName} at ${quest.startPlace}.")
        // The short quests ask nothing of a player: no levels, items or earlier quests (owner 2026-09-26, Quest Tutor).
        line(player, "req", 0, "None - every player can do this quest.")
        // Owner 2026-10-10: the recommended combat level (strongest foe, at most 126) - advice, not a requirement.
        quest.recommendedLine(player)?.let { line(player, "req", 0, it) }
        quest.steps.forEachIndexed { i, step ->
            val fight = step.fight ?: return@forEachIndexed
            val more = fight.chain.size
            val names =
                if (more == 0) {
                    "${fight.name} (level ${fight.level})"
                } else {
                    val defs = player.world.definitions
                    val chain = fight.chain.joinToString(", ") { (id, _) -> defs.getNullable(NpcDef::class.java, id)?.name ?: "?" }
                    "${fight.name} (level ${fight.level}), then $chain"
                }
            line(player, "enemy", i + 1, names)
        }
        quest.reward.lines.forEach { line(player, "reward", 0, it) }
        quest.steps.forEachIndexed { i, step ->
            val n = i + 1
            line(player, "step", n, step.journal.joinToString(" "))
            step.place?.let { line(player, "place", n, it) }
            val where = step.fight?.tile ?: step.location
            if (where != null && AreaState.isDangerous(where)) line(player, "danger", n, "Dangerous area: other players can attack you here.")
            step.tip?.let { line(player, "tip", n, it) }
        }
        ClientHud.send(player, "qhe")
    }
}
