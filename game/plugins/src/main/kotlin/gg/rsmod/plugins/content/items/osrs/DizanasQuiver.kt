package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.hasWeaponType
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile

/**
 * OSRS-IMPORT Dizana's quiver - Dizana's Sunfire (OSRS Wiki "Dizana's quiver" raw wikitext, "Sunfire splinters",
 * 2026-09-14): while charged (or blessed, permanently) "arrows and bolts that are shot from either ammunition slot will gain
 * an additional +1 Ranged Strength and +10 Ranged accuracy"; it does "not apply to other types of ammunition, such as
 * darts shot from a toxic blowpipe, chinchompas, or the bow of Faerdhinen"; "Whether or not the quiver consumes a charge is
 * determined by a random 1/3 roll"; at most 20,000 charges; one charge per Sunfire splinter.
 *
 * Charges live in [ItemAttribute.CHARGES]. SOURCE_GAP (not stated on the pages): whether a charged quiver reverts to the
 * uncharged item at 0 charges - it does here, like the other charged items.
 *
 * Ava's device upgrade (OSRS Wiki "Dizana's quiver", re-read 2026-09-17c): players "can bring the quiver to Ava, along with Ava's
 * assembler, Ava's accumulator, or their max cape equivalents, to apply the same ammunition-saving effect of her devices to the
 * quiver. Doing so will not consume the devices ... the quiver must be combined with Ava's assembler to receive its effect. This
 * upgrade applies to all quivers the player owns, now and in the future ... The interaction between Ava devices and metal torsos
 * does not carry over". It is an ammunition-saving effect only - it never grants Dizana's Sunfire. (The 2026-09-16 build read
 * this as "permanent Sunfire"; owner live report 2026-09-17c: "when uncharged it still gives the same bonuses as a charged" -
 * that misreading is removed.) Stored per account in [AVA_EFFECT]. OWNER DECISION: the Animal Magnetism requirement is not
 * enforced here (this cache has no quest system).
 *
 * Second ammunition slot (OWNER DECISION 2026-09-14: build it, server-side storage, real ammo slot first): "an additional
 * ammunition slot, which can only be filled with arrows or bolts", filled "using the Fill option from the Worn Equipment
 * tab" (message quoted in [NOTHING_TO_FILL_MESSAGE]); which ammo fires is decided by `RangedAmmo`. The stored ammo lives on
 * the quiver item ([ItemAttribute.ATTACHED_ITEM_ID] / [ItemAttribute.ATTACHED_ITEM_COUNT]), so it follows the item through
 * equip, bank, relog and death. SOURCE_GAP (ADAPTED, recorded): filling while a different ammo type is stored (refused,
 * nothing lost), the Open interface (a message listing the contents) and the Empty wording.
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

    val BLESSED_QUIVERS: Set<Int> = setOf(Items.BLESSED_DIZANAS_QUIVER, Items.BLESSED_DIZANAS_QUIVER_L)

    /**
     * OSRS Wiki "Dizana's max cape": "The cape retains the perks of the blessed Dizana's quiver" (with its (l)); its menu is "Wear, Open,
     * Empty, Destroy" - it keeps Sunfire and the stored ammunition, but no worn Fill option is listed (not a quiver item).
     */
    val MAX_CAPES: Set<Int> = setOf(Items.DIZANAS_MAX_CAPE, Items.DIZANAS_MAX_CAPE_L)

    /** Everything with permanent Dizana's Sunfire. */
    val BLESSED: Set<Int> = BLESSED_QUIVERS + MAX_CAPES

    /** Which of Ava's ammunition-saving effects the account's quivers carry - see the class doc "Ava's device upgrade". */
    enum class AvaEffect { ACCUMULATOR, ASSEMBLER }

    val AVA_EFFECT = AttributeKey<String>(persistenceKey = "dizanas_quiver_ava_effect")

    /**
     * The 2026-09-16 flag (then misread as permanent Sunfire). It did not record which device was shown to Ava, so an account that
     * only has this flag gets the lesser accumulator effect until Ava sees an assembler - never more than it earned.
     */
    val AVA_UPGRADED = AttributeKey<Boolean>(persistenceKey = "dizanas_quiver_ava_upgraded")

    fun avaEffect(player: Player): AvaEffect? =
        player.attr[AVA_EFFECT]?.let { stored -> AvaEffect.values().firstOrNull { it.name == stored } }
            ?: if (player.attr[AVA_UPGRADED] == true) AvaEffect.ACCUMULATOR else null

    /** The effect a device shown to Ava grants: only an assembler (or its max capes) carries the assembler effect. */
    fun effectOf(deviceId: Int): AvaEffect =
        if (deviceId in gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.ASSEMBLERS) AvaEffect.ASSEMBLER else AvaEffect.ACCUMULATOR

    /** The ammunition-saving effect of the worn cape when it is a quiver of an upgraded account, else null. */
    fun wornAvaEffect(player: Player): AvaEffect? =
        if (player.getEquipment(EquipmentType.CAPE)?.id in AMMO_HOLDERS) avaEffect(player) else null

    /** Every device (plus max cape equivalent) that grants the Ava's-device upgrade when brought to Ava. */
    val AVA_UPGRADE_DEVICES: Set<Int> = gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.ASSEMBLERS + Items.AVAS_ACCUMULATOR

    fun charges(quiver: Item): Int = quiver.attr[ItemAttribute.CHARGES] ?: 0

    /** Dizana's Sunfire: permanent on the blessed quiver / max cape, otherwise only while the quiver holds charges. */
    fun sunfireActive(quiver: Item?): Boolean {
        if (quiver == null || quiver.id !in AMMO_HOLDERS) return false
        return quiver.id in BLESSED || (quiver.id in QUIVERS && charges(quiver) > 0)
    }

    fun isArrowOrBolt(ammoId: Int?): Boolean =
        ammoId != null && RangedProjectile.values.any { ammoId in it.items && (it.type == ProjectileType.ARROW || it.type == ProjectileType.BOLT) }

    /** True while a bow or crossbow shot with arrows or bolts gains Dizana's Sunfire. */
    fun applies(player: Player): Boolean =
        (player.hasWeaponType(WeaponType.BOW) || player.hasWeaponType(WeaponType.CROSSBOW)) &&
            isArrowOrBolt(RangedAmmo.fired(player)?.item?.id) &&
            sunfireActive(player.getEquipment(EquipmentType.CAPE))

    const val NOTHING_TO_FILL_MESSAGE = "You have nothing in your worn quiver to fill your Dizana's Quiver with."

    /** All six quiver items (uncharged, charged, blessed, each with its (l)). */
    val QUIVERS: Set<Int> = CHARGED_FOR.keys + CHARGED_FOR.values + BLESSED_QUIVERS

    /** The quivers plus Dizana's max cape and its (l): everything that stores the second ammunition. */
    val AMMO_HOLDERS: Set<Int> = QUIVERS + MAX_CAPES

    /** The arrows or bolts stored in [quiver], or null when it holds none (or is not a quiver). */
    fun storedAmmo(quiver: Item?): Item? {
        if (quiver == null || quiver.id !in AMMO_HOLDERS) return null
        val id = quiver.attr[ItemAttribute.ATTACHED_ITEM_ID] ?: return null
        val count = quiver.attr[ItemAttribute.ATTACHED_ITEM_COUNT] ?: 0
        return if (count > 0) Item(id, count) else null
    }

    /** [quiver] holding [id] x [count] (none when [count] is 0); every other attribute (charges) is kept. */
    fun withStored(quiver: Item, id: Int, count: Int): Item =
        Item(quiver.id, quiver.amount).copyAttr(quiver).also {
            if (count > 0) {
                it.attr[ItemAttribute.ATTACHED_ITEM_ID] = id
                it.attr[ItemAttribute.ATTACHED_ITEM_COUNT] = count
            } else {
                it.attr.remove(ItemAttribute.ATTACHED_ITEM_ID)
                it.attr.remove(ItemAttribute.ATTACHED_ITEM_COUNT)
            }
        }

    sealed class FillResult {
        data class Filled(val quiver: Item, val moved: Int) : FillResult()

        object NothingWorn : FillResult()

        object NotArrowOrBolt : FillResult()

        object DifferentAmmo : FillResult()

        object Full : FillResult()
    }

    /** Moves worn [ammo] into [quiver]: same type only, arrows or bolts only, never above Int.MAX_VALUE in total. */
    fun fill(quiver: Item, ammo: Item?): FillResult {
        if (ammo == null) return FillResult.NothingWorn
        if (!isArrowOrBolt(ammo.id)) return FillResult.NotArrowOrBolt
        val stored = storedAmmo(quiver)
        if (stored != null && stored.id != ammo.id) return FillResult.DifferentAmmo
        val current = stored?.amount ?: 0
        val moved = minOf(ammo.amount.toLong(), Int.MAX_VALUE.toLong() - current).toInt()
        if (moved <= 0) return FillResult.Full
        return FillResult.Filled(withStored(quiver, ammo.id, current + moved), moved)
    }

    /** Removes [amount] stored ammo from the worn quiver (a quiver shot). */
    fun removeStored(player: Player, amount: Int) {
        val quiver = player.getEquipment(EquipmentType.CAPE) ?: return
        val stored = storedAmmo(quiver) ?: return
        player.equipment[EquipmentType.CAPE.id] = withStored(quiver, stored.id, stored.amount - amount)
    }

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

    /**
     * Owner-approved (2026-09-14, `OSRS_IMPORT_MASTER.yml` "Owner answers ... to build", item 3: "Uncharge -> Sunfire
     * splinters"). The exact inverse of [charge]: splinters charge 1-for-1 (`DizanasQuiverTests` "splinters charge
     * one for one"), so uncharging returns every remaining charge as a splinter and reverts to the uncharged item.
     * A no-op (`Charge(0, quiver)`) for a quiver with no charges or one [charge] itself would no-op on (blessed).
     */
    fun uncharge(quiver: Item): Charge {
        val uncharged = UNCHARGED_FOR[quiver.id] ?: return Charge(0, quiver)
        val current = charges(quiver)
        if (current <= 0) return Charge(0, quiver)
        val result = Item(uncharged, quiver.amount).copyAttr(quiver)
        result.attr.remove(ItemAttribute.CHARGES)
        return Charge(current, result)
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

    /** Called after a shot that gained Sunfire: spends the charge roll on the worn quiver (blessed quivers never spend one). */
    fun afterShot(player: Player) {
        val quiver = player.getEquipment(EquipmentType.CAPE) ?: return
        val result = spendShot(quiver, player.world.randomDouble())
        if (result !== quiver) player.equipment[EquipmentType.CAPE.id] = result
    }

    /**
     * [item] with its stored ammo and charges cleared (id/amount/every other attribute kept); `null`
     * when [item] is not an [AMMO_HOLDERS] stack or already carries neither. OSRS Wiki "Dizana's
     * quiver" Death: an unprotected Wilderness PvP death loses "any ammo that the quiver was holding,
     * as well as all the charges" - see `QuiverDeathRules`, which calls this for both a lost quiver
     * (dropping to the killer) and one kept because it is Trouver-locked.
     */
    fun strippedForDeath(item: Item): Item? {
        if (item.id !in AMMO_HOLDERS) return null
        if (storedAmmo(item) == null && charges(item) <= 0) return null
        return Item(item.id, item.amount).copyAttr(item).also {
            it.attr.remove(ItemAttribute.ATTACHED_ITEM_ID)
            it.attr.remove(ItemAttribute.ATTACHED_ITEM_COUNT)
            it.attr.remove(ItemAttribute.CHARGES)
        }
    }
}
