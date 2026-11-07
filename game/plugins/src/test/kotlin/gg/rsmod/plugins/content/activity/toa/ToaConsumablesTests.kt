package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.inter.attack.AttackTab
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class ToaConsumablesTests {
    @Test fun `dose items are the OSRS ids two apart, the noted id in between`() {
        // cache2686 / our import: Nectar (4) 27315 .. Nectar (1) 27321; Ambrosia (2) 27347, (1) 27349; Honey locust 27351 (stackable).
        assertEquals(listOf(27315, 27317, 27319, 27321), ToaConsumables.osrsDoses(ToaConsumables.Kind.NECTAR))
        assertEquals(listOf(27347, 27349), ToaConsumables.osrsDoses(ToaConsumables.Kind.AMBROSIA))
        assertEquals(listOf(27351), ToaConsumables.osrsDoses(ToaConsumables.Kind.HONEY_LOCUST))
        // Every dose lies inside the family ToaSupplies hands out and cleans up (27314..27351).
        ToaConsumables.Kind.values().flatMap(ToaConsumables::osrsDoses).forEach { assert(it in ToaSupplies.BAG_SOURCE..27351) }
    }

    @Test fun `liquid adrenaline halves the special attack cost in the energy varp's tenths`() {
        val p = mockk<Player>(relaxed = true)
        val timers = TimerMap()
        every { p.timers } returns timers
        assertEquals(250, AttackTab.specialCostTenths(p, 25))
        assertEquals(500, AttackTab.specialCostTenths(p, 50))
        timers[ToaConsumables.ADRENALINE_TIMER] = ToaConsumables.ADRENALINE_TICKS
        // Wiki Liquid adrenaline: 25% -> 12.5%, 75% -> 37.5%.
        assertEquals(125, AttackTab.specialCostTenths(p, 25))
        assertEquals(375, AttackTab.specialCostTenths(p, 75))
    }

    @Test fun `smelling salts run eight minutes in refreshes of 25 ticks, scarab and dressing as published`() {
        assertEquals(32, ToaConsumables.SALTS_EVENTS)
        assertEquals(36, ToaConsumables.SCARAB_EVENTS * ToaConsumables.SCARAB_INTERVAL)
        assertEquals(100, ToaConsumables.SILK_EVENTS * ToaConsumables.SILK_INTERVAL)
        assertEquals(250, ToaConsumables.ADRENALINE_TICKS)
    }
}
