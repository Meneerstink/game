package gg.rsmod.plugins.content.mechanics.exchange

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import gg.rsmod.game.Server
import gg.rsmod.game.model.World
import gg.rsmod.game.service.BackgroundIo
import gg.rsmod.game.service.Service
import gg.rsmod.util.ServerProperties
import gg.rsmod.util.io.AtomicFiles
import mu.KLogging
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Owns the Grand Exchange order book: submitting/cancelling offers, matching
 * them (via the pure [GrandExchangeBook] engine), and persisting the book to
 * disk so open offers and un-collected proceeds survive a server restart, per
 * the confirmed PROJECT_PLAN.md SS7 requirement.
 *
 * Deliberately decoupled from any player inventory access - this service
 * only ever moves quantities between an offer's escrow fields. The caller
 * (see `grand_exchange.plugin.kts`) is responsible for actually debiting the
 * submitting player's inventory *before* calling [submit], and for crediting
 * a player's inventory *from* [takeCollectable]'s result, restoring anything
 * that couldn't be delivered via [restoreCollectable] so nothing is ever
 * silently duplicated or lost.
 *
 * Persistence is synchronous (write-through on every mutation) rather than
 * on a periodic timer: offer mutations are rare compared to game ticks, so
 * the simplicity and crash-safety of always-current data on disk outweighs
 * the cost, matching the hard economy-integrity requirement.
 *
 * RCV-010 C3: every offer now occupies one of the player's [SLOTS] offer boxes (the 667 screen has six), a slot is
 * released only once its offer is finished and nothing is left to collect, and every executed trade feeds the guide
 * price used by the ±5 % offer range.
 *
 * RCV-011: every match is capped by the buyers' [GeBuyLedger] ([GeBuyLimits]), and releasing a finished offer that
 * traded anything adds it to the owner's History ([GrandExchangeHistory], newest first, [HISTORY_ROWS] kept).
 */
