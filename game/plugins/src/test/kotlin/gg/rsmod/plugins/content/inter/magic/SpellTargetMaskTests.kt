package gg.rsmod.plugins.content.inter.magic

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetEventsMessage
import gg.rsmod.game.model.Tile
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
import gg.rsmod.game.tools.importer.InterfaceHookProbeTool
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.ext.switchSpellbook
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.combat.strategy.magic.SpellEffect
import gg.rsmod.plugins.content.magic.SpellbookData
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
 * N76 (owner report 2026-10-16 20:04, anudd2 at 3149,3551 in the Wilderness, his War tortoise and a Bloodhound pet beside him):
 * "when a npc or summoning monster or pet is standing in your opponent when fighting u want to cast teleblock it tries to cast it
 * on the npc instead of the player".
 *
 * Cause: the 667 cache bakes Tele Block (192:86) with the target mask NPC + player (events 0x5400, mask 10). The client
 * (MiniMenu.addNpcEntries) offers "Cast -> <npc>" for every NPC under the cursor while the mask has TGT_NPC, and target entries are
 * ordered by entity, so a follower picked after the opponent puts its Cast entry on top = the left-click. The server refuses it
 * ("You can only cast this spell on other players.") and the cast is lost. OSRS Wiki Tele Block, 17 October 2019: "The Teleblock
 * spell no longer gives options for casting it on NPCs."
 *
 * Class (owner 2026-10-17 "every variant"): every spell of the three spellbooks whose 667 mask offers a target kind the server never
 * accepts - Tele Block (combat engine refuses NPCs), Teleother x3 / Cure Other / Energy Transfer / Stat Spy / Vengeance Other /
 * Heal Other (only an on_spell_on_player handler), Monster Examine (only an on_spell_on_npc handler). The test scans every
 * component of 192/193/430 in the real cache, binds the real compiled scripts (magic tab, combat spells, lunar, standard utility),
 * opens each book the way the server does (switchSpellbook -> IfOpenSub -> on_interface_open, one tick later the masks), and
 * checks the mask the client ends up with: a target bit stays exactly when the server accepts that target kind. The legacy
 * Autocast-op strip (LEGACY_AUTOCAST_OPS) must stay as it is.
 */
