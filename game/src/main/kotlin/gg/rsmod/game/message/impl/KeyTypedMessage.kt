package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * @author Alycia <https://github.com/alycii>
 */
data class KeyTypedMessage(
    val keycode: Int,
    /** The keys the client batched after [keycode] in the same packet, in order (client: n x (p1 key, p2 delay)). */
    val following: List<Int> = emptyList(),
) : Message
