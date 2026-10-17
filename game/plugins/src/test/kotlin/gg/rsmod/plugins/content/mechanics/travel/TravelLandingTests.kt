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
import gg.rsmod.plugins.content.skills.agility.isUsableLanding
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertEquals
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
        players.forEach { world.unregister(it) }
        players.clear()
    }

    @Test
    fun `every fixed travel landing is open walkable ground and not an npc's post`() {
        val spawns = pluginNpcSpawns()
        val problems =
            landings().mapNotNull { (name, tile) ->
                val pocket = Landing.pocket(world.collision, tile, limit = 16).size
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
    }

    // ---------------------------------------------------------------------------------------------------------------- helpers

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
    private fun pluginNpcSpawns(): List<Pair<String, Tile>> {
        val spawn = Regex("""spawn_npc\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
        fun field(body: String, key: String) = Regex("""(?<![A-Za-z])$key\s*=\s*([\w.]+)""").find(body)?.groupValues?.get(1)
        return File("src/main/kotlin/gg/rsmod/plugins").walkTopDown().filter { it.isFile && it.name.endsWith(".plugin.kts") }.flatMap { file ->
            spawn.findAll(file.readText()).mapNotNull { m ->
                val body = m.groupValues[1]
                val x = field(body, "x")?.toIntOrNull() ?: return@mapNotNull null
                val z = field(body, "z")?.toIntOrNull() ?: return@mapNotNull null
                if ((field(body, "walkRadius")?.toIntOrNull() ?: 0) > 0) return@mapNotNull null
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
            loader.loadClass(SCRIPT)
                .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                .newInstance(repo, world, mockk<Server>(relaxed = true))
            repo
        }
    }
}
