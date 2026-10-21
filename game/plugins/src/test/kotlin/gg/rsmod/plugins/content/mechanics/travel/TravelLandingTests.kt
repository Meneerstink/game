package gg.rsmod.plugins.content.mechanics.travel

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.queue.QueueTaskSet
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.portsarim.Destinations
import gg.rsmod.plugins.content.areas.portsarim.Ports
import gg.rsmod.plugins.content.mechanics.canoes.CanoeUtils
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.skills.agility.isUsableLanding
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
import java.nio.file.Paths

/**
 * N51 (Keurmeester LC19 2026-10-16): Void Knights' Outpost squire 3800 "Travel" -> Port Sarim put the player on 3041,3201,0 - the
 * Gangplank 14304 (blockwalk ground decoration) between the ship and the pier. There he "cannot step in any direction", two walk
 * clicks did not move him and squire 3801 next to him said "I can't reach that!". The return tile was invented (Void has no return
 * trip). Darkan has it: TravelMethods.java:69 `VOID_OUTPOST_FARE(null, "Port Sarim", Tile.of(2659, 2676, 0), Tile.of(3041, 3202, 0))`,
 * :250 `returning ? ship.getOrigon()` - the pier tile; Void's gangplank teles (object-teleports.json 14305 exit -> 3041,3202,0) agree.
 *
 * `player.moveTo(<fixed tile>)` does not go through [Landing] (ROOT_CAUSES 108), so every fixed travel landing must itself be open
 * ground on the real map, and not an npc's own post (ROOT_CAUSES 174). Class: every boat / cart / charter / canoe landing the travel
 * plugins move a player to with moveTo.
 */
class TravelLandingTests {
    private val players = mutableListOf<Client>()
    private val written = HashMap<Client, MutableList<Message>>()

    @After
    fun cleanUp() {
        players.forEach {
            SevenSecondAction.cancel(it)
            queues(it).terminateTasks()
            world.unregister(it)
        }
        players.clear()
        written.clear()
    }

