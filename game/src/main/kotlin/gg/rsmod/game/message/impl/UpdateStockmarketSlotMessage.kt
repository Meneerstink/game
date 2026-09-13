package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * Revision-667 `UPDATE_STOCKMARKET_SLOT` (server prot 61, 20 bytes): one Grand Exchange offer box.
 *
 * Layout from the client's own `StockmarketOffer(Packet)`: status (g1b), obj (g2), price, count, completedCount and
 * completedGold (g4 each), preceded by the slot byte read in `ServerConnectionReader`. The client reads [status] as
 * `type = status & 0x8` (8 = sell) and `state = status & 0x7` (0 empty, 1 adding, 2 stable, 5 finished).
 */
class UpdateStockmarketSlotMessage(
    val slot: Int,
    val status: Int,
    val item: Int,
    val price: Int,
    val count: Int,
    val completedCount: Int,
    val completedGold: Int,
) : Message
