package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.plugins.api.PrayerIcon

/**
 * The 19 toggleable Ancient Curses of the 2011 book (Turmoil keeps its own implementation in
 * [AncientCurses]; Protect Item is the normal book's shared effect, routed by book slot 0).
 *
 * Level/drain/effect data: https://wiki.darkan.org/Ancient_Curses (2011-era reference; the modern
 * runescape.wiki reflects the EoC rework and does not apply to this revision).
 *
 * Book slot / varbit contract (PROVEN from this cache's own CS2, not guessed): enum 862/863 maps
 * book slots 0..19 to structs 888..907 (Protect Item, Sap Warrior, Sap Ranger, Sap Mage, Sap
 * Spirit, Berserker, Deflect Summoning, Deflect Magic, Deflect Missiles, Deflect Melee, Leech
 * Attack, Leech Ranged, Leech Magic, Leech Defence, Leech Strength, Leech Energy, Leech Special
 * Attack, Wrath, Soul Split, Turmoil); clientscript 2296 switches on those struct ids to the
 * ACTIVE varbits 6820..6839 (varp 1582) when the book varbit 6840 is set, and clientscript 2297
 * to the quick-curse varbits 6862..6881. Decoded with InterfaceHookProbeTool/ConfigDefProbeTool.
 *
 * Animation/graphic/projectile ids for Sap/Leech/Deflect are PROVEN from Novite's own rev-667
 * `game/player/Player.java` (`handleIngoingHit`, the same method this project already trusts for
 * the 2011 Sap/Leech activation chances and Wrath's death-explosion multiplier - see
 * [AncientCurses] KDoc), not guessed: real per-curse anim/gfx/projectile ids appear inline in that
 * method's `usingPrayer(1, <slot>)` branches, one branch per curse in this enum.
 */
