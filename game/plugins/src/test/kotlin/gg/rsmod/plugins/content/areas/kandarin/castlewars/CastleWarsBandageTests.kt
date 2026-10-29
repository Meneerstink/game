package gg.rsmod.plugins.content.areas.kandarin.castlewars

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals

/** K07 N412: OSRS Castle Wars bandages and barricade spots. */
class CastleWarsBandageTests {
    @Test
    fun `bandages heal 10 percent rounded down and half again with a brace`() {
        assertEquals(9, CastleWarsHandler.bandageHeal(99, brace = false))
        assertEquals(14, CastleWarsHandler.bandageHeal(99, brace = true))
        assertEquals(1, CastleWarsHandler.bandageHeal(10, brace = false))
        assertEquals(30.0, CastleWarsHandler.BANDAGE_RUN_ENERGY, 0.0)
    }

    @Test
    fun `no barricade on the tiles in front of the castle doors`() {
        assertEquals(false, CastleWarsHandler.canPlaceBarricade(Tile(2422, 3076, 1)))
        assertEquals(false, CastleWarsHandler.canPlaceBarricade(Tile(2377, 3131, 1)))
        assertEquals(true, CastleWarsHandler.canPlaceBarricade(Tile(2424, 3078, 1)))
    }
}
