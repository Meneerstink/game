package gg.rsmod.game.sync.block

import gg.rsmod.game.model.ChatMessage
import gg.rsmod.game.model.ForcedMovement
import gg.rsmod.game.model.Hit

/**
 * @author Tom <rspsmods@gmail.com>
 */
class UpdateBlockBuffer {
    internal var teleport = false
    private var mask = 0

    var forceChat = ""
    lateinit var publicChat: ChatMessage

    var faceDegrees = 0
    var facePawnIndex = -1

    var animation = 0
    var animationDelay = 0
    var idleOnly = false

    var graphicId = 0
    var graphicHeight = 0
    var graphicDelay = 0
    var graphicRotation = 0

    /**
     * NPC only. The combat level to advertise instead of the one baked in the client's own
     * `NPCType`. `NPCList` treats 65535 as "use the cache value", so that is the neutral value.
     */
    var combatLevel = CACHE_COMBAT_LEVEL

    lateinit var forceMovement: ForcedMovement

    val hits = mutableListOf<Hit>()

    fun isDirty(): Boolean = mask != 0

    fun clean() {
        mask = 0
        teleport = false
        hits.clear()
    }

    fun addBit(bit: Int) {
        mask = mask or bit
    }

    fun hasBit(bit: Int): Boolean {
        return (mask and bit) != 0
    }

    fun blockValue(): Int = mask

    companion object {
        /** The value `NPCList` reads as "fall back to `npc.type.combatLevel`". */
        const val CACHE_COMBAT_LEVEL = 65535
    }
}
