package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.items.osrs.CasketTeleportScrolls
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleports.TeleportSpell

/**
 * Owner request 2026-09-19: "all the teleports in the game must be in the POH". The house nexus shows this directory on the
 * Portal Nexus screen ([PohTeleportUi]):
 * each spellbook teleport ([TeleportSpell]), every teleport jewellery destination, the house/city tablets, the OSRS
 * casket scrolls and home. Destinations are the same tiles the item / spell handlers use (sources named per group); the
 * teleport itself goes through [canTeleport] / [teleport], so wilderness and Deadman rules stay as for the real item.
 */
object PohTeleports {
    sealed class Node(val name: String)

    /** The row icon on the nexus screen: a spell's own spellbook sprite, or the item that makes the teleport. */
    sealed class Icon {
        data class Sprite(val id: Int) : Icon()

        data class Obj(val id: Int) : Icon()
    }

    class Leaf(name: String, val type: TeleportType, val icon: Icon, val tile: (Player) -> Tile?) : Node(name)

    class Branch(name: String, val children: List<Node>) : Node(name)

    /**
     * The lit icon of every teleport spell: the first sprite argument of the 667 spellbook's onLoad script 6 on the spell's
     * component (interfaces 192 / 193 / 430, read from the cache 2026-10-07 with InterfaceHookProbeTool) - the icon the
     * player sees in the spellbook, as OSRS's nexus rows show the spell's own icon (clientscript 2675, struct param 675).
     */
    val SPELL_ICONS: Map<TeleportSpell, Int> =
        mapOf(
            TeleportSpell.VARROCK to 27, TeleportSpell.MOBILISING_ARMIES to 1564, TeleportSpell.LUMBRIDGE to 30,
            TeleportSpell.FALADOR to 33, TeleportSpell.CAMELOT to 37, TeleportSpell.ARDOUGNE to 54, TeleportSpell.WATCHTOWER to 55,
            TeleportSpell.TROLLHEIM to 323, TeleportSpell.APE_ATOLL to 357,
            TeleportSpell.PADDEWWA to 341, TeleportSpell.SENNTISTEN to 342, TeleportSpell.KHARYRLL to 343, TeleportSpell.LASSAR to 344,
            TeleportSpell.DAREEYAK to 345, TeleportSpell.CARRALLANGAR to 346, TeleportSpell.ANNAKARL to 347, TeleportSpell.GHORROCK to 348,
            TeleportSpell.MOONCLAN to 544, TeleportSpell.OURANIA to 583, TeleportSpell.WATERBIRTH to 545, TeleportSpell.BARBARIAN to 547,
            TeleportSpell.KHAZARD to 549, TeleportSpell.FISHING_GUILD to 555, TeleportSpell.CATHERBY to 556,
            TeleportSpell.ICE_PLATEAU to 557, TeleportSpell.SOUTH_FALADOR to 4585, TeleportSpell.NORTH_ARDOUGNE to 4587,
            TeleportSpell.LUNAR_TROLLHEIM to 7685,
        )

    /** Home Teleport's spellbook icon (192 component 24). */
    const val HOME_ICON = 356

    private fun spells(type: TeleportType) =
        TeleportSpell.values().filter { it.type == type }.map { spell ->
            Leaf(spell.spellName.removeSuffix(" Teleport").removePrefix("Teleport to "), spell.type, Icon.Sprite(SPELL_ICONS.getValue(spell))) { player ->
                if (spell == TeleportSpell.APE_ATOLL && !gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(player, "Monkey Madness")) {
                    player.message("You need to complete Monkey Madness to use this teleport.")
                    null
                } else {
                    gg.rsmod.game.model.collision.Landing.pick(player.world.collision, spell.endArea)
                }
            }
        }

    private fun fixed(item: Int, vararg places: Pair<String, Tile>) = places.map { (name, tile) -> Leaf(name, TeleportType.JEWELRY, Icon.Obj(item)) { tile } }

