package gg.rsmod.plugins.content.skills

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals

/** K03 N410 part 4: OSRS skillcape Boost - +1 up to 100, only from 99 or below, cooldown for Hitpoints and Prayer. */
class SkillcapeBoostTests {
    @Test
    fun `boost adds one level up to 100 and waits until the skill is back at 99`() {
        assertEquals(100, SkillcapeBoost.boosted(base = 99, current = 99))
        assertEquals(91, SkillcapeBoost.boosted(base = 99, current = 90))
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
    }
}
