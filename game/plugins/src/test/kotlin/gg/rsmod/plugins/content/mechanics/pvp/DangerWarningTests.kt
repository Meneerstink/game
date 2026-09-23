package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-09-23: the Dangerous-area warning shows on every Safe -> Dangerous move, but only while a new account's welcome
 * introduction runs; afterwards (and for existing accounts) never, unless the player turns it back on.
 */
class DangerWarningTests {
    /** Grand Exchange home square (guarded) and the Wilderness just north of Edgeville (dangerous). */
    private val safe = Tile(3165, 3487, 0)
    private val dangerous = Tile(3100, 3530, 0)

    private var cycle = 1000

    private fun player(intro: Boolean = true): Player {
        val world = mockk<World>(relaxed = true)
        every { world.currentCycle } answers { cycle }
        val p = mockk<Player>(relaxed = true)
        every { p.attr } returns AttributeMap()
        every { p.world } returns world
        every { p.entityType } returns EntityType.CLIENT
        every { p.initiated } returns true
        if (intro) DangerWarning.startIntro(p)
        return p
    }

    @Test
    fun `the test tiles really are safe and dangerous`() {
        assertFalse(AreaState.isDangerous(safe))
        assertTrue(AreaState.isDangerous(dangerous))
    }

    @Test
    fun `safe to dangerous warns during the intro, every other direction does not`() {
        val p = player()
        assertTrue(DangerWarning.needsWarning(p, safe, dangerous))
        assertFalse(DangerWarning.needsWarning(p, dangerous, safe))
        assertFalse(DangerWarning.needsWarning(p, dangerous, dangerous))
        assertFalse(DangerWarning.needsWarning(p, safe, safe))
    }

    @Test
    fun `existing accounts and finished intros never warn`() {
        assertFalse(DangerWarning.needsWarning(player(intro = false), safe, dangerous))
        val p = player()
        DangerWarning.endIntro(p)
        assertFalse(DangerWarning.needsWarning(p, safe, dangerous))
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
    fun `don't show again switches it off, the Doomsayer turns it back on for good`() {
        val p = player()
        DangerWarning.setActive(p, false)
        assertFalse(DangerWarning.needsWarning(p, safe, dangerous))
        DangerWarning.setActive(p, true)
        DangerWarning.endIntro(p)
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
