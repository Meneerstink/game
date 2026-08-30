package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleport

/**
 * R02.3: the Magic Carpet network (desert Rug Merchants), matching HOME_DESIGN_2.png's rolled-
 * carpet prop in the Vervoer quadrant. Same discipline as `gnome_glider.plugin.kts`: all 6 real
 * Rug Merchant npcs had a genuine dead cache `"Travel"` option (confirmed via `NpcCensus`), and
 * each station's tile is that merchant's own real, already-verified spawn location (from the
 * matching `spawns_*.plugin.kts` file) - not invented. All 6 real coordinates sit in the
 * Kharidian Desert band (x 3182-3401, z 2814-3109), consistent with a real desert carpet
 * network, not a coincidence.
 *
 * [TeleportType.MODERN] used for the same reason as the Gnome Glider: no verified carpet-
 * specific animation/graphic exists in this environment.
 */
private data class CarpetStation(
    val npc: Int,
    val label: String,
    val tile: Tile,
)

private val STATIONS =
    listOf(
        CarpetStation(Npcs.RUG_MERCHANT, "Rug Merchant's station", Tile(3311, 3109, 0)),
        CarpetStation(Npcs.RUG_MERCHANT_2292, "Rug Merchant's station (west)", Tile(3182, 3043, 0)),
        CarpetStation(Npcs.RUG_MERCHANT_2293, "Rug Merchant's station (north)", Tile(3349, 2942, 0)),
        CarpetStation(Npcs.RUG_MERCHANT_2294, "Rug Merchant's station (far north)", Tile(3350, 3001, 0)),
        CarpetStation(Npcs.RUG_MERCHANT_2298, "Rug Merchant's station (south)", Tile(3287, 2814, 0)),
        CarpetStation(Npcs.RUG_MERCHANT_3020, "Rug Merchant's station (east)", Tile(3401, 2918, 0)),
    )

STATIONS.forEach { origin ->
    on_npc_option(origin.npc, "Travel") {
        val destinations = STATIONS.filter { it.npc != origin.npc }
        player.queue {
            val choice = options(*destinations.map { it.label }.toTypedArray(), title = "Travel to...")
            if (choice < 1 || choice > destinations.size) {
                return@queue
            }
            val dest = destinations[choice - 1]
            player.teleport(dest.tile.transform(1, 0), TeleportType.MODERN)
        }
    }
}
