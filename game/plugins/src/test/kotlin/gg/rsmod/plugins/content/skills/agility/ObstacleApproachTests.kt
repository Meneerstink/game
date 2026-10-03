package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.queue.QueueTaskSet
import gg.rsmod.plugins.testutil.RealMapWorld
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan 8A (owner priority #1 2026-10-03): an obstacle's approach is normal movement that must really reach the start tile; the
 * crossing is an explicit action that never starts from somewhere else. The old helpers waited `distance + 1` ticks blind.
 * Checked on the real map around the Trollheim chain rocks.
 */
class ObstacleApproachTests {
    private val world get() = RealMapWorld.world

    private fun player(at: Tile): Client {
        world.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        // RealMapWorld's GameContext is a relaxed mock: give the player the real skill count so he has hitpoints.
        io.mockk.every { world.gameContext.skillCount } returns 25
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = "approach"
        client.username = "approach"
        client.tile = at
        // Alive (a refused approach checks for death) and able to be written to (stopMovement resets the client's map flag).
        client.skills.setBaseLevel(gg.rsmod.plugins.api.Skills.CONSTITUTION, 99)
        client.setCurrentLifepoints(99)
        client.gameSystem = io.mockk.mockk(relaxed = true)
        return client
    }

    /** `Pawn.queues` is internal to the game module: cycle it the way QueueHandlerTask and the pre-sync task do. */
    private fun tick(
        player: Client,
        cycles: Int,
    ) {
        val field = Pawn::class.java.getDeclaredField("queues").apply { isAccessible = true }
        val queues = field.get(player) as QueueTaskSet
        repeat(cycles) {
            queues.cycle()
            player.movementQueue.cycle()
            world.currentCycle++
        }
    }

    @Test
    fun `an approach returns only once the player stands on the start tile`() {
        val start = Tile(2883, 3669)
        val target = Tile(2880, 3669)
        assertTrue(Landing.pocket(world.collision, target).size >= Landing.OPEN_GROUND)
        val player = player(start)
        var arrivedAt: Tile? = null
        var error: Throwable? = null
        player.queue {
            try {
                walkToTile(player, target)
                arrivedAt = Tile(player.tile)
            } catch (t: Throwable) {
                error = t
            }
        }
        tick(player, 1)
        assertNull("still walking after one tick", arrivedAt)
        tick(player, 6)
        assertNull("approach threw: ${error?.stackTraceToString()?.take(1500)}", error)
        assertEquals("at ${player.tile}, moving=${player.movementQueue.hasDestination()}, queue=${describe(player)}", target, arrivedAt)
        assertEquals(target, player.tile)
    }

    private fun describe(player: Client): List<String> {
        val field = Pawn::class.java.getDeclaredField("queues").apply { isAccessible = true }
        return (field.get(player) as QueueTaskSet).describe()
    }

    @Test
    fun `an approach to a walled-in tile is refused before any crossing`() {
        // 2877,3671: the one-tile pocket inside the Trollheim rock 3803 (TrollheimRockChainTests).
        val pocket = Tile(2877, 3671)
        assertEquals(1, Landing.pocket(world.collision, pocket).size)
        val player = player(Tile(2883, 3669))
        var failure: String? = null
        var crossed = false
        player.queue {
            try {
                walkToTile(player, pocket)
                crossed = true
            } catch (e: ObstacleApproachFailed) {
                failure = e.reason
            } catch (t: Throwable) {
                failure = "threw ${t.stackTraceToString().take(1500)}"
            }
        }
        tick(player, 12)
        assertTrue("refused: $failure at ${player.tile} queue=${describe(player)}", failure?.startsWith("no route") == true)
        assertTrue("no crossing from the wrong tile", !crossed)
        assertTrue("the player stays on open ground: ${player.tile}", Landing.pocket(world.collision, player.tile).size >= Landing.OPEN_GROUND)
    }

    @Test
    fun `a collision-free crossing is bounded to the obstacle`() {
        val player = player(Tile(2883, 3669))
        var failure: String? = null
        player.queue {
            try {
                walkOverTile(player, Tile(2883, 3669 + MAX_WALK_OVER + 1), Render.CLIMBING)
            } catch (e: ObstacleApproachFailed) {
                failure = e.reason
            }
        }
        tick(player, 2)
        assertTrue("refused: $failure", failure?.contains("no bounded obstacle crossing") == true)
        assertEquals(Tile(2883, 3669), player.tile)
    }
}
