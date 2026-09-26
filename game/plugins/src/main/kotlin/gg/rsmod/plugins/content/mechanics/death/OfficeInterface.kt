package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentPosition
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Office as Layout

/**
 * "Death's Office Item Retrieval" (OSRS interface 669 as 667 interface [INTERFACE_ID]): Death's items in a grid; "Select"
 * one, then take 1 / 5 / X / All of it for the office fee, or Take-All. The texts are OSRS clientscript
 * `death_office_redraw` (3492) and `death_office_title` (3494).
 */
object OfficeInterface {
    const val INTERFACE_ID = DeathsOfficeInterfaceImportTool.OFFICE
    const val CLOSE = DeathsOfficeInterfaceImportTool.CLOSE

    private const val EVENTS_OP1 = 0x2
    private const val EVENTS_SLOT = 0x2 or 0x400

    /** The Death's Office container slot the player selected (OSRS var262). */
    private val SELECTED = AttributeKey<Int>()
    private val VIEW = AttributeKey<List<Int>>()

    fun isOpen(player: Player): Boolean = player.attr[VIEW] != null

    fun open(player: Player) {
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INTERFACE_ID, CLOSE, -1..-1, EVENTS_OP1)
        intArrayOf(Layout.BUTTON_1, Layout.BUTTON_5, Layout.BUTTON_X, Layout.BUTTON_ALL, Layout.BUTTON_TAKE_ALL).forEach {
            player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, EVENTS_OP1)
        }
        for (i in 0 until Layout.SLOTS) player.setInterfaceEvents(INTERFACE_ID, Layout.SLOT_FIRST + i, -1..-1, EVENTS_SLOT)
        player.setInterfaceEvents(INTERFACE_ID, Layout.SELECTED, -1..-1, EVENTS_SLOT)
        player.attr.remove(SELECTED)
        player.attr[VIEW] = emptyList()
        refresh(player)
    }

    fun close(player: Player) {
        player.attr.remove(SELECTED)
        player.attr.remove(VIEW)
    }

    /** The office slot shown by grid [index], or null. */
    fun slotAt(player: Player, index: Int): Int? = player.attr[VIEW]?.getOrNull(index)

    fun selected(player: Player): Int? = player.attr[SELECTED]?.takeIf { player.deathRecovery[it] != null }

    fun select(player: Player, officeSlot: Int) {
        if (player.deathRecovery[officeSlot] == null) return
        player.attr[SELECTED] = officeSlot
        refresh(player)
    }

    fun title(used: Int): String = "Death's Office Item Retrieval <col=ffb83f>($used/120)</col>"

    private fun spaced(value: Long): String = String.format("%,d", value)

    /** death_office_redraw's info text for the selected stack ([total] units of it in the office, [feeEach] per unit). */
    fun itemText(name: String, total: Int, feeEach: Int, coffer: Int): String {
        val cofferLine = "<br>Death's Coffer: <col=ffffff>${spaced(coffer.toLong())} coins</col>"
        return if (total > 1) {
            if (feeEach == 1) {
                "${spaced(total.toLong())} x $name:<br>Fee: <col=ffffff>1 coin</col> each$cofferLine"
            } else {
                "${spaced(total.toLong())} x $name:<br>Fee: <col=ffffff>${spaced(feeEach.toLong())} coins</col> each " +
                    "(<col=ffffff>${spaced(total.toLong() * feeEach)}</col>)$cofferLine"
            }
        } else if (feeEach == 1) {
            "$name:<br>Fee: <col=ffffff>1 coin</col>$cofferLine"
        } else {
            "$name:<br>Fee: <col=ffffff>${spaced(feeEach.toLong())} coins</col>$cofferLine"
        }
    }

    fun noneText(coffer: Int): String = "Select an item to retrieve.<br>Death's Coffer: <col=ffffff>${spaced(coffer.toLong())}</col>"

    fun refresh(player: Player) {
        if (!isOpen(player)) return
        val office = player.deathRecovery
        val slots = (0 until office.capacity).filter { office[it] != null }
        player.attr[VIEW] = slots
        player.setComponentText(INTERFACE_ID, DeathsOfficeInterfaceImportTool.TITLE, title(slots.size))
        for (i in 0 until Layout.SLOTS) {
            val item = slots.getOrNull(i)?.let { office[it] }
            val component = Layout.SLOT_FIRST + i
            if (item == null) {
                player.setComponentHidden(INTERFACE_ID, component, hidden = true)
            } else {
                player.setComponentItem(INTERFACE_ID, component, item.id, item.amount)
                player.setComponentHidden(INTERFACE_ID, component, hidden = false)
            }
        }
        val rows = (slots.size + Layout.COLUMNS - 1) / Layout.COLUMNS
        player.runClientScript(
            DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT,
            (INTERFACE_ID shl 16) or Layout.ITEMS,
            (INTERFACE_ID shl 16) or Layout.SCROLLBAR,
            if (rows == 0) 0 else (rows - 1) * Layout.ROW_STEP + 32,
        )
        val coffer = DeathPayment.coffer(player)
        val selected = selected(player)
        val index = selected?.let { slots.indexOf(it) } ?: -1
        val buttons = intArrayOf(Layout.BUTTON_1, Layout.BUTTON_5, Layout.BUTTON_X, Layout.BUTTON_ALL)
        if (selected == null || index < 0) {
            player.attr.remove(SELECTED)
            player.setComponentHidden(INTERFACE_ID, Layout.SELECTED, hidden = true)
            buttons.forEach { player.setComponentHidden(INTERFACE_ID, it, hidden = true) }
            player.setComponentHidden(INTERFACE_ID, Layout.INFO_ITEM, hidden = true)
            player.setComponentText(INTERFACE_ID, Layout.INFO_NONE, noneText(coffer))
            player.setComponentHidden(INTERFACE_ID, Layout.INFO_NONE, hidden = false)
            return
        }
        val item = office[selected]!!
        player.setComponentItem(INTERFACE_ID, Layout.SELECTED, item.id, item.amount)
        player.setComponentPosition(INTERFACE_ID, Layout.SELECTED, Layout.MARGIN + (index % Layout.COLUMNS) * Layout.COLUMN_STEP, (index / Layout.COLUMNS) * Layout.ROW_STEP)
        player.setComponentHidden(INTERFACE_ID, Layout.SELECTED, hidden = false)
        buttons.forEach { player.setComponentHidden(INTERFACE_ID, it, hidden = false) }
        val name = player.world.definitions.get(ItemDef::class.java, item.id).name
        val total = slots.mapNotNull { office[it] }.filter { it.id == item.id && it.attr == item.attr }.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val feeEach = DeathFees.officeUnitFee(item, GuidePriceValueProvider(player.world))
        player.setComponentText(INTERFACE_ID, Layout.INFO_ITEM, itemText(name, total, feeEach, coffer))
        player.setComponentHidden(INTERFACE_ID, Layout.INFO_NONE, hidden = true)
        player.setComponentHidden(INTERFACE_ID, Layout.INFO_ITEM, hidden = false)
    }

    /** Takes [amount] units of the selected item (all of them across its stacks when [amount] exceeds one stack). */
    fun take(
        player: Player,
        amount: Int,
    ) {
        val selected = selected(player) ?: return
        val value = GuidePriceValueProvider(player.world)
        val first = player.deathRecovery[selected] ?: return
        var left = amount
        for (slot in listOf(selected) + (0 until player.deathRecovery.capacity).filter { it != selected }) {
            if (left <= 0) break
            val item = player.deathRecovery[slot] ?: continue
            if (item.id != first.id || item.attr != first.attr) continue
            when (val outcome = DeathsOffice.retrieve(player, slot, minOf(left, item.amount), value)) {
                is OfficeRetrieveOutcome.Retrieved -> left -= outcome.item.amount
                else -> {
                    report(player, outcome)
                    break
                }
            }
        }
        refresh(player)
    }

    /** Take-All: every stack, as long as the inventory has room and the fees can be paid. */
    fun takeAll(player: Player) {
        val value = GuidePriceValueProvider(player.world)
        var took = false
        for (slot in 0 until player.deathRecovery.capacity) {
            val item = player.deathRecovery[slot] ?: continue
            when (val outcome = DeathsOffice.retrieve(player, slot, item.amount, value)) {
                is OfficeRetrieveOutcome.Retrieved -> took = true
                OfficeRetrieveOutcome.NothingThere -> {}
                else -> {
                    report(player, outcome)
                    break
                }
            }
        }
        if (!took && DeathsOffice.isEmpty(player)) player.attr.remove(SELECTED)
        refresh(player)
    }

    fun report(
        player: Player,
        outcome: OfficeRetrieveOutcome,
    ) {
        when (outcome) {
            OfficeRetrieveOutcome.NoInventorySpace -> player.message("You don't have enough inventory space.")
            is OfficeRetrieveOutcome.CannotAfford -> player.message("You don't have enough coins to pay Death's fee of ${spaced(outcome.fee)} coins.")
            else -> {}
        }
    }
}
