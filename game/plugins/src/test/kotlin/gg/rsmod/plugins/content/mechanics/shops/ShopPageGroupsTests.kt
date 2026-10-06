package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.model.shop.Shop
import gg.rsmod.plugins.api.ext.MAX_DIALOG_OPTIONS
import gg.rsmod.plugins.content.skills.summoning.SummoningShopStock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Owner 2026-10-06 (bug 204956 / OWNER_QUESTIONS Q2 = A): paged shops - Trade opens page 1, Prev/Next page walk the pages. */
class ShopPageGroupsTests {
    @Test
    fun `turning pages clamps at the first and last page like the 78 Store`() {
        val group = ShopPageGroups.Group("test-clamp", listOf("A", "B", "C"))
        assertEquals(0, group.turn(0, -1), "Prev on page 1 stays on page 1")
        assertEquals(1, group.turn(0, 1))
        assertEquals(2, group.turn(1, 1))
        assertEquals(2, group.turn(2, 1), "Next on the last page stays there")
        assertEquals(1, group.turn(2, -1))
        assertEquals("Page 1 / 3", group.label(0))
        assertEquals("Page 3 / 3", group.label(2))
        assertEquals(1, group.indexOf("B"))
        assertEquals(-1, group.indexOf("not a page"))
    }

    @Test
    fun `a group needs pages, no shop twice and one label per page`() {
        assertFailsWith<IllegalArgumentException> { ShopPageGroups.Group("empty", emptyList()) }
        assertFailsWith<IllegalArgumentException> { ShopPageGroups.Group("twice", listOf("A", "A")) }
        assertFailsWith<IllegalArgumentException> { ShopPageGroups.Group("labels", listOf("A", "B"), labels = listOf("a")) }
    }

    @Test
    fun `registering finds every page, replaces itself and refuses a shop in two groups`() {
        val first = ShopPageGroups.Group("test-register", listOf("test-register 1", "test-register 2"))
        ShopPageGroups.register(first)
        assertSame(first, ShopPageGroups.groupOf("test-register 2"))
        // A plugin reload registers the group again: the new definition wins, a dropped page leaves the group.
        val again = ShopPageGroups.Group("test-register", listOf("test-register 1"))
        ShopPageGroups.register(again)
        assertSame(again, ShopPageGroups.groupOf("test-register 1"))
        assertNull(ShopPageGroups.groupOf("test-register 2"))
        assertFailsWith<IllegalArgumentException> { ShopPageGroups.register(ShopPageGroups.Group("other", listOf("test-register 1"))) }
        assertNull(ShopPageGroups.groupOf("a shop nobody grouped"), "a shop outside every group shows no page row")
    }

    /** Pikkupstix's Trade opens the supplies shop; Next page walks the pouch pages, then the scroll pages. */
    @Test
    fun `Pikkupstix trade opens the supplies shop first, then pouches, then scrolls`() {
        val group = SummoningShopStock.PAGE_GROUP
        assertEquals(SummoningShopStock.SUPPLIES_SHOP, group.pages.first(), "the shop Trade opens")
        assertEquals(listOf(SummoningShopStock.SUPPLIES_SHOP) + SummoningShopStock.pages.map { it.title }, group.pages)
        val pouchPages = SummoningShopStock.pages.indexOfLast { it.title.startsWith("Pikkupstix's Pouches") }
        val scrollPages = SummoningShopStock.pages.indexOfFirst { it.title.startsWith("Pikkupstix's Scrolls") }
        assertTrue(pouchPages >= 0 && scrollPages > pouchPages, "pouch pages before scroll pages: ${group.pages}")
        // Walking Next from page 1 visits every page once and stops on the last.
        val walked = generateSequence(0) { i -> group.turn(i, 1).takeIf { it != i } }.toList()
        assertEquals(group.pages.indices.toList(), walked)
        // The chat chooser is only the fallback for a cache without the page row; it must still offer every page.
        assertTrue(group.labels.size <= MAX_DIALOG_OPTIONS, "${group.labels.size} pages do not fit the fallback chooser")
        assertTrue(SummoningShopStock.pages.all { it.stock.size <= Shop.CLIENT_VISIBLE_SLOTS })
    }
}
