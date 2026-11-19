package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.plugins.api.Skills
import kotlin.test.Test
import kotlin.test.assertEquals

/** N640: OSRS Wiki Dark relic - 150 x level for the Raids skills, 50 x level for the rest (Construction included). */
class CoxDarkRelicTests {
    @Test
    fun `xp is 150 x level for raid skills and 50 x level for the others`() {
        assertEquals(150.0 * 99, CoxDarkRelic.xpFor(Skills.ATTACK, 99))
        assertEquals(150.0 * 70, CoxDarkRelic.xpFor(Skills.HERBLORE, 70))
        assertEquals(150.0 * 50, CoxDarkRelic.xpFor(Skills.AGILITY, 50))
        assertEquals(50.0 * 80, CoxDarkRelic.xpFor(Skills.CONSTRUCTION, 80))
        assertEquals(50.0 * 60, CoxDarkRelic.xpFor(Skills.CRAFTING, 60))
    }

    @Test
    fun `only the 23 OSRS skills are offered`() {
        assertEquals(23, CoxDarkRelic.CHOICES.toSet().size)
        assertEquals(false, Skills.SUMMONING in CoxDarkRelic.CHOICES || Skills.DUNGEONEERING in CoxDarkRelic.CHOICES)
    }
}
