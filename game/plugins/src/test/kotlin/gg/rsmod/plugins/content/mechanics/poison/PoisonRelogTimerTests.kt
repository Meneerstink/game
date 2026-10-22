package gg.rsmod.plugins.content.mechanics.poison

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.game.model.timer.VENOM_TIMER
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.serializer.PlayerLoadResult
import gg.rsmod.game.service.serializer.json.JsonPlayerSerializer
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.game.task.QueueHandlerTask
import gg.rsmod.game.task.WorldRemoveTask
import gg.rsmod.game.task.sequential.SequentialNpcCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerPostCycleTask
import gg.rsmod.game.task.sequential.SequentialSynchronizationTask
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.content.combat.isEnvenomed
import gg.rsmod.plugins.content.combat.isPoisoned
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * N77 (owner 2026-10-16 report, decision 2026-10-17 VRAGEN 28 = A): "poison / venom stays after logging in again, but the clock to the
 * next hit starts again (18 s = 30 ticks), like Void / 2009scape. Teleporting changes nothing (OSRS). Otherwise exactly OSRS."
 *
 * Class: every relog of a poisoned or envenomed player, whatever part of the 30-tick countdown was left when the save was written
 * (1..30), whatever the severity / venom step, and a teleport in the middle of the countdown. Driven the way the game drives it: the
 * real world, the real JsonPlayerSerializer (save + load), the real login hooks ([PluginRepository.executeLogin]) and the real
 * player cycle (timers, hits, HP).
 *
 * Donors: Void Poison.kt:66-69 (login restarts the countdown), 2009scape Poison.kt (timer saved without its ticks).
 */
