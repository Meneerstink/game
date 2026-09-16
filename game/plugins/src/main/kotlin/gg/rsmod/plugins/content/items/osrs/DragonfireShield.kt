package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment

/**
 * OSRS-IMPORT Dragonfire shield charges (OSRS Wiki "Dragonfire shield", fetched 2026-09-16): "dragonfire
 * needs to be absorbed from dragonfire or icy breath attacks while it is equipped... up to a maximum of 50
 * charges, the shield will absorb one charge per adult dragon dragonfire attack... although it will still
 * discharge dragonfire"; "Each charge increases its melee and Ranged defensive bonuses by 1, giving a
 * maximum total bonus of +50 to melee and Ranged defensive bonuses"; "Clicking the Inspect option... can be
 * used to check its current number of charges" ("The shield has N charges."); "A dragonfire shield can be
 * drained of all charges at once by right-clicking the shield in the inventory and selecting Empty,
 * releasing it all in a harmless burst" (no items result).
 *
 * Only [Items.DRAGONFIRE_SHIELD] (the id Oziach's shield-assembly hands out, and the only one of this
 * item's two same-named cache ids that actually carries the "Empty" option) is modelled here. Charges
 * live in [ItemAttribute.CHARGES], the same attribute the crystal equipment/Dizana's quiver charge
 * systems already use.
 *
 * SOURCE_GAP (not built, recorded): the dynamic +1 melee/+1 Ranged defensive bonus per charge (needs a
 * call-site addition in every combat formula's defence-bonus computation, a materially larger and
 * riskier change than the reported Inspect/Empty bug this batch fixes); "it can only be traded when
 * completely uncharged" (no charged item in this codebase currently enforces a trade-lock, so none is
 * invented here in isolation); wyvern icy breath also granting a charge (Icy breath is handled by a
 * separate formula, [gg.rsmod.plugins.content.combat.formula.WyvernIcyBreath], not wired here).
 */
object DragonfireShield {
    const val MAX_CHARGES = 50

    fun charges(item: Item): Int = item.attr[ItemAttribute.CHARGES] ?: 0

    fun withCharges(
        item: Item,
        charges: Int,
    ): Item =
        item.copyAttr(item).also {
            val clamped = charges.coerceIn(0, MAX_CHARGES)
            if (clamped > 0) it.attr[ItemAttribute.CHARGES] = clamped else it.attr.remove(ItemAttribute.CHARGES)
        }

    /** "the shield will absorb one charge per adult dragon dragonfire attack... although it will still discharge dragonfire." */
    fun gainChargeFromDragonfire(player: Player) {
        val shield = player.getEquipment(EquipmentType.SHIELD) ?: return
        if (shield.id != Items.DRAGONFIRE_SHIELD) return
        val current = charges(shield)
        if (current >= MAX_CHARGES) return
        player.equipment[EquipmentType.SHIELD.id] = withCharges(shield, current + 1)
    }
}
