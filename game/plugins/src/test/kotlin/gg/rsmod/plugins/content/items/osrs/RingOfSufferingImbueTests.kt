package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.handler.OpNpcUHandler
import gg.rsmod.game.message.handler.ResumePauseButtonHandler
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.message.impl.OpNpcUMessage
import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_ITEM
import gg.rsmod.game.model.attr.INTERACTING_ITEM_ID
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
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
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.mechanics.store.StoreUi
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * A11 + N61 + N82 (one cause, owner 2026-10-16): an imbue must turn the ring the player HANDS IN into its imbued variant, keeping
 * everything stored on it - the recoil charges and the recoil on/off setting (ItemAttribute.CHARGES / TOGGLED_OFF).
 *
 * - A11 (owner 10-11 "ring of suffering needs the exact same working as osrs so switchting r to ri", answer A = imbue at Perdu, charges
 *   kept; VRAGEN 12 answered 2026-10-17: 600 Deadman Points, only the Ring of suffering and its (r)).
 * - N61 (owner 10-16 "ring of recoil when recoil is enabled needs to show ri"): his ring (22407 (r), 1240 charges, Recoil settings
 *   disabled -> enabled) must become the Ring of suffering (ri) and stay (ri) through the Recoil settings toggle (OSRS: the toggle
 *   never renames the ring; only the imbue makes (r) into (ri)).
 * - N82: the 78 Store "Imbue services" upgrade built a fresh Item (StoreUi Kind.UPGRADE) and wiped the charges and the toggle; the
 *   whole class is every UPGRADE entry of the catalogue.
 *
 * Donor: Darkan SoulwarsRewards.kt:126-162 (use the ring on the imbue npc; "I can imbue that ring for you for <cost> ...", options
 * "Yes, please imbue my ring." / "Nevermind."; the SAME item object gets the imbued id, so its data stays).
 *
 * Driven on the real map and cache with the compiled scripts bound into a real [PluginRepository], the real use-item-on-npc packet
 * handler ([OpNpcUHandler], the player walks to Perdu) and the real dialogue continue / option clicks ([ResumePauseButtonHandler]).
 */
class RingOfSufferingImbueTests {
    private val players = mutableListOf<Client>()
    private val npcs = mutableListOf<Npc>()
    private val written = HashMap<Client, MutableList<Message>>()
    private val traced = mutableListOf<String>()

