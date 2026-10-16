package gg.rsmod.game.model.entity

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.LAST_LOGOUT_DATE
import gg.rsmod.game.service.login.LoginService
import gg.rsmod.game.service.serializer.PlayerLoadResult
import gg.rsmod.game.service.serializer.PlayerSerializerService
import gg.rsmod.net.codec.login.LoginRequest
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.netty.channel.embedded.EmbeddedChannel
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N56 (VPS 2026-10-16, server.log 16:39): logout -> on_logout LootKeyChest -> LootKeyInterfaceImportTool ->
 * OsrsItemImportTool.<clinit> -> OsrsPetRoster.load threw on a laptop path. The JVM wraps that in an
 * ExceptionInInitializerError the first time and a NoClassDefFoundError on every later touch of the class. Both are
 * Errors, and every lifecycle net (PluginRepository.executeLogout/Login/PlayerDeathStart/PreDeath, the death-action
 * runDeathHook nets) caught only Exception: the Error escaped Player.handleLogout before world.unregister, the logout save
 * was skipped and the player stayed in the world ("already online") until a restart.
 *
 * The class: every lifecycle hook loop and every mandatory step after it (unregister, logout save, reboot loop for the
 * other players). Donors: 2009scape DisconnectionQueue.kt:70-71 (finishClear under a Throwable net, then removePlayer and
 * the save) and :43-45 (removal in a finally); Void AccountManager.kt:120 (removal queued before the despawn handlers).
 *
 * Runs on a real [World] with the real 667 cache definitions and real [Client]s on an embedded channel; the failing hook
 * produces the real ExceptionInInitializerError / NoClassDefFoundError chain from a class whose static initialiser
 * throws, exactly as the importer object did on the VPS.
 */
class LogoutLifecycleIsolationTests {
    // Each object is its own "importer": the first touch throws ExceptionInInitializerError, every later touch
    // NoClassDefFoundError. One object per test keeps the chain fresh for each test.
    private object PetRosterA { val roster: String = missingLaptopPath() }

    private object PetRosterB { val roster: String = missingLaptopPath() }

    private object PetRosterC { val roster: String = missingLaptopPath() }

    private object PetRosterD { val roster: String = missingLaptopPath() }

    private class RecordingSerializer : PlayerSerializerService() {
        val saved = mutableListOf<String>()

        override fun initSerializer(server: Server, world: World, serviceProperties: ServerProperties) {}

        override fun loadClientData(client: Client, request: LoginRequest): PlayerLoadResult = PlayerLoadResult.LOAD_ACCOUNT

        override fun saveClientData(client: Client): Boolean {
            saved += client.loginUsername
            return true
        }
    }

    private val world =
        World(mockk<GameContext>(relaxed = true) { every { playerLimit } returns 2048 }, mockk<DevContext>(relaxed = true)).also {
            it.filestore = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
            it.definitions.loadAll(it.filestore)
        }

    private val serializer = RecordingSerializer().also { world.services += it }
    private val login = LoginService().also { world.services += it }

