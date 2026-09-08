package gg.rsmod.plugins.content.inter.pricecheck

import getInterfaceHash
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*

/**
 * The Price Checker, opened from the "Show Price-checker" button on the worn-equipment tab.
 *
 * This was never implemented on this server - there is no handler for 387:42 anywhere - so the
 * button drew its menu entry and did nothing. Everything below is built from what the cache says
 * interface 206 expects, read with
 * `./gradlew :game:runInterfaceHookProbeTool --args="../data/cache layout 206"` and the clientscripts
 * that layout names:
 *
 *  - 206:15 is a layer whose `onLoad` and `onInvTransmit`/`onVarcTransmit` hooks are clientscripts
 *    2182/2183, both of which call 2184, and whose triggers are `inventoryTriggers=[90]` and
 *    `varcTriggers=[700..728]`. So the client rebuilds the screen by itself whenever container
 *    [CONTAINER_KEY] or one of those varcs is sent - the server never has to draw the grid;
 *  - the hook arguments are the component hashes 13500432 and 13500434, which are 206:16 (the item
 *    grid) and 206:18 (the total line);
 *  - clientscript 2184 lays container 90 out over 206:16 in four columns and calls `cc_setop` with
 *    "Remove-1", "Remove-5", "Remove-10", "Remove-All", "Remove-X" and "Examine" - so those six ops
 *    are the grid's, in that order, and [GRID_OPS] has to enable exactly them;
 *  - 2184 reads varc [TOTAL_VALUE_VARC] for the "Total value:" line, and calls clientscript 2185
 *    per slot. 2185 is a 28-case switch that pushes one of varcs 700..727, which is what makes
 *    [SLOT_VALUE_VARC] + slot the value of the item in that slot.
 *
 * `cc_setop` only sets an op's *text*: `ScriptRunner` (opcode 1300, `CC_IF_SETOP`) writes
 * `component.setOp(...)` and nothing else, and a click is only transmitted if the server has
 * enabled that op with an IF_SETEVENTS. That is why both [setInterfaceEvents] calls in [open] are
 * required even though the cache builds the grid.
 *
 * Two things here are deliberate server-side choices rather than sourced facts, because the cache
 * does not state them:
 *
 *  - interface 206 has no inventory of its own, and the cache holds no price-checker-specific
 *    inventory overlay, so the player's items are shown in the same bare, entirely server-driven
 *    overlay the trade screen uses ([OVERLAY_INTERFACE_ID], a single component with no cache
 *    hooks). Its op labels ("Check", "Check-5", ...) are ours;
 *  - an item's value is `ItemDef.cost`, the same valuation `TradeSession` already uses for the
 *    trade screen's wealth figures. This server has no Grand Exchange guide price to read.
 */
object PriceChecker {
    const val INTERFACE_ID = 206

    /** The bare inventory overlay the trade screen also uses; see the class note. */
    const val OVERLAY_INTERFACE_ID = 336

    /** 206:16, the item grid clientscript 2184 builds over container [CONTAINER_KEY]. */
    const val GRID_COMPONENT = 16

    /** 206:13, `Close`. The component bakes op1 itself, so the click always reaches us. */
    const val CLOSE_COMPONENT = 13

    /** The overlay's only component. */
    const val OVERLAY_COMPONENT = 0

    /** An inventory's worth of slots, which is also the range of the per-slot value varcs. */
    const val CAPACITY = 28

    /** Container 90, the `inventoryTriggers` of 206:15. */
    private const val CONTAINER_KEY = 90

    /** Container 93, the player's real inventory, which is what the overlay shows. */
    private const val INVENTORY_KEY = 93

    /** varcs 700..727, one per slot, read by clientscript 2185. */
    private const val SLOT_VALUE_VARC = 700

    /** varc 728, the "Total value:" figure. */
    private const val TOTAL_VALUE_VARC = 728

    /** Ops 1-5 on the inventory overlay: the five "Check" amounts. Same mask the trade screen uses. */
    private const val INVENTORY_OPS = 1086

    /** Ops 1-6 on the grid: "Remove-1" through "Remove-X", then "Examine". */
    private const val GRID_OPS = 1278

    val CONTAINER_ATTR = AttributeKey<ItemContainer>()

    fun container(player: Player): ItemContainer? = player.attr[CONTAINER_ATTR]

    fun isOpen(player: Player): Boolean = player.attr.has(CONTAINER_ATTR)