    @Before
    fun traces() {
        Trace.capture(traced)
    }

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        npcs.forEach { world.remove(it) }
        players.clear()
        npcs.clear()
        Trace.stopCapture()
    }

    // ------------------------------------------------------------------------------------------------------- A11 / N61: Perdu

    @Test
    fun `the owner case N61 - his recoil-enabled (r) with 1240 charges becomes the (ri) at Perdu and stays (ri) through Recoil settings`() {
        val (player, perdu) = atPerdu("n61")
        player.inventory[14] = charged(Items.RING_OF_SUFFERING_R, 1240, recoilOn = true)
        player.attr[DEADMAN] = 600
        // His chat: "Recoil is disabled." then "Recoil is enabled." - the real inventory option, twice.
        recoilSettings(player, 14)
        recoilSettings(player, 14)
        assertEquals("the owner's toggle round trip leaves recoil enabled", null, player.inventory[14]!!.attr[ItemAttribute.TOGGLED_OFF])

        useOn(player, perdu, 14)
        answer(player, YES)

        val ring = player.inventory[14]
        assertNotNull("the ring is still in slot 14: ${messages(player)}", ring)
        assertEquals("(r) -> (ri) at Perdu, item name '${name(ring!!.id)}'${dialogue()}", Items.RING_OF_SUFFERING_RI, ring.id)
        assertEquals("the inventory shows 'Ring of suffering (ri)'", "Ring of suffering (ri)", name(ring.id))
        assertEquals("all 1240 recoil charges kept", 1240, RingOfSuffering.charges(ring))
        assertTrue("recoil still enabled", RingOfSuffering.recoilEnabled(ring))
        assertEquals("600 Deadman Points taken once", 0, player.attr[DEADMAN])
        // OSRS (wiki Ring of suffering, 19 Nov 2025): the Recoil settings toggle never renames the ring - the (ri) stays (ri) both ways.
        recoilSettings(player, 14)
        assertEquals(Items.RING_OF_SUFFERING_RI, player.inventory[14]!!.id)
        assertTrue(messages(player).last(), messages(player).last().endsWith("1240 recoil charges. Recoil is disabled."))
        recoilSettings(player, 14)
        assertEquals(Items.RING_OF_SUFFERING_RI, player.inventory[14]!!.id)
        assertTrue(messages(player).last(), messages(player).last().endsWith("1240 recoil charges. Recoil is enabled."))
    }

    @Test
    fun `A11 every ring Perdu imbues keeps its charges and its recoil setting - (r) on and off, plain ring`() {
        val cases =
            listOf(
                Triple(charged(Items.RING_OF_SUFFERING_R, 100_000, recoilOn = false), Items.RING_OF_SUFFERING_RI, "full (r), recoil off"),
                Triple(charged(Items.RING_OF_SUFFERING_R, 40, recoilOn = true), Items.RING_OF_SUFFERING_RI, "one recoil (r), recoil on"),
                Triple(Item(Items.RING_OF_SUFFERING), Items.RING_OF_SUFFERING_I, "plain ring"),
            )
        cases.forEachIndexed { i, (before, imbuedId, label) ->
            val (player, perdu) = atPerdu("a11c$i")
            player.inventory[3] = before
            player.attr[DEADMAN] = 1_000
            useOn(player, perdu, 3)
            answer(player, YES)
            val ring = player.inventory[3]
            assertEquals("$label: imbued id${dialogue()} ${messages(player)}", imbuedId, ring?.id)
            assertEquals("$label: charges", RingOfSuffering.charges(before.copyCharged(imbuedId)), RingOfSuffering.charges(ring!!))
            assertEquals("$label: recoil setting", before.attr[ItemAttribute.TOGGLED_OFF], ring.attr[ItemAttribute.TOGGLED_OFF])
            assertEquals("$label: exactly 600 points", 400, player.attr[DEADMAN])
            assertEquals("$label: one ring in, one ring out", 1, player.inventory.rawItems.count { it != null })
            remove(player, perdu)
        }
    }

    @Test
    fun `A11 Perdu names the price, Nevermind costs nothing, too few points changes nothing`() {
        val (player, perdu) = atPerdu("a11p")
        player.inventory[0] = charged(Items.RING_OF_SUFFERING_R, 800, recoilOn = false)
        player.attr[DEADMAN] = 5_000
        useOn(player, perdu, 0)
        assertTrue("Perdu names the price${dialogue()}", traced.any { it.contains("dialogue npc ${Npcs.PERDU}") && it.contains("600 Deadman Points") })
        answer(player, NEVERMIND)
        assertEquals("Nevermind: ring unchanged", Items.RING_OF_SUFFERING_R, player.inventory[0]?.id)
        assertEquals("Nevermind: points unchanged", 5_000, player.attr[DEADMAN])

        player.attr[DEADMAN] = 599
        useOn(player, perdu, 0)
        answer(player, YES)
        assertEquals("599 points: ring unchanged", Items.RING_OF_SUFFERING_R, player.inventory[0]?.id)
        assertEquals("599 points: charges unchanged", 800, RingOfSuffering.charges(player.inventory[0]!!))
        assertEquals("599 points: nothing taken", 599, player.attr[DEADMAN])
        assertTrue("refusal said${dialogue()}", traced.any { it.contains("You don't have enough Deadman Points.") })
    }

    @Test
    fun `A11 a ring moved out of its slot while Perdu asks is never imbued and never paid for`() {
        val (player, perdu) = atPerdu("a11m")
        player.inventory[5] = charged(Items.RING_OF_SUFFERING_R, 400, recoilOn = true)
        player.attr[DEADMAN] = 1_200
        useOn(player, perdu, 5)
        // The question is open; the ring leaves the slot (dropped / banked / traded) before Yes.
        player.inventory[5] = null
        answer(player, YES)
        assertEquals("no points taken for a ring that is gone", 1_200, player.attr[DEADMAN])
        assertEquals("nothing created", 0, player.inventory.rawItems.count { it != null })
    }

    @Test
    fun `A11 the Perdu price and ring pairs are the 78 Store Imbue services entries - one price, one list`() {
        val store =
            StoreCatalogue.ENTRIES.filter { it.kind == StoreCatalogue.Kind.UPGRADE && it.category == "Imbue services" }
                .associate { it.requiredBaseItems.single() to it }
        listOf(Items.RING_OF_SUFFERING to Items.RING_OF_SUFFERING_I, Items.RING_OF_SUFFERING_R to Items.RING_OF_SUFFERING_RI).forEach { (base, imbued) ->
            val entry = store[base] ?: throw AssertionError("store has no imbue for $base")
            assertEquals(imbued, entry.resultItems.single())
            assertEquals("owner VRAGEN 12 A (2026-10-17): 600 Deadman Points", 600, entry.price)
            assertEquals(StoreCatalogue.Currency.DEADMAN, entry.currency)
        }
    }

    // ----------------------------------------------------------------------------------------------- N82: every store upgrade

    @Test
    fun `N82 every 78 Store upgrade keeps the data stored on the item the player hands in`() {
        val upgrades = StoreCatalogue.ENTRIES.filter { it.kind == StoreCatalogue.Kind.UPGRADE }
        assertTrue("the catalogue has upgrade entries (imbues, defenders)", upgrades.size >= 5)
        upgrades.forEachIndexed { i, entry ->
            val player = player("n82u$i", HOME)
            val base = entry.requiredBaseItems.single()
            val given = Item(base).also {
                it.attr[ItemAttribute.CHARGES] = 1240
                it.attr[ItemAttribute.TOGGLED_OFF] = 1
            }
            player.inventory[7] = given
            player.attr[entry.currency.attr] = entry.price
            buyInStore(player, entry)
            val result = player.inventory[7]
            assertEquals("${entry.category} $base -> ${entry.resultItems}: ${messages(player)}", entry.resultItems.single(), result?.id)
            assertEquals("${entry.category} $base: data kept (charges)", 1240, result!!.attr[ItemAttribute.CHARGES])
            assertEquals("${entry.category} $base: data kept (recoil off)", 1, result.attr[ItemAttribute.TOGGLED_OFF])
            assertEquals("${entry.category} $base: price taken once", 0, player.attr[entry.currency.attr])
            remove(player)
        }
    }

    @Test
    fun `N82 the owner's ring through the store - (r) with 1240 charges and recoil off becomes (ri) with both kept`() {
        val player = player("n82", HOME)
        player.inventory[14] = charged(Items.RING_OF_SUFFERING_R, 1240, recoilOn = false)
        player.attr[DEADMAN] = 600
        val entry = StoreCatalogue.ENTRIES.single { it.kind == StoreCatalogue.Kind.UPGRADE && it.requiredBaseItems == listOf(Items.RING_OF_SUFFERING_R) }
        buyInStore(player, entry)
        val ring = player.inventory[14]!!
        assertEquals(Items.RING_OF_SUFFERING_RI, ring.id)
        assertEquals("charges kept", 1240, RingOfSuffering.charges(ring))
        assertTrue("recoil still off", !RingOfSuffering.recoilEnabled(ring))
        assertEquals(0, player.attr[DEADMAN])
    }

    // ---------------------------------------------------------------------------------------------------------------- helpers

    private fun charged(
        id: Int,
        charges: Int,
        recoilOn: Boolean,
    ): Item =
        Item(id).also {
            it.attr[ItemAttribute.CHARGES] = charges
            if (!recoilOn) it.attr[ItemAttribute.TOGGLED_OFF] = 1
        }

    /** The expected ring after an imbue that keeps everything: same attributes on the imbued id. */
    private fun Item.copyCharged(id: Int): Item = Item(id, amount).copyAttr(this)

    private fun name(id: Int) = world.definitions.get(ItemDef::class.java, id).name

    private fun atPerdu(name: String): Pair<Client, Npc> {
        world.collision.isClipped(PERDU_TILE) // loads the region
        val npc = Npc(Npcs.PERDU, PERDU_TILE, world)
        assertTrue(world.spawn(npc))
        npcs += npc
        // The first walkable tile beside her (the GE home floor around her post).
        val stand = listOf(Tile(0, 1), Tile(1, 0), Tile(-1, 0), Tile(0, -1), Tile(1, 1), Tile(-1, 1))
            .map { PERDU_TILE.transform(it.x, it.z) }.firstOrNull { !world.collision.isClipped(it) }
            ?: throw AssertionError("no walkable tile beside Perdu at $PERDU_TILE")
        return player(name, stand) to npc
    }

    /** The client's "Use ring -> Perdu" packet (OpNpcU): the player walks to her and her handler runs. */
    private fun useOn(
        player: Client,
        npc: Npc,
        slot: Int,
    ) {
        val item = player.inventory[slot]!!
        OpNpcUHandler().handle(player, world, OpNpcUMessage(componentHash = 0, npcIndex = npc.index, item = item.id, slot = slot, movementType = 0))
        tick(8)
    }

    /** Continue Perdu's line, then click [option] (1 = Yes, 2 = Nevermind) - the client's RESUME_PAUSEBUTTON packets. */
    private fun answer(
        player: Client,
        option: Int,
    ) {
        ResumePauseButtonHandler().handle(player, world, ResumePauseButtonMessage(interfaceId = 241, component = 5, button = -1))
        tick(2)
        ResumePauseButtonHandler().handle(player, world, ResumePauseButtonMessage(interfaceId = 228, component = option + 1, button = option + 1))
        tick(3)
        // A closing line (refusal) is continued too.
        ResumePauseButtonHandler().handle(player, world, ResumePauseButtonMessage(interfaceId = 241, component = 5, button = -1))
        tick(2)
    }

    /** The inventory "Recoil settings" option of the ring in [slot] (ring_of_suffering.plugin.kts). */
    private fun recoilSettings(
        player: Client,
        slot: Int,
    ) {
        val item = player.inventory[slot]!!
        val option = world.definitions.get(ItemDef::class.java, item.id).inventoryMenu.indexOfFirst { it?.lowercase() == "recoil settings" } + 1
        assertTrue("'Recoil settings' on ${item.id}", option > 0)
        player.attr[INTERACTING_ITEM] = java.lang.ref.WeakReference(item)
        player.attr[INTERACTING_ITEM_ID] = item.id
        player.attr[INTERACTING_ITEM_SLOT] = slot
        assertTrue("Recoil settings is bound", repo.executeItem(player, item.id, option))
        tick(1)
    }

    /** The 78 Store: open the entry's shop, page to it, select it, then Buy twice (arm + confirm), as the player clicks. */
    private fun buyInStore(
        player: Client,
        entry: StoreCatalogue.Entry,
    ) {
        StoreUi.open(player, entry.shop)
        val index = StoreUi.entries(player).indexOf(entry)
        assertTrue("entry listed in ${entry.shop}: ${messages(player)}", index >= 0)
        val perPage = StoreUi.slotCount(player)
        StoreUi.turnPage(player, index / perPage)
        StoreUi.select(player, index % perPage)
        assertEquals(entry, StoreUi.selected(player))
        StoreUi.buy(player)
        StoreUi.buy(player)
    }

    private fun player(
        name: String,
        at: Tile,
    ): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.tile = at
        // A fresh Client has 0 hitpoints - "dead" for every service gate (RemoteAccess NOT_NOW).
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

    private fun messages(p: Client) = written.getValue(p).filterIsInstance<MessageGameMessage>().map { it.message }

    private fun dialogue() = "\n" + traced.filter { it.contains("dialogue") || it.contains("item-option") }.takeLast(12).joinToString("\n")

    private fun tick(n: Int) {
        repeat(n) {
            tasks.forEach { it.execute(world, service) }
            worldCycle.invoke(world)
        }
    }

    companion object {
        const val YES = 1
        const val NEVERMIND = 2

        /** Perdu's post in the GE home hall (owner report 2026-10-11: "14394 'Perdu' at 3158,3495"). */
        val PERDU_TILE = Tile(3158, 3495)

        /** Two tiles north of her, inside the GE safe zone. */
        val HOME = Tile(3158, 3497)

        val DEADMAN = StoreCatalogue.Currency.DEADMAN.attr

        /** Every script that binds a use-item-on-Perdu or the ring's own options; a script that does not exist yet is skipped. */
        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.items.osrs.Ring_of_suffering_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/items/osrs/ring_of_suffering.plugin.kts",
                "gg.rsmod.plugins.content.items.osrs.Ring_of_suffering_imbue_plugin" to
                    "src/main/kotlin/gg/rsmod/plugins/content/items/osrs/ring_of_suffering_imbue.plugin.kts",
                "gg.rsmod.plugins.content.mechanics.trouver.Trouver_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/mechanics/trouver/trouver.plugin.kts",
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
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy { World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true } }

        private lateinit var repo: PluginRepository

        private fun repoOf(world: World) {
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), RingOfSufferingImbueTests::class.java.classLoader)
            SCRIPTS.forEach { (script, source) ->
                if (!File(source).exists()) {
                    println("[A11] script $source does not exist - not bound")
                    return@forEach
                }
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
