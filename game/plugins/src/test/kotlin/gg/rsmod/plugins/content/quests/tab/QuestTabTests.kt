package gg.rsmod.plugins.content.quests.tab

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LOYALTY_POINTS
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool
import gg.rsmod.game.tools.importer.ProductionTabClientScriptPatchTool
import gg.rsmod.game.tools.importer.QuestTabIconScriptPatchTool
import gg.rsmod.game.tools.importer.N108InterfaceImportTool as N
import gg.rsmod.game.tools.importer.QuestTabInterfaceImportTool as L
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Currency
import gg.rsmod.plugins.content.quests.foundation.ChoiceGroup
import gg.rsmod.plugins.content.quests.foundation.DesertTreasure
import gg.rsmod.plugins.content.quests.foundation.DesertTreasureII
import gg.rsmod.plugins.content.quests.foundation.LunarDiplomacy
import gg.rsmod.plugins.content.quests.foundation.QuestChoices
import gg.rsmod.plugins.content.quests.foundation.QuestGuideUi
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.tab.DeadmanGroupsView.State
import gg.rsmod.plugins.content.quests.tab.DeadmanTabValues.Row
import gg.rsmod.plugins.content.quests.tab.QuestTabInfo.Subject
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The quest tab, part 1 (owner mission 2026-09-26): the Deadman values come from the store balances, the emblem and the
 * breach; every breach notation; the Deadman Groups view (chosen / unlocked / locked / not chosen); the remembered tab.
 * Varps, varbits (real cache definitions) and containers are real; the rest of the player is a relaxed mock.
 */
class QuestTabTests {
    /** New = an account made before DMM-ARMAGEDDON (cohort "choice", the Quest Guide groups these tests cover). */
    private class Account(isNew: Boolean = true) {
        val attributes = AttributeMap()
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val bank = ItemContainer(DEFINITIONS, BANK_KEY)
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        init {
            if (isNew) attributes[NEW_ACCOUNT_ATTR] = true
            if (isNew) attributes[gg.rsmod.plugins.content.quests.foundation.QuestChoices.COHORT] = gg.rsmod.plugins.content.quests.foundation.QuestChoices.CHOICE
            every { world.definitions } returns DEFINITIONS
            every { player.world } returns world
            every { player.attr } returns attributes
            every { player.username } returns "questtab_test_account"
            every { player.varps } returns VarpSet((0..8000).toSet())
            every { player.skills } returns SkillSet(25)
            every { player.inventory } returns inventory
            every { player.bank } returns bank
            every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
            every { player.gravestone } returns mockk(relaxed = true)
            every { player.deathRecovery } returns mockk(relaxed = true)
        }
    }

    // ------------------------------------------------------------------ Deadman values

    @Test
    fun `point lines read exactly the balances the three stores spend`() {
        val account = Account()
        account.player.attr[Currency.LOYALTY.attr] = 12_003
        account.player.attr[Currency.DONATOR.attr] = 450
        account.player.attr[Currency.DEADMAN.attr] = 1_234_567
        assertEquals("12,003", DeadmanTabValues.value(account.player, Row.LOYALTY))
        assertEquals("450", DeadmanTabValues.value(account.player, Row.DONATOR))
        assertEquals("1,234,567", DeadmanTabValues.value(account.player, Row.DEADMAN))
        // Loyalty is the very attribute every loyalty grant writes (Player.addLoyalty): no copy in between.
        account.player.attr[LOYALTY_POINTS] = 99
        assertEquals("99", DeadmanTabValues.value(account.player, Row.LOYALTY))
        assertEquals("0", DeadmanTabValues.value(Account().player, Row.DONATOR))
    }

    @Test
    fun `emblem line shows None or the owned tier with its points`() {
        val account = Account()
        assertEquals("None", DeadmanTabValues.value(account.player, Row.EMBLEM))
        account.bank[0] = Item(DeadmanEmblem.emblemId(4), 1)
        assertEquals("Tier 4 - 360", DeadmanTabValues.value(account.player, Row.EMBLEM))
        account.bank[0] = null
        account.inventory[5] = Item(DeadmanEmblem.emblemId(6), 1)
        assertEquals("Tier 6 - 1,150", DeadmanTabValues.value(account.player, Row.EMBLEM))
        account.inventory[5] = null
        assertEquals("None", DeadmanTabValues.value(account.player, Row.EMBLEM))
        (1..DeadmanEmblem.MAX_TIER).forEach { tier ->
            assertEquals("Tier $tier - ${DeadmanTabValues.number(DeadmanEmblem.points(tier))}", DeadmanTabValues.emblemText(tier))
        }
    }

