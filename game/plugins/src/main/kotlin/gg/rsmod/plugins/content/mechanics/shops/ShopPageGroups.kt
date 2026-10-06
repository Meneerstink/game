package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.CURRENT_SHOP_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.plugins.api.ext.openShop
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import java.util.concurrent.ConcurrentHashMap
import gg.rsmod.game.tools.importer.ShopPageInterfaceImportTool as Ui

/**
 * Shops split over several pages of the 667 shop window (owner 2026-10-06, bug 204956 / OWNER_QUESTIONS Q2 = A: "Trade opens the
 * shop directly on page 1; the shop gets Previous/Next page buttons like the Loyalty/Donator store screen").
 *
 * A page is one ordinary [Shop]; a [Group] is the ordered list of their names. [open] shows page 1; the "Prev page" / "Next page"
 * row that `ShopPageInterfaceImportTool` appends to interface 620 switches to the previous/next shop of the group, clamped at both
 * ends like the 78 Store (`StoreUi.turnPage`). Turning a page re-opens the shop window through [openShop] (which closes the open
 * one first), so stock, samples, prices, the sell control and the inventory side panel are exactly those of a freshly traded shop.
 * The row is shown only while the open shop belongs to a group ([render], called by every [openShop]) and hidden otherwise.
 */
object ShopPageGroups {
    /** An ordered set of shop pages; [labels] are the dialogue lines of the fallback page chooser (cache without the page row). */
    class Group(val name: String, val pages: List<String>, val labels: List<String> = pages, val points: Boolean = false) {
        init {
            require(pages.isNotEmpty()) { "page group '$name' has no pages" }
            require(pages.distinct().size == pages.size) { "page group '$name' lists a shop twice" }
            require(labels.size == pages.size) { "page group '$name': ${labels.size} labels for ${pages.size} pages" }
        }

        fun indexOf(shop: String): Int = pages.indexOf(shop)

        /** The page [delta] away from [from], clamped to the first/last page. */
        fun turn(from: Int, delta: Int): Int = (from + delta).coerceIn(0, pages.size - 1)

        fun label(index: Int): String = "Page ${index + 1} / ${pages.size}"
    }

    private val byShop = ConcurrentHashMap<String, Group>()

    /** Registers [group] (plugin load); a re-registered group of the same name replaces itself, a shop in two groups is refused. */
    fun register(group: Group) {
        group.pages.forEach { shop ->
            val other = byShop[shop]
            require(other == null || other.name == group.name) { "shop '$shop' is already a page of group '${other!!.name}'" }
        }
        byShop.values.removeIf { it.name == group.name }
        group.pages.forEach { byShop[it] = group }
    }

    fun groupOf(shop: String): Group? = byShop[shop]

    private const val OP1 = 0x2

    @Volatile
    private var navAvailable: Boolean? = null

    /** Whether the cache's shop window has the page row (`ShopPageInterfaceImportTool --apply`); read once from 620's file count. */
    fun navAvailable(world: World): Boolean =
        navAvailable ?: run {
            val count = runCatching { world.filestore.index(INTERFACE_INDEX).archive(Ui.INTERFACE_ID)?.fileIds()?.size ?: 0 }.getOrDefault(0)
            val available = count >= Ui.COMPONENT_COUNT
            if (!available) {
                Trace.problem(Trace.Cat.INTERFACE) {
                    "shop interface ${Ui.INTERFACE_ID} has $count components, not the ${Ui.COMPONENT_COUNT} with the page row: the cache still lacks " +
                        "620:${Ui.NAV}..${Ui.PAGE_NEXT} - run ShopPageInterfaceImportTool --apply with the servers stopped (page groups fall back to a chat chooser)"
                }
            }
            navAvailable = available
            available
        }

    private const val INTERFACE_INDEX = 3

    /** Trade on a paged shop: page 1 directly. Without the page row in the cache, the pages are offered in a chat chooser instead. */
    fun open(
        player: Player,
        group: Group,
    ) {
        if (navAvailable(player.world)) {
            openPage(player, group, 0)
            return
        }
        player.queue {
            val choice = options(*group.labels.toTypedArray(), title = "What would you like to buy?")
            if (choice in 1..group.pages.size) openPage(player, group, choice - 1)
        }
    }

    private fun openPage(
        player: Player,
        group: Group,
        index: Int,
    ) {
        val name = group.pages[index]
        if (player.world.getShop(name) == null) {
            Trace.problem(Trace.Cat.INTERFACE) { "page group '${group.name}' page ${index + 1} names shop '$name', which does not exist" }
            return
        }
        player.openShop(name, points = group.points)
    }

    /** "Prev page" ([delta] -1) / "Next page" (+1) on the shop window. */
    fun turnPage(
        player: Player,
        delta: Int,
    ) {
        val shop = player.attr[CURRENT_SHOP_ATTR]
        val group = shop?.let { groupOf(it.name) }
        if (shop == null || group == null) {
            Trace.problem(Trace.Cat.INTERFACE) { "${player.username} clicked shop page ${if (delta < 0) "Prev" else "Next"} while the open shop '${shop?.name}' is not in a page group" }
            return
        }
        val from = group.indexOf(shop.name)
        val to = group.turn(from, delta)
        if (to == from) return
        openPage(player, group, to)
    }

    /** Shows the page row with this shop's page for a group page, hides it for every other shop. Called by [openShop]. */
    fun render(
        player: Player,
        shop: Shop,
    ) {
        val group = groupOf(shop.name)
        // IF_SETHIDE / IF_SETTEXT on a component the cache does not have is never sent (the 667 client dereferences it).
        if (!navAvailable(player.world)) return
        player.setComponentHidden(Ui.INTERFACE_ID, Ui.NAV, group == null)
        if (group == null) return
        player.setInterfaceEvents(Ui.INTERFACE_ID, Ui.PAGE_PREVIOUS, -1..-1, OP1)
        player.setInterfaceEvents(Ui.INTERFACE_ID, Ui.PAGE_NEXT, -1..-1, OP1)
        player.setComponentText(Ui.INTERFACE_ID, Ui.PAGE_TEXT, group.label(group.indexOf(shop.name)))
    }
}
