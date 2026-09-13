package gg.rsmod.game.message.encoder

import gg.rsmod.game.message.MessageEncoder
import gg.rsmod.game.message.impl.UpdateStockmarketSlotMessage

class UpdateStockmarketSlotEncoder : MessageEncoder<UpdateStockmarketSlotMessage>() {
    override fun extract(
        message: UpdateStockmarketSlotMessage,
        key: String,
    ): Number =
        when (key) {
            "slot" -> message.slot
            "status" -> message.status
            "item" -> message.item
            "price" -> message.price
            "count" -> message.count
            "completed_count" -> message.completedCount
            "completed_gold" -> message.completedGold
            else -> throw Exception("Unhandled value key.")
        }

    override fun extractBytes(
        message: UpdateStockmarketSlotMessage,
        key: String,
    ): ByteArray = throw Exception("Unhandled value key.")
}