    @Test
    fun `breach time notations`() {
        val now = ZonedDateTime.of(2026, 9, 26, 12, 0, 0, 0, ZoneOffset.UTC)
        fun text(seconds: Long) = DeadmanTabValues.breachText(false, now, now.plusSeconds(seconds))
        assertEquals("11 days 22 hours", text(((11 * 24 + 22) * 60 + 0) * 60L))
        assertEquals("1 day 1 hour", text((25 * 60) * 60L))
        assertEquals("1 day 0 hours", text(24 * 3600L))
        assertEquals("5 hours 12 minutes", text((5 * 60 + 12) * 60L))
        assertEquals("1 hour 1 minute", text(61 * 60L))
        assertEquals("1 hour 0 minutes", text(3600L))
        assertEquals("12 minutes", text(12 * 60L))
        assertEquals("1 minute", text(60L))
        // Rounded up to the minute: the last seconds before an opening never read "0 minutes".
        assertEquals("1 minute", text(20L))
        assertEquals("1 minute", text(-30L))
        assertEquals("13 minutes", text(12 * 60L + 1))
        assertEquals("Active", DeadmanTabValues.breachText(true, now, now.plusDays(3)))
    }

    // ------------------------------------------------------------------ Deadman Groups

    private fun finish(player: Player, quest: ShortQuest) {
        if (quest.usesVarbits) player.setVarbit(quest.questId, quest.completedValue) else player.setVarp(quest.questId, quest.completedValue)
    }

    private fun option(view: DeadmanGroupsView.View, quest: ShortQuest) = view.groups.flatMap { it.options }.first { it.quest == quest }

    @Test
    fun `groups not chosen yet show the Quest Tutor note and every option locked`() {
        val account = Account(isNew = true)
        val view = DeadmanGroupsView.view(account.player)
        assertTrue(view.showNote)
        assertEquals("Choose your Deadman groups at the Quest Tutor in the mansion.", DeadmanGroupsView.CHOOSE_NOTE)
        assertEquals(listOf("Magic", "Prayer", "Gear"), view.groups.map { it.label })
        view.groups.flatMap { it.options }.forEach { assertEquals(State.LOCKED, it.state, it.name) }
        assertEquals("Locked - complete Desert Treasure II", option(view, DesertTreasureII).status)
    }

    @Test
    fun `chosen option is marked with its unlock, finished quests read Unlocked, the rest Locked`() {
        val account = Account(isNew = true)
        assertEquals(QuestChoices.Result.CHOSEN, QuestChoices.choose(account.player, ChoiceGroup.MAGIC, DesertTreasure))
        finish(account.player, LunarDiplomacy)
        val view = DeadmanGroupsView.view(account.player)
        val chosen = option(view, DesertTreasure)
        assertEquals(State.CHOSEN, chosen.state)
        assertEquals("Desert Treasure", chosen.name)
        assertEquals("Chosen - Ancient Magicks spellbook", chosen.status)
        assertEquals(State.UNLOCKED, option(view, LunarDiplomacy).state)
        assertEquals("Unlocked", option(view, LunarDiplomacy).status)
        assertEquals(State.LOCKED, option(view, DesertTreasureII).state)
        // Prayer and Gear are still open, so the note stays.
        assertTrue(view.showNote)
    }

