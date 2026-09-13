package gg.rsmod.game.sync.segment

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.sync.SynchronizationSegment
import gg.rsmod.game.sync.block.GraphicBlock
import gg.rsmod.game.sync.block.UpdateBlockBuffer
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketBuilder

/**
 * @author Tom <rspsmods@gmail.com>
 */
class NpcUpdateBlockSegment(
    private val npc: Npc,
    private val newAddition: Boolean,
) : SynchronizationSegment {
    override fun encode(buf: GamePacketBuilder) {
        var mask = npc.blockBuffer.blockValue()
        val blocks = npc.world.npcUpdateBlocks

        var forceFacePawn = false
        var forceFaceTile = false
        var forceCombatLevel = false

        if (newAddition) {
            if (npc.blockBuffer.faceDegrees != 0) {
                mask = mask or blocks.updateBlocks[UpdateBlockType.FACE_TILE]!!.bit
                forceFaceTile = true
            } else if (npc.blockBuffer.facePawnIndex != -1) {
                mask = mask or blocks.updateBlocks[UpdateBlockType.FACE_PAWN]!!.bit
                forceFacePawn = true
            }
            /*
             * A combat-level override is a property of the npc, not an event, and the block mask
             * is cleared every cycle - so it has to be re-sent to each player the moment the npc
             * enters their local list, exactly as the facing blocks are. Without this only the
             * player who was watching on the cycle it was set would ever see the level.
             */
            if (npc.blockBuffer.combatLevel != UpdateBlockBuffer.CACHE_COMBAT_LEVEL) {
                mask = mask or blocks.updateBlocks[UpdateBlockType.COMBAT_LEVEL]!!.bit
                forceCombatLevel = true
            }
        }

        /*
         * The npc mask is up to three bytes, not one. `NPCList` reads one byte, then a second
         * only if 0x80 is set, then a third only if 0x8000 is set - so a block living above the
         * first byte is unreachable unless the markers below it are set as well.
         *
         * Only writing `mask and 0xFF` was safe while every implemented block fitted in the low
         * byte, but it silently truncates anything above it. COMBAT_LEVEL is 0x80000, in the
         * third byte, so both markers have to be raised for it to arrive at all.
         */
        if (mask > 0xFF) {
            mask = mask or 0x80
        }
        if (mask > 0xFFFF) {
            mask = mask or 0x8000
        }
        buf.put(DataType.BYTE, mask and 0xFF)
        if ((mask and 0x80) != 0) {
            buf.put(DataType.BYTE, (mask shr 8) and 0xFF)
        }
        if ((mask and 0x8000) != 0) {
            buf.put(DataType.BYTE, (mask shr 16) and 0xFF)
        }

        blocks.updateBlockOrder.forEach { blockType ->
            val force =
                when (blockType) {
                    UpdateBlockType.FACE_TILE -> forceFaceTile
                    UpdateBlockType.FACE_PAWN -> forceFacePawn
                    UpdateBlockType.COMBAT_LEVEL -> forceCombatLevel
                    else -> false
                }
            if (npc.hasBlock(blockType) || force) {
                write(buf, blockType)
            }
        }
    }

    private fun write(
        buf: GamePacketBuilder,
        blockType: UpdateBlockType,
    ) {
        val blocks = npc.world.npcUpdateBlocks

        when (blockType) {
            UpdateBlockType.FACE_PAWN -> {
                val structure = blocks.updateBlocks[blockType]!!.values
                buf.put(
                    structure[0].type,
                    structure[0].order,
                    structure[0].transformation,
                    npc.blockBuffer.facePawnIndex,
                )
            }

            UpdateBlockType.FACE_TILE -> {
                val structure = blocks.updateBlocks[blockType]!!.values
                val x = npc.blockBuffer.faceDegrees shr 16
                val z = npc.blockBuffer.faceDegrees and 0xFFFF
                buf.put(structure[0].type, structure[0].order, structure[0].transformation, x)
                buf.put(structure[1].type, structure[1].order, structure[1].transformation, z)
            }

            UpdateBlockType.ANIMATION -> {
                val structure = blocks.updateBlocks[blockType]!!.values
                for (i in 0..3) {
                    buf.put(
                        structure[0].type,
                        structure[0].order,
                        structure[0].transformation,
                        npc.blockBuffer.animation,
                    )
                }
                buf.put(
                    structure[4].type,
                    structure[4].order,
                    structure[4].transformation,
                    npc.blockBuffer.animationDelay,
                )
            }

            UpdateBlockType.APPEARANCE -> {
                val structure = blocks.updateBlocks[blockType]!!.values
                buf.put(structure[0].type, structure[0].order, structure[0].transformation, npc.getTransmogId())
            }

            UpdateBlockType.COMBAT_LEVEL -> {
                val structure = blocks.updateBlocks[blockType]!!.values
                buf.put(
                    structure[0].type,
                    structure[0].order,
                    structure[0].transformation,
                    npc.blockBuffer.combatLevel,
                )
            }

            UpdateBlockType.GFX, UpdateBlockType.GFX_2, UpdateBlockType.GFX_3, UpdateBlockType.GFX_4 -> {
                GraphicBlock.write(buf, blocks.updateBlocks[blockType]!!, npc.blockBuffer.graphics[GraphicBlock.slotOf(blockType)])
            }

            UpdateBlockType.FORCE_CHAT -> {
                buf.putString(npc.blockBuffer.forceChat)
            }

            UpdateBlockType.HITMARK -> {
                val structure = blocks.updateBlocks[blockType]!!.values

                val hitmarkCountStructure = structure[0]
                val hitbarPercentageStructure = structure[1]

                val hits = npc.blockBuffer.hits

                buf.put(
                    hitmarkCountStructure.type,
                    hitmarkCountStructure.order,
                    hitmarkCountStructure.transformation,
                    hits.size,
                )
                hits.forEach { hit ->
                    val hitmarks = Math.min(2, hit.hitmarks.size)

                    /*
                     * Inform the client of how many hitmarkers to decode.
                     */
                    if (hitmarks == 0) {
                        buf.putSmart(32766)
                    }

                    for (i in 0 until hitmarks) {
                        val hitmark = hit.hitmarks[i]
                        buf.putSmart(hitmark.type)
                        buf.putSmart(hitmark.damage)
                    }

                    buf.putSmart(hit.clientDelay)
                    val max: Int = npc.getMaximumLifepoints()
                    var percentage = 0
                    if (max > 0) {
                        percentage =
                            if (max < npc.getCurrentLifepoints()) {
                                255
                            } else {
                                npc.getCurrentLifepoints() * 255 / max
                            }
                    }
                    buf.put(
                        hitbarPercentageStructure.type,
                        hitbarPercentageStructure.order,
                        hitbarPercentageStructure.transformation,
                        percentage,
                    )
                }
            }

            else -> throw RuntimeException("Unhandled update block type: $blockType")
        }
    }
}
