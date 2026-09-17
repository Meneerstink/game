package gg.rsmod.plugins.content.mechanics.trading

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DEATH_FLAG
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.content.mechanics.trading.impl.TradeSession

/**
 * An attribute that represents a trade session between two players
 */
val TRADE_SESSION_ATTR = AttributeKey<TradeSession>()

/**
 * An attribute that represents if a player has accepted the trade
 */
val TRADE_ACCEPTED_ATTR = AttributeKey<Boolean>()

/**
 * The attribute holding the set of players who have recently requested a trade
 * with the player
 */
val TRADE_REQUESTS = AttributeKey<HashMap<Player, Int>>()

/** A trade request is only answerable for this many ticks (60 s); after that it is forgotten. */
const val TRADE_REQUEST_TICKS = 100

/** At most this many distinct open requests are kept per player; the oldest is dropped beyond it. */
const val TRADE_REQUEST_CAPACITY = 10

/**
 * If the [Player] has a [TradeSession]
 */
fun Player.hasTradeSession() = this.attr.has(TRADE_SESSION_ATTR)

/**
 * Gets the [TradeSession] instance for a player
 */
fun Player.getTradeSession(): TradeSession? = this.attr[TRADE_SESSION_ATTR]

/**
 * If the [Player] has accepted a trade session
 */
fun Player.hasAcceptedTrade(): Boolean = this.attr[TRADE_ACCEPTED_ATTR] ?: false

/**
 * Removes the [TradeSession] instance from a [Player]
 */
fun Player.removeTradeSession() {
    this.attr.remove(TRADE_SESSION_ATTR)
    this.attr.remove(TRADE_ACCEPTED_ATTR)
    closeInterface(InterfaceDestination.MAIN_SCREEN)
    closeInterface(TradeSession.OVERLAY_INTERFACE)
    // A trade ended by death must not release the death sequence's own lock.
    if (attr[DEATH_FLAG] != true) {
        this.unlock()
    }
}

/**
 * Gets the set of trade requests for a [Player]
 */
fun Player.getTradeRequests(): HashMap<Player, Int> =
    attr[TRADE_REQUESTS] ?: HashMap<Player, Int>().also { attr[TRADE_REQUESTS] = it }

/**
 * Drops requests that expired or whose sender is gone. Requests hold a strong reference to the
 * requesting player, so without this a logged-out player stayed reachable (and answerable) for as
 * long as the target stayed online.
 */
private fun Player.purgeTradeRequests(): HashMap<Player, Int> {
    val requests = getTradeRequests()
    val now = world.currentCycle
    requests.entries.removeIf { (from, at) -> !from.isOnline || now - at > TRADE_REQUEST_TICKS || now < at }
    return requests
}

/** Records that [from] asked this player to trade, keeping only the newest [TRADE_REQUEST_CAPACITY]. */
fun Player.addTradeRequest(from: Player) {
    val requests = purgeTradeRequests()
    requests[from] = world.currentCycle
    while (requests.size > TRADE_REQUEST_CAPACITY) {
        val oldest = requests.minByOrNull { it.value }?.key ?: break
        requests.remove(oldest)
    }
}

/** True when [from] has an unexpired request open with this player. */
fun Player.hasTradeRequestFrom(from: Player): Boolean = purgeTradeRequests().containsKey(from)

fun Player.removeTradeRequest(from: Player) {
    getTradeRequests().remove(from)
}
