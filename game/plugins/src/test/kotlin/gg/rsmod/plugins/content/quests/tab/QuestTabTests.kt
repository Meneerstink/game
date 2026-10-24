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
    private class Account(isNew: Boolean = true) {
        val attributes = AttributeMap()
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val bank = ItemContainer(DEFINITIONS, BANK_KEY)
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        init {
            if (isNew) attributes[NEW_ACCOUNT_ATTR] = true
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
        assertEquals("Tier 4 - 360 pts", DeadmanTabValues.value(account.player, Row.EMBLEM))
        account.bank[0] = null
        account.inventory[5] = Item(DeadmanEmblem.emblemId(6), 1)
        assertEquals("Tier 6 - 1,150 pts", DeadmanTabValues.value(account.player, Row.EMBLEM))
        account.inventory[5] = null
        assertEquals("None", DeadmanTabValues.value(account.player, Row.EMBLEM))
        (1..DeadmanEmblem.MAX_TIER).forEach { tier ->
            assertEquals("Tier $tier - ${DeadmanTabValues.number(DeadmanEmblem.points(tier))} pts", DeadmanTabValues.emblemText(tier))
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
        // The Quests tab (night job 2026-10-24) shows the same state: chosen = tick, the other two locked.
        val rows = QuestTab.questRows(account.player)
        assertTrue(rows.any { it.text.startsWith("Desert Treasure") && it.icon == "q_ck" })
        assertTrue(rows.any { it.text.contains("Desert Treasure II") && it.icon == "q_lock" })
    }

    @Test
    fun `a point row opens Info on those points and never a store, the emblem row Info on the emblem`() {
        assertEquals(QuestTab.POINT_CURRENCIES.size, QuestTab.POINT_SUBJECTS.size)
        assertEquals(listOf("Loyalty Points", "Donator Points", "Deadman Points"), QuestTab.POINT_SUBJECTS.map { it.title })
        val deadman = gg.rsmod.game.tools.importer.N108InterfaceImportTool.deadman(gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Fonts(0, 0, 0, 0))
        val dp = gg.rsmod.game.tools.importer.N108InterfaceImportTool.DP
        for (r in 0 until dp.POINTS) {
            val hit = deadman.single { it.id == dp.point(r) + 4 }
            assertEquals(listOf("Info"), hit.ops)
            assertEquals(dp.POINT_Y + r * 18, hit.y)
        }
        val source = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/quests/tab/QuestTab.kt").readText()
        assertTrue("StoreUi" !in source, "the tab opens no store (N280)")
    }

    @Test
    fun `accounts from before the choices get no note`() {
        val view = DeadmanGroupsView.view(Account(isNew = false).player)
        assertFalse(view.showNote)
    }

    @Test
    fun `N289 - a group whose quests are all yours reads a green Unlocked, a chosen group Chosen, an open group Pick 1`() {
        // An account from before the choices has every quest: every group Unlocked (it said "All yours").
        val legacy = QuestTab.questRows(Account(isNew = false).player).filter { it.icon == null && it.right != null }
        assertEquals(listOf("<col=0dc10d>Unlocked</col>"), legacy.map { it.right }.distinct())
        val account = Account(isNew = true)
        assertEquals(QuestChoices.Result.CHOSEN, QuestChoices.choose(account.player, ChoiceGroup.MAGIC, DesertTreasure))
        val rows = QuestTab.questRows(account.player).filter { it.icon == null && it.right != null }.associate { it.key to it.right }
        assertEquals("<col=ffcf4a>Chosen</col>", rows["MAGIC"])
        assertEquals("<col=9a9083>Pick 1</col>", rows["PRAYER"])
        assertTrue(QuestTab.questRows(account.player).none { "All yours" in (it.right ?: "") })
    }

    @Test
    fun `groups use the existing choice data and the Quests tab lists every group, quest and requirement`() {
        assertEquals(ChoiceGroup.values().toList(), QuestGuideUi.ROWS)
        val account = Account()
        val rows = QuestTab.questRows(account.player)
        assertEquals(listOf("MAGIC", "PRAYER", "GEAR"), rows.filter { it.icon == null && it.right != null }.map { it.key })
        QuestGuideUi.ROWS.flatMap { it.quests }.forEach { q -> assertTrue(rows.any { it.key == (QuestGuideUi.NAMES[q] ?: q.name) }, q.name) }
        QuestGuideUi.ROWS.flatMap { it.quests }.forEach { q -> assertTrue(QuestTab.REQUIREMENTS.containsKey(QuestGuideUi.NAMES[q] ?: q.name), "requirements of ${q.name}") }
        // N103: only the Quest Tutor quests; folding the longest one open still fits the rows.
        assertEquals(3 + 8, rows.size)
        assertTrue(rows.size + 7 <= gg.rsmod.game.tools.importer.N108InterfaceImportTool.QP.ROWS)
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
        account.attributes[QuestTab.SELECTED] = "SOMETHING_OLD"
        assertEquals(QuestTab.Tab.DEADMAN, QuestTab.selected(account.player))
    }

    @Test
    fun `tabs left to right as the approved design - Quests, Deadman, Deadman Tasks, Drops & Collection Log`() {
        assertEquals(listOf("QUESTS", "DEADMAN", "TASKS", "DROPS"), QuestTab.Tab.values().map { it.name })
        assertEquals(listOf("Quests", "Deadman", "Deadman Tasks", "Drops & Collection Log"), gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.TOOLTIPS)
        assertEquals(gg.rsmod.game.tools.importer.N108InterfaceImportTool.CONTAINER, InterfaceDestination.QUEST_TAB.interfaceId)
        assertEquals(with(gg.rsmod.game.tools.importer.N108InterfaceImportTool) { listOf(QUESTS, DEADMAN, TASKS, DROPS_LOG) }, QuestTab.Tab.values().map { it.panel })
        // Four tabs with 2 px gaps across 188 px of the 190 px pane, the panel box 184 x 233 under them.
        assertEquals(188, gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.TAB_W.sum() + 6)
        gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.TAB_X.zip(gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q.TAB_W).zipWithNext().forEach { (a, b) -> assertEquals(a.first + a.second + 2, b.first) }
    }

    @Test
    fun `six Deadman lines fit the fixed-mode panel without scrolling`() {
        assertEquals(Row.values().size, L.ROWS)
        assertTrue((L.ROWS - 1) * L.ROW_PITCH + L.rowHeight(L.ROWS - 1) + 4 <= L.CONTENT_HEIGHT)
        assertEquals(
            listOf("Loyalty Points", "Donator Points", "Deadman Points", "Archaic Emblem", "Next Breach", "Deadman's Chest"),
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
        assertTrue("5 minutes" in all(Subject.SKULL) && "7 s" in all(Subject.SKULL))
        val table = QuestTabInfo.page(account.player, Subject.BRACKETS).table!!
        assertEquals(listOf("Level", "Combat XP", "Skills XP", "Drops"), table.first())
        assertTrue(table.any { it[0].startsWith("96+") && it.drop(1) == listOf("20x", "10x", "5x") })
        assertTrue("14 levels" in QuestTabInfo.page(account.player, Subject.BRACKETS).text)
        assertTrue("7 s" in all(Subject.TELEPORTS))
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