enum class AncientCurse(
    val curseName: String,
    /** Position in the curse book grid (interface 271 component 8 child index). */
    val slot: Int,
    val level: Int,
    /**
     * Drain rate in the same units [gg.rsmod.plugins.content.mechanics.prayer.Prayer.drainEffect]
     * uses, i.e. the authentic RS drain-counter model: every game tick the sum of all active
     * drain effects is added to a counter, and each time that counter reaches
     * `60 + 2 * prayerBonus` one tenth of a Prayer point is spent. Seconds per whole point at zero
     * prayer bonus is therefore `360 / drainEffect`.
     */
    val drainEffect: Int,
    val category: Category,
    /**
     * The 4 Deflects and Wrath each have their own overhead [PrayerIcon] (ids 8/10-13 in the same
     * `headicons_prayer` sprite table Protect prayers use - see [PrayerIcon] KDoc). Soul Split has
     * no overhead icon in the real game.
     */
    val icon: PrayerIcon? = null,
    /**
     * Caster-side activation animation played on a landed hit that triggers this curse (Sap uses
     * 12569, Leech uses 12575 - both PROVEN from Novite `Player.java` rev 667, `handleIngoingHit`,
     * the same source [AncientCurses] KDoc's Sap/Leech percentages come from). Null for curses with
     * no per-hit animation (Deflects/Wrath/Soul Split/Turmoil, handled directly in [AncientCurses]).
     */
    val castAnimation: Int? = null,
    /** Sap-only immediate caster graphic (Leech has none - PROVEN, same source as [castAnimation]). */
    val castGraphic: Int? = null,
    /** Projectile from caster to target on activation (PROVEN, same source). */
    val projectileGraphic: Int? = null,
    /** Graphic shown on the target one tick after the projectile lands (PROVEN, same source). */
    val targetGraphic: Int? = null,
    /**
     * Deflect Melee/Missiles/Magic only: animation+graphic played on the DEFENDER when a hit is
     * actually reflected (PROVEN from the same `handleIngoingHit`: anim 12573, gfx 2228/2229/2230
     * per style). Deflect Summoning has no sourced reflect effect - see [AncientCurses.onIncomingHit].
     */
    val reflectAnimation: Int? = null,
    val reflectGraphic: Int? = null,
    /**
     * Real per-hit activation chance for Sap/Leech, PROVEN from the same Novite `handleIngoingHit`
     * (`Utils.getRandom(maxValue)` there returns a uniform value in `0..maxValue` inclusive, so
     * `getRandom(4)==0` is 1-in-5, `getRandom(7)==0` is 1-in-8, `getRandom(10)==0` is 1-in-11) -
     * replaces this file's previous single shared 25% *provisional* guess for every curse alike,
     * which the 2011 wiki reference never actually published. Meaningless for non-Sap/Leech curses.
     */
    val activationChancePercent: Double = 100.0,
) {
    SAP_WARRIOR(
        "Sap Warrior", 1, level = 50, drainEffect = 150, category = Category.SAP,
        castAnimation = 12569, castGraphic = 2214, projectileGraphic = 2215, targetGraphic = 2216,
        activationChancePercent = 20.0,
    ),
    SAP_RANGER(
        "Sap Ranger", 2, level = 52, drainEffect = 150, category = Category.SAP,
        castAnimation = 12569, castGraphic = 2217, projectileGraphic = 2218, targetGraphic = 2219,
        activationChancePercent = 20.0,
    ),
    SAP_MAGE(
        "Sap Mage", 3, level = 54, drainEffect = 150, category = Category.SAP,
        castAnimation = 12569, castGraphic = 2220, projectileGraphic = 2221, targetGraphic = 2222,
        activationChancePercent = 20.0,
    ),
    SAP_SPIRIT(
        "Sap Spirit", 4, level = 56, drainEffect = 150, category = Category.SAP,
        castAnimation = 12569, castGraphic = 2223, projectileGraphic = 2224, targetGraphic = 2225,
        activationChancePercent = 100.0 / 11.0,
    ),
    BERSERKER("Berserker", 5, level = 59, drainEffect = 20, category = Category.FREE),
    DEFLECT_SUMMONING("Deflect Summoning", 6, 62, 120, Category.DEFLECT_SUMMONING, PrayerIcon.DEFLECT_SUMMONING),
    DEFLECT_MAGIC(
        "Deflect Magic", 7, 65, 120, Category.DEFLECT_COMBAT, PrayerIcon.DEFLECT_MAGIC,
        reflectAnimation = 12573, reflectGraphic = 2228,
    ),
    DEFLECT_MISSILES(
        "Deflect Missiles", 8, 68, 120, Category.DEFLECT_COMBAT, PrayerIcon.DEFLECT_MISSILES,
        reflectAnimation = 12573, reflectGraphic = 2229,
    ),
    DEFLECT_MELEE(
        "Deflect Melee", 9, 71, 120, Category.DEFLECT_COMBAT, PrayerIcon.DEFLECT_MELEE,
        reflectAnimation = 12573, reflectGraphic = 2230,
    ),
    LEECH_ATTACK(
        "Leech Attack", 10, level = 74, drainEffect = 100, category = Category.LEECH,
        castAnimation = 12575, projectileGraphic = 2231, targetGraphic = 2232,
        activationChancePercent = 12.5,
    ),
    LEECH_RANGED(
        "Leech Ranged", 11, level = 76, drainEffect = 100, category = Category.LEECH,
        castAnimation = 12575, projectileGraphic = 2236, targetGraphic = 2238,
        activationChancePercent = 12.5,
    ),
    LEECH_MAGIC(
        "Leech Magic", 12, level = 78, drainEffect = 100, category = Category.LEECH,
        castAnimation = 12575, projectileGraphic = 2240, targetGraphic = 2242,
        activationChancePercent = 12.5,
    ),
    LEECH_DEFENCE(
        "Leech Defence", 13, level = 80, drainEffect = 100, category = Category.LEECH,
        castAnimation = 12575, projectileGraphic = 2244, targetGraphic = 2246,
        activationChancePercent = 100.0 / 11.0,
    ),
    LEECH_STRENGTH(
        "Leech Strength", 14, level = 82, drainEffect = 100, category = Category.LEECH,
        castAnimation = 12575, projectileGraphic = 2248, targetGraphic = 2250,
        activationChancePercent = 12.5,
    ),
    LEECH_ENERGY(
        "Leech Energy", 15, level = 84, drainEffect = 100, category = Category.LEECH,
        castAnimation = 12575, projectileGraphic = 2256, targetGraphic = 2258,
        activationChancePercent = 100.0 / 11.0,
    ),
    LEECH_SPECIAL_ATTACK(
        "Leech Special Attack", 16, level = 86, drainEffect = 100, category = Category.LEECH,
        castAnimation = 12575, projectileGraphic = 2252, targetGraphic = 2254,
        activationChancePercent = 100.0 / 11.0,
    ),
    WRATH("Wrath", 17, level = 89, drainEffect = 30, category = Category.WRATH, icon = PrayerIcon.WRATH),
    SOUL_SPLIT("Soul Split", 18, level = 92, drainEffect = 180, category = Category.SOUL_SPLIT, icon = PrayerIcon.SOUL_SPLIT),
    ;

    /** Client-side "active" varbit (varp 1582 bit [slot]). */
    val varbit: Int get() = ACTIVE_VARBIT_BASE + slot


    /**
     * 2011 mutual-exclusion rules (wiki.darkan.org key): Saps stack with Saps and Leeches with
     * Leeches, but a Sap and a Leech never run together and neither runs with Turmoil; the three
     * combat Deflects, Wrath and Soul Split are mutually exclusive overheads; Deflect Summoning pairs
     * with the combat Deflects but not with Wrath or Soul Split; Berserker (and Protect Item)
     * combine with anything.
     */
    enum class Category { SAP, LEECH, DEFLECT_COMBAT, DEFLECT_SUMMONING, WRATH, SOUL_SPLIT, FREE }

    fun conflictsWith(other: AncientCurse): Boolean {
        if (other == this) return false
        val a = category
        val b = other.category
        return when {
            a == Category.SAP && b == Category.LEECH -> true
            a == Category.LEECH && b == Category.SAP -> true
            a == Category.DEFLECT_COMBAT && b == Category.DEFLECT_COMBAT -> true
            a.isOverhead() && b.isOverhead() && (a != Category.DEFLECT_COMBAT || b != Category.DEFLECT_COMBAT) &&
                !(a.isDeflect() && b.isDeflect()) -> true
            else -> false
        }
    }

    /** Sap/Leech never run together with Turmoil. */
    val conflictsWithTurmoil: Boolean get() = category == Category.SAP || category == Category.LEECH

    companion object {
        /**
         * Turmoil is implemented separately in [AncientCurses] rather than as an entry above, but
         * it drains through the same shared counter. Same units; 2 seconds per Prayer point at
         * zero prayer bonus, matching Soul Split.
         */
        const val TURMOIL_DRAIN_EFFECT = 180

        /** Cache-proven contract (see class KDoc). */
        const val ACTIVE_VARBIT_BASE = 6820
        const val QUICK_VARBIT_BASE = 6862
        const val BOOK_VARBIT = 6840
        const val PROTECT_ITEM_SLOT = 0
        const val TURMOIL_SLOT = 19
        const val TURMOIL_VARBIT = ACTIVE_VARBIT_BASE + TURMOIL_SLOT
        const val PROTECT_ITEM_VARBIT = ACTIVE_VARBIT_BASE + PROTECT_ITEM_SLOT

        val values = enumValues<AncientCurse>()

        fun bySlot(slot: Int): AncientCurse? = values.firstOrNull { it.slot == slot }

        fun byCommand(arg: String): AncientCurse? =
            values.firstOrNull { it.name.equals(arg, ignoreCase = true) || it.curseName.equals(arg, ignoreCase = true) }

        private fun Category.isOverhead() =
            this == Category.DEFLECT_COMBAT || this == Category.DEFLECT_SUMMONING || this == Category.WRATH || this == Category.SOUL_SPLIT

        private fun Category.isDeflect() = this == Category.DEFLECT_COMBAT || this == Category.DEFLECT_SUMMONING
    }
}
