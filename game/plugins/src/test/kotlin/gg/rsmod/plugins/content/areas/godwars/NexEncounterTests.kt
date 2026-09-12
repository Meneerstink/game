package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter
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
}
