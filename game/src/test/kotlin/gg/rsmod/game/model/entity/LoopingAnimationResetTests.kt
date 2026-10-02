package gg.rsmod.game.model.entity

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.def.AnimDef
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner priority #1 2026-10-03 ("agility obstakels en noclip issues structureel oplossen"; 2026-10-02 23:58 "im stuck.." after
 * Trollheim rock 3803). Reproduced in the agent's client with the `[walktrace]` telemetry: the server walked the player to
 * 2874,3669 while the client kept drawing him on the landing 2875,3671 - "PROBLEM body held by action animation ... anim=740
 * (walkPrec=0 loopOffset=16 loop=4/99)". The 667 client replays a sequence with a loop offset up to 99 times and, at walking
 * precedence 0, draws the body standing still until it ends; the server's AnimDef counted one pass (2 ticks) and never reset it.
 */
class LoopingAnimationResetTests {
    private val world =
        World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true)).also {
            it.filestore = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
            it.definitions.loadAll(it.filestore)
            it.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            // animate() asks the player update blocks for the ANIMATION mask (data/blocks.yml).
            it.playerUpdateBlocks.load(gg.rsmod.util.ServerProperties().loadYaml(Paths.get("..", "data", "blocks.yml").toFile()).extract("players"))
        }

    private fun anim(id: Int) = world.definitions.get(AnimDef::class.java, id)

    private fun player(at: Tile): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = "loopanim"
        client.username = "loopanim"
        client.tile = at
        client.lastTile = Tile(at)
        return client
    }

    private fun slide(
        from: Tile,
        to: Tile,
        delay: Int,
    ) = ForcedMovement.of(from, to, clientDuration1 = 0, clientDuration2 = delay, directionAngle = 0, lockState = LockState.NONE)

    private fun run(
        player: Client,
        cycles: Int,
    ) {
        repeat(cycles) {
            player.queues.cycle()
            player.lastTile = Tile(player.tile)
            player.blockBuffer.clean()
            world.currentCycle++
        }
    }

    @Test
    fun `the rocks climb 740 loops on the client for about 96 seconds, the human climb 1148 ends by itself`() {
        val rocks = anim(740)
        assertTrue(rocks.loops, "740 loops (loopOffset ${rocks.loopOffset}, maxLoops ${rocks.maxLoops})")
        assertEquals(16, rocks.loopOffset)
        assertEquals(99, rocks.maxLoops)
        assertEquals(0, rocks.walkingPrecedence, "the body waits for 740")
        assertTrue(rocks.holdsBody)
        assertEquals(2, rocks.cycleLength, "one pass is what the server used to count")
        assertTrue(rocks.clientCycleLength in 150..170, "99 loops of 48 client cycles: ${rocks.clientCycleLength} game cycles")
        val human = anim(1148)
        assertFalse(human.loops)
        assertEquals(human.cycleLength, human.clientCycleLength)
        // Opcode 10 sets the walking precedence; it used to be read as the sequence priority.
        assertEquals(1, anim(2586).walkingPrecedence)
    }

    @Test
    fun `a looping climb animation is reset when the forced move it was started for ends`() {
        val player = player(Tile(2878, 3670))
        player.queue {
            player.animate(740)
            player.forceMove(this, slide(player.tile, Tile(2875, 3671), 50), cycleDuration = 2)
        }
        run(player, 4)
        assertEquals(Tile(2875, 3671), player.tile)
        assertEquals(-1, player.lastAnimationId, "740 reset at the end of the slide")
    }

    @Test
    fun `a one-shot climb animation is left to end on the client`() {
        val player = player(Tile(2893, 3675))
        player.queue {
            player.animate(1148)
            player.forceMove(this, slide(player.tile, Tile(2894, 3672), 40), cycleDuration = 2)
        }
        run(player, 4)
        assertEquals(1148, player.lastAnimationId)
    }

    @Test
    fun `a looping animation ends with the locked action that started it, however the task ends`() {
        val finished = player(Tile(3200, 3200))
        finished.lockingQueue {
            finished.animate(740)
            wait(2)
        }
        run(finished, 4)
        assertEquals(LockState.NONE, finished.lock)
        assertEquals(-1, finished.lastAnimationId, "reset when the task finished")

        val terminated = player(Tile(3200, 3200))
        terminated.lockingQueue {
            terminated.animate(740)
            wait(50)
        }
        run(terminated, 2)
        assertEquals(740, terminated.lastAnimationId, "still climbing while locked")
        terminated.interruptQueues()
        assertEquals(-1, terminated.lastAnimationId, "reset when the task was terminated")
        assertEquals(LockState.NONE, terminated.lock)
    }

    @Test
    fun `a walk click resets the action animation on the map as on the minimap`() {
        val handlers = File("src/main/kotlin/gg/rsmod/game/message/handler")
        listOf("ClickMapHandler.kt", "ClickMinimapHandler.kt").forEach { name ->
            val text = File(handlers, name).readText()
            assertTrue(Regex("""fullInterruption\([^)]*animations = true""").containsMatchIn(text), "$name resets animations on a walk")
            assertFalse(text.contains("animations = false"), "$name must not keep a stale animation on a walk")
        }
    }

    @Test
    fun `a client slide of n client cycles occupies ceil(n over 30) game cycles, never 0`() {
        assertEquals(1, Player.slideCycles(0))
        assertEquals(1, Player.slideCycles(30))
        assertEquals(2, Player.slideCycles(31))
        assertEquals(2, Player.slideCycles(50))
        assertEquals(4, Player.slideCycles(94))
        assertEquals(4, Player.slideCycles(120))
    }
}
