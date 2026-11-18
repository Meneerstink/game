package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/** N635: OSRS Wiki 'Salarin the twisted' - only Strike spells (fixed 9/10/11/12 by highest unlocked Bolt), recoil passes, rest 0. */
class SalarinTheTwistedTests {
    private val salarin = mockk<Npc>().also { every { it.id } returns Npcs.SALARIN_THE_TWISTED }
    private val other = mockk<Npc>().also { every { it.id } returns Npcs.SALARIN_THE_TWISTED + 1 }

    private fun mage(level: Int) = mockk<Player>(relaxed = true).also { every { it.skills.getCurrentLevel(Skills.MAGIC) } returns level }

    private fun hit(level: Int, type: HitType, rolled: Int, landed: Boolean, spell: CombatSpell?) =
        SalarinTheTwisted.modifyIncomingDamage(salarin, mage(level), type, rolled, landed, spell?.uniqueId)

    @Test fun `strikes always deal the highest unlocked bolt's max`() {
        assertEquals(9, hit(17, HitType.MAGIC, 2, true, CombatSpell.WIND_STRIKE))
        assertEquals(10, hit(23, HitType.MAGIC, 0, true, CombatSpell.FIRE_STRIKE))
        assertEquals(11, hit(29, HitType.MAGIC, 5, true, CombatSpell.WATER_STRIKE))
        assertEquals(12, hit(35, HitType.MAGIC, 1, true, CombatSpell.EARTH_STRIKE))
        assertEquals(12, hit(99, HitType.MAGIC, 1, true, CombatSpell.WIND_STRIKE))
    }

    @Test fun `a splash does nothing and below 17 magic the strike keeps its own roll`() {
        assertEquals(0, hit(50, HitType.MAGIC, 0, false, CombatSpell.FIRE_STRIKE))
        assertEquals(3, hit(16, HitType.MAGIC, 3, true, CombatSpell.FIRE_STRIKE))
    }

    @Test fun `melee ranged and every other spell do no damage, recoil does`() {
        assertEquals(0, hit(99, HitType.MELEE, 40, true, null))
        assertEquals(0, hit(99, HitType.RANGE, 30, true, null))
        assertEquals(0, hit(99, HitType.MAGIC, 20, true, CombatSpell.FIRE_BOLT))
        assertEquals(0, hit(99, HitType.MAGIC, 20, true, null))
        assertEquals(7, hit(99, HitType.REFLECTED, 7, true, null))
    }

    @Test fun `other npcs are untouched`() {
        assertEquals(40, SalarinTheTwisted.modifyIncomingDamage(other, mage(99), HitType.MELEE, 40, true, null))
    }
}
