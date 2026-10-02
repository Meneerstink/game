package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.PaneMountHooks
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
 * Each re-mount through [gg.rsmod.plugins.api.ext.openInterface] runs [PaneMountHooks.afterMount] after the IF_OPENSUB, so
 * the state is sent again there. Tabs whose ops come from the cache (prayer, emotes, music, ...) lose nothing on a re-mount;
 * the spellbooks re-arm themselves a tick after opening (magic_tab.plugin.kts), the quest tab through its own mount hook.
 */
object TabEvents {
    fun install() {
        PaneMountHooks.register(InterfaceDestination.INVENTORY_TAB.interfaceId) { player -> player.sendInventoryEvents() }
        PaneMountHooks.register(InterfaceDestination.ATTACK_TAB.interfaceId) { player -> combatTab(player) }
    }

    private fun combatTab(player: Player) {
        player.sendWeaponComponentInformation()
        Autocast.refreshCombatTab(player)
    }
}
