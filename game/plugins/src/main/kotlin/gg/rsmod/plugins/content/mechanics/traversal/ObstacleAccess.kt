package gg.rsmod.plugins.content.mechanics.traversal

import gg.rsmod.game.model.entity.Player

/**
 * Obstacles only a minigame team member may use (the Castle Wars and Soul Wars team barriers): the minigame registers how a
 * player becomes a member, so `conform traversal` tests the crossing as a member instead of judging the refusal of an outsider as
 * a dead click (sweep 2026-10-03 10:23: every team barrier FAIL "click did nothing"). The grant returns its own undo.
 */
object ObstacleAccess {
    private val grants = HashMap<Int, (Player) -> () -> Unit>()

    fun register(
        id: Int,
        grant: (Player) -> () -> Unit,
    ) {
        grants[id] = grant
    }

    /** Makes [player] a member for obstacle [id]; null when the obstacle has no membership rule. */
    fun grant(
        id: Int,
        player: Player,
    ): (() -> Unit)? = grants[id]?.invoke(player)
}
