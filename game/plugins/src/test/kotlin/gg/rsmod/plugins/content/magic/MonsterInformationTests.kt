package gg.rsmod.plugins.content.magic

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonsterInformationTests {
    private fun npc(): Npc = mockk<Npc>(relaxed = true).also { npc ->
        every { npc.id } returns 1
        every { npc.def } returns NpcDef(1).apply { name = "N72 test monster"; combatLevel = 50 }
        every { npc.combatDef } returns NpcCombatDef.DEFAULT.copy(poisonImmunity = true, venomImmunity = true)
        every { npc.attr } returns AttributeMap()
        every { npc.species } returns setOf(NpcSpecies.DEMON)
        every { npc.getCurrentLifepoints() } returns 320
        every { npc.stats } returns Npc.Stats(5).apply {
            repeat(5) { setMaxLevel(it, 99); setCurrentLevel(it, 60 + it) }
        }
        every { npc.equipmentBonuses } returns IntArray(18).apply {
            this[BonusSlot.ATTACK_SLASH.id] = 17
            this[BonusSlot.DEFENCE_RANGED.id] = -10
        }
    }

    @Test
    fun `inspect reads drained current stats and health without changing the NPC`() {
        val npc = npc()
        val pages = MonsterInformation.pages(npc, examine = false)
        assertTrue("Life points: 320" in pages[0])
        assertTrue("Attack: 60" in pages[0])
        assertTrue("Strength: 61" in pages[0])
        assertTrue("Defence: 62" in pages[0])
        assertFalse(pages.flatten().any { "Main attack style" in it || "Slash bonus: 17" in it })
        assertEquals(60, npc.stats.getCurrentLevel(NpcSkills.ATTACK))
        assertEquals(99, npc.stats.getMaxLevel(NpcSkills.ATTACK))
        assertEquals(320, npc.getCurrentLifepoints())
    }

    @Test
    fun `examine shows detailed offensive bonuses and real shared ranged defence`() {
        val pages = MonsterInformation.pages(npc(), examine = true)
        assertTrue("Slash bonus: 17" in pages[1])
        assertTrue("Light ranged bonus: -10" in pages[2])
        assertTrue("Standard ranged bonus: -10" in pages[2])
        assertTrue("Heavy ranged bonus: -10" in pages[2])
        assertTrue("Immune to poison." in pages[3])
        assertTrue("Immune to venom." in pages[3])
        assertTrue("Demonic." in pages[3])
    }

    @Test
    fun `phase weakness and explicit no-weakness override static data`() {
        val npc = npc()
        npc.attr[ElementalWeakness.INSTANCE] = ElementalWeakness.Weakness(ElementalWeakness.Element.WATER, 50)
        assertTrue("Water weakness: 50%" in MonsterInformation.pages(npc, false)[3])
        npc.attr[ElementalWeakness.INSTANCE_NONE] = true
        assertFalse(MonsterInformation.pages(npc, true)[3].any { "weakness" in it })
    }

    @Test
    fun `double click cannot reserve two paid information casts in one cycle`() {
        val player = mockk<Player>()
        every { player.timers } returns TimerMap()
        assertTrue(MonsterInformation.claim(player))
        assertFalse(MonsterInformation.claim(player))
    }
}
