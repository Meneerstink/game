package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter
import gg.rsmod.plugins.content.areas.godwars.nex.NexCombatScript
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.Phase
import kotlin.test.Test
import kotlin.test.assertEquals

class NexEncounterTests {
    @Test
    fun `phase thresholds use the engine lifepoint unit`() {
        assertEquals(30000, NexEncounter.MAX_LIFEPOINTS)
        assertEquals(24000, NexEncounter.phaseFloor(Phase.SMOKE))
        assertEquals(18000, NexEncounter.phaseFloor(Phase.SHADOW))
        assertEquals(12000, NexEncounter.phaseFloor(Phase.BLOOD))
        assertEquals(6000, NexEncounter.phaseFloor(Phase.ICE))
        assertEquals(0, NexEncounter.phaseFloor(Phase.ZAROS))
        assertEquals(6000, NexEncounter.ZAROS_HEAL)
    }

    @Test
    fun `phase sounds are the sourced 667 Nex events`() {
        assertEquals(3325, Phase.SMOKE.startSound)
        assertEquals(3310, Phase.SMOKE.transitionSound)
        assertEquals(3313, Phase.SHADOW.startSound)
        assertEquals(3307, Phase.SHADOW.transitionSound)
        assertEquals(3299, Phase.BLOOD.startSound)
        assertEquals(3298, Phase.BLOOD.transitionSound)
        assertEquals(3304, Phase.ICE.startSound)
        assertEquals(3327, Phase.ICE.transitionSound)
        assertEquals(3312, Phase.ZAROS.transitionSound)
    }

    @Test
    fun `ice and Zaros rotations use the sourced inclusive local bounds`() {
        assertEquals(1, NexCombatScript.nextIceAttackIndex(0))
        assertEquals(10, NexCombatScript.nextIceAttackIndex(9))
        assertEquals(0, NexCombatScript.nextIceAttackIndex(10))
        assertEquals(2, NexCombatScript.zarosAttackRoll(false, 2))
        assertEquals(13, NexCombatScript.zarosAttackRoll(true, 10))
    }
}