    private fun online(name: String): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        // Edgeville bank, the Deadman hub where the Loot Chest (Skully) sits; the position plays no part in the bug.
        client.tile = Tile(3094, 3492, 0)
        client.lastTile = Tile(client.tile)
        client.gameSystem = mockk(relaxed = true)
        assertTrue(world.register(client), "register $name")
        assertTrue(login.sessions.tryClaim(name), "claim $name")
        return client
    }

    @Test
    fun `an Error in a logout hook cannot leave the player online - the owner's VPS case`() {
        val ran = mutableListOf<String>()
        world.plugins.bindLogout { ran += "friends-status" }
        world.plugins.bindLogout { ran += "loot-chest"; PetRosterA.roster }
        world.plugins.bindLogout { ran += "familiar" }

        val first = online("ghost1")
        first.handleLogout()

        assertEquals(listOf("friends-status", "loot-chest", "familiar"), ran, "every logout hook still runs, in order")
        assertFalse(world.players.contains(first), "unregistered after an ExceptionInInitializerError")
        assertNull(world.getPlayerForName("ghost1"), "the account must not stay 'already online'")
        assertEquals(listOf("ghost1"), serializer.saved, "the logout save still runs")
        assertNotNull(first.attr[LAST_LOGOUT_DATE])
        assertFalse(login.sessions.isActive("ghost1"), "the session claim is released after the save")

        // Every later logout touches the broken class again -> NoClassDefFoundError; it must behave the same.
        ran.clear()
        val second = online("ghost2")
        second.handleLogout()
        assertEquals(listOf("friends-status", "loot-chest", "familiar"), ran)
        assertFalse(world.players.contains(second), "unregistered after a NoClassDefFoundError")
        assertNull(world.getPlayerForName("ghost2"))
        assertEquals(listOf("ghost1", "ghost2"), serializer.saved)
        assertFalse(login.sessions.isActive("ghost2"))
        assertEquals(0, world.players.count(), "nobody is left in the world")
    }

    @Test
    fun `logout hooks still see the player registered - unregister comes after them`() {
        var registeredDuringHook = false
        world.plugins.bindLogout { registeredDuringHook = world.players.contains(ctx as Player) }
        val client = online("normal")
        client.handleLogout()
        assertTrue(registeredDuringHook, "unchanged order: hooks first, then world.unregister")
        assertFalse(world.players.contains(client))
        assertEquals(listOf("normal"), serializer.saved)
    }

    @Test
    fun `a reboot logs every player out even when one logout fails`() {
        world.plugins.bindLogout { if ((ctx as Client).loginUsername == "reboot1") PetRosterB.roster }
        val a = online("reboot1")
        val b = online("reboot2")
        val c = online("reboot3")

        // reboot2's channel is already gone: closing it throws after his logout. The reboot loop must still reach reboot3.
        every { b.gameSystem.close() } throws IllegalStateException("channel already closed")

        world.rebootTimer = 1
        world.cycle()

        listOf(a, b, c).forEach { p ->
            assertFalse(world.players.contains(p), "${p.loginUsername} logged out by the reboot")
            assertFalse(login.sessions.isActive(p.loginUsername), "${p.loginUsername} session released")
        }
        assertEquals(listOf("reboot1", "reboot2", "reboot3"), serializer.saved, "every player got the reboot logout save")
        verify { a.gameSystem.close() }
        verify { c.gameSystem.close() }
    }

    @Test
    fun `an Error in a login hook does not skip the remaining login hooks`() {
        val ran = mutableListOf<String>()
        world.plugins.bindLogin { ran += "a"; PetRosterC.roster }
        world.plugins.bindLogin { ran += "b"; TODO("hook not finished") }
        world.plugins.bindLogin { ran += "c" }
        val client = online("loginhooks")
        world.plugins.executeLogin(client)
        assertEquals(listOf("a", "b", "c"), ran)
        // Second login touches the broken class again (NoClassDefFoundError).
        ran.clear()
        world.plugins.executeLogin(client)
        assertEquals(listOf("a", "b", "c"), ran)
    }

    @Test
    fun `an Error in a death hook does not skip the remaining death hooks`() {
        val ran = mutableListOf<String>()
        world.plugins.bindPlayerDeathStart { ran += "start-1"; PetRosterD.roster }
        world.plugins.bindPlayerDeathStart { ran += "start-2" }
        world.plugins.bindPlayerPreDeath { ran += "pre-1"; PetRosterD.roster }
        world.plugins.bindPlayerPreDeath { ran += "pre-2" }
        world.plugins.bindPlayerDeath { ran += "death-1"; TODO("hook not finished") }
        world.plugins.bindPlayerDeath { ran += "death-2" }
        val client = online("deathhooks")
        world.plugins.executePlayerDeathStart(client)
        world.plugins.executePlayerPreDeath(client)
        world.plugins.executePlayerDeath(client)
        assertEquals(listOf("start-1", "start-2", "pre-1", "pre-2", "death-1", "death-2"), ran)
    }

    @Test
    fun `a fatal JVM error still propagates out of the lifecycle nets`() {
        world.plugins.bindLogout { throw OutOfMemoryError("test: fatal") }
        world.plugins.bindLogin { throw OutOfMemoryError("test: fatal") }
        val client = online("fatal")
        assertFailsWith<OutOfMemoryError> { world.plugins.executeLogout(client) }
        assertFailsWith<OutOfMemoryError> { world.plugins.executeLogin(client) }
    }

    @Test
    fun `every lifecycle net catches Throwable, not only Exception (source guard)`() {
        val repo = File("src/main/kotlin/gg/rsmod/game/plugin/PluginRepository.kt").readText()
        listOf("executeLogin", "executeLogout", "executePlayerDeathStart", "executePlayerPreDeath", "executePlayerDeath").forEach { fn ->
            val body = functionBody(repo, fn)
            assertTrue(body.contains("catch (e: Throwable)"), "$fn must catch Throwable per hook")
            assertTrue(body.contains("rethrowIfFatal()"), "$fn must let fatal JVM errors through")
            assertFalse(body.contains("catch (e: Exception)"), "$fn still catches only Exception")
        }
        listOf(
            "src/main/kotlin/gg/rsmod/game/action/PlayerDeathAction.kt",
            "src/main/kotlin/gg/rsmod/game/action/NpcDeathAction.kt",
        ).forEach { path ->
            val body = functionBody(File(path).readText(), "runDeathHook")
            assertTrue(body.contains("catch (e: Throwable)"), "$path runDeathHook must catch Throwable")
            assertTrue(body.contains("rethrowIfFatal()"), "$path runDeathHook must let fatal JVM errors through")
            assertFalse(body.contains("catch (e: Exception)"), "$path runDeathHook still catches only Exception")
        }
        val player = functionBody(File("src/main/kotlin/gg/rsmod/game/model/entity/Player.kt").readText(), "handleLogout")
        assertTrue(Regex("finally \\{\\s*world\\.unregister\\(this\\)").containsMatchIn(player), "Player.handleLogout must unregister in a finally")
    }

    /** The text of `fun <name>(` up to the next top-level member at the same indent. */
    private fun functionBody(source: String, name: String): String {
        val start = Regex("fun $name\\(").find(source)?.range?.first ?: error("fun $name not found")
        val indent = source.lastIndexOf('\n', start).let { source.substring(it + 1, start) }.takeWhile { it == ' ' }
        val end = Regex("\n$indent}\n").find(source, start)?.range?.last ?: source.length
        return source.substring(start, end)
    }

    companion object {
        private fun missingLaptopPath(): String =
            throw java.io.FileNotFoundException("C:\\RSPS\\game\\game\\data\\cfg\\pets\\osrs_pets.tsv (No such file or directory)")
    }
}
