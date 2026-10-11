package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.PaneMountHooks
import gg.rsmod.plugins.api.ext.openChatboxInterface
import gg.rsmod.plugins.api.ext.sendWeaponComponentInformation
import gg.rsmod.plugins.content.combat.magic.Autocast

/**
 * ROOT_CAUSES #143: the client drops every server-set event, hide flag and position of an interface when it is closed or
 * mounted again in its pane (`InterfaceManager.closeSubInterface -> resetServerActiveProperties`, client source). Gameframe
 * tabs whose components get their ops from the server, not the cache, therefore went dead after a re-mount until something
 * happened to re-send them:
 *  - inventory 679:0 (the cache gives its slots no ops): after the GE's Back, an equipment-stats / price-checker / shop /
 *    coffer / quiver / tool-store / fairy-ring / BoB side panel, a left click on any item sent nothing (2026-10-02: "Pest
 *    control teleport does nothing") until the inventory next changed;
 *  - combat tab 884: the attack-style buttons (11-14), the special-bar visibility and the autocast boxes after Autocast's
 *    spell list closes back to the tab.
 *  - prayer tab 271:8 / 271:42 (cache events 0x000000 - the grid's slots are made by its onLoad script): after a window-mode
 *    switch every prayer and curse click did nothing until a relog or a book switch (owner 2026-10-11: "curses do not turn
 *    on", after login, after a book switch and at random). Novite re-sends them with every prayer-tab send
 *    (InterfaceManager.sendPrayerBook + Prayer.refresh -> unlockPrayerBookButtons).
 * Each re-mount through [gg.rsmod.plugins.api.ext.openInterface] runs [PaneMountHooks.afterMount] after the IF_OPENSUB, so
 * the state is sent again there. The spellbooks re-arm themselves a tick after opening (magic_tab.plugin.kts), the quest
 * tab through its own mount hook.
 */
object TabEvents {
    fun install() {
        PaneMountHooks.register(InterfaceDestination.INVENTORY_TAB.interfaceId) { player -> player.sendInventoryEvents() }
        PaneMountHooks.register(InterfaceDestination.ATTACK_TAB.interfaceId) { player -> combatTab(player) }
        PaneMountHooks.register(InterfaceDestination.PRAYER_TAB.interfaceId) { player ->
            gg.rsmod.plugins.content.mechanics.prayer.Prayers.unlockPrayerBookButtons(player)
        }
        // The chat history (137 in 752:9) was mounted at login only; a window-mode switch re-mounts 752 without it.
        PaneMountHooks.register(InterfaceDestination.CHAT_BOX_PANE.interfaceId) { player ->
            player.openChatboxInterface(interfaceId = 137, child = 9, dest = InterfaceDestination.CHAT_BOX_PANE)
        }
    }

    private fun combatTab(player: Player) {
        player.sendWeaponComponentInformation()
        Autocast.refreshCombatTab(player)
    }
}
