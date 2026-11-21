package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.fs.def.ItemDef

/*
 * PROJECT48 DMM-ITEMS group A: the item options of the nut items (cache options of the imported items).
 */

on_item_option(DmmItems.ROYAL_SEED_POD, "commune") { DmmItems.royalSeedPod(player) }
on_item_option(DmmItems.ROYAL_SEED_POD, "destroy") { player.message("You can get another one from King Narnode Shareen for 2 coins.") }

on_item_option(DmmItems.ESCAPE_CRYSTAL, "teleport") { DmmItems.escapeCrystal(player) }
on_item_option(DmmItems.ESCAPE_CRYSTAL, "configure") { player.message("Auto-teleport settings are not available on this server yet.") }

on_item_option(DmmItems.CRYSTAL_OF_ECHOES, "teleport-back") { DmmItems.teleportBack(player) }
on_item_option(DmmItems.CRYSTAL_OF_ECHOES, "save-memory") { DmmItems.saveMemory(player) }
on_item_option(DmmItems.CRYSTAL_OF_ECHOES, "check-memories") { DmmItems.checkMemory(player) }

on_item_option(DmmItems.ETERNAL_TELEPORT_CRYSTAL, "lletya") { DmmItems.eternalCrystal(player) }
on_item_option(DmmItems.ETERNAL_TELEPORT_CRYSTAL, "prifddinas") { DmmItems.eternalCrystal(player) }

on_item_option(DmmItems.AMULET_OF_ETERNAL_GLORY, "rub") {
    player.queue {
        val names = DmmItems.ETERNAL_GLORY_TELEPORTS.keys.toList()
        val choice = options(*names.map { "$it." }.toTypedArray(), "Nowhere.")
        if (choice in 1..names.size) DmmItems.eternalGlory(player, DmmItems.ETERNAL_GLORY_TELEPORTS.getValue(names[choice - 1]))
    }
}

run {
    val def = world.definitions.get(ItemDef::class.java, DmmItems.AMULET_OF_ETERNAL_GLORY)
    DmmItems.ETERNAL_GLORY_TELEPORTS.forEach { (name, tile) ->
        if (def.equipmentMenu.any { it.equals(name, true) }) {
            on_equipment_option(DmmItems.AMULET_OF_ETERNAL_GLORY, option = name) { DmmItems.eternalGlory(player, tile) }
        }
    }
}

on_item_option(DmmItems.IMBUED_HEART, "invigorate") { DmmItems.invigorate(player, saturated = false) }
on_item_option(DmmItems.SATURATED_HEART, "invigorate") { DmmItems.invigorate(player, saturated = true) }
