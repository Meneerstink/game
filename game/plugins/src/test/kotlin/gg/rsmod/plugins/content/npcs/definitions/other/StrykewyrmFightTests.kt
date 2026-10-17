package gg.rsmod.plugins.content.npcs.definitions.other

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.BreachTrace
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_NPC_ATTR
import gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.game.task.QueueHandlerTask
import gg.rsmod.game.task.WorldRemoveTask
import gg.rsmod.game.task.sequential.SequentialNpcCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerPostCycleTask
import gg.rsmod.game.task.sequential.SequentialSynchronizationTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.ref.WeakReference
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * N24 (Keurmeester LC10 2026-10-16: "the strykewyrm is unbeatable"): after "Investigate" the wyrm surfaced, cast once, and the
 * player got "You can't attack this npc." on his own Attack click AND on his auto-retaliate; the wyrm never attacked again and never
 * burrowed. Cause: the awake wyrm keeps the MOUND's server id (9462/9464/9466, cache ops [Investigate]) and only shows the worm
 * (9463/9465/9467, cache ops [-, Attack]) as a transmog; the player attack gate read the mound's definition.
 *
 * Donor: Void `NPC.def(player)` (NPC.kt:98-103, the transform definition while "transform_id" is set) used by Void `Target.attackable`
 * (Target.kt:56-58) for a player attacker; Darkan/Novite turn the npc into the worm id outright (Strykewyrm.kt:270 / :100-119).
 *
 * Every wyrm of the class (ice, desert, jungle) is driven on the real map with the compiled `strykewyrms.plugin.kts` and
 * `combat.plugin.kts` bound into a real [PluginRepository] and the server's own game-tick tasks (GameService order).
 */
class StrykewyrmFightTests {
    private val players = mutableListOf<Client>()
    private val npcs = mutableListOf<Npc>()
    private val written = HashMap<Client, MutableList<Message>>()
    private val traced = mutableListOf<String>()

