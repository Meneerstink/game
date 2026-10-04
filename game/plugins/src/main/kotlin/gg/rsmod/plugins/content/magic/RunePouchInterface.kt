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
 * runes or withdrawing them"). OSRS opens a window with the pouch's rune slots - Withdraw-1 / Withdraw-5 / Withdraw-All / Withdraw-X -
 * next to the backpack, whose runes offer Store-1 / Store-5 / Store-All / Store-X (OSRS Wiki "Rune pouch": "the player can store and
 * withdraw runes through its interface").
 *
 * ADAPTED_TO_667: this client has no OSRS pouch interface; the window is the 667 storage pair the Beast of Burden uses - 671 (a grid
 * filled by clientscript 150 from any inventory, here [GRID_INV]) and 665 (the backpack beside it) - titled "Rune pouch" with the
 * BoB's "Take BoB" button hidden. Bank load-outs (OSRS 2023) are not part of it.
 */
object RunePouchInterface {
    const val WINDOW = 671
    const val GRID = 27
    const val TITLE = 14
    const val TAKE_BOB = 29
    const val SIDE = 665
    const val SIDE_GRID = 0

    /** Client-side inventory of the opened pouch's slots (the spellbook's own pouch inventory is 30001). */
    const val GRID_INV = 30002
    private const val INVENTORY_INV = 93
    private const val GRID_SLOTS = 30

    /** Ops 1..6 plus examine, the same events the Beast of Burden window uses (0x47E). */
    private const val ITEM_EVENTS = 0x47E

    /** Op opcodes as the BoB / bank handlers decode them: op1 61, op2 64, op3 4, op4 52. */
    private const val OP1 = 61
    private const val OP2 = 64
    private const val OP3 = 4
    private const val OP4 = 52
    const val EXAMINE = 25

    /** The inventory slot of the pouch the window shows. */
    private val OPEN_SLOT = AttributeKey<Int>()

    fun isOpen(player: Player): Boolean = player.attr[OPEN_SLOT] != null

    private fun pouch(player: Player): Pair<Int, Item>? {
        val slot = player.attr[OPEN_SLOT] ?: return null
        val pouch = player.inventory[slot]?.takeIf { RunePouch.isPouch(it.id) } ?: return null
        return slot to pouch
    }

    fun open(
        player: Player,
        pouchSlot: Int,
    ) {
        val pouch = player.inventory[pouchSlot]?.takeIf { RunePouch.isPouch(it.id) } ?: return
        player.attr[OPEN_SLOT] = pouchSlot
        player.openInterface(WINDOW, InterfaceDestination.MAIN_SCREEN)
        player.openInterface(SIDE, InterfaceDestination.TAB_AREA)
        player.setComponentText(WINDOW, TITLE, player.world.definitions.get(ItemDef::class.java, pouch.id).name)
        player.setComponentHidden(WINDOW, TAKE_BOB, true)
        player.runClientScript(150, (WINDOW shl 16) or GRID, GRID_INV, 6, 5, 0, "Withdraw-1", "Withdraw-5", "Withdraw-All", "Withdraw-X")
        player.setInterfaceEvents(WINDOW, GRID, 0 until GRID_SLOTS, ITEM_EVENTS)
        player.runClientScript(150, (SIDE shl 16) or SIDE_GRID, INVENTORY_INV, 4, 7, 0, "Store-1", "Store-5", "Store-All", "Store-X")
        player.setInterfaceEvents(SIDE, SIDE_GRID, 0 until player.inventory.capacity, ITEM_EVENTS)
        refresh(player)
    }

    fun refresh(player: Player) {
        val (_, pouch) = pouch(player) ?: return
        val items = arrayOfNulls<Item>(GRID_SLOTS)
        RunePouch.contents(pouch).forEachIndexed { i, rune -> if (i < GRID_SLOTS) items[i] = rune }
        player.sendItemContainer(GRID_INV, items)
        player.inventory.dirty = true
    }

    /** The amount a click asks for (op1 1, op2 5, op3 all), or null for op4 "-X" (an amount prompt). */
    private fun amountFor(
        opcode: Int,
        available: Int,
    ): Int? =
        when (opcode) {
            OP1 -> 1
            OP2 -> 5
            OP3 -> available
            OP4 -> null
            else -> 0
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
        val amount = amountFor(opcode, rune.amount)
        if (amount == null) {
            player.queue {
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

    fun store(
        player: Player,
        inventorySlot: Int,
        opcode: Int,
    ) {
        val item = player.inventory[inventorySlot] ?: return
        val available = player.inventory.getItemCount(item.id)
        val amount = amountFor(opcode, available)
        if (amount == null) {
            player.queue {
                val asked = inputInt("How many would you like to store?")
                if (asked > 0) put(player, item.id, asked)
            }
            return
        }
        if (amount > 0) put(player, item.id, amount)
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

    /** Restores the backpack tab however the window went away (its Close button, Escape, walking off). */
    fun onClosed(player: Player) {
        if (!isOpen(player)) return
        player.attr.remove(OPEN_SLOT)
        player.closeInterface(InterfaceDestination.TAB_AREA)
        player.openInterface(InterfaceDestination.INVENTORY_TAB)
        player.inventory.dirty = true
    }
}