class PoisonRelogTimerTests {
    private val players = mutableListOf<Client>()

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        players.clear()
    }

    // ------------------------------------------------------------------------------------------------------------------- poison

    @Test
    fun `poison relog - the saved remainder of the countdown is dropped and the next hit comes 30 ticks after login`() {
        withSerializer { serializer ->
            val before = player("n77poison")
            Poison.poisonSeverity(before, 15) // first hit 3 (ticks left 14)
            tick(18) // 12 ticks to the first hit are left
            assertEquals("precondition: 12 ticks left when the save is written", 12, before.timers[POISON_TIMER])
            val after = relog(serializer, before)

            assertTrue("poisoned after the relog", after.isPoisoned())
            assertEquals("severity (ticks left) kept as saved", 14, after.attr[POISON_TICKS_LEFT_ATTR])
            assertEquals("the countdown restarted at 30 (the old build kept the saved 12)", 30, after.timers[POISON_TIMER])
            assertEquals("HP orb shows poison again", 1, after.getVarp(ORB))

            val hp = after.getCurrentLifepoints()
            tick(29)
            assertEquals("no hit during the 29 ticks after login", hp, after.getCurrentLifepoints())
            tick(1)
            assertEquals("the first hit lands on tick 30 after login: damage 3 (severity 15)", hp - 3, after.getCurrentLifepoints())
            assertEquals("severity steps down once", 13, after.attr[POISON_TICKS_LEFT_ATTR])
            assertEquals("and the 30-tick cycle goes on", 30, after.timers[POISON_TIMER])
        }
    }

    @Test
    fun `poison relog - every remainder of the countdown (1 to 30) restarts at 30`() {
        withSerializer { serializer ->
            (1..30).forEach { left ->
                val before = player("n77rem$left")
                Poison.poisonSeverity(before, 22)
                before.timers[POISON_TIMER] = left
                val after = relog(serializer, before)
                assertEquals("remainder $left", 30, after.timers[POISON_TIMER])
                assertEquals("remainder $left keeps the severity", 21, after.attr[POISON_TICKS_LEFT_ATTR])
                world.unregister(after)
                players.remove(after)
            }
        }
    }

    @Test
    fun `poison relog - every severity is kept and a relog right after a relog stays at 30`() {
        withSerializer { serializer ->
            listOf(1, 2, 5, 6, 10, 20, 30, 40).forEach { severity ->
                var current = player("n77sev$severity")
                Poison.poisonSeverity(current, severity)
                current.timers[POISON_TIMER] = 7
                repeat(3) { round ->
                    current = relog(serializer, current)
                    assertEquals("severity $severity relog #${round + 1}", severity - 1, current.attr[POISON_TICKS_LEFT_ATTR])
                    assertEquals("severity $severity relog #${round + 1} timer", 30, current.timers[POISON_TIMER])
                    tick(2) // two ticks of the new session pass; the next save then holds 28 and is restarted again
                }
                world.unregister(current)
                players.remove(current)
            }
        }
    }

    @Test
    fun `poison relog - a poison that was saved at severity zero is gone, it does not start a new countdown`() {
        withSerializer { serializer ->
            val before = player("n77zero")
            before.attr[POISON_TICKS_LEFT_ATTR] = -1
            before.timers[POISON_TIMER] = 5
            val after = relog(serializer, before)
            assertFalse(after.isPoisoned())
            assertFalse("no timer", after.timers.has(POISON_TIMER))
            assertEquals("orb normal", 0, after.getVarp(ORB))
        }
    }

    @Test
    fun `relog - a player who is not poisoned or envenomed gets no timer, orb and hp untouched`() {
        withSerializer { serializer ->
            val before = player("n77clean")
            val after = relog(serializer, before)
            assertFalse(after.timers.has(POISON_TIMER))
            assertFalse(after.timers.has(VENOM_TIMER))
            assertEquals(0, after.getVarp(ORB))
            tick(60)
            assertEquals(99, after.getCurrentLifepoints())
        }
    }

    @Test
    fun `relog - a cured poison stays cured`() {
        withSerializer { serializer ->
            val before = player("n77cured")
            Poison.poisonSeverity(before, 10)
            Poison.cure(before)
            val after = relog(serializer, before)
            assertFalse(after.isPoisoned())
            assertFalse(after.timers.has(POISON_TIMER))
            assertEquals(0, after.getVarp(ORB))
        }
    }

    // -------------------------------------------------------------------------------------------------------------------- venom

    @Test
    fun `venom relog - venom stays, its damage step is kept, the next hit comes 30 ticks after login`() {
        withSerializer { serializer ->
            val before = player("n77venom")
            Venom.envenom(before)
            before.attr[VENOM_TICKS_ELAPSED_ATTR] = 2 // next hit 10
            before.timers[VENOM_TIMER] = 9
            val after = relog(serializer, before)

            assertTrue("envenomed after the relog", after.isEnvenomed())
            assertFalse(after.isPoisoned())
            assertEquals("venom step kept", 2, after.attr[VENOM_TICKS_ELAPSED_ATTR])
            assertEquals("the countdown restarted at 30 (the old build kept the saved 9)", 30, after.timers[VENOM_TIMER])
            assertEquals("HP orb shows venom", 2, after.getVarp(ORB))

            val hp = after.getCurrentLifepoints()
            tick(29)
            assertEquals("no hit during the 29 ticks after login", hp, after.getCurrentLifepoints())
            tick(1)
            assertEquals("first venom hit after login: 10", hp - 10, after.getCurrentLifepoints())
            assertEquals("venom step goes up", 3, after.attr[VENOM_TICKS_ELAPSED_ATTR])
            assertEquals(30, after.timers[VENOM_TIMER])
        }
    }

    @Test
    fun `venom relog - every remainder (1 to 30) and every step restarts at 30 and venom never wears off by a relog`() {
        withSerializer { serializer ->
            (1..30).forEach { left ->
                val step = left % 8
                var current = player("n77v$left")
                Venom.envenom(current)
                current.attr[VENOM_TICKS_ELAPSED_ATTR] = step
                current.timers[VENOM_TIMER] = left
                repeat(2) {
                    current = relog(serializer, current)
                    assertTrue("remainder $left still envenomed", current.isEnvenomed())
                    assertEquals("remainder $left", 30, current.timers[VENOM_TIMER])
                    assertEquals("remainder $left keeps the step", step, current.attr[VENOM_TICKS_ELAPSED_ATTR])
                }
                world.unregister(current)
                players.remove(current)
            }
        }
    }

    @Test
    fun `venom relog - a save that holds both states keeps the venom only (never both at once)`() {
        withSerializer { serializer ->
            val before = player("n77both")
            Venom.envenom(before)
            before.attr[POISON_TICKS_LEFT_ATTR] = 9
            before.timers[POISON_TIMER] = 4
            val after = relog(serializer, before)
            assertTrue(after.isEnvenomed())
            assertFalse("poison removed", after.isPoisoned())
            assertFalse(after.timers.has(POISON_TIMER))
            assertEquals(2, after.getVarp(ORB))
        }
    }

    // ----------------------------------------------------------------------------------------------------------------- teleport

    @Test
    fun `teleport - poison and venom change nothing - same severity, same clock, hits keep landing on schedule`() {
        listOf("poison", "venom").forEach { kind ->
            val client = player("n77tp$kind")
            if (kind == "poison") Poison.poisonSeverity(client, 15) else Venom.envenom(client)
            val timer = if (kind == "poison") POISON_TIMER else VENOM_TIMER
            tick(10)
            assertEquals("$kind: 20 ticks left before the teleport", 20, client.timers[timer])
            val severity = client.attr[POISON_TICKS_LEFT_ATTR]
            val step = client.attr[VENOM_TICKS_ELAPSED_ATTR]

            client.teleport(Tile(3200, 3200), TeleportType.MODERN, exact = true)
            var elapsed = 0
            while (client.tile.x != 3200 && elapsed < 20) {
                tick(1) // the cast and the landing (teleportDelay 5)
                elapsed++
            }
            assertEquals("$kind: the player really arrived", 3200, client.tile.x)
            assertEquals("$kind: the clock went on one tick per tick, untouched by the teleport", 20 - elapsed, client.timers[timer])
            assertEquals("$kind: severity untouched", severity, client.attr[POISON_TICKS_LEFT_ATTR])
            assertEquals("$kind: venom step untouched", step, client.attr[VENOM_TICKS_ELAPSED_ATTR])
            assertEquals("$kind: orb untouched", if (kind == "poison") 1 else 2, client.getVarp(ORB))

            val hp = client.getCurrentLifepoints()
            val expected = if (kind == "poison") 3 else 6
            tick(20 - elapsed)
            assertEquals("$kind: the hit after the teleport lands on schedule", expected, hp - client.getCurrentLifepoints())
            assertEquals("$kind: and the cycle continues", 30, client.timers[timer])
        }
    }

    // -------------------------------------------------------------------------------------------------------------- source guard

    @Test
    fun `source guard - the poison plugin restarts the countdown at login and no relog path keeps the saved remainder`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/poison/poison_plugin.plugin.kts").readText()
        assertTrue("poison_plugin must hook on_login", "on_login" in plugin && "restartCountdownAtLogin" in plugin)
    }

    // -------------------------------------------------------------------------------------------------------------------- helpers

    private fun player(name: String): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.tile = HOME
        client.skills.setBaseLevel(gg.rsmod.plugins.api.Skills.CONSTITUTION, 99)
        client.setCurrentLifepoints(99)
        client.currentXteaKeys = intArrayOf(1, 2, 3, 4)
        client.passwordHash = "x"
        val system = mockk<GameSystem>(relaxed = true)
        client.gameSystem = system
        assertTrue("registered $name", world.register(client))
        players += client
        return client
    }

    /** Logout + login the way the server does: save, remove from the world, load the save, run the login hooks. */
    private fun relog(
        serializer: JsonPlayerSerializer,
        before: Client,
    ): Client {
        val name = before.loginUsername
        assertTrue("saved", serializer.saveClientData(before))
        world.unregister(before)
        players.remove(before)
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        val request =
            gg.rsmod.net.codec.login.LoginRequest(
                mockk(relaxed = true), name, "", 667, intArrayOf(1, 2, 3, 4), false, 0, "", 765, 503, reconnecting = true,
            )
        assertEquals(PlayerLoadResult.LOAD_ACCOUNT, serializer.loadClientData(client, request))
        client.currentXteaKeys = intArrayOf(1, 2, 3, 4)
        client.gameSystem = mockk<GameSystem>(relaxed = true)
        assertTrue("registered $name", world.register(client))
        players += client
        world.plugins.executeLogin(client)
        return client
    }

    private fun withSerializer(block: (JsonPlayerSerializer) -> Unit) {
        val dir: Path = Files.createTempDirectory("n77-saves")
        try {
            val serializer = JsonPlayerSerializer()
            serializer.initSerializer(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to dir.toString())))
            block(serializer)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun tick(n: Int) {
        repeat(n) {
            tasks.forEach { it.execute(world, service) }
            worldCycle.invoke(world)
        }
    }

    companion object {
        /** Poison.POISON_VARP: the HP orb (0 normal, 1 poison, 2 venom). */
        const val ORB = 102

        val HOME = Tile(3158, 3497)

        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.mechanics.poison.Poison_plugin_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/mechanics/poison/poison_plugin.plugin.kts",
                "gg.rsmod.plugins.content.mechanics.poison.Venom_plugin_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/mechanics/poison/venom_plugin.plugin.kts",
            )

        private val service = mockk<GameService>(relaxed = true)

        private val tasks =
            listOf(
                QueueHandlerTask(),
                SequentialPlayerCycleTask(),
                WorldRemoveTask(),
                SequentialNpcCycleTask(),
                SequentialSynchronizationTask(),
                SequentialPlayerPostCycleTask(),
            )

        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            every { ctx.npcStatCount } returns Npc.Stats.DEFAULT_NPC_STAT_COUNT
            every { ctx.home } returns Tile(3222, 3218)
            every { ctx.cycleTime } returns 600
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            world.privileges.load(
                ServerProperties().loadMap(
                    mapOf("privileges" to arrayListOf(linkedMapOf("id" to 0, "name" to "player", "powers" to listOf<String>()))),
                ),
            )
            world.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            world.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            world.definitions.loadAll(world.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(world) as MutableList<Service>
            services += xteas
            World::class.java.declaredMethods.first { it.name.startsWith("loadUpdateBlocks") }.apply { isAccessible = true }
                .invoke(world, Paths.get("..", "..", "data", "blocks.yml").toFile())
            bindScripts(world)
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy {
            World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true }
        }

        private fun bindScripts(world: World) {
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), PoisonRelogTimerTests::class.java.classLoader)
            SCRIPTS.forEach { (script, source) ->
                val compiled = File(classes, script.replace('.', '/') + ".class")
                if (!compiled.exists() || compiled.lastModified() < File(source).lastModified()) {
                    fail("${compiled.path} is missing or older than $source: the scripts compile did not run first")
                }
                loader.loadClass(script)
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
            }
        }
    }
}
