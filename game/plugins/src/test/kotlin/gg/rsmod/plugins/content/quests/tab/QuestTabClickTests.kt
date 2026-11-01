package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetEventsMessage
import gg.rsmod.game.message.impl.IfSetTextMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.QP
import gg.rsmod.plugins.content.quests.foundation.QuestChoices
import gg.rsmod.plugins.content.quests.foundation.QuestGuideUi
import gg.rsmod.plugins.content.quests.impl.PriestInPeril
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** N500: exercise the actual draw -> events -> row click -> server overview route, without a cache or client. */
class QuestTabClickTests {
    private class Fixture {
        val player = mockk<Player>(relaxed = true)
        val sent = mutableListOf<Message>()
        val attrs = AttributeMap()
        val events = mutableMapOf<Int, Int>()

        init {
            val world = mockk<World>(relaxed = true)
            val defs = mockk<DefinitionSet>(relaxed = true)
            every { defs.get(VarbitDef::class.java, any()) } answers { VarbitDef(secondArg()) }
            every { world.definitions } returns defs
            every { player.world } returns world
            every { player.attr } returns attrs
            every { player.interfaces.isVisible(any()) } answers { firstArg<Int>() in listOf(QuestTab.CONTAINER, QuestTab.Tab.QUESTS.panel) }
            every { player.write(*anyVararg<Message>()) } answers {
                val messages = firstArg<Array<Message>>()
                sent.addAll(messages)
                messages.filterIsInstance<IfSetEventsMessage>().forEach {
                    assertEquals(-1, it.fromChild)
                    assertEquals(-1, it.toChild)
                    events[it.hash] = it.setting
                }
                Unit
            }
            attrs[QuestChoices.COHORT] = QuestChoices.LEGACY
            QuestTab.select(player, QuestTab.Tab.QUESTS)
        }

        fun rows() = QuestTab.visibleQuestRows(player)
        fun row(key: String) = rows().indexOfFirst { it.key == key }
        fun click(key: String, toggle: Boolean = false) {
            val slot = row(key)
            assertTrue(slot >= 0, "Missing $key in ${rows().map { it.key }}")
            QuestTab.questClick(player, slot, toggle)
        }

        fun text(component: Int) = sent.filterIsInstance<IfSetTextMessage>().last { it.parent == 178 && it.child == component }.text

        fun assertEvents() {
            val rows = rows()
            for (slot in 0 until QP.ROWS) {
                val row = rows.getOrNull(slot)
                val group = row?.key in QuestGuideUi.ROWS.map { it.name }
                assertEquals(if (row == null || group) 0 else 2, events[(QuestTab.Tab.QUESTS.panel shl 16) or (QP.row(slot) + 4)], "row $slot body ${row?.key}")
                assertEquals(if (row?.arrow != null) 2 else 0, events[(QuestTab.Tab.QUESTS.panel shl 16) or (QP.row(slot) + 3)], "row $slot arrow")
            }
        }
    }

    @Test
    fun `every group and every prerequisite including the last row opens its own server detail`() {
        val f = Fixture()
        f.assertEvents()
        for (group in QuestGuideUi.ROWS) {
            for (quest in group.quests) {
                val name = QuestGuideUi.NAMES.getValue(quest)
                assertSame(quest, QuestTabDetails.quest(name))
                f.click(name)
                assertEquals(quest.name, f.text(14))
                assertEquals(quest.startPoint, f.text(52))
                assertEquals("<br>${quest.requiredItems}", f.text(59))
                assertEquals("<br>${quest.rewards}", f.text(66))
                f.click(name, toggle = true)
                f.assertEvents()
                for (required in QuestTab.REQUIREMENTS.getValue(name)) {
                    f.click(required)
                    assertEquals(QuestTabDetails.quest(required)?.name ?: required, f.text(14))
                }
                val before = f.rows().map { it.key }
                // Scrolling changes client coordinates, never the server slot-to-quest mapping.
                QuestTab.refresh(f.player)
                assertEquals(before, f.rows().map { it.key })
                QuestTab.select(f.player, QuestTab.Tab.DROPS)
                QuestTab.select(f.player, QuestTab.Tab.QUESTS)
                f.assertEvents()
                f.click(QuestTab.REQUIREMENTS.getValue(name).last())
                f.click(name, toggle = true)
                f.assertEvents()
            }
        }
    }

