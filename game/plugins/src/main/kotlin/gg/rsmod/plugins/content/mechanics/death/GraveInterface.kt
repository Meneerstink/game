package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Grave as Layout

/**
 * The OSRS gravestone screen (OSRS interface 672, built as 667 interface [INTERFACE_ID] by `DeathsOfficeInterfaceImportTool`):
 *  - title "Gravestone (n/120)" (gravestone_generic_title);
 *  - "Free to reclaim:" - every stack that costs nothing, with the Take-All button;
 *  - the fee section - every stack still behind the fee, each labelled with its own fee ("1k", "10k", "100k" per unit,
 *    gravestone_generic_window_set), "Fee: X coins" / "Fee: Paid" (gravestone_generic_parsefee), the Unlock button while a
 *    fee is due and Take-All once it is paid, the incinerator ("Discard items to reduce a fee.") and the coffer line
 *    (gravestone_generic_parsecoffer).
 * Items are listed in the order OSRS lists them: the fee section by fee tier (100k, then 10k, then 1k), then slot order.
 * The view maps each shown component back to a gravestone slot ([freeSlots] / [paySlots]).
 */
object GraveInterface {
    const val INTERFACE_ID = DeathsOfficeInterfaceImportTool.GRAVE
    const val CLOSE = DeathsOfficeInterfaceImportTool.CLOSE

    private const val EVENTS_OP1 = 0x2
    private const val EVENTS_SLOT = 0x2 or 0x400

    /** Drag depth 1 (bits 18-20) on the fee items and drag target (bit 21) on the incinerator: "drag items onto it". */
    private const val EVENTS_DRAGGABLE = EVENTS_SLOT or (1 shl 18)
    private const val EVENTS_DRAG_TARGET = 1 shl 21

    private val FREE_VIEW = AttributeKey<List<Int>>()
    private val PAY_VIEW = AttributeKey<List<Int>>()

    fun isOpen(player: Player): Boolean = player.attr[FREE_VIEW] != null

