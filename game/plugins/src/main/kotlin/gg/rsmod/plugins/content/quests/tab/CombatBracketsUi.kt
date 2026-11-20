package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.N108InterfaceImportTool
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.CB
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.Bracket
import gg.rsmod.plugins.content.quests.foundation.QuestBrackets

/**
 * DMM-TAB D4 (owner design 2026-11-20, screens/DMM-TAB/mock/dmm_tab_definitief.png panels 8/9): the Combat Brackets window behind the
 * Deadman tab's Bracket Info button. Live data only: the bracket from [DeadmanBrackets], the quests and storylines from
 * [QuestBrackets] (the photo's list), the choice through [QuestBrackets.choose] - the same path as the Quest Tutor's window, so both
 * always agree. Quests that are not on the server yet ([QuestBrackets.Step.notOnServer], the slayer rewards, miniquests) are not
 * listed (owner: built later).
 */
object CombatBracketsUi {
    const val INTERFACE_ID = N108InterfaceImportTool.BRACKETS

    private const val EVENTS_OP1 = 0x2

    /** The bracket shown on the right (the eye); default the player's own. */
    private val VIEW = AttributeKey<Int>()

    private const val GREEN = "0dc10d"
    private const val RED = "ff0000"
    private const val YELLOW = "ffff00"
    private const val LATER = "968c78"
    private const val DIM = "beaa8c"
    private const val SUB = "c8beaa"

    /** Recipe for Disaster subquests in OSRS order (wiki); a bracket frees those after the previous bracket's count up to its own. */
    val RFD_NAMES =
        listOf(
            "RFD: Another Cook's Quest", "RFD: Mountain Dwarf", "RFD: Goblin Generals", "RFD: Pirate Pete", "RFD: Lumbridge Guide",
            "RFD: Evil Dave", "RFD: Skrach Uglogwee", "RFD: King Awowogei", "RFD: Sir Amik Varze",
        )

    /** The quests a bracket unlocks by itself, as the photo lists them (RFD subquests first). */
    fun bracketQuests(bracket: Bracket): List<String> {
        val i = QuestBrackets.STEPS.indexOfFirst { it.bracket == bracket }
        val step = QuestBrackets.STEPS[i]
        val before = if (i == 0) -1 else QuestBrackets.STEPS[i - 1].rfdSubquests
        val rfd = if (step.rfdSubquests in 0 until QuestBrackets.RFD_COMPLETE) RFD_NAMES.subList(before + 1, step.rfdSubquests + 1) else emptyList()
        return rfd + step.fixed
    }

    /** "Gnome storyline (4 quests)" - owner 20-11: the count instead of the wiki's "#1-#6". */
    fun storylineName(s: QuestBrackets.Storyline): String = "${s.label.substringBefore(" #")} (${s.quests.size} quests)"

    fun reached(player: Player, bracket: Bracket): Boolean = bracket.ordinal <= DeadmanBrackets.of(player).ordinal

    /** Older accounts keep every quest (QuestBrackets.applies false): every bracket reads as done. */
    private fun allUnlocked(player: Player) = !QuestBrackets.applies(player)

    /** (done, total) of a bracket: its quests and - with storylines - the one storyline choice. */
    fun progress(player: Player, bracket: Bracket): Pair<Int, Int> {
        val step = QuestBrackets.step(bracket)
        val quests = bracketQuests(bracket).size
        val story = if (step.options.isEmpty()) 0 else 1
        if (allUnlocked(player)) return quests + story to quests + story
        if (!reached(player, bracket)) return 0 to quests + story
        val chosen = if (story == 1 && QuestBrackets.chosenOption(player, bracket) != null) 1 else 0
        return quests + chosen to quests + story
    }

