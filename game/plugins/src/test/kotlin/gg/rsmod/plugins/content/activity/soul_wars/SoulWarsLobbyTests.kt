package gg.rsmod.plugins.content.activity.soul_wars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N477: the Soul Wars lobby list holds only players who are really waiting - a game starts with every listed player and moves them
 * into the field, so a player who teleported off (or logged out) must not be on it.
 */
class SoulWarsLobbyTests {
    @After
    fun clear() {
        SoulWarsMatch.lobbyPlayers.clear()
    }

    private fun player(
        online: Boolean,
        at: Tile,
    ): Player =
        mockk(relaxed = true) {
            every { isOnline } returns online
            every { tile } returns at
        }

    @Test
    fun `the lobby list keeps only online players still in the lobby`() {
        val blueRoom = player(true, SoulWarsHandler.BLUE_ROOM_DOOR)
        val redRoom = player(true, SoulWarsHandler.RED_ROOM_DOOR)
        val teleportedToEdgeville = player(true, Tile(3087, 3490, 0))
        val loggedOut = player(false, SoulWarsHandler.BLUE_ROOM_DOOR)
        val inTheField = player(true, SoulWarsData.BLUE_RESPAWN_AREA.first)
        SoulWarsMatch.lobbyPlayers += listOf(blueRoom, redRoom, teleportedToEdgeville, loggedOut, inTheField)
        SoulWarsHandler.pruneLobby()
        assertEquals(setOf(blueRoom, redRoom), SoulWarsMatch.lobbyPlayers)
    }

    @Test
    fun `the rooms are in the lobby region, the field is not`() {
        assertEquals(SoulWarsHandler.LOBBY_REGION, SoulWarsHandler.BLUE_ROOM_DOOR.regionId)
        assertEquals(SoulWarsHandler.LOBBY_REGION, SoulWarsHandler.RED_ROOM_DOOR.regionId)
        assertEquals(SoulWarsHandler.LOBBY_REGION, Tile(1889, 3161, 0).regionId, "the Balance portal")
        assertTrue(SoulWarsData.BLUE_RESPAWN_AREA.first.regionId != SoulWarsHandler.LOBBY_REGION)
    }

    @Test
    fun `the Balance portal puts the player in a room and the list is pruned before a game can start`() {
        val plugin = File(Paths.get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content", "activity", "soul_wars").toFile(), "soul_wars.plugin.kts").readText()
        assertTrue("SoulWarsHandler.joinByBalancePortal(player)" in plugin)
        val timer = plugin.substringAfter("on_timer(SW_TICK_TIMER) {").substringBefore("\n}")
        assertTrue(timer.indexOf("pruneLobby()") in 0 until timer.indexOf("attemptStartGame"), timer)
    }
}
