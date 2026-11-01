package gg.rsmod.plugins.content.skills

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N410: cape Boost cannot restore drained stats. */
class SkillcapeBoostTests {
    @Test
    fun `boost adds one level up to 100 and waits until the skill is back at 99`() {
        assertEquals(100, SkillcapeBoost.boosted(base = 99, current = 99))
        assertEquals(null, SkillcapeBoost.boosted(base = 99, current = 90))
        assertEquals(null, SkillcapeBoost.boosted(base = 99, current = 97))
        assertEquals(null, SkillcapeBoost.boosted(base = 99, current = 100))
        assertEquals(null, SkillcapeBoost.boosted(base = 99, current = 105))
        assertEquals(null, SkillcapeBoost.boosted(base = 98, current = 98))
    }

    @Test
    fun `only the hitpoints and prayer capes have the one minute cooldown`() {
        assertEquals(true, SkillcapeBoost.hasCooldown(Skills.CONSTITUTION))
        assertEquals(true, SkillcapeBoost.hasCooldown(Skills.PRAYER))
        assertEquals(false, SkillcapeBoost.hasCooldown(Skills.ATTACK))
        assertEquals(100, SkillcapeBoost.COOLDOWN_TICKS)
    }

    @Test
    fun `both cape versions map to their skill`() {
        assertEquals(Skillcapes.ATTACK, SkillcapeBoost.capeFor(Items.ATTACK_CAPE))
        assertEquals(Skillcapes.ATTACK, SkillcapeBoost.capeFor(Items.ATTACK_CAPE_T))
        assertEquals(null, SkillcapeBoost.capeFor(Items.FIRE_CAPE))
        Skillcapes.values().forEach { assertEquals(null, SkillcapeBoost.capeFor(it.hood)) }
    }

    @Test
    fun `Constitution and Prayer use point storage and cooldown persists through removal`() {
        listOf(Skillcapes.CONSTITUTION, Skillcapes.PRAYER).forEach { cape ->
            val p = mockk<Player>(relaxed = true)
            val world = mockk<World>(relaxed = true)
            val skill = cape.ordinal
            val skills = SkillSet(25).also { it.setBaseLevel(skill, 99); it.setCurrentLevel(skill, 99) }
            var points = 97
            var cycle = 0
            every { p.world } returns world
            every { world.currentCycle } answers { cycle }
            every { p.skills } returns skills
            every { p.attr } returns AttributeMap()
            every { p.getCurrentLifepoints() } answers { points }
            every { p.getCurrentPrayerPoints() } answers { points }
            every { p.setCurrentLifepoints(any()) } answers { points = firstArg() }
            every { p.setCurrentPrayerPoints(any()) } answers { points = firstArg() }
            assertFalse(SkillcapeBoost.apply(p, cape))
            assertEquals(97, points)
            points = 99
            assertTrue(SkillcapeBoost.apply(p, cape))
            assertEquals(100, points)
            SkillcapeBoost.remove(p, cape.untrimmedCape)
            assertEquals(99, points)
            cycle = 99
            assertFalse(SkillcapeBoost.apply(p, cape))
            cycle = 100
            assertTrue(SkillcapeBoost.apply(p, cape))
            SkillcapeBoost.remove(p, cape.untrimmedCape)
            assertEquals(99, points)
            if (skill == Skills.PRAYER) assertEquals(99, skills.getCurrentLevel(skill))
        }
    }
}
