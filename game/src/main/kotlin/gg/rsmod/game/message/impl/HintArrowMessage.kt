package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * HINT_ARROW (server prot 81, 12 bytes, client `ServerConnectionReader`): a hint arrow over an npc or player, or the removal
 * of the arrow in [slot]. For an npc arrow [target] is the npc's index; [flashRate] 0 keeps it steady; [model] -1 draws the
 * default arrow.
 */
data class HintArrowMessage(
    val slot: Int,
    val type: Int,
    val sprite: Int = 0,
    val target: Int = 0,
    val flashRate: Int = 0,
    val model: Int = -1,
) : Message {
    companion object {
        const val TYPE_CLEAR = 0
        const val TYPE_NPC = 1
        const val TYPE_PLAYER = 10
    }
}
