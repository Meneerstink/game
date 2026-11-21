package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.message

/**
 * PROJECT48 DMM-ITEMS I-A05, Ring of endurance (OSRS Wiki, read 21-11): charged with stamina potions / mixes (one charge per dose) and extended
 * stamina potions (two per dose), max 1,000 charges. Worn, a stamina dose is doubled (effect and duration) and uses 1 charge (2 for extended);
 * with 500+ charges it passively cuts the run energy drain by 15% (no stacking with the stamina potion's 70%). Charges survive death, an uncharge
 * refunds nothing.
 */
object RingOfEndurance {
    const val MAX_CHARGES = 1_000
    const val PASSIVE_CHARGES = 500
    const val PASSIVE_DRAIN_MULTIPLIER = 0.85

    /** The doses of a stamina potion item and the charges one dose gives: Pair(replacement item, charges per dose). */
    private val DOSES: Map<Int, Pair<Int, Int>> =
        mapOf(
            Items.STAMINA_POTION_4 to (Items.STAMINA_POTION_3 to 1),
            Items.STAMINA_POTION_3 to (Items.STAMINA_POTION_2 to 1),
            Items.STAMINA_POTION_2 to (Items.STAMINA_POTION_1 to 1),
            Items.STAMINA_POTION_1 to (Items.VIAL to 1),
            Items.STAMINA_MIX_2 to (Items.STAMINA_MIX_1 to 1),
            Items.STAMINA_MIX_1 to (Items.VIAL to 1),
            Items.EXTENDED_STAMINA_POTION_4 to (Items.EXTENDED_STAMINA_POTION_3 to 2),
            Items.EXTENDED_STAMINA_POTION_3 to (Items.EXTENDED_STAMINA_POTION_2 to 2),
            Items.EXTENDED_STAMINA_POTION_2 to (Items.EXTENDED_STAMINA_POTION_1 to 2),
            Items.EXTENDED_STAMINA_POTION_1 to (Items.VIAL to 2),
        )

    val POTIONS: IntArray = DOSES.keys.toIntArray()

    fun charges(item: Item?): Int = if (item?.id == DmmItems.RING_OF_ENDURANCE) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    private fun worn(player: Player): Item? = player.getEquipment(EquipmentType.RING)?.takeIf { it.id == DmmItems.RING_OF_ENDURANCE }

    private fun withCharges(
        item: Item,
        charges: Int,
    ): Item =
        Item(if (charges > 0) DmmItems.RING_OF_ENDURANCE else DmmItems.RING_OF_ENDURANCE_UNCHARGED, item.amount).copyAttr(item).also {
            if (charges > 0) it.attr[ItemAttribute.CHARGES] = charges.coerceAtMost(MAX_CHARGES) else it.attr.remove(ItemAttribute.CHARGES)
        }

    /** Using a stamina potion on the ring in the inventory adds one dose worth of charges. */
    fun charge(
        player: Player,
        potion: Int,
    ) {
        val dose = DOSES[potion] ?: return
        val ringSlot = player.inventory.indexOfFirst { it?.id == DmmItems.RING_OF_ENDURANCE || it?.id == DmmItems.RING_OF_ENDURANCE_UNCHARGED }
        val potionSlot = player.inventory.getItemIndex(potion, skipAttrItems = false)
        if (ringSlot < 0 || potionSlot < 0) return
        val ring = player.inventory[ringSlot] ?: return
        val have = charges(ring)
        if (have + dose.second > MAX_CHARGES) {
            player.message("The ring cannot hold any more charges.")
            return
        }
        player.inventory[potionSlot] = Item(dose.first)
        player.inventory[ringSlot] = withCharges(ring, have + dose.second)
        player.message("You add ${dose.second} charge${if (dose.second > 1) "s" else ""} to the ring; it now holds ${have + dose.second}.")
    }

    fun check(
        player: Player,
        item: Item?,
    ) {
        val n = charges(item)
        player.message("Your ring of endurance has $n charge${if (n == 1) "" else "s"}.")
    }

    fun uncharge(
        player: Player,
        slot: Int,
    ) {
        val ring = player.inventory[slot] ?: return
        if (ring.id != DmmItems.RING_OF_ENDURANCE) return
        player.inventory[slot] = withCharges(ring, 0)
        player.message("You remove the charges from the ring. They are not returned.")
    }

    /**
     * Called by StaminaPotions.drink: worn with enough charges, the dose is doubled. Returns the factor for the restore and the duration
     * (2 or 1) and consumes the charges (1 per normal dose, 2 per extended dose; [extended] = the potion restores 40%).
     */
    fun doubling(
        player: Player,
        extended: Boolean,
    ): Int {
        val ring = worn(player) ?: return 1
        val need = if (extended) 2 else 1
        val have = charges(ring)
        if (have < need) return 1
        val slot = EquipmentType.RING.id
        player.equipment[slot] = withCharges(ring, have - need)
        return 2
    }

    /** Called by StaminaPotions.drainMultiplier when no stamina potion runs: 500+ charges worn = 15% less drain. */
    fun passiveDrain(player: Player): Double = if (charges(worn(player)) >= PASSIVE_CHARGES) PASSIVE_DRAIN_MULTIPLIER else 1.0
}
