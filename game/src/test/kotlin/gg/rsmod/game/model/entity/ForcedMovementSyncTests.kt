package gg.rsmod.game.model.entity

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.ForcedMovement
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-02 ("after a rock my whole character can noclip everywhere" - Trollheim rocks, shortcuts, dungeons, boulders;
 * ROOT_CAUSES #147). The player update sends every move as a delta from [Pawn.lastTile], the tile the client last saw. A forced
 * move set [Pawn.lastTile] itself, so a second move in the same cycle made the client add the delta from the in-between tile:
 * from then on the client drew the player one or more tiles off the server and walked him through walls everywhere.
 */
class ForcedMovementSyncTests {
    private val world =
        World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true)).also {
            // A Player reads the varp definitions when it is built.
            it.filestore = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
            it.definitions.loadAll(it.filestore)
            it.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        }

    private fun player(at: Tile): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = "forcemove"
        client.username = "forcemove"
        client.tile = at
        client.lastTile = Tile(at)
        return client
    }

    private fun slide(
        from: Tile,
        to: Tile,
        delay: Int,
    ) = ForcedMovement.of(from, to, clientDuration1 = 0, clientDuration2 = delay, directionAngle = 0, lockState = LockState.NONE)

    /** Runs [cycles] game cycles of the player's queue; after each, what the client is told (tile - lastTile) moves the client's tile, as the post-sync task does. */
    private fun run(
        player: Client,
        clientTile: Tile,
        cycles: Int,
    ): Pair<Tile, List<Tile>> {
        var seen = clientTile
        val serverTiles = mutableListOf<Tile>()
        repeat(cycles) {
            player.queues.cycle()
            val last = player.lastTile!!
            seen = seen.transform(player.tile.x - last.x, player.tile.z - last.z)
            player.lastTile = Tile(player.tile)
            serverTiles += Tile(player.tile)
            world.currentCycle++
        }
        return seen to serverTiles
    }

    @Test
    fun `two forced moves in a row leave the client on the server tile (Trollheim rocks 9305)`() {
        val start = Tile(2893, 3673)
        val rock = Tile(2894, 3672)
        val landing = Tile(2898, 3673)
        val player = player(start)
        player.queue {
            player.forceMove(this, slide(player.tile, rock, 40), requestedCycles = 1)
            player.forceMove(this, slide(player.tile, landing, 120), requestedCycles = 4)
        }
        val (clientTile, serverTiles) = run(player, start, 8)
        assertEquals(landing, player.tile)
        assertEquals(player.tile, clientTile, "client and server tile differ after the climb")
        assertEquals(rock, serverTiles.first(), "the climb onto the rock is its own update, not overwritten by the next slide")
    }

    @Test
    fun `a moveTo and a forced move in the same cycle keep the client in sync`() {
        val start = Tile(3200, 3200)
        val player = player(start)
        player.queue {
            player.moveTo(Tile(3201, 3200))
            player.forceMove(this, slide(player.tile, Tile(3204, 3200), 90), requestedCycles = 3)
            player.forceMove(this, slide(player.tile, Tile(3204, 3203), 90), requestedCycles = 3)
        }
        val (clientTile, _) = run(player, start, 10)
        assertEquals(Tile(3204, 3203), player.tile)
        assertEquals(player.tile, clientTile)
    }

    @Test
    fun `only the synchronization tasks set the tile the client last saw`() {
        // `x.lastTile =` anywhere, and a bare `lastTile =` inside the entity classes (a local of that name elsewhere is not it).
        val property = Regex("""\.lastTile\s*=[^=]""")
        val bare = Regex("""^\s*lastTile\s*=[^=]""")
        val writers =
            File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
                val entity = file.parentFile.name == "entity"
                file.readLines().mapIndexedNotNull { i, line ->
                    if (property.containsMatchIn(line) || (entity && bare.containsMatchIn(line))) "${file.name}:${i + 1}" else null
                }.asSequence()
            }.filterNot { it.startsWith("PlayerPostSynchronizationTask.kt") || it.startsWith("NpcPostSynchronizationTask.kt") }.toList()
        assertTrue(writers.isEmpty(), "lastTile written outside the post-sync tasks: $writers")
    }
}
