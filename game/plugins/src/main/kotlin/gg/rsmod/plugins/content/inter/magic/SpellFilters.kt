package gg.rsmod.plugins.content.inter.magic

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarp

/**
 * N72 (owner 2026-10-16: "magic spellbook needs to exact same as osrs style layout options"): the OSRS spellbook filters on all
 * three books (Standard 192, Ancient 193, Lunar 430).
 *
 * OSRS (Wiki "Spellbook", cache 2686 scripts 907/2603/2611): the magic tab stone's right-click "Enable spell filtering" /
 * "Disable spell filtering"; once enabled a "Filters" button at the bottom of the book opens the menu "Show Combat spells",
 * "Show Teleport spells", "Show Utility spells", "Show spells you lack the Magic level to cast", "Show spells you lack the runes
 * to cast", "Show spells you lack the requirements to cast", "Enable icon resizing". Everything shows by default.
 *
 * State lives in the 667 spellbook varp 1376 (saved with the account, one state shared by every book like the donors -
 * Novite CombatDefinitions.refreshSpellBook, Darkan CombatDefinitions.kt:232-275):
 *  - bits 0-8: the 667 sort order per book (varbits 5822/5823/5824, one shared value as in Novite);
 *  - bits 9-17: the 667 category hide bits the cache layout script 2059 reads (6459-6467). They are a PROJECTION: the OSRS
 *    Combat/Teleport/Utility "hide" choice, enabled or not (N494: "Enable spell filtering" is only the editing mode, N495);
 *  - bits 18-25 (the removed Dungeoneering book's varbits 7347-7351 + unused bits): the OSRS filter state the client
 *    (RspsSpellFilters) reads - 18 enabled, 19 hide combat, 20 hide teleport, 21 hide utility, 22 hide lacks-level,
 *    23 hide lacks-runes, 24 hide lacks-requirements, 25 icon resizing off. 0 = the OSRS default (all shown, resizing on).
 *  - bits 26-31: the prayer filters (N482, [gg.rsmod.plugins.content.mechanics.prayer.PrayerFilters]), kept as they are.
 *
 * The client sends the OSRS actions as IF_BUTTON1 on the book's full-size layer ([Book.menuLayer], the cache layer the layout
 * script clears, where the client draws the Filters button and menu) with the action as the slot ([Action]).
 */
object SpellFilters {
    const val VARP = 1376

    enum class Category { COMBAT, TELEPORT, UTILITY }

    /** The OSRS menu actions (IF_BUTTON1 slot on [Book.menuLayer]). */
    enum class Action(val slot: Int, val bit: Int) {
        TOGGLE_FILTERING(0, 18),
        COMBAT(1, 19),
        TELEPORT(2, 20),
        UTILITY(3, 21),
        LACKS_LEVEL(4, 22),
        LACKS_RUNES(5, 23),
        LACKS_REQUIREMENTS(6, 24),
        ICON_RESIZING(7, 25),
        ;

        companion object {
            fun forSlot(slot: Int): Action? = values().firstOrNull { it.slot == slot }
        }
    }

    /**
     * One spellbook: the 667 bottom-row buttons (cache 192/193/430, Novite ButtonHandler.java:416-431, 604-625; sort ids = cache
     * enums 2169/2165/2173) and the layer of param 688 of the book struct (735/736/737).
     */
    enum class Book(
        val interfaceId: Int,
        val filterButtons: Map<Int, Category>,
        val sortButtons: List<Int>,
        val menuLayer: Int,
    ) {
        STANDARD(192, mapOf(7 to Category.COMBAT, 9 to Category.TELEPORT, 11 to Category.UTILITY, 13 to Category.UTILITY), listOf(15, 16, 17), 97),
        ANCIENT(193, mapOf(5 to Category.COMBAT, 7 to Category.TELEPORT), listOf(9, 10, 11), 54),
        LUNAR(430, mapOf(5 to Category.COMBAT, 7 to Category.TELEPORT, 9 to Category.UTILITY), listOf(11, 12, 13), 66),
    }

