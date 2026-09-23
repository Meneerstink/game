package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_ACTIVE_CYCLE_ATTR
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-09-23: the Dangerous-area warning shows on every Safe -> Dangerous move, the "don't show again" choice unlocks
 * only after one hour of genuine active play, and nothing bypasses it.
 */
class DangerWarningTests {
    /** Grand Exchange home square (guarded) and the Wilderness just north of Edgeville (dangerous). */
    private val safe = Tile(3165, 3487, 0)
    private val dangerous = Tile(3100, 3530, 0)

    private var cycle = 1000

    private fun player(): Player {
        val world = mockk<World>(relaxed = true)
        every { world.currentCycle } answers { cycle }
        val p = mockk<Player>(relaxed = true)
        every { p.attr } returns AttributeMap()
        every { p.world } returns world
        every { p.entityType } returns EntityType.CLIENT
        every { p.initiated } returns true
        return p
    }

    @Test
    fun `the test tiles really are safe and dangerous`() {
        assertFalse(AreaState.isDangerous(safe))
        assertTrue(AreaState.isDangerous(dangerous))
    }

    @Test
    fun `safe to dangerous warns, every other direction does not`() {
        val p = player()
        assertTrue(DangerWarning.needsWarning(p, safe, dangerous))
        assertFalse(DangerWarning.needsWarning(p, dangerous, safe))
        assertFalse(DangerWarning.needsWarning(p, dangerous, dangerous))
        assertFalse(DangerWarning.needsWarning(p, safe, safe))
    }

    @Test
    fun `a confirmation lets exactly one crossing through`() {
        val p = player()
        DangerWarning.allowNextCrossing(p)
        assertFalse(DangerWarning.needsWarning(p, safe, dangerous))
        assertTrue(DangerWarning.needsWarning(p, safe, dangerous))
    }

    @Test
    fun `an expired confirmation does not let a later crossing through`() {
        val p = player()
        DangerWarning.allowNextCrossing(p)
        cycle += DangerWarning.BYPASS_TICKS + 1
        assertTrue(DangerWarning.needsWarning(p, safe, dangerous))
    }

    @Test
    fun `new players cannot switch warnings off, not even by writing the preference`() {
        val p = player()
        DangerWarning.setDisabled(p, true)
        assertFalse(DangerWarning.isDisabled(p))
        p.attr[DangerWarning.DISABLED] = true // a stale or forged save value
        assertFalse(DangerWarning.isDisabled(p))
        assertTrue(DangerWarning.needsWarning(p, safe, dangerous))
    }

    @Test
    fun `only active minutes count towards the hour`() {
        val p = player()
        DangerWarning.countActive(p, 100) // never acted: nothing
        assertEquals(0, DangerWarning.activeTicks(p))
        p.attr[LAST_ACTIVE_CYCLE_ATTR] = cycle - DangerWarning.ACTIVE_WINDOW_TICKS - 1 // AFK
        DangerWarning.countActive(p, 100)
        assertEquals(0, DangerWarning.activeTicks(p))
        p.attr[LAST_ACTIVE_CYCLE_ATTR] = cycle - 10 // acted just now
        DangerWarning.countActive(p, 100)
        assertEquals(100, DangerWarning.activeTicks(p))
    }

    @Test
    fun `after an hour of active play the warning can be switched off and back on`() {
        val p = player()
        p.attr[DangerWarning.ACTIVE_TICKS] = DangerWarning.UNLOCK_TICKS
        DangerWarning.setDisabled(p, true)
        assertTrue(DangerWarning.isDisabled(p))
        assertFalse(DangerWarning.needsWarning(p, safe, dangerous))
        DangerWarning.setDisabled(p, false)
        assertTrue(DangerWarning.needsWarning(p, safe, dangerous))
    }

    @Test
    fun `bots and players still logging in are never held back`() {
        val bot = player()
        every { bot.entityType } returns EntityType.PLAYER
        assertFalse(DangerWarning.needsWarning(bot, safe, dangerous))
        val loggingIn = player()
        every { loggingIn.initiated } returns false
        assertFalse(DangerWarning.needsWarning(loggingIn, safe, dangerous))
    }
}