    @Before
    fun traces() {
        AvTrace.enabled = true
        Trace.capture(traced)
        // The combat-loop exits, leash and target changes of the three mounds (combat.plugin.kts, Pawn.attack) - shown when a check fails.
        BreachTrace.watchedNpcIds = Strykewyrms.WYRMS.map { it.mound }.toSet()
        BreachTrace.sink = { synchronized(traced) { traced += it } }
        // A fixed seed: the attack-style rolls of a fight are the same on every run.
        World::class.java.getDeclaredField("random").apply { isAccessible = true }.set(world, java.util.Random(24L))
    }

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        npcs.forEach { world.remove(it) }
        players.clear()
        npcs.clear()
        Trace.stopCapture()
        AvTrace.enabled = false
        BreachTrace.sink = null
        BreachTrace.watchedNpcIds = emptySet()
    }

    @Test
    fun `the client shows the awake wyrm's Attack option - the cache defs this class rests on`() {
        Strykewyrms.WYRMS.forEach { w ->
            val mound = world.definitions.get(NpcDef::class.java, w.mound)
            val worm = world.definitions.get(NpcDef::class.java, w.wyrm)
            assertFalse("mound ${w.mound} has no Attack (only Investigate): ${mound.options.toList()}", mound.isAttackable())
            assertTrue("worm ${w.wyrm} has Attack: ${worm.options.toList()}", worm.isAttackable())
        }
    }

    @Test
    fun `the owner case - Attack on every awake wyrm is accepted and the player hits it`() {
        Strykewyrms.WYRMS.forEach { w ->
            val (player, npc) = wake(w)
            player.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
            click(player, npc)
            fight(player, 20)
            assertFalse("${w.name}: ${messages(player)}", REFUSAL in messages(player))
            assertTrue("${w.name}: the player's attack reached the wyrm at ${player.tile}, wyrm at ${npc.tile}${log(player, npc)}", swung(player, npc))
            remove(player, npc)
        }
    }

    @Test
    fun `auto-retaliate on the wyrm's attack hits back without a refusal`() {
        Strykewyrms.WYRMS.forEach { w ->
            val (player, npc) = wake(w)
            fight(player, 20)
            assertFalse("${w.name}: ${messages(player)}", REFUSAL in messages(player))
            assertTrue("${w.name}: the auto-retaliate reached the wyrm${log(player, npc)}", swung(player, npc))
            remove(player, npc)
        }
    }

    @Test
    fun `an awake wyrm keeps attacking at its own speed and burrows under its target`() {
        // The player does not fight back (auto-retaliate off, no click): only the wyrm's own combat loop can make it attack again.
        // LC10: it cast once and stopped. Darkan: an attack every attackSpeed (4) ticks, 1 in 21 a burrow (Strykewyrm.kt:69-86).
        Strykewyrms.WYRMS.forEach { w ->
            val (player, npc) = wake(w)
            player.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
            val from = traced.size
            fight(player, 400)
            val anims = traced.subList(from, traced.size).filter { it.contains("anim NPC index=${npc.index} npc=${w.mound} ") }.map { it.substringAfter(" id=").substringBefore(' ').toInt() }
            val attacks = anims.count { it == npc.combatDef.attackAnimation || it == Strykewyrms.ANIM_CAST }
            val burrows = anims.windowed(2).count { it == listOf(Strykewyrms.ANIM_BURROW, Strykewyrms.ANIM_EMERGE) }
            assertTrue("${w.name}: an attack about every 4 ticks - >= 60 attacks in 400 ticks, saw $attacks (anims $anims)${log(player, npc)}", attacks >= 60)
            assertTrue("${w.name}: at least one burrow attack (12796 then 12795), anims $anims", burrows >= 1)
            assertEquals("${w.name}: still awake and fighting after 400 ticks", w.wyrm, npc.getTransmogId())
            assertTrue("${w.name}: never refused: ${messages(player)}", REFUSAL !in messages(player))
            remove(player, npc)
        }
    }

    @Test
    fun `a sleeping mound still refuses Attack with its own message`() {
        val w = Strykewyrms.WYRMS[1]
        val player = player("sleep", Tile(3360, 3152))
        val npc = mound(w, Tile(3363, 3155))
        click(player, npc)
        tick(3)
        assertTrue(messages(player).toString(), "The mound stirs, but nothing emerges. Try stomping on it." in messages(player))
        assertFalse("no swing at a sleeping mound", swung(player, npc))
        remove(player, npc)
    }

    @Test
    fun `area attacks splash on an awake wyrm and still skip a sleeping mound`() {
        // The same base-definition gate sits in the splash check (Burst/Barrage, chinchompas, area specials). The wyrm areas are
        // single-way; the region is made multi for this check only, the way an activity registers one at runtime.
        Strykewyrms.WYRMS.forEach { w ->
            val region = spawnTile(w).regionId
            world.plugins.addMultiCombatRegion(region)
            try {
                val (player, npc) = wake(w)
                assertTrue("${w.name}: a barrage splash reaches the awake wyrm", SpecialAttackSupport.canSplash(player, npc))
                val sleeping = mound(w, spawnTile(w).transform(4, 4))
                assertFalse("${w.name}: a sleeping mound is still skipped", SpecialAttackSupport.canSplash(player, sleeping))
                remove(player, npc, sleeping)
            } finally {
                world.plugins.removeMultiCombatRegion(region)
            }
        }
    }

    @Test
    fun `every wyrm's combat definition carries its Slayer level - the gate every attack path reads`() {
        // N24 round 2 (Keurmeester FAIL N24-1): only "Investigate" checked Slayer 93/77/73; the combat def had slayerReq 1, so once a
        // wyrm was awake anybody could hit it. Void puts slayer_level on the mound def (ice_strykewyrm_cave / desert_strykewyrm /
        // feldip_hills .npcs.toml) and Attack.kt:27/88 refuses a player below it; ours is NpcCombatDef.slayerReq read by
        // Combat.slayerLevelTooLow (attack, splash, brews).
        Strykewyrms.WYRMS.forEach { w ->
            val npc = mound(w, spawnTile(w))
            assertEquals("${w.name}: combatDef.slayerReq", w.slayer, npc.combatDef.slayerReq)
            remove(npc)
        }
    }

    @Test
    fun `a player below the wyrm's Slayer level cannot wound a wyrm a friend woke`() {
        // The Keurmeester's case: A (Slayer 99) investigates, B (one level short) clicks Attack on the awake wyrm.
        Strykewyrms.WYRMS.forEach { w ->
            val (waker, npc) = wake(w)
            val low = player("low${w.mound}", standTile(npc).transform(0, 1))
            low.skills.setBaseLevel(Skills.SLAYER, w.slayer - 1)
            low.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
            click(low, npc)
            fight(waker, 30)
            assertTrue("${w.name}: '${gg.rsmod.plugins.content.combat.Combat.SLAYER_LEVEL_MESSAGE}' for Slayer ${w.slayer - 1}: ${messages(low)}",
                gg.rsmod.plugins.content.combat.Combat.SLAYER_LEVEL_MESSAGE in messages(low))
            assertFalse("${w.name}: no swing from the Slayer ${w.slayer - 1} player${log(low, npc)}", swung(low, npc))
            remove(waker, low, npc)
        }
    }

    @Test
    fun `area attacks skip an awake wyrm for a player below its Slayer level`() {
        Strykewyrms.WYRMS.forEach { w ->
            val region = spawnTile(w).regionId
            world.plugins.addMultiCombatRegion(region)
            try {
                val (waker, npc) = wake(w)
                val low = player("splash${w.mound}", standTile(npc).transform(0, 1))
                low.skills.setBaseLevel(Skills.SLAYER, w.slayer - 1)
                assertFalse("${w.name}: no splash from a Slayer ${w.slayer - 1} player", SpecialAttackSupport.canSplash(low, npc))
                low.skills.setBaseLevel(Skills.SLAYER, w.slayer)
                assertTrue("${w.name}: a Slayer ${w.slayer} player's splash reaches it", SpecialAttackSupport.canSplash(low, npc))
                remove(waker, low, npc)
            } finally {
                world.plugins.removeMultiCombatRegion(region)
            }
        }
    }

    @Test
    fun `a Wrath death blast skips an awake wyrm for a player below its Slayer level`() {
        // N24 round 3 (Keurmeester note N24-2): the Wrath blast (AncientCurses.wrathExplosion, the third area attack beside splash and
        // brews) only read the attack option, so a dying Slayer-1 player still wounded a wyrm a friend woke. Darkan Strykewyrm.kt:114-124
        // zeroes every hit of a player below the level (handlePreHit, so Wrath too); Novite Player.sendDeath skips an npc the
        // controller may not hit. Ours: the one silent area-victim check, SpecialAttackSupport.canSplash (Slayer gate, guards, hooks).
        Strykewyrms.WYRMS.forEach { w ->
            val region = spawnTile(w).regionId
            world.plugins.addMultiCombatRegion(region)
            try {
                val (waker, npc) = wake(w)
                waker.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
                // Wrath reaches every npc whose tile is within 2 of the death tile (AncientCurses.wrathExplosion).
                val blastTile = listOf(Tile(-1, 0), Tile(0, -1), Tile(-1, -1), Tile(-2, 0), Tile(0, -2), Tile(-2, -2))
                    .map { npc.tile.transform(it.x, it.z) }.firstOrNull { !world.collision.isClipped(it) }
                    ?: throw AssertionError("no walkable tile within 2 of ${npc.tile}")
                val low = player("wrath${w.mound}", blastTile)
                low.skills.setBaseLevel(Skills.PRAYER, 99)
                low.skills.setBaseLevel(Skills.SLAYER, w.slayer - 1)
                repeat(3) {
                    gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.wrathExplosion(low)
                    tick(2)
                }
                assertEquals("${w.name}: Wrath damage from a Slayer ${w.slayer - 1} player", 0, npc.damageMap.getDamageFrom(low))
                low.skills.setBaseLevel(Skills.SLAYER, w.slayer)
                repeat(3) {
                    gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.wrathExplosion(low)
                    tick(2)
                }
                assertTrue("${w.name}: a Slayer ${w.slayer} player's Wrath wounds it", npc.damageMap.getDamageFrom(low) > 0)
                remove(waker, low, npc)
            } finally {
                world.plugins.removeMultiCombatRegion(region)
            }
        }
    }

    @Test
    fun `a player at exactly the wyrm's Slayer level wakes it and hits it`() {
        Strykewyrms.WYRMS.forEach { w ->
            val (player, npc) = wake(w, slayer = w.slayer)
            player.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
            click(player, npc)
            fight(player, 20)
            assertFalse("${w.name}: ${messages(player)}", gg.rsmod.plugins.content.combat.Combat.SLAYER_LEVEL_MESSAGE in messages(player))
            assertTrue("${w.name}: a Slayer ${w.slayer} player hits the wyrm${log(player, npc)}", swung(player, npc))
            remove(player, npc)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- helpers

    private fun spawnTile(w: Wyrm) =
        when (w.mound) {
            9462 -> Tile(3436, 5668) // spawns_13656 (Fremennik ice strykewyrm cave)
            9464 -> Tile(3363, 3155) // spawns_13361 (Kharidian desert)
            else -> Tile(2445, 2896) // spawns_9773 (Feldip hills)
        }

    /** A walkable tile two steps west/south/east/north of the mound's body, the first one the map allows (Feldip has trees). */
    private fun standTile(npc: Npc): Tile {
        val size = npc.getSize()
        val candidates = listOf(Tile(-2, 0), Tile(0, -2), Tile(size + 1, 0), Tile(0, size + 1), Tile(-2, -2), Tile(-3, -3))
        return candidates.map { npc.tile.transform(it.x, it.z) }.firstOrNull { !world.collision.isClipped(it) }
            ?: throw AssertionError("no walkable tile beside ${npc.tile}")
    }

    private fun mound(
        w: Wyrm,
        at: Tile,
    ): Npc {
        world.collision.isClipped(at) // loads the region
        val npc = Npc(w.mound, at, world)
        assertTrue(world.spawn(npc))
        npcs += npc
        return npc
    }

    /** The real Investigate click on the real mound spawn, then the 2-tick stomp and the 2-tick emerge. */
    private fun wake(
        w: Wyrm,
        slayer: Int = 99,
    ): Pair<Client, Npc> {
        val at = spawnTile(w)
        val npc = mound(w, at)
        val player = player("w${w.mound}", standTile(npc))
        player.skills.setBaseLevel(Skills.SLAYER, slayer)
        player.attr[INTERACTING_NPC_ATTR] = WeakReference(npc)
        player.attr[INTERACTING_OPT_ATTR] = 1
        assertTrue("Investigate is bound by strykewyrms.plugin.kts", repo.executeNpc(player, npc.id, 1))
        tick(3)
        assertEquals("${w.name} surfaced as the worm", w.wyrm, npc.getTransmogId())
        return player to npc
    }

    /** OpNpc2Handler: the player's Attack click. */
    private fun click(
        player: Client,
        npc: Npc,
    ) {
        player.fullInterruption(movement = true, interactions = true, queue = true)
        player.attack(npc)
    }

    /** [n] ticks of fighting; the player is kept alive so the fight is not decided by his hitpoints. */
    private fun fight(
        player: Client,
        n: Int,
    ) {
        repeat(n) {
            tick(1)
            player.setCurrentLifepoints(99)
        }
    }

    private fun player(
        name: String,
        at: Tile,
    ): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.tile = at
        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.SLAYER, Skills.MAGIC, Skills.RANGED).forEach {
            client.skills.setBaseLevel(it, 99)
        }
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

    private fun remove(vararg es: Any) {
        es.forEach {
            when (it) {
                is Client -> {
                    world.unregister(it)
                    players.remove(it)
                }
                is Npc -> {
                    world.remove(it)
                    npcs.remove(it)
                }
            }
        }
    }

    /** The trace lines of one fight, for a failing check. */
    private fun log(
        player: Client,
        npc: Npc,
    ) = "\n" + traced.filter { it.contains("#${npc.index}") || it.contains("index=${npc.index} ") || it.contains(player.username) }.takeLast(60).joinToString("\n")

    /** The server's own swing trace (combat.plugin.kts / Combat): the player's attack on this wyrm really went out. */
    private fun swung(
        player: Client,
        npc: Npc,
    ) = traced.any { it.contains("swing ${player.username} -> npc ${npc.id}#${npc.index} ") }

    private fun messages(p: Client) = written.getValue(p).filterIsInstance<MessageGameMessage>().map { it.message }

    /** One game tick: the server's own GameService task order, then World.cycle (world timers such as the wyrm idle timer). */
    private fun tick(n: Int) {
        repeat(n) {
            tasks.forEach { it.execute(world, service) }
            worldCycle.invoke(world)
        }
    }

    companion object {
        const val REFUSAL = "You can't attack this npc."

        /** AttackTab.DISABLE_AUTO_RETALIATE_VARP. */
        val AUTO_RETALIATE_OFF_VARP = gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP

        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.combat.Combat_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts",
                "gg.rsmod.plugins.content.npcs.definitions.other.Strykewyrms_plugin" to
                    "src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/other/strykewyrms.plugin.kts",
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
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            world.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            world.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            world.definitions.loadAll(world.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(world) as MutableList<Service>
            services += xteas
            // World.loadUpdateBlocks (internal to the game module) loads data/blocks.yml exactly as the server does.
            World::class.java.declaredMethods.first { it.name.startsWith("loadUpdateBlocks") }.apply { isAccessible = true }
                .invoke(world, Paths.get("..", "..", "data", "blocks.yml").toFile())
            repoOf(world)
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy { World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true } }

        private lateinit var repo: PluginRepository

        /** The compiled scripts bound into the world's real repository, exactly as the server loads them. */
        private fun repoOf(world: World) {
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), StrykewyrmFightTests::class.java.classLoader)
            SCRIPTS.forEach { (script, source) ->
                val compiled = File(classes, script.replace('.', '/') + ".class")
                if (!compiled.exists() || compiled.lastModified() < File(source).lastModified()) {
                    fail("${compiled.path} is missing or older than $source: the scripts compile did not run first (plugins/build.gradle test dependsOn)")
                }
                loader.loadClass(script)
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
            }
            repo = world.plugins
        }
    }
}
