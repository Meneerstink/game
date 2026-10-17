package gg.rsmod.plugins.content.areas.wilderness

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_OBJ_ATTR
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.queue.QueueTaskSet
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.ref.WeakReference
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * N28 + N6 (Keurmeester LC12 2026-10-16: "the level-13 obelisk never teleports anybody"): every one of the six Wilderness
 * obelisks, driven through the real click route (the compiled `wilderness_obelisk.plugin.kts` bound into a real
 * [PluginRepository], `executeObject` on the pillar the real map places) on the real map, with real players in
 * `world.players` and the real game-tick order (world queue, player queues, movement).
 *
 * OSRS (wiki "Wilderness Obelisk") + Darkan WildernessObelisk.java:33-72: touching a pillar starts the obelisk; when it fires,
 * every player on the centre 3x3 of the 5x5 platform who is not Tele Blocked is moved with the same offset to a random obelisk -
 * the same one included; arrival text "Ancient magic teleports you somewhere in the Wilderness!"; the activation plays sound
 * 204 (Darkan :39 `soundEffect(204, true)`: the activator plus an area sound, radius 10); "The obelisk is already active." while
 * it runs (Darkan :40-43). A player still next to the pillar (outer ring) stays - and the server now says so in a trace, so a
 * miss can no longer look like a dead obelisk.
 *
 * The script is loaded from `scripts/build/classes` (`:game:plugins:test` depends on `:game:plugins:scripts:compileKotlin`).
 */
class WildernessObeliskPlatformTests {
    /** Platform centres (2009scape WildernessObeliskPlugin enum; Darkan OBELISK_CENTER_TILES = SW pillar, centre - 2). */
    private val centres =
        listOf(
            Tile(3156, 3620),
            Tile(3219, 3656),
            Tile(3035, 3732),
            Tile(3106, 3794),
            Tile(2980, 3866),
            Tile(3307, 3916),
        )

    private val players = mutableListOf<Client>()
    private val written = HashMap<Client, MutableList<Message>>()
    private val traced = mutableListOf<String>()

    @Before
    fun traces() {
        Trace.set(Trace.Cat.INTERACT, true)
        AvTrace.enabled = true
        Trace.capture(traced)
    }

    @After
    fun cleanUp() {
        // Let every running obelisk finish so the next test starts with idle obelisks.
        tick(30)
        players.forEach { world.unregister(it) }
        players.clear()
        Trace.stopCapture()
        Trace.set(Trace.Cat.INTERACT, false)
        AvTrace.enabled = false
    }

    @Test
    fun `every obelisk has its four activate pillars on the real map around a walkable 3x3 centre`() {
        centres.forEach { c ->
            val pillars = pillarsOf(c)
            assertEquals("pillars around $c: ${pillars.map { "${it.id}@${it.tile}" }} near: ${near(c)}", 4, pillars.size)
            platform(c).forEach { t ->
                assertTrue("centre tile $t of the $c obelisk is walkable", !world.collision.isClipped(t))
            }
        }
    }

    @Test
    fun `a player on the centre 3x3 when the obelisk fires is moved with his offset to an obelisk platform`() {
        centres.forEach { c ->
            val activator = player("act${c.x}", ring(c))
            val rider = player("ride${c.x}", c.transform(1, -1))
            activate(activator, c)
            tick(TICKS_TO_ARRIVE)
            val landed = rider.tile
            val destination = centres.firstOrNull { it.transform(1, -1) == landed || Landing.resolve(world.collision, it.transform(1, -1)) == landed }
            assertTrue("rider on $c's platform was moved to the same spot of an obelisk platform; he stands at $landed", destination != null)
            remove(activator, rider)
        }
    }

    @Test
    fun `the arrival message is the OSRS line`() {
        val c = centres[0]
        val activator = player("msgact", ring(c))
        val rider = player("msgride", c)
        activate(activator, c)
        tick(TICKS_TO_ARRIVE)
        assertEquals(
            "rider's chat after the obelisk fired",
            listOf("Ancient magic teleports you somewhere in the Wilderness!"),
            messages(rider).filter { it.startsWith("Ancient magic") },
        )
    }

