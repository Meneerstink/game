package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS-IMPORT Granite maul Quick Smash against the OSRS Wiki Special attack and Mechanics sections. */
class GraniteMaulTests {
    @Test
    fun `Quick Smash costs 60 percent, 50 with the ornate handle, and never more than two prepared`() {
        assertEquals(60, GraniteMaul.cost(Items.GRANITE_MAUL))
        assertEquals(50, GraniteMaul.cost(Items.GRANITE_MAUL_ORNATE_HANDLE))
        assertNull(GraniteMaul.cost(Items.ABYSSAL_WHIP))
        assertEquals(1, GraniteMaul.smashCount(energy = 100, cost = 60, doubled = false))
        assertEquals(1, GraniteMaul.smashCount(energy = 100, cost = 60, doubled = true), "energy limits the second smash")
        assertEquals(2, GraniteMaul.smashCount(energy = 100, cost = 50, doubled = true))
        assertEquals(1, GraniteMaul.smashCount(energy = 100, cost = 50, doubled = false))
        assertEquals(0, GraniteMaul.smashCount(energy = 49, cost = 50, doubled = true))
    }

    @Test
    fun `homing lasts 5 ticks after an attack and the deselect window 3 ticks after the tick it opened`() {
        assertTrue(GraniteMaul.homingActive(lastAttackCycle = 100, now = 105))
        assertFalse(GraniteMaul.homingActive(lastAttackCycle = 100, now = 106))
        assertFalse(GraniteMaul.homingActive(lastAttackCycle = null, now = 100))
        assertFalse(GraniteMaul.windowActive(opened = 200, now = 200), "not on the deselect tick itself")
        assertTrue(GraniteMaul.windowActive(opened = 200, now = 201))
        assertTrue(GraniteMaul.windowActive(opened = 200, now = 203))
        assertFalse(GraniteMaul.windowActive(opened = 200, now = 204))
        assertFalse(GraniteMaul.windowActive(opened = null, now = 201))
    }

    @Test
    fun `the special runs from the bar click and before the attack delay, with no cooldown and no 667 delayed special`() {
        val cycle = File("src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts").readText()
        val hook = cycle.indexOf("GraniteMaul.onCombatCycle(pawn, target)")
        assertTrue(hook > 0 && hook < cycle.indexOf("if (Combat.isAttackDelayReady(pawn))"), "instant: before the attack delay")
        assertTrue("GraniteMaul.onBarClick(player)" in File("src/main/kotlin/gg/rsmod/plugins/content/inter/attack/attack_tab.plugin.kts").readText())
        assertTrue("GraniteMaul.LAST_ATTACK_CYCLE] = pawn.world.currentCycle" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/Combat.kt").readText())
        assertFalse("SpecialAttacks.register(50, Items.GRANITE_MAUL)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/melee_specials.plugin.kts").readText())
        val model = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/GraniteMaul.kt").readText()
        assertTrue("if (remaining != null) player.timers[ATTACK_DELAY] = remaining else player.timers.remove(ATTACK_DELAY)" in model, "no attack cooldown")
        assertTrue("SpecialAttackSupport.meleeHit(player, target, delay = 0)" in model, "no accuracy increase, instant hit")
    }
}
