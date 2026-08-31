package gg.rsmod.game

/**
 * @author Tom <rspsmods@gmail.com>
 */
data class DevContext(
    val debugExamines: Boolean,
    val debugObjects: Boolean,
    val debugButtons: Boolean,
    val debugItemActions: Boolean,
    val debugMagicSpells: Boolean,
    /**
     * Audit findings 6/7 (double-click cancellation, bank-distance root cause): when true,
     * logs the target/object id, tile, cancellation reason and handler result at every
     * choke point in [gg.rsmod.game.action.PawnPathAction] and
     * [gg.rsmod.game.action.ObjectPathAction] instead of failing silently. Off by default.
     */
    val debugInteractions: Boolean = false,
    /**
     * When true (default), the full NPC census (R04.2) is written to
     * ./npc_inventory.csv automatically once the world finishes loading —
     * no owner login required. See [gg.rsmod.game.model.npc.NpcCensus].
     */
    val debugNpcCensus: Boolean = true,
)
