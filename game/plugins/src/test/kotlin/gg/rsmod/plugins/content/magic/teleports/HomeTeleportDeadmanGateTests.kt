package gg.rsmod.plugins.content.magic.teleports

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
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
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.isAttacking
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.ext.switchSpellbook
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.mechanics.travel.SpiritTrees
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * N90 / N91 / N30 (one cause, team group N90 [N30, N91]).
 *
 * N90 (owner report bug-20261017-092722-anudd, 2841,3681 Death Plateau, Lunar book, unskulled, a Mountain troll 1108 attacking
 * him): "WHEN RUNNING AWAY AND TRYING TO TELEPORT IT SAYS YOU CANT USE THAT TELEPORT AT THE MOMENT THIS IS A BIG BUGG".
 * N91 (bug-20261017-092822-anudd, the same fight): "WHEN IN COMBAT WITH AN NPC IT SAYS ... YOU CANT HOME TELEPORT UNTILL 10
 * SECONDS AFTER THE END OF COMBAT IT SHOULD GIVE THE DEADMANTIMER".
 *
 * Cause: home_teleport_spells.plugin.kts kept the 2011scape-base refusals (hasMoveDestination / isAttacking -> "You can't use
 * that teleport at the moment."; ACTIVE_COMBAT_TIMER, 17 ticks -> "You can't home teleport until 10 seconds after the end of
 * combat.", the Darkan LodestoneAction / Novite HomeTeleport rule of the long 2011 lodestone cast) IN FRONT of the shared
 * Deadman gate (canTeleport -> DeadmanTimerGate.teleportDecision), which every other teleport route calls directly.
 *
 * Class (owner 2026-10-17 "every variant"): every teleport route answers the same state with the same shared gate - the three
 * home teleports (Standard 192, Ancient 193, Lunar 430), the ordinary book teleports (Varrock / Paddewwa) and the spirit tree
 * (N30: a teleport, OSRS Deadman Mode, never the always-7-s transport route). The test runs the real compiled scripts on the
 * real 667 map at the owner's spot, with a real Mountain troll (1108) fighting through the real combat script, and checks per
 * route: running away (a move destination) out of combat, attacking an npc, hit 2 ticks ago, hit 8 seconds ago (Deadman window
 * over, the 2011 10-second window not), skulled, skulled and hit during the countdown, hit by a boss, Tele Blocked, deep
 * Wilderness.
 */
class HomeTeleportDeadmanGateTests {
    private val players = mutableListOf<Client>()
    private val npcs = mutableListOf<Npc>()
    private val written = HashMap<Client, MutableList<Message>>()

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        players.clear()
        npcs.forEach { world.remove(it) }
        npcs.clear()
    }

    // ------------------------------------------------------------------------------------------------------------- owner cases

    @Test
    fun `N90 owner case - running away from the mountain troll and casting Lunar Home Teleport teleports home`() {
        val player = player("n90run", OWNER_TILE)
        player.switchSpellbook(Spellbook.LUNAR)
        val troll = troll()
        hitBy(player, troll)
        stopFight(player, troll)
        // The owner kept running: past the Deadman 7-second window (12 ticks) but inside the 2011 17-tick ACTIVE_COMBAT window.
        tick(DeadmanTimerGate.PVP_HIT_TELEPORT_BLOCK_CYCLES + 1)
        assertTrue("precondition: still inside the 2011 10-second window", player.timers.has(ACTIVE_COMBAT_TIMER))
        runAway(player)

        press(player, "Lunar Home Teleport")
        val landed = landedAfter(player, OWNER_TILE, 10)

        assertNoLegacyLine(player)
        assertNotNull("Lunar Home Teleport while running, 8 s after the last troll hit, must teleport home: ${messages(player)}", landed)
        assertTrue("landed at home ${player.tile}", player.tile.getDistance(HOME) <= 3)
    }

    @Test
    fun `N91 owner case - hit by the troll a moment ago, home teleport answers with the Deadman gate, never the 2011 line`() {
        val player = player("n91hit", OWNER_TILE)
        player.switchSpellbook(Spellbook.LUNAR)
        val troll = troll()
        hitBy(player, troll)
        stopFight(player, troll)
        tick(2)

        press(player, "Lunar Home Teleport")
        val landed = landedAfter(player, OWNER_TILE, 8)

        assertNoLegacyLine(player)
        assertEquals("unskulled + hit in the last 7 s: no teleport (DeadmanTimerGate BLOCKED_IN_COMBAT)", null, landed)
        assertTrue(
            "the Deadman gate's own answer, like every other teleport: ${messages(player)}",
            messages(player).any { it.startsWith("You must be out of combat for another ") && it.endsWith(" seconds to teleport.") },
        )
    }

    // ------------------------------------------------------------------------------------------------------------- the class

    @Test
    fun `class - every teleport route gives the same Deadman answer for every combat and movement state`() {
        val failures = mutableListOf<String>()
        for (route in ROUTES) {
            for (state in State.values()) {
                val player = player("c${route.key}${state.ordinal}", if (state == State.DEEP_WILDERNESS) DEEP_WILDERNESS else OWNER_TILE)
                val start = player.tile
                player.switchSpellbook(route.book)
                tick(1)
                written.getValue(player).clear()
                val troll = state.npc?.let { troll(it) }
                state.prepare(this, player, troll)
                route.press(this, player)
                state.afterPress(this, player, troll)
                val landed = landedAfter(player, start, if (state.name.startsWith("SKULLED")) SevenSecondAction.DURATION_CYCLES + 10 else 10)
                val got =
                    when {
                        landed != null -> Outcome.TELEPORTED
                        SevenSecondAction.isActive(player) -> Outcome.COUNTDOWN
                        else -> Outcome.REFUSED
                    }
                val legacy = messages(player).filter { it in LEGACY_LINES }
                if (got != state.expected || legacy.isNotEmpty()) {
                    failures += "${route.name} / $state: expected ${state.expected}, got $got; messages=${messages(player)}"
                }
                if (state.expectedMessage != null && messages(player).none { it.startsWith(state.expectedMessage) }) {
                    failures += "${route.name} / $state: expected the line '${state.expectedMessage}...', messages=${messages(player)}"
                }
                cleanUp()
            }
        }
        if (failures.isNotEmpty()) fail("${failures.size} route/state cases differ from the shared Deadman gate:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `the home teleport handler has no route-specific refusal in front of the shared gate`() {
        val source = File(HOME_SOURCE).readText()
        assertTrue("home teleport goes through canTeleport(TeleportType.MODERN)", "canTeleport(TeleportType.MODERN)" in source)
        listOf("hasMoveDestination()", "isAttacking()", "ACTIVE_COMBAT_TIMER", "You can't use that teleport at the moment.", "10 seconds after the end of combat")
            .forEach { assertFalse("home_teleport_spells.plugin.kts still contains '$it'", it in source) }
    }

    // ------------------------------------------------------------------------------------------------------------- states

    enum class Outcome { TELEPORTED, COUNTDOWN, REFUSED }

    enum class State(
        val expected: Outcome,
        /** The npc that fights the player in this state: the owner's Mountain troll, or a boss. */
        val npc: Int? = null,
        val expectedMessage: String? = null,
    ) {
        /** Not hit, running away: instant (OSRS Deadman: unskulled + not attacked in 7 s = instant; the teleport ends the walk). */
        RUNNING_OUT_OF_COMBAT(Outcome.TELEPORTED) {
            override fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) = t.runAway(p)
        },

        /** Attacking a troll that has not hit back yet: instant. */
        ATTACKING_NPC(Outcome.TELEPORTED, npc = Npcs.MOUNTAIN_TROLL_1108) {
            override fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) {
                p.attack(troll!!)
                assertTrue("precondition: attacking", p.isAttacking())
            }
        },

        /** Hit by the troll 2 ticks ago, standing still: the gate's out-of-combat message, no countdown. */
        HIT_TWO_TICKS_AGO(Outcome.REFUSED, npc = Npcs.MOUNTAIN_TROLL_1108, expectedMessage = "You must be out of combat for another ") {
            override fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) {
                t.hitBy(p, troll!!)
                t.stopFight(p, troll)
                t.tick(2)
            }
        },

        /** Hit 8 seconds ago (Deadman window over, the old 2011 17-tick window not), running away: instant. */
        HIT_EIGHT_SECONDS_AGO_RUNNING(Outcome.TELEPORTED, npc = Npcs.MOUNTAIN_TROLL_1108) {
            override fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) {
                t.hitBy(p, troll!!)
                t.stopFight(p, troll)
                t.tick(DeadmanTimerGate.PVP_HIT_TELEPORT_BLOCK_CYCLES + 1)
                assertTrue("precondition: inside the 2011 window", p.timers.has(ACTIVE_COMBAT_TIMER))
                t.runAway(p)
            }
        },

        /** Skulled, hit 8 seconds ago, standing still: the 7-second countdown, which then teleports by itself. */
        SKULLED_STANDING(Outcome.TELEPORTED, npc = Npcs.MOUNTAIN_TROLL_1108, expectedMessage = "Any action or being attacked will cancel this.") {
            override fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) {
                t.hitBy(p, troll!!)
                t.stopFight(p, troll)
                t.tick(DeadmanTimerGate.PVP_HIT_TELEPORT_BLOCK_CYCLES + 1)
                PvpSkull.applyTestSkull(p)
            }
        },

        /** Skulled, out of combat: the countdown starts; a troll hit during it cancels it - no teleport (OSRS Deadman). */
        SKULLED_HIT_DURING_COUNTDOWN(Outcome.REFUSED, npc = Npcs.MOUNTAIN_TROLL_1108, expectedMessage = "You have been attacked!") {
            override fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) = PvpSkull.applyTestSkull(p)

            override fun afterPress(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) {
                assertTrue("precondition: the countdown started", SevenSecondAction.isActive(p))
                t.hitBy(p, troll!!)
                t.stopFight(p, troll, clearMessages = false)
            }
        },

        /** Hit by a boss (Dharok) 2 ticks ago, unskulled: a boss hit never delays a teleport (owner 09-17) - instant. */
        BOSS_HIT_TWO_TICKS_AGO(Outcome.TELEPORTED, npc = Npcs.DHAROK_THE_WRETCHED) {
            override fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) {
                t.hitBy(p, troll!!)
                t.stopFight(p, troll)
                t.tick(2)
            }
        },

        /** Tele Blocked: refused by the shared restriction. */
        TELE_BLOCKED(Outcome.REFUSED, expectedMessage = "A magical force has stopped you from teleporting.") {
            override fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) {
                p.timers[TELEBLOCK_TIMER] = 500
            }
        },

        /** Level 48 Wilderness: refused (MODERN / spirit-tree level limits). */
        DEEP_WILDERNESS(Outcome.REFUSED) {
            override fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) {}
        },
        ;

        abstract fun prepare(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?)

        open fun afterPress(t: HomeTeleportDeadmanGateTests, p: Client, troll: Npc?) {}
    }

    class Route(val key: String, val name: String, val book: Spellbook, val press: HomeTeleportDeadmanGateTests.(Client) -> Unit)

    // ------------------------------------------------------------------------------------------------------------- helpers

    /** A real hit: the troll attacks through combat.plugin.kts until Combat.postAttack armed the player's combat state. */
    fun hitBy(
        player: Client,
        troll: Npc,
    ) {
        troll.attack(player)
        for (i in 0 until 30) {
            tick(1)
            player.setCurrentLifepoints(99)
            if (player.timers.has(TELEPORT_COMBAT_TIMER) && player.attr[LAST_HIT_BY_ATTR]?.get() === troll) return
        }
        fail("the Mountain troll never attacked ${player.username} (troll at ${troll.tile}, player at ${player.tile})")
    }

    /** The troll gives up (the owner outran it); the hit's timers keep running. */
    fun stopFight(
        player: Client,
        troll: Npc,
        clearMessages: Boolean = true,
    ) {
        Combat.reset(troll)
        troll.resetInteractions()
        world.remove(troll)
        npcs.remove(troll)
        player.resetInteractions()
        if (clearMessages) written.getValue(player).clear()
    }

    /** The owner's click on a far tile: a move destination while the spell is pressed. */
    fun runAway(player: Client) {
        player.walkTo(Tile(player.tile.x + 6, player.tile.z, player.tile.height))
        tick(1)
        assertTrue("precondition: ${player.username} is running (move destination) at ${player.tile}", player.hasMoveDestination())
        written.getValue(player).clear()
    }

    fun pressSpell(
        player: Client,
        name: String,
    ) = press(player, name)

    private fun press(
        player: Client,
        name: String,
    ) {
        val spell = MagicSpells.metadataByName(name) ?: throw AssertionError("no spell button for '$name'")
        assertTrue("'$name' (${spell.interfaceId}:${spell.component}) is bound", repo.executeButton(player, spell.interfaceId, spell.component))
    }

    /** The spirit tree route: the menu opened at the Stronghold tree, then the Grand Exchange row picked (SpiritTrees.select). */
    fun pressSpiritTree(player: Client) {
        SpiritTrees.open(player, atTree = false)
        SpiritTrees.select(player, SpiritTrees.STATIONS.indexOfFirst { it.tile == Tile(3185, 3511, 0) })
    }

    /** Ticks until [player] stood more than 20 tiles from [from] (a teleport), or null. */
    private fun landedAfter(
        player: Client,
        from: Tile,
        ticks: Int,
    ): Int? {
        for (i in 1..ticks) {
            tick(1)
            if (player.tile.getDistance(from) > 20) return i
        }
        return null
    }

    private fun assertNoLegacyLine(player: Client) {
        val legacy = messages(player).filter { it in LEGACY_LINES }
        assertTrue("the 2011 home-teleport refusals must be gone: $legacy", legacy.isEmpty())
    }

    private fun messages(p: Client) = written.getValue(p).filterIsInstance<MessageGameMessage>().map { it.message }

    private fun troll(id: Int = Npcs.MOUNTAIN_TROLL_1108): Npc {
        world.collision.isClipped(TROLL_TILE)
        val npc = Npc(id, TROLL_TILE, world)
        assertTrue(world.spawn(npc))
        npcs += npc
        return npc
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
        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.MAGIC).forEach { client.skills.setBaseLevel(it, 99) }
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

    fun tick(n: Int) {
        repeat(n) {
            tasks.forEach { it.execute(world, service) }
            worldCycle.invoke(world)
        }
    }

    companion object {
        /** bug-20261017-092722-anudd: the owner at 2841,3681, Mountain troll 1108 #4349 at 2839,3679 attacking him. */
        val OWNER_TILE = Tile(2841, 3681, 0)
        val TROLL_TILE = Tile(2839, 3679, 0)

        /** Wilderness level (3900 - 3520) / 8 + 1 = 48. */
        val DEEP_WILDERNESS = Tile(3040, 3900, 0)

        /** game.yml home-x/home-z (gameContext.home). */
        val HOME = Tile(3164, 3495, 0)

        const val HOME_SOURCE = "src/main/kotlin/gg/rsmod/plugins/content/magic/teleports/home_teleport_spells.plugin.kts"

        val LEGACY_LINES = setOf("You can't use that teleport at the moment.", "You can't home teleport until 10 seconds after the end of combat.")

        val ROUTES =
            listOf(
                Route("hs", "Home Teleport (192)", Spellbook.STANDARD) { pressSpell(it, "Home Teleport") },
                Route("ha", "Edgeville Home Teleport (193)", Spellbook.ANCIENT) { pressSpell(it, "Edgeville Home Teleport") },
                Route("hl", "Lunar Home Teleport (430)", Spellbook.LUNAR) { pressSpell(it, "Lunar Home Teleport") },
                Route("vt", "Varrock Teleport (192)", Spellbook.STANDARD) { pressSpell(it, "Varrock Teleport") },
                Route("pt", "Paddewwa Teleport (193)", Spellbook.ANCIENT) { pressSpell(it, "Paddewwa Teleport") },
                Route("st", "Spirit tree (N30)", Spellbook.STANDARD) { pressSpiritTree(it) },
            )

        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.combat.Combat_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts",
                "gg.rsmod.plugins.content.magic.teleports.Teleport_spells_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/magic/teleports/teleport_spells.plugin.kts",
                "gg.rsmod.plugins.content.magic.teleports.Home_teleport_spells_plugin" to HOME_SOURCE,
                "gg.rsmod.plugins.content.mechanics.pvp.Seven_second_action_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/seven_second_action.plugin.kts",
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
            every { ctx.home } returns HOME
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
            bind(world)
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy {
            World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true }
        }

        private val repo: PluginRepository get() = world.plugins

        private fun bind(world: World) {
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), HomeTeleportDeadmanGateTests::class.java.classLoader)
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
