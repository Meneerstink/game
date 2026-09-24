package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/** Moves an interface component to [x],[y] inside its parent (667 IF_SETPOSITION). */
data class IfSetPositionMessage(
    val hash: Int,
    val x: Int,
    val y: Int,
) : Message
