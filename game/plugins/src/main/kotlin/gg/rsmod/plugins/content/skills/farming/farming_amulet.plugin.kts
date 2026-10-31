package gg.rsmod.plugins.content.skills.farming

import gg.rsmod.plugins.api.EquipmentType

fun Plugin.rubFarmingAmulet(id: Int, worn: Boolean) {
    val container = if (worn) player.equipment else player.inventory
    val slot = if (worn) EquipmentType.AMULET.id else player.getInteractingItemSlot()
    val held = container[slot] ?: return
    if (held.id != id) return
    val patch = FarmingAmulet.bound(player)
    if (patch == null) {
        player.message("Use this amulet on an allotment or flower patch to bind it first.")
        return
    }
    player.queue {
        if (options("Check the bound patch.", "Cancel.", title = "Amulet of farming") == 1) {
            FarmingAmulet.report(player, container, slot, held, patch)
        }
    }
}

FarmingAmulet.ids.forEach { id ->
    on_item_option(id, "Rub") { rubFarmingAmulet(id, false) }
    on_equipment_option(id, "Rub") { rubFarmingAmulet(id, true) }
}
