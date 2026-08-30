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
 */
class GrandExchangeService : Service {
    private val lock = ReentrantLock()
    private val offers = mutableListOf<GrandExchangeOffer>()
    private val nextId = AtomicLong(1)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val saveFile = File("data/ge/offers.json")

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
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

    private fun load() {
        if (!saveFile.exists()) return
        lock.lock()
        try {
            saveFile.bufferedReader().use { reader ->
                val type = object : TypeToken<MutableList<GrandExchangeOffer>>() {}.type
                val loaded: MutableList<GrandExchangeOffer>? = gson.fromJson(reader, type)
                if (loaded != null) {
                    offers.clear()
                    offers.addAll(loaded)
                    nextId.set((offers.maxOfOrNull { it.id } ?: 0L) + 1)
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
    }

    /**
     * Submits a new offer and immediately attempts to match it against the
     * resting book (and, for a buy order, system liquidity). Matching
     * mutates escrow directly on both sides' [GrandExchangeOffer] objects, so
     * the returned fills are informational (e.g. for messaging the other
     * party if online) rather than something the caller needs to apply.
     */
    fun submit(
        username: String,
        type: OfferType,
        itemId: Int,
        pricePerItem: Int,
        quantity: Int,
    ): Pair<GrandExchangeOffer, List<GeFill>> {
        lock.lock()
        try {
            val offer =
                GrandExchangeOffer(
                    id = nextId.getAndIncrement(),
                    username = username,
                    type = type,
                    itemId = itemId,
                    pricePerItem = pricePerItem,
                    totalQuantity = quantity,
                )
            offers.add(offer)
            val fills = GrandExchangeBook.match(offers, offer)
            save()
            return offer to fills
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
}
