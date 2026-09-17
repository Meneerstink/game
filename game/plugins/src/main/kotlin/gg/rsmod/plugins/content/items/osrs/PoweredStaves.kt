package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getAttackStyle
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.venom

/**
 * OSRS-IMPORT powered staves (OWNER DECISION 2026-09-14, option a). Sources (OSRS Wiki, fetched 2026-09-14):
 * - "Trident of the Seas": charge = "1 x Death rune, 1 x Chaos rune, 5 x Fire rune, 10 x Coins", "holding up to 2,500 charges
 *   when fully charged", max hit ⌊Magic/3⌋ - 5, attack range 7; the page's variant tabs are Uncharged / Partially charged /
 *   Charged, the fully charged item being "Trident of the Seas (full)"; "If lost on death, only the uncharged trident will
 *   appear on the floor". Uncharge returns the runes, not the coins ("(e)": "the coins will not be refunded").
 * - "Trident of the Swamp": "made with level 59 Crafting by using a magic fang on it with a chisel, which is a reversible
 *   process"; charge "1 x Death rune, 1 x Chaos rune, 5 x Fire rune, 1 x Zulrah's scales", "Charging the trident requires
 *   level 78 Magic"; uncharging recovers "the runes and Zulrah's scales"; "successful hits with either the built-in spell or
 *   a manually-cast combat spell have a 25% chance of inflicting venom" ("the staff must contain at least one charge ... this
 *   will not use up any charges"); max hit ⌊Magic/3⌋ - 2.
 * - "(e)" versions: "holds 20,000 charges instead of 2,500"; created by Lieve McCracken with 10 kraken tentacles (NPC absent:
 *   CONTEXT_UNAVAILABLE, not built).
 * - "Sanguinesti staff" (22 July 2026 rebalance included): "Each cast requires 2 blood runes", "holding up to 20,000 charges",
 *   "Players can uncharge the staff whenever and wherever they choose, getting all of the blood runes back"; max hit
 *   "⌊MagicLevel/3⌋" ("starting at 27 with level 82 Magic"); "Successful hits with the spell have a 1/5 (20%) chance of dealing
 *   8 additional damage and healing the user half the amount of hitpoints dealt to a target"; 82 Magic to wield.
 * - "Powered staff": "2 Magic experience per damage dealt", "cannot be used to autocast", attack speed 4, "cannot be cast upon
 *   other players in the Wilderness". "Combat Options": Accurate "+3 invisible bonus to their Magic level", Longrange "+1
 *   invisible bonus to their Magic level and a +3 invisible bonus to their Defence level" (rsmod and the wiki DPS calculator
 *   agree: Accurate 11, other styles 9 including the +8).
 * Charges live in [ItemAttribute.CHARGES]; the (full) item carries its 2,500 charges implicitly.
 * SOURCE_GAP (recorded, ADAPTED): charging/uncharge/check wording; how many charges one use adds (all that the inventory and
 * the cap allow); whether an uncharged staff can attack (it cannot here); Sanguinesti heal rounding (floor) and overheal (the
 * normal heal cap).
 */
object PoweredStaves {
    enum class Staff(
        val charged: Int,
        val uncharged: Int,
        /** The fully charged item (Trident of the Seas (full)), or null. */
        val full: Int?,
        val maxCharges: Int,
        val requiredMagic: Int,
        /** Items for one charge. */
        val chargeCost: List<Item>,
        /** Item ids returned by Uncharge (per charge, as in [chargeCost]). */
        val refunded: Set<Int>,
        /** Built-in spell max hit: max(1, ⌊Magic/3⌋ + [maxHitOffset]). */
        val maxHitOffset: Int,
        val venomChance: Double,
        /** Sanguinesti life leech on successful hits. */
        val leech: Boolean = false,
    ) {
        SEAS(
            Items.TRIDENT_OF_THE_SEAS, Items.UNCHARGED_TRIDENT, Items.TRIDENT_OF_THE_SEAS_FULL, 2_500, 75,
            seasCost(), setOf(Items.DEATH_RUNE, Items.CHAOS_RUNE, Items.FIRE_RUNE), -5, 0.0,
        ),
        SEAS_E(
            Items.TRIDENT_OF_THE_SEAS_E, Items.UNCHARGED_TRIDENT_E, null, 20_000, 75,
            seasCost(), setOf(Items.DEATH_RUNE, Items.CHAOS_RUNE, Items.FIRE_RUNE), -5, 0.0,
        ),
        SWAMP(
            Items.TRIDENT_OF_THE_SWAMP, Items.UNCHARGED_TOXIC_TRIDENT, null, 2_500, 78,
            swampCost(), setOf(Items.DEATH_RUNE, Items.CHAOS_RUNE, Items.FIRE_RUNE, Items.ZULRAHS_SCALES), -2, 0.25,
        ),
        SWAMP_E(
            Items.TRIDENT_OF_THE_SWAMP_E, Items.UNCHARGED_TOXIC_TRIDENT_E, null, 20_000, 78,
            swampCost(), setOf(Items.DEATH_RUNE, Items.CHAOS_RUNE, Items.FIRE_RUNE, Items.ZULRAHS_SCALES), -2, 0.25,
        ),
        SANGUINESTI(
            Items.SANGUINESTI_STAFF, Items.SANGUINESTI_STAFF_UNCHARGED, null, 20_000, 82,
            listOf(Item(Items.BLOOD_RUNE, 2)), setOf(Items.BLOOD_RUNE), 0, 0.0, leech = true,
        ),
        ;

        val ids: Set<Int> get() = setOfNotNull(charged, uncharged, full)

        fun baseMaxHit(magicLevel: Int): Int = maxOf(1, magicLevel / 3 + maxHitOffset)
    }

