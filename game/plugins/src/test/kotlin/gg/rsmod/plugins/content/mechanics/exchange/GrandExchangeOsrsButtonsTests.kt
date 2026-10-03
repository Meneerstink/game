package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.Server
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetEventsMessage
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.message.impl.RunClientScriptMessage
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.model.interf.InterfaceSet
import gg.rsmod.game.model.interf.listener.InterfaceListener
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.game.service.serializer.PlayerSerializerService
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E6 (owner 2026-10-03): the OSRS Grand Exchange parts the 667 screens lacked - item search history, the Collect
 * buttons, the History screen - and the Steel titan pouch report (205415) through the whole offer path.
 * Sources: OSRS cache 2686 scripts 793 / 788 / 803 / 1645 / 3362 and the OSRS Wiki "Grand Exchange" changes (see
 * [GrandExchangeInterface] / [GrandExchangeHistory]); layouts from the 667 cache.
 */
class GrandExchangeOsrsButtonsTests {
    private val sent = mutableListOf<Message>()
    private val serializer = mockk<PlayerSerializerService>(relaxed = true)

    private fun messages() = sent.filterIsInstance<MessageGameMessage>().map { it.message }

    private fun newService(): GrandExchangeService {
        val service = GrandExchangeService(File(Files.createTempDirectory("ge").toFile(), "offers.json"))
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        // init() reads the buy-limit table; the tests run from game/plugins, so the path is given (as the other GE
        // tests do, GrandExchangeBuyLimitTests) and the book is written on the test thread.
        val properties =
            ServerProperties().loadMap(
                mapOf("buy-limits" to Paths.get("..", "..", "data", "cfg", "ge", "buy-limits.json").toString(), "async-writes" to false),
            )
        service.init(mockk<Server>(relaxed = true), world, properties)
        return service
    }

