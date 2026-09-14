package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey

/**
 * OSRS-IMPORT step 4 Nightmare staves (OSRS Wiki raw wikitext 2026-09-14, "Nightmare staff", "Harmonised/Volatile/Eldritch Nightmare
 * staff"; wiki DPS calculator `PlayerVsNPCCalc.ts`):
 * - Nightmare staff 72 Magic + 50 Hitpoints; orb staves 82 Magic + 50 Hitpoints; every staff +15 % magic damage (build-240 params).
 * - Harmonised: autocasts standard spells only; "reduces the cast time from 5 to 4 ticks", "only applies when autocasting".
 * - Volatile, Immolate: 55 % energy, "50% increased accuracy" (calculator [3, 2]); spell max hit
 *   min(floor(58 x Magic level / 99 + 1), 58) = calculator min(58, trunc((99 + 58 x level) / 99)), at least 1; "All magic damage gear,
 *   including the +15% from the staff is calculated separately"; "The special attack does not consume runes."
 * - Eldritch, Invocate: 55 % energy; spell max hit min(44, trunc((99 + 44 x level) / 99)); "restores the caster's Prayer points by 50% of
 *   its calculated damage. This effect can boost the caster's Prayer points above their natural Prayer level, up to a maximum of 120";
 *   restoration uses "the attack's potential damage before certain damage immunities" and requires a successful hit.
 * - Orbs: attached to the Nightmare staff, reverted with Dismantle; "If lost on death in the Wilderness, the killer will receive the staff
 *   and the orb" ([OsrsOrnamentKits]).
 * ADAPTED: rounding of "50% of its calculated damage" (rounded down); OSRS NIGHTMARE_STAFF_*_CAST/HIT spotanims are not imported.
 */
object NightmareStaves {
    const val SPECIAL_ENERGY = 55
    const val IMMOLATE_ACCURACY = 1.5
    const val IMMOLATE_CAP = 58
    const val INVOCATE_CAP = 44
    const val INVOCATE_PRAYER_CAP = 120

    /** Set while a Nightmare staff special computes its hit: replaces the spell base and hides the autocast spell's effects. */
    val SPECIAL_BASE_MAX_HIT = AttributeKey<Int>()

    fun immolateBase(magicLevel: Int): Int = ((99 + 58 * magicLevel) / 99).coerceIn(1, IMMOLATE_CAP)

    fun invocateBase(magicLevel: Int): Int = ((99 + 44 * magicLevel) / 99).coerceIn(1, INVOCATE_CAP)

    fun invocatePrayer(damage: Int): Int = damage / 2
}
