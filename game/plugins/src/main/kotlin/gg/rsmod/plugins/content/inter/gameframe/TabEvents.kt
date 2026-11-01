package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.PaneMountHooks
import gg.rsmod.plugins.api.ext.openChatboxInterface
import gg.rsmod.plugins.api.ext.sendWeaponComponentInformation
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentPosition
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
            // N515 (test client 2026-11-01 02:1x): after a switch to the Curses or Ruinous book their cells had no "Activate" (right-click:
            // only Cancel) and no click reached the server - the grid of those books is built by the tab's onLoad script after the
            // mount, so the events of this tick could land before the slots existed. Sent once more a tick later, like the spellbooks
            // (magic_tab.plugin.kts).
            player.world.queue {
                wait(1)
                if (player.isOnline) gg.rsmod.plugins.content.mechanics.prayer.Prayers.unlockPrayerBookButtons(player)
            }
        }
        // The chat history (137 in 752:9) was mounted at login only; a window-mode switch re-mounts 752 without it.
        PaneMountHooks.register(InterfaceDestination.CHAT_BOX_PANE.interfaceId) { player ->
            player.openChatboxInterface(interfaceId = 137, child = 9, dest = InterfaceDestination.CHAT_BOX_PANE)
        }
        // A21 (owner 10-11): the Assist System is removed, so its chat filter tab goes too. 751:12 is the Assist tab layer (button
        // 751:16 + texts 17/18) and the rightmost tab: the others keep their places; Report Abuse 751:13 is right-aligned.
        // N240 (owner 2026-10-23: "lege ruimte tussen de Report-knop en de Trade-knop"): Report Abuse takes the Assist tab's place,
        // straight after Trade, as in OSRS (an IF_SETPOSITION is absolute: the client drops the right alignment).
        PaneMountHooks.register(InterfaceDestination.CHATBOX_TABS.interfaceId) { player ->
            player.setComponentHidden(InterfaceDestination.CHATBOX_TABS.interfaceId, ASSIST_TAB, true)
            player.setComponentPosition(InterfaceDestination.CHATBOX_TABS.interfaceId, REPORT_TAB, REPORT_X, REPORT_Y)
            // N240 (owner 10-31 23:4x: 'naast de Report-knop is het nog steeds leeg'): Report runs on to the bar's right edge, as the wider
            // OSRS Report button - its sprite and text (751:14/15) are 0x0 = fill the layer, so they grow with it. Re-sent on every mount.
            gg.rsmod.plugins.content.quests.tab.N108Ui.reset(player, InterfaceDestination.CHATBOX_TABS.interfaceId)
            gg.rsmod.plugins.content.quests.tab.N108Ui.size(player, InterfaceDestination.CHATBOX_TABS.interfaceId, REPORT_TAB, REPORT_WIDTH, REPORT_HEIGHT)
        }
    }

    /** The Assist tab layer of the chat filter bar 751 (cache decode, dossier A21). */
    const val ASSIST_TAB = 12

    /** Report Abuse (751:13, 54 x 22, right-aligned 4 px from the edge in the cache) and the Assist tab's left edge it moves to. */
    const val REPORT_TAB = 13
    const val REPORT_X = 404
    const val REPORT_Y = 1

    /** N240: from the Assist tab's left edge (404) to 4 px before the bar's right edge (751:0 is 519 wide), 22 high like the tabs. */
    const val REPORT_WIDTH = 519 - REPORT_X - 4

    const val REPORT_HEIGHT = 22

    private fun combatTab(player: Player) {
        player.sendWeaponComponentInformation()
        Autocast.refreshCombatTab(player)
    }
}