    private fun newPlayer(
        service: GrandExchangeService,
        name: String = "tester",
    ): Client {
        val player = mockk<Client>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { world.getService(GrandExchangeService::class.java) } returns service
        // persistNow (GeEscrow.place, collect) saves through the serializer service, as CasinoPersistenceTests sets it up.
        every { world.getService(PlayerSerializerService::class.java, true) } returns serializer
        val players = PawnList<Player>(arrayOfNulls(8))
        every { world.players } returns players
        every { player.world } returns world
        every { player.loginUsername } returns name
        every { player.username } returns name
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val bank = ItemContainer(DEFINITIONS, BANK_KEY)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.inventory } returns inventory
        every { player.bank } returns bank
        every { player.equipment } returns equipment
        every { player.varps } returns VarpSet((0 until 4000).toSet())
        every { player.varcs } returns MutableList(4000) { 0 }
        val attributes = AttributeMap()
        every { player.attr } returns attributes
        every { player.tile } returns Tile(3165, 3487)
        // A real interface set (as SummoningPanelMountTests): the History / GE screens open on the resizable main screen.
        val interfaces =
            InterfaceSet(
                object : InterfaceListener {
                    override fun onInterfaceOpen(interfaceId: Int) = Unit

                    override fun onInterfaceClose(interfaceId: Int) = Unit
                },
            )
        interfaces.displayMode = DisplayMode.RESIZABLE_NORMAL
        every { player.interfaces } returns interfaces
        every { player.write(*anyVararg<Message>()) } answers { sent.addAll(firstArg<Array<Message>>()) }
        players.add(player)
        return player
    }

    @Test
    fun `steel titan pouch is bought through search, select, price, confirm, match, collect and history`() {
        // Owner report 205415: "trying to buy a steel titan pouch ... it gives a sound" (MSG_NOT_TRADEABLE + GE_TRADE_ERROR).
        val pouch = DEFINITIONS.get(ItemDef::class.java, STEEL_TITAN_POUCH)
        assertEquals("Steel titan pouch", pouch.name)
        assertTrue(GrandExchangeInterface.exchangeable(pouch), "Steel titan pouch must be exchangeable (items.yml tradeable)")
        assertEquals(STEEL_TITAN_POUCH, GrandExchangeInterface.unnoted(DEFINITIONS.get(ItemDef::class.java, pouch.noteLinkId)))

        val service = newService()
        val player = newPlayer(service)
        player.inventory.add(Items.COINS_995, 50_000_000)
        GrandExchangeInterface.beginBuy(player, 0)
        // The search answers with the item (on_obj_dialog), which the plugin also remembers as the previous search.
        assertTrue(GrandExchangeInterface.select(player, STEEL_TITAN_POUCH), "select refused: ${messages()}")
        GrandExchangeInterface.rememberSearch(player, STEEL_TITAN_POUCH)
        val selection = player.attr[GrandExchangeInterface.SELECTION_ATTR]!!
        assertEquals(STEEL_TITAN_POUCH, selection.itemId)
        assertTrue(selection.price >= 1 && selection.quantity == 1, "price ${selection.price} quantity ${selection.quantity}")
        val ask = service.houseQuote(STEEL_TITAN_POUCH)?.ask
        assertNotNull(ask, "the house must deal in the pouch, or a buy offer waits forever")
        selection.price = maxOf(selection.price, ask)

        assertTrue(GrandExchangeInterface.confirm(player), "confirm refused: ${messages()}")
        val offer = service.offerInSlot("tester", 0)
        assertNotNull(offer)
        assertEquals(1, offer.quantityFilled, "the buy offer must match (house liquidity)")
        assertTrue(messages().any { it == "Grand Exchange: Finished buying 1 x Steel titan pouch." }, messages().toString())

        assertTrue(GrandExchangeInterface.collectAll(player, toBank = false) > 0)
        assertEquals(1, player.inventory.getItemCount(STEEL_TITAN_POUCH))
        assertEquals(null, service.offerInSlot("tester", 0), "a drained offer frees its box")
        val history = service.historyFor("tester")
        assertEquals(STEEL_TITAN_POUCH, history.first().itemId)
        assertEquals(false, history.first().sold)

        // The next search offers the pouch as "Previous search:".
        sent.clear()
        GrandExchangeInterface.openItemSearch(player)
        assertTrue("${GrandExchangeInterface.CLIENT_PREFIX}last|$STEEL_TITAN_POUCH" in messages(), messages().toString())
    }

    @Test
    fun `search without a previous item tells the client none and keeps the 667 instruction`() {
        val player = newPlayer(newService())
        GrandExchangeInterface.openItemSearch(player)
        assertTrue("${GrandExchangeInterface.CLIENT_PREFIX}last|-1" in messages(), messages().toString())
        assertEquals("ge_last_search", GrandExchangeInterface.LAST_SEARCH_ATTR.persistenceKey, "the previous search survives a logout")
    }

    @Test
    fun `collect to inventory gives notes for a stack and coins, collect to bank gives the items`() {
        val service = newService()
        val seller = newPlayer(service, "seller")
        val buyer = newPlayer(service, "buyer")
        // A player trade: 10 Dragon bones bought at 12,000 offered for 12,500 each - the buyer gets items + change.
        service.submit("seller", OfferType.SELL, DRAGON_BONES, 12_000, 10, 0)
        service.submit("buyer", OfferType.BUY, DRAGON_BONES, 12_500, 10, 0)
        GrandExchangeInterface.collectAll(buyer, toBank = false)
        val notedBar = DEFINITIONS.get(ItemDef::class.java, DRAGON_BONES).noteLinkId
        assertEquals(10, buyer.inventory.getItemCount(notedBar), "a stack of more than one comes as notes")
        assertEquals(0, buyer.inventory.getItemCount(DRAGON_BONES))
        assertEquals(5_000, buyer.inventory.getItemCount(Items.COINS_995), "the change of a cheaper fill")

        GrandExchangeInterface.collectAll(seller, toBank = true)
        assertTrue(seller.bank.getItemCount(Items.COINS_995) > 0, "the seller's coins go to the bank")
        assertEquals(0, seller.inventory.getItemCount(Items.COINS_995))
        assertEquals(null, service.offerInSlot("seller", 0))
        assertEquals(null, service.offerInSlot("buyer", 0))
    }

    @Test
    fun `the Collect button's ops and the collection box's Inventory and Bank buttons reach collect all`() {
        val service = newService()
        val buyer = newPlayer(service, "buyer")
        service.submit("seller", OfferType.SELL, DRAGON_BONES, 12_000, 3, 0)
        service.submit("buyer", OfferType.BUY, DRAGON_BONES, 12_000, 3, 0)
        service.submit("seller", OfferType.SELL, DRAGON_BONES, 12_000, 2, 1)
        service.submit("buyer", OfferType.BUY, DRAGON_BONES, 12_000, 2, 1)
        val g = GrandExchangeInterface
        val collect = g.buttonLabelChild(g.INDEX_COLLECT_FIRST_CHILD)
        // Right-click "Collect to bank" (op2) on the GE screen: the items go to the bank as the item itself.
        assertTrue(g.pressButton(buyer, g.INDEX_BUTTONS, collect, op2 = true))
        assertEquals(5, buyer.bank.getItemCount(DRAGON_BONES))
        verify(atLeast = 1) { serializer.saveClientData(buyer) }
        assertEquals(null, service.offerInSlot("buyer", 0))
        assertEquals(null, service.offerInSlot("buyer", 1))

        // The seller's own boxes 0 and 1 still hold its uncollected coins.
        service.submit("seller", OfferType.SELL, DRAGON_BONES, 12_000, 4, 2)
        service.submit("buyer", OfferType.BUY, DRAGON_BONES, 12_000, 4, 0)
        // The collection box's "Inventory" button: a stack of more than one comes as notes.
        assertTrue(g.pressButton(buyer, g.COLLECTION_BOX_BUTTONS, g.buttonLabelChild(g.BOX_INVENTORY_FIRST_CHILD), op2 = false))
        assertEquals(4, buyer.inventory.getItemCount(DEFINITIONS.get(ItemDef::class.java, DRAGON_BONES).noteLinkId))
        // The sprite child (no ops) is not a button.
        assertEquals(false, g.pressButton(buyer, g.INDEX_BUTTONS, g.INDEX_COLLECT_FIRST_CHILD, op2 = false))
    }

    @Test
    fun `collect to bank fills the item's bank placeholder instead of adding a second stack`() {
        val service = newService()
        val buyer = newPlayer(service, "buyer")
        val def = DEFINITIONS.get(ItemDef::class.java, DRAGON_BONES)
        val saved = def.placeholderLink
        try {
            def.placeholderLink = PLACEHOLDER_ID
            buyer.bank[0] = gg.rsmod.game.model.item.Item(Items.LOBSTER, 1)
            buyer.bank[1] = gg.rsmod.game.model.item.Item(PLACEHOLDER_ID, 0)
            assertEquals(7, GrandExchangeInterface.addToBank(buyer, DRAGON_BONES, 7))
            assertEquals(DRAGON_BONES, buyer.bank[1]?.id)
            assertEquals(7, buyer.bank[1]?.amount)
            assertEquals(7, buyer.bank.getItemCount(DRAGON_BONES), "one stack, in the placeholder's slot")
        } finally {
            def.placeholderLink = saved
        }
        // Without a placeholder the item is added as usual.
        assertEquals(3, GrandExchangeInterface.addToBank(buyer, Items.COINS_995, 3))
        assertEquals(3, buyer.bank.getItemCount(Items.COINS_995))
    }

    @Test
    fun `collect keeps what does not fit in its box`() {
        val service = newService()
        val buyer = newPlayer(service, "buyer")
        service.submit("seller", OfferType.SELL, DRAGON_BONES, 12_000, 1, 0)
        service.submit("buyer", OfferType.BUY, DRAGON_BONES, 12_000, 1, 0)
        repeat(28) { buyer.inventory.add(Items.ABYSSAL_WHIP, 1) }
        assertEquals(0, GrandExchangeInterface.collectAll(buyer, toBank = false))
        assertTrue(GrandExchangeInterface.MSG_NO_SPACE in messages())
        val offer = service.offerInSlot("buyer", 0)
        assertNotNull(offer, "nothing collected - the box stays")
        assertEquals(1, offer.collectableItems)
    }

    @Test
    fun `history keeps the newest 20 trades with the fee a sale paid`() {
        val service = newService()
        repeat(25) { i ->
            service.submit("seller", OfferType.SELL, DRAGON_BONES, 12_000 + i, 1, 0)
            service.submit("buyer", OfferType.BUY, DRAGON_BONES, 12_000 + i, 1, 0)
            listOf("seller", "buyer").forEach { name ->
                val offer = service.offerInSlot(name, 0)!!
                service.takePart(name, offer.id, coins = true)
                service.takePart(name, offer.id, coins = false)
                service.releaseIfDrained(name, offer.id)
            }
        }
        val sold = service.historyFor("seller")
        assertEquals(GrandExchangeHistory.KEPT, sold.size, "OSRS keeps 20 trades")
        assertEquals(12_024L, sold.first().coins, "newest first")
        assertTrue(sold.all { it.sold })
        assertEquals(sold.first().coins - service.taxPerItem(DRAGON_BONES, 12_024).toLong(), sold.first().coins - sold.first().fee)
    }

    @Test
    fun `history rows read like OSRS script 1645`() {
        val buyOne = GeHistoryEntry(DRAGON_BONES, 1, 12_000, sold = false)
        val buyMany = GeHistoryEntry(DRAGON_BONES, 1_000, 12_000_000, sold = false)
        val sellFee = GeHistoryEntry(DRAGON_BONES, 10, 120_000, sold = true, fee = 2_400)
        val oneCoin = GeHistoryEntry(DRAGON_BONES, 1, 1, sold = false)
        assertEquals("<col=ffb83f>12,000 coins</col>", GrandExchangeHistory.price(buyOne))
        assertEquals("<col=ffb83f>12,000,000 coins</col><br>= 12,000 each", GrandExchangeHistory.price(buyMany))
        assertEquals("<col=ffb83f>117,600 coins</col><br><col=9f9f9f>(120,000 - 2,400)</col><br>= 11,760 each", GrandExchangeHistory.price(sellFee))
        assertEquals("<col=ffb83f>1 coin</col>", GrandExchangeHistory.price(oneCoin))
        val rows = GrandExchangeHistory.rows(listOf(sellFee, buyMany)) { "Dragon bones" }
        assertEquals(GrandExchangeHistory.Row("Sold:", "Dragon bones", "10", GrandExchangeHistory.price(sellFee)), rows[0])
        assertEquals(GrandExchangeHistory.Row("Bought:", "Dragon bones", "1,000", GrandExchangeHistory.price(buyMany)), rows[1])
        assertEquals(GrandExchangeHistory.Row("", "", "", ""), rows[2])
        assertEquals(GrandExchangeHistory.ROWS, rows.size)
    }

    @Test
    fun `history opens with OSRS title, row ops for filled rows and no ops for empty rows`() {
        val service = newService()
        service.submit("seller", OfferType.SELL, DRAGON_BONES, 12_000, 1, 0)
        service.submit("buyer", OfferType.BUY, DRAGON_BONES, 12_000, 1, 0)
        val offer = service.offerInSlot("buyer", 0)!!
        service.takePart("buyer", offer.id, coins = false)
        service.releaseIfDrained("buyer", offer.id)
        GrandExchangeHistory.open(newPlayer(service, "buyer"))
        val scripts = sent.filterIsInstance<RunClientScriptMessage>().filter { it.id == GrandExchangeHistory.SCRIPT_RSPS_SET_OPS }
        assertEquals(1, scripts.size, "one filled row")
        val events = sent.filterIsInstance<IfSetEventsMessage>().filter { it.hash shr 16 == GrandExchangeHistory.INTERFACE }
        assertEquals(GrandExchangeHistory.ROWS, events.size)
        assertEquals(GrandExchangeHistory.ROW_EVENTS, events.first { it.hash and 0xFFFF == GrandExchangeHistory.ROW[0] }.setting)
        assertTrue(events.filter { it.hash and 0xFFFF != GrandExchangeHistory.ROW[0] }.all { it.setting == 0 })
        assertEquals(2 or 1024, GrandExchangeHistory.ROW_EVENTS, "op1 Buy-offer (IF_BUTTON1 61) + op10 Examine (IF_BUTTON10 25)")
    }

    @Test
    fun `buttons carry the OSRS labels and ops and sit inside their layers`() {
        val g = GrandExchangeInterface
        assertEquals(listOf("History" to listOf("History"), "Collect" to listOf("Collect to inventory", "Collect to bank")), g.INDEX_BUTTONS.map { it.label to listOf(it.op1, it.op2).filter { o -> o.isNotEmpty() } })
        assertEquals(listOf("Inventory" to "Collect to inventory", "Bank" to "Collect to bank"), g.COLLECTION_BOX_BUTTONS.map { it.label to it.op1 })
        g.INDEX_BUTTONS.forEach { b ->
            assertTrue(b.x >= 21 && b.x + b.width <= 492 && b.y + b.height <= 80, "105:17 ${b.label} must stay above the offer boxes (y 80) inside the frame")
            assertEquals(g.MAIN, b.layer shr 16)
            assertEquals(g.INDEX_LAYER, b.layer and 0xFFFF)
        }
        g.COLLECTION_BOX_BUTTONS.forEach { b ->
            assertTrue(b.x + b.width <= 342 && b.y + b.height <= 39, "109:1 ${b.label} must stay left of the close button and above the boxes")
        }
        // Two children per button, in order without gaps (the client's CC_CREATE refuses a gap).
        listOf(g.INDEX_BUTTONS, g.COLLECTION_BOX_BUTTONS).forEach { buttons ->
            assertEquals(buttons.indices.map { it * 2 }, buttons.map { it.firstChild })
        }
        assertEquals(6, g.INDEX_BUTTONS[1].events, "Collect: op1 + op2")
        assertEquals(2, g.INDEX_BUTTONS[0].events, "History: op1")
    }

    @Test
    fun `the button layers are layers in the 667 cache and the history rows exist`() {
        // Dynamic children are drawn only under a LAYER (client InterfaceManager.draw); new-format byte 0 = -1, byte 1 = type.
        listOf(GrandExchangeInterface.MAIN to GrandExchangeInterface.INDEX_LAYER, GrandExchangeInterface.COLLECTION_BOX to GrandExchangeInterface.COLLECTION_BOX_LAYER)
            .forEach { (i, c) ->
                val data = LIBRARY.data(3, i, c)!!
                assertEquals(-1, data[0].toInt(), "$i:$c format")
                assertEquals(0, data[1].toInt(), "$i:$c must be a LAYER")
            }
        (GrandExchangeHistory.ROW.toList() + GrandExchangeHistory.TITLE).forEach { assertNotNull(LIBRARY.data(3, GrandExchangeHistory.INTERFACE, it), "643:$it") }
        assertNotNull(LIBRARY.data(3, GrandExchangeInterface.ITEM_SEARCH, GrandExchangeInterface.SEARCH_TEXT))
    }

    @Test
    fun `the 78 client knows the same script ids and side channel`() {
        val source = Paths.get("..", "..", "..", "..", "2011scape-client", "2011scape-client", "runescape", "src", "main", "java", "RspsGrandExchange.java").toFile()
        if (!source.isFile) return
        val text = source.readText()
        assertTrue("SCRIPT_BUTTON = ${GrandExchangeInterface.SCRIPT_RSPS_BUTTON};" in text)
        assertTrue("SCRIPT_SET_OPS = ${GrandExchangeHistory.SCRIPT_RSPS_SET_OPS};" in text)
        assertTrue("PREFIX = \"${GrandExchangeInterface.CLIENT_PREFIX}\";" in text)
    }

    companion object {
        const val STEEL_TITAN_POUCH = 12790
        const val DRAGON_BONES = 536

        /** Any id: a bank placeholder is the linked id with amount 0 (Bank.removePlaceholder). */
        const val PLACEHOLDER_ID = 4151

        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
            var id = -1
            File(Paths.get("..", "..", "data", "cfg", "items.yml").toFile().path).forEachLine { line ->
                when {
                    line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                    line.startsWith("  tradeable: ") && id >= 0 ->
                        DEFINITIONS.getNullable(ItemDef::class.java, id)?.tradeable = line.removePrefix("  tradeable: ").trim() == "true"
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
