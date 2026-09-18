package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.ItemAttribute

/*
 * Owner 2026-09-19: "put the shrine of ralos in the grand exchange". OSRS loc 52405 "Shrine of Ralos" (option "Bask") imported as
 * local loc 62746 (ShrineOfRalosImportTool, tx-20260918-222559, both caches) and spawned at the GE next to the singing bowl.
 *
 * OSRS Wiki "Shrine of Ralos": shrines are "used to restore prayer points, create sunfire runes, and upgrade Dizana's quiver using
 * 150,000 sunfire splinters". "Blessed Dizana's quiver": use a Dizana's quiver on the shrine with "a total of 150,000 sunfire
 * splinters"; "Charging the quiver with sunfire splinters contributes to the required amount of splinters to upgrade".
 * ADAPTED (not quoted by the wiki): the Bask animation/sound/message (the altar pray route) and the blessing messages.
 * Sunfire runes are not part of this cache (no sunfire rune item) and are not offered.
 */
val SHRINE_OF_RALOS = 62746

spawn_obj(obj = SHRINE_OF_RALOS, x = 3158, z = 3499, type = 10, rot = 0)

on_obj_option(obj = SHRINE_OF_RALOS, option = "Bask") {
    player.queue {
        player.animate(Anims.ALTAR_PRAY)
        player.filterableMessage("You recharge your Prayer points.")
        player.playSound(Sfx.PRAYER_RECHARGE)
        gg.rsmod.plugins.content.mechanics.prayer.Prayers.rechargePrayerPoints(player)
    }
}

/** Normal and Trouver-locked quivers (charged or not) -> the matching blessed quiver. */
val BLESS_RESULT: Map<Int, Int> =
    mapOf(
        Items.DIZANAS_QUIVER_UNCHARGED to Items.BLESSED_DIZANAS_QUIVER,
        Items.DIZANAS_QUIVER to Items.BLESSED_DIZANAS_QUIVER,
        Items.DIZANAS_QUIVER_L_UNCHARGED to Items.BLESSED_DIZANAS_QUIVER_L,
        Items.DIZANAS_QUIVER_L to Items.BLESSED_DIZANAS_QUIVER_L,
    )

BLESS_RESULT.forEach { (quiverId, blessedId) ->
    on_item_on_obj(obj = SHRINE_OF_RALOS, item = quiverId) {
        val charged = player.attr[DizanasQuiver.SPLINTERS_CHARGED] ?: 0
        val needed = maxOf(0, DizanasQuiver.BLESSING_SPLINTERS - charged)
        val carried = player.inventory.getItemCount(Items.SUNFIRE_SPLINTERS)
        if (carried < needed) {
            player.queue {
                itemMessageBox(
                    "You need a total of 150,000 sunfire splinters to bless your quiver. You have charged it with " +
                        "${"%,d".format(charged)} and carry ${"%,d".format(carried)}.",
                    item = Items.SUNFIRE_SPLINTERS,
                )
            }
            return@on_item_on_obj
        }
        player.queue {
            if (!confirmItemAction(blessedId, "Bless your quiver at the Shrine of Ralos?", "This uses ${"%,d".format(needed)} sunfire splinters from your inventory.")) {
                return@queue
            }
            val slot = player.inventory.getItemIndex(quiverId, skipAttrItems = false)
            val quiver = player.inventory[slot]?.takeIf { it.id == quiverId } ?: return@queue
            if (player.inventory.getItemCount(Items.SUNFIRE_SPLINTERS) < needed) return@queue
            if (needed > 0) player.inventory.remove(Items.SUNFIRE_SPLINTERS, needed)
            player.attr[DizanasQuiver.SPLINTERS_CHARGED] = charged + needed - DizanasQuiver.BLESSING_SPLINTERS
            // Stored ammunition stays in the quiver; charges are no longer needed (permanent Sunfire).
            player.inventory[slot] = Item(blessedId, quiver.amount).copyAttr(quiver).also { it.attr.remove(ItemAttribute.CHARGES) }
            player.animate(Anims.ALTAR_PRAY)
            itemMessageBox("Ralos blesses your quiver.", item = blessedId)
        }
    }
}
