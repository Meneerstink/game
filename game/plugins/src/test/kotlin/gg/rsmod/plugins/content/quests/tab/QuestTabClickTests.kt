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
            // Quest overviews name skill requirements through enum 680 (Skills.getSkillName); a relaxed mock returned the wrong type
            // once another test had registered quests with skill requirements (package run).
            val skillNames = mockk<gg.rsmod.game.fs.def.EnumDef>(relaxed = true)
            every { skillNames.getString(any()) } returns "Attack"
            every { defs.get(gg.rsmod.game.fs.def.EnumDef::class.java, any()) } returns skillNames
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

        fun rows() = QuestTab.questRows(player)
        fun row(key: String) = rows().indexOfFirst { it.key == key }
        fun click(key: String) {
            val slot = row(key)
            assertTrue(slot >= 0, "Missing $key in ${rows().map { it.key }}")
            QuestTab.questClick(player, slot)
        }

        fun text(component: Int) = sent.filterIsInstance<IfSetTextMessage>().last { it.parent == 178 && it.child == component }.text
    }

    @Test
    fun `DMM-TAB - every row and the filter button have their click, a quest row opens its own server detail`() {
        val f = Fixture()
        for (slot in 0 until QP.ROWS) assertEquals(2, f.events[(QuestTab.Tab.QUESTS.panel shl 16) or (QP.row(slot) + 1)], "row $slot")
        assertEquals(2, f.events[(QuestTab.Tab.QUESTS.panel shl 16) or QP.FILTER])
        f.click(PriestInPeril.name)
        assertSame(PriestInPeril, QuestTabDetails.quest(PriestInPeril.name))
        assertEquals(PriestInPeril.name, f.text(14))
        assertEquals(PriestInPeril.startPoint, f.text(52))
        f.click("Cook's Assistant")
        assertTrue(f.text(14).isNotEmpty())
    }

    @Test
    fun `DMM-TAB - invalid slots, gap rows and hidden tabs do nothing`() {
        val f = Fixture()
        val gap = f.rows().indexOfFirst { it.kind == QuestTab.ROW_GAP }
        f.sent.clear()
        listOf(-1, QP.ROWS, Int.MAX_VALUE, gap).forEach { QuestTab.questClick(f.player, it) }
        assertTrue(f.sent.isEmpty())
        QuestTab.select(f.player, QuestTab.Tab.DROPS)
        f.sent.clear()
        QuestTab.questClick(f.player, 1)
        assertTrue(f.sent.isEmpty())
    }
}