    fun open(player: Player) {
        if (isOpen(player)) {
            return
        }
        player.attr[CONTAINER_ATTR] = ItemContainer(player.world.definitions, CAPACITY, ContainerStackType.NORMAL)

        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.openInterface(OVERLAY_INTERFACE_ID, InterfaceDestination.TAB_AREA)

        player.setInterfaceEvents(OVERLAY_INTERFACE_ID, OVERLAY_COMPONENT, 0 until CAPACITY, INVENTORY_OPS)
        player.setInterfaceEvents(INTERFACE_ID, GRID_COMPONENT, 0 until CAPACITY, GRID_OPS)
        player.runClientScript(
            INTERFACE_INV_INIT_BIG,
            OVERLAY_INTERFACE_ID.getInterfaceHash(),
            INVENTORY_KEY,
            4,
            7,
            0,
            -1,
            "Check",
            "Check-5",
            "Check-10",
            "Check-All",
            "Check-X",
        )

        player.inventory.dirty = true
        refresh(player)
    }

    /**
     * Moves [amount] of the item in inventory slot [slot] onto the price checker.
     *
     * The items really do leave the inventory, which is what keeps the overlay honest: it is the
     * player's own inventory container, not a copy, so it needs no separate bookkeeping.
     */
    fun check(
        player: Player,
        slot: Int,
        amount: Int,
    ) {
        val container = container(player) ?: return
        val item = player.inventory[slot] ?: return
        val count = Math.min(amount, player.inventory.getItemCount(item.id))
        if (count <= 0) {
            return
        }

        val removal = player.inventory.remove(item.id, count, assureFullRemoval = true, beginSlot = slot)
        if (removal.hasSucceeded()) {
            val insertion = container.add(item.id, count, assureFullInsertion = true)
            if (!insertion.hasSucceeded()) {
                // Unreachable while the containers are the same size, but an item must never be
                // destroyed by a screen that only exists to look at prices.
                player.inventory.add(item.id, count)
                player.message("You can't check any more items at once.")
            }
        }
        refresh(player)
    }

    /**
     * Takes [amount] of the item in price-checker slot [slot] back off the screen.
     */
    fun uncheck(
        player: Player,
        slot: Int,
        amount: Int,
    ) {
        val container = container(player) ?: return
        val item = container[slot] ?: return
        val count = Math.min(amount, container.getItemCount(item.id))
        if (count <= 0) {
            return
        }

        val removal = container.remove(item.id, count, assureFullRemoval = true, beginSlot = slot)
        if (removal.hasSucceeded()) {
            val insertion = player.inventory.add(item.id, count, assureFullInsertion = true)
            if (!insertion.hasSucceeded()) {
                container.add(item.id, count)
                player.message("You don't have enough inventory space.")
            }
        }
        refresh(player)
    }

    /**
     * Gives back everything on the screen and forgets the session. Runs whenever interface 206
     * closes for any reason, and on logout, so there is no path that leaves items in the container.
     */
    fun close(player: Player) {
        val container = player.attr[CONTAINER_ATTR] ?: return
        player.attr.remove(CONTAINER_ATTR)

        container.rawItems.filterNotNull().forEach { item ->
            val insertion = player.inventory.add(item.id, item.amount, assureFullInsertion = false)
            val leftOver = insertion.getLeftOver()
            if (leftOver > 0) {
                player.world.spawn(GroundItem(item.id, leftOver, player.tile, player))
            }
        }
        container.removeAll()

        player.closeInterface(OVERLAY_INTERFACE_ID)
        player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
        player.inventory.dirty = true
    }

    /**
     * Sends the checked items and their values. Every slot is written, empty ones included: the
     * client redraws from the varcs it was last sent, so a value left behind by a removed item
     * would keep showing.
     */
    fun refresh(player: Player) {
        val container = container(player) ?: return
        val definitions = player.world.definitions
        player.sendItemContainer(CONTAINER_KEY, container)

        for (slot in 0 until CAPACITY) {
            player.setVarc(SLOT_VALUE_VARC + slot, value(definitions, container[slot]))
        }
        player.setVarc(TOTAL_VALUE_VARC, total(definitions, container))
    }

    /**
     * An item is worth its shop value times its amount, taken from the unnoted form so that a note
     * is worth what the item is - the same valuation the trade screen uses.
     *
     * A varc carries an int, and a checked stack can be far larger than one: 2,147,483,647 coins
     * held as a single stack is already worth more than an int can hold once multiplied out. The
     * arithmetic is done in a long and clamped so the figure the player sees is a ceiling rather
     * than a wrapped negative number.
     */
    fun value(
        definitions: DefinitionSet,
        item: Item?,
    ): Int {
        if (item == null) {
            return 0
        }
        val unnoted = Item(item).toUnnoted(definitions)
        val cost = definitions.get(ItemDef::class.java, unnoted.id).cost
        return clamp(cost.toLong() * item.amount)
    }

    /** The "Total value:" figure: every slot's [value], clamped the same way. */
    fun total(
        definitions: DefinitionSet,
        container: ItemContainer,
    ): Int = clamp(container.rawItems.sumOf { value(definitions, it).toLong() })

    private fun clamp(value: Long): Int = Math.max(0L, Math.min(value, Int.MAX_VALUE.toLong())).toInt()
}
