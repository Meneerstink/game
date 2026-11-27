package gg.rsmod.plugins.content.diaries

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
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
        "Ardougne" to 43, "Desert" to 39, "Falador" to 42, "Fremennik" to 34, "Kandarin" to 43, "Karamja" to 44,
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

    /** The diary's journal: OSRS order easy -> elite, done tasks struck through. */
    fun openJournal(player: Player, row: Int) {
        val diary = diaryOfRow(row)
        if (diary == null) {
            player.message("The ${ROWS.getOrNull(row)?.first ?: ""} Achievement Diary is not available yet.")
            return
        }
        val done = Diaries.done(player)
        val lines = ArrayList<String>()
        Tier.values().forEach { tier ->
            val tasks = Diaries.tasks(diary, tier)
            val claimed = Diaries.hasClaimed(player, diary, tier)
            val tierDone = tasks.all { it.id in done }
            lines += "<col=800000>${tier.title}</col>" + when {
                claimed -> " <col=006600>(reward claimed)</col>"
                tierDone -> " <col=006600>(complete - speak to ${diary.taskmaster})</col>"
                else -> ""
            }
            tasks.forEach { t ->
                val text = t.text + if (t.req.isNotEmpty()) " <col=7f0000>(${t.req})</col>" else ""
                lines += if (t.id in done) "<str>$text</str>" else text
            }
            lines += ""
        }
        player.setComponentHidden(interfaceId = 275, component = 14, hidden = true)
        player.setComponentText(interfaceId = 275, component = 2, "<col=8A0808>Achievement Diary - ${diary.title}")
        for (i in 16..315) player.setComponentText(interfaceId = 275, component = i, "")
        lines.take(299).forEachIndexed { i, s ->
            player.runClientScript(4265, 275 shl 16 or 16 + i, 0x000080)
            player.setComponentText(interfaceId = 275, component = 16 + i, text = s)
        }
        player.runClientScript(1208, 275 shl 16 or 7, 275 shl 16 or 6, lines.size * 25, 0)
        player.openInterface(dest = InterfaceDestination.MAIN_SCREEN_FULL, interfaceId = 275)
    }
}
