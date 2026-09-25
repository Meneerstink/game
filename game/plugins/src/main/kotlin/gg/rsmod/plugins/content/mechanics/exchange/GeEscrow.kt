package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.persistNow
import mu.KLogging
import java.util.UUID

/**
 * Audit E-08: crash-safe order of the two saves when an offer is placed.
 *
 * Placing an offer moves coins or items out of the player's save and into the offer book (`data/ge/offers.json`), two
 * files written one after the other. The book used to be written first: a crash between the book write and the player
 * save left the escrow in the book *and* the coins/items in the player's save - a duplicate.
 *
 * The simplest correct order, used by every way of placing an offer ([place]):
 *  1. the inventory is debited (in memory);
 *  2. a pending marker ([PENDING]: a fresh token plus exactly what was debited) is set and the player is saved - the
 *     save now lacks the escrowed coins/items but says what to give back;
 *  3. the offer is submitted under that token ([GrandExchangeService.submit] stores it on the offer and writes the book);
 *  4. the marker is cleared and the player is saved again.
 *
 * A login reconciles a marker that is still set ([reconcile], run from [GrandExchangeInterface.onLogin] before anything
 * else can touch the book): when the book holds an offer with the token, the offer was placed and the marker is simply
 * dropped; otherwise the book write never happened and the debited coins/items are given back (inventory, then bank).
 * A crash at any point therefore ends in exactly one copy. The markers live in the player saves, which are only read at
 * login, so the reconcile runs per login rather than once at boot - a player cannot act before it.
 *
 * This relies on [GrandExchangeService.submit] having written the book when it returns (the write-through save).
 *
 * Collecting keeps its order (book first, then the inventory and a player save): a crash in that window can lose that
 * one payout but never duplicate it.
 */
object GeEscrow : KLogging() {
    val PENDING = AttributeKey<String>(persistenceKey = "ge_pending_escrow")

    fun newToken(): String = UUID.randomUUID().toString()

    /** `token;itemId:amount,itemId:amount` - strings survive the JSON save exactly (numbers come back as doubles). */
    fun encode(
        token: String,
        refund: List<Pair<Int, Int>>,
    ): String = token + ";" + refund.filter { it.second > 0 }.joinToString(",") { "${it.first}:${it.second}" }

    fun decode(raw: String): Pair<String, List<Pair<Int, Int>>>? {
        val parts = raw.split(";", limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) return null
        val items: List<Pair<Int, Int>> =
            if (parts[1].isBlank()) {
                emptyList()
            } else {
                parts[1].split(",").map { entry ->
                    val pair = entry.split(":")
                    if (pair.size != 2) return null
                    val id = pair[0].toIntOrNull() ?: return null
                    val amount = pair[1].toIntOrNull() ?: return null
                    if (id < 0 || amount <= 0) return null
                    id to amount
                }
            }
        return parts[0] to items
    }

    /** What a login must give back for marker [raw]: nothing when the offer reached the book ([inBook]). */
    fun owed(
        raw: String,
        inBook: (token: String) -> Boolean,
    ): List<Pair<Int, Int>>? {
        val (token, items) = decode(raw) ?: return null
        return if (inBook(token)) emptyList() else items
    }

    /**
     * Steps 2-4 of the class comment. The caller has already debited [refund] from the inventory; [restore] undoes that
     * debit when [submit] refuses the offer (returns null). [submit] receives the token to store on the offer.
     */
    fun <R : Any> place(
        player: Player,
        refund: List<Pair<Int, Int>>,
        restore: () -> Unit,
        submit: (token: String) -> R?,
    ): R? {
        val token = newToken()
        player.attr[PENDING] = encode(token, refund)
        player.persistNow()
        val result = submit(token)
        if (result == null) restore()
        player.attr.remove(PENDING)
        player.persistNow()
        return result
    }

    /** Settles a marker left by a crash; see the class comment. */
    fun reconcile(
        player: Player,
        service: GrandExchangeService,
        username: String,
    ) {
        val raw = player.attr[PENDING] ?: return
        val owed = owed(raw) { token -> service.hasEscrow(username, token) }
        if (owed == null) {
            logger.error { "Unreadable Grand Exchange escrow marker for $username: '$raw' - dropped, check the book by hand." }
            player.attr.remove(PENDING)
            player.persistNow()
            return
        }
        val undelivered = mutableListOf<Pair<Int, Int>>()
        owed.forEach { (itemId, amount) ->
            val toInventory = player.inventory.add(itemId, amount, assureFullInsertion = false).completed
            val toBank = if (toInventory < amount) player.bank.add(itemId, amount - toInventory, assureFullInsertion = false).completed else 0
            val left = amount - toInventory - toBank
            if (left > 0) undelivered.add(itemId to left)
        }
        if (owed.isNotEmpty()) {
            logger.warn { "Grand Exchange escrow of $username never reached the book (crash); returned $owed." }
        }
        if (undelivered.isEmpty()) {
            player.attr.remove(PENDING)
        } else {
            // Nothing fitted anywhere: keep what is still owed for the next login (the token is still not in the book).
            val token = decode(raw)!!.first
            player.attr[PENDING] = encode(token, undelivered)
            player.message("Some of your Grand Exchange coins or items could not be returned yet; free some space and log in again.")
        }
        player.persistNow()
    }
}
