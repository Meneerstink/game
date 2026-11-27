package gg.rsmod.plugins.content.diaries

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getSpellbook
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.diaries.Diaries.Diary
import gg.rsmod.plugins.content.diaries.Diaries.Tier
import gg.rsmod.plugins.content.quests.tab.N108Ui

/**
 * DIARIES-OSRS D2 (ontwerp v4, owner 27-11 'perfect'): the green-gem panel of the quest tab - the OSRS 'Achievement Diaries' list,
 * one box per diary with its name and done/total (red = not started, yellow = started, green = all done) and a bar in four pieces
 * (easy / medium / hard / elite: green = tier done, orange = started). A click opens the diary's journal (the 667 quest journal 275,
 * as OSRS opens its journal scroll): every task by tier, a done task struck through, its requirements behind it.
 */
object DiaryUi {
    /** The panel interface and its layout - the same numbers as N108InterfaceImportTool.DY (DiaryUiLayoutTests keeps them equal). */
    const val INTERFACE = 1242

    object DY {
        const val LIST = 3
        const val SCROLLBAR = 4
        const val ROW_FIRST = 5
        const val ROW_STRIDE = 17
        const val ROWS = 12
        const val ROW_H = 30
        const val SEG_W = 36
        const val SEG_H = 5

        fun row(i: Int) = ROW_FIRST + i * ROW_STRIDE
    }

    /** The twelve OSRS diaries in the OSRS list order, with their OSRS task totals for the ones we do not have yet. */
    val ROWS = listOf(
        "Ardougne" to 42 /* OSRS Wiki "Ardougne Diary" 10/12/12/8 (27-11) */, "Desert" to 39, "Falador" to 42, "Fremennik" to 34, "Kandarin" to 43, "Karamja" to 44,
        "Kourend & Kebos" to 43, "Lumbridge & Draynor" to 41, "Morytania" to 38, "Varrock" to 42, "Western Provinces" to 44, "Wilderness" to 40,
    )

    fun diaryOfRow(i: Int): Diary? = Diary.values().firstOrNull { it.title == ROWS.getOrNull(i)?.first }

    const val RED = "ff0000"
    const val YELLOW = "ffff00"
    const val GREEN = "00ff00"

    fun refresh(player: Player) {
        runCatching { gg.rsmod.plugins.content.quests.tab.QuestTab.refresh(player) }
        runCatching { gg.rsmod.plugins.content.quests.tab.QuestTab.refreshTasksTab(player) }
    }

    /** Draws the panel (called by QuestTab.refresh while the green gem shows); only what changed goes out (N108Ui caches). */
    fun draw(player: Player) {
        val p = INTERFACE
        ROWS.forEachIndexed { i, (name, osrsTotal) ->
            val base = DY.row(i)
            val diary = diaryOfRow(i)
            val done = diary?.let { Diaries.doneCount(player, it) } ?: 0
            val total = diary?.let { Diaries.totalCount(it) } ?: osrsTotal
            val col = when {
                done == 0 -> RED
                done >= total -> GREEN
                else -> YELLOW
            }
            N108Ui.text(player, p, base + 2, "<col=$col>$name</col>")
            N108Ui.text(player, p, base + 3, "<col=$col>$done/$total</col>")
            Tier.values().forEachIndexed { k, tier ->
                val seg = base + 4 + k * 3
                val d = diary?.let { Diaries.doneCount(player, it, tier) } ?: 0
                val t = diary?.let { Diaries.totalCount(it, tier) } ?: 1
                N108Ui.hidden(player, p, seg + 1, !(d >= t && d > 0))
                val partial = d in 1 until t
                N108Ui.hidden(player, p, seg + 2, !partial)
                if (partial) N108Ui.size(player, p, seg + 2, (DY.SEG_W * d / t).coerceAtLeast(4), DY.SEG_H)
            }
        }
        N108Ui.scroll(player, p, DY.LIST, DY.SCROLLBAR, DY.ROWS * DY.ROW_H)
    }

    /**
     * The diary's journal = OSRS interface 741 `journalscroll` (owner 27-11 12:0x: the 667 quest scroll 275 was not the OSRS screen),
     * ported as [J.INTERFACE] by DiaryJournalInterfaceTool with the OSRS component numbers.
     */
    object J {
        const val INTERFACE = 1243
        const val TITLE = 2
        const val TEXTLAYER = 3
        const val LINE_FIRST = 4
        const val LINES = 200
        const val LINE_H = 20
        const val SCROLLBAR = 204
        const val CLOSE = 205
    }

    /** Characters per journal line: the 404 px text layer in p12 (RuneLite wraps by font width; ~6.4 px per character). */
    const val JOURNAL_CHARS = 60

    /** OSRS journal colours: a task still to do is navy, a done task is struck through (RuneLite DiaryRequirementsPlugin keys on '<str>'). */
    const val TODO = "000080"
    const val HEADER = "800000"

