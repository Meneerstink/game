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
     * When true (default), the full NPC census (R04.2) is written to
     * ./npc_inventory.csv automatically once the world finishes loading —
     * no owner login required. See [gg.rsmod.game.model.npc.NpcCensus].
     */
    val debugNpcCensus: Boolean = true,
)
