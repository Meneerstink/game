package gg.rsmod.plugins.content.combat.scripts

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.message.Message
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.timer.ATTACK_DELAY
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
import gg.rsmod.plugins.content.areas.tzhaar.fightcaves.FightCaveCombatScripts
import gg.rsmod.plugins.content.combat.scripts.impl.CorporealBeastCombatScript
import gg.rsmod.plugins.content.combat.scripts.impl.GiantMoleCombatScript
import gg.rsmod.plugins.content.combat.scripts.impl.GlacorCombatScript
import gg.rsmod.plugins.content.combat.scripts.impl.TormentedDemonCombatScript
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * N84 (found by Bouwer N24 2026-10-17, proven for the strykewyrm: breach trace "delayReady=false delay=1"). A boss script that
 * loops `while (canEngageCombat(target) && isAttackDelayReady()) { attack; postAttackLogic; wait(attackSpeed) }` wakes one tick
 * before its own ATTACK_DELAY runs out, finds the delay not ready, leaves the loop and drops its target: the npc attacks ONCE and
 * then stands still until the player hits it again (a passive player, a player who ran, a pulled player: never attacked again).
 * Darkan NPCCombat.java:48-61 (process): every tick the combat delay counts down, a lost target ends the fight, and the npc attacks
 * whenever the delay reaches 0 - the delay never ends a fight. Ported as the shared [awaitAttackDelay] (waits tick by tick for the
 * delay, ends only on a lost / unreachable target), used by every looping script.
 *
 * Class test: every npc whose bound combat script loops (Giant Mole, Corporeal Beast, Tormented demon, Glacor and its
 * glacytes, Bork + Ork legion + Dagon'hai elite, the six Fight Cave monsters) keeps attacking a PASSIVE player (auto-retaliate off,
 * never hits back) at its own speed - and no plugin source still loops on the attack delay (the unbound Chaos Elemental, Dagannoth
 * Kings, KBD, metal dragons, Spinolyp scripts and the Nex minions included).
 */
class NpcCombatLoopTests {
    private val players = mutableListOf<Client>()
    private val npcs = mutableListOf<Npc>()

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        npcs.forEach { world.remove(it) }
        players.clear()
        npcs.clear()
    }

    @Test
    fun `every npc with a looping combat script keeps attacking a passive player`() {
        val failures = mutableListOf<String>()
        LOOPING.forEach { (name, id) ->
            val npc = Npc(id, ARENA, world)
            world.collision.isClipped(ARENA)
            assertTrue("$name spawned", world.spawn(npc))
            npcs += npc
            val player = player("n$id", ARENA.transform(-(2 + npc.getSize()), 0))
            player.setVarp(AUTO_RETALIATE_OFF_VARP, 1)
            npc.attack(player)
            var attacks = 0
            var delayed = false
            repeat(TICKS) {
                tick(1)
                player.setCurrentLifepoints(player.getMaximumLifepoints())
                val now = npc.timers.has(ATTACK_DELAY)
                if (now && !delayed) attacks++
                delayed = now
            }
            val speed = npc.combatDef.attackSpeed.coerceAtLeast(1)
            val expected = TICKS / (speed * 3)
            if (attacks < expected) failures += "$name ($id, attackSpeed $speed): $attacks attacks in $TICKS ticks against a passive player, expected >= $expected"
            world.unregister(player)
            players.remove(player)
            world.remove(npc)
            npcs.remove(npc)
        }
        if (failures.isNotEmpty()) fail("npc combat scripts that stop after an attack:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `no combat script loops on the attack delay any more`() {
        val loop = Regex("""while\s*\(.*&&.*isAttackDelayReady\(\)""")
        val offenders =
            File("src/main/kotlin").walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line -> if (loop.containsMatchIn(line)) "${f.path}:${i + 1}: ${line.trim()}" else null }
            }.toList()
        assertTrue("loops that end the fight when the attack delay is not ready yet (use awaitAttackDelay):\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    private fun player(
        name: String,
        at: Tile,
    ): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.tile = at
        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.MAGIC, Skills.RANGED).forEach {
            client.skills.setBaseLevel(it, 99)
        }
        client.setCurrentLifepoints(client.getMaximumLifepoints())
        val out = mutableListOf<Message>()
        val system = mockk<GameSystem>(relaxed = true)
        every { system.write(any()) } answers { out += firstArg<Message>() }
        client.gameSystem = system
        assertTrue("registered $name", world.register(client))
        players += client
        return client
    }

    private fun tick(n: Int) {
        repeat(n) {
            tasks.forEach { it.execute(world, service) }
            worldCycle.invoke(world)
        }
    }

    companion object {
        const val TICKS = 60

        /** An open, walkable floor (the Nex arena) - only the scripts below are bound, so it is just a big empty room here. */
        val ARENA = Tile(2928, 5203)

        val AUTO_RETALIATE_OFF_VARP = gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP

        val LOOPING: List<Pair<String, Int>> =
            listOf(
                "Giant Mole" to GiantMoleCombatScript.ids.first(),
                "Corporeal Beast" to CorporealBeastCombatScript.ids.first(),
                "Tormented demon" to TormentedDemonCombatScript.ids.first(),
                "Glacor" to GlacorCombatScript.ids.first(),
                "Glacyte" to GlacorCombatScript.GLACYTE_IDS.first(),
                "Bork" to Npcs.BORK_7134,
                "Ork legion" to Npcs.ORK_LEGION,
                "Dagon'hai elite" to 7137,
                "Tz-Kih" to FightCaveCombatScripts.TzKih.ids.first(),
                "Tok-Xil" to FightCaveCombatScripts.TokXil.ids.first(),
                "Yt-MejKot" to FightCaveCombatScripts.YtMejKot.ids.first(),
                "Ket-Zek" to FightCaveCombatScripts.KetZek.ids.first(),
                "TzTok-Jad" to FightCaveCombatScripts.TzTokJad.ids.first(),
                "Yt-HurKot" to FightCaveCombatScripts.YtHurKot.ids.first(),
                // N160 (2026-10-22): the WildyWyrm never attacked here - its victims came from a chunk scan.
                "WildyWyrm" to gg.rsmod.plugins.content.combat.scripts.impl.WildyWyrmCombatScript.ids.first(),
            )

        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.combat.Combat_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts",
                "gg.rsmod.plugins.content.combat.scripts.Combat_script_binding_plugin" to
                    "src/main/kotlin/gg/rsmod/plugins/content/combat/scripts/combat_script_binding.plugin.kts",
                "gg.rsmod.plugins.content.npcs.definitions.other.Glacor_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/other/glacor.plugin.kts",
                "gg.rsmod.plugins.content.areas.tzhaar.fightcaves.Fight_caves_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/areas/tzhaar/fightcaves/fight_caves.plugin.kts",
                "gg.rsmod.plugins.content.areas.wilderness.Bork_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/areas/wilderness/bork.plugin.kts",
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
            World::class.java.getDeclaredField("random").apply { isAccessible = true }.set(world, java.util.Random(84L))
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), NpcCombatLoopTests::class.java.classLoader)
            SCRIPTS.forEach { (script, source) ->
                val compiled = File(classes, script.replace('.', '/') + ".class")
                if (!compiled.exists() || compiled.lastModified() < File(source).lastModified()) {
                    fail("${compiled.path} is missing or older than $source: the scripts compile did not run first")
                }
                loader.loadClass(script)
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
            }
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy {
            World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true }
        }
    }
}
