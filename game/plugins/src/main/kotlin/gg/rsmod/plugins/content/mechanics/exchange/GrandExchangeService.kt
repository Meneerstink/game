package gg.rsmod.plugins.content.mechanics.exchange

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import gg.rsmod.game.Server
import gg.rsmod.game.model.World
import gg.rsmod.game.service.Service
import gg.rsmod.util.ServerProperties
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
    clock: () -> Long = System::currentTimeMillis,
) : Service {
    private val lock = ReentrantLock()
    private val offers = mutableListOf<GrandExchangeOffer>()
    private val nextId = AtomicLong(1)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val priceFile: File get() = File(saveFile.parentFile ?: File("."), "guide_prices.json")
    private val historyFile: File get() = File(saveFile.parentFile ?: File("."), "history.json")
    private val tradePrices = mutableMapOf<Int, MutableList<Int>>()
    private val history = mutableMapOf<String, MutableList<GeHistoryEntry>>()
    private val buyLedger = GeBuyLedger(clock)

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        GeBuyLimits.load()
        load()
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
        save()
    }

    fun load() {
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
            // A corrupt/missing save file should never block startup - the
            // book simply starts empty rather than crashing the server.
        } finally {
            lock.unlock()
        }
    }

    private fun save() {
        saveFile.parentFile?.mkdirs()
        saveFile.bufferedWriter().use { writer -> gson.toJson(offers, writer) }
        priceFile.bufferedWriter().use { writer -> gson.toJson(tradePrices, writer) }
        historyFile.bufferedWriter().use { writer -> gson.toJson(history, writer) }
    }

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
                )
            offers.add(offer)
            val fills = GrandExchangeBook.match(offers, offer, buyLedger)
            fills.forEach { recordTrade(itemId, it.unitPrice) }
            save()
            return offer to fills
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
     * The guide price of [itemId]: the average of its last [PRICE_HISTORY] executed trades, or [fallback] (the
     * cache item value, the same fallback Void's `ExchangeHistory.marketPrice` uses) before any trade happened.
     */
    fun guidePrice(
        itemId: Int,
        fallback: Int,
    ): Int {
        lock.lock()
        try {
            val history = tradePrices[itemId]
            val price = if (history.isNullOrEmpty()) fallback else (history.sumOf { it.toLong() } / history.size).toInt()
            return price.coerceAtLeast(1)
        } finally {
            lock.unlock()
        }
    }

    private fun recordTrade(
        itemId: Int,
        price: Int,
    ) {
        val history = tradePrices.getOrPut(itemId) { mutableListOf() }
        history.add(price)
        while (history.size > PRICE_HISTORY) history.removeAt(0)
    }

    companion object {
        /** The revision-667 Grand Exchange screen shows six offer boxes (interface 105). */
        const val SLOTS = 6
        const val PRICE_HISTORY = 20

        /** Rows on the History screen (interface 643). */
        const val HISTORY_ROWS = GrandExchangeHistory.ROWS
    }
}
