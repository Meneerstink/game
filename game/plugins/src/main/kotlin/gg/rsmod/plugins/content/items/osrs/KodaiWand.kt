package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment

/**
 * OSRS-IMPORT Kodai wand (OSRS Wiki "Kodai wand", 2026-09-14): "The wand has a 15% chance of negating rune costs when casting an
 * offensive spell"; "provides unlimited water runes when equipped" (MagicStaves); "able to autocast Ancient Magicks, standard spells,
 * and Arceuus combat spells" (no autocast restriction applies here; Arceuus does not exist in 667); with a Tome of Water "the accuracy
 * and damage bonuses from the tome are used while also using soaked page charges" (Tomes is independent of the weapon).
 */
object KodaiWand {
    const val RUNE_SAVE_CHANCE = 0.15

    fun isWielding(player: Player): Boolean = player.getEquipment(EquipmentType.WEAPON)?.id == Items.KODAI_WAND

    fun savesRunes(player: Player): Boolean = isWielding(player) && player.world.randomDouble() < RUNE_SAVE_CHANCE
}
