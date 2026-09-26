package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.api.ext.unlockIComponentOptionSlots
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Coffer as Layout

/**
 * "Sacrifice items to Death's Coffer" (OSRS interfaces 670 + 671 as 667 [INTERFACE_ID] + [SIDE_ID]): select an item in the
 * side inventory, a quantity (1 / 5 / X / All), then Confirm. Texts and button states follow OSRS clientscripts
 * `death_coffer_init` (3479), `death_coffer_setcontents` (3481) and `script3483`.
 */
object CofferInterface {
    const val INTERFACE_ID = DeathsOfficeInterfaceImportTool.COFFER
    const val SIDE_ID = DeathsOfficeInterfaceImportTool.COFFER_SIDE
    const val CLOSE = DeathsOfficeInterfaceImportTool.CLOSE

    /** OSRS var263 "All". */
    const val ALL = Int.MAX_VALUE

    private const val EVENTS_OP1 = 0x2

    /** Inventory slot on offer (OSRS var262) and the chosen quantity (var263, 0 = none yet). */
    private val SELECTED = AttributeKey<Int>()
    private val QUANTITY = AttributeKey<Int>()
    private val OPEN = AttributeKey<Boolean>()

    fun isOpen(player: Player): Boolean = player.attr[OPEN] == true

    fun open(player: Player) {
        player.attr[OPEN] = true
        player.attr.remove(SELECTED)
        player.attr.remove(QUANTITY)
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INTERFACE_ID, CLOSE, -1..-1, EVENTS_OP1)
        intArrayOf(Layout.BUTTON_1, Layout.BUTTON_5, Layout.BUTTON_X, Layout.BUTTON_ALL, Layout.CONFIRM).forEach {
            player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, EVENTS_OP1)
        }
        player.openInterface(SIDE_ID, InterfaceDestination.INVENTORY_TAB)
        player.unlockIComponentOptionSlots(SIDE_ID, DeathsOfficeInterfaceImportTool.CofferSide.ROOT, 0, 27, 0)
        player.runClientScript(150, (SIDE_ID shl 16) or DeathsOfficeInterfaceImportTool.CofferSide.ROOT, 93, 4, 7, 0, -1, "Select")
        refresh(player)
    }

    /** Puts the normal inventory back (the side panel replaced it). */
    fun close(player: Player) {
        if (player.attr[OPEN] != true) return
        player.attr.remove(OPEN)
        player.attr.remove(SELECTED)
        player.attr.remove(QUANTITY)
        player.closeInterface(SIDE_ID)
        player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
        player.inventory.dirty = true
    }

    fun select(player: Player, inventorySlot: Int) {
        if (player.inventory[inventorySlot] == null) return
        player.attr[SELECTED] = inventorySlot
        player.attr.remove(QUANTITY)
        refresh(player)
    }

    fun setQuantity(player: Player, quantity: Int) {
        if (player.attr[SELECTED] == null || quantity <= 0) return
        player.attr[QUANTITY] = quantity
        refresh(player)
    }

    private fun spaced(value: Long): String = String.format("%,d", value)

    fun cofferText(coffer: Int): String =
        when {
            coffer > 1 -> "Coffer: <col=ffffff>${spaced(coffer.toLong())}</col> coins"
            coffer == 1 -> "Coffer: <col=ffffff>1</col> coin"
            else -> "Coffer: <col=ffffff>Empty</col>"
        }

    /** What the screen shows as one unit's worth (OSRS var264): the coffer offer, or the item's value when refused. */
    fun shownValue(player: Player, itemId: Int, value: ItemRiskValueProvider): Long {
        val definitions = player.world.definitions
        return if (DeathCoffer.eligible(definitions, itemId, value)) {
            DeathCoffer.unitOffer(definitions, itemId, value)
        } else {
            val def = definitions.get(ItemDef::class.java, itemId)
            val base = if (def.noted) def.noteLinkId else itemId
            val raw = value.getValue(base)
            // A refused item is always shown below 10,000 (red, "Unacceptable item"), whatever it is worth.
            if (raw >= 10_000L) 0L else raw
        }
    }

    fun refresh(player: Player) {
        if (!isOpen(player)) return
        player.setComponentText(INTERFACE_ID, Layout.COFFER_TEXT, cofferText(DeathPayment.coffer(player)))
        val slot = player.attr[SELECTED]
        val item = slot?.let { player.inventory[it] }
        val buttons = listOf(Layout.BUTTON_1 to 1, Layout.BUTTON_5 to 5, Layout.BUTTON_X to -1, Layout.BUTTON_ALL to ALL)
        if (item == null) {
            player.attr.remove(SELECTED)
            player.attr.remove(QUANTITY)
            player.setComponentText(INTERFACE_ID, Layout.NAME, "Select an item...")
            player.setComponentText(INTERFACE_ID, Layout.VALUE, "---")
            player.setComponentHidden(INTERFACE_ID, Layout.DISPLAY, hidden = true)
            buttons.forEach { (button, _) -> showLook(player, button, Layout.LOOK_DISABLED) }
            showConfirm(player, null)
            return
        }
        val value = GuidePriceValueProvider(player.world)
        val definitions = player.world.definitions
        player.setComponentText(INTERFACE_ID, Layout.NAME, definitions.get(ItemDef::class.java, item.id).name)
        player.setComponentItem(INTERFACE_ID, Layout.DISPLAY, item.id, item.amount)
        player.setComponentHidden(INTERFACE_ID, Layout.DISPLAY, hidden = false)
        val quantity = player.attr[QUANTITY] ?: 0
        buttons.forEach { (button, amount) ->
            val selected = quantity > 0 && (amount == quantity || (amount == -1 && quantity != 1 && quantity != 5 && quantity != ALL))
            showLook(player, button, if (selected) Layout.LOOK_SELECTED else Layout.LOOK_ACTIVE)
        }
        if (quantity <= 0) {
            player.setComponentText(INTERFACE_ID, Layout.VALUE, "Select a quantity...")
            showConfirm(player, null)
            return
        }
        val count = minOf(quantity.toLong(), player.inventory.getItemCount(item.id).toLong())
        val each = shownValue(player, item.id, value)
        if (each >= 10_000L) {
            player.setComponentText(INTERFACE_ID, Layout.VALUE, if (count > 1) "${spaced(count)} x ${spaced(each)} coins" else "${spaced(each)} coins")
            val total = count * each
            showConfirm(player, if (total > Int.MAX_VALUE) "Many coins" else "${spaced(total)} coins")
        } else {
            val coins = if (each == 1L) "1 coin" else "${spaced(each)} coins"
            player.setComponentText(INTERFACE_ID, Layout.VALUE, if (count > 1) "${spaced(count)} x <col=ff0000>$coins</col>" else "<col=ff0000>$coins</col>")
            showConfirm(player, "Unacceptable item")
        }
    }

    private fun showLook(player: Player, button: Int, look: Int) {
        listOf(Layout.LOOK_ACTIVE, Layout.LOOK_SELECTED, Layout.LOOK_DISABLED).forEach {
            player.setComponentHidden(INTERFACE_ID, button + it, hidden = it != look)
        }
    }

    private fun showConfirm(player: Player, value: String?) {
        player.setComponentHidden(INTERFACE_ID, Layout.CONFIRM_ACTIVE, hidden = value == null)
        player.setComponentHidden(INTERFACE_ID, Layout.CONFIRM_DISABLED, hidden = value != null)
        if (value != null) player.setComponentText(INTERFACE_ID, Layout.CONFIRM_VALUE, value)
    }

    fun confirm(player: Player) {
        val slot = player.attr[SELECTED] ?: return
        val quantity = player.attr[QUANTITY] ?: return
        when (DeathCoffer.sacrifice(player, slot, quantity, GuidePriceValueProvider(player.world))) {
            CofferOutcome.Ineligible -> player.message(DeathCoffer.CANNOT_TRADE)
            CofferOutcome.Full -> player.message(DeathCoffer.TOO_FULL)
            else -> {}
        }
        if (player.inventory[slot] == null) {
            player.attr.remove(SELECTED)
            player.attr.remove(QUANTITY)
        }
        refresh(player)
    }
}
