package gg.rsmod.plugins.content.items.jewellery

RingOfDueling.ITEMS.forEach { ring ->
    on_item_option(item = ring, option = "rub") {
        val used = player.getInteractingItem()
        val slot = player.getInteractingItemSlot()
        player.queue {
            val choice = options("Al Kharid Duel Arena.", "Castle Wars Arena.", "Ferox Enclave.", "Nowhere.")
            val destination = RingOfDueling.DESTINATIONS.values.toList().getOrNull(choice - 1) ?: return@queue
            RingOfDueling.use(player, destination, used, slot, equipped = false)
        }
    }
    // Claude imports the worn menu; Rub works now without binding a nonexistent cache option.
    val definition = world.definitions.get(ItemDef::class.java, ring)
    RingOfDueling.DESTINATIONS.forEach { (option, destination) ->
        if (definition.equipmentMenu.any { it.equals(option, true) }) {
            on_equipment_option(item = ring, option = option) {
                val slot = EquipmentType.RING.id
                val used = player.equipment[slot] ?: return@on_equipment_option
                RingOfDueling.use(player, destination, used, slot, equipped = true)
            }
        }
    }
    if (definition.equipmentMenu.any { it.equals("Rub", true) }) {
        on_equipment_option(item = ring, option = "rub") {
            val slot = EquipmentType.RING.id
            val used = player.equipment[slot] ?: return@on_equipment_option
            player.queue {
                val choice = options("Al Kharid Duel Arena.", "Castle Wars Arena.", "Ferox Enclave.", "Nowhere.")
                val destination = RingOfDueling.DESTINATIONS.values.toList().getOrNull(choice - 1) ?: return@queue
                RingOfDueling.use(player, destination, used, slot, equipped = true)
            }
        }
    }
}
