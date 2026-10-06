package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message
import gg.rsmod.game.model.entity.LocFit

/**
 * LOC_CUSTOMISE (667 client opcode 99, zone prot 2): the client's per-instance loc customisation. The client reads
 * shape/rotation, a flag byte, the zone-local tile and the loc id; flags 0x2/0x4/0x8 carry model / colour / texture
 * replacements (the 727 form Darkan sends as CustomizeObject), flag 0x1 resets the instance. The RSPS client adds flag
 * [FLAG_FIT]: five shorts with a [LocFit] (dx, dy, dz, scale, yaw) for that one instance.
 */
data class LocCustomiseMessage(
    val id: Int,
    val settings: Int,
    val tile: Int,
    val fit: LocFit?,
) : Message {
    val flags: Int get() = if (fit == null) FLAG_RESET else FLAG_FIT

    companion object {
        const val FLAG_RESET = 0x1
        const val FLAG_FIT = 0x10
    }
}
