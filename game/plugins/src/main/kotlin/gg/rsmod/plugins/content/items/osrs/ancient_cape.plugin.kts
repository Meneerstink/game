package gg.rsmod.plugins.content.items.osrs

AncientCape.assets.fabric?.let { fabric ->
    (AncientCape.GOD_CAPES + AncientCape.MAX_HOODS.keys).forEach { cape ->
        on_item_on_item(item1 = fabric, item2 = cape) {
            val slot = player.inventory.getItemIndex(cape, skipAttrItems = false)
            if (slot < 0) return@on_item_on_item
            player.queue {
                if (options("Yes, I wish to proceed.", "No, I do not wish to proceed.", title = "This process is irreversible. Are you sure you wish to proceed?") != 1) return@queue
                // The exact cape must still occupy the original slot after the confirmation.
                if (player.inventory[slot]?.id != cape) return@queue
                when (AncientCape.convert(player, slot)) {
                    AncientCape.Result.CONVERTED -> {
                        if (cape in AncientCape.GOD_CAPES) {
                            val god = gg.rsmod.plugins.content.areas.wilderness.MageArenaII.God.values().first { it.imbued == cape }
                            itemMessageBox("You use the Ancient fabric on the Imbued ${god.title} cape to create an Imbued ancient cape.", AncientCape.assets.cape!!)
                        }
                    }
                    AncientCape.Result.MISSING_HOOD -> player.message("You need a max hood in your inventory to do that.")
                    else -> Unit
                }
            }
        }
    }
}
