package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter
import gg.rsmod.plugins.content.areas.godwars.nex.NexCombatScript
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.Phase
import kotlin.test.Test
import kotlin.test.assertEquals

class NexEncounterTests {
    @Test
    fun `phase thresholds use the engine lifepoint unit`() {
        assertEquals(3000, NexEncounter.MAX_LIFEPOINTS)
        assertEquals(2400, NexEncounter.phaseFloor(Phase.SMOKE))
        assertEquals(1800, NexEncounter.phaseFloor(Phase.SHADOW))
        assertEquals(1200, NexEncounter.phaseFloor(Phase.BLOOD))
        assertEquals(600, NexEncounter.phaseFloor(Phase.ICE))
        assertEquals(0, NexEncounter.phaseFloor(Phase.ZAROS))
        assertEquals(600, NexEncounter.ZAROS_HEAL)
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

    @Test
    fun `Nex source hit audio and Soul Split use the runtime scale`() {
        assertEquals(12, NexEncounter.HIT_SOUNDS.size)
        assertEquals(false, NexEncounter.shouldPlayHitSound(14))
        assertEquals(true, NexEncounter.shouldPlayHitSound(15))
        assertEquals(0, NexCombatScript.soulSplitAmount(4))
        assertEquals(3, NexCombatScript.soulSplitAmount(19))
    }
}
