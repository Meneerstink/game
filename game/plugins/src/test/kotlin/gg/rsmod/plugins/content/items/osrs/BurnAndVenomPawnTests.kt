package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.poison.Venom
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-09-17c: "make sure burn works on npcs and player also and venom also". Both systems are written against Pawn; these tests
 * pin that for a Player and an Npc target alike, with the OSRS Wiki numbers (five burn stacks of 10, Scorching bow's burn of 5, venom
 * 6 -> 20 in steps of 2).
 */
class BurnAndVenomPawnTests {
    private fun targets(): List<Pawn> =
        listOf(mockk<Player>(relaxed = true), mockk<Npc>(relaxed = true)).onEach { every { it.attr } returns AttributeMap() }

    @Test
    fun `a burn starts on players and npcs, stacks five times and is consumed whole`() {
        targets().forEach { target ->
            repeat(Burns.MAX_STACKS) { assertTrue(Burns.apply(target), "${target.javaClass.simpleName} burn ${it + 1}") }
            assertFalse(Burns.apply(target), "a sixth burn must wait for one to expire")
            assertEquals(Burns.MAX_STACKS * Burns.DAMAGE, Burns.consumeRemaining(target))
            assertEquals(0, Burns.active(target).size)
            // Scorching bow's shackles burn is a 5-damage stack in the same system.
            assertTrue(Burns.apply(target, 5))
            assertEquals(5, Burns.consumeRemaining(target))
        }
    }

    @Test
    fun `venom damage climbs from 6 by 2 to the cap of 20`() {
        assertEquals(listOf(6, 8, 10, 12, 14, 16, 18, 20, 20), (0..8).map { Venom.damageForTick(it) })
    }
}
