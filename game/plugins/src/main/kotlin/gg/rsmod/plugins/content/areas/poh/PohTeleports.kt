package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.randomTile
import gg.rsmod.plugins.content.items.osrs.CasketTeleportScrolls
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.magic.teleports.TeleportSpell

/**
 * Owner request 2026-09-19: "all the teleports in the game must be in the POH". Every house portal opens this directory:
 * each spellbook teleport ([TeleportSpell]), every teleport jewellery destination, the house/city tablets, the OSRS
 * casket scrolls and home. Destinations are the same tiles the item / spell handlers use (sources named per group); the
 * teleport itself goes through [canTeleport] / [teleport], so wilderness and Deadman rules stay as for the real item.
 */
object PohTeleports {
    sealed class Node(val name: String)

    class Leaf(name: String, val type: TeleportType, val tile: (Player) -> Tile?) : Node(name)

    class Branch(name: String, val children: List<Node>) : Node(name)

    private fun spells(type: TeleportType) =
        TeleportSpell.values().filter { it.type == type }.map { spell ->
            Leaf(spell.spellName.removeSuffix(" Teleport").removePrefix("Teleport to "), spell.type) { player ->
                if (spell == TeleportSpell.APE_ATOLL && player.getVarp(Varps.MONKEY_MADNESS_PROGRESS) < 9) {
                    player.message("You must speak to King Narnode in the Grand Exchange to unlock Ape Atoll.")
                    null
                } else {
                    spell.endArea.randomTile
                }
            }
        }

    private fun fixed(vararg places: Pair<String, Tile>) = places.map { (name, tile) -> Leaf(name, TeleportType.JEWELRY) { tile } }

    private fun area(name: String, x1: Int, z1: Int, x2: Int, z2: Int) =
        Leaf(name, TeleportType.MODERN) { Tile((x1..x2).random(), (z1..z2).random(), 0) }

    /** Tiles copied from items/jewellery/<item>.plugin.kts and items/teletabs/teleport_tab.plugin.kts. */
    val ROOT =
        Branch(
            "Teleports",
            listOf(
                Branch("Standard spellbook", spells(TeleportType.MODERN)),
                Branch("Ancient Magicks", spells(TeleportType.ANCIENT)),
                Branch("Lunar spellbook", spells(TeleportType.LUNAR)),
                Branch(
                    "Jewellery",
                    listOf(
                        Branch(
                            "Amulet of glory",
                            fixed(
                                "Edgeville" to Tile(3086, 3503, 0), "Karamja" to Tile(2917, 3175, 0),
                                "Draynor Village" to Tile(3104, 3249, 0), "Al Kharid" to Tile(3293, 3162, 0),
                            ),
                        ),
                        Branch(
                            "Games necklace",
                            fixed(
                                "Burthorpe" to Tile(2899, 3546, 0), "Barbarian Outpost" to Tile(2520, 3571, 0),
                                "Gamers' Grotto" to Tile(2970, 9673, 0), "Corporeal Beast" to Tile(2885, 4372, 2),
                            ),
                        ),
                        Branch(
                            "Ring of dueling",
                            fixed(
                                "Duel Arena" to Tile(3308, 3234, 0), "Castle Wars" to Tile(2440, 3089, 0),
                                "Mobilising Armies" to Tile(2412, 2849, 0), "Fist of Guthix" to Tile(1703, 5599, 0),
                            ),
                        ),
                        Branch(
                            "Skills necklace",
                            fixed(
                                "Fishing Guild" to Tile(2614, 3383, 0), "Mining Guild" to Tile(3017, 3339, 0),
                                "Crafting Guild" to Tile(2933, 3294, 0), "Cooking Guild" to Tile(3143, 3441, 0),
                            ),
                        ),
                        Branch(
                            "Combat bracelet",
                            fixed(
                                "Warriors' Guild" to Tile(2881, 3542, 0), "Champions' Guild" to Tile(3191, 3367, 0),
                                "Monastery" to Tile(3052, 3490, 0), "Ranging Guild" to Tile(2654, 3441, 0),
                            ),
                        ),
                        Branch("Ring of wealth", fixed("Miscellania" to Tile(2528, 3859, 0), "Grand Exchange" to Tile(3164, 3460, 0))),
                        Branch(
                            "Ring of slaying",
                            fixed(
                                "Sumona" to Tile(3359, 2993, 0), "Slayer Tower" to Tile(3428, 3535, 0),
                                "Fremennik Slayer Dungeon" to Tile(2791, 3615, 0), "Tarn's Lair" to Tile(3187, 4601, 0),
                            ),
                        ),
                    ),
                ),
                Branch(
                    "Tablets and scrolls",
                    listOf(
                        Leaf("Home", TeleportType.MODERN) { it.world.gameContext.home },
                        area("Rimmington", 2953, 3222, 2956, 3226),
                        area("Taverley", 2893, 3463, 2894, 3467),
                        area("Pollnivneach", 3338, 3003, 3342, 3004),
                        area("Rellekka", 2668, 3631, 2671, 3632),
                        area("Brimhaven", 2757, 3176, 2758, 3179),
                        area("Yanille", 2542, 3095, 2545, 3096),
                        area("Trollheim", 2888, 3678, 2893, 3681),
                    ) +
                        CasketTeleportScrolls.DESTINATIONS.map { (item, d) ->
                            val name =
                                when (item) {
                                    gg.rsmod.plugins.api.cfg.Items.DIGSITE_TELEPORT -> "Digsite"
                                    gg.rsmod.plugins.api.cfg.Items.FELDIP_HILLS_TELEPORT -> "Feldip Hills"
                                    gg.rsmod.plugins.api.cfg.Items.PEST_CONTROL_TELEPORT -> "Pest Control"
                                    gg.rsmod.plugins.api.cfg.Items.PISCATORIS_TELEPORT -> "Piscatoris"
                                    else -> "Lumberyard"
                                }
                            area(name, d.centreX - d.radius, d.centreZ - d.radius, d.centreX + d.radius, d.centreZ + d.radius)
                        },
                ),
            ),
        )

    /** Every leaf in the directory (for the guard test). */
    fun leaves(node: Node = ROOT): List<Leaf> =
        when (node) {
            is Leaf -> listOf(node)
            is Branch -> node.children.flatMap { leaves(it) }
        }

    /** Paged chooser: four entries per page plus "More..." (or "Cancel" on the last page); a branch opens its children. */
    suspend fun QueueTask.choose(branch: Branch): Leaf? {
        var page = 0
        while (true) {
            val slice = branch.children.drop(page * 4).take(4)
            val more = branch.children.size > (page + 1) * 4
            val labels = slice.map { it.name } + (if (more) "More..." else "Cancel")
            val pick = options(*labels.toTypedArray(), title = branch.name)
            if (pick < 1) return null
            if (pick <= slice.size) {
                return when (val node = slice[pick - 1]) {
                    is Leaf -> node
                    is Branch -> choose(node)
                }
            }
            if (!more) return null
            page++
        }
    }

    suspend fun QueueTask.openDirectory(player: Player) {
        val leaf = choose(ROOT) ?: return
        val tile = leaf.tile(player) ?: return
        player.canTeleport(leaf.type) {
            player.teleport(tile, leaf.type)
        }
    }
}
