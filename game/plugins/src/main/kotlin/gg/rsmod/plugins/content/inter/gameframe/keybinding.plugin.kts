package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.tools.importer.KeybindingInterfaceImportTool as Ui
import gg.rsmod.plugins.api.ext.setComponentHidden

/*
 * The OSRS Keybinding window (owner 2026-10-11): Settings tab > Keybinding (261:23, appended by KeybindingInterfaceImportTool)
 * opens it; KeybindingUi does the rest.
 */
// After the Settings tab is mounted (on_interface_open runs before its IF_OPENSUB, which drops server events in the client).
gg.rsmod.plugins.api.ext.PaneMountHooks.register(Ui.SETTINGS_INTERFACE) { p ->
    p.setInterfaceEvents(Ui.SETTINGS_INTERFACE, Ui.SETTINGS_BUTTON, -1..-1, 0x2)
    p.setComponentHidden(Ui.SETTINGS_INTERFACE, KeybindingUi.SETTINGS_DIVIDER, true)
}

on_login {
    player.setInterfaceEvents(Ui.SETTINGS_INTERFACE, Ui.SETTINGS_BUTTON, -1..-1, 0x2)
    player.setComponentHidden(Ui.SETTINGS_INTERFACE, KeybindingUi.SETTINGS_DIVIDER, true)
}

on_button(interfaceId = Ui.SETTINGS_INTERFACE, component = Ui.SETTINGS_BUTTON) {
    KeybindingUi.open(player)
}

on_button(interfaceId = Ui.INTERFACE_ID, component = gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.CLOSE) {
    player.closeInterface(Ui.INTERFACE_ID)
}

Ui.ROWS.indices.forEach { row ->
    on_button(interfaceId = Ui.INTERFACE_ID, component = Ui.row(row) + Ui.ROW_CLICK) {
        KeybindingUi.openDropdown(player, row)
    }
}

Ui.KEY_LABELS.indices.forEach { option ->
    on_button(interfaceId = Ui.INTERFACE_ID, component = Ui.option(option)) {
        KeybindingUi.choose(player, option)
    }
}

on_button(interfaceId = Ui.INTERFACE_ID, component = Ui.DROP_OVERLAY) {
    KeybindingUi.closeDropdown(player)
}

on_button(interfaceId = Ui.INTERFACE_ID, component = Ui.ESC) {
    KeybindingUi.toggleEsc(player)
}

on_button(interfaceId = Ui.INTERFACE_ID, component = Ui.RESTORE) {
    KeybindingUi.restoreDefaults(player)
}
