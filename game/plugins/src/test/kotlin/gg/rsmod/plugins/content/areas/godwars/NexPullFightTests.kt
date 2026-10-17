package gg.rsmod.plugins.content.areas.godwars

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.BreachTrace
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
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
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.areas.godwars.nex.NexCombatScript
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.items.osrs.GraniteMaul
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
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * N25 (Keurmeester LC10 2026-10-16): after Nex's smoke-phase pull ("You've been injured and you cannot use protective prayers!" +
 * frozen) Nex and the player both stopped attacking for minutes; a new Attack click resumed the fight until the next pull.
 *
 * The class: Nex's own combat must never stop while a player stands in her arena - not after a pull, not after any other attack of
 * her five-phase rotation, not when the player is passive (auto-retaliate off, no clicks) - and a pulled player keeps his own fight.
 * Donors: Darkan zaros/attack/Drag.java:31-60 (player.lock(3), `nex.setCombatTarget(player)` - target only - and the attack speed
 * returned to Nex's own combat cycle) + Darkan NPCCombat.java (per-tick process), Novite NexCombat.sendPullAttack (the player's
 * combat action is kept, only delayed).
 *
 * The real map, the compiled `combat.plugin.kts`, `nex_arena.plugin.kts` and `nex.plugin.kts` in a real [PluginRepository] and the
 * server's own game-tick tasks (GameService order) - the StrykewyrmFightTests harness (N24).
 */
class NexPullFightTests {
    private val players = mutableListOf<Client>()
    private val written = HashMap<Client, MutableList<Message>>()
    private val traced = mutableListOf<String>()

