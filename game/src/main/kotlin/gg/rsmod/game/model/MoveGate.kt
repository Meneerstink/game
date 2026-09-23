package gg.rsmod.game.model

import gg.rsmod.game.model.entity.Player

/**
 * Plugin-installed gates on player movement, the single place every walked step and every teleport passes.
 *
 * Owner 2026-09-23: a player leaving a Safe zone for a Dangerous one must confirm first, whatever moved them (walking
 * out of the Grand Exchange gate, a spell, a tab, jewellery, a portal, an npc, the POH ...). The teleport routes number
 * in the hundreds, but every one of them ends in [gg.rsmod.game.model.entity.Pawn.moveTo] and every walked step in
 * [MovementQueue.cycle], so the check lives there once instead of in each route.
 */
object MoveGate {
    /** Called before a player is moved to a tile (teleport); returns true when the move was held back. */
    @Volatile
    var teleport: ((Player, Tile) -> Boolean)? = null

    /** Called before a player walks or runs one step from the first tile to the second; true cancels the path. */
    @Volatile
    var step: ((Player, Tile, Tile) -> Boolean)? = null
}
