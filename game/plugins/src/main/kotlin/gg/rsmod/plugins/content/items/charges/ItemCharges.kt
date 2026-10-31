package gg.rsmod.plugins.content.items.charges

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * Shared read-out behind the `Check-charges` option.
 *
 * 46 items in the production cache expose that option - the Nex sets (Torva/Pernix/Virtus/Zaryte),
 * the Dungeoneering chaotic and gravite gear, the locators, both Vampyrium medallions, the Crystal
 * saw, the Enchanted water tiara, the Sceptre of the gods, the Arcane capacitor (c) and the three
 * "Eye of the ..." items. The exact set is `./gradlew :game:runItemParamProbeTool
 * --args="<cache> menu check-charges"`; nothing had ever bound it, so every one of them answered
 * with `OpHeld3Handler`'s `Unhandled item action` fallback.
 *
 * Charges are read from [ItemAttribute.CHARGES], the single generic charge store this server
 * already uses (brawling gloves in `PlayerExt`). None
 * of the 46 items above consume charges yet, so in practice they all still carry no attribute and
 * report as fully charged - which is exactly what they are. The read-out starts reporting real
 * numbers the moment any of those degradation models is implemented, without touching this file.
 *
 * Deliberately *not* done here: inventing per-item charge capacities. Charge counts for these sets
 * are a server-side quantity with no representation anywhere in the revision-667 cache, so printing
 * "60000 charges remaining" would be fabricated rather than sourced.
 */
object ItemCharges {
    const val OPTION = "Check-charges"

    /** Native charged GE forms. OSRS Wiki Black mask and the three Broodoo shield pages. */
    val removable = mapOf(
        Items.BROODOO_SHIELD_10 to Items.BROODOO_SHIELD,
        Items.BROODOO_SHIELD_10_6237 to Items.BROODOO_SHIELD_6257,
        Items.BROODOO_SHIELD_10_6259 to Items.BROODOO_SHIELD_6279,
        Items.BLACK_MASK_10 to Items.BLACK_MASK,
    )

    fun removeCharges(player: Player, slot: Int) {
        val held = player.inventory[slot] ?: return
        val empty = removable[held.id] ?: return
        // Charge state belongs to the id for these items; never carry CHARGES into the empty form.
        player.inventory[slot] = Item(empty)
    }

    fun check(
        player: Player,
        item: Item?,
    ) {
        if (item == null) {
            return
        }
        val name =
            player.world.definitions
                .get(ItemDef::class.java, item.id)
                .name
        val charges = item.attr[ItemAttribute.CHARGES]
        if (charges == null) {
            player.message("Your $name is fully charged.")
        } else {
            player.message("Your $name has $charges charge${if (charges == 1) "" else "s"} remaining.")
        }
    }
}
