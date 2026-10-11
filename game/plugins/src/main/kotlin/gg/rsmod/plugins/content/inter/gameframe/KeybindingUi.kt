package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.KeybindingInterfaceImportTool as Ui
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.mechanics.hud.ClientHudConfig

/**
 * The in-game OSRS Keybinding window (owner 2026-10-11: "zet keybinding net als osrs in de client ipv sidebar"), interface
 * [Ui.INTERFACE_ID] - a port of OSRS interface 121 - opened from the Settings tab's Keybinding button. The bindings are saved
 * with the account (ClientHudConfig keybinding.*, OSRS defaults) and pushed to the client, whose RspsFunctionKeys opens the
 * bound tab on F1-F12 / Esc.
 *
 * OSRS behaviour kept: a row's dropdown lists None, F1-F12 and Esc; picking a key another tab had takes it from that tab
 * (ClientHudConfig.set); "Esc closes current interface" is a checkbox; Restore Defaults puts every key back.
 */
object KeybindingUi {
    const val INTERFACE_ID = Ui.INTERFACE_ID

    /** The row whose dropdown is open. */
    private val OPEN_ROW = AttributeKey<Int>()

    private const val OP1 = 0x2

    fun open(player: Player) {
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INTERFACE_ID, gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.CLOSE, -1..-1, OP1)
        Ui.ROWS.indices.forEach { player.setInterfaceEvents(INTERFACE_ID, Ui.row(it) + Ui.ROW_CLICK, -1..-1, OP1) }
        listOf(Ui.ESC, Ui.RESTORE, Ui.DROP_OVERLAY).forEach { player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, OP1) }
        Ui.KEY_LABELS.indices.forEach { player.setInterfaceEvents(INTERFACE_ID, Ui.option(it), -1..-1, OP1) }
        closeDropdown(player)
        render(player)
    }

    private fun label(key: String): String = Ui.KEY_LABELS[ClientHudConfig.KEYBIND_KEYS.indexOf(key).coerceAtLeast(0)]

    fun render(player: Player) {
        Ui.ROWS.forEachIndexed { i, (tab, _, _) ->
            player.setComponentText(INTERFACE_ID, Ui.row(i) + Ui.ROW_KEY, label(ClientHudConfig.get(player, "keybinding.$tab")))
        }
        val esc = ClientHudConfig.bool(player, "keybinding.escCloses")
        player.setComponentSprite(INTERFACE_ID, Ui.ESC_CHECKBOX, Ui.sprite(if (esc) 8379 else 8378))
    }

    /** Opens [row]'s dropdown under its box (above it when the list would run off the bottom), as OSRS does. */
    fun openDropdown(
        player: Player,
        row: Int,
    ) {
        if (row !in Ui.ROWS.indices) return
        player.attr[OPEN_ROW] = row
        val x = Ui.rowX(row) + Ui.BOX_X
        val below = Ui.rowY(row) + Ui.BOX_Y + Ui.BOX_H
        val y = if (below + Ui.LIST_H <= Ui.CONTENT_H) below else (Ui.rowY(row) + Ui.BOX_Y - Ui.LIST_H).coerceAtLeast(0)
        player.setComponentPosition(INTERFACE_ID, Ui.DROP_BOX, x, y)
        player.setComponentHidden(INTERFACE_ID, Ui.DROP_OVERLAY, false)
    }

    fun closeDropdown(player: Player) {
        player.attr.remove(OPEN_ROW)
        player.setComponentHidden(INTERFACE_ID, Ui.DROP_OVERLAY, true)
    }

    /** A key picked in the open dropdown. */
    fun choose(
        player: Player,
        option: Int,
    ) {
        val row = player.attr[OPEN_ROW] ?: return
        val key = ClientHudConfig.KEYBIND_KEYS.getOrNull(option) ?: return
        ClientHudConfig.set(player, "keybinding.${Ui.ROWS[row].first}", key)
        ClientHudConfig.push(player)
        closeDropdown(player)
        render(player)
    }

    fun toggleEsc(player: Player) {
        ClientHudConfig.set(player, "keybinding.escCloses", (!ClientHudConfig.bool(player, "keybinding.escCloses")).toString())
        ClientHudConfig.push(player)
        render(player)
    }

    fun restoreDefaults(player: Player) {
        ClientHudConfig.reset(player, "keybinding")
        ClientHudConfig.push(player)
        closeDropdown(player)
        render(player)
    }
}
