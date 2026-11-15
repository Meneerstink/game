package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.NpcSkills
import kotlin.test.*

/** Exercise the built combat definition: a text check missed 23 HP instead of 230. */
class TheMimicCombatDefTests {
    @Test fun `runtime stats and bonuses match the Wiki in real HP units`() {
        val def = MimicCombat.definition()
        assertEquals(230, def.lifepoints)
        assertEquals(3, def.attackSpeed)
        assertEquals(StyleType.CRUSH, def.attackStyleType)
        assertTrue(def.poisonImmunity)
        assertTrue(def.venomImmunity)
        assertEquals(0, def.respawnDelay)
        assertEquals(15404, def.attackAnimation)
        assertEquals(listOf(15406), def.deathAnimation)
        for ((skill, level) in listOf(NpcSkills.ATTACK to 185, NpcSkills.STRENGTH to 120,
            NpcSkills.DEFENCE to 120, NpcSkills.MAGIC to 60, NpcSkills.RANGED to 1)) assertEquals(level, def.stats[skill])
        for ((slot, bonus) in listOf(BonusSlot.ATTACK_CRUSH to 135, BonusSlot.STRENGTH_BONUS to 48,
            BonusSlot.ATTACK_MAGIC to 180, BonusSlot.DEFENCE_STAB to 160, BonusSlot.DEFENCE_SLASH to 165,
            BonusSlot.DEFENCE_CRUSH to 150, BonusSlot.DEFENCE_MAGIC to 30, BonusSlot.DEFENCE_RANGED to 145)) assertEquals(bonus, def.bonuses[slot.id])
    }
}
