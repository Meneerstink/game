package gg.rsmod.game.model.combat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the kill-credit rule `PlayerDeathAction` depends on.
 *
 * A damage map has no notion of "this fight": it accumulates for as long as it lives, so the
 * untimed [DamageMap.getMostDamage] returns the highest *lifetime* damage dealer. Two things keep
 * that from turning a PvM death into a PvP one - the map is cleared on every player death (an npc's
 * already was, through `Npc.reset`), and the killer snapshot only considers damage dealt inside the
 * PvP aggressor window. Both are asserted on the source, in the same style as
 * `PlayerDeathHookIsolationTests`, because a [gg.rsmod.game.model.entity.Pawn] key cannot be built
 * in this module's tests without a full `World`.
 *
 * Audit T-12: the window is counted in game cycles (it was wall-clock milliseconds).
 */
class DamageMapTests {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun `most damage can be restricted to a recent number of cycles`() {
        val source = source("src/main/kotlin/gg/rsmod/game/model/combat/DamageMap.kt")

        assertTrue("fun getMostDamage(timeFrameCycles: Int? = null): Pawn?" in source)
        assertTrue("withinWindow(it.value.lastHitCycle, now, timeFrameCycles)" in source)
        assertFalse("System.currentTimeMillis() - it.value.lastHit" in source, "no window may use the wall clock")
        assertTrue("fun reset()" in source)
    }

    @Test
    fun `a hit on cycle 0 still counts on cycle 99 but not on cycle 100 or 101`() {
        assertTrue(DamageMap.withinWindow(lastHitCycle = 0, nowCycle = 99, windowCycles = 100))
        assertFalse(DamageMap.withinWindow(lastHitCycle = 0, nowCycle = 100, windowCycles = 100))
        assertFalse(DamageMap.withinWindow(lastHitCycle = 0, nowCycle = 101, windowCycles = 100))
        assertTrue(DamageMap.withinWindow(lastHitCycle = 0, nowCycle = 1_000_000, windowCycles = null), "no window: always")
    }

    @Test
    fun `pawn damage maps are stamped with the world cycle`() {
        val pawn = source("src/main/kotlin/gg/rsmod/game/model/entity/Pawn.kt")
        assertTrue("val damageMap = DamageMap { world.currentCycle }" in pawn)
    }

    @Test
    fun `a player death clears the damage map and credits only recent damage`() {
        val source = source("src/main/kotlin/gg/rsmod/game/action/PlayerDeathAction.kt")

        assertTrue("player.damageMap.getMostDamage(KILL_CREDIT_WINDOW_CYCLES)" in source)
        assertTrue("private const val KILL_CREDIT_WINDOW_CYCLES = 100" in source)
        assertTrue("player.damageMap.reset()" in source)
    }

    @Test
    fun `an npc death still clears its own damage map`() {
        val source = source("src/main/kotlin/gg/rsmod/game/action/NpcDeathAction.kt")

        assertTrue("damageMap.reset()" in source)
    }
}