    @Test
    fun `the owner case - the activator still next to the pillar stays and the server names where he stood`() {
        // LC12: teamtest1 clicked the NW pillar 3154,3622 and walked to 3155,3622 - outside the centre 3x3 when it fired.
        val c = centres[0]
        val activator = player("ringonly", Tile(3155, 3622))
        activate(activator, c)
        tick(TICKS_TO_ARRIVE)
        assertEquals("off the platform: not moved", Tile(3155, 3622), activator.tile)
        val line = traced.firstOrNull { it.contains("PROBLEM") && it.contains("obelisk") }
        assertTrue("a PROBLEM trace names the empty platform and where ringonly stood; traced=$traced", line != null && line.contains("ringonly") && line.contains("3155,3622"))
    }

    @Test
    fun `Tele Block wins at the actual teleport and the other riders still go`() {
        val c = centres[0]
        val activator = player("tbact", ring(c))
        val blocked = player("tbblocked", c.transform(-1, 0))
        val free = player("tbfree", c.transform(0, 1))
        blocked.timers[TELEBLOCK_TIMER] = 500
        activate(activator, c)
        tick(TICKS_TO_ARRIVE)
        assertEquals("Tele Blocked rider stays on the platform", c.transform(-1, 0), blocked.tile)
        assertTrue("told why: ${messages(blocked)}", "A magical force has stopped you from teleporting." in messages(blocked))
        assertTrue("the free rider was moved off ${c.transform(0, 1)} or onto the same spot by a draw of this obelisk", free.lastTeleportCycle != -1)
    }

    @Test
    fun `a touch while the obelisk is still active is refused and starts no second run`() {
        val c = centres[0]
        val activator = player("twice", ring(c))
        val rider = player("twiceride", c)
        activate(activator, c)
        // The four pillars are 14825 (no "Activate") for 9 ticks; the obelisk fires at tick 10. A touch in between
        // must not start a second run (Darkan WildernessObelisk.java:40-43 IS_ACTIVE).
        tick(9)
        activate(activator, c)
        assertTrue("second touch: ${messages(activator)}", "The obelisk is already active." in messages(activator))
        tick(TICKS_TO_ARRIVE)
        assertEquals("one teleport message for one activation", 1, messages(rider).count { it.startsWith("Ancient magic") })
    }

    @Test
    fun `activation plays sound 204 once to the activator and once as an area sound for the others`() {
        val c = centres[0]
        val activator = player("sound", ring(c))
        activate(activator, c)
        val own = written.getValue(activator).filterIsInstance<SynthSoundMessage>().filter { it.sound == WILDERNESS_TELEPORT_SFX }
        assertEquals("sound 204 to the activator at activation: ${written.getValue(activator)}", 1, own.size)
        val area = traced.filter { it.contains("areasound id=$WILDERNESS_TELEPORT_SFX ") }
        assertEquals("one area sound 204 at activation; traced=$traced", 1, area.size)
    }

    @Test
    fun `the destination is any of the six obelisks, the current one included`() {
        val c = centres[0]
        val seen = HashSet<Tile>()
        val activator = player("pool", ring(c))
        val rider = player("poolride", c)
        repeat(60) {
            rider.teleportTo(c)
            activate(activator, c)
            tick(TICKS_TO_ARRIVE)
            centres.firstOrNull { rider.tile == it || Landing.resolve(world.collision, it) == rider.tile }?.let { seen += it }
            var guard = 0
            while ((rider.lock != LockState.NONE || queues(rider).size > 0) && guard++ < 40) tick(1)
        }
        assertEquals("obelisks drawn in 60 activations of the level-13 obelisk: $seen", centres.toSet(), seen)
    }

    // ---------------------------------------------------------------------------------------------------------------- helpers

    /** Pillars at the four corners of the 5x5 (centre +-2), ids with an "Activate" option. */
    private fun pillarsOf(c: Tile): List<GameObject> =
        listOf(Tile(-2, 2), Tile(2, 2), Tile(-2, -2), Tile(2, -2)).mapNotNull { o ->
            val t = c.transform(o.x, o.z)
            world.collision.isClipped(t) // loads the region
            world.chunks.get(t, true)!!.getEntities<GameObject>(t, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).firstOrNull { obj ->
                world.definitions.get(ObjectDef::class.java, obj.id).options.any { it?.lowercase() == "activate" }
            }
        }

