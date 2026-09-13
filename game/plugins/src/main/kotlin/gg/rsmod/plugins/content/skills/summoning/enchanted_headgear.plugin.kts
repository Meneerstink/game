package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_ID
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_ID_ATTR
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/** RCV-010 A5: Pikkupstix enchanting and scroll-holding headgear (see [EnchantedHeadgear]). */
val pikkupstixIds = intArrayOf(Npcs.PIKKUPSTIX, Npcs.PIKKUPSTIX_6971, Npcs.PIKKUPSTIX_7952, Npcs.PIKKUPSTIX_7953)
    .filter { if_npc_has_option(it, "enchant") }

pikkupstixIds.forEach { npc ->
    on_npc_option(npc = npc, option = "enchant") {
        player.queue {
            chatNpc("Bring me a piece of headwear and I'll enchant it to hold Summoning scrolls, free of charge. Just use the helm on me.")
        }
    }
    EnchantedHeadgear.allItems.forEach { item ->
        on_item_on_npc(item = item, npc = npc) {
            val slot = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_npc
            val id = player.attr[INTERACTING_ITEM_ID] ?: return@on_item_on_npc
            EnchantedHeadgear.enchant(player, id, slot)
        }
    }
}

val scrollPairs = mutableSetOf<Pair<Int, Int>>()
EnchantedHeadgear.rows.forEach { headgear ->
    EnchantedHeadgear.combatScrollIds.forEach { scroll ->
        listOf(headgear.enchanted, headgear.charged).forEach { helm -> scrollPairs += minOf(scroll, helm) to maxOf(scroll, helm) }
    }
}
scrollPairs.forEach { (first, second) ->
    on_item_on_item(item1 = first, item2 = second) {
        val usedId = player.attr[INTERACTING_ITEM_ID] ?: return@on_item_on_item
        val usedSlot = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val otherSlot = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val otherId = player.attr[OTHER_ITEM_ID_ATTR] ?: return@on_item_on_item
        if (usedId in EnchantedHeadgear.combatScrollIds) {
            EnchantedHeadgear.store(player, usedId, otherSlot)
        } else {
            EnchantedHeadgear.store(player, otherId, usedSlot)
        }
    }
}

EnchantedHeadgear.rows.forEach { headgear ->
    val def = world.definitions.get(ItemDef::class.java, headgear.charged)
    if (def.inventoryMenu.any { it.equals("Commune", ignoreCase = true) }) {
        on_item_option(item = headgear.charged, option = "commune") { EnchantedHeadgear.commune(player) }
    }
    if (def.inventoryMenu.any { it.equals("Uncharge", ignoreCase = true) }) {
        on_item_option(item = headgear.charged, option = "uncharge") {
            val slot = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_option
            EnchantedHeadgear.uncharge(player, headgear, worn = false, slot = slot)
        }
    }
    if (def.equipmentMenu.any { it.equals("Commune", ignoreCase = true) }) {
        on_equipment_option(item = headgear.charged, option = "commune") { EnchantedHeadgear.commune(player) }
    }
    if (def.equipmentMenu.any { it.equals("Uncharge", ignoreCase = true) }) {
        on_equipment_option(item = headgear.charged, option = "uncharge") { EnchantedHeadgear.uncharge(player, headgear, worn = true, slot = -1) }
    }
}
