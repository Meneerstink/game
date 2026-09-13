package gg.rsmod.plugins.content.inter.attack

import gg.rsmod.game.model.timer.SPECIAL_ATTACK_TIMER
import gg.rsmod.game.model.timer.TimerMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SpecialEnergyRegen] against the OSRS Wiki "Special attacks" and "Lightbearer" rules, including the owner's plan
 * requirement that equipping/unequipping, relog and the normal timer share one timer without free restores.
 */
class SpecialEnergyRegenTests {
    @Test
    fun `normal regeneration is 10 percent every 50 ticks and the Lightbearer halves the interval`() {
        val timers = TimerMap()
        SpecialEnergyRegen.onLogin(timers, wearingLightbearer = false)
        assertEquals(50, timers[SPECIAL_ATTACK_TIMER])
        assertEquals(60, SpecialEnergyRegen.onTimer(timers, 50, wearingLightbearer = false))
        assertEquals(50, timers[SPECIAL_ATTACK_TIMER])
        assertEquals(60, SpecialEnergyRegen.onTimer(timers, 50, wearingLightbearer = true))
        assertEquals(25, timers[SPECIAL_ATTACK_TIMER])
        assertEquals(100, SpecialEnergyRegen.onTimer(timers, 95, wearingLightbearer = true), "capped at 100")
        SpecialEnergyRegen.onLogin(timers, wearingLightbearer = true)
        assertEquals(25, timers[SPECIAL_ATTACK_TIMER], "login restarts the timer at the worn ring's interval")
    }

    @Test
    fun `equipping resets the timer only while natural regeneration is below half, unequipping always resets`() {
        val early = TimerMap().apply { set(SPECIAL_ATTACK_TIMER, 40) }
        SpecialEnergyRegen.onLightbearerEquipped(early)
        assertEquals(25, early[SPECIAL_ATTACK_TIMER], "below half (40 left) -> restart at 25")

        val late = TimerMap().apply { set(SPECIAL_ATTACK_TIMER, 10) }
        SpecialEnergyRegen.onLightbearerEquipped(late)
        assertEquals(10, late[SPECIAL_ATTACK_TIMER], "past half (10 left) -> no delay")

        val halfway = TimerMap().apply { set(SPECIAL_ATTACK_TIMER, 25) }
        SpecialEnergyRegen.onLightbearerEquipped(halfway)
        assertEquals(25, halfway[SPECIAL_ATTACK_TIMER])

        SpecialEnergyRegen.onLightbearerUnequipped(late)
        assertEquals(50, late[SPECIAL_ATTACK_TIMER])
    }

    @Test
    fun `swapping the ring every tick never restores faster than 10 percent per 25 ticks`() {
        listOf(1, 2, 3, 5, 13, 24, 25, 26).forEach { swapEvery ->
            val timers = TimerMap()
            var wearing = true
            SpecialEnergyRegen.onLogin(timers, wearing)
            var energy = 0
            var restores = 0
            val ticks = 1000
            for (tick in 1..ticks) {
                if (tick % swapEvery == 0) {
                    wearing = !wearing
                    if (wearing) SpecialEnergyRegen.onLightbearerEquipped(timers) else SpecialEnergyRegen.onLightbearerUnequipped(timers)
                }
                val left = timers[SPECIAL_ATTACK_TIMER] - 1
                timers[SPECIAL_ATTACK_TIMER] = left
                if (left <= 0) {
                    energy = SpecialEnergyRegen.onTimer(timers, energy, wearing)
                    restores++
                }
            }
            assertTrue(restores <= ticks / SpecialEnergyRegen.LIGHTBEARER_TICKS, "swap every $swapEvery ticks gave $restores restores")
            assertTrue(energy <= 100)
        }
    }

    @Test
    fun `the plugin routes login, timer and both Lightbearer hooks through SpecialEnergyRegen`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/inter/attack/attack_tab.plugin.kts").readText()
        assertTrue("SpecialEnergyRegen.onLogin(" in script)
        assertTrue("SpecialEnergyRegen.onTimer(" in script)
        assertTrue("on_item_equip(item = Items.LIGHTBEARER)" in script && "SpecialEnergyRegen.onLightbearerEquipped(" in script)
        assertTrue("on_item_unequip(item = Items.LIGHTBEARER)" in script && "SpecialEnergyRegen.onLightbearerUnequipped(" in script)
        assertTrue("player.timers[SPECIAL_ATTACK_TIMER] = 50" !in script, "no second, hard-coded timer path")
    }
}