    @Test
    fun `DMM-TAB - the Deadman panel buttons link through and Points Info names 78 Tokens`() {
        val tool = gg.rsmod.game.tools.importer.N108InterfaceImportTool
        val dp = gg.rsmod.game.tools.importer.N108InterfaceImportTool.DP
        val comps = tool.deadman(gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Fonts(0, 0, 0, 0))
        assertEquals(listOf("Points Info"), comps.single { it.id == dp.POINTS_HIT }.ops)
        assertEquals(listOf("Bracket Info"), comps.single { it.id == dp.BRACKET_HIT }.ops)
        assertEquals(listOf("Back"), comps.single { it.id == dp.BACK_HIT }.ops)
        // SIGIL-INFO-UI (owner design 21-11): the Sigils Info button under Bracket Info, part of the main state.
        assertEquals(listOf("Sigils Info"), comps.single { it.id == dp.SIGILS_HIT }.ops)
        assertTrue(dp.SIGILS_HIT in dp.MAIN && dp.SIGILS_TEXT in dp.MAIN)
        assertEquals(comps.single { it.id == dp.BRACKET_HIT }.y + 34, comps.single { it.id == dp.SIGILS_HIT }.y)
        // The bar's fill lies inside the Combat Bracket box (box 149 x 55 at panel 17,104 since the block moved up 22 for that button).
        assertTrue(dp.FILL_X >= 17 && dp.FILL_X + dp.FILL_W <= 17 + 149 && dp.FILL_Y >= 104 && dp.FILL_Y + dp.FILL_H <= 104 + 55)
        assertEquals(listOf("Deadman Points", "78 Tokens", "Loyalty Points"), QuestTab.POINTS_INFO.map { it.second })
        val source = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/quests/tab/QuestTab.kt").readText()
        assertTrue("StoreUi" !in source, "the tab opens no store (N280)")
    }

    @Test
    fun `DMM-TAB - Combat Brackets lists the photo's quests per bracket, storylines with their quest count`() {
        val b = gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.Bracket.values()
        assertEquals(listOf(14, 7, 5, 3, 7), b.map { CombatBracketsUi.bracketQuests(it).size })
        assertEquals(
            listOf("RFD: King Awowogei", "Tree Gnome Village", "The Grand Tree", "Monkey Madness", "Troll Stronghold"),
            CombatBracketsUi.bracketQuests(b[2]),
        )
        val step = gg.rsmod.plugins.content.quests.foundation.QuestBrackets.step(b[2])
        assertEquals(listOf("Gnome storyline (4 quests)", "Mahjarrat storyline (6 quests)"), step.options.map { CombatBracketsUi.storylineName(it) })
    }

    @Test
    fun `DMM-TAB - the Quest List shows every bracket, its storylines and Other Quests at the bottom`() {
        val rows = QuestTab.questRows(Account(isNew = false).player)
        assertEquals(
            listOf("Combat 3 - 50", "Combat 51 - 70", "Combat 71 - 90", "Combat 91 - 110", "Combat 111 - 138", "Other Quests"),
            rows.filter { it.kind == QuestTab.ROW_HEAD || it.kind == QuestTab.ROW_OTHER_HEAD }.map { it.text.replace(Regex("<[^>]*>"), "") },
        )
        // An account from before the brackets has every storyline: all green.
        assertTrue(rows.filter { it.kind == QuestTab.ROW_STORY }.all { it.text.startsWith("<col=0dc10d>") })
        assertTrue(rows.size <= gg.rsmod.game.tools.importer.N108InterfaceImportTool.QP.ROWS)
        // A bracket quest is not repeated under Other Quests, and every row under it is a quest.
        val other = rows.dropWhile { it.kind != QuestTab.ROW_OTHER_HEAD }.drop(1)
        assertTrue(other.isNotEmpty() && other.all { it.kind == QuestTab.ROW_QUEST })
        assertFalse(other.any { it.key == "Ernest the Chicken" })
    }

    @Test
    fun `N646 - the Filter hides completed quests in the brackets too, not only under Other Quests`() {
        val account = Account(isNew = false)
        val green = "<col=0dc10d>"
        val shown = QuestTab.questRows(account.player)
        account.attributes[QuestTab.HIDE_DONE] = true
        val filtered = QuestTab.questRows(account.player)
        assertFalse(filtered.any { it.kind == QuestTab.ROW_QUEST && it.text.startsWith(green) }, "a completed quest is still listed")
        // Headers and storylines stay; only completed quest rows go.
        assertEquals(shown.count { it.kind != QuestTab.ROW_QUEST }, filtered.count { it.kind != QuestTab.ROW_QUEST })
        // The list is cut at the panel's row count, so with the filter at least as many open quests fit.
        assertTrue(filtered.count { it.kind == QuestTab.ROW_QUEST } >= shown.count { it.kind == QuestTab.ROW_QUEST && !it.text.startsWith(green) })
    }

