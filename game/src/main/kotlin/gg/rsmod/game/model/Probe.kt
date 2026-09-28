package gg.rsmod.game.model

import gg.rsmod.game.message.Message
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.game.sync.block.UpdateBlockType

/**
 * Conformance suite (P8, docs\conformance_catalogue.md): the one place where everything that leaves the server is seen while a
 * recorder is attached - every packet written to a player, every pawn's update blocks at the end of the cycle (what the sync
 * sent), every projectile and area sound spawned. With no recorder attached each hook is one volatile read.
 */
object Probe {
    interface Recorder {
        /** A packet written to [player] (sounds, varps, interfaces, inventories, messages, ... - every type). */
        fun message(
            player: Player,
            message: Message,
        )

        /** [pawn]'s update blocks of this cycle, called before they are cleaned (post-sync), plus its state at that moment. */
        fun endOfCycle(pawn: Pawn)

        fun projectile(projectile: Projectile)

        fun areaSound(sound: AreaSound)
    }

    @Volatile
    var recorder: Recorder? = null

    /** Engine-internal state of [pawn] the recorder cannot reach itself: queued tasks, planned path, lock, facing. */
    fun internals(pawn: Pawn): Map<String, String> =
        linkedMapOf(
            "queues" to pawn.queues.describe().joinToString(" "),
            "path" to pawn.movementQueue.peekSteps(30).joinToString(" ") { "${it.tile.x},${it.tile.z}:${it.type.name.lowercase()}" },
            "lock" to pawn.lock.name,
            "facing" to pawn.lastFacingDirection.name,
        )

    /**
     * Everything [pawn] sends this cycle, one entry per block, plus its true (server) tile and the steps it took: the block
     * buffer is internal to the engine, so this reads it for the recorder. Order is stable (block type order).
     */
    fun blocks(pawn: Pawn): List<String> {
        val b = pawn.blockBuffer
        val out = ArrayList<String>(4)
        pawn.steps?.let { s -> out += "step walk=${s.walkDirection ?: "-"} run=${s.runDirection ?: "-"}" }
        if (b.teleport) out += "teleport"
        for (type in UpdateBlockType.values()) {
            if (!pawn.hasBlock(type)) continue
            out +=
                when (type) {
                    UpdateBlockType.ANIMATION -> "anim id=${b.animation} delay=${b.animationDelay}${if (b.idleOnly) " idleOnly" else ""}"
                    UpdateBlockType.GFX, UpdateBlockType.GFX_2, UpdateBlockType.GFX_3, UpdateBlockType.GFX_4 -> {
                        val slot = listOf(UpdateBlockType.GFX, UpdateBlockType.GFX_2, UpdateBlockType.GFX_3, UpdateBlockType.GFX_4).indexOf(type)
                        val g = b.graphics[slot]
                        "gfx id=${g.id} height=${g.height} delay=${g.delay} rot=${g.rotation} slot=$slot"
                    }
                    UpdateBlockType.HITMARK ->
                        "hits " +
                            b.hits.joinToString(";") { h ->
                                h.hitmarks.joinToString(",") { "type=${it.type} dmg=${it.damage}" } +
                                    " clientDelay=${h.clientDelay}" + (h.hitbar?.let { " bar=${it.percentage}/${it.maxPercentage} deplete=${it.depleteSpeed} delay=${it.delay}" } ?: "")
                            }
                    UpdateBlockType.FACE_PAWN -> "face pawn=${b.facePawnIndex}"
                    UpdateBlockType.FACE_TILE -> "face degrees=${b.faceDegrees}"
                    UpdateBlockType.FORCE_CHAT -> "forcechat \"${b.forceChat}\""
                    UpdateBlockType.FORCE_MOVEMENT ->
                        b.forceMovement.let { f -> "forcemove to=${f.destinations.joinToString("->") { "${it.x},${it.z}" }} dur=${f.clientDuration1}/${f.clientDuration2} dir=${f.directionAngle}" }
                    UpdateBlockType.COMBAT_LEVEL -> "combatlevel ${b.combatLevel}"
                    else -> type.name.lowercase()
                }
        }
        return out
    }
}
