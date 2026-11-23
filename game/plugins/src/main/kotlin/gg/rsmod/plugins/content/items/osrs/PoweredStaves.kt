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
        private val chargedId: Int?,
        private val unchargedId: Int?,
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
        /**
         * Revenant sceptre ([RevenantWeapons]): charged with revenant ether behind a 1,000-ether activation deposit, stays
         * activated at 0 ether, may attack players, and is charged / uncharged by revenant_weapons.plugin.kts.
         */
        val revenant: Boolean = false,
        /** N462 Tumeken's shadow: its own passive (equipment magic attack and damage x3, x4 inside ToA) and range/look. */
        val shadow: Boolean = false,
        val corrupted: Boolean = false,
        val zorya: Boolean = false,
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

        // Revenant sceptres (OSRS Wiki): Thammaron's "⌊Magic/3⌋ - 8", Accursed "Magic/3 - 6"; 1 ether per cast.
        THAMMARON(
            Items.THAMMARONS_SCEPTRE, Items.THAMMARONS_SCEPTRE_U, null, RevenantWeapons.MAX_ETHER, 60,
            listOf(Item(Items.REVENANT_ETHER, 1)), setOf(Items.REVENANT_ETHER), -8, 0.0, revenant = true,
        ),
        ACCURSED(
            Items.ACCURSED_SCEPTRE, Items.ACCURSED_SCEPTRE_U, null, RevenantWeapons.MAX_ETHER, 70,
            listOf(Item(Items.REVENANT_ETHER, 1)), setOf(Items.REVENANT_ETHER), -6, 0.0, revenant = true,
        ),

        // N462 OSRS Wiki Tumeken's shadow: "requiring 85 Magic to wield", "charged with soul and chaos runes, holding up to 20,000
        // charges"; each cast 2 soul + 5 chaos; uncharge "getting all of their runes back"; max hit floor(Magic/3 + 1). ToA imports
        // (OSRS 27275 charged, 27277 uncharged -> ToaIds).
        SHADOW(
            gg.rsmod.plugins.content.activity.toa.ToaIds.item(27275) ?: -2, gg.rsmod.plugins.content.activity.toa.ToaIds.item(27277) ?: -3,
            null, 20_000, 85, listOf(Item(Items.SOUL_RUNE, 2), Item(Items.CHAOS_RUNE, 5)), setOf(Items.SOUL_RUNE, Items.CHAOS_RUNE), 1, 0.0,
            shadow = true,
        ),
        ZORYA(
            null, null, null, 20_000, 85, listOf(Item(Items.ASTRAL_RUNE, 3), Item(Items.NATURE_RUNE, 2)),
            setOf(Items.ASTRAL_RUNE, Items.NATURE_RUNE), -10, 0.0, zorya = true,
        ),
        CORRUPTED_SHADOW(
            null, null, null, 20_000, 75, listOf(Item(Items.SOUL_RUNE, 2), Item(Items.CHAOS_RUNE, 5)),
            setOf(Items.SOUL_RUNE, Items.CHAOS_RUNE), 1, 0.0, shadow = true, corrupted = true,
        ),
        ;

        val available: Boolean get() = if (zorya) gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.item(34725) != null && gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.item(34727) != null else !corrupted || (gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.ItemId.SHADOW.local != null && gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.ItemId.SHADOW_EMPTY.local != null)
        val charged: Int get() = if (zorya) requireNotNull(gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.item(34725)) else if (corrupted) requireNotNull(gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.ItemId.SHADOW.local) else requireNotNull(chargedId)
        val uncharged: Int get() = if (zorya) requireNotNull(gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.item(34727)) else if (corrupted) requireNotNull(gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.ItemId.SHADOW_EMPTY.local) else requireNotNull(unchargedId)
        val ids: Set<Int> get() = setOfNotNull(charged, uncharged, full)

        fun baseMaxHit(magicLevel: Int): Int = maxOf(1, magicLevel / 3 + maxHitOffset)
    }

    /** The [Staff] tier a charged or (full) item id belongs to, or `null` for an uncharged id or anything else. */
    fun chargedTierOf(itemId: Int): Staff? = Staff.values().filter { it.available }.firstOrNull { it.charged == itemId || it.full == itemId }

    const val ATTACK_RANGE = 7

    /** Tumeken's shadow: "attack range is 8" (OSRS Wiki). */
    const val SHADOW_ATTACK_RANGE = 8

    /**
     * Tumeken's shadow passive (OSRS Wiki): the magic attack bonus and magic damage of the worn equipment "multiplied by three (four within
     * the Tombs of Amascut), capped at a total of 100% magic damage"; built-in spell only, and "the multiplier does not apply" in PvP.
     */
    fun shadowMultiplier(pawn: Pawn, target: Pawn?): Int {
        val player = pawn as? Player ?: return 1
        if (wielded(player)?.shadow != true || !usingBuiltInSpell(player) || target is Player) return 1
        val inToa = gg.rsmod.plugins.content.activity.toa.ToaLobby.of(player.world).raid(player.uid)?.contains(player) == true
        val corrupted = wielded(player)?.corrupted == true
        return if (inToa) (if (corrupted) 3 else 4) else (if (corrupted) 2 else 3)
    }

    /** Magic damage cap of the shadow passive: 100 % (the bonus slot holds tenths of a percent). */
    const val SHADOW_DAMAGE_CAP = 1000

    /** "Combat Options" / osrsbox stance data: Longrange extends the attack range by 2. */
    const val LONGRANGE_EXTRA_RANGE = 2

    const val MAGIC_XP_PER_DAMAGE = 2.0

    const val SWAMP_CRAFTING_LEVEL = 59

    const val LEECH_CHANCE = 0.2
    const val LEECH_BONUS_DAMAGE = 8

    const val NO_AUTOCAST_MESSAGE = "You can't autocast spells with this weapon."
    const val NO_CHARGES_MESSAGE = "Your weapon has no charges left."
    const val WILDERNESS_PLAYER_MESSAGE = "You can't use this weapon's spell on players in the Wilderness."

    /** Audit I-06: powered staves cannot attack players anywhere. */
    const val PLAYER_TARGET_MESSAGE = "You can't use this weapon's spell on other players."

    /** Magic fang + uncharged trident (normal and (e)) -> uncharged toxic trident; Dismantle reverses it. */
    val TOXIC_UPGRADE: Map<Int, Int> =
        mapOf(Items.UNCHARGED_TRIDENT to Items.UNCHARGED_TOXIC_TRIDENT, Items.UNCHARGED_TRIDENT_E to Items.UNCHARGED_TOXIC_TRIDENT_E)

    fun staffFor(itemId: Int?): Staff? = if (itemId == null) null else Staff.values().filter { it.available }.firstOrNull { itemId in it.ids }

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
        // A revenant sceptre stays activated when its ether runs out (RevenantWeapons).
        if (staff.revenant) return if (item.id == staff.uncharged) item else RevenantWeapons.withEther(item, charges)
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
