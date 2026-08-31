package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.plugins.api.PrayerIcon

/**
 * BATCH 2: the 19 curse-specific entries of the real Ancient Curses book (Turmoil is deliberately
 * excluded here - it already has its own implementation in [AncientCurses] and is not toggled
 * through this enum). Protect Item is also excluded: it is shared with the normal Prayer book's
 * own Protect Item effect and must not be duplicated as a second, independent toggle.
 *
 * Level/drain-rate/effect data sourced from https://wiki.darkan.org/Ancient_Curses (a 2011-era
 * reference site; the modern runescape.wiki reflects a much later EoC/RS3 rework of the curse
 * book that does not apply to this revision) - not guessed. Spot-check against a stronger source
 * if one becomes available; see NIGHT_SERVER_STATUS.md.
 */
enum class AncientCurse(
    val curseName: String,
    val level: Int,
    /** Real-world seconds to drain one Prayer point while this curse alone is active. */
    val secondsPerPoint: Double,
    val group: Group,
    /** Only the 4 Deflects reuse a real overhead [PrayerIcon]; everything else has none. */
    val icon: PrayerIcon? = null,
) {
    SAP_WARRIOR("Sap Warrior", level = 50, secondsPerPoint = 0.24, group = Group.OFFENSIVE),
    SAP_RANGER("Sap Ranger", level = 52, secondsPerPoint = 0.24, group = Group.OFFENSIVE),
    SAP_MAGE("Sap Mage", level = 54, secondsPerPoint = 0.24, group = Group.OFFENSIVE),
    SAP_SPIRIT("Sap Spirit", level = 56, secondsPerPoint = 0.24, group = Group.OFFENSIVE),
    BERSERKER("Berserker", level = 59, secondsPerPoint = 1.8, group = Group.NONE),
    DEFLECT_SUMMONING(
        "Deflect Summoning",
        level = 62,
        secondsPerPoint = 0.3,
        group = Group.DEFLECT,
        icon = PrayerIcon.PROTECT_FROM_SUMMONING,
    ),
    DEFLECT_MAGIC(
        "Deflect Magic",
        level = 65,
        secondsPerPoint = 0.3,
        group = Group.DEFLECT,
        icon = PrayerIcon.PROTECT_FROM_MAGIC,
    ),
    DEFLECT_MISSILES(
        "Deflect Missiles",
        level = 68,
        secondsPerPoint = 0.3,
        group = Group.DEFLECT,
        icon = PrayerIcon.PROTECT_FROM_MISSILES,
    ),
    DEFLECT_MELEE(
        "Deflect Melee",
        level = 71,
        secondsPerPoint = 0.3,
        group = Group.DEFLECT,
        icon = PrayerIcon.PROTECT_FROM_MELEE,
    ),
    LEECH_ATTACK("Leech Attack", level = 74, secondsPerPoint = 0.36, group = Group.OFFENSIVE),
    LEECH_RANGED("Leech Ranged", level = 76, secondsPerPoint = 0.36, group = Group.OFFENSIVE),
    LEECH_MAGIC("Leech Magic", level = 78, secondsPerPoint = 0.36, group = Group.OFFENSIVE),
    LEECH_DEFENCE("Leech Defence", level = 80, secondsPerPoint = 0.36, group = Group.OFFENSIVE),
    LEECH_STRENGTH("Leech Strength", level = 82, secondsPerPoint = 0.36, group = Group.OFFENSIVE),
    LEECH_ENERGY("Leech Energy", level = 84, secondsPerPoint = 0.36, group = Group.OFFENSIVE),
    LEECH_SPECIAL_ATTACK("Leech Special Attack", level = 86, secondsPerPoint = 0.36, group = Group.OFFENSIVE),
    WRATH("Wrath", level = 89, secondsPerPoint = 1.2, group = Group.NONE),
    SOUL_SPLIT("Soul Split", level = 92, secondsPerPoint = 0.2, group = Group.NONE),
    ;

    /**
     * Amount to drain (on the *10 internal prayer-point scale) each time [AncientCurses]' shared
     * drain loop fires, once every [LOOP_TICKS] ticks. `10.0` converts real points to the *10
     * scale; `LOOP_TICKS * 0.6` converts game ticks to real seconds.
     */
    val drainPerInvocation: Int
        get() = Math.round(10.0 * LOOP_TICKS * 0.6 / secondsPerPoint).toInt().coerceAtLeast(1)

    /**
     * Mutual-exclusion grouping (wiki.darkan.org / general Ancient Curses convention): every
     * OFFENSIVE curse (all Sap + all Leech) excludes every other OFFENSIVE curse and Turmoil;
     * DEFLECT curses only exclude each other (mirrors the normal book's single-Protect-prayer
     * rule); NONE curses (Berserker/Wrath/Soul Split) freely combine with anything.
     */
    enum class Group { OFFENSIVE, DEFLECT, NONE }

    companion object {
        /** 3 ticks = 1.8 real seconds; mirrors Turmoil's own `wait(5)` shape at a shared granularity. */
        const val LOOP_TICKS = 3
        val values = enumValues<AncientCurse>()

        fun byCommand(arg: String): AncientCurse? =
            values.firstOrNull { it.name.equals(arg, ignoreCase = true) || it.curseName.equals(arg, ignoreCase = true) }
    }
}
