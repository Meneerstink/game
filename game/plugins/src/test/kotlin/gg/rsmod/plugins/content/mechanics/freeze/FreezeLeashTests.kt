package gg.rsmod.plugins.content.mechanics.freeze

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.ext.freeze
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OSRS Wiki "Freeze": a player's freeze on a player only restricts movement while both stay within the 10-tile maximum player
 * weapon range. Combat run 2026-09-29: the freeze used to hold for its full duration whatever the distance.
 */
class FreezeLeashTests {
    @Test
    fun `a player freeze holds within 10 tiles and re-arms its check every tick`() {
        val caster = player(Tile(3200, 3200))
        val target = player(Tile(3210, 3190))
        assertTrue(target.freeze(32, caster) {})
        assertEquals(1, target.timers[FreezeLeash.CHECK_TIMER])

        repeat(5) { assertFalse(FreezeLeash.check(target)) }
        assertTrue(target.timers.has(FROZEN_TIMER))
        assertEquals(1, target.timers[FreezeLeash.CHECK_TIMER])
    }

    @Test
    fun `the freeze ends with 5 ticks immunity once the caster is 11 tiles away`() {
        var casterTile = Tile(3200, 3200)
        val caster = player(casterTile)
        every { caster.tile } answers { casterTile }
        val target = player(Tile(3200, 3210))
        assertTrue(target.freeze(32, caster) {})

        casterTile = Tile(3200, 3199)
        assertTrue(FreezeLeash.check(target))
        assertFalse(target.timers.has(FROZEN_TIMER))
        assertEquals(5, target.timers[FREEZE_IMMUNITY_TIMER])
        assertFalse(target.timers.exists(FreezeLeash.CHECK_TIMER))
        // Still immune: a new freeze in the window does not land.
        assertFalse(target.freeze(32, caster) {})
    }

    @Test
    fun `a caster on another plane or logged out breaks the freeze`() {
        val caster = player(Tile(3200, 3200, 1))
        val target = player(Tile(3200, 3201, 0))
        assertTrue(target.freeze(8, caster) {})
        assertTrue(FreezeLeash.check(target))

        val gone = player(Tile(3200, 3200))
        every { gone.isOnline } returns false
        val target2 = player(Tile(3200, 3201))
        assertTrue(target2.freeze(8, gone) {})
        assertTrue(FreezeLeash.check(target2))
    }

    @Test
    fun `npc freezes and freezes on npcs are not leashed`() {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.timers } returns TimerMap()
        every { npc.attr } returns AttributeMap()
        val target = player(Tile(3200, 3200))
        assertTrue(target.freeze(8, npc) {})
        assertFalse(target.timers.exists(FreezeLeash.CHECK_TIMER))

        val frozenNpc = mockk<Npc>(relaxed = true)
        every { frozenNpc.timers } returns TimerMap()
        every { frozenNpc.attr } returns AttributeMap()
        assertTrue(frozenNpc.freeze(8, target) {})
        assertFalse(frozenNpc.timers.exists(FreezeLeash.CHECK_TIMER))
    }

    @Test
    fun `the leash check stops by itself when the freeze already ended`() {
        val caster = player(Tile(3200, 3200))
        val target = player(Tile(3200, 3201))
        assertTrue(target.freeze(8, caster) {})
        target.timers.remove(FROZEN_TIMER)
        assertFalse(FreezeLeash.check(target))
        assertFalse(target.timers.exists(FreezeLeash.CHECK_TIMER))
        assertEquals(null, target.attr[FreezeLeash.SOURCE_ATTR])
    }

    @Test
    fun `the leash state is cleared by a death`() {
        assertTrue(FreezeLeash.CHECK_TIMER.resetOnDeath && FreezeLeash.SOURCE_ATTR.resetOnDeath)
    }

    private fun player(tile: Tile): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.timers } returns TimerMap()
        every { p.attr } returns AttributeMap()
        every { p.tile } returns tile
        every { p.isOnline } returns true
        return p
    }
}
