package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.tools.importer.RuinousPowersCacheTool
import gg.rsmod.plugins.api.PrayerIcon

/**
 * N463: the 24 prayers of the OSRS Ruinous Powers book (Deadman: Annihilation 2026 / build 240), the third prayer book next to the
 * normal book and the Ancient Curses (owner 2026-10-31). The order is the book's own (by level, five per row, the owner's Wiki
 * screenshot) and is the grid slot, the cache enum key ([RuinousPowersCacheTool.ENUM_ID]) and the bit of the active / quick varps.
 *
 * Levels and sounds: OSRS item params 1753 / 1755 of enum 4959's objects (the 667 cache has the same synth ids under the same
 * names, e.g. 2676 Protect from Melee). [drainEffect]: the OSRS Wiki "Ruinous Powers" drain effect x10, the unit of [Prayer.drainEffect]
 * (Protect from Melee 12 -> 120). [icon]: the OSRS overhead, imported as head-icon frames 27-33.
 *
 * [group]: what switches off what. OSRS Wiki: Intensify "cannot be used alongside other offensive prayers such as Trinitas,
 * Vaporise, Decimate or Annihilate" ([Group.OFFENCE]); "Only one Ancient Vow can be activated at a time" ([Group.VOW]); one overhead
 * at a time, as every prayer book ([Group.OVERHEAD]: the seven with a head icon). The rest stack freely.
 */
enum class RuinousPrayer(
    val named: String,
    val level: Int,
    val drainEffect: Int,
    val group: Group,
    val sound: Int,
    val icon: PrayerIcon? = null,
) {
    REJUVENATION("Rejuvenation", 60, 40, Group.OTHER, 2678),
    ANCIENT_STRENGTH("Ancient Strength", 61, 180, Group.OFFENCE, 2688),
    ANCIENT_SIGHT("Ancient Sight", 62, 180, Group.OFFENCE, 2666),
    ANCIENT_WILL("Ancient Will", 63, 180, Group.OFFENCE, 2670),
    PROTECT_ITEM("Protect Item", 65, 180, Group.OTHER, 2665),
    RUINOUS_GRACE("Ruinous Grace", 66, 10, Group.OTHER, 1982),
    DAMPEN_MAGIC("Dampen Magic", 67, 140, Group.OVERHEAD, 2675, PrayerIcon.RUINOUS_DAMPEN_MAGIC),
    DAMPEN_RANGED("Dampen Ranged", 69, 140, Group.OVERHEAD, 2677, PrayerIcon.RUINOUS_DAMPEN_RANGED),
    DAMPEN_MELEE("Dampen Melee", 71, 140, Group.OVERHEAD, 2676, PrayerIcon.RUINOUS_DAMPEN_MELEE),
    TRINITAS("Trinitas", 72, 220, Group.OFFENCE, 2662),
    BERSERKER("Berserker", 74, 20, Group.OTHER, 2687),
    PURGE("Purge", 75, 180, Group.OVERHEAD, 2686, PrayerIcon.RUINOUS_PURGE),
    METABOLISE("Metabolise", 77, 120, Group.OTHER, 2687),
    REBUKE("Rebuke", 78, 120, Group.OVERHEAD, 2668, PrayerIcon.RUINOUS_REBUKE),
    VINDICATION("Vindication", 80, 90, Group.OVERHEAD, 2665, PrayerIcon.RUINOUS_VINDICATION),
    DECIMATE("Decimate", 82, 280, Group.OFFENCE, 3826),
    ANNIHILATE("Annihilate", 84, 280, Group.OFFENCE, 2670),
    VAPORISE("Vaporise", 86, 280, Group.OFFENCE, 2668),
    FUMUS_VOW("Fumus' Vow", 87, 140, Group.VOW, 2680),
    UMBRA_VOW("Umbra's Vow", 88, 140, Group.VOW, 2685),
    CRUOR_VOW("Cruor's Vow", 89, 140, Group.VOW, 2686),
    GLACIES_VOW("Glacies' Vow", 90, 140, Group.VOW, 2670),
    WRATH("Wrath", 91, 30, Group.OVERHEAD, 2682, PrayerIcon.RUINOUS_WRATH),
    INTENSIFY("Intensify", 92, 280, Group.OFFENCE, 2666),
    ;

    enum class Group { OFFENCE, OVERHEAD, VOW, OTHER }

    val slot: Int get() = ordinal

    val varbit: Int get() = RuinousPowersCacheTool.ACTIVE_VARBIT_BASE + ordinal

    val quickVarbit: Int get() = RuinousPowersCacheTool.QUICK_VARBIT_BASE + ordinal

    fun conflictsWith(other: RuinousPrayer): Boolean = other != this && group != Group.OTHER && group == other.group

    companion object {
        val values = values().toList()

        fun bySlot(slot: Int): RuinousPrayer? = values.getOrNull(slot)
    }
}
