package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.heal

/**
 * OSRS-IMPORT Amulet of blood fury (OSRS Wiki "Amulet of blood fury" raw wikitext, fetched 2026-09-14):
 * - "Combine an Amulet of fury with a Blood shard"; "Once created, the amulet has 10,000 charges. Additional blood shards can be
 *   used to charge the amulet up to a maximum of 30,000 charges - each additional blood shard adds 10,000 charges."
 * - "A charge is depleted for every successful melee hit regardless of whether the amulet's effect triggers."
 * - "When dealing melee damage, the amulet grants a passive 20% chance to heal 30% of the damage dealt on any hit" (2 September
 *   2020: 10%/10% -> 20%/30%); it "can trigger independently from Guthan the Infested's equipment ability".
 * - At 0 charges it reverts to an Amulet of fury; "When killed by another player and the amulet of blood fury is not one of the
 *   items protected on death, the amulet is converted to a normal amulet of fury; the blood shard and any remaining charges are
 *   lost."
 * SOURCE_GAP (recorded): heal rounding (floored) and overheal (normal heal cap); the Revert option's result (not bound).
 * "Successful hit" = an accurate melee hit (the accuracy roll passed).
 */
object BloodFury {
    const val CHARGES_PER_SHARD = 10_000
    const val MAX_CHARGES = 30_000
    const val HEAL_CHANCE = 0.2
    const val HEAL_FRACTION = 0.3

    fun charges(item: Item): Int = if (item.id == Items.AMULET_OF_BLOOD_FURY) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    fun withCharges(
        item: Item,
        charges: Int,
    ): Item {
        val id = if (charges > 0) Items.AMULET_OF_BLOOD_FURY else Items.AMULET_OF_FURY
        return Item(id, item.amount).copyAttr(item).also {
            if (charges > 0) it.attr[ItemAttribute.CHARGES] = charges.coerceAtMost(MAX_CHARGES) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    /** Shards [available] can add to an Amulet of fury (creation) or an Amulet of blood fury. */
    fun shardsToAdd(
        item: Item,
        available: Int,
    ): Int = minOf(available, (MAX_CHARGES - charges(item)) / CHARGES_PER_SHARD).coerceAtLeast(0)

    fun healFor(damage: Int): Int = (damage * HEAL_FRACTION).toInt().coerceAtLeast(0)

    /** A landed melee hit of [damage]: 20 % chance to heal 30 %, and one charge used either way. */
    fun onMeleeHit(
        player: Player,
        damage: Int,
    ) {
        val amulet = player.getEquipment(EquipmentType.AMULET) ?: return
        if (amulet.id != Items.AMULET_OF_BLOOD_FURY) return
        if (player.world.randomDouble() < HEAL_CHANCE) {
            healFor(damage).takeIf { it > 0 }?.let { player.heal(it) }
        }
        player.equipment[EquipmentType.AMULET.id] = withCharges(amulet, charges(amulet) - 1)
    }
}
