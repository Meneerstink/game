package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.*
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.content.inter.attack.AttackTab
import io.mockk.*
import kotlin.test.*

class ToaRecoveryTests {
    @Test fun `challenge cures only ailments and boss restores resources while preserving boosts`() {
        val player = mockk<Player>(relaxed = true)
        val skills = SkillSet(24)
        for (skill in 0 until skills.maxSkills) { skills.setXp(skill, 13_034_431.0); skills.restore(skill) }
        skills.setCurrentLevel(0, 118)
        skills.setCurrentLevel(1, 10)
        every { player.skills } returns skills
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.varps } returns VarpSet((0..8000).toSet())
        every { player.getMaximumLifepoints() } returns 990
        every { player.getCurrentLifepoints() } returns 100
        every { player.getMaximumPrayerPoints() } returns 99
        every { player.getCurrentPrayerPoints() } returns 10
        player.attr[POISON_TICKS_LEFT_ATTR] = 10
        player.attr[VENOM_TICKS_ELAPSED_ATTR] = 5
        player.timers[POISON_TIMER] = 30
        player.timers[VENOM_TIMER] = 30
        AttackTab.setEnergy(player, 5)
        ToaRecovery.challenge(player)
        assertFalse(player.attr.has(POISON_TICKS_LEFT_ATTR)); assertFalse(player.attr.has(VENOM_TICKS_ELAPSED_ATTR))
        assertFalse(player.timers.has(POISON_TIMER)); assertFalse(player.timers.has(VENOM_TIMER))
        assertEquals(5, AttackTab.getEnergy(player))
        assertEquals(10, skills.getCurrentLevel(1))
        ToaRecovery.full(player)
        assertEquals(118, skills.getCurrentLevel(0))
        assertEquals(99, skills.getCurrentLevel(1))
        assertEquals(100, AttackTab.getEnergy(player))
        verify { player.setCurrentLifepoints(990); player.setCurrentPrayerPoints(99); player.runEnergy = 100.0 }
    }
}
