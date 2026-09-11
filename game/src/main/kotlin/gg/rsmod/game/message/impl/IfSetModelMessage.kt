package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * Sets a model on an interface component (667 opcode 58; Novite donor DefaultGameEncoder
 * sendIComponentModel: IntV1 hash, Short128 model).
 */
data class IfSetModelMessage(
    val hash: Int,
    val model: Int,
) : Message