    /** RuneLite's 'Diary Requirements' layout (what OSRS players see): a met requirement struck through, a missing one dark red. */
    fun requirement(player: Player, req: String): String = req.split(", ").joinToString(", ") { part ->
        val met = requirementMet(player, part.trim())
        if (met) "<str>$part</str>" else "<col=$HEADER>$part</col><col=$TODO>"
    }

    /**
     * One requirement part ('Fishing 70', 'Combat 100', 'Lunar spellbook'). OSRS lets almost every diary skill requirement be boosted, so
     * the higher of the current and the real level counts. [unknown] = the answer for a part this cannot read (shown as missing, never
     * blocking a task).
     */
    fun requirementMet(player: Player, part: String, unknown: Boolean = false): Boolean {
        if (part.equals("Lunar spellbook", true)) return runCatching { player.getSpellbook() == gg.rsmod.plugins.api.Spellbook.LUNAR }.getOrDefault(unknown)
        val name = part.substringBeforeLast(' ')
        val level = part.substringAfterLast(' ').toIntOrNull() ?: return unknown
        if (name.equals("Combat", true)) return player.combatLevel >= level
        val skill = SKILL_NAMES.indexOfFirst { it.equals(name, true) }.takeIf { it >= 0 } ?: return unknown
        return maxOf(player.skills.getMaxLevel(skill), player.skills.getCurrentLevel(skill)) >= level
    }

    /** OSRS: a task only counts with its requirements (the content itself enforces them there; ours is 667 content). */
    fun meetsRequirements(player: Player, req: String): Boolean =
        req.isBlank() || req.split(", ").all { requirementMet(player, it.trim(), unknown = true) }

    /** Skill ids 0..24 (gg.rsmod.plugins.api.Skills order) by the names DiaryTasks uses in its requirements. */
    val SKILL_NAMES = listOf("Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting", "Fletching",
        "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving", "Slayer", "Farming", "Runecrafting",
        "Hunter", "Construction", "Summoning", "Dungeoneering")

    fun wrap(text: String, max: Int): List<String> {
        val out = ArrayList<String>()
        var cur = ""
        text.split(' ').forEach { w ->
            cur = if (cur.isEmpty()) w else if (cur.length + 1 + w.length <= max) "$cur $w" else { out += cur; w }
        }
        if (cur.isNotEmpty()) out += cur
        return out.ifEmpty { listOf("") }
    }

    fun openJournal(player: Player, row: Int) {
        val diary = diaryOfRow(row)
        if (diary == null) {
            player.message("The ${ROWS.getOrNull(row)?.first ?: ""} Achievement Diary is not available yet.")
            return
        }
        val lines = journalLines(player, diary)
        val p = J.INTERFACE
        player.openInterface(dest = InterfaceDestination.MAIN_SCREEN, interfaceId = p)
        N108Ui.reset(player, p)
        player.setInterfaceEvents(p, J.CLOSE, -1..-1, 0x2)
        player.setComponentText(interfaceId = p, component = J.TITLE, "Achievement Diary - ${diary.title}")
        for (i in 0 until J.LINES) N108Ui.text(player, p, J.LINE_FIRST + i, lines.getOrElse(i) { "" })
        N108Ui.scrollTop(player, p, J.TEXTLAYER, J.SCROLLBAR, 210)
        N108Ui.scroll(player, p, J.TEXTLAYER, J.SCROLLBAR, maxOf(210, lines.size * J.LINE_H))
    }

    /** The OSRS order: '<Area> Tasks', then per tier its name and the tasks; a done task struck through. */
    fun journalLines(player: Player, diary: Diary): List<String> {
        val done = Diaries.done(player)
        val lines = ArrayList<String>()
        lines += "<col=$HEADER>${diary.journalHeader} Tasks</col>"
        lines += ""
        Tier.values().forEach { tier ->
            val tasks = Diaries.tasks(diary, tier)
            lines += "<col=$HEADER>${tier.title}</col>"
            tasks.forEach { t ->
                val isDone = t.id in done
                val req = if (t.req.isNotEmpty() && !isDone) " (" + requirement(player, t.req) + ")" else ""
                // A long task wraps onto the next line (the text layer is 404 px); the requirement goes behind the last part, like RuneLite.
                val parts = wrap(t.text, JOURNAL_CHARS)
                val reqPlain = if (t.req.isNotEmpty() && !isDone) " (${t.req})" else ""
                parts.forEachIndexed { i, s ->
                    val last = i == parts.lastIndex
                    val withReq = last && reqPlain.isNotEmpty() && s.length + reqPlain.length <= JOURNAL_CHARS
                    lines += if (isDone) "<str>$s</str>" else "<col=$TODO>$s${if (withReq) req else ""}</col>"
                    if (last && reqPlain.isNotEmpty() && !withReq) lines += "<col=$TODO>${req.trim()}</col>"
                }
            }
            lines += ""
        }
        return lines.take(J.LINES)
    }

    fun close(player: Player) {
        if (player.interfaces.isVisible(J.INTERFACE)) player.closeInterface(J.INTERFACE)
    }
}