    /** Opens on [view] (a bracket ordinal; a Quest List header or storyline row) or on the player's own bracket. */
    fun open(player: Player, view: Int = -1) {
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        N108Ui.reset(player, INTERFACE_ID)
        player.attr[VIEW] = if (view in 0 until CB.ROWS) view else DeadmanBrackets.of(player).ordinal
        val hits = mutableListOf(CB.CLOSE)
        for (i in 0 until CB.ROWS) hits += CB.row(i) + 6
        for (k in 0 until 2) hits += CB.btn(k) + 3
        hits.forEach { player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, EVENTS_OP1) }
        draw(player)
    }

    fun isOpen(player: Player) = player.interfaces.isVisible(INTERFACE_ID)

    fun view(player: Player, row: Int) {
        if (!isOpen(player) || row !in 0 until CB.ROWS) return
        player.attr[VIEW] = row
        draw(player)
    }

    fun close(player: Player) {
        if (isOpen(player)) player.closeInterface(INTERFACE_ID)
    }

    fun draw(player: Player) {
        val p = INTERFACE_ID
        val world = player.world
        val own = DeadmanBrackets.of(player)
        N108Ui.text(player, p, CB.YOUR, "Your bracket: <col=ffffff>${label(own)}</col>")
        Bracket.values().forEachIndexed { i, bracket ->
            val base = CB.row(i)
            val (done, total) = progress(player, bracket)
            val open = reached(player, bracket) || allUnlocked(player)
            val full = open && done == total
            val fill = if (!open || total == 0) 0 else CB.FILL_W * done / total
            N108Ui.pos(player, p, base + 1, if (full) 0 else -CB.FILL_W, 0)
            N108Ui.pos(player, p, base + 2, if (full) -CB.FILL_W else fill - CB.FILL_W, 0)
            val col = if (open) "ff981f" else DIM
            N108Ui.text(player, p, base + 3, "<col=$col>${label(bracket)}</col>")
            N108Ui.text(player, p, base + 4, "<col=$col>${if (open) "$done/$total" else "Locked"}</col>")
            N108Ui.hidden(player, p, base + 5, i != (player.attr[VIEW] ?: own.ordinal))
        }
        val (next, fill) = QuestTab.nextBracket(player)
        N108Ui.text(player, p, CB.NEXT_TEXT, next)
        N108Ui.pos(player, p, CB.NEXT_FILL, CB.NEXT_W * fill / N108InterfaceImportTool.DP.FILL_W - CB.NEXT_W, 0)
        drawCard(player, Bracket.values()[player.attr[VIEW] ?: own.ordinal])
    }

    private fun label(b: Bracket) = "${b.levels.first} - ${b.levels.last}"

    private fun drawCard(player: Player, bracket: Bracket) {
        val p = INTERFACE_ID
        val world = player.world
        val step = QuestBrackets.step(bracket)
        val own = DeadmanBrackets.of(player)
        val all = allUnlocked(player)
        val open = reached(player, bracket) || all
        val (done, total) = progress(player, bracket)
        N108Ui.text(player, p, CB.CARD_TITLE, "Combat ${label(bracket)}")
        N108Ui.text(
            player, p, CB.CARD_SUB,
            when {
                bracket == own -> "Your bracket - $done/$total unlocked"
                open -> "Earlier bracket - $done/$total unlocked"
                else -> "Unlocks at combat ${bracket.levels.first}"
            },
        )
        N108Ui.text(player, p, CB.UNLOCKED, if (open) "Unlocked quests" else "Quests")
        val story = step.options.isNotEmpty()
        val rows = if (story) 5 else CB.QUESTS
        val quests = bracketQuests(bracket)
        val lines = if (quests.size <= rows) quests else quests.take(rows - 1) + "+ ${quests.size - rows + 1} more"
        val qcol = if (open) GREEN else LATER
        for (k in 0 until CB.QUESTS) {
            val line = lines.getOrNull(k)
            N108Ui.hidden(player, p, CB.QUEST_FIRST + k, line == null)
            if (line != null) N108Ui.text(player, p, CB.QUEST_FIRST + k, "<col=$qcol>${QuestTab.display(line)}</col>")
        }
        listOf(CB.DIV_DARK, CB.DIV_LIGHT, CB.STORY, CB.STORY_STATUS).forEach { N108Ui.hidden(player, p, it, !story) }
        for (k in 0 until 2) for (c in 0 until CB.BTN_STRIDE) N108Ui.hidden(player, p, CB.btn(k) + c, !story)
        if (!story) return
        val chosen = QuestBrackets.chosenOption(player, bracket)
        N108Ui.text(
            player, p, CB.STORY_STATUS,
            when {
                all -> "<col=$GREEN>All unlocked</col>"
                !open -> "<col=$LATER>Later</col>"
                chosen != null -> "<col=$GREEN>Chosen</col>"
                else -> "<col=$YELLOW>Click 1 to choose</col>"
            },
        )
        step.options.forEachIndexed { k, s ->
            val base = CB.btn(k)
            val n = s.quests.size
            val (nameCol, line) =
                when {
                    all -> GREEN to "<col=$GREEN>Unlocked - $n quests</col>"
                    !open -> DIM to "<col=$LATER>${questLine(world, s.quests)}</col>"
                    chosen == k + 1 -> GREEN to "<col=$GREEN>Chosen - $n quests unlocked</col>"
                    chosen != null -> RED to "<col=$RED>Not chosen - quest these $n yourself</col>"
                    else -> "ff981f" to "<col=$SUB>${questLine(world, s.quests)}</col>"
                }
            N108Ui.text(player, p, base + 1, "<col=$nameCol>${storylineName(s)}</col>")
            N108Ui.text(player, p, base + 2, line)
        }
    }

    /** "Grand Tree, Eyes of Glouphrie +2": the storyline's quests as far as they fit under the button. */
    fun questLine(world: gg.rsmod.game.model.World, quests: List<String>): String {
        val names = quests.map { it.removePrefix("The ") }
        for (n in names.size downTo 1) {
            val s = names.take(n).joinToString(", ") + if (n < names.size) " +${names.size - n}" else ""
            if (N108Ui.p11(world, s) <= 214) return s
        }
        return names.first()
    }

    /** A storyline button: asks first (the choice is for good), then the one choice path. */
    fun choose(player: Player, option: Int) {
        if (!isOpen(player)) return
        val bracket = Bracket.values()[player.attr[VIEW] ?: DeadmanBrackets.of(player).ordinal]
        val step = QuestBrackets.step(bracket)
        val storyline = step.options.getOrNull(option - 1) ?: return
        when {
            !QuestBrackets.applies(player) -> { player.message("Every quest is already unlocked on your account."); return }
            !reached(player, bracket) -> { player.message("You need combat level ${bracket.levels.first} for the ${label(bracket)} bracket."); return }
            QuestBrackets.chosenOption(player, bracket) != null -> { player.message("You already chose your storyline for the ${label(bracket)} bracket."); return }
        }
        val name = storylineName(storyline)
        // A STANDARD queue waits while a modal window is open (test 20-11: the question never showed), so the window closes for the
        // question and opens again on the same bracket afterwards, whatever the answer.
        close(player)
        player.queue {
            val yes = options("Yes, unlock the ${name.substringBefore(" (")}.", "No.", title = "Your choice for ${label(bracket)} is permanent.") == 1
            if (yes) {
                QuestBrackets.choose(player, bracket, option)
                QuestTab.refresh(player)
            }
            open(player, bracket.ordinal)
        }
    }
}
