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

    /**
     * The revision-667 Treasure Trail scrolls (19475-19480, cache option "Read"), which had no handler at all (owner
     * 2026-09-22: "Bandit Camp teleport is currently unhandled"). Landing squares are the Void donor's same-era
     * `*_teleport` areas tagged "scroll" (bandit_camp, miscellania, piscatoris, tai_bwo_wannai, varrock.areas.toml);
     * Void has no Nardah square, and RS Wiki "Pollnivneach Teleport" (`{{Teleport map|3361,2970}}`) states this scroll
     * was the "Nardah teleport" before its 2013 rename to match where it already went. Visuals: TeleportType.SCROLL
     * (Void teleport_scroll anim 14293 / gfx 94).
     */
    val READ_SCROLLS =
        mapOf(
            Items.NARDAH_TELEPORT to Destination(3361, 2970, 1),
            Items.BANDIT_CAMP_TELEPORT to Destination(3172, 2983, 2),
            Items.MISCELLANIA_TELEPORT to Destination(2513, 3858, 2),
            Items.PHOENIX_LAIR_TELEPORT to Destination(2292, 3620, 2),
            Items.TAI_BWO_WANNAI_TELEPORT to Destination(2805, 3086, 2),
            Items.LUMBER_YARD_TELEPORT to Destination(3301, 3486, 1),
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