    private fun area(name: String, item: Int, x1: Int, z1: Int, x2: Int, z2: Int) =
        Leaf(name, TeleportType.MODERN, Icon.Obj(item)) { player -> gg.rsmod.game.model.collision.Landing.pick(player.world.collision, gg.rsmod.game.model.Area(x1, z1, x2, z2)) }

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
                                Items.AMULET_OF_GLORY_4,
                                "Edgeville" to Tile(3086, 3503, 0), "Karamja" to Tile(2917, 3175, 0),
                                "Draynor Village" to Tile(3104, 3249, 0), "Al Kharid" to Tile(3293, 3162, 0),
                            ),
                        ),
                        Branch(
                            "Games necklace",
                            fixed(
                                Items.GAMES_NECKLACE_8,
                                "Burthorpe" to Tile(2899, 3546, 0), "Barbarian Outpost" to Tile(2520, 3571, 0),
                                "Gamers' Grotto" to Tile(2970, 9673, 0), "Corporeal Beast" to Tile(2885, 4372, 2),
                            ),
                        ),
                        Branch(
                            "Ring of dueling",
                            fixed(
                                Items.RING_OF_DUELLING_8,
                                "Duel Arena" to Tile(3308, 3234, 0), "Castle Wars" to Tile(2440, 3089, 0),
                                "Mobilising Armies" to Tile(2412, 2849, 0), "Fist of Guthix" to Tile(1703, 5599, 0),
                            ),
                        ),
                        Branch(
                            "Skills necklace",
                            fixed(
                                Items.SKILLS_NECKLACE_4,
                                "Fishing Guild" to Tile(2614, 3383, 0), "Mining Guild" to Tile(3017, 3339, 0),
                                "Crafting Guild" to Tile(2933, 3294, 0), "Cooking Guild" to Tile(3143, 3441, 0),
                            ),
                        ),
                        Branch(
                            "Combat bracelet",
                            fixed(
                                Items.COMBAT_BRACELET_4,
                                "Warriors' Guild" to Tile(2881, 3542, 0), "Champions' Guild" to Tile(3191, 3367, 0),
                                "Monastery" to Tile(3052, 3490, 0), "Ranging Guild" to Tile(2654, 3441, 0),
                            ),
                        ),
                        Branch("Ring of wealth", fixed(Items.RING_OF_WEALTH_4, *gg.rsmod.plugins.content.magic.teleports.JewelleryDestinations.RING_OF_WEALTH.toList().toTypedArray())),
                        Branch(
                            "Ring of slaying",
                            fixed(
                                Items.RING_OF_SLAYING_8,
                                "Sumona" to Tile(3353, 3006, 0), "Slayer Tower" to Tile(3427, 3538, 0),
                                "Fremennik Slayer Dungeon" to Tile(2787, 3616, 0), "Tarn's Lair" to Tile(3150, 4666, 0),
                            ),
                        ),
                    ),
                ),
                Branch(
                    "Tablets and scrolls",
                    listOf(
                        Leaf("Home", TeleportType.MODERN, Icon.Sprite(HOME_ICON)) { it.world.gameContext.home },
                        area("Rimmington", Items.RIMMINGTON_TABLET, 2953, 3222, 2956, 3226),
                        area("Taverley", Items.TAVERLEY_TABLET, 2893, 3463, 2894, 3467),
                        area("Pollnivneach", Items.POLLNIVNEACH_TABLET, 3338, 3003, 3342, 3004),
                        area("Rellekka", Items.RELLEKKA_TABLET, 2668, 3631, 2671, 3632),
                        area("Brimhaven", Items.BRIMHAVEN_TABLET, 2757, 3176, 2758, 3179),
                        area("Yanille", Items.YANILLE_TABLET, 2542, 3095, 2545, 3096),
                        area("Trollheim", Items.TROLLHEIM_TABLET, 2888, 3678, 2893, 3681),
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
                            area(name, item, d.centreX - d.radius, d.centreZ - d.radius, d.centreX + d.radius, d.centreZ + d.radius)
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
}
