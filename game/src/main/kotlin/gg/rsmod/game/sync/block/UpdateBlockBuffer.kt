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

    /**
     * The 667 client keeps four independent spot anims per entity per tick (SPOTANIM0..3). One
     * slot used to exist, so a second `graphic()` in the same tick silently replaced the first.
     */
    val graphics = Array(GRAPHIC_SLOTS) { GraphicSlot() }

    var graphicCount = 0
        private set

    class GraphicSlot {
        var id = 0
        var height = 0
        var delay = 0
        var rotation = 0

        fun matches(
            id: Int,
            height: Int,
            delay: Int,
            rotation: Int,
        ): Boolean = this.id == id && this.height == height && this.delay == delay && this.rotation == rotation
    }

    /**
     * Novite `Entity.setNextGraphics`: an identical graphic already queued this tick is ignored,
     * otherwise the first free slot is used and the fourth is overwritten when all are taken.
     *
     * @return the slot index written, or -1 when the graphic was a duplicate.
     */
    fun putGraphic(
        id: Int,
        height: Int,
        delay: Int,
        rotation: Int,
    ): Int {
        for (i in 0 until graphicCount) {
            if (graphics[i].matches(id, height, delay, rotation)) {
                return -1
            }
        }
        val slot = if (graphicCount < GRAPHIC_SLOTS) graphicCount++ else GRAPHIC_SLOTS - 1
        graphics[slot].also {
            it.id = id
            it.height = height
            it.delay = delay
            it.rotation = rotation
        }
        return slot
    }

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
        graphicCount = 0
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

        const val GRAPHIC_SLOTS = 4
    }
}