    private const val SORT_MASK = 0x1FF
    private const val OSRS_MASK = 0xFF shl 18

    /** Bits 26-31 are the prayer filters (N482, PrayerFilters) - this varp only carries them, the projection keeps them. */
    const val PRAYER_MASK = 0x3F shl 26

    /** 667 hide bits per OSRS category on every book (Standard 9-12, Lunar 13-15, Ancient 16-17). */
    private val CATEGORY_BITS =
        mapOf(
            Category.COMBAT to listOf(9, 13, 16),
            // OSRS "Utility" = the 667 skill + miscellaneous categories.
            Category.UTILITY to listOf(10, 11, 14),
            Category.TELEPORT to listOf(12, 15, 17),
        )

    private val CATEGORY_ACTION = mapOf(Category.COMBAT to Action.COMBAT, Category.TELEPORT to Action.TELEPORT, Category.UTILITY to Action.UTILITY)

    fun isSet(
        value: Int,
        action: Action,
    ): Boolean = value and (1 shl action.bit) != 0

    /**
     * The full varp 1376 for a stored OSRS state + sort: the 667 category bits follow the OSRS Combat / Teleport / Utility choice
     * ALWAYS (N494, owner 2026-10-31 20:5x: the Filters button and its ticks work whether filtering is enabled or not; "Enable spell
     * filtering" is only the editing mode for Hide / Unhide and moving, N495).
     */
    fun project(value: Int): Int {
        val osrs = value and (OSRS_MASK or PRAYER_MASK)
        val sort = (value and 0x7).coerceIn(0, 2)
        var out = osrs or sort or (sort shl 3) or (sort shl 6)
        CATEGORY_BITS.forEach { (category, bits) ->
            if (isSet(osrs, CATEGORY_ACTION.getValue(category))) bits.forEach { out = out or (1 shl it) }
        }
        return out
    }

    /**
     * Re-sends the projection (login: saves from before N72 may hold old 667 filter bits without filtering enabled). The sort goes back
     * to 0 = level order: OSRS sorts every book by level (script 2611 / 2621) and the 667 Sort buttons are gone (VRAGEN 27 A), so an
     * old "combat first" / "teleports first" save would otherwise keep an order no button can change (N72 research 5a).
     */
    fun refresh(player: Player) {
        val current = player.getVarp(VARP) and SORT_MASK.inv()
        val projected = project(current)
        player.setVarp(VARP, projected)
    }

    /** One OSRS menu action. Turning filtering on keeps the earlier choices, as OSRS does. */
    fun toggle(
        player: Player,
        action: Action,
    ) {
        player.setVarp(VARP, project(player.getVarp(VARP) xor (1 shl action.bit)))
    }

    /** A 667 bottom-row Filter button (any book): toggles the OSRS category and turns filtering on (a filter only works then). */
    fun clickLegacyFilter(
        player: Player,
        category: Category,
    ) {
        val bit = 1 shl CATEGORY_ACTION.getValue(category).bit
        val value = (player.getVarp(VARP) xor bit) or (1 shl Action.TOGGLE_FILTERING.bit)
        player.setVarp(VARP, project(value))
    }

    /** A 667 Sort button (index 0 level order, 1 combat first, 2 teleports first) - one shared order, as in Novite. */
    fun clickSort(
        player: Player,
        index: Int,
    ) {
        val value = (player.getVarp(VARP) and SORT_MASK.inv()) or index
        player.setVarp(VARP, project(value))
    }

    /** The IF_BUTTON1 slot sent on a book's [Book.menuLayer]. Unknown slots are ignored. */
    fun clickMenu(
        player: Player,
        slot: Int,
    ) {
        val action = Action.forSlot(slot) ?: return
        toggle(player, action)
    }
}