    @Before
    fun traces() {
        AvTrace.enabled = true
        Trace.capture(traced)
        BreachTrace.watchedNpcIds = setOf(Npcs.NEX)
        BreachTrace.sink = { synchronized(traced) { traced += it } }
        World::class.java.getDeclaredField("random").apply { isAccessible = true }.set(world, java.util.Random(25L))
        NexCombatScript.reset()
        removeLeftoverNexObjects()
    }

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        players.clear()
        NexEncounter.end()
        NexCombatScript.reset()
        // Nex's temporary objects (ice prison stalagmites: 5 + 12 ticks) must be gone before the next test walks the arena.
        tick(25)
        removeLeftoverNexObjects()
        Trace.stopCapture()
        AvTrace.enabled = false
        BreachTrace.sink = null
        BreachTrace.watchedNpcIds = emptySet()
    }

    @Test
    fun `Nex keeps attacking a passive player through every phase of her rotation`() {
        // The player never fights back: only Nex's own combat loop can make her attack again (N84 / class 191 loop pattern).
        val player = enter("passive", Tile(2918, 5202))
        player.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
        val nex = awaitFight(player)
        for (phase in NexEncounter.Phase.values()) {
            assertEquals("phase", phase, NexEncounter.phase)
            val ticks = if (phase == NexEncounter.Phase.SMOKE) 400 else 150
            val from = messages(player).size
            val attacks = fight(player, nex, ticks)
            val gaps = attacks.zipWithNext { a, b -> b - a }
            val maxGap = (listOf(attacks.firstOrNull() ?: ticks) + gaps).maxOrNull() ?: ticks
            assertTrue(
                "$phase: Nex attacks about every ${nex.combatDef.attackSpeed} ticks - >= ${ticks / 8} attacks in $ticks ticks, saw ${attacks.size} " +
                    "(ticks $attacks) target=${nex.getCombatTarget()}${log(player, nex)}",
                attacks.size >= ticks / 8,
            )
            assertTrue("$phase: never a gap of more than $MAX_GAP ticks between two Nex attacks, longest $maxGap (ticks $attacks)${log(player, nex)}", maxGap <= MAX_GAP)
            if (phase == NexEncounter.Phase.SMOKE) {
                // The LC10 stall: every pull. The seeded smoke rotation pulls the (only, so farthest) player more than once.
                val pulls = messages(player).drop(from).count { it == PULL_MESSAGE }
                assertTrue("smoke: the rotation pulled the player at least twice in $ticks ticks, saw $pulls: ${messages(player).drop(from)}", pulls >= 2)
            }
            if (phase != NexEncounter.Phase.ZAROS) nextPhase()
        }
    }

    @Test
    fun `a pulled player keeps his own fight and Nex keeps hers - no new Attack click`() {
        // The owner's case: the player attacks Nex, gets pulled, and both must fight on by themselves (LC10: 0 hits for 3 minutes).
        // Melee beside her body (LC10: teamtest1 with a melee weapon next to Nex); the pull moves him back onto her ring.
        val player = enter("puller", Tile(2923, 5203))
        val nex = awaitFight(player)
        player.fullInterruption(movement = true, interactions = true, queue = true)
        player.attack(nex)
        fight(player, nex, 20)
        assertTrue("the player's Attack click reached Nex: at ${player.tile} frozen=${player.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)} lock=${player.lock} " +
            "ring clipped=${(5201..5205).map { z -> "2923,$z=" + world.collision.isClipped(Tile(2923, z)) }} msgs=${messages(player)}\n" +
            traced.filter { it.contains(player.username) && Regex("npcpath|route|walk|moveTo|swing|single|refused|combat").containsMatchIn(it) }.take(40).joinToString("\n"), swung(player, nex, 0))
        repeat(3) { round ->
            val from = messages(player).size
            pull(nex)
            val pulledAt = traced.size
            val attacks = fight(player, nex, 30)
            assertTrue("pull ${round + 1} ran: ${messages(player).drop(from)}", PULL_MESSAGE in messages(player).drop(from))
            assertTrue(
                "pull ${round + 1}: Nex attacks again within ${nex.combatDef.attackSpeed + 3} ticks of the pull, attack ticks $attacks${log(player, nex)}",
                attacks.isNotEmpty() && attacks.first() <= nex.combatDef.attackSpeed + 3,
            )
            assertTrue("pull ${round + 1}: Nex keeps attacking (>= 4 attacks in 30 ticks), saw $attacks${log(player, nex)}", attacks.size >= 4)
            assertTrue("pull ${round + 1}: the player's combat target is still Nex after the stun, was ${player.getCombatTarget()}", player.getCombatTarget() === nex)
            assertTrue("pull ${round + 1}: the player swings at Nex again without a new click${log(player, nex)}", swung(player, nex, pulledAt))
            assertEquals("pull ${round + 1}: no lock left on the player", LockState.NONE, player.lock)
        }
    }

    @Test
    fun `the pull lands the player beside Nex's body, never inside it`() {
        val player = enter("landing", Tile(2918, 5202))
        player.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
        val nex = awaitFight(player)
        repeat(12) {
            player.moveTo(Tile(2918, 5202))
            tick(1)
            val from = messages(player).size
            pull(nex)
            tick(4)
            if (PULL_MESSAGE !in messages(player).drop(from)) return@repeat
            val t = player.tile
            val size = nex.getSize()
            val inside = t.x in nex.tile.x until nex.tile.x + size && t.z in nex.tile.z until nex.tile.z + size
            val beside = t.x in nex.tile.x - 1..nex.tile.x + size && t.z in nex.tile.z - 1..nex.tile.z + size
            assertTrue("pulled to $t: not inside Nex's ${size}x$size body at ${nex.tile}", !inside)
            assertTrue("pulled to $t: next to Nex's body at ${nex.tile}", beside)
            player.timers.remove(gg.rsmod.game.model.timer.FROZEN_TIMER)
            player.timers.remove(gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER)
            tick(1)
        }
    }

    @Test
    fun `a pull on a player who left the arena is dropped and leaves no lock`() {
        // 7439c545d safety: the pull is owned by the victim and aborts when he died, logged out or left the arena first.
        val player = enter("escape", Tile(2918, 5202))
        player.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
        val nex = awaitFight(player)
        val from = messages(player).size
        pull(nex)
        val outside = Tile(2900, 5203)
        player.moveTo(outside)
        tick(5)
        assertEquals("not pulled back into the arena", outside, player.tile)
        assertTrue("no pull message: ${messages(player).drop(from)}", PULL_MESSAGE !in messages(player).drop(from))
        assertEquals("no lock left", LockState.NONE, player.lock)
    }

    /*
     * N25 round 2 (AUDIT 10-17): "There is... NO ESCAPE!" set the `busy` flag inside Nex's own combat queue and cleared it only at
     * its last line. Anything that kills that queue mid-rush (Pawn.attack -> interruptQueues: npc retaliation Combat.kt:275, the
     * phase-change re-engage NexEncounter.engageRandomPlayer) left busy=true for ever, and the per-tick loop waits on it - Nex stood
     * still, even in the NEXT fight. Darkan NoEscape.java:44-79 runs the rush in a world task (WorldTasks.scheduleLooping) and
     * returns her attack speed, so her combat cycle cannot strand it. The class: every interruption of Nex's combat queue during the
     * rush - and a fight that ends during it - must leave her attacking again within a few ticks, back on her centre tile.
     */

    @Test
    fun `No Escape interrupted by a retaliation switch - Nex attacks on`() {
        // The audit's case: two players (multi, like live multi.yml 11601); Nex's target dies mid-rush and the other player hits her.
        val a = enter("rushA", Tile(2923, 5203))
        val b = enter("rushB", Tile(2921, 5199))
        val nex = awaitFight(a)
        a.attack(nex)
        b.attack(nex)
        awaitRush(nex, listOf(a, b))
        tick(1)
        val target = nex.getCombatTarget() as Client
        val other = if (target === a) b else a
        target.setCurrentLifepoints(0)
        gg.rsmod.plugins.content.combat.Combat.postDamage(other, nex)
        assertRushRecovered(nex, listOf(other), "retaliation switch")
    }

    @Test
    fun `No Escape interrupted by a re-engage - Nex attacks on`() {
        // Pawn.attack on Nex from outside her loop (a phase change's NexEncounter.engageRandomPlayer) interrupts her queues.
        val a = enter("rushC", Tile(2923, 5203))
        a.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
        val nex = awaitFight(a)
        awaitRush(nex, listOf(a))
        tick(1)
        NexEncounter.engageRandomPlayer(nex)
        assertRushRecovered(nex, listOf(a), "re-engage")
    }

    @Test
    fun `a fight that ends during No Escape leaves nothing behind for the next fight`() {
        val a = enter("rushD", Tile(2923, 5203))
        a.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
        val nex = awaitFight(a)
        awaitRush(nex, listOf(a))
        tick(1)
        world.plugins.executeLogout(a)
        world.unregister(a)
        players.remove(a)
        tick(10)
        assertTrue("the fight ended with the last player: fightActive=${NexEncounter.fightActive}", !NexEncounter.fightActive)
        val c = enter("rushE", Tile(2918, 5202))
        c.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
        val nex2 = awaitFight(c)
        val attacks = fight(c, nex2, 60)
        assertTrue("new fight: not busy (busy=${busy()})", !busy())
        assertTrue("new fight: Nex attacks the new player >= 8 times in 60 ticks, saw $attacks${log(c, nex2)}", attacks.size >= 8)
    }

    // ---------------------------------------------------------------------------------------------------------------- helpers

    /**
     * Test isolation only (side finding N25 round 2, own BUGLIJST item): World.spawnTemporaryObject spawns a COPY of the object and
     * on expiry removes the original, which Chunk.removeEntity matches by identity - the copy and its collision stay. Nex's
     * "Contain this!" ring and shadow traps then block tiles around her in every later test (run 1 of round 2: column 2923,5201-5205
     * clipped, the next test's player could not reach her). Remove what is left in the arena so each test starts on a clean map.
     */
    private fun removeLeftoverNexObjects() {
        val ids = setOf(gg.rsmod.plugins.api.cfg.Objs.STALAGMITE_57263, gg.rsmod.plugins.api.cfg.Objs.SHADOW_57261)
        for (x in NexEncounter.ARENA_X) {
            for (z in NexEncounter.ARENA_Z) {
                val t = Tile(x, z, 0)
                val chunk = world.chunks.get(t, createIfNeeded = false) ?: continue
                chunk.getEntities<gg.rsmod.game.model.entity.GameObject>(t, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                    .filter { it.id in ids }
                    .forEach { world.remove(it) }
            }
        }
    }

    /** Nex's No Escape flag (NexCombatScript.busy): her loop waits while it is set. */
    private fun busy(): Boolean = NexCombatScript::class.java.getDeclaredField("busy").apply { isAccessible = true }.getBoolean(NexCombatScript)

    /** Ticks the fight (smoke phase, melee range: No Escape is 1 roll in 11) until Nex starts her No Escape rush. */
    private fun awaitRush(
        nex: Npc,
        alive: List<Client>,
    ) {
        repeat(3000) {
            tick(1)
            alive.forEach { keepAlive(it, nex) }
            if (busy()) return
        }
        fail("No Escape never rolled in 3000 ticks: phase=${NexEncounter.phase} target=${nex.getCombatTarget()}${log(alive.first(), nex)}")
    }

    /** After an interrupt mid-rush: the rush finishes (busy cleared, Nex back on her centre tile) and her own combat goes on. */
    private fun assertRushRecovered(
        nex: Npc,
        alive: List<Client>,
        what: String,
    ) {
        val speed = nex.combatDef.attackSpeed
        val attacks = mutableListOf<Int>()
        var last = nex.attr[GraniteMaul.LAST_ATTACK_CYCLE]
        var clearedAt = -1
        var clearedTile: Tile? = null
        for (t in 1..60) {
            tick(1)
            alive.forEach { keepAlive(it, nex) }
            if (clearedAt < 0 && !busy()) {
                clearedAt = t
                clearedTile = Tile(nex.tile)
            }
            val now = nex.attr[GraniteMaul.LAST_ATTACK_CYCLE]
            if (now != null && now != last) attacks += t
            last = now
        }
        val centre = Tile(NexEncounter.CENTRE.x - 1, NexEncounter.CENTRE.z - 1, 0)
        assertTrue("$what: the rush ends within 5 ticks (busy cleared at tick $clearedAt, busy=${busy()})${log(alive.first(), nex)}", clearedAt in 1..5)
        assertEquals("$what: Nex is back on her centre tile when the rush ends", centre, clearedTile)
        assertTrue("$what: Nex attacks again within ${speed + 4} ticks, attack ticks $attacks${log(alive.first(), nex)}", attacks.isNotEmpty() && attacks.first() <= speed + 4)
        assertTrue("$what: Nex keeps attacking (>= 8 attacks in 60 ticks), saw $attacks${log(alive.first(), nex)}", attacks.size >= 8)
    }

    /** Nex's smoke-phase pull exactly as her rotation calls it (roll 3). */
    private fun pull(nex: Npc) {
        NexCombatScript::class.java.getDeclaredMethod("pullAttack", Npc::class.java).apply { isAccessible = true }.invoke(NexCombatScript, nex)
    }

    /** The phase's minion dies (the plugin's on_npc_death hook): Nex switches to the next element. */
    private fun nextPhase() {
        val index = NexEncounter.phase.ordinal
        NexEncounter.minions[index]?.let { if (it.isSpawned()) world.remove(it) }
        NexEncounter.onMinionDeath(index)
        tick(3)
    }

    /** A player walks into the arena; the arena timer (on_login) registers him and the fight starts. */
    private fun enter(
        name: String,
        at: Tile,
    ): Client {
        val player = player(name, at)
        world.plugins.executeLogin(player)
        return player
    }

    /** The intro ("AT LAST!", the four minions) ends and Nex engages her first target. */
    private fun awaitFight(player: Client): Npc {
        repeat(40) {
            tick(1)
            keepAlive(player, NexEncounter.nex)
            if (!NexEncounter.intro && NexEncounter.nex?.getCombatTarget() != null) return NexEncounter.nex!!
        }
        throw AssertionError("Nex never engaged: fightActive=${NexEncounter.fightActive} intro=${NexEncounter.intro} nex=${NexEncounter.nex}${log(player, NexEncounter.nex)}")
    }

    /** [n] ticks of fighting; returns the tick numbers (1-based) on which Nex attacked (Combat.postAttack). */
    private fun fight(
        player: Client,
        nex: Npc,
        n: Int,
    ): List<Int> {
        val attacks = mutableListOf<Int>()
        var last = nex.attr[GraniteMaul.LAST_ATTACK_CYCLE]
        for (t in 1..n) {
            tick(1)
            keepAlive(player, nex)
            val now = nex.attr[GraniteMaul.LAST_ATTACK_CYCLE]
            if (now != null && now != last) attacks += t
            last = now
        }
        return attacks
    }

    /** The fight is not decided by hitpoints: the player stays alive and Nex stays above her phase threshold. */
    private fun keepAlive(
        player: Client,
        nex: Npc?,
    ) {
        player.setCurrentLifepoints(player.getMaximumLifepoints())
        nex?.setCurrentLifepoints(NexEncounter.phaseFloor(NexEncounter.phase) + NexEncounter.PHASE_LIFEPOINTS)
    }

    private fun player(
        name: String,
        at: Tile,
    ): Client {
        world.collision.isClipped(at) // loads the region
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.tile = at
        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.MAGIC, Skills.RANGED, Skills.PRAYER).forEach {
            client.skills.setBaseLevel(it, 99)
        }
        client.setCurrentLifepoints(client.getMaximumLifepoints())
        val out = mutableListOf<Message>()
        val system = mockk<GameSystem>(relaxed = true)
        every { system.write(any()) } answers { out += firstArg<Message>() }
        client.gameSystem = system
        written[client] = out
        assertTrue("registered $name", world.register(client))
        players += client
        return client
    }

    private fun log(
        player: Client,
        npc: Npc?,
    ) = "\n" + traced.filter { (npc != null && (it.contains("#${npc.index}") || it.contains("index=${npc.index} "))) || it.contains(player.username) }.takeLast(50).joinToString("\n")

    /** The server's own swing trace (combat.plugin.kts / Combat) after trace line [from]: the player's attack on Nex went out. */
    private fun swung(
        player: Client,
        npc: Npc,
        from: Int,
    ) = traced.drop(from).any { it.contains("swing ${player.username} -> npc ${npc.id}#${npc.index} ") }

    private fun messages(p: Client) = written.getValue(p).filterIsInstance<MessageGameMessage>().map { it.message }

    private fun tick(n: Int) {
        repeat(n) {
            tasks.forEach { it.execute(world, service) }
            worldCycle.invoke(world)
        }
    }

    companion object {
        const val PULL_MESSAGE = "You've been injured and you cannot use protective prayers!"

        /** Longest pause between two Nex attacks: attack speed 5 plus No Escape's 4-tick rush (Novite NexCombat). */
        const val MAX_GAP = 10

        val AUTO_RETALIATE_OFF_VARP = gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP

        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.combat.Combat_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts",
                "gg.rsmod.plugins.content.npcs.definitions.godwars.Nex_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/godwars/nex.plugin.kts",
                "gg.rsmod.plugins.content.areas.godwars.nex.Nex_arena_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/areas/godwars/nex/nex_arena.plugin.kts",
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
            World::class.java.declaredMethods.first { it.name.startsWith("loadUpdateBlocks") }.apply { isAccessible = true }
                .invoke(world, Paths.get("..", "..", "data", "blocks.yml").toFile())
            repoOf(world)
            world.plugins.executeWorldInit(world)
            // Live data/cfg/areas/multi.yml lists the Ancient Prison (region 11601): Nex's arena is multi-combat.
            world.plugins.addMultiCombatRegion(11601)
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy { World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true } }

        private fun repoOf(world: World) {
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), NexPullFightTests::class.java.classLoader)
            SCRIPTS.forEach { (script, source) ->
                val compiled = File(classes, script.replace('.', '/') + ".class")
                if (!compiled.exists() || compiled.lastModified() < File(source).lastModified()) {
                    fail("${compiled.path} is missing or older than $source: the scripts compile did not run first (plugins/build.gradle test dependsOn)")
                }
                loader.loadClass(script)
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
            }
        }
    }
}
