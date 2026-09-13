package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped

/**
 * Void Knight set detection shared by the three combat formulas (OSRS Wiki "Void Knight equipment"):
 * one style helmet plus top, robe and gloves. Elite bonuses (ranged damage 12.5 %, magic damage +5 %)
 * need the elite top and elite robe. Elite pieces also count for the normal set effect.
 */
object VoidKnight {
    val MELEE_HELMS = intArrayOf(Items.VOID_MELEE_HELM, Items.VOID_MELEE_HELM_11676)
    val RANGER_HELMS = intArrayOf(Items.VOID_RANGER_HELM, Items.VOID_RANGER_HELM_11675)
    val MAGE_HELMS = intArrayOf(Items.VOID_MAGE_HELM, Items.VOID_MAGE_HELM_11674)

    private val ELITE_TOPS =
        intArrayOf(Items.ELITE_VOID_KNIGHT_TOP, Items.ELITE_VOID_KNIGHT_TOP_19787, Items.ELITE_VOID_KNIGHT_TOP_19789, Items.ELITE_VOID_KNIGHT_TORSO)
    private val ELITE_ROBES =
        intArrayOf(Items.ELITE_VOID_KNIGHT_ROBE, Items.ELITE_VOID_KNIGHT_ROBE_19788, Items.ELITE_VOID_KNIGHT_ROBE_19790, Items.ELITE_VOID_KNIGHT_LEGS)
    private val TOPS = intArrayOf(Items.VOID_KNIGHT_TOP, Items.VOID_KNIGHT_TOP_10611, *ELITE_TOPS)
    private val ROBES = intArrayOf(Items.VOID_KNIGHT_ROBE, *ELITE_ROBES)

    fun wearing(
        player: Player,
        helms: IntArray,
    ): Boolean =
        player.hasEquipped(EquipmentType.HEAD, *helms) &&
            player.hasEquipped(EquipmentType.CHEST, *TOPS) &&
            player.hasEquipped(EquipmentType.LEGS, *ROBES) &&
            player.hasEquipped(EquipmentType.GLOVES, Items.VOID_KNIGHT_GLOVES)

    fun wearingElite(
        player: Player,
        helms: IntArray,
    ): Boolean =
        wearing(player, helms) &&
            player.hasEquipped(EquipmentType.CHEST, *ELITE_TOPS) &&
            player.hasEquipped(EquipmentType.LEGS, *ELITE_ROBES)
}
