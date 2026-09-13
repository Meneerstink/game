package gg.rsmod.game

/**
 * @author Tom <rspsmods@gmail.com>
 */
data class DevContext(
    /**
     * These six flags were boot-time-only (`val`, set from config and never touched again)
     * until the Crown of Helios "Interface Inspector" / "Entity Inspector" dev-tool menu
     * (2026-09-11) needed to flip them live from an admin-only in-game menu instead of editing
     * config and restarting - `var` so `world.devContext.debugButtons = true` works at runtime.
     * The console output they gate (button component/option/slot/item, unhandled item/object/
     * spell actions, object id/rot/transform) is unchanged; this only adds a runtime switch.
     */
    var debugExamines: Boolean,
    var debugObjects: Boolean,
    var debugButtons: Boolean,
    var debugItemActions: Boolean,
    var debugMagicSpells: Boolean,
    /**
     * Audit findings 6/7 (double-click cancellation, bank-distance root cause): when true,
     * logs the target/object id, tile, cancellation reason and handler result at every
     * choke point in [gg.rsmod.game.action.PawnPathAction] and
     * [gg.rsmod.game.action.ObjectPathAction] instead of failing silently. Off by default.
     */
    var debugInteractions: Boolean = false,
    /**
     * When true (default), the full NPC census (R04.2) is written to
     * ./npc_inventory.csv automatically once the world finishes loading —
     * no owner login required. See [gg.rsmod.game.model.npc.NpcCensus].
     */
    val debugNpcCensus: Boolean = true,
)