    @Test
    fun `accounts from before the choices get no note`() {
        val view = DeadmanGroupsView.view(Account(isNew = false).player)
        assertFalse(view.showNote)
    }

    // ------------------------------------------------------------------ tab choice and layout

    @Test
    fun `chosen tab is stored on the account and a new account starts on Deadman`() {
        val account = Account()
        assertEquals(QuestTab.Tab.DEADMAN, QuestTab.selected(account.player))
        assertEquals("quest_tab_selected", QuestTab.SELECTED.persistenceKey)
        QuestTab.select(account.player, QuestTab.Tab.DROPS)
        assertEquals("DROPS", account.attributes[QuestTab.SELECTED])
        assertEquals(QuestTab.Tab.DROPS, QuestTab.selected(account.player))
        // The tabs of before 2026-10-24 open the Quests tab; anything unknown the default.
        for (old in listOf("GROUPS", "QUEST_LIST", "DIARIES")) {
            account.attributes[QuestTab.SELECTED] = old
            assertEquals(QuestTab.Tab.QUESTS, QuestTab.selected(account.player))
        }
        // DIARIES-OSRS D2: the old Deadman Tasks tab moved to the gameframe Tasks tab - a saved TASKS opens Deadman.
        account.attributes[QuestTab.SELECTED] = "TASKS"
        assertEquals(QuestTab.Tab.DEADMAN, QuestTab.selected(account.player))
        account.attributes[QuestTab.SELECTED] = "SOMETHING_OLD"
        assertEquals(QuestTab.Tab.DEADMAN, QuestTab.selected(account.player))
    }

    @Test
    fun `tabs left to right as the approved design v4 - Deadman, Drops & Collection Log, Quests, Achievement Diaries`() {
        assertEquals(listOf("DEADMAN", "DROPS", "QUESTS", "DIARY"), QuestTab.Tab.values().map { it.name })
        assertEquals(listOf("Deadman", "Drops & Collection Log", "Quests", "Achievement Diaries"), gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.TOOLTIPS)
        assertEquals(listOf("zj_ic_dmm", "qt_ic_log", "qt_ic_quests", "qt_ic_diary"), gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.ICONS)
        // The plugin keeps its own copy of the diary panel numbers (fastpatch-safe); they must equal the import tool's.
        val dy = gg.rsmod.game.tools.importer.N108InterfaceImportTool.DY
        val ui = gg.rsmod.plugins.content.diaries.DiaryUi.DY
        assertEquals(gg.rsmod.game.tools.importer.N108InterfaceImportTool.DIARIES, gg.rsmod.plugins.content.diaries.DiaryUi.INTERFACE)
        assertEquals(listOf(dy.LIST, dy.SCROLLBAR, dy.ROW_FIRST, dy.ROW_STRIDE, dy.ROWS, dy.ROW_H, dy.SEG_W, dy.SEG_H),
            listOf(ui.LIST, ui.SCROLLBAR, ui.ROW_FIRST, ui.ROW_STRIDE, ui.ROWS, ui.ROW_H, ui.SEG_W, ui.SEG_H))
        // The OSRS diary journal (741 journalscroll): plugin numbers == DiaryJournalInterfaceTool's (OSRS component numbers).
        val jt = gg.rsmod.game.tools.importer.DiaryJournalInterfaceTool
        val j = gg.rsmod.plugins.content.diaries.DiaryUi.J
        assertEquals(listOf(jt.INTERFACE, jt.TITLE, jt.TEXTLAYER, jt.LINE_FIRST, jt.LINES, jt.LINE_H, jt.SCROLLBAR, jt.CLOSE),
            listOf(j.INTERFACE, j.TITLE, j.TEXTLAYER, j.LINE_FIRST, j.LINES, j.LINE_H, j.SCROLLBAR, j.CLOSE))
        assertEquals(listOf(2, 3, 4, 200, 204, 205), listOf(jt.TITLE, jt.TEXTLAYER, jt.LINE_FIRST, jt.LINES, jt.SCROLLBAR, jt.CLOSE))
        assertEquals(gg.rsmod.game.tools.importer.N108InterfaceImportTool.TASKS, InterfaceDestination.ACHIEVEMENTS_TAB.interfaceId)
        assertEquals(gg.rsmod.game.tools.importer.N108InterfaceImportTool.CONTAINER, InterfaceDestination.QUEST_TAB.interfaceId)
        assertEquals(with(gg.rsmod.game.tools.importer.N108InterfaceImportTool) { listOf(DEADMAN, DROPS_LOG, QUESTS, DIARIES) }, QuestTab.Tab.values().map { it.panel })
        // Four tabs with 2 px gaps across 188 px of the 190 px pane, the panel box 184 x 233 under them.
        assertEquals(188, gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.TAB_W.sum() + 6)
        gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.TAB_X.zip(gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.TAB_W).zipWithNext().forEach { (a, b) -> assertEquals(a.first + a.second + 2, b.first) }
    }

