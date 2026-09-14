package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS-IMPORT casket sub-batch "casket-teleports": Treasure Trail teleport scrolls (OSRS Wiki item pages and "Teleport scrolls").
 *
 * - Destinations: the wiki item page map square (centre x, z and radius), e.g. Digsite teleport `{{Map|3325,3412|r=2}}`. PROVISIONAL landing
 *   area: the map marks the arrival square, not an exact tile; landing avoids tiles holding an interactable object.
 * - Gated scrolls: "Attempting to use the scroll without meeting the requirements displays the game message" (wiki). Lunar Diplomacy,
 *   Regicide and Cabin Fever are not implemented here, and the Mort'ton requirement ("spoken to Drezel after completing Priest in Peril")
 *   is not tracked by the Priest in Peril implementation, so these four always refuse with the sourced message (the requirement is
 *   enforced, never bypassed).
 * - ADAPTED: the 667 teleport tablet visuals (TeleportType.TAB) and its Wilderness limit (level 20); the wiki states no Wilderness rule
 *   for teleport scrolls (SOURCE_GAP).
 */
object CasketTeleportScrolls {
    data class Destination(val centreX: Int, val centreZ: Int, val radius: Int)

    val DESTINATIONS =
        mapOf(
            Items.DIGSITE_TELEPORT to Destination(3325, 3412, 2),
            Items.FELDIP_HILLS_TELEPORT to Destination(2541, 2925, 2),
            Items.PEST_CONTROL_TELEPORT to Destination(2658, 2659, 2),
            // "{{Map|2339,3649|r=1.5}}": the 1.5-tile square rounds down to the 3x3 around the centre.
            Items.PISCATORIS_TELEPORT to Destination(2339, 3649, 1),
            Items.LUMBERYARD_TELEPORT to Destination(3302, 3487, 2),
        )

    val REFUSALS =
        mapOf(
            Items.LUNAR_ISLE_TELEPORT to "You need to complete the Lunar Diplomacy quest before you can reach the island.",
            Items.MORTTON_TELEPORT to "You must speak to Drezel after completing the Priest in Peril quest before you can teleport to Mort'ton",
            Items.IORWERTH_CAMP_TELEPORT to "You need to complete the Regicide quest before you can teleport to Tirannwn.",
            Items.MOS_LEHARMLESS_TELEPORT to "You need to complete the Cabin Fever quest before you can teleport to Mos Le'harmless.",
        )

    const val LANDING_ATTEMPTS = 20
}
