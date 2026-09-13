package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.sendItemContainer

/**
 * The real "Familiar Inventory" window - interface 671 plus the inventory-side panel 665.
 *
 * Evidence (decoded from this cache, full table in `C:\RSPS\RSPS_SUMMONING_2011_EVIDENCE.md`):
 *
 *  - `runInterfaceHookProbeTool layout 671` gives 671:14 = the "Familiar Inventory" title,
 *    671:13 = `op1 'Close'`, 671:29 = `op1 'Take BoB'`, and 671:27 = a bare `LAYER` at
 *    `box=88,65 308x244`. The six vertical divider graphics 671:20..25 sit at x = 116, 170, 224,
 *    278, 332 and 386 (pitch 54) and the four horizontal ones 671:16..19 at y = 87, 141, 195, 249
 *    (pitch 54): a **6 x 5 = 30 cell** grid, which is exactly the pack yak's 30-item capacity,
 *    i.e. the largest beast of burden in the game. 671:27 is that grid.
 *  - `runInterfaceHookProbeTool layout 665` gives a single `LAYER` at `box=16,8 162x250`, the
 *    4 x 7 = 28 cell shape of the player's own backpack. That is the inventory panel that sits
 *    alongside the window in the reference screenshot.
 *  - `refs` finds **no** cs2 script referencing any 671 component, and neither 671:27 nor 665:0
 *    carries an `onInvTransmit` hook. Both grids are therefore built by the server the same way
 *    every other dynamic item grid in this codebase is: client script 150, whose real signature
 *    `disasm 150` -> `disasm 153` shows to be
 *    `(componentHash, inv, columns, rows, flags, op1..opN)`. This is the identical call
 *    `gg.rsmod.plugins.content.inter.bank.Bank.openDepositBox` already makes for the deposit box.
 *
 * The one value here that the cache cannot supply is [FAMILIAR_INV], the *transport* key the
 * `UPDATE_INV_FULL` packet labels the familiar's items with. The client stores incoming
 * containers in a plain id-keyed map (`ClientInventory.setSlot`, no cache lookup and no
 * validation), so this id is invisible to the player and constrained only by not colliding with
 * another container this server sends. It is disclosed as a server-internal choice rather than
 * presented as recovered cache data; 530 is the id the 667/718-era server community has long used
 * for this container, which keeps us compatible with anything else built against that convention.
 */
object FamiliarInventory {
    const val WINDOW_INTERFACE = 671
    const val GRID_COMPONENT = 27
    const val TAKE_BOB_COMPONENT = 29
    const val CLOSE_COMPONENT = 13
    const val SIDE_INTERFACE = 665
    const val SIDE_COMPONENT = 0

    /** Server-internal transport key for the familiar's container - see the class kdoc. */
    const val FAMILIAR_INV = 530

    /** The player's own backpack container key, as used everywhere else in this engine. */
    private const val INVENTORY_INV = 93

    private const val GRID_COLUMNS = 6
    private const val GRID_ROWS = 5
    private const val GRID_SLOTS = GRID_COLUMNS * GRID_ROWS

    private const val SIDE_COLUMNS = 4
    private const val SIDE_ROWS = 7

    /**
     * `IF_SETEVENTS` bitmask: ops 1..6 plus op10 (examine). Same value the deposit box uses
     * (`setting = 1150` = 0x47E), so the meaning of each bit is established by existing working
     * code rather than assumed.
     */
    private const val ITEM_OPTION_EVENTS = 0x47E

    /** Set while 671 is open, so item clicks can't be replayed against a closed window. */
    private val OPEN_ATTR = AttributeKey<Boolean>()

    fun isOpen(player: Player): Boolean = player.attr.getOrDefault(OPEN_ATTR, false)