    @Test
    fun `six Deadman lines fit the fixed-mode panel without scrolling`() {
        assertEquals(Row.values().size, L.ROWS)
        assertTrue((L.ROWS - 1) * L.ROW_PITCH + L.rowHeight(L.ROWS - 1) + 4 <= L.CONTENT_HEIGHT)
        assertEquals(
            listOf("Loyalty Points", "78 Tokens", "Deadman Points", "Deadman Emblem", "Next Breach", "Deadman's Chest"),
            Row.values().map { it.title },
        )
    }

    @Test
    fun `chest line - Active over its spot, else the time until the next one`() {
        assertEquals("Active<br>East of the Wilderness Bandit Camp", DeadmanTabValues.chestText(true, "east of the Wilderness Bandit Camp", 10))
        assertEquals("in 30 minutes<br>Random spot", DeadmanTabValues.chestText(true, null, 3000))
        assertEquals("in 12 minutes<br>Random spot", DeadmanTabValues.chestText(true, null, 1200))
        assertEquals("in 1 minute<br>Random spot", DeadmanTabValues.chestText(true, null, 1))
        assertEquals("Unavailable", DeadmanTabValues.chestText(true, null, -1))
        assertEquals("Unavailable", DeadmanTabValues.chestText(false, "Catherby beach", 10))
    }

    /*
     * N102: measured with the real advance widths (font metrics, cache index 13; p11 lines, p12 Info window) and the client's word wrap at spaces, so a
     * title never runs into its value and no Info text runs out of its box.
     */
    private fun advances(pick: (LootKeyInterfaceImportTool.Fonts) -> Int): IntArray {
        val library = CacheLibrary(CACHE_PATH)
        val id = pick(LootKeyInterfaceImportTool.fonts(library))
        val data = library.data(13, id, 0) ?: error("no metrics for font $id")
        library.close()
        assertEquals(0, data[0].toInt(), "metrics format")
        return IntArray(256) { data[2 + it].toInt() and 0xFF }
    }

    private fun width(widths: IntArray, text: String): Int = Regex("<[^>]*>").replace(text, "").sumOf { widths[it.code and 0xFF] }

