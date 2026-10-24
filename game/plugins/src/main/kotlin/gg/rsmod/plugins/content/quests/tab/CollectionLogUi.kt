package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.N108InterfaceImportTool
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.C
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * The Collection Log window (N104, owner "net als OSRS", GOEDGEKEURD 04): category tabs, the pages of that category on the left
 * (green when complete), the page on the right - its name, "Obtained: n/m" (red none / yellow some / green all), the kills and the
 * items: obtained in full colour with their count, the rest half transparent. Data: [CollectionLog], [CollectionLogPages].
 */
object CollectionLogUi {
    const val INTERFACE = N108InterfaceImportTool.LOG

    private val CATEGORY = AttributeKey<Int>()
    private val PAGE = AttributeKey<String>()
    private val SHOWN = AttributeKey<List<String>>()

    private const val EVENTS_OP1 = 0x2

    fun isOpen(player: Player): Boolean = player.interfaces.isVisible(INTERFACE)

    fun open(player: Player, pageKey: String? = null) {
        val pages = CollectionLogPages.all(player)
        val page = pageKey?.let { k -> pages.firstOrNull { it.key == k } } ?: CollectionLogPages.byKey(player, player.attr[PAGE]) ?: pages.firstOrNull()
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        N108Ui.reset(player, INTERFACE)
        player.setInterfaceEvents(INTERFACE, C.CLOSE, -1..-1, EVENTS_OP1)
        for (c in 0 until C.CATS) player.setInterfaceEvents(INTERFACE, C.cat(c) + 4, -1..-1, EVENTS_OP1)
        for (e in 0 until C.ENTRIES) player.setInterfaceEvents(INTERFACE, C.entry(e) + 2, -1..-1, EVENTS_OP1)
        // The items: "Inspect" (and with it the tooltip of the hovered item).
        for (s in 0 until C.SLOTS) {
            player.setInterfaceEvents(INTERFACE, C.slot(s), -1..-1, EVENTS_OP1)
            player.setInterfaceEvents(INTERFACE, C.slot(s) + 1, -1..-1, EVENTS_OP1)
        }
        player.attr[CATEGORY] = page?.category?.ordinal ?: 0
        player.attr[PAGE] = page?.key ?: ""
        draw(player)
    }

    fun category(player: Player, index: Int) {
        val pages = CollectionLogPages.all(player).filter { it.category.ordinal == index }
        player.attr[CATEGORY] = index
        player.attr[PAGE] = pages.firstOrNull()?.key ?: ""
        draw(player)
    }

    fun entry(player: Player, index: Int) {
        val key = player.attr[SHOWN]?.getOrNull(index) ?: return
        player.attr[PAGE] = key
        draw(player)
    }

    /** "Inspect" on an item: what it is and how many the player has collected. */
    fun inspect(player: Player, slot: Int) {
        val page = CollectionLogPages.byKey(player, player.attr[PAGE]) ?: return
        val item = page.items.getOrNull(slot) ?: return
        val entry = CollectionLog.entries(player)[item]
        val name = DropSources.itemName(player.world, item)
        player.message(
            if (entry == null) {
                "$name: not in your collection log yet."
            } else {
                "$name: obtained ${N108Ui.number(entry.count)}" + if (entry.kc > 0) ", the first at kill count ${N108Ui.number(entry.kc)}." else "."
            },
        )
    }

    private fun colourOf(have: Int, of: Int) = when {
        of > 0 && have >= of -> "0dc10d"
        have > 0 -> "ffff00"
        else -> "ff0000"
    }

