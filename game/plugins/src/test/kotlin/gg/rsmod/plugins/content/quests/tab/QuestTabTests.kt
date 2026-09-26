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
import gg.rsmod.plugins.content.quests.tab.DeadmanTabValues.Block
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
    fun `point blocks read exactly the balances the three stores spend`() {
        val account = Account()
        account.player.attr[Currency.LOYALTY.attr] = 12_003
        account.player.attr[Currency.DONATOR.attr] = 450
        account.player.attr[Currency.DEADMAN.attr] = 1_234_567
        assertEquals("12,003", DeadmanTabValues.value(account.player, Block.LOYALTY))
        assertEquals("450", DeadmanTabValues.value(account.player, Block.DONATOR))
        assertEquals("1,234,567", DeadmanTabValues.value(account.player, Block.DEADMAN))
        // Loyalty is the very attribute every loyalty grant writes (Player.addLoyalty): no copy in between.
        account.player.attr[LOYALTY_POINTS] = 99
        assertEquals("99", DeadmanTabValues.value(account.player, Block.LOYALTY))
        assertEquals("0", DeadmanTabValues.value(Account().player, Block.DONATOR))
    }

    @Test
    fun `emblem block shows None or the owned tier with its points`() {
        val account = Account()
        assertEquals("None", DeadmanTabValues.value(account.player, Block.EMBLEM))
        account.bank[0] = Item(DeadmanEmblem.emblemId(4), 1)
        assertEquals("Tier 4 - 360 pts", DeadmanTabValues.value(account.player, Block.EMBLEM))
        account.bank[0] = null
        account.inventory[5] = Item(DeadmanEmblem.emblemId(6), 1)
        assertEquals("Tier 6 - 1,150 pts", DeadmanTabValues.value(account.player, Block.EMBLEM))
        account.inventory[5] = null
        assertEquals("None", DeadmanTabValues.value(account.player, Block.EMBLEM))
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
        // The panel redraws only when something it shows changed.
        assertNotEquals(QuestTab.signature(DeadmanGroupsView.view(Account(isNew = true).player)), QuestTab.signature(view))
    }

    @Test
    fun `accounts from before the choices get no note`() {
        val view = DeadmanGroupsView.view(Account(isNew = false).player)
        assertFalse(view.showNote)
    }

    @Test
    fun `groups use the existing choice data and the interface has room for each group`() {
        assertEquals(ChoiceGroup.values().toList(), QuestGuideUi.ROWS)
        assertEquals(L.GROUP_SIZES.toList(), QuestGuideUi.ROWS.map { it.quests.size })
        assertTrue(QuestGuideUi.ROWS.all { it.quests.size <= L.MAX_OPTIONS })
        val view = DeadmanGroupsView.view(Account().player)
        val layout = QuestTab.layout(view)
        // Options never overlap and the scroll height covers the last one.
        val ys = layout.optionY.flatten()
        ys.zipWithNext().forEach { (a, b) -> assertTrue(b >= a + L.OPTION_HEIGHT) }
        assertTrue(layout.height >= ys.last() + L.OPTION_HEIGHT)
    }

    // ------------------------------------------------------------------ tab choice and layout

    @Test
    fun `chosen tab is stored on the account and a new account starts on Deadman`() {
        val account = Account()
        assertEquals(QuestTab.Tab.DEADMAN, QuestTab.selected(account.player))
        assertEquals("quest_tab_selected", QuestTab.SELECTED.persistenceKey)
        QuestTab.select(account.player, QuestTab.Tab.GROUPS)
        assertEquals("GROUPS", account.attributes[QuestTab.SELECTED])
        assertEquals(QuestTab.Tab.GROUPS, QuestTab.selected(account.player))
        QuestTab.select(account.player, QuestTab.Tab.QUEST_LIST)
        assertEquals(QuestTab.Tab.QUEST_LIST, QuestTab.selected(account.player))
        account.attributes[QuestTab.SELECTED] = "SOMETHING_OLD"
        assertEquals(QuestTab.Tab.DEADMAN, QuestTab.selected(account.player))
    }

    @Test
    fun `tabs left to right with their tooltips, and the container owns the quest tab slot`() {
        assertEquals(listOf("Deadman Groups", "Quest List", "Achievement Diaries", "Deadman"), QuestTab.Tab.values().map { it.tooltip })
        assertEquals(L.CONTAINER, InterfaceDestination.QUEST_TAB.interfaceId)
        assertEquals(190, QuestTab.Tab.QUEST_LIST.panel)
        assertEquals(L.PANE_WIDTH, (0 until 4).sumOf { L.tabWidth(it) })
    }

    @Test
    fun `five Deadman blocks fit the fixed-mode panel without scrolling`() {
        assertEquals(Block.values().size, L.BLOCKS)
        assertTrue((L.BLOCKS - 1) * L.BLOCK_PITCH + L.BLOCK_HEIGHT <= L.CONTENT_HEIGHT)
        assertEquals(listOf("Loyalty Points", "Donator Points", "Deadman Points", "Archaic Emblem", "Next Breach"), Block.values().map { it.title })
    }

    @Test
    fun `Info window fits the fixed-mode main screen`() {
        assertTrue(L.INFO_WIDTH <= 512 && L.INFO_HEIGHT <= 334)
    }

    @Test
    fun `every Info text names where the points are spent`() {
        assertTrue("Loyalty Store" in QuestTabInfo.text(Block.LOYALTY) && "Xuan" in QuestTabInfo.text(Block.LOYALTY))
        assertTrue("Donator Store" in QuestTabInfo.text(Block.DONATOR))
        assertTrue("Deadman Store" in QuestTabInfo.text(Block.DEADMAN))
        assertTrue("Deadman Store" in QuestTabInfo.text(Block.EMBLEM) && "1,150" in QuestTabInfo.text(Block.EMBLEM))
        assertTrue("(UTC)" in QuestTabInfo.text(Block.BREACH))
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
    fun `right-hand tab pieces are mirrored left-right, not turned upside down`() {
        val library = CacheLibrary(CACHE_PATH)
        try {
            val comps = L.container(LootKeyInterfaceImportTool.fonts(library))
            (0 until 4).forEach { tab ->
                val right = comps.first { it.id == L.tabBase(tab) + L.TAB_RIGHT }
                assertTrue(right.hflip)
                // First byte: the client's up-down flip; second: its left-right mirror.
                assertEquals(0 to 1, DeathsOfficeInterfaceImportTool.flipBytes(right))
                assertTrue(DeathsOfficeInterfaceImportTool.encode(right).contentEquals(library.data(3, L.CONTAINER, right.id)!!), "cache holds tab $tab's right piece")
                val left = comps.first { it.id == L.tabBase(tab) + L.TAB_LEFT }
                assertEquals(0 to 0, DeathsOfficeInterfaceImportTool.flipBytes(left))
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `quest list 190 fits under the tab row at full width, nothing cut off`() {
        val library = CacheLibrary(CACHE_PATH)
        try {
            val comps = L.container(LootKeyInterfaceImportTool.fonts(library))
            val mount = comps.first { it.id == L.QUEST_MOUNT }
            val area = L.PANE_HEIGHT - L.TAB_HEIGHT
            assertEquals(L.PANE_WIDTH, mount.width, "190 gets the full pane width (its filters reach x 183)")
            assertEquals(area, mount.height)
            fun u2(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
            library.index(3).archive(190)!!.fileIds().forEach { f ->
                val b = library.data(3, 190, f)!!
                if (b[0].toInt() and 0xFF != 255 || u2(b, 16) != 0) return@forEach
                val (x, y, w, h) = listOf(u2(b, 4), u2(b, 6), u2(b, 8), u2(b, 10))
                val (wMode, hMode, xMode, yMode) = listOf(b[12].toInt(), b[13].toInt(), b[14].toInt(), b[15].toInt())
                if (wMode == 0 && xMode == 0) assertTrue(x + w <= L.PANE_WIDTH, "190:$f ends at x ${x + w}")
                // Text and buttons fit completely; a decoration graphic (type 5, the bottom shade 6 hung 7 px over the
                // 261 in the original too) only has to start inside the area and keep the original's visible part.
                if (hMode == 0 && yMode == 0) {
                    if (b[1].toInt() == 5) assertTrue(y < area, "190:$f starts at y $y") else assertTrue(y + h <= area, "190:$f ends at y ${y + h}")
                }
                // Bottom-aligned with a fixed height (the quest list): inside the area and below the filters (y 76).
                if (hMode == 0 && yMode == 2) assertTrue(area - y - h >= 76, "190:$f top at ${area - y - h}")
            }
            assertTrue("QUEST_SCROLLBAR, hidden = true" in File("src/main/kotlin/gg/rsmod/plugins/content/quests/tab/QuestTab.kt").readText(), "no second scrollbar")
        } finally {
            library.close()
        }
    }

    @Test
    fun `questtabshots only closes an Info window that is still open`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/quests/tab/quest_tab_container.plugin.kts").readText()
        val shots = script.substringAfter("on_command(\"questtabshots\"")
        assertFalse(Regex("""\n\s*player\.closeInterface\(QuestTab\.INFO\)""").containsMatchIn(shots), "an unguarded close logs 'Interface 1175 is not visible'")
        assertTrue("if (player.interfaces.isVisible(QuestTab.INFO)) player.closeInterface(QuestTab.INFO)" in shots)
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
