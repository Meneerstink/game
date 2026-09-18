package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/** ClientProt OPLOCT (42): an interface target - a spell, or a familiar special move - used on an object. */
data class OpLocTMessage(
    val componentHash: Int,
    val componentSlot: Int,
    val item: Int,
    val x: Int,
    val z: Int,
    val id: Int,
    val movementType: Int,
) : Message