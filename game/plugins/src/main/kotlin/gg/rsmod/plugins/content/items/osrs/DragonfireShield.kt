package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment

/**
 * OSRS-IMPORT breath-charged shields: Dragonfire shield, Dragonfire ward, Ancient wyvern shield (OSRS Wiki item pages, DFS fetched
 * 2026-09-16, ward and wyvern shield 2026-09-17c). Shared rules: "up to a maximum of 50 charges"; "Each breath attack gives the
 * ward one charge, and each charge increases its Melee and Ranged defensive bonuses by +1"; "The Inspect or Check option, while
 * the ward is in the inventory or equipped respectively, can be used to check its current number of charges"; Empty drains "all of
 * the ... charges at once ... in a harmless burst" (wyvern shield message quoted verbatim in [Family.emptyMessage]); "it can only be
 * traded when completely uncharged".
 * - Dragonfire shield / ward: charged by "the dragonfire attacks of adult dragons" and "the breath attacks of wyverns".
 * - Ancient wyvern shield: charged with fossils or numulites ("Recharge"), not by breath - [gainChargeFromDragonfire] skips it.
 *
 * The ward and the wyvern shield are two cache items each (charged / uncharged): the charged id exists only while charges > 0.
 * The 667 Dragonfire shield keeps its single modelled id. Charges live in [ItemAttribute.CHARGES].
 *
 * SOURCE_GAP / not built yet (recorded in OSRS_IMPORT_MASTER.yml): the +1 melee/ranged defence per charge, the Operate breath attack
 * of the ward and the wyvern shield, the wyvern shield's fossil / numulite Recharge, the trade lock while charged.
 */
object DragonfireShield {
    const val MAX_CHARGES = 50

    class Family(
        val charged: Int,
        val uncharged: Int,
        val noun: String,
        val emptyMessage: String,
        val breathCharged: Boolean,
    ) {
        val ids: Set<Int> = setOf(charged, uncharged)
    }

    val FAMILIES: List<Family> =
        listOf(
            Family(
                Items.DRAGONFIRE_SHIELD,
                Items.DRAGONFIRE_SHIELD,
                "shield",
                "You empty the shield of its remaining charges, releasing them in a harmless burst.",
                breathCharged = true,
            ),
            Family(
                Items.DRAGONFIRE_WARD,
                Items.DRAGONFIRE_WARD_UNCHARGED,
                "ward",
                "You empty the ward of its remaining charges, releasing them in a harmless burst.",
                breathCharged = true,
            ),
            Family(
                Items.ANCIENT_WYVERN_SHIELD,
                Items.ANCIENT_WYVERN_SHIELD_UNCHARGED,
                "shield",
                "You vent the shield's remaining charges harmlessly into the air.",
                breathCharged = false,
            ),
        )

    fun familyOf(itemId: Int): Family? = FAMILIES.firstOrNull { itemId in it.ids }

    fun charges(item: Item): Int = item.attr[ItemAttribute.CHARGES] ?: 0

    /** [item] holding [charges] (0-50): the family's charged id while it holds any, its uncharged id at 0. */
    fun withCharges(
        item: Item,
        charges: Int,
    ): Item {
        val clamped = charges.coerceIn(0, MAX_CHARGES)
        val family = familyOf(item.id)
        val id = if (family == null) item.id else if (clamped > 0) family.charged else family.uncharged
        return Item(id, item.amount).copyAttr(item).also {
            if (clamped > 0) it.attr[ItemAttribute.CHARGES] = clamped else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    fun inspectMessage(item: Item): String = "The ${familyOf(item.id)?.noun ?: "shield"} has ${charges(item)} charges."

    /** "the shield will absorb one charge per adult dragon dragonfire attack... although it will still discharge dragonfire." */
    fun gainChargeFromDragonfire(player: Player) {
        val shield = player.getEquipment(EquipmentType.SHIELD) ?: return
        if (familyOf(shield.id)?.breathCharged != true) return
        val current = charges(shield)
        if (current >= MAX_CHARGES) return
        player.equipment[EquipmentType.SHIELD.id] = withCharges(shield, current + 1)
    }
}
