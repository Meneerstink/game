package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** M1 contract: Ferox's non-bank tiles are PvP-dangerous; bank safety is supplied by BankZones. */
class AreaStateTests {
    private val home = Tile(3137, 3629, 0)

    @Test
    fun `Ferox tile outside the bank is not a global safe zone`() {
        val feroxNonBankTile = Tile(3140, 3646, 0)

        assertFalse(BankZones.isSafe(feroxNonBankTile))
        assertTrue(AreaState.isPvpAllowed(feroxNonBankTile, home))
        assertFalse(AreaState.isSafe(feroxNonBankTile, home))
    }
}