    fun draw(player: Player) {
        if (!isOpen(player)) return
        val i = INTERFACE
        val all = CollectionLogPages.all(player)
        val cat = player.attr[CATEGORY] ?: 0
        val total = CollectionLogPages.allItems(player).size
        val got = CollectionLog.totalObtained(player)
        N108Ui.text(player, i, C.TOTAL, "Obtained: <col=${colourOf(got, total)}>$got/$total</col>")
        for (c in 0 until C.CATS) {
            val on = c == cat
            N108Ui.hidden(player, i, C.cat(c), !on)
            N108Ui.hidden(player, i, C.cat(c) + 1, on)
            N108Ui.hidden(player, i, C.cat(c) + 2, !on)
            N108Ui.hidden(player, i, C.cat(c) + 3, on)
        }
        val pages = all.filter { it.category.ordinal == cat }
        player.attr[SHOWN] = pages.map { it.key }
        val selected = pages.firstOrNull { it.key == player.attr[PAGE] } ?: pages.firstOrNull()
        for (e in 0 until C.ENTRIES) {
            val base = C.entry(e)
            val page = pages.getOrNull(e)
            N108Ui.hidden(player, i, base, page == null || page != selected)
            N108Ui.hidden(player, i, base + 1, page == null)
            N108Ui.hidden(player, i, base + 2, page == null)
            if (page == null) continue
            val done = page.items.isNotEmpty() && CollectionLog.obtained(player, page) >= page.items.size
            val colour = when {
                page == selected && done -> "7cff7c"
                page == selected -> "ffffff"
                done -> "0dc10d"
                else -> "ff981f"
            }
            N108Ui.text(player, i, base + 1, "<col=$colour>${page.name}</col>")
        }
        N108Ui.scroll(player, i, C.LIST, C.LIST_SCROLLBAR, maxOf(pages.size * C.ENTRY_H, C.LIST_H))
        if (selected == null) {
            N108Ui.text(player, i, C.NAME, "")
            N108Ui.text(player, i, C.OBTAINED, "")
            N108Ui.text(player, i, C.KILLS, "")
            N108Ui.hidden(player, i, C.EMPTY, false)
            N108Ui.text(player, i, C.EMPTY, "Nothing to collect here yet.")
            for (s in 0 until C.SLOTS) for (c in 0 until C.SLOT_STRIDE) N108Ui.hidden(player, i, C.slot(s) + c, true)
            return
        }
        N108Ui.hidden(player, i, C.EMPTY, true)
        val entries = CollectionLog.entries(player)
        val have = CollectionLog.obtained(player, selected)
        N108Ui.text(player, i, C.NAME, selected.name)
        N108Ui.text(player, i, C.OBTAINED, "Obtained: <col=${colourOf(have, selected.items.size)}>$have/${selected.items.size}</col>")
        if (selected.sources.isNotEmpty()) {
            N108Ui.text(player, i, C.KILLS, "${selected.countLabel ?: "${selected.name} kills"}: <col=ffffff>${N108Ui.number(KillCounts.ofPage(player, selected))}</col>")
        } else {
            N108Ui.text(player, i, C.KILLS, "")
        }
        val tips = ArrayList<Pair<Int, String>>()
        for (s in 0 until C.SLOTS) {
            val base = C.slot(s)
            val item = selected.items.getOrNull(s)
            val entry = item?.let { entries[it] }
            N108Ui.hidden(player, i, base, item == null || entry == null)
            N108Ui.hidden(player, i, base + 1, item == null || entry != null)
            N108Ui.hidden(player, i, base + 2, entry == null)
            if (item == null) continue
            val name = DropSources.itemName(player.world, item)
            if (entry != null) {
                N108Ui.item(player, i, base, item, 1)
                N108Ui.text(player, i, base + 2, N108Ui.number(entry.count))
                tips += N108Ui.hash(i, base) to "$name" + if (entry.kc > 0) "\nObtained at KC ${N108Ui.number(entry.kc)}" else ""
            } else {
                N108Ui.item(player, i, base + 1, item, 1)
                tips += N108Ui.hash(i, base + 1) to name
            }
        }
        if (tips.isNotEmpty()) N108Ui.tooltips(player, tips)
        val rows = (selected.items.size + C.COLUMNS - 1) / C.COLUMNS
        N108Ui.scroll(player, i, C.ITEMS, C.ITEMS_SCROLLBAR, maxOf(8 + rows * C.PITCH_Y, C.ITEMS_H))
    }
}
