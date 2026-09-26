package gg.rsmod.plugins.content.areas.deathsoffice

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.RESPAWN_TILE_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.areas.wilderness.FeroxRespawn
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Owner 2026-09-26: respawn points bought from Death (500k once), Ferox payers keep theirs, never a dangerous tile. */
class RespawnPointsTests {
    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        return player
    }

    @Test
    fun `players who paid Ferox keep the Ferox respawn`() {
        val player = newPlayer()
        assertFalse(RespawnPoints.owns(player, RespawnPoints.Point.FEROX))
        player.attr[FeroxRespawn.PAID] = true
        assertTrue(RespawnPoints.owns(player, RespawnPoints.Point.FEROX))
        assertFalse(RespawnPoints.owns(player, RespawnPoints.Point.FALADOR))
    }

    @Test
    fun `bought points are one bit each`() {
        val player = newPlayer()
        player.attr[RespawnPoints.OWNED] = 1 shl RespawnPoints.Point.LUMBRIDGE.ordinal
        assertTrue(RespawnPoints.owns(player, RespawnPoints.Point.LUMBRIDGE))
        assertFalse(RespawnPoints.owns(player, RespawnPoints.Point.ARDOUGNE))
        assertEquals(500_000, RespawnPoints.PRICE)
    }

    @Test
    fun `a saved respawn that is not a verified safe point falls back to the home`() {
        val player = newPlayer()
        player.attr[RESPAWN_TILE_ATTR] = Tile(3094, 3469, 0).as30BitInteger
        RespawnPoints.sanitize(player)
        assertNull(player.attr[RESPAWN_TILE_ATTR], "unverified (e.g. dangerous Edgeville) respawns are never kept")
    }

    @Test
    fun `the six owner points are offered in the owner's order`() {
        assertEquals(
            listOf("Edgeville", "Ferox Enclave", "Lumbridge", "Falador", "Camelot", "Ardougne"),
            RespawnPoints.Point.values().map { it.label },
        )
    }
}