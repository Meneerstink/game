package gg.rsmod.plugins.content.combat.strategy.ranged

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.CrossbowType
import gg.rsmod.plugins.content.items.osrs.DizanasQuiver

/**
 * The ammunition a bow or crossbow shot fires. OSRS Wiki "Dizana's quiver": the quiver is "an additional ammunition slot,
 * which can only be filled with arrows or bolts", with "the ammo in the actual ammo slot prioritised"; the ammo used
 * "depend[s] on the player's currently equipped ranged weapon". So the worn ammo slot fires when it suits the weapon,
 * otherwise the ammo stored in a worn Dizana's quiver does.
 *
 * Weapons without an ammo list here keep the old behaviour (the ammo slot as it is). Ranged attack/strength bonuses of the
 * fired quiver ammo replace those of the unused slot ammo only for a quiver shot (the wiki DPS calculator
 * `ammoApplicability` counts ammo bonuses only for ammo the weapon uses); the general 667 rule that an unused ammo slot
 * still adds its bonuses is an ADJACENT GAP recorded in OSRS_IMPORT_STATUS.md, not changed here.
 */
object RangedAmmo {
    data class Fired(val item: Item, val fromQuiver: Boolean)

    /** Ammo item ids the wielded weapon accepts, or null when the weapon has no ammo list. */
    fun validAmmo(weaponId: Int?): Array<Int>? =
        CrossbowType.values.firstOrNull { it.item == weaponId }?.ammo
            ?: BowType.values.firstOrNull { it.item == weaponId }?.ammo?.takeIf { it.isNotEmpty() }?.let(::withSeekingArrows)

    /** A bow that fires a base arrow tier also fires that tier's seeking arrow (ammo2, [Arrows.SEEKING_ARROWS]). */
    fun withSeekingArrows(ammo: Array<Int>): Array<Int> =
        ammo + gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.SEEKING_ARROWS
            .filterValues { base -> base.any { it in ammo } }.keys

    fun fired(player: Player): Fired? {
        // Crystal bows and the Bow of Faerdhinen generate their own arrows: worn ammo is never fired or used.
        if (gg.rsmod.plugins.content.items.osrs.CrystalEquipment.isCrystalBow(player.getEquipment(EquipmentType.WEAPON)?.id)) return null
        val slot = player.getEquipment(EquipmentType.AMMO)
        val valid = validAmmo(player.getEquipment(EquipmentType.WEAPON)?.id) ?: return slot?.let { Fired(it, false) }
        if (slot != null && slot.id in valid) return Fired(slot, false)
        val stored = DizanasQuiver.storedAmmo(player.getEquipment(EquipmentType.CAPE)) ?: return null
        return if (stored.id in valid) Fired(stored, true) else null
    }

    /** Uses [amount] of the fired ammo from the slot it came from. */
    fun consume(
        player: Player,
        fired: Fired,
        amount: Int = 1,
    ) {
        if (fired.fromQuiver) DizanasQuiver.removeStored(player, amount) else player.equipment.remove(fired.item.id, amount)
    }

    /** Bonus change for a quiver shot: the stored ammo's bonus instead of the unused slot ammo's (0 otherwise). */
    fun quiverBonusCorrection(
        player: Player,
        slot: BonusSlot,
    ): Int {
        val fired = fired(player)?.takeIf { it.fromQuiver } ?: return 0
        fun bonus(id: Int?) = if (id == null) 0 else player.world.definitions.get(ItemDef::class.java, id).bonuses[slot.id]
        return bonus(fired.item.id) - bonus(player.getEquipment(EquipmentType.AMMO)?.id)
    }
}
