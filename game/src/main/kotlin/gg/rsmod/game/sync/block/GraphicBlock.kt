package gg.rsmod.game.sync.block

import gg.rsmod.net.packet.GamePacketBuilder

/**
 * The four spot-anim (graphic) update blocks, shared by the player and npc encoders.
 *
 * Index = client slot: 667 `PlayerList`/`NPCList` decode SPOTANIM0..3 into `setSpotAnim(0..3)`.
 * Each slot has its own mask bit and byte layout in `data/blocks.yml`; every slot writes the same
 * three values - id, `delay | height << 16`, and the data byte (rotation in the low 3 bits), as
 * Novite `Graphics.getSettingsHash`/`getSettings2Hash`.
 */
object GraphicBlock {
    val SLOTS = listOf(UpdateBlockType.GFX, UpdateBlockType.GFX_2, UpdateBlockType.GFX_3, UpdateBlockType.GFX_4)

    fun slotOf(type: UpdateBlockType): Int = SLOTS.indexOf(type)

    fun write(
        buf: GamePacketBuilder,
        structure: UpdateBlockStructure,
        slot: UpdateBlockBuffer.GraphicSlot,
    ) {
        val values = structure.values
        buf.put(values[0].type, values[0].order, values[0].transformation, slot.id)
        buf.put(values[1].type, values[1].order, values[1].transformation, (slot.delay and 0xffff) or (slot.height shl 16))
        buf.put(values[2].type, values[2].order, values[2].transformation, slot.rotation and 0x7)
    }
}
