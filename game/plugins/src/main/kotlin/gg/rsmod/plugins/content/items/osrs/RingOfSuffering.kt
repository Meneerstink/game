package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS-IMPORT Ring of suffering recoil (owner decision (h) 2026-09-14; OSRS Wiki "Ring of suffering" raw wikitext):
 * - "The ring can also be charged with noted and unnoted rings of recoil to give it the recoil effect, renaming the item to ring
 *   of suffering (r)"; 40 charges per ring of recoil; "A maximum of 100,000 charges can be stored, equivalent to 2,500 rings";
 *   discharged rings are not refunded; "Charges are not lost upon death" (charges live on the item).
 * - "The ring of recoil effect is toggle-able" (19 Nov 2025: a disabled recoil consumes no charges); options Check / Recoil
 *   settings (inventory and worn).
 * - The effect is the Ring of recoil's: floor(10 % of the damage) + 1 reflected, one charge per point reflected.
 * ADAPTED (owner decision): at 0 charges (r) -> Ring of suffering and (ri) -> Ring of suffering (i); the toggle is stored per ring
 * ([ItemAttribute.TOGGLED_OFF]); Check / Recoil settings messages are not sourced.
 */
object RingOfSuffering {
    const val CHARGES_PER_RECOIL = 40
    const val MAX_CHARGES = 100_000

    private val chargedOf = mapOf(Items.RING_OF_SUFFERING to Items.RING_OF_SUFFERING_R, Items.RING_OF_SUFFERING_I to Items.RING_OF_SUFFERING_RI)
    private val baseOf = chargedOf.entries.associate { (base, charged) -> charged to base }

    val ALL: Set<Int> = chargedOf.keys + baseOf.keys

    fun isCharged(id: Int): Boolean = id in baseOf

    fun charges(item: Item): Int = if (isCharged(item.id)) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    fun recoilEnabled(item: Item): Boolean = item.attr[ItemAttribute.TOGGLED_OFF] != 1

    fun withCharges(
        item: Item,
        charges: Int,
    ): Item {
        val base = baseOf[item.id] ?: item.id
        val id = if (charges > 0) chargedOf.getValue(base) else base
        return Item(id, item.amount).copyAttr(item).also {
            if (charges > 0) it.attr[ItemAttribute.CHARGES] = charges.coerceAtMost(MAX_CHARGES) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    fun toggled(item: Item): Item =
        Item(item.id, item.amount).copyAttr(item).also {
            if (recoilEnabled(item)) it.attr[ItemAttribute.TOGGLED_OFF] = 1 else it.attr.remove(ItemAttribute.TOGGLED_OFF)
        }

    /** Rings of recoil from [available] that fit into [item]. */
    fun recoilsToAdd(
        item: Item,
        available: Int,
    ): Int = minOf(available, (MAX_CHARGES - charges(item)) / CHARGES_PER_RECOIL).coerceAtLeast(0)

    /** The ring after reflecting [reflected] damage, or null when it does not recoil (uncharged or switched off). */
    fun afterRecoil(
        item: Item,
        reflected: Int,
    ): Item? {
        if (charges(item) <= 0 || !recoilEnabled(item)) return null
        return withCharges(item, charges(item) - reflected)
    }
}