    private fun lines(widths: IntArray, text: String, boxWidth: Int): Int =
        text.split("<br>").sumOf { paragraph ->
            var count = 1
            var line = ""
            for (word in paragraph.split(' ')) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && width(widths, next) > boxWidth) {
                    count++
                    line = word
                } else {
                    line = next
                }
            }
            count
        }

    @Test
    fun `every Deadman line fits next to its title, the chest spot within three lines`() {
        val widths = advances { it.p11 }
        assertTrue(width(advances { it.b12 }, L.DEADMAN_TITLE) <= L.DEADMAN_TITLE_WIDTH, "title runs under [Info]")
        val box = L.CONTENT_WIDTH
        val samples = mapOf(
            Row.LOYALTY to listOf(DeadmanTabValues.number(Int.MAX_VALUE)),
            Row.DONATOR to listOf(DeadmanTabValues.number(Int.MAX_VALUE)),
            Row.DEADMAN to listOf(DeadmanTabValues.number(Int.MAX_VALUE)),
            Row.EMBLEM to (1..DeadmanEmblem.MAX_TIER).map { DeadmanTabValues.emblemText(it) },
            Row.BREACH to listOf("13 days 23 hours", "23 hours 59 minutes", "Active"),
        )
        for ((row, values) in samples) {
            assertTrue(width(widths, row.title) <= L.ROW_TITLE_WIDTH, row.title)
            for (v in values) assertTrue(width(widths, row.title) + 6 + width(widths, v) <= box, "${row.title}: $v")
        }
        val spotsFile = Paths.get("..", "..", "data", "cfg", "deadman", "chest_spots.json").toFile()
        val spots = Regex("\"name\": *\"([^\"]+)\"").findAll(spotsFile.readText()).map { it.groupValues[1] }.toList()
        assertTrue(spots.isNotEmpty())
        for (spot in spots) {
            val text = DeadmanTabValues.chestText(true, spot, 0)
            assertTrue(width(widths, Row.CHEST.title) + 6 + width(widths, text.substringBefore("<br>")) <= box)
            assertTrue(lines(widths, text, box) <= 3, spot)
        }
        assertTrue(width(widths, Row.CHEST.title) + 6 + width(widths, "in 30 minutes") <= box)
    }

    @Test
    fun `Info window fits the fixed-mode main screen, the list fits its well, every page fits its boxes`() {
        // N274 (owner design 2026-10-24, screens\team\INFO\info-v1.png): 500 x 322 like every window of the night job.
        val comps = N.info(LootKeyInterfaceImportTool.Fonts(0, 0, 0, 0))
        assertEquals(N.I.COUNT, comps.size)
        assertTrue(comps.first { it.id == 1 }.width <= 512 && comps.first { it.id == 1 }.height <= 334)
        assertEquals(Subject.values().size, N.I.SUBJECTS)
        assertEquals(Subject.values().size, N.I.GROUP_SIZES.sum())
        assertTrue(N.I.subjectY(N.I.SUBJECTS - 1) + N.I.SUBJECT_H <= 29 + 286 - 1, "the last subject stays inside the list well")
        val p11 = advances { it.p11 }
        val b12 = advances { it.b12 }
        val account = Account()
        for (subject in Subject.values()) {
            assertTrue(width(p11, subject.title) <= 118, subject.title)
            assertTrue(width(b12, subject.title) <= 280, subject.title)
            val page = QuestTabInfo.page(account.player, subject)
            assertTrue(width(p11, page.tag) <= 280, "${subject.title} tag line")
            val rows = page.table?.size ?: page.facts.size
            assertTrue(rows in 1..N.I.ROWS, "${subject.title}: $rows rows")
            page.facts.forEach { f ->
                assertTrue(width(p11, f.label) + 10 + width(p11, f.value) <= N.I.ROW_W - 12, "${subject.title}: '${f.label}' + '${f.value}' overlap")
            }
            page.table?.forEach { row -> assertEquals(4, row.size) }
            val textY = N.I.ROW_Y + rows * N.I.ROW_H + 6
            val n = lines(p11, page.text, N.I.TEXT_W)
            assertTrue(n * 15 <= N.I.TEXT_BOTTOM - textY, "${subject.title}: $n text lines below $rows rows")
            val barRoom = if (page.map != null) N.I.MAP_X - N.I.BAR_X - 12 else N.I.ROW_W - 16
            assertTrue(width(p11, page.bar) <= barRoom, "${subject.title} live line '${page.bar}'")
        }
        // The Brackets table: every column fits before the next one.
        val table = QuestTabInfo.page(account.player, Subject.BRACKETS).table!!
        val columns = intArrayOf(0, 112, 196, 270, N.I.ROW_W - 6)
        table.forEach { row -> row.forEachIndexed { c, cell -> assertTrue(width(p11, cell) <= columns[c + 1] - columns[c] - 4, cell) } }
    }

    @Test
    fun `every points page names where the points are spent`() {
        val account = Account()
        fun all(s: Subject) = QuestTabInfo.page(account.player, s).let { p -> p.facts.joinToString(" ") { "${it.label} ${it.value}" } + " " + p.text }
        assertTrue("Loyalty Store" in all(Subject.LOYALTY) && "Xuan" in all(Subject.LOYALTY))
        assertTrue("Donator Store" in all(Subject.DONATOR))
        assertTrue("Deadman Store" in all(Subject.DEADMAN))
        assertTrue("Deadman Store" in all(Subject.EMBLEM) && "1,150" in all(Subject.EMBLEM))
        assertTrue("(UTC)" in all(Subject.BREACH))
        assertTrue("Your balance" in QuestTabInfo.page(account.player, Subject.LOYALTY).bar)
    }

    @Test
    fun `Info pages carry the numbers of the rules they explain`() {
        val account = Account()
        fun all(s: Subject) = QuestTabInfo.page(account.player, s).let { p -> p.facts.joinToString(" ") { "${it.label} ${it.value}" } + " " + p.text }
        assertTrue("30 minutes" in all(Subject.CHEST) && "100,000" in all(Subject.CHEST) && "60 s" in all(Subject.CHEST))
        assertTrue("60 minutes" in all(Subject.PROTECTION))
        assertTrue("5 minutes" in all(Subject.SKULL) && "6 s" in all(Subject.SKULL))
        val table = QuestTabInfo.page(account.player, Subject.BRACKETS).table!!
        assertEquals(listOf("Level", "Combat XP", "Skills XP", "Drops"), table.first())
        assertTrue(table.any { it[0].startsWith("111-138") && it.drop(1) == listOf("15x", "10x", "4x") }, "Armageddon top bracket: $table")
        // DMM-ARMAGEDDON: own combat bracket (D8), 6 s teleport/logout while skulled (D1).
        assertTrue("own combat bracket" in QuestTabInfo.page(account.player, Subject.BRACKETS).text)
        assertTrue("3 - 50" in QuestTabInfo.page(account.player, Subject.BRACKETS).bar, "the bar names the bracket")
        assertTrue("6 s" in all(Subject.TELEPORTS))
        assertTrue("5 / 10 / 15 / 25" in all(Subject.DEADMAN), "the Deadman Tasks points per tier")
        assertEquals("<col=ffffff>Skull</col>", QuestTab.subjectLabel(Subject.SKULL, Subject.SKULL))
        assertEquals("Skull", QuestTab.subjectLabel(Subject.SKULL, Subject.LOYALTY))
    }

    @Test
    fun `a long place reads in full, as its short form or cut - never past its room`() {
        val account = Account()
        val world = account.world
        val p11 = advances { it.p11 }
        every { world.filestore } returns CacheLibrary(CACHE_PATH)
        assertEquals("Catherby beach", QuestTab.placeLabel(world, "Catherby beach", 150))
        assertEquals("Rimmington mine (E)", QuestTab.placeLabel(world, "East of the Rimmington mine", 111))
        assertEquals("Al Kharid bank", QuestTab.placeLabel(world, "Outside the Al Kharid bank", 100))
        assertEquals("Sorcerer's Tower (NE)", QuestTab.placeLabel(world, "North-east of the Sorcerer's Tower", 150))
        val spots = com.google.gson.Gson().fromJson(File("../../data/cfg/deadman/chest_spots.json").readText(), com.google.gson.JsonObject::class.java)["spots"].asJsonArray
        for (s in spots) {
            val name = s.asJsonObject["name"].asString.replaceFirstChar { it.titlecase() }
            assertTrue(width(p11, QuestTab.placeLabel(world, name, 150)) <= 150, name)
            assertFalse(QuestTab.placeLabel(world, name, 150).endsWith("..."), "$name fits as itself or its short form")
            // The Info window's live line beside "Show on map" holds every spot in full.
            assertTrue(width(p11, "Now: $name") <= N.I.MAP_X - N.I.BAR_X - 12, "Info live line: $name")
            // The Drop Viewer's line under "Deadman's Chest" (228 px): "Now at <place> · Opened 9,999".
            val tail = " · Opened 9,999"
            val room = 228 - width(p11, "Now at ") - width(p11, tail)
            assertTrue(width(p11, "Now at " + QuestTab.placeLabel(world, s.asJsonObject["name"].asString, room) + tail) <= 228, "Drop Viewer: $name")
        }
    }

    // ------------------------------------------------------------------ bug-test round 2026-09-26

    @Test
    fun `the gameframe quest icon stays the Deadman skull after clientscript 1766 redraws the tab strip`() {
        val library = CacheLibrary(CACHE_PATH)
        try {
            // 1766 sets the graphic of 548:141 (fixed) and 746:26 (resizable) itself, per game mode (VARC 234). The normal
            // mode ("Quest Journals") must push the skull: the old blue quest icon 1819 came back stretched to 33 x 36.
            val script = ProductionTabClientScriptPatchTool.decode(library.data(12, 1766, 0)!!)
            val targets = setOf((548 shl 16) or 141, (746 shl 16) or 26)
            val sprites = script.indices.filter { i -> i > 0 && script[i].opcode == 0 && script[i].intOperand in targets }.map { script[it - 1].intOperand }
            assertTrue(L.GAMEFRAME_ICON in sprites)
            assertFalse(QuestTabIconScriptPatchTool.OLD_SPRITE in sprites, "sprites set on the quest icon: $sprites")
            QuestTabIconScriptPatchTool.SPRITE_INSTRUCTIONS.forEach { assertEquals(L.GAMEFRAME_ICON, script[it].intOperand) }
            // The components themselves carry the skull too (graphic at byte 19, legacy if3).
            L.ICON_PATCHES.forEach { p ->
                val b = library.data(3, p.interfaceId, p.component)!!
                val graphic = ((b[19].toInt() and 0xFF) shl 24) or ((b[20].toInt() and 0xFF) shl 16) or ((b[21].toInt() and 0xFF) shl 8) or (b[22].toInt() and 0xFF)
                assertEquals(L.GAMEFRAME_ICON, graphic, "${p.interfaceId}:${p.component}")
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `the classic quest list 190 fits the panel box of the Quests tab, nothing cut off at the right`() {
        val library = CacheLibrary(CACHE_PATH)
        try {
            val box = gg.rsmod.game.tools.importer.N108InterfaceImportTool.container(LootKeyInterfaceImportTool.fonts(library)).first { it.id == gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.BOX }
            assertEquals(184, box.width)
            assertEquals(233, box.height)
            fun u2(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
            library.index(3).archive(190)!!.fileIds().forEach { f ->
                val b = library.data(3, 190, f)!!
                if (b[0].toInt() and 0xFF != 255 || u2(b, 16) != 0) return@forEach
                val (x, y, w) = listOf(u2(b, 4), u2(b, 6), u2(b, 8))
                val (wMode, _, xMode, yMode) = listOf(b[12].toInt(), b[13].toInt(), b[14].toInt(), b[15].toInt())
                // Text and layers fit; a full-width shade graphic / rectangle (190:3, 6, 7 are 190 wide) only loses its last 6 px.
                if (wMode == 0 && xMode == 0 && b[1].toInt() != 5 && b[1].toInt() != 3) assertTrue(x + w <= box.width, "190:$f ends at x ${x + w}")
                if (yMode == 0) assertTrue(y < box.height, "190:$f starts at y $y")
            }
            // The new container (night job 2026-10-24) is in the cache as the tool builds it.
            gg.rsmod.game.tools.importer.N108InterfaceImportTool.container(LootKeyInterfaceImportTool.fonts(library)).forEach { c ->
                assertTrue(DeathsOfficeInterfaceImportTool.encode(c).contentEquals(library.data(3, gg.rsmod.game.tools.importer.N108InterfaceImportTool.CONTAINER, c.id)!!), "cache holds container component ${c.id}")
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `the Info window close is guarded`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/quests/tab/quest_tab_container.plugin.kts").readText()
        assertFalse(Regex("""
\s*player\.closeInterface\(QuestTab\.INFO\)""").containsMatchIn(script), "an unguarded close logs 'Interface 1175 is not visible'")
        assertTrue("if (player.interfaces.isVisible(QuestTab.INFO)) player.closeInterface(QuestTab.INFO)" in script)
    }

    companion object {
        private val CACHE_PATH = Paths.get("..", "..", "data", "cache").toFile().toString()
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(CACHE_PATH))
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
