package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items

/*
 * N462 ToA rewards: fortifying Masori armour (OSRS Wiki Masori mask (f) / Masori body (f) / Masori chaps (f), Recipe templates):
 * Masori piece + Armadylean plate x1 / x4 / x3, Hammer, 90 Crafting, 830 / 3,320 / 2,490 Crafting experience; "not reversible".
 * ONZEKER: the OSRS messages and animation are not in the sources - the level refusal uses the codebase's standard wording, the
 * fortifying itself is silent.
 */
data class Fortify(val masori: Int, val fortified: Int, val plates: Int, val xp: Double)

val FORTIFY = listOf(
    Fortify(Items.MASORI_MASK, Items.MASORI_MASK_F, 1, 830.0),
    Fortify(Items.MASORI_BODY, Items.MASORI_BODY_F, 4, 3_320.0),
    Fortify(Items.MASORI_CHAPS, Items.MASORI_CHAPS_F, 3, 2_490.0),
)

FORTIFY.forEach { f ->
    on_item_on_item(item1 = Items.ARMADYLEAN_PLATE, item2 = f.masori) {
        if (player.skills.getCurrentLevel(Skills.CRAFTING) < 90) {
            player.message("You need a Crafting level of 90 to do that.")
            return@on_item_on_item
        }
        if (!player.inventory.contains(Items.HAMMER)) {
            player.message("You need a hammer to do that.")
            return@on_item_on_item
        }
        if (player.inventory.getItemCount(Items.ARMADYLEAN_PLATE) < f.plates) {
            player.message("You need ${f.plates} Armadylean plates to do that.")
            return@on_item_on_item
        }
        val slot = player.inventory.getItemIndex(f.masori, skipAttrItems = false)
        if (slot < 0 || !player.inventory.remove(Items.ARMADYLEAN_PLATE, f.plates, assureFullRemoval = true).hasSucceeded()) return@on_item_on_item
        player.inventory[slot] = gg.rsmod.game.model.item.Item(f.fortified)
        player.addXp(Skills.CRAFTING, f.xp)
    }
}
