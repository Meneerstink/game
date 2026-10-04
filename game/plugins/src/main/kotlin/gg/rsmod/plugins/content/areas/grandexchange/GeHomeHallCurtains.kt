package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.message.impl.LocAddChangeMessage
import gg.rsmod.game.message.impl.LocDelMessage
import gg.rsmod.game.message.impl.UpdateZonePartialFollowsMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.RoyalHallLocTool
import gg.rsmod.game.tools.importer.RoyalHallMapTool

/**
 * The Royal Hall's facade over each balcony, drawn per player while they stand inside a lower storey.
 *
 * Owner 2026-09-29: from inside no balcony may show ("op de begane grond zie je alle balkons dit hoort niet") and no hole
 * either ("zodra je binnen ... draait de camera dan zie je het balkon als gat, op alle etages"). A balcony and its wall-ring
 * row hide with the roof from inside a lower storey (RoyalHallMapTool.BELOW_BUILDING, client roof flood), which opened the
 * facade there. The roof flood never hides level 0, so this sends that player - and only that player - the facade of each
 * hidden row as level-0 locs lifted to its storey (RoyalHallLocTool.CURTAIN_1 / CURTAIN_2, not solid): standing inside the
 * ground floor, both upper storeys' rows; inside level 1, the level-2 rows; anywhere else (outside, on a balcony, on the
 * storey itself) none, so the doorway there is open.
 *
 * Why the holes stayed after the 2026-09-29 fix (owner B5/C5/E15/E20/F11, ROOT_CAUSES #154): the client keeps one changed
 * loc per tile and layer, and both storeys' pieces were centrepieces on the same doorway tiles - each replaced or deleted
 * the other ([Piece.type]); and the server's own zone refresh on a plane change to level 0 (PlayerPostSynchronizationTask,
 * UPDATE_ZONE_FULL_FOLLOWS on the player's level) wiped every piece while this object still counted them as shown
 * ([update]).
 */
object GeHomeHallCurtains {
    /**
     * One facade piece: [tile] on level 0, facing like the storey wall of that ring side, closing the row of [storey].
     * [type] keeps the two storeys in different client loc layers (RoyalHallLocTool.CURTAIN_1_SHAPE / CURTAIN_2_SHAPE).
     */
    data class Piece(val tile: Tile, val rot: Int, val storey: Int) {
        val id: Int get() = if (storey == 1) RoyalHallLocTool.CURTAIN_1 else RoyalHallLocTool.CURTAIN_2
        val type: Int get() = if (storey == 1) RoyalHallLocTool.CURTAIN_1_SHAPE else RoyalHallLocTool.CURTAIN_2_SHAPE
    }

    private val MIN_X = RoyalHallMapTool.FLOOR_MIN_X - 1
    private val MAX_X = RoyalHallMapTool.FLOOR_MAX_X + 1
    private val MIN_Z = RoyalHallMapTool.FLOOR_MIN_Z - 1
    private val MAX_Z = RoyalHallMapTool.FLOOR_MAX_Z + 1

    /** The storey walls' rotation per ring side (home_decor.txt): west 2, east 0, south 1, north 3. */
    private fun rotation(x: Int, z: Int) =
        when {
            x == MIN_X -> 2
            x == MAX_X -> 0
            z == MIN_Z -> 1
            else -> 3
        }

    /** Every wall-ring tile of every balcony, per storey. */
    val PIECES: List<Piece> =
        (RoyalHallMapTool.BALCONIES + RoyalHallMapTool.BALCONIES_2).flatMap { b ->
            b.xs.flatMap { x -> b.zs.map { z -> x to z } }
                .filter { (x, z) -> x in MIN_X..MAX_X && z in MIN_Z..MAX_Z && (x == MIN_X || x == MAX_X || z == MIN_Z || z == MAX_Z) }
                .map { (x, z) -> Piece(Tile(x, z, 0), rotation(x, z), b.level) }
        }

    /** The storeys whose facade rows [player] must see closed: those above their storey while they stand inside. */
    fun wanted(t: Tile): Int {
        val inside = t.x in RoyalHallMapTool.FLOOR_MIN_X..RoyalHallMapTool.FLOOR_MAX_X && t.z in RoyalHallMapTool.FLOOR_MIN_Z..RoyalHallMapTool.FLOOR_MAX_Z
        if (!inside) return 0
        return (1..2).filter { it > t.height }.fold(0) { mask, storey -> mask or (1 shl storey) }
    }

    /**
     * Whether the client has lost every piece since the last [update]: a map rebuild (a new base object) drops every loc
     * change, and the full zone refresh the server sends on a plane change (PlayerPostSynchronizationTask) clears the loc
     * changes of the player's NEW level only (client UPDATE_ZONE_FULL_FOLLOWS) - so arriving on level 0 (down the stairs, a
     * teleport, a login) wipes the pieces, while going up leaves them. [sentHeight] null = nothing sent yet.
     */
    fun clientLostPieces(
        sentBase: Any?,
        base: Any,
        sentHeight: Int?,
        height: Int,
    ): Boolean = sentBase !== base || (height == 0 && sentHeight != 0)

    /**
     * The pieces to send to go from [shown] to [want] (storey bit masks): true adds, false deletes. [readd] sends every
     * wanted piece again although the client should hold it (the tick after a loss, in case the zone refresh reached the
     * client after the first resend).
     */
    fun changes(
        want: Int,
        shown: Int,
        readd: Boolean,
    ): List<Pair<Piece, Boolean>> =
        PIECES.mapNotNull { piece ->
            val bit = 1 shl piece.storey
            val on = want and bit != 0
            val was = shown and bit != 0
            when {
                on && (readd || !was) -> piece to true
                !on && was -> piece to false
                else -> null
            }
        }

    private val SENT = AttributeKey<Int>()
    private val SENT_BASE = AttributeKey<Any>()
    private val SENT_HEIGHT = AttributeKey<Int>()
    private val READD = AttributeKey<Boolean>()

    /**
     * Brings [player]'s client in line with [wanted]; sends only on a change, after the client lost the pieces
     * ([clientLostPieces]) and once more on the tick after such a loss. Runs from the world timer (after the tick's
     * synchronization, so after any zone refresh of the same tick).
     */
    fun update(player: Player) {
        val base = player.lastKnownRegionBase
        val want = wanted(player.tile)
        if (base == null) {
            if (want != 0) Trace.problem(Trace.Cat.PLUGIN) { "hall facade not sent to ${player.username} at ${player.tile}: no map base yet" }
            return
        }
        val height = player.tile.height
        val lost = clientLostPieces(player.attr[SENT_BASE], base, player.attr[SENT_HEIGHT], height)
        val readd = lost || player.attr[READD] == true
        val shown = if (lost) 0 else player.attr[SENT] ?: 0
        player.attr[SENT_BASE] = base
        player.attr[SENT_HEIGHT] = height
        player.attr[READD] = lost
        if (want == shown && (!readd || want == 0)) {
            player.attr[SENT] = want
            return
        }
        changes(want, shown, readd).forEach { (piece, add) ->
            val local = base.toLocal(piece.tile)
            player.write(UpdateZonePartialFollowsMessage(local.x shr 3, local.z shr 3, 0))
            val settings = (piece.type shl 2) or piece.rot
            val packed = (piece.tile.chunkOffsetX shl 4) or piece.tile.chunkOffsetZ
            player.write(if (add) LocAddChangeMessage(piece.id, settings, packed) else LocDelMessage(settings, packed))
        }
        player.attr[SENT] = want
    }
}
