package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * The costume room's storage on its real interface (owner 2026-10-02: "voor veel opties chatinterfaces ... dit moeten interfaces zijn").
 *
 * Interface 467 in this cache (read with the component dumper, 137 components): a scroll list ([LIST] 11, scrollbar 13, set up by its
 * own clientscript 2677) of 30 rows - row i has its name text 15 + 2i, its "Select" button 16 + 2i and a hidden highlight layer 77 + 2i
 * - the title 136 and the Close button 75. One row per set of [PohStorageSets] (2009scape StorageInterface: one "Select" stores the set
 * from the inventory or takes the stored set out; its messages too); a highlighted row is a stored set. More than 30 sets go over pages
 * with "More..." and "Back..." rows, as the donor pages.
 */
object PohStorageUi {
    const val INTERFACE = 467
    const val ROWS = 30
    const val TITLE = 136

    fun nameComponent(row: Int) = 15 + 2 * row

    fun buttonComponent(row: Int) = 16 + 2 * row

    fun markComponent(row: Int) = 77 + 2 * row

    /** The row of a "Select" button, or -1. */
    fun rowOf(component: Int): Int = if (component in 16..(16 + 2 * (ROWS - 1)) && component % 2 == 0) (component - 16) / 2 else -1

    private const val OP1 = 2

    private val FAMILY = AttributeKey<PohStorage.Family>()
    private val PAGE = AttributeKey<Int>()

    /** What a row shows: a set, or a page turn. */
    sealed class Row {
        class SetRow(val set: PohStorageSets.StorageSet) : Row()

        object More : Row()

        object Back : Row()
    }

    /** The rows of [page]: up to 30 sets, or 28 and the page turns when the family has more than fit. */
    fun rows(
        sets: List<PohStorageSets.StorageSet>,
        page: Int,
    ): List<Row> {
        if (sets.size <= ROWS) return sets.map { Row.SetRow(it) }
        val perPage = ROWS - 2
        val slice = sets.drop(page * perPage).take(perPage).map<PohStorageSets.StorageSet, Row> { Row.SetRow(it) }
        val more = sets.size > (page + 1) * perPage
        return slice + listOfNotNull(if (more) Row.More else null, if (page > 0) Row.Back else null)
    }

    fun open(
        player: Player,
        family: PohStorage.Family,
    ) {
        player.attr[FAMILY] = family
        player.attr[PAGE] = 0
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        render(player)
    }

    fun isStored(
        player: Player,
        set: PohStorageSets.StorageSet,
    ): Boolean {
        val stored = PohStorage.stored(player, set.family)
        return set.ids.any { it in stored }
    }

    private fun render(player: Player) {
        val family = player.attr[FAMILY] ?: return
        val rows = rows(PohStorageSets.of(family), player.attr[PAGE] ?: 0)
        player.setComponentText(INTERFACE, TITLE, family.displayName)
        for (row in 0 until ROWS) {
            val shown = rows.getOrNull(row)
            player.setComponentHidden(INTERFACE, nameComponent(row), shown == null)
            player.setComponentHidden(INTERFACE, buttonComponent(row), shown == null)
            player.setComponentHidden(INTERFACE, markComponent(row), !(shown is Row.SetRow && isStored(player, shown.set)))
            if (shown == null) continue
            val text =
                when (shown) {
                    is Row.SetRow -> shown.set.name
                    Row.More -> "More..."
                    Row.Back -> "Back..."
                }
            player.setComponentText(INTERFACE, nameComponent(row), text)
            player.setInterfaceEvents(INTERFACE, buttonComponent(row), -1..-1, OP1)
        }
    }

    /** A click on row [row]'s "Select". */
    fun select(
        player: Player,
        row: Int,
    ) {
        val family = player.attr[FAMILY] ?: return
        val page = player.attr[PAGE] ?: 0
        when (val clicked = rows(PohStorageSets.of(family), page).getOrNull(row) ?: return) {
            Row.More -> player.attr[PAGE] = page + 1
            Row.Back -> player.attr[PAGE] = (page - 1).coerceAtLeast(0)
            is Row.SetRow -> transfer(player, clicked.set)
        }
        render(player)
    }

    /** Takes the stored set out, or stores the set's items the player carries (2009scape StorageInterface messages). */
    private fun transfer(
        player: Player,
        set: PohStorageSets.StorageSet,
    ) {
        val stored = PohStorage.stored(player, set.family)
        val out = set.ids.filter { it in stored }
        if (out.isNotEmpty()) {
            if (player.inventory.freeSlotCount < out.size) {
                player.message("You don't have enough inventory space.")
                return
            }
            out.forEach { id ->
                if (PohStorage.take(player, set.family, id)) player.inventory.add(id)
            }
            player.message("You take the item from the box.")
            return
        }
        val carried = set.ids.filter { player.inventory.contains(it) }
        if (carried.isEmpty()) {
            player.message("You don't have that item in your inventory.")
            return
        }
        carried.forEach { id ->
            if (player.inventory.remove(id).hasSucceeded()) PohStorage.put(player, set.family, id)
        }
        player.message("You put the item into the box.")
    }
}
