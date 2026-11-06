package gg.rsmod.plugins.content.activity.toa

import kotlin.random.Random
import kotlin.test.*

class ToaSuppliesTests {
    @Test fun `power exact and Life remains within published unreduced ranges`() {
        val power = ToaSupplies.bundle(ToaSupplies.Choice.POWER, ToaRaidSettings(), Random(1))
        assertEquals(listOf(27343, 27343, 27339), power)
        repeat(50) { seed ->
            val life = ToaSupplies.bundle(ToaSupplies.Choice.LIFE, ToaRaidSettings(), Random(seed)).groupingBy { it }.eachCount()
            assertEquals(5, life[27315]); assertEquals(5, life[27327])
            assertTrue(life.getValue(27323) in 1..3); assertTrue(life.getValue(27335) in 1..3)
            assertTrue(life.getValue(27347) in 1..2)
        }
    }

    @Test fun `withdraw selected slot retries full inventory and last item releases bag slot`() {
        val bag = ToaSupplyBag(listOf(10, 20, 30))
        assertFalse(bag.withdraw(1) { _, _ -> false })
        assertEquals(listOf(10, 20, 30), bag.contents())
        assertTrue(bag.withdraw(1) { id, last -> assertEquals(20, id); assertFalse(last); true })
        assertEquals(listOf(10, null, 30), bag.contents())
        assertTrue(bag.put(40)); assertEquals(listOf(10, 40, 30), bag.contents())
        bag.withdraw(0) { _, _ -> true }; bag.withdraw(2) { _, _ -> true }
        assertTrue(bag.withdraw(1) { id, last -> assertEquals(40, id); assertTrue(last); true })
        assertTrue(bag.empty)
        assertFalse(bag.withdraw(1) { _, _ -> error("double withdraw") })
    }
}
