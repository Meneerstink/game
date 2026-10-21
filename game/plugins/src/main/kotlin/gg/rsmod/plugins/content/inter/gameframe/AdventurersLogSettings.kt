package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setVarp

/**
 * The Adventurer's Log settings page (interface 623, Options tab -> `Adventurer's Log Options`).
 *
 * Owner 2026-10-04 032152 ("adventurers log options does nothing"): each `Toggle On/Off` switch's onVarTransmit hook in the
 * cache is `clientscript 4732(component, bit)`, which draws sprite 6004 when that bit of varp 2396 is set and 6011 when it is
 * clear (`runInterfaceHookProbeTool --args="../data/cache interface 623"`), so the bit a switch owns is that hook argument and
 * a click flips it. Varp 2396 is saved with the player.
 *
 * N123 (owner 2026-10-11: "Chaotics en ALLES van Dungeoneering volledig verwijderen; de skill volledig weg"): the cache page
 * still offers four Dungeoneering rows - switch + label 623:32/33 "deepest level", 34/35 "boss kills", 36/37 "spent tokens",
 * 38/39 "found journals" (bits 17-20). They are the last four rows of the right column, so hiding them leaves no gap. The
 * cache never un-hides them (script 4732 only sets the switch graphic, the page has no onLoad), so the IF_SETHIDE sent right
 * after the page is mounted holds while it is open. Mechanism as Darkan (WorldEncoder.setIFHidden right after the open,
 * Book.java:75-85, Quest.java:334-340) and Void (TaskSystem.kt sendVisibility after open); the switches are no longer bound,
 * so a forged click on a hidden switch does nothing. Bits 17-20 already in a save are left alone (no log feed reads them).
 */
object AdventurersLogSettings {
    const val INTERFACE_ID = 623
    const val VARP = 2396

    /** Switch component -> varp 2396 bit, from each switch's cache hook `4732(component, bit)`, Dungeoneering rows left out. */
    val SWITCHES: Map<Int, Int> =
        mapOf(
            2 to 1, 4 to 2, 6 to 3, 8 to 4, 10 to 5, 12 to 6, 14 to 7, 16 to 8, 18 to 9, 20 to 10,
            22 to 11, 24 to 13, 26 to 14, 28 to 15, 30 to 16,
        )

    /** The removed Dungeoneering rows: switches 32/34/36/38 and their labels 33/35/37/39. */
    val REMOVED_DUNGEONEERING_COMPONENTS: List<Int> = (32..39).toList()

    /** Runs after every mount of 623 (registered in settings_tab.plugin.kts through PaneMountHooks). */
    fun hideRemovedRows(player: Player) {
        REMOVED_DUNGEONEERING_COMPONENTS.forEach { component -> player.setComponentHidden(INTERFACE_ID, component, true) }
    }

    fun toggle(
        player: Player,
        component: Int,
    ) {
        val bit = SWITCHES[component] ?: return
        player.setVarp(VARP, player.getVarp(VARP) xor (1 shl bit))
    }
}
