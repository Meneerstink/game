package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Sfx

on_item_on_item(item1 = Items.GLASSBLOWING_PIPE, item2 = Items.DRAGONSTONE) {
    LocatorOrb.craft(player)
}

// The cache import is deliberately a separate Claude step. Name-based Feel binding only after the right definitions exist.
if (LocatorOrb.available(world.definitions)) {
    on_item_option(item = LocatorOrb.ACTIVE, option = "Feel") {
        if (player.attr[LocatorOrb.WARNING_ACCEPTED] == true) {
            LocatorOrb.feel(player)
        } else {
            player.queue {
                if (options("Yes, feel the orb.", "No.", title = "The orb will hurt you, reducing your Hitpoints by up to 10. Continue?") != 1) return@queue
                if (!player.inventory.contains(LocatorOrb.ACTIVE) || player.isDead()) return@queue
                player.attr[LocatorOrb.WARNING_ACCEPTED] = true
                LocatorOrb.feel(player)
            }
        }
    }
}

// Both objects have a Destroy option; use the existing GUI with the item's OSRS replacement explanation.
if (LocatorOrb.available(world.definitions)) {
    LocatorOrb.IDS.forEach { id ->
        val destroy: Plugin.() -> Unit = {
            player.queue {
                val detail = if (id == LocatorOrb.ACTIVE) "Hopefully Ava can give you another." else "You'll have to make another one."
                if (!confirmItemAction(id, "Are you sure you want to destroy this item?", detail)) return@queue
                if (player.inventory.remove(id).hasSucceeded()) player.playSound(Sfx.DESTROY_OBJECT)
            }
        }
        on_item_option(item = id, option = "Destroy", logic = destroy)
        // The 667 inventory interface emits op 10 for Destroy (IfButton1Handler), while OpHeld5 emits op 5.
        // 10 is that protocol action, not a guessed cache menu slot. The shared late fallback respects this binding.
        on_item_option(item = id, option = 10, logic = destroy)
    }
}
