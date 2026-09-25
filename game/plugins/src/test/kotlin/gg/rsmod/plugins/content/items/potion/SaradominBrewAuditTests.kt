package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.Skills
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Audit C-01: Saradomin brew drains 10% + 2 of the current level, never raises it, and never throws. */
class SaradominBrewAuditTests {
    @Test
    fun `brew drain is minus ten percent plus two at every level and never positive`() {
        val expected = mapOf(1 to -2, 10 to -3, 25 to -4, 30 to -5, 99 to -11)
        expected.forEach { (level, drain) ->
            assertEquals(drain, PotionType.SARADOMIN_BREW.boostQuantity(level.toDouble(), "brewDrain"), "drain at $level")
        }
        for (level in 1..120) {
            assertTrue(PotionType.SARADOMIN_BREW.boostQuantity(level.toDouble(), "brewDrain") < 0, "drain at $level must be negative")
        }
    }

    @Test
    fun `a brew at Attack 25 does not throw and drains from the current level`() {
        val skills = SkillSet(SkillSet.DEFAULT_SKILL_COUNT)
        for (skill in 0 until SkillSet.DEFAULT_SKILL_COUNT) {
            skills.setBaseLevel(skill, 25)
        }
        // A previously drained Attack: the drain is taken from the current level (20 -> 20 - (2 + 2) = 16).
        skills.setCurrentLevel(Skills.ATTACK, 20)
        val player = mockk<Player>(relaxed = true)
        every { player.skills } returns skills
        every { player.timers } returns TimerMap()

        PotionType.SARADOMIN_BREW.apply(player)

        assertEquals(16, skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(25 - 4, skills.getCurrentLevel(Skills.STRENGTH))
        assertEquals(25 + 7, skills.getCurrentLevel(Skills.DEFENCE))
    }

    @Test
    fun `potion delays are set even when the effect throws`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/Potions.kt").readText()
        val applyAt = source.indexOf("potion.potionType.apply(player)")
        val finallyAt = source.indexOf("} finally {", applyAt)
        assertTrue(applyAt > 0 && finallyAt > applyAt, "apply must be wrapped in try/finally")
        assertTrue(source.indexOf("player.timers[POTION_DELAY] = TICK_DELAY", finallyAt) > finallyAt)
    }
}
