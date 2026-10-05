package gg.rsmod.plugins.content.magic

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.sendItemContainer
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * The rune pouch window (owner 2026-10-04 024434: "the rune pouch and the divine rune pouch need an GUI just like osrs when storing
 * runes or withdrawing them"). OSRS interface 190, built into the 667 cache as [WINDOW] by `RunePouchInterfaceImportTool`: the
 * "Pouch" box with the pouch's rune slots, the "Inventory" box with the runes the player carries, and the 1 / 5 / X / All quantity
 * buttons below them. As in OSRS, a left-click moves the chosen quantity (Withdraw from the pouch, Store from the inventory) and
 * the right-click menu offers every quantity. The rune slots are the 667 item grid (clientscript 150 on [POUCH_GRID] /
 * [INVENTORY_GRID], filled from the client-only inventories [GRID_INV] / [CARRIED_INV]). Bank load-outs (OSRS 2023) are not part
 * of it.
 */
object RunePouchInterface {
    const val WINDOW = 1177
    const val TITLE = 11
    const val CLOSE = 12
    const val POUCH_GRID = 15
    const val INVENTORY_GRID = 18

    /** Quantity buttons 1 / 5 / X / All (RunePouchInterfaceImportTool): button 19 + 3i, its highlight one above it. */
    const val BUTTON_FIRST = 19
    const val BUTTON_STRIDE = 3
    val QUANTITIES = listOf("1", "5", "X", "All")

    /** Client-side inventory of the opened pouch's slots (the spellbook's own pouch inventory is 30001). */
    const val GRID_INV = 30002

    /** Client-side inventory of the runes the player carries, in backpack order, packed (OSRS shows only runes there). */
    const val CARRIED_INV = 30003
    private const val CARRIED_SLOTS = 28

    private const val GRID_SCRIPT = 150

    /** Ops 1..6 plus examine, the same events the Beast of Burden / bank grids use (0x47E). */
    private const val ITEM_EVENTS = 0x47E

    /** Op opcodes as the bank / BoB handlers decode them: op1 61, op2 64, op3 4, op4 52; 25 = examine. */
    private val OPCODES = listOf(61, 64, 4, 52)
    const val EXAMINE = 25

    /** The inventory slot of the pouch the window shows. */
    private val OPEN_SLOT = AttributeKey<Int>()

    /** The chosen quantity (index into [QUANTITIES]); kept like OSRS's own setting. */
    private val QUANTITY = AttributeKey<Int>(persistenceKey = "rune_pouch_quantity")

    /** Rune ids in [CARRIED_INV] slot order, as last sent. */
    private val CARRIED = AttributeKey<List<Int>>()

    fun isOpen(player: Player): Boolean = player.attr[OPEN_SLOT] != null

    private fun pouch(player: Player): Pair<Int, Item>? {
        val slot = player.attr[OPEN_SLOT] ?: return null
        val pouch = player.inventory[slot]?.takeIf { RunePouch.isPouch(it.id) } ?: return null
        return slot to pouch
    }

    private fun quantity(player: Player): Int = (player.attr[QUANTITY] ?: 0).coerceIn(0, QUANTITIES.size - 1)

    /** The quantity each op stands for: op1 the chosen one (left-click), then the others in button order. */
    fun opOrder(selected: Int): List<Int> = listOf(selected) + QUANTITIES.indices.filter { it != selected }

    fun open(
        player: Player,
        pouchSlot: Int,
    ) {
        val pouch = player.inventory[pouchSlot]?.takeIf { RunePouch.isPouch(it.id) } ?: return
        player.attr[OPEN_SLOT] = pouchSlot
        player.openInterface(WINDOW, InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(WINDOW, TITLE, player.world.definitions.get(ItemDef::class.java, pouch.id).name.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } })
        // if3 ops are clickable only once the server enables them (op1 = bit 1), as the quiver / grave windows do.
        player.setInterfaceEvents(WINDOW, CLOSE, -1..-1, 0x2)
        QUANTITIES.indices.forEach { i -> player.setInterfaceEvents(WINDOW, BUTTON_FIRST + i * BUTTON_STRIDE, -1..-1, 0x2) }
        drawGrids(player)
        refresh(player)
    }

    /** The two grids and their ops for the chosen quantity, and the button highlight. */
    private fun drawGrids(player: Player) {
        val (_, pouch) = pouch(player) ?: return
        val order = opOrder(quantity(player))
        val withdraw = order.map { "Withdraw-${QUANTITIES[it]}" }
        val store = order.map { "Store-${QUANTITIES[it]}" }
        val slots = RunePouch.slots(pouch.id)
        player.runClientScript(GRID_SCRIPT, (WINDOW shl 16) or POUCH_GRID, GRID_INV, slots, 1, 0, *withdraw.toTypedArray())
        player.setInterfaceEvents(WINDOW, POUCH_GRID, 0 until slots, ITEM_EVENTS)
        player.runClientScript(GRID_SCRIPT, (WINDOW shl 16) or INVENTORY_GRID, CARRIED_INV, 7, 4, 0, *store.toTypedArray())
        player.setInterfaceEvents(WINDOW, INVENTORY_GRID, 0 until CARRIED_SLOTS, ITEM_EVENTS)
        QUANTITIES.indices.forEach { i -> player.setComponentHidden(WINDOW, BUTTON_FIRST + i * BUTTON_STRIDE + 1, i != quantity(player)) }
    }

    fun refresh(player: Player) {
        val (_, pouch) = pouch(player) ?: return
        val stored = arrayOfNulls<Item>(RunePouch.slots(pouch.id))
        RunePouch.contents(pouch).forEachIndexed { i, rune -> if (i < stored.size) stored[i] = rune }
        player.sendItemContainer(GRID_INV, stored)
        val carried = RunePouch.RUNES.let { runes -> player.inventory.rawItems.filterNotNull().filter { it.id in runes } }.take(CARRIED_SLOTS)
        player.attr[CARRIED] = carried.map { it.id }
        val shown = arrayOfNulls<Item>(CARRIED_SLOTS)
        carried.forEachIndexed { i, rune -> shown[i] = Item(rune.id, rune.amount) }
        player.sendItemContainer(CARRIED_INV, shown)
        player.inventory.dirty = true
    }

    /** A quantity button (1 / 5 / X / All). */
    fun selectQuantity(
        player: Player,
        index: Int,
    ) {
        if (!isOpen(player) || index !in QUANTITIES.indices) return
        player.attr[QUANTITY] = index
        drawGrids(player)
        refresh(player)
    }

    /** The amount a click asks for, or null for "-X" (an amount prompt). */
    private fun amountFor(
        player: Player,
        opcode: Int,
        available: Int,
    ): Int? {
        val op = OPCODES.indexOf(opcode)
        if (op < 0) return 0
        return when (QUANTITIES[opOrder(quantity(player))[op]]) {
            "1" -> 1
            "5" -> 5
            "All" -> available
            else -> null
        }
    }

    fun withdraw(
        player: Player,
        gridSlot: Int,
        opcode: Int,
    ) {
        val (_, pouch) = pouch(player) ?: return
        val rune = RunePouch.contents(pouch).getOrNull(gridSlot) ?: return
        if (opcode == EXAMINE) {
            player.world.sendExamine(player, rune.id, gg.rsmod.game.model.ExamineEntityType.ITEM)
            return
        }
        val amount = amountFor(player, opcode, rune.amount)
        if (amount == null) {
            // WEAK: a STANDARD task waits for the open window (a modal) to close, so the amount prompt never showed.
            player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) {
                val asked = inputInt("How many would you like to withdraw?")
                if (asked > 0) take(player, rune.id, asked)
            }
            return
        }
        if (amount > 0) take(player, rune.id, amount)
    }

    private fun take(
        player: Player,
        runeId: Int,
        amount: Int,
    ) {
        val (slot, pouch) = pouch(player) ?: return
        val taken = minOf(amount, RunePouch.count(pouch, runeId))
        if (taken <= 0) return
        val added = player.inventory.add(runeId, taken).completed
        if (added <= 0) {
            player.message("You don't have enough inventory space.")
            return
        }
        player.inventory[slot] = RunePouch.withdraw(pouch, runeId, added)
        refresh(player)
    }

    /** A click on a carried rune in the window's Inventory box. */
    fun store(
        player: Player,
        carriedSlot: Int,
        opcode: Int,
    ) {
        val runeId = player.attr[CARRIED]?.getOrNull(carriedSlot) ?: return
        if (opcode == EXAMINE) {
            player.world.sendExamine(player, runeId, gg.rsmod.game.model.ExamineEntityType.ITEM)
            return
        }
        val available = player.inventory.getItemCount(runeId)
        val amount = amountFor(player, opcode, available)
        if (amount == null) {
            player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) {
                val asked = inputInt("How many would you like to store?")
                if (asked > 0) put(player, runeId, asked)
            }
            return
        }
        if (amount > 0) put(player, runeId, amount)
    }

    private fun put(
        player: Player,
        runeId: Int,
        amount: Int,
    ) {
        val (slot, pouch) = pouch(player) ?: return
        val added = RunePouch.space(pouch, runeId, minOf(amount, player.inventory.getItemCount(runeId)))
        if (added <= 0) {
            player.message(if (runeId in RunePouch.RUNES) "Your pouch has no space for that rune." else "You can only store runes in the pouch.")
            return
        }
        player.inventory.remove(runeId, added)
        player.inventory[slot] = RunePouch.deposit(player.inventory[slot] ?: pouch, runeId, added)
        refresh(player)
    }

    fun close(player: Player) {
        if (!isOpen(player)) return
        player.closeInterface(WINDOW)
        onClosed(player)
    }

    /** However the window went away (its Close button, Escape, walking off). */
    fun onClosed(player: Player) {
        if (!isOpen(player)) return
        player.attr.remove(OPEN_SLOT)
        player.attr.remove(CARRIED)
        player.inventory.dirty = true
    }
}