class GrandExchangeService(
    private val saveFile: File = File("data/ge/offers.json"),
    private val clock: () -> Long = System::currentTimeMillis,
) : Service {
    private val lock = ReentrantLock()
    private val offers = mutableListOf<GrandExchangeOffer>()
    private val nextId = AtomicLong(1)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val priceFile: File get() = File(saveFile.parentFile ?: File("."), "guide_prices.json")
    private val historyFile: File get() = File(saveFile.parentFile ?: File("."), "history.json")
    private val tradePrices = mutableMapOf<Int, MutableList<Int>>()

    /** Audit E-01: the guide price per item ([GeGuidePrice]); `guide_prices.json` is still written for older builds. */
    private val guides = mutableMapOf<Int, GeGuideIndex>()
    private val guideFile: File get() = File(saveFile.parentFile ?: File("."), "guide_index.json")
    private val history = mutableMapOf<String, MutableList<GeHistoryEntry>>()
    private val buyLedger = GeBuyLedger(clock)

    /**
     * Item definitions, captured at boot so [housePrice] can seed a guide price for an item that has never been
     * traded. The service has no other route to the cache.
     */
    private var definitions: gg.rsmod.game.fs.DefinitionSet? = null

    /** The world, for the NPC shop bounds of the house price ([GeHousePricing]). */
    private var world: World? = null

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        definitions = world.definitions
        this.world = world
        GeBuyLimits.load()
        load()
        // Audit T-11: in the running server the book is written off the game thread (see save()).
        asyncWrites = serviceProperties.getOrDefault("async-writes", true)
    }

    /**
     * The house quote for [itemId], or null when the house does not deal in it.
     *
     * Owner 2026-09-20: every item is buyable and sellable from the house
     * ([GeSystemLiquidity.ALL_ITEMS_AT_GUIDE_PRICE]). Only items that may be exchanged at all qualify - an
     * untradeable item, a noted id or coins are never dealt by the house, the same rule the offer screen applies.
     *
     * Audit E-01/E-03: the quote comes from the fixed OSRS seed and the shop/alchemy bounds ([GeHousePricing]),
     * never from the guide price, which trades between players move. The seller's convenience fee ([GeTax]) is
     * charged by [GrandExchangeBook.match] through [taxPerItem], on house sales as on player trades.
     */
    fun houseQuote(itemId: Int): GeHouseQuote? {
        if (!GeSystemLiquidity.ALL_ITEMS_AT_GUIDE_PRICE) {
            return GeSystemLiquidity.quote(itemId)
        }
        val defs = definitions ?: return GeSystemLiquidity.quote(itemId)
        val def =
            try {
                defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, itemId)
            } catch (ignored: Exception) {
                return null
            }
        if (!GrandExchangeInterface.exchangeable(def)) {
            return null
        }
        return GeHousePricing.quote(def, world)
    }

    /** What the house sells [itemId] for (its ask), or null when it does not deal in it. */
    fun housePrice(itemId: Int): Int? = houseQuote(itemId)?.ask

    /** The OSRS seed of [itemId] ([OsrsGuidePrices.seed]), or null without item definitions (unit tests). */
    private fun seedOf(itemId: Int): Int? {
        val def = definitions?.getNullable(gg.rsmod.game.fs.def.ItemDef::class.java, itemId) ?: return null
        return OsrsGuidePrices.seed(def)
    }

    /** The OSRS convenience fee ([GeTax]) on one [itemId] sold at [unitPrice]; 0 for an exempt item. */
    fun taxPerItem(
        itemId: Int,
        unitPrice: Int,
    ): Int {
        val def = definitions?.getNullable(gg.rsmod.game.fs.def.ItemDef::class.java, itemId)
        return if (GeTax.exempt(def)) 0 else GeTax.perItem(unitPrice)
    }

    override fun postLoad(
        server: Server,
        world: World,
    ) {
    }

    override fun bindNet(
        server: Server,
        world: World,
    ) {
    }

    override fun terminate(
        server: Server,
        world: World,
    ) {
        lock.lock()
        try {
            save()
        } finally {
            lock.unlock()
        }
        // Audit T-11: the last snapshot must be on disk before the server exits.
        BackgroundIo.flush()
    }

    fun load() {
        // Audit T-11: a restarted service must read what an earlier instance queued for writing.
        BackgroundIo.flush()
        lock.lock()
        try {
            if (saveFile.exists()) {
                saveFile.bufferedReader().use { reader ->
                    val type = object : TypeToken<MutableList<GrandExchangeOffer>>() {}.type
                    val loaded: MutableList<GrandExchangeOffer>? = gson.fromJson(reader, type)
                    if (loaded != null) {
                        offers.clear()
                        offers.addAll(loaded)
                        nextId.set((offers.maxOfOrNull { it.id } ?: 0L) + 1)
                    }
                }
            }
            if (priceFile.exists()) {
                priceFile.bufferedReader().use { reader ->
                    val type = object : TypeToken<MutableMap<Int, MutableList<Int>>>() {}.type
                    val loaded: MutableMap<Int, MutableList<Int>>? = gson.fromJson(reader, type)
                    if (loaded != null) {
                        tradePrices.clear()
                        tradePrices.putAll(loaded)
                    }
                }
            }
            // Audit E-01: guide_index.json supersedes guide_prices.json; a book without it is migrated from the old file.
            guides.clear()
            if (guideFile.exists()) {
                guideFile.bufferedReader().use { reader ->
                    val type = object : TypeToken<MutableMap<Int, GeGuideIndex>>() {}.type
                    val loaded: MutableMap<Int, GeGuideIndex>? = gson.fromJson(reader, type)
                    loaded?.forEach { (itemId, index) ->
                        @Suppress("SENSELESS_COMPARISON")
                        if (index.samples == null) index.samples = mutableListOf()
                        guides[itemId] = index
                    }
                }
            } else {
                tradePrices.forEach { (itemId, prices) ->
                    if (prices.isNotEmpty()) guides[itemId] = GeGuidePrice.migrate(prices, seedOf(itemId), clock())
                }
            }
            if (historyFile.exists()) {
                historyFile.bufferedReader().use { reader ->
                    val type = object : TypeToken<MutableMap<String, MutableList<GeHistoryEntry>>>() {}.type
                    val loaded: MutableMap<String, MutableList<GeHistoryEntry>>? = gson.fromJson(reader, type)
                    if (loaded != null) {
                        history.clear()
                        history.putAll(loaded)
                    }
                }
            }
        } catch (e: Exception) {
            // A corrupt save file must not block startup, but starting with an empty book means
            // every escrowed item and coin is gone - that must never happen silently.
            logger.error("Grand Exchange book could not be loaded from ${saveFile.absolutePath}; starting EMPTY. Restore the file before players trade.", e)
        } finally {
            lock.unlock()
        }
    }

    /*
     * The offer book is the only record of every escrowed item and coin; a truncated offers.json
     * would silently wipe all of it at the next boot (see load). Every file is replaced atomically.
     */
    private fun save() {
        /*
         * Audit T-11: every mutation used to rewrite these files synchronously on the game thread. The
         * book is now serialised here, under the lock, into an immutable snapshot, and only the disk
         * writes run on [BackgroundIo], whose single thread keeps the snapshots in submission order (the
         * last one written is the latest book). [asyncWrites] is switched on by [init], i.e. only in the
         * running server; a service built directly (tests, tools) keeps writing synchronously.
         */
        val snapshot =
            listOf(
                saveFile to gson.toJson(offers),
                priceFile to gson.toJson(tradePrices),
                guideFile to gson.toJson(guides),
                historyFile to gson.toJson(history),
            )
        val write = { snapshot.forEach { (file, json) -> AtomicFiles.writeText(file, json) } }
        if (asyncWrites) {
            BackgroundIo.submit("Grand Exchange book ${saveFile.absolutePath}", write)
        } else {
            write()
        }
    }

    /** Audit T-11: write the book on [BackgroundIo] instead of the calling (game) thread; see [save]. */
    @Volatile
    internal var asyncWrites = false

    /** The first free offer box of [username], or null when all [SLOTS] are in use. */
    fun freeSlot(username: String): Int? {
        lock.lock()
        try {
            return (0 until SLOTS).firstOrNull { slot -> offers.none { it.username == username && it.slot == slot } }
        } finally {
            lock.unlock()
        }
    }

    fun offerInSlot(
        username: String,
        slot: Int,
    ): GrandExchangeOffer? {
        lock.lock()
        try {
            return offers.firstOrNull { it.username == username && it.slot == slot }
        } finally {
            lock.unlock()
        }
    }

    fun ownerOf(offerId: Long): String? {
        lock.lock()
        try {
            return offers.firstOrNull { it.id == offerId }?.username
        } finally {
            lock.unlock()
        }
    }

    /** A snapshot of offer [offerId], or null when it no longer exists. */
    fun offer(offerId: Long): GrandExchangeOffer? {
        lock.lock()
        try {
            return offers.firstOrNull { it.id == offerId }?.copy()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Submits a new offer into [slot] (or the first free slot when [slot] is -1) and immediately attempts to match it
     * against the resting book (and, for a buy order, system liquidity). Matching mutates escrow directly on both
     * sides' [GrandExchangeOffer] objects, so the returned fills are informational (e.g. for messaging the other
     * party if online) rather than something the caller needs to apply.
     *
     * Returns null, changing nothing, when the requested slot is taken or the player has no free slot left; the
     * caller must then give back whatever it escrowed.
     */
    fun submit(
        username: String,
        type: OfferType,
        itemId: Int,
        pricePerItem: Int,
        quantity: Int,
        slot: Int = -1,
        escrowToken: String? = null,
    ): Pair<GrandExchangeOffer, List<GeFill>>? {
        lock.lock()
        try {
            val target = if (slot == -1) freeSlot(username) ?: return null else slot
            if (target !in 0 until SLOTS || offers.any { it.username == username && it.slot == target }) return null
            val offer =
                GrandExchangeOffer(
                    id = nextId.getAndIncrement(),
                    username = username,
                    type = type,
                    itemId = itemId,
                    pricePerItem = pricePerItem,
                    totalQuantity = quantity,
                    slot = target,
                    escrowToken = escrowToken,
                )
            offers.add(offer)
            val fills = GrandExchangeBook.match(offers, offer, buyLedger, ::taxPerItem, ::houseQuote)
            // Audit E-01: house fills never move the guide price.
            fills.filter { !it.fromSystem }.forEach { recordTrade(itemId, it) }
            save()
            return offer to fills
        } finally {
            lock.unlock()
        }
    }

    /** Audit E-08: true when [username] has an offer that was placed under pending-escrow marker [token]. */
    fun hasEscrow(
        username: String,
        token: String,
    ): Boolean {
        lock.lock()
        try {
            return offers.any { it.username == username && it.escrowToken == token }
        } finally {
            lock.unlock()
        }
    }

    /** How many more of [itemId] [username] may buy in the current buy-limit window (Int.MAX_VALUE: no limit). */
    fun buyAllowance(
        username: String,
        itemId: Int,
    ): Int {
        lock.lock()
        try {
            return buyLedger.remaining(username, itemId)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Cancels [username]'s active offer [offerId], moving any unfilled
     * quantity into escrow for collection (remaining coins for a buy order,
     * remaining stock for a sell order). Already-filled quantity is
     * unaffected - it was already escrowed as it filled.
     */
    fun cancel(
        username: String,
        offerId: Long,
    ): GrandExchangeOffer? {
        lock.lock()
        try {
            val offer = offers.find { it.id == offerId && it.username == username } ?: return null
            if (offer.status != OfferStatus.ACTIVE) return null
            if (offer.remaining > 0) {
                if (offer.type == OfferType.BUY) {
                    offer.collectableCoins += offer.remaining.toLong() * offer.pricePerItem
                } else {
                    offer.collectableItems += offer.remaining
                }
            }
            offer.status = OfferStatus.CANCELLED
            save()
            return offer
        } finally {
            lock.unlock()
        }
    }

    fun offersFor(username: String): List<GrandExchangeOffer> {
        lock.lock()
        try {
            return offers.filter { it.username == username }.sortedByDescending { it.createdAtMs }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Zeroes and returns the coins/items currently owed on [offerId], for
     * the caller to pay out. If delivery only partially succeeds (e.g. a
     * full inventory), call [restoreCollectable] with the leftover so it
     * isn't lost - it stays collectable for next time.
     */
    fun takeCollectable(
        username: String,
        offerId: Long,
    ): Pair<Long, Int>? {
        lock.lock()
        try {
            val offer = offers.find { it.id == offerId && it.username == username } ?: return null
            val coins = offer.collectableCoins
            val items = offer.collectableItems
            if (coins == 0L && items == 0) return null
            offer.collectableCoins = 0
            offer.collectableItems = 0
            save()
            return coins to items
        } finally {
            lock.unlock()
        }
    }

    /** Zeroes and returns one half of what [offerId] owes: its coins when [coins] is true, otherwise its items. */
    fun takePart(
        username: String,
        offerId: Long,
        coins: Boolean,
    ): Long {
        lock.lock()
        try {
            val offer = offers.find { it.id == offerId && it.username == username } ?: return 0
            val owed: Long
            if (coins) {
                owed = offer.collectableCoins
                offer.collectableCoins = 0
            } else {
                owed = offer.collectableItems.toLong()
                offer.collectableItems = 0
            }
            if (owed > 0) save()
            return owed
        } finally {
            lock.unlock()
        }
    }

    fun restoreCollectable(
        offerId: Long,
        coins: Long,
        items: Int,
    ) {
        lock.lock()
        try {
            val offer = offers.find { it.id == offerId } ?: return
            offer.collectableCoins += coins
            offer.collectableItems += items
            save()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Frees the offer box of [offerId] once the offer is no longer active and owes nothing, so a finished or aborted
     * offer never keeps a slot (and never disappears while something is still owed). Returns true when released.
     * An offer that traded anything is added to the owner's History first (Void `GrandExchangeCollection`).
     */
    fun releaseIfDrained(
        username: String,
        offerId: Long,
    ): Boolean {
        lock.lock()
        try {
            val offer = offers.find { it.id == offerId && it.username == username } ?: return false
            if (offer.status == OfferStatus.ACTIVE || offer.collectableCoins > 0 || offer.collectableItems > 0) return false
            if (offer.quantityFilled > 0) {
                val entries = history.getOrPut(username) { mutableListOf() }
                entries.add(0, GeHistoryEntry(offer.itemId, offer.quantityFilled, offer.coinsTraded, offer.type == OfferType.SELL))
                while (entries.size > HISTORY_ROWS) entries.removeAt(entries.size - 1)
            }
            offers.remove(offer)
            save()
            return true
        } finally {
            lock.unlock()
        }
    }

    /** [username]'s finished offers, newest first. */
    fun historyFor(username: String): List<GeHistoryEntry> {
        lock.lock()
        try {
            return history[username]?.toList() ?: emptyList()
        } finally {
            lock.unlock()
        }
    }

    /**
     * The guide price of [itemId] ([GeGuidePrice]): the median of recent trades between different accounts, moving at
     * most 5 % per hour, or [fallback] (the OSRS seed / cache item value) until enough such trades happened.
     */
    fun guidePrice(
        itemId: Int,
        fallback: Int,
    ): Int {
        lock.lock()
        try {
            return GeGuidePrice.guide(guides[itemId], fallback)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Offer prices are free (OSRS), but the guide price must not be steerable by two accounts trading at 1 gp or at
     * absurd prices - the house deals at the guide price. A trade therefore moves the guide by at most 5 % per trade.
     */
    fun guidedTradePrice(
        guide: Int?,
        unitPrice: Int,
    ): Int {
        if (guide == null) return unitPrice
        val range = GrandExchangeInterface.priceRange(guide)
        return unitPrice.coerceIn(range.first, range.last)
    }

    /** Audit E-01: feeds one fill between two different accounts into the guide ([GeGuidePrice.record]). */
    private fun recordTrade(
        itemId: Int,
        fill: GeFill,
    ) {
        val buyer = fill.buyOfferId?.let { id -> offers.firstOrNull { it.id == id }?.username } ?: return
        val seller = fill.sellOfferId?.let { id -> offers.firstOrNull { it.id == id }?.username } ?: return
        if (buyer == seller) return
        val seed = seedOf(itemId)
        val index = guides.getOrPut(itemId) { GeGuideIndex() }
        val now = clock()
        val sample = GeTradeSample(guidedTradePrice(if (index.price > 0) index.price else seed, fill.unitPrice), GeGuidePrice.pairKey(buyer, seller), now)
        GeGuidePrice.record(index, sample, seed, now)
        // Kept in the old format too, so guide_prices.json stays readable by an older build.
        tradePrices[itemId] = index.samples.map { it.price }.toMutableList()
    }

    companion object : KLogging() {
        /** The revision-667 Grand Exchange screen shows six offer boxes (interface 105). */
        const val SLOTS = 6
        const val PRICE_HISTORY = GeGuidePrice.SAMPLES

        /** Rows on the History screen (interface 643). */
        const val HISTORY_ROWS = GrandExchangeHistory.ROWS
    }
}