    @Test
    fun `two fast toggles rebind moved rows and titles invalid slots and hidden tabs do nothing`() {
        val f = Fixture()
        repeat(3) {
            f.click("Desert Treasure", toggle = true)
            f.click("King's Ransom", toggle = true)
            f.assertEvents()
            f.click("The Dig Site")
            assertEquals("The Dig Site", f.text(14))
            assertTrue("no playable start location" in f.text(52))
            f.click("Desert Treasure", toggle = true)
            f.click("King's Ransom", toggle = true)
            f.assertEvents()
        }
        val before = f.rows().map { it.key }
        f.sent.clear()
        listOf(-1, QP.ROWS, Int.MAX_VALUE, f.row("MAGIC"), f.row("PRAYER"), f.row("GEAR")).forEach { QuestTab.questClick(f.player, it) }
        assertTrue(f.sent.isEmpty())
        assertEquals(before, f.rows().map { it.key })
        QuestTab.select(f.player, QuestTab.Tab.DROPS)
        f.sent.clear()
        QuestTab.questClick(f.player, 1)
        assertTrue(f.sent.isEmpty())
        QuestTab.select(f.player, QuestTab.Tab.QUESTS)
        every { f.player.interfaces.isVisible(QuestTab.Tab.QUESTS.panel) } returns false
        f.sent.clear()
        QuestTab.questClick(f.player, 1, toggle = true)
        assertTrue(f.sent.isEmpty())
    }

    @Test
    fun `all expansions can stay open and every overflow quest remains reachable with events`() {
        val f = Fixture()
        val expanded = mutableSetOf<String>()
        val parents = QuestGuideUi.NAMES.values.toSet()
        repeat(30) {
            val key = f.rows().firstOrNull { it.key in parents && it.key !in expanded }?.key
            if (key != null) {
                f.click(key, toggle = true)
                expanded += key
            } else if (expanded.size < parents.size) {
                if (f.row("next-page") >= 0) f.click("next-page") else f.click("previous-page")
            }
        }
        assertEquals(parents, expanded)
        while (f.row("previous-page") >= 0) f.click("previous-page")
        val seen = mutableSetOf<String>()
        do {
            f.assertEvents()
            val rows = f.rows()
            assertTrue(rows.size <= QP.ROWS)
            rows.filter { it.key in parents || it.key in QuestTab.REQUIREMENTS.values.flatten() }.forEach {
                f.click(it.key)
                assertEquals(QuestTabDetails.quest(it.key)?.name ?: it.key, f.text(14))
                seen += it.key
            }
            val next = f.row("next-page") >= 0
            if (next) f.click("next-page")
        } while (next)
        assertTrue(seen.containsAll(parents + QuestTab.REQUIREMENTS.values.flatten()))
        assertFalse(f.rows().isEmpty())
    }

    @Test
    fun `exactly thirty rows enable the final physical slot and collapsing clears its old event`() {
        val f = Fixture()
        // 11 headings/choice quests + 6 + 7 + 6 prerequisites = all 30 existing cache rows, without pagination.
        listOf("Desert Treasure", "Dragon Slayer II", "Monkey Madness II").forEach { f.click(it, toggle = true) }
        assertEquals(QP.ROWS, f.rows().size)
        assertEquals("Watchtower", f.rows().last().key)
        f.assertEvents()
        QuestTab.questClick(f.player, QP.ROWS - 1)
        assertEquals("Watchtower", f.text(14))
        f.click("Desert Treasure", toggle = true)
        f.assertEvents()
        f.sent.clear()
        QuestTab.questClick(f.player, QP.ROWS - 1)
        assertTrue(f.sent.isEmpty())
    }

    @Test
    fun `registered prerequisite uses its real requirements start items combat and rewards`() {
        val f = Fixture()
        val quest = PriestInPeril // Same registration that occurs when the server loads its quest plugins.
        f.click("Desert Treasure", toggle = true)
        f.click(quest.name)
        assertSame(quest, QuestTabDetails.quest(quest.name))
        assertEquals(quest.startPoint, f.text(52))
        assertEquals("None.", f.text(55))
        assertEquals("<br>${quest.requiredItems}", f.text(59))
        assertEquals(quest.combat, f.text(62))
        assertEquals("<br>${quest.rewards}", f.text(66))
    }

    @Test
    fun `refresh sends no redundant event packets and remount restores every event`() {
        val f = Fixture()
        f.sent.clear()
        QuestTab.refresh(f.player)
        assertTrue(f.sent.none { it is IfSetEventsMessage })
        f.events.clear()
        QuestTab.mount(f.player)
        f.assertEvents()
    }
}
