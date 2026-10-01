package gg.rsmod.plugins.content.magic.lunar

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.plugins.content.items.potion.Potion
import gg.rsmod.plugins.content.items.potion.PotionType
import gg.rsmod.plugins.content.items.potion.PotionType.*

/**
 * Lunar Stat Restore Pot Share (81) and Boost Potion Share (84) - the rules, kept apart from the spell plugin so they can be tested.
 *
 * RuneScape Wiki (2011 versions of both pages): the caster casts on a potion; "3x3 square area, including the middle square";
 * one dose is given to each player there with Accept Aid on, "up to 4" from a vial; "doses of potion are only used up on other
 * players, not the caster" (the caster gets the effect for free); experience 84 / 88.
 * Stat Restore Pot Share: "Prayer potion, super prayer, restore potion, super restore, energy potion, and super energy".
 * Boost Potion Share: "most non-barbaric potions" except Zamorak / Saradomin brews, Combat potions, Guthix rest and untradeable
 * potions (extremes, overloads).
 * OSRS-imported potions (no 2011 rule exists for them) follow the OSRS Wiki: Sanfew serum and Stamina potion are Stat Restore
 * potions; the Boost Potion Share incompatible table excludes every brew, divine, energy, prayer, restore, stamina, surge,
 * goading, menaphite and prayer regeneration potion, so of the imports only the antivenoms, extended antifires, super combat,
 * bastion and battlemage remain. Anything not listed (the hunter / barbarian mixes, Super fishing / hunter) is refused.
 * Messages: no source quotes them; Void's PotionShare lines (Donors\void) - ADAPTED.
 */
object PotionShare {
    const val RADIUS = 1
    const val STAT_RESTORE_XP = 84.0
    const val BOOST_XP = 88.0

    const val CANNOT_SHARE = "You can't share that potion."
    const val NOBODY = "There is nobody around to share the potion with."

    val STAT_RESTORE: Set<PotionType> = setOf(PRAYER, SUPER_PRAYER, RESTORE, SUPER_RESTORE, ENERGY, SUPER_ENERGY, SANFEW_SERUM, STAMINA)

    val BOOST: Set<PotionType> =
        setOf(
            STRENGTH, SUPER_STRENGTH, ATTACK, SUPER_ATTACK, DEFENCE, SUPER_DEFENCE, MAGIC, RANGING, FISHING, AGILITY, HUNTER,
            CRAFTING, FLETCHING, SUMMONING, MAGIC_ESSENCE, ANTIPOISON, SUPER_ANTIPOISON, ANTIPOISON_PLUS, ANTIPOISON_PLUS_PLUS,
            ANTIFIRE, SUPER_ANTIFIRE,
            // OSRS imports allowed by the OSRS table.
            SUPER_COMBAT, BASTION, BATTLEMAGE, ANTI_VENOM, ANTI_VENOM_PLUS, EXTENDED_ANTI_VENOM_PLUS, EXTENDED_ANTIFIRE,
            EXTENDED_SUPER_ANTIFIRE,
        )

    /** The potion of [itemId] when it may be shared by the spell whose list is [allowed]; plain potions only (no barbarian mix). */
    fun shareable(
        itemId: Int,
        allowed: Set<PotionType>,
    ): Potion? = Potion.values().firstOrNull { it.item == itemId && it.mixHeal == 0 && it.potionType in allowed }

    /** Doses left in [potion] (the vial it walks down to before the empty vial). */
    fun doses(potion: Potion): Int {
        var count = 1
        var next = potion.replacement
        while (true) {
            val step = Potion.values().firstOrNull { it.item == next && it.potionType == potion.potionType } ?: return count
            count++
            next = step.replacement
        }
    }

    /** The item left after [used] doses were taken from [potion]. */
    fun remainder(
        potion: Potion,
        used: Int,
    ): Int {
        var current = potion
        repeat(used) {
            val next = Potion.values().firstOrNull { it.item == current.replacement && it.potionType == potion.potionType }
            if (next == null) return current.replacement
            current = next
        }
        return current.item
    }

    /** Recipients: other players in the 3x3 who accept aid and may drink it now, at most one per dose. */
    fun recipients(
        candidates: List<Player>,
        potion: Potion,
    ): List<Player> =
        candidates
            .filter { gg.rsmod.plugins.content.mechanics.aid.AcceptAid.accepts(it) && mayDrink(it, potion.potionType) }
            .take(doses(potion))

    /**
     * The drinking rules a shared dose obeys like a drunk one (Potions.drinkAt): no potion delay running, no activity rule against
     * drinking (Duel Arena "no drinks" ...), the potion's own gate.
     */
    fun mayDrink(
        p: Player,
        type: PotionType,
    ): Boolean =
        !p.timers.has(POTION_DELAY) &&
            gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refusal(
                p, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.DRINK,
            ) == null &&
            type.canDrink(p)

    /** The delays a dose starts (Potions.drinkAt: 3 ticks for potions and food). */
    fun startDelays(p: Player) {
        p.timers[POTION_DELAY] = 3
        p.timers[gg.rsmod.game.model.timer.FOOD_DELAY] = 3
    }
}