    private fun near(c: Tile): List<String> =
        (-3..3).flatMap { x -> (-3..3).map { z -> c.transform(x, z) } }.flatMap { t ->
            world.chunks.get(t, true)!!.getEntities<GameObject>(t, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).map { "${it.id}/${it.type}@${t.x},${t.z}" }
        }

    private fun platform(c: Tile) = (-1..1).flatMap { x -> (-1..1).map { z -> c.transform(x, z) } }

    /** The tile beside the NW pillar a player walks to when he clicks it (LC12: 3155,3622 for the level-13 obelisk). */
    private fun ring(c: Tile) = c.transform(-1, 2)

    /** The real click: the pillar the map holds becomes the interacting object and the bound "Activate" plugin runs. */
    private fun activate(
        p: Client,
        c: Tile,
    ) {
        val pillar = pillarsOf(c).first { it.tile == c.transform(-2, 2) }
        val def = world.definitions.get(ObjectDef::class.java, pillar.id)
        val opt = def.options.indexOfFirst { it?.lowercase() == "activate" } + 1
        p.attr[INTERACTING_OBJ_ATTR] = WeakReference(pillar)
        assertTrue("obelisk ${pillar.id} 'Activate' is bound by wilderness_obelisk.plugin.kts", repo.executeObject(p, pillar.id, opt))
    }

    private fun player(
        name: String,
        at: Tile,
    ): Client {
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

    private fun remove(vararg ps: Client) {
        tick(30)
        ps.forEach {
            world.unregister(it)
            players.remove(it)
        }
    }

    private fun messages(p: Client) = written.getValue(p).filterIsInstance<MessageGameMessage>().map { it.message }

    private fun queues(p: Pawn): QueueTaskSet = Pawn::class.java.getDeclaredField("queues").apply { isAccessible = true }.get(p) as QueueTaskSet

    /**
     * One game tick in the server's order (QueueHandlerTask: player queues, then the world queue; movement; then World.cycle,
     * which turns the activated pillars 14825 back after their 9 ticks).
     */
    private fun tick(n: Int) {
        repeat(n) {
            players.toList().forEach { queues(it).cycle() }
            worldQueues.cycle()
            players.toList().forEach { it.movementQueue.cycle() }
            worldCycle.invoke(world)
        }
    }

    companion object {
        /** Sfx.WILDERNESS_TELEPORT (api/cfg/Sfx.kt) = Darkan :39 / 2009scape Sounds.WILDERNESS_TELEPORT_204. */
        const val WILDERNESS_TELEPORT_SFX = 204

        /** Fire (10) + TeleportType.WILDERNESS_OBELISK delay (5) + slack. */
        const val TICKS_TO_ARRIVE = 18

        private const val SCRIPT = "gg.rsmod.plugins.content.areas.wilderness.Wilderness_obelisk_plugin"

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

        val worldCycle: java.lang.reflect.Method by lazy { World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true } }

        val worldQueues: QueueTaskSet by lazy { World::class.java.getDeclaredField("queues").apply { isAccessible = true }.get(world) as QueueTaskSet }

        /** The compiled script bound into a real repository, exactly as the server loads it. */
        val repo: PluginRepository by lazy {
            val classes = File("scripts/build/classes/kotlin/main")
            val compiled = File(classes, SCRIPT.replace('.', '/') + ".class")
            val source = File("src/main/kotlin/gg/rsmod/plugins/content/areas/wilderness/wilderness_obelisk.plugin.kts")
            if (!compiled.exists() || compiled.lastModified() < source.lastModified()) {
                fail("${compiled.path} is missing or older than ${source.path}: the scripts compile did not run first (plugins/build.gradle test dependsOn)")
            }
            val repo = world.plugins
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), WildernessObeliskPlatformTests::class.java.classLoader)
            loader.loadClass(SCRIPT)
                .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                .newInstance(repo, world, mockk<Server>(relaxed = true))
            repo
        }
    }
}
