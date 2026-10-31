package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR
import gg.rsmod.game.model.attr.INTERACTING_SLOT_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.plugins.content.inter.magic.SpellFilters
import gg.rsmod.plugins.content.inter.magic.SpellTargetMaskHarness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N482 (owner 2026-10-31): the OSRS prayer filters. Every action the client's menu sends (IF_BUTTON1 on 271:4, slot 0 = enable /
 * disable, 1-5 = the five "Show ..." lines) must reach the server, flip exactly its own bit of varp 1376 (26-31) and leave the spell
 * filters (bits 0-25) alone - and the spell filters' projection must never drop the prayer bits (both live in the one saved varp).
 */
class PrayerFiltersTests {
    private val h = SpellTargetMaskHarness

    @After
    fun cleanUp() = h.cleanUp()

    @Test
    fun `N482 every prayer filter action flips its own bit and nothing else`() {
        val problems = mutableListOf<String>()
        for (action in PrayerFilters.Action.values()) {
            // The second line only works while the first is crossed (OSRS 2758) - test it with the first crossed.
            val start = if (action == PrayerFilters.Action.COMBINED_TIERS) 1 shl PrayerFilters.Action.LOWER_TIERS.bit else 0
            val spells = (1 shl 18) or (1 shl 19) or (1 shl 25) // spell filtering on, Combat hidden, resizing off
            val player = h.player("n482${action.slot}", mapOf(SpellFilters.VARP to (spells or start)))
            click(player, action.slot)
            val after = player.varps.getState(SpellFilters.VARP)
            val want = SpellFilters.project(spells or start) or (1 shl action.bit)
            if (after != want) problems += "$action: varp 1376 = 0x%08x, want 0x%08x".format(after, want)
            click(player, action.slot)
            val back = player.varps.getState(SpellFilters.VARP)
            if (back != SpellFilters.project(spells or start)) problems += "$action twice: 0x%08x, want it back".format(back)
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `N482 the second line does nothing while the first line is ticked (OSRS greys it out)`() {
        val player = h.player("n482grey")
        click(player, PrayerFilters.Action.COMBINED_TIERS.slot)
        assertEquals(0, player.varps.getState(SpellFilters.VARP) and SpellFilters.PRAYER_MASK)
    }

    @Test
    fun `N482 the spell filters keep the prayer filter bits`() {
        val prayers = (1 shl 26) or (1 shl 29) or (1 shl 31)
        assertEquals(prayers, SpellFilters.project(prayers) and SpellFilters.PRAYER_MASK)
        val player = h.player("n482spell", mapOf(SpellFilters.VARP to prayers))
        player.attr[INTERACTING_OPT_ATTR] = 1
        player.attr[INTERACTING_SLOT_ATTR] = SpellFilters.Action.TOGGLE_FILTERING.slot
        assertTrue(h.world.plugins.executeButton(player, 192, 97))
        h.tick(1)
        assertEquals(prayers, player.varps.getState(SpellFilters.VARP) and SpellFilters.PRAYER_MASK)
        unknownSlot(player)
    }

    private fun unknownSlot(player: Client) {
        val before = player.varps.getState(SpellFilters.VARP)
        click(player, 6)
        click(player, -1)
        assertEquals("unknown slots change nothing", before, player.varps.getState(SpellFilters.VARP))
    }

    private fun click(
        player: Client,
        slot: Int,
    ) {
        player.attr[INTERACTING_OPT_ATTR] = 1
        player.attr[INTERACTING_SLOT_ATTR] = slot
        assertTrue("271:4 slot $slot has a server handler", h.world.plugins.executeButton(player, PrayerFilters.INTERFACE, PrayerFilters.ROOT))
        h.tick(1)
    }
}