    @Test
    fun `every fixed travel landing is open walkable ground and not an npc's post`() {
        val spawns = pluginNpcSpawns()
        val problems =
            landings().mapNotNull { (name, tile) ->
                val pocket = Landing.pocket(world.collision, tile, limit = 16).size
                println("N51 fixed travel landing: $name $tile pocket=$pocket")
                when {
                    !isUsableLanding(world, tile) -> "$name $tile: walk pocket of $pocket tile(s) - the player cannot walk away"
                    spawns.any { it.second == tile } -> "$name $tile: the standing post of ${spawns.filter { it.second == tile }.joinToString { it.first }}"
                    else -> null
                }
            }
        assertTrue("travel landings that strand the player (moveTo bypasses Landing):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the class sees every travel plugin and the owner's gangplank tile is a trap on the real map`() {
        val names = landings().map { it.first }
        listOf("void_outpost_boat TO_PORT_SARIM", "void_outpost_boat TO_OUTPOST", "waterbirth_boat WATERBIRTH", "travel_cart BRIMHAVEN")
            .forEach { assertTrue("landing $it is checked; found $names", it in names) }
        assertTrue("charter ports checked", names.count { it.startsWith("Ports.") } >= 10)
        assertTrue("canoe stations checked", names.count { it.startsWith("canoe ") } == CANOE_BUTTONS.size)
        // LC19: the gangplank tile between the Port Sarim pier and the outpost ship.
        assertTrue("3041,3201 (Gangplank 14304) is no landing", !isUsableLanding(world, Tile(3041, 3201, 0)))
    }

    /** The owner's case through the real route: the compiled plugin, the Deadman transport gate, the real tick order. */
    @Test
    fun `outpost squire Travel lands on the Port Sarim pier where the player walks and reaches squire 3801`() {
        val p = player("n51", Tile(2659, 2678, 0))
        val def = world.definitions.get(NpcDef::class.java, Npcs.SQUIRE_3800)
        val opt = def.options.indexOfFirst { it?.lowercase() == "travel" } + 1
        assertTrue("squire 3800 has Travel in the cache: ${def.options.toList()}", opt > 0)
        assertTrue("squire 3800 Travel is bound by void_outpost_boat.plugin.kts", repo.executeNpc(p, Npcs.SQUIRE_3800, opt))
        tick(1)
        assertTrue("Deadman transport gate: the 7-second countdown runs first (AGENTS.md 9)", SevenSecondAction.isActive(p))
        SevenSecondAction.complete(p) // what seven_second_action.plugin.kts on_timer(COUNTDOWN_TIMER) does
        tick(20)
        val landed = p.tile
        assertTrue("arrived at Port Sarim; stands at $landed", landed.isWithinRadius(Tile(3041, 3202, 0), 3))
        assertEquals("not left locked after the trip", LockState.NONE, p.lock)
        val pocket = Landing.pocket(world.collision, landed)
        assertTrue("player at $landed can walk away (pocket ${pocket.size})", isUsableLanding(world, landed) && pocket.size > 1)
        val squire = Tile(3041, 3202, 0)
        assertTrue("not on squire 3801's own tile", landed != squire)
        val besideSquire = Direction.NESW.map { squire.step(it) }.filter { it in pocket && world.collision.canStep(it, dirTo(it, squire)) }
        assertTrue("squire 3801 can be reached from $landed (pocket=$pocket)", besideSquire.isNotEmpty())
        assertTrue("return landing is not any npc's spawn tile", pluginNpcSpawns(stationaryOnly = false).none { it.second == landed })
    }

    @Test
    fun `squire boat spawn roster matches Darkan and leaves both arrival tiles unoccupied`() {
        // Darkan real_rs_spawns_unorganized.json: 3790 belongs at the outpost, 3800/3801 are the two boat agents.
        // Void port_sarim.npc-spawns.toml:50 independently has only squire_port_sarim at 3041,3202.
        val expected = mapOf(
            "Npcs.SQUIRE_3790" to Tile(2651, 2664, 0),
            "Npcs.SQUIRE_3800" to Tile(2658, 2676, 0),
            "Npcs.SQUIRE_3801" to Tile(3041, 3202, 0),
        )
        val spawns = pluginNpcSpawns(stationaryOnly = false).filter { it.first.substringBefore(" (") in expected }
        val actual = spawns.groupBy({ it.first.substringBefore(" (") }, { it.second })
        println("N51 runtime-source squire spawns: $actual")
        assertEquals("one squire per donor post, including wanderers", expected.mapValues { listOf(it.value) }, actual)
        landings().filter { it.first.startsWith("void_outpost_boat ") }.forEach { (name, tile) ->
            assertTrue("$name $tile must not overlap any squire spawn", spawns.none { it.second == tile })
        }
    }

    @Test
    fun `both squires support repeated concurrent round trips and ordinary walking into town`() {
        val travellers = listOf(player("n51fresh", Tile(2659, 2678, 0)), player("n51skull", Tile(2659, 2677, 0)))
        PvpSkull.applyTestSkull(travellers.last())
        repeat(3) { round ->
            travellers.forEach { startTrip(it, Npcs.SQUIRE_3800) }
            travellers.forEach { finishCountdown(it) }
            tick(25)
            travellers.forEach { p ->
                assertEquals("return $round for ${p.username}", Tile(3041, 3203, 0), p.tile)
                assertEquals(LockState.NONE, p.lock)
                // Actual collision-aware engine movement, not just a check of the destination constant.
                listOf(Tile(3040, 3203, 0), Tile(3042, 3203, 0)).forEach { destination ->
                    p.walkTo(destination)
                    tick(4)
                    assertEquals("walk along the pier after return $round", destination, p.tile)
                }
                val reachable = reachableTown(p.tile)
                listOf(Tile(3027, 3218, 0), Tile(3030, 3236, 0), Tile(3046, 3236, 0)).forEach {
                    assertTrue("town $it reachable from ${p.tile}", it in reachable)
                }
            }
            travellers.forEach { startTrip(it, Npcs.SQUIRE_3801) }
            travellers.forEach { finishCountdown(it) }
            tick(25)
            travellers.forEach {
                assertEquals("forward $round", Tile(2663, 2676, 1), it.tile)
                assertEquals(LockState.NONE, it.lock)
                assertTrue("accepted outpost deck stays usable", isUsableLanding(world, it.tile))
            }
        }
    }

    @Test
    fun `moving away cancels either squire countdown and cannot fire its old destination later`() {
        listOf(Npcs.SQUIRE_3800 to Tile(2659, 2678, 0), Npcs.SQUIRE_3801 to Tile(3041, 3203, 0)).forEach { (npc, at) ->
            val p = player("n51move$npc", at)
            startTrip(p, npc)
            val moved = at.step(Direction.WEST)
            p.moveTo(moved)
            SevenSecondAction.onZoneCheck(p)
            assertFalse("movement clears countdown", SevenSecondAction.isActive(p))
            finishCountdown(p) // A stale expiry must not retain the trip callback.
            tick(25)
            assertEquals("cancelled trip cannot move the player", moved, p.tile)
            assertEquals(LockState.NONE, p.lock)
        }
    }

    @Test
    fun `logout relog and death clear pending trips in both directions and allow a fresh trip`() {
        listOf(Npcs.SQUIRE_3800 to Tile(2659, 2678, 0), Npcs.SQUIRE_3801 to Tile(3041, 3203, 0)).forEach { (npc, at) ->
            for (death in listOf(false, true)) {
                val p = player("n51life${npc}$death", at)
                startTrip(p, npc)
                if (death) {
                    p.timers.remove(SevenSecondAction.COUNTDOWN_TIMER) // TimerKey.resetOnDeath, before pre-death hooks.
                    repo.executePlayerPreDeath(p)
                } else {
                    repo.executeLogout(p)
                }
                tick(1)
                assertFalse("lifecycle clears countdown", SevenSecondAction.isActive(p))
                finishCountdown(p)
                tick(25)
                assertEquals("no delayed trip after lifecycle", at, p.tile)
                val next = if (death) p else {
                    world.unregister(p)
                    players.remove(p)
                    queues(p).terminateTasks()
                    player(p.username, at) // New Client/attributes, no disk saves or owner account.
                }
                startTrip(next, npc)
                finishCountdown(next)
                tick(25)
                assertEquals(if (npc == Npcs.SQUIRE_3800) Tile(3041, 3203, 0) else Tile(2663, 2676, 1), next.tile)
                assertEquals(LockState.NONE, next.lock)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- helpers

    private fun startTrip(p: Client, npc: Int) {
        println("N51 Travel npc=$npc player=${p.username} from=${p.tile}")
        val def = world.definitions.get(NpcDef::class.java, npc)
        val option = def.options.indexOfFirst { it?.equals("travel", ignoreCase = true) == true } + 1
        assertTrue("cache Travel on $npc", option > 0)
        assertTrue("compiled Travel binding on $npc", repo.executeNpc(p, npc, option))
        tick(1)
        assertTrue("transport waits for its countdown", SevenSecondAction.isActive(p))
    }

    private fun finishCountdown(p: Client) {
        assertTrue("compiled countdown expiry is bound", repo.executeTimer(p, SevenSecondAction.COUNTDOWN_TIMER))
    }

    private fun reachableTown(start: Tile): Set<Tile> {
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val tile = queue.removeFirst()
            Direction.NESW.forEach { dir ->
                val next = tile.step(dir)
                if (kotlin.math.abs(next.x - start.x) <= 40 && kotlin.math.abs(next.z - start.z) <= 40 &&
                    world.collision.canStep(tile, dir) && seen.add(next)
                ) queue.addLast(next)
            }
        }
        return seen
    }

    private fun dirTo(from: Tile, to: Tile): Direction = Direction.NESW.first { from.step(it) == to }

    /** Every fixed landing a travel plugin hands to `player.moveTo`. */
    private fun landings(): List<Pair<String, Tile>> {
        val tileVal = Regex("""(?m)^val (\w+) = Tile\((\d+), (\d+)(?:, (\d+))?\)""")
        val scripts =
            SCRIPT_LANDINGS.flatMap { path ->
                val file = File(path)
                assertTrue("travel plugin ${file.path} exists", file.isFile)
                val text = file.readText()
                assertTrue("${file.name} moves the player with moveTo", text.contains("moveTo("))
                tileVal.findAll(text).map { m ->
                    "${file.name.removeSuffix(".plugin.kts")} ${m.groupValues[1]}" to
                        Tile(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].ifEmpty { "0" }.toInt())
                }.toList()
            }
        val ports = Ports.values().map { "Ports.${it.name}" to it.tile }
        val destinations = Destinations.values().map { it.tile }.distinct().map { "Destinations $it" to it }
        val canoes = CANOE_BUTTONS.map { "canoe $it" to CanoeUtils.getDestinationFromButtonId(it) }
        return (scripts + ports + destinations + canoes).distinctBy { it.first }
    }

    /**
     * Stationary npc posts: `spawn_npc(npc = .., x = .., z = .., height = ..)` with no walk radius (0 = the default) in every plugin
     * source. A wandering npc (walkRadius > 0, e.g. the Brimhaven trader crewmember 4656 on the Darkan charter tile 2760,3238) leaves
     * its spawn tile; a post does not (ROOT_CAUSES 174: squire 3801 stands on 3041,3202 for good).
     */
    private fun pluginNpcSpawns(stationaryOnly: Boolean = true): List<Pair<String, Tile>> {
        val spawn = Regex("""spawn_npc\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
        fun field(body: String, key: String) = Regex("""(?<![A-Za-z])$key\s*=\s*([\w.]+)""").find(body)?.groupValues?.get(1)
        return File("src/main/kotlin/gg/rsmod/plugins").walkTopDown().filter { it.isFile && it.name.endsWith(".plugin.kts") }.flatMap { file ->
            spawn.findAll(file.readText()).mapNotNull { m ->
                val body = m.groupValues[1]
                val x = field(body, "x")?.toIntOrNull() ?: return@mapNotNull null
                val z = field(body, "z")?.toIntOrNull() ?: return@mapNotNull null
                if (stationaryOnly && (field(body, "walkRadius")?.toIntOrNull() ?: 0) > 0) return@mapNotNull null
                "${field(body, "npc")} (${file.name})" to Tile(x, z, field(body, "height")?.toIntOrNull() ?: 0)
            }
        }.toList()
    }

    private fun player(name: String, at: Tile): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.tile = at
        client.skills.setBaseLevel(gg.rsmod.plugins.api.Skills.CONSTITUTION, 99)
        client.setCurrentLifepoints(99)
        val out = mutableListOf<Message>()
        val system = mockk<GameSystem>(relaxed = true)
        every { system.write(any()) } answers { out += firstArg<Message>() }
        client.gameSystem = system
        written[client] = out
        assertTrue("registered $name", world.register(client))
        players += client
        return client
    }

    private fun queues(p: Pawn): QueueTaskSet = Pawn::class.java.getDeclaredField("queues").apply { isAccessible = true }.get(p) as QueueTaskSet

    /** One game tick in the server's order: player queues, world queue, movement. */
    private fun tick(n: Int) {
        repeat(n) {
            players.toList().forEach { queues(it).cycle() }
            worldQueues.cycle()
            players.toList().forEach { it.movementQueue.cycle() }
        }
    }

    companion object {
        private const val ROOT = "src/main/kotlin/gg/rsmod/plugins/content"

        /** Plugins whose landings are top-level `val X = Tile(..)` constants passed to moveTo. */
        val SCRIPT_LANDINGS =
            listOf(
                "$ROOT/areas/voidoutpost/void_outpost_boat.plugin.kts",
                "$ROOT/mechanics/travel/waterbirth_boat.plugin.kts",
                "$ROOT/areas/karamja/travel_cart.plugin.kts",
            )

        /** Canoe destination buttons (CanoeUtils.getDestinationFromButtonId: Lumbridge, Champions' Guild, Barbarian Village, Edgeville, Wilderness). */
        val CANOE_BUTTONS = listOf(47, 48, 3, 6, 49)

        private const val SCRIPT = "gg.rsmod.plugins.content.areas.voidoutpost.Void_outpost_boat_plugin"

        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            world.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            world.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            world.definitions.loadAll(world.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(world) as MutableList<Service>
            services += xteas
            world
        }

        val worldQueues: QueueTaskSet by lazy { World::class.java.getDeclaredField("queues").apply { isAccessible = true }.get(world) as QueueTaskSet }

        /** The compiled script bound into a real repository, exactly as the server loads it. */
        val repo: PluginRepository by lazy {
            val classes = File("scripts/build/classes/kotlin/main")
            val compiled = File(classes, SCRIPT.replace('.', '/') + ".class")
            val source = File("$ROOT/areas/voidoutpost/void_outpost_boat.plugin.kts")
            if (!compiled.exists() || compiled.lastModified() < source.lastModified()) {
                fail("${compiled.path} is missing or older than ${source.path}: the scripts compile did not run first (plugins/build.gradle test dependsOn)")
            }
            val repo = world.plugins
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), TravelLandingTests::class.java.classLoader)
            listOf(
                SCRIPT,
                "gg.rsmod.plugins.content.mechanics.pvp.Seven_second_action_plugin",
            ).forEach { script ->
                loader.loadClass(script)
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(repo, world, mockk<Server>(relaxed = true))
            }
            repo
        }
    }
}