    fun open(player: Player) {
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INTERFACE_ID, CLOSE, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE_ID, Layout.FREE_BUTTON, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE_ID, Layout.UNLOCK_BUTTON, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE_ID, Layout.PAY_TAKE_ALL_BUTTON, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE_ID, Layout.INCINERATOR, -1..-1, EVENTS_DRAG_TARGET)
        for (i in 0 until Layout.SLOTS) {
            player.setInterfaceEvents(INTERFACE_ID, Layout.FREE_SLOT_FIRST + i, -1..-1, EVENTS_SLOT)
            player.setInterfaceEvents(INTERFACE_ID, Layout.PAY_SLOT_FIRST + i, -1..-1, EVENTS_DRAGGABLE)
        }
        player.attr[FREE_VIEW] = emptyList()
        player.attr[PAY_VIEW] = emptyList()
        resetShown(player)
        refresh(player)
    }

    fun close(player: Player) {
        player.attr.remove(FREE_VIEW)
        player.attr.remove(PAY_VIEW)
        resetShown(player)
    }

    fun freeSlot(player: Player, index: Int): Int? = player.attr[FREE_VIEW]?.getOrNull(index)

    fun paySlot(player: Player, index: Int): Int? = player.attr[PAY_VIEW]?.getOrNull(index)

    /** OSRS fee label under a fee item: "10k" from 1,000, else the number (gravestone_generic_window_set). */
    fun feeLabel(fee: Long): String = if (fee >= 1000) "${fee / 1000}k" else fee.toString()

    fun feeText(fee: Int): String =
        when (fee) {
            0 -> "Fee: <col=ffffff>Paid</col>"
            1 -> "Fee: <col=ffffff>1 coin</col>"
            else -> "Fee: <col=ffffff>${String.format("%,d", fee)} coins</col>"
        }

    fun cofferText(coffer: Int): String =
        when (coffer) {
            0 -> "Death's Coffer: <col=ffffff>Empty</col><br>Discard items to reduce a fee."
            1 -> "Death's Coffer: <col=ffffff>1 coin</col><br>Discard items to reduce a fee."
            else -> "Death's Coffer: <col=ffffff>${String.format("%,d", coffer)} coins</col><br>Discard items to reduce a fee."
        }

    fun title(used: Int): String = "Gravestone <col=ffb83f>($used/120)</col>"

    fun refresh(player: Player) {
        if (!isOpen(player)) return
        val value = GuidePriceValueProvider(player.world)
        val grave = player.gravestone
        val stacks = (0 until grave.capacity).mapNotNull { slot -> grave[slot]?.let { slot to it } }
        val free = stacks.filter { DeathFees.graveStackFee(it.second, value) == 0L }.map { it.first }
        val pay =
            stacks.filter { DeathFees.graveStackFee(it.second, value) > 0L }
                .sortedByDescending { DeathFees.graveUnitFee(value.getValue(it.second.id)) }
                .map { it.first }
        player.attr[FREE_VIEW] = free
        player.attr[PAY_VIEW] = pay
        player.setComponentText(INTERFACE_ID, DeathsOfficeInterfaceImportTool.TITLE, title(stacks.size))
        showItems(player, free, Layout.FREE_SLOT_FIRST, null)
        showItems(player, pay, Layout.PAY_SLOT_FIRST, Layout.PAY_FEE_FIRST) { DeathFees.graveStackFee(it, value) }
        val fee = Gravestone.fee(player, value)
        player.setComponentText(INTERFACE_ID, Layout.FEE, feeText(fee))
        player.setComponentHidden(INTERFACE_ID, Layout.UNLOCK_BUTTON, hidden = fee == 0)
        player.setComponentHidden(INTERFACE_ID, Layout.PAY_TAKE_ALL_BUTTON, hidden = fee != 0)
        player.setComponentText(INTERFACE_ID, Layout.INFO, cofferText(DeathPayment.coffer(player)))
        val freeRows = (free.size + Layout.COLUMNS - 1) / Layout.COLUMNS
        val payRows = (pay.size + Layout.COLUMNS - 1) / Layout.COLUMNS
        setScroll(player, Layout.FREE_ITEMS, Layout.FREE_SCROLLBAR, if (freeRows == 0) 0 else (freeRows - 1) * Layout.FREE_ROW_STEP + 32)
        setScroll(player, Layout.PAY_ITEMS, Layout.PAY_SCROLLBAR, if (payRows == 0) 0 else (payRows - 1) * Layout.PAY_ROW_STEP + 32 + 12)
    }

    private fun showItems(
        player: Player,
        slots: List<Int>,
        firstComponent: Int,
        firstLabel: Int?,
        fee: ((Item) -> Long)? = null,
    ) {
        val shown = player.attr[LAST_SHOWN] ?: HashMap<Int, Pair<Int, Int>>().also { player.attr[LAST_SHOWN] = it }
        for (i in 0 until Layout.SLOTS) {
            val component = firstComponent + i
            val item = slots.getOrNull(i)?.let { player.gravestone[it] }
            val state = item?.let { it.id to it.amount } ?: (-1 to 0)
            if (shown[component] != state) {
                shown[component] = state
                if (item == null) {
                    player.setComponentHidden(INTERFACE_ID, component, hidden = true)
                    if (firstLabel != null) player.setComponentHidden(INTERFACE_ID, firstLabel + i, hidden = true)
                } else {
                    player.setComponentItem(INTERFACE_ID, component, item.id, item.amount)
                    player.setComponentHidden(INTERFACE_ID, component, hidden = false)
                }
            }
            if (item != null && firstLabel != null && fee != null) {
                player.setComponentText(INTERFACE_ID, firstLabel + i, feeLabel(fee(item)))
                player.setComponentHidden(INTERFACE_ID, firstLabel + i, hidden = false)
            }
        }
    }

    private val LAST_SHOWN = AttributeKey<HashMap<Int, Pair<Int, Int>>>()

    fun resetShown(player: Player) {
        player.attr.remove(LAST_SHOWN)
    }

    private fun setScroll(
        player: Player,
        layer: Int,
        scrollbar: Int,
        height: Int,
    ) {
        player.runClientScript(DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT, (INTERFACE_ID shl 16) or layer, (INTERFACE_ID shl 16) or scrollbar, height)
    }

    /** Chat feedback for a take that did not happen. */
    fun report(
        player: Player,
        outcome: GraveTakeOutcome,
    ) {
        when (outcome) {
            GraveTakeOutcome.NoInventorySpace -> player.message("You don't have enough inventory space.")
            else -> {}
        }
    }
}