    /** The [Staff] tier a charged or (full) item id belongs to, or `null` for an uncharged id or anything else. */
    fun chargedTierOf(itemId: Int): Staff? = Staff.values().firstOrNull { it.charged == itemId || it.full == itemId }

    const val ATTACK_RANGE = 7

    /** "Combat Options" / osrsbox stance data: Longrange extends the attack range by 2. */
    const val LONGRANGE_EXTRA_RANGE = 2

    const val MAGIC_XP_PER_DAMAGE = 2.0

    const val SWAMP_CRAFTING_LEVEL = 59

    const val LEECH_CHANCE = 0.2
    const val LEECH_BONUS_DAMAGE = 8

    const val NO_AUTOCAST_MESSAGE = "You can't autocast spells with this weapon."
    const val NO_CHARGES_MESSAGE = "Your weapon has no charges left."
    const val WILDERNESS_PLAYER_MESSAGE = "You can't use this weapon's spell on players in the Wilderness."

    /** Magic fang + uncharged trident (normal and (e)) -> uncharged toxic trident; Dismantle reverses it. */
    val TOXIC_UPGRADE: Map<Int, Int> =
        mapOf(Items.UNCHARGED_TRIDENT to Items.UNCHARGED_TOXIC_TRIDENT, Items.UNCHARGED_TRIDENT_E to Items.UNCHARGED_TOXIC_TRIDENT_E)

    fun staffFor(itemId: Int?): Staff? = if (itemId == null) null else Staff.values().firstOrNull { itemId in it.ids }

    fun wielded(pawn: Pawn): Staff? = (pawn as? Player)?.let { staffFor(it.getEquipment(EquipmentType.WEAPON)?.id) }

    /** The built-in spell is used when a powered staff is wielded and no spell is being cast manually. */
    fun usingBuiltInSpell(pawn: Pawn): Boolean = wielded(pawn) != null && !pawn.attr.has(Combat.CASTING_SPELL)

    fun charges(item: Item): Int {
        val staff = staffFor(item.id) ?: return 0
        return when (item.id) {
            staff.uncharged -> 0
            staff.full -> staff.maxCharges
            else -> item.attr[ItemAttribute.CHARGES] ?: 0
        }
    }

    /** [item]'s staff holding [charges]: uncharged at 0, (full) at the cap where the staff has one, else the charged item. */
    fun withCharges(item: Item, charges: Int): Item {
        val staff = staffFor(item.id) ?: return item
        val id =
            when {
                charges <= 0 -> staff.uncharged
                charges >= staff.maxCharges && staff.full != null -> staff.full
                else -> staff.charged
            }
        return Item(id, item.amount).copyAttr(item).also {
            if (id == staff.charged) it.attr[ItemAttribute.CHARGES] = charges.coerceAtMost(staff.maxCharges) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    /** Charges one [available] inventory can add: limited by every cost item and the remaining capacity. */
    fun chargesAffordable(
        staff: Staff,
        current: Int,
        available: (Int) -> Int,
    ): Int {
        val affordable = staff.chargeCost.minOf { cost -> available(cost.id) / cost.amount }
        return minOf(affordable, staff.maxCharges - current).coerceAtLeast(0)
    }

    /** Items returned when [charges] are removed (runes, plus scales for the Swamp; never coins). */
    fun refund(
        staff: Staff,
        charges: Int,
    ): List<Item> = staff.chargeCost.filter { it.id in staff.refunded }.map { Item(it.id, it.amount * charges) }

    /** Sanguinesti heal for a leeching hit that dealt [dealt] damage: half, rounded down (SOURCE_GAP rounding). */
    fun leechHeal(dealt: Int): Int = (dealt / 2).coerceAtLeast(0)

    /** Accurate (first and second style) +3, Longrange (last style) +1 invisible Magic levels for the built-in spell. */
    fun stanceMagicBonus(player: Player): Int =
        when {
            !usingBuiltInSpell(player) -> 0
            isLongrange(player) -> 0
            else -> 2
        }

    /** Staff style set 1 has three buttons: the last one is Longrange (owner decision option a). */
    fun isLongrange(player: Player): Boolean = player.getAttackStyle() >= 2

    /** Swamp venom on a successful hit (built-in or manual spell); needs at least one charge, which is not used. */
    fun rollVenom(
        player: Player,
        target: Pawn,
    ) {
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return
        val staff = staffFor(weapon.id) ?: return
        if (staff.venomChance <= 0.0 || charges(weapon) <= 0) return
        if (player.world.randomDouble() < staff.venomChance) target.venom()
    }

    private fun seasCost() = listOf(Item(Items.DEATH_RUNE, 1), Item(Items.CHAOS_RUNE, 1), Item(Items.FIRE_RUNE, 5), Item(Items.COINS_995, 10))

    private fun swampCost() = listOf(Item(Items.DEATH_RUNE, 1), Item(Items.CHAOS_RUNE, 1), Item(Items.FIRE_RUNE, 5), Item(Items.ZULRAHS_SCALES, 1))
}
