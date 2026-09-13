package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.hasWeaponType
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile

/**
 * OSRS-IMPORT Dizana's quiver - Dizana's Sunfire (OSRS Wiki "Dizana's quiver" raw wikitext, "Sunfire splinters",
 * 2026-09-14): while charged (or blessed, permanently) "arrows and bolts that are shot from either ammunition slot will gain
 * an additional +1 Ranged Strength and +10 Ranged accuracy"; it does "not apply to other types of ammunition, such as
 * darts shot from a toxic blowpipe, chinchompas, or the bow of Faerdhinen"; "Whether or not the quiver consumes a charge is
 * determined by a random 1/3 roll"; at most 20,000 charges; one charge per Sunfire splinter.
 *
 * Charges live in [ItemAttribute.CHARGES]. SOURCE_GAP (not stated on the pages): whether a charged quiver reverts to the
 * uncharged item at 0 charges - it does here, like the other charged items; the Open/Empty/Uncharge option behaviour, the
 * second ammunition slot and the Ava's device upgrade are owner questions and not built.
 */
object DizanasQuiver {
    const val MAX_CHARGES = 20_000
    const val ACCURACY_BONUS = 10
    const val STRENGTH_BONUS = 1
    const val CHARGE_USE_CHANCE = 1.0 / 3.0

    /** Uncharged -> charged item id (normal and Trouver-locked). */
    val CHARGED_FOR: Map<Int, Int> =
        mapOf(Items.DIZANAS_QUIVER_UNCHARGED to Items.DIZANAS_QUIVER, Items.DIZANAS_QUIVER_L_UNCHARGED to Items.DIZANAS_QUIVER_L)

    val UNCHARGED_FOR: Map<Int, Int> = CHARGED_FOR.entries.associate { (uncharged, charged) -> charged to uncharged }

    val BLESSED: Set<Int> = setOf(Items.BLESSED_DIZANAS_QUIVER, Items.BLESSED_DIZANAS_QUIVER_L)

    fun charges(quiver: Item): Int = quiver.attr[ItemAttribute.CHARGES] ?: 0

    fun sunfireActive(quiver: Item?): Boolean =
        quiver != null && (quiver.id in BLESSED || (quiver.id in UNCHARGED_FOR && charges(quiver) > 0))

    fun isArrowOrBolt(ammoId: Int?): Boolean =
        ammoId != null && RangedProjectile.values.any { ammoId in it.items && (it.type == ProjectileType.ARROW || it.type == ProjectileType.BOLT) }

    /** True while a bow or crossbow shot with arrows or bolts gains Dizana's Sunfire. */
    fun applies(player: Player): Boolean =
        (player.hasWeaponType(WeaponType.BOW) || player.hasWeaponType(WeaponType.CROSSBOW)) &&
            isArrowOrBolt(player.getEquipment(EquipmentType.AMMO)?.id) &&
            sunfireActive(player.getEquipment(EquipmentType.CAPE))

    fun accuracyBonus(player: Player): Int = if (applies(player)) ACCURACY_BONUS else 0

    fun strengthBonus(player: Player): Int = if (applies(player)) STRENGTH_BONUS else 0

    data class Charge(val added: Int, val result: Item)

    /** Adds up to [available] splinters as charges; an uncharged quiver becomes the charged item. */
    fun charge(quiver: Item, available: Int): Charge {
        val chargedId = CHARGED_FOR[quiver.id] ?: quiver.id.takeIf { it in UNCHARGED_FOR } ?: return Charge(0, quiver)
        val current = charges(quiver)
        val added = minOf(MAX_CHARGES - current, available)
        if (added <= 0) return Charge(0, quiver)
        val result = Item(chargedId, quiver.amount).copyAttr(quiver)
        result.attr[ItemAttribute.CHARGES] = current + added
        return Charge(added, result)
    }

    /** One shot's charge roll ([roll] uniform in [0, 1)); blessed quivers never use charges. */
    fun spendShot(quiver: Item, roll: Double): Item {
        if (quiver.id !in UNCHARGED_FOR || roll >= CHARGE_USE_CHANCE) return quiver
        val left = charges(quiver) - 1
        return if (left > 0) {
            Item(quiver.id, quiver.amount).copyAttr(quiver).also { it.attr[ItemAttribute.CHARGES] = left }
        } else {
            Item(UNCHARGED_FOR.getValue(quiver.id), quiver.amount).copyAttr(quiver).also { it.attr.remove(ItemAttribute.CHARGES) }
        }
    }

    /** Called after a shot that gained Sunfire: spends the charge roll on the worn quiver. */
    fun afterShot(player: Player) {
        val quiver = player.getEquipment(EquipmentType.CAPE) ?: return
        val result = spendShot(quiver, player.world.randomDouble())
        if (result !== quiver) player.equipment[EquipmentType.CAPE.id] = result
    }
}
