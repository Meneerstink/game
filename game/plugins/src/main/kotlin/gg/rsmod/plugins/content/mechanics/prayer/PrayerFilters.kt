package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.inter.magic.SpellFilters

/**
 * N482 (owner 2026-10-31: "we moeten de prayer filtering perfect krijgen", all three books): the OSRS prayer filters.
 *
 * OSRS (Wiki "Prayer" > Prayer filtering; osrs-dumps rev241 scripts 907 / 3156 / 3309): the prayer tab stone's right-click
 * "Enable prayer filtering" / "Disable prayer filtering"; while enabled the "Filters" button opens five "Show ..." lines:
 * lower tiers of tiered prayers, tiered prayers even if multi-skill prayers are available, Rapid Healing prayers, prayers you
 * lack the Prayer level to activate, prayers you lack the requirements to activate. Everything shows by default; turning
 * filtering off keeps the choices for the next time, as OSRS does (its five bits are not the master switch's).
 *
 * The state is the account's: bits 26-31 of varp 1376 (saved with the account like the spell filters next to it, no new cache
 * varp - [SpellFilters.project] keeps these bits): 26 filtering on, 27-31 the five lines (set = the line is crossed = hidden).
 * The client (RspsPrayerFilters) draws the button / menu and filters the grid; it sends the actions as IF_BUTTON1 on the prayer
 * tab's root 271:4 with the action as slot: 0 = enable / disable, 1-5 = the five lines. Hidden prayers and their order are the
 * client's (RuneLite-style, kept on the player's machine).
 */
object PrayerFilters {
    const val VARP = SpellFilters.VARP
    const val INTERFACE = 271
    const val ROOT = 4

    enum class Action(
        val slot: Int,
        val bit: Int,
    ) {
        TOGGLE_FILTERING(0, 26),
        LOWER_TIERS(1, 27),
        COMBINED_TIERS(2, 28),
        RAPID_HEALING(3, 29),
        LACKS_LEVEL(4, 30),
        LACKS_REQUIREMENTS(5, 31),
        ;

        companion object {
            fun forSlot(slot: Int): Action? = values().firstOrNull { it.slot == slot }
        }
    }

    fun isSet(
        value: Int,
        action: Action,
    ): Boolean = value and (1 shl action.bit) != 0

    /**
     * One menu action. The second line only works while the first is crossed (OSRS script 2758 greys it out otherwise and its bit
     * has no effect), so a click on it then changes nothing.
     */
    fun click(
        player: Player,
        slot: Int,
    ) {
        val action = Action.forSlot(slot) ?: return
        val value = player.getVarp(VARP)
        if (action == Action.COMBINED_TIERS && !isSet(value, Action.LOWER_TIERS)) return
        player.setVarp(VARP, SpellFilters.project(value xor (1 shl action.bit)))
    }
}