    /**
     * Opens the window for the active familiar. Beasts of burden get Store/Withdraw both ways;
     * foragers, which "you are only able to 'Withdraw' items from", get a read-only backpack
     * panel - the grid still withdraws, the inventory side simply offers no Store option.
     */
    fun open(player: Player) {
        val npc = Familiar.current(player)
        if (npc == null || !BeastOfBurden.isCarrierNpc(npc.id)) {
            player.message("You don't have a familiar that can carry items.")
            return
        }
        val withdrawOnly = BeastOfBurden.isWithdrawOnlyNpc(npc.id)
        player.attr[OPEN_ATTR] = true
        player.openInterface(WINDOW_INTERFACE, InterfaceDestination.MAIN_SCREEN)
        player.openInterface(SIDE_INTERFACE, InterfaceDestination.TAB_AREA)

        player.runClientScript(
            150,
            (WINDOW_INTERFACE shl 16) or GRID_COMPONENT,
            FAMILIAR_INV,
            GRID_COLUMNS,
            GRID_ROWS,
            0,
            "Withdraw-1",
            "Withdraw-5",
            "Withdraw-10",
            "Withdraw-All",
            "Withdraw-X",
        )
        player.setEvents(WINDOW_INTERFACE, GRID_COMPONENT, to = GRID_SLOTS, setting = ITEM_OPTION_EVENTS)

        if (withdrawOnly) {
            player.runClientScript(
                150,
                (SIDE_INTERFACE shl 16) or SIDE_COMPONENT,
                INVENTORY_INV,
                SIDE_COLUMNS,
                SIDE_ROWS,
                0,
            )
            player.setEvents(SIDE_INTERFACE, SIDE_COMPONENT, to = player.inventory.capacity, setting = 0x400)
        } else {
            player.runClientScript(
                150,
                (SIDE_INTERFACE shl 16) or SIDE_COMPONENT,
                INVENTORY_INV,
                SIDE_COLUMNS,
                SIDE_ROWS,
                0,
                "Store-1",
                "Store-5",
                "Store-10",
                "Store-All",
                "Store-X",
            )
            player.setEvents(SIDE_INTERFACE, SIDE_COMPONENT, to = player.inventory.capacity, setting = ITEM_OPTION_EVENTS)
        }
        refresh(player)
        // The tab-strip re-arm after mounting over the tab area is done once in PlayerExt.openInterface (RCV-012 B5).
    }

    /**
     * Pushes both containers. The familiar's is padded to the full 30-cell grid so the client
     * renders the same window for a 3-slot thorny snail as for a 30-slot pack yak - real RS shows
     * one fixed-size window and simply refuses items past the familiar's own capacity, which is
     * enforced server-side by [BeastOfBurden.deposit] against the container's real capacity.
     */
    fun refresh(player: Player) {
        val container = BeastOfBurden.activeContainer(player)
        val padded = arrayOfNulls<Item>(GRID_SLOTS)
        if (container != null) {
            for (slot in 0 until minOf(container.capacity, GRID_SLOTS)) {
                padded[slot] = container[slot]
            }
        }
        player.sendItemContainer(FAMILIAR_INV, padded)
        player.inventory.dirty = true
    }

    /**
     * Closes the window. Called by 671's own Close button and by every path that takes the
     * familiar away (dismiss, expiry, death, logout), so a stale window can never be left open
     * over a familiar that no longer exists.
     */
    fun close(player: Player) {
        if (!isOpen(player)) return
        player.closeInterface(WINDOW_INTERFACE)
        onClosed(player)
    }

    /**
     * Restores the sidebar after the window goes away by any route, including the player pressing
     * Escape or clicking elsewhere - that path never runs [close], so the tab area would otherwise
     * be left showing 665 instead of the backpack.
     */
    fun onClosed(player: Player) {
        if (!isOpen(player)) return
        player.attr[OPEN_ATTR] = false
        player.closeInterface(InterfaceDestination.TAB_AREA)
        player.openInterface(InterfaceDestination.INVENTORY_TAB)
        player.inventory.dirty = true
        // The re-arm after restoring the tab area is done once in PlayerExt.closeInterface (RCV-012 B5).
    }

    /**
     * Amount for an item-grid click, decoded from the same [gg.rsmod.game.message.impl] opcodes
     * the bank's own container handlers use: 61/64/4 are ops 1..3 ("-1"/"-5"/"-10"), 91 is "-All"
     * and 81 is "-X".
     */
    fun amountFor(
        opcode: Int,
        available: Int,
    ): Int =
        when (opcode) {
            61 -> 1
            64 -> 5
            4 -> 10
            91 -> available
            52 -> available
            else -> 0
        }

    /** True when the click was the "-X" option and therefore needs an amount prompt. */
    fun isEnterAmount(opcode: Int): Boolean = opcode == 81
}