class SpellTargetMaskTests {
    private val players = mutableListOf<Client>()
    private val written = HashMap<Client, MutableList<Message>>()

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        players.clear()
    }

    @Test
    fun `N76 owner case - Tele Block opened on the standard book offers only players, never the War tortoise or a pet`() {
        val player = player("n76tb", OWNER_TILE)
        open(player, Spellbook.STANDARD)
        val mask = targetMask(effectiveEvents(player, 192, SpellbookData.TELEPORT_BLOCK.component))
        assertTrue("Tele Block keeps the player target (mask $mask)", mask and TGT_PLAYER != 0)
        assertEquals("OSRS 17-10-2019: Tele Block gives no option to cast it on NPCs (mask $mask)", 0, mask and TGT_NPC)
    }

    @Test
    fun `N76 class - every spell's target mask offers exactly the target kinds the server accepts, on every book`() {
        val problems = mutableListOf<String>()
        val unhandled = mutableListOf<String>()
        var checked = 0
        for ((book, spellbook) in BOOKS) {
            val player = player("n76c$book", OWNER_TILE)
            open(player, spellbook)
            for (component in components(book)) {
                val baked = bakedEvents(book, component)
                val bakedTargets = targetMask(baked) and (TGT_NPC or TGT_PLAYER)
                if (bakedTargets == 0) continue
                val name = spellName(book, component)
                val acceptsNpc = repo.hasSpellOnNpc(book, component) && !refusesNpcs(book, component)
                val acceptsPlayer = repo.hasSpellOnPlayer(book, component)
                if (!acceptsNpc && !acceptsPlayer) {
                    // No server handler at all: not this item's class (a spell without a cast handler), left as the cache has it.
                    unhandled += "$book:$component $name"
                    continue
                }
                checked++
                val effective = effectiveEvents(player, book, component)
                val mask = targetMask(effective)
                val wantNpc = bakedTargets and TGT_NPC != 0 && acceptsNpc
                val wantPlayer = bakedTargets and TGT_PLAYER != 0 && acceptsPlayer
                if ((mask and TGT_NPC != 0) != wantNpc) {
                    problems += "$book:$component $name: NPC target ${if (wantNpc) "missing" else "offered but the server never accepts it"} " +
                        "(baked 0x%06x, sent 0x%06x)".format(baked, effective)
                }
                if ((mask and TGT_PLAYER != 0) != wantPlayer) {
                    problems += "$book:$component $name: player target ${if (wantPlayer) "missing" else "offered but the server never accepts it"} " +
                        "(baked 0x%06x, sent 0x%06x)".format(baked, effective)
                }
                val otherTargets = targetMask(baked) and (TGT_NPC or TGT_PLAYER).inv()
                if (targetMask(effective) and (TGT_NPC or TGT_PLAYER).inv() != otherTargets) {
                    problems += "$book:$component $name: other target bits changed (baked 0x%06x, sent 0x%06x)".format(baked, effective)
                }
            }
        }
        println("[N76] checked $checked targeted spells; without any cast handler (left alone): $unhandled")
        assertTrue("the scan reached the spellbooks (checked $checked)", checked >= 60)
        if (problems.isNotEmpty()) fail("${problems.size} spell target masks disagree with the server:\n" + problems.joinToString("\n"))
    }

    @Test
    fun `N76 the player-only masks are re-armed on every open - book switch 192 to 193 to 430 and back, relog`() {
        val player = player("n76re", OWNER_TILE)
        val cycle = listOf(Spellbook.STANDARD, Spellbook.ANCIENT, Spellbook.LUNAR, Spellbook.STANDARD, Spellbook.LUNAR, Spellbook.STANDARD)
        cycle.forEachIndexed { step, book ->
            open(player, book)
            when (book) {
                Spellbook.STANDARD -> {
                    val mask = targetMask(effectiveEvents(player, 192, SpellbookData.TELEPORT_BLOCK.component))
                    assertEquals("step $step: Tele Block has no NPC target after re-opening the standard book", 0, mask and TGT_NPC)
                    assertTrue("step $step: Tele Block keeps the player target", mask and TGT_PLAYER != 0)
                }
                Spellbook.LUNAR -> {
                    val mask = targetMask(effectiveEvents(player, 430, SpellbookData.HEAL_OTHER.component))
                    assertEquals("step $step: Heal Other has no NPC target after re-opening the lunar book", 0, mask and TGT_NPC)
                }
                else -> Unit
            }
        }
        // Relog: a new session opens the book again from the login tab setup.
        cleanUp()
        val relogged = player("n76re", OWNER_TILE)
        open(relogged, Spellbook.STANDARD)
        assertEquals("after relog Tele Block has no NPC target", 0, targetMask(effectiveEvents(relogged, 192, SpellbookData.TELEPORT_BLOCK.component)) and TGT_NPC)
    }

    @Test
    fun `N76 neighbours - combat spells keep NPC and player, Crumble Undead stays NPC only, the Autocast op stays stripped`() {
        val standard = player("n76n1", OWNER_TILE)
        open(standard, Spellbook.STANDARD)
        listOf(CombatSpell.FIRE_SURGE, CombatSpell.WIND_STRIKE, CombatSpell.ENTANGLE).forEach { spell ->
            val events = effectiveEvents(standard, 192, spell.componentId)
            assertEquals("$spell keeps NPC + player (0x%06x)".format(events), TGT_NPC or TGT_PLAYER, targetMask(events) and (TGT_NPC or TGT_PLAYER))
            assertEquals("$spell: legacy Autocast op1 stays stripped", 0, events and 0x2)
        }
        val crumble = effectiveEvents(standard, 192, CombatSpell.CRUMBLE_UNDEAD.componentId)
        assertEquals("Crumble Undead stays NPC only", TGT_NPC, targetMask(crumble) and (TGT_NPC or TGT_PLAYER))
        assertEquals("Defensive Casting toggle 192:2 stays stripped", 0x000400, effectiveEvents(standard, 192, 2))
        val ancient = player("n76n2", OWNER_TILE)
        open(ancient, Spellbook.ANCIENT)
        val barrage = effectiveEvents(ancient, 193, CombatSpell.ICE_BARRAGE.componentId)
        assertEquals("Ice Barrage keeps NPC + player", TGT_NPC or TGT_PLAYER, targetMask(barrage) and (TGT_NPC or TGT_PLAYER))
        assertEquals("Ice Barrage: Ancient Autocast op6 stays stripped", 0, barrage and 0x40)
        // The Tele Block override keeps every non-target bit of the 667 component (op bit 0x400 included).
        val tb = effectiveEvents(standard, 192, SpellbookData.TELEPORT_BLOCK.component)
        assertEquals("Tele Block non-target bits = the 667 baked ones", bakedEvents(192, 86) and TARGET_BITS.inv(), tb and TARGET_BITS.inv())
    }

    @Test
    fun `N76 the corrected masks start from the 667 cache's own baked events`() {
        SpellTargetMasks.BAKED.forEach { (spell, baked) ->
            assertEquals("${spell.spellName} ${spell.interfaceId}:${spell.component} baked events", bakedEvents(spell.interfaceId, spell.component), baked)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- helpers

    private fun open(
        player: Client,
        book: Spellbook,
    ) {
        written.getValue(player).clear()
        player.switchSpellbook(book)
        tick(2)
    }

    /** What the client ends up with: it reloads the baked mask on open, then applies the last IF_SETEVENTS for that component. */
    private fun effectiveEvents(
        player: Client,
        book: Int,
        component: Int,
    ): Int =
        written.getValue(player).filterIsInstance<IfSetEventsMessage>()
            .lastOrNull { it.hash == ((book shl 16) or component) && it.fromChild <= -1 && it.toChild >= -1 }?.setting
            ?: bakedEvents(book, component)

    private fun refusesNpcs(
        book: Int,
        component: Int,
    ): Boolean =
        // MagicCombatStrategy: a Teleblock-effect spell on a non-player -> "You can only cast this spell on other players."
        CombatSpell.values.any { it.interfaceId == book && it.componentId == component && it.effect == SpellEffect.Teleblock }

    private fun spellName(
        book: Int,
        component: Int,
    ): String = SpellbookData.values().firstOrNull { it.interfaceId == book && it.component == component }?.spellName ?: "?"

    private fun PluginRepository.hasSpellOnNpc(
        book: Int,
        component: Int,
    ): Boolean = spellMap("spellOnNpcPlugins").containsKey((book shl 16) or component)

    private fun PluginRepository.hasSpellOnPlayer(
        book: Int,
        component: Int,
    ): Boolean = spellMap("spellOnPlayerPlugins").containsKey((book shl 16) or component)

    private fun player(
        name: String,
        at: Tile,
    ): Client {
        world.collision.isClipped(at) // loads the region
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

    private fun tick(n: Int) {
        repeat(n) {
            tasks.forEach { it.execute(world, service) }
            worldCycle.invoke(world)
        }
    }

    companion object {
        const val TGT_NPC = 0x02
        const val TGT_PLAYER = 0x08
        const val TARGET_BITS = 0x7F shl 11

        /** The owner's report position (bug-20261016-200406-anudd2): Wilderness north of Edgeville. */
        val OWNER_TILE = Tile(3149, 3551, 0)

        val BOOKS = listOf(192 to Spellbook.STANDARD, 193 to Spellbook.ANCIENT, 430 to Spellbook.LUNAR)

        fun targetMask(events: Int) = (events shr 11) and 0x7F

        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.inter.magic.Magic_tab_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/inter/magic/magic_tab.plugin.kts",
                "gg.rsmod.plugins.content.combat.strategy.magic.Combat_spells_plugin" to
                    "src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/magic/combat_spells.plugin.kts",
                "gg.rsmod.plugins.content.magic.lunar.Lunar_spells_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/magic/lunar/lunar_spells.plugin.kts",
                "gg.rsmod.plugins.content.magic.standard.Standard_utility_spells_plugin" to
                    "src/main/kotlin/gg/rsmod/plugins/content/magic/standard/standard_utility_spells.plugin.kts",
            )

        private val LIBRARY by lazy { CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()) }

        private val bakedCache = HashMap<Int, Int>()

        /** The 667 cache's baked `events` of one component (InterfaceHookProbeTool's decoder, the client's Component.decode layout). */
        fun bakedEvents(
            book: Int,
            component: Int,
        ): Int =
            bakedCache.getOrPut((book shl 16) or component) {
                val data = LIBRARY.data(3, book, component) ?: throw AssertionError("no cache component $book:$component")
                val decoded = DECODE.invoke(InterfaceHookProbeTool, data)
                decoded.javaClass.getDeclaredMethod("getEvents").apply { isAccessible = true }.invoke(decoded) as Int
            }

        fun components(book: Int): List<Int> = (0 until 400).filter { LIBRARY.data(3, book, it) != null }

        private val DECODE by lazy {
            InterfaceHookProbeTool::class.java.declaredMethods.first { it.name.startsWith("decodeComponent") }.apply { isAccessible = true }
        }

        @Suppress("UNCHECKED_CAST")
        private fun PluginRepository.spellMap(field: String): Map<Int, Any> =
            PluginRepository::class.java.getDeclaredField(field).apply { isAccessible = true }.get(this) as Map<Int, Any>

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
            bind(world)
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy {
            World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true }
        }

        private val repo: PluginRepository get() = world.plugins

        private fun bind(world: World) {
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), SpellTargetMaskTests::class.java.classLoader)
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
