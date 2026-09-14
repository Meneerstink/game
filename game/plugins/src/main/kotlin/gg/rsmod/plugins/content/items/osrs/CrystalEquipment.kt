package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.Bows
import kotlin.math.floor

/**
 * OSRS-IMPORT Bow of Faerdhinen and crystal armour (OSRS Wiki "Bow of Faerdhinen" and "Crystal equipment#Crystal armour" raw
 * wikitext, fetched 2026-09-14):
 * - Bow: "Requiring level 80 Ranged and 70 Agility to wield"; "In order to add charges to the bow, crystal shards must be used on
 *   it (this can be done before it is inactive as well), with each shard granting 100 charges, up to a maximum of 20,000";
 *   "one charge being depleted for each hit, whether it is a successful one or not; once the bow's charges have been fully
 *   depleted, it will become inactive and lose its stat bonuses"; "does not require any arrows to use, as it generates its own
 *   when fired"; the corrupted bow "stays charged permanently"; "On unprotected death in PvP, the inactive bow is dropped, and all
 *   shards used to charge it will be lost. In a PvM or protected on PvP death, all current charges are kept."
 * - Armour: helm "5% accuracy bonus" / "2.5% damage bonus", body "15%" / "7.5%", legs "10%" / "5%" ("30% accuracy" and "15%
 *   damage" for the full set); "Each shard granting 100 charges", maximum "20,000"; "One charge is depleted for each successful
 *   hit that is received from combat" (not for damage negated by protection prayers or from non-monster sources).
 * - Stacking (wiki DPS calculator `isWearingCrystalBow`: "Crystal bow" or any "Bow of Faerdhinen" name, inactive included):
 *   accuracy trunc(roll × (20 + n) / 20) and max hit trunc(max × (40 + n) / 40) with helm 1, legs 2, body 3, applied before the
 *   Salve / Slayer factors ("would be 37 if placed after slayer helm", tested in-game).
 * SOURCE_GAP (not built, recorded): the Bow of Faerdhinen "Uncharge" result; the number of crystal armour seeds Revert returns;
 * seed singing (Prifddinas singing bowl) and corruption progress. "Non-monster sources" is read as: only NPC hits use armour
 * charges.
 */
object CrystalEquipment {
    const val CHARGES_PER_SHARD = 100
    const val MAX_CHARGES = 20_000

    data class Piece(
        val active: Int,
        val inactive: Int,
        /** Crystal armour weight in the bonus formula: helm 1, legs 2, body 3. */
        val weight: Int,
    )

    val ARMOUR =
        listOf(
            Piece(Items.CRYSTAL_HELM, Items.CRYSTAL_HELM_INACTIVE, 1),
            Piece(Items.CRYSTAL_LEGS, Items.CRYSTAL_LEGS_INACTIVE, 2),
            Piece(Items.CRYSTAL_BODY, Items.CRYSTAL_BODY_INACTIVE, 3),
        )

    val BOWFA: Set<Int> = setOf(Items.BOW_OF_FAERDHINEN, Items.BOW_OF_FAERDHINEN_INACTIVE, Items.BOW_OF_FAERDHINEN_C)

    /** Charged item -> inactive item, for everything that uses crystal shard charges. */
    val INACTIVE_FOR: Map<Int, Int> =
        mapOf(Items.BOW_OF_FAERDHINEN to Items.BOW_OF_FAERDHINEN_INACTIVE, Items.CRYSTAL_BOW_OSRS to Items.CRYSTAL_BOW_OSRS_INACTIVE) +
            ARMOUR.associate { it.active to it.inactive }

    val CHARGED_FOR: Map<Int, Int> = INACTIVE_FOR.entries.associate { (charged, inactive) -> inactive to charged }

    fun isCrystalBow(weaponId: Int?): Boolean = weaponId != null && (weaponId in Bows.CRYSTAL_BOWS || weaponId in BOWFA)

    /** Sum of the worn active crystal armour weights (0..6). */
    fun armourWeight(player: Player): Int {
        val worn = EquipmentType.values().mapNotNull { player.getEquipment(it)?.id }.toSet()
        return ARMOUR.filter { it.active in worn }.sumOf { it.weight }
    }

    fun applyAccuracy(
        player: Player,
        roll: Double,
    ): Double = if (isCrystalBow(player.getEquipment(EquipmentType.WEAPON)?.id)) accuracy(roll, armourWeight(player)) else roll

    fun applyDamage(
        player: Player,
        maxHit: Double,
    ): Double = if (isCrystalBow(player.getEquipment(EquipmentType.WEAPON)?.id)) damage(maxHit, armourWeight(player)) else maxHit

    fun accuracy(
        roll: Double,
        weight: Int,
    ): Double = floor(roll * (20 + weight) / 20.0)

    fun damage(
        maxHit: Double,
        weight: Int,
    ): Double = floor(maxHit * (40 + weight) / 40.0)

    fun charges(item: Item): Int = if (item.id in INACTIVE_FOR) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    /** The charged item holding [charges], or the inactive item at 0. */
    fun withCharges(
        item: Item,
        charges: Int,
    ): Item {
        val charged = CHARGED_FOR[item.id] ?: item.id.takeIf { it in INACTIVE_FOR } ?: return item
        val id = if (charges > 0) charged else INACTIVE_FOR.getValue(charged)
        return Item(id, item.amount).copyAttr(item).also {
            if (charges > 0) it.attr[ItemAttribute.CHARGES] = charges.coerceAtMost(MAX_CHARGES) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    /** Shards [available] add to [item] (100 charges each, never above 20,000). */
    fun shardsToAdd(
        item: Item,
        available: Int,
    ): Int = minOf(available, (MAX_CHARGES - charges(item)) / CHARGES_PER_SHARD).coerceAtLeast(0)

    /** "one charge being depleted for each hit, whether it is a successful one or not" (the corrupted bow never). */
    fun afterBowShot(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return
        // OSRS-IMPORT bows: the crystal bow degrades one charge per shot too ("will last for ... 2,500 shots", "Crystal shards can be
        // used to recharge the weapon to a maximum of 20,000 charges, with each shard providing 100 charges").
        if (weapon.id != Items.BOW_OF_FAERDHINEN && weapon.id != Items.CRYSTAL_BOW_OSRS) return
        player.equipment[EquipmentType.WEAPON.id] = withCharges(weapon, charges(weapon) - 1)
    }

    /** "One charge is depleted for each successful hit that is received from combat", per worn active piece. */
    fun onHitReceived(player: Player) {
        EquipmentType.values().forEach { type ->
            val item = player.getEquipment(type) ?: return@forEach
            if (ARMOUR.none { it.active == item.id }) return@forEach
            player.equipment[type.id] = withCharges(item, charges(item) - 1)
        }
    }
}
