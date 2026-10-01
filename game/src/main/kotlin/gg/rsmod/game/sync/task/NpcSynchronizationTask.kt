package gg.rsmod.game.sync.task

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.sync.SynchronizationSegment
import gg.rsmod.game.sync.SynchronizationTask
import gg.rsmod.game.sync.segment.*
import gg.rsmod.net.packet.GamePacketBuilder
import gg.rsmod.net.packet.PacketType
import gg.rsmod.util.Misc

/**
 * @author Tom <rspsmods@gmail.com>
 */
class NpcSynchronizationTask(
    private val worldNpcs: Array<Npc?>,
) : SynchronizationTask<Player> {
    override fun run(pawn: Player) {
        val largeScene = pawn.hasLargeViewport()

        val opcode =
            if (!largeScene) {
                pawn.world.npcUpdateBlocks.updateOpcode
            } else {
                pawn.world.npcUpdateBlocks.largeSceneUpdateOpcode
            }

        val buf = GamePacketBuilder(opcode, PacketType.VARIABLE_SHORT)
        val maskBuf = GamePacketBuilder()

        buf.switchToBitAccess()

        val segments = getSegments(pawn)
        segments.forEach { segment ->
            segment.encode(if (segment is NpcUpdateBlockSegment) maskBuf else buf)
        }

        if (maskBuf.byteBuf.writerIndex() > 0) {
            buf.putBits(15, 0x7FFF)
        }

        buf.switchToByteAccess()

        buf.putBytes(maskBuf.byteBuf)
        pawn.write(buf.toGamePacket())
    }

    private fun getSegments(player: Player): List<SynchronizationSegment> {
        val segments = mutableListOf<SynchronizationSegment>()

        if (player.movedToInstance || player.movedFromInstance) {
            player.localNpcs.clear()
            segments.add(NpcCountSegment(0))
            return segments
        }

        val localNpcs = player.localNpcs
        val iterator = localNpcs.iterator()

        segments.add(NpcCountSegment(localNpcs.size))
        while (iterator.hasNext()) {
            val npc = iterator.next()
            // RCV-012 B3/B4: a moved (moveTo/teleportTo) npc was sent as update type 3, which the 667 client reads as
            // REMOVE (client NpcUpdate.REMOVE, Novite LocalNPCUpdate removes teleported npcs the same way) while this
            // list kept it, so every later npc entry in the packet was decoded against the wrong npc. Remove it on both
            // sides; `shouldAdd` re-adds it at its new tile next cycle.
            if (shouldRemove(player, npc) || npc.moved) {
                segments.add(RemoveLocalNpcSegment())
                iterator.remove()
                continue
            }
            npc.setActive(true)

            val requiresBlockUpdate = npc.blockBuffer.isDirty()

            if (npc.steps != null) {
                segments.add(NpcSkipSegment(skip = false))
                segments.add(
                    NpcWalkSegment(
                        Misc.getNpcMoveDirection(npc.steps!!.walkDirection!!.walkValue),
                        npc.steps!!.runDirection?.let { Misc.getNpcMoveDirection(it.walkValue) } ?: -1,
                        requiresBlockUpdate,
                    ),
                )
                if (requiresBlockUpdate) {
                    segments.add(NpcUpdateBlockSegment(npc, false))
                }
            } else if (requiresBlockUpdate) {
                segments.add(NpcSkipSegment(skip = false))
                segments.add(NpcNoMovementSegment())
                segments.add(NpcUpdateBlockSegment(npc, false))
            } else {
                segments.add(NpcSkipSegment(skip = true))
            }
        }

        var added = 0

        for (npc in worldNpcs) {
            if (added >= MAX_NPC_ADDITIONS_PER_CYCLE || player.localNpcs.size >= MAX_LOCAL_NPCS) {
                break
            }

            if (npc == null || !shouldAdd(player, npc) || player.localNpcs.contains(npc)) {
                continue
            }

            // Facing and a combat-level override are persistent state, not one-cycle events: the
            // block mask is wiped every cycle, so an npc that enters a local list on a cycle it is
            // not otherwise dirty (every moveTo'd npc - it is removed on the move cycle and re-added
            // on the next - and every npc a player walks up to) must still get its block segment,
            // or NpcUpdateBlockSegment's newAddition forcing never runs and it never turns to its
            // target (owner 2026-09-18: Deadman guards "do not face my way when attacking me").
            val requiresBlockUpdate = npc.blockBuffer.isDirty() || hasPersistentBlockState(npc.blockBuffer)
            segments.add(AddLocalNpcSegment(player, npc, requiresBlockUpdate, player.hasLargeViewport()))
            if (requiresBlockUpdate) {
                segments.add(NpcUpdateBlockSegment(npc, true))
            }

            added++
            player.localNpcs.add(npc)
        }

        if (gg.rsmod.game.model.BreachTrace.active()) traceViewer(player, segments)

        return segments
    }

    /**
     * Breach conformance trace: for every watched npc in view of [player], which update blocks this player receives this
     * cycle (animation, graphics, facing, hitsplats, forced chat), or why the npc is not in the player's list at all.
     */
    private fun traceViewer(
        player: Player,
        segments: List<SynchronizationSegment>,
    ) {
        val blocks = segments.filterIsInstance<NpcUpdateBlockSegment>().map { it.npc }.toHashSet()
        for (npc in worldNpcs) {
            if (npc == null || !gg.rsmod.game.model.BreachTrace.watches(npc.id)) continue
            if (npc.tile.height != player.tile.height || !npc.tile.isWithinRadius(player.tile, 16)) continue
            val b = npc.blockBuffer
            if (!player.localNpcs.contains(npc)) {
                gg.rsmod.game.model.BreachTrace.log("sync.notlocal") { "npc=${npc.id}#${npc.index} viewer=${player.username} dirty=${b.isDirty()} anim=${b.animation}" }
                continue
            }
            if (npc !in blocks) continue
            gg.rsmod.game.model.BreachTrace.log("sync.block") {
                val gfx = (0 until b.graphicCount).joinToString("/") { "${b.graphics[it].id}" }
                "npc=${npc.id}#${npc.index} viewer=${player.username} " +
                    "anim=${if (npc.hasBlock(gg.rsmod.game.sync.block.UpdateBlockType.ANIMATION)) b.animation else -1} " +
                    "gfx=${if (gfx.isEmpty()) "-" else gfx} face=${b.facePawnIndex} hits=${b.hits.size} " +
                    "chat=${if (npc.hasBlock(gg.rsmod.game.sync.block.UpdateBlockType.FORCE_CHAT)) b.forceChat else "-"}"
            }
        }
    }

    private fun shouldRemove(
        player: Player,
        npc: Npc,
    ): Boolean = !npc.isSpawned() || npc.invisible || !isWithinView(player, npc.tile)

    private fun shouldAdd(
        player: Player,
        npc: Npc,
    ): Boolean =
        npc.isSpawned() &&
            !npc.invisible &&
            !npc.moved &&
            isWithinView(player, npc.tile) &&
            (npc.owner == null || npc.owner == player || npc.publicOwner)

    /**
     * In view: within the view distance on the player's own floor or a floor below it. `Tile.isWithinRadius` also demands
     * the same height, so from an upper floor (the mansion's balconies) nobody on the ground was ever sent and the Grand
     * Exchange square looked empty (owner 2026-09-27, foto balc.png). The add segment carries the npc's floor, so the
     * client places them below; floors above stay out, the client hides those levels anyway.
     */
    private fun isWithinView(
        player: Player,
        tile: Tile,
    ): Boolean =
        tile.height <= player.tile.height &&
            Tile(tile.x, tile.z, player.tile.height).isWithinRadius(
                player.tile,
                if (player.hasLargeViewport()) Player.LARGE_VIEW_DISTANCE else Player.NORMAL_VIEW_DISTANCE,
            ) &&
            (tile.height == player.tile.height || !underFloor(player, tile))

    /**
     * Owner 2026-09-29 ("summoning monsters noclip on the mansion's first floor, probably because the ceiling is so low"):
     * a tall npc on a lower floor was drawn through the floor above it. A lower npc is only sent when it stands in the open:
     * not on a roofed tile (flag 4, inside a building) and with no floor (flag 4 on the upper storeys) above it up to the
     * viewer's floor. The balconies still see the open square below.
     */
    private fun underFloor(
        player: Player,
        tile: Tile,
    ): Boolean {
        val chunk = player.world.chunks.get(tile) ?: return false
        if (chunk.isRoofed(tile)) return true
        return (tile.height + 1..player.tile.height).any { h -> chunk.isRoofed(Tile(tile.x, tile.z, h)) }
    }

    companion object {
        private const val MAX_LOCAL_NPCS = 255
        private const val MAX_NPC_ADDITIONS_PER_CYCLE = 40

        /** State [NpcUpdateBlockSegment] re-sends to a player whose client only now learns of the npc. */
        fun hasPersistentBlockState(buffer: gg.rsmod.game.sync.block.UpdateBlockBuffer): Boolean =
            buffer.facePawnIndex != -1 ||
                buffer.faceDegrees != 0 ||
                buffer.combatLevel != gg.rsmod.game.sync.block.UpdateBlockBuffer.CACHE_COMBAT_LEVEL
    }
}
