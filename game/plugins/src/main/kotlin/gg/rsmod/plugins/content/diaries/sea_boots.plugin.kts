package gg.rsmod.plugins.content.diaries

/** DIARIES-OSRS Fremennik (27-11): Fremennik sea boots "Operate" = Rellekka marketplace teleport (option names from the 667 cache). */
listOf(Items.FREMENNIK_SEA_BOOTS_1, Items.FREMENNIK_SEA_BOOTS_2, Items.FREMENNIK_SEA_BOOTS_3, Items.FREMENNIK_SEA_BOOTS_4).forEach { boots ->
    val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, boots)
    def.inventoryMenu.filterNotNull().distinct().filter { it.equals("operate", true) || it.contains("teleport", true) }.forEach { opt ->
        on_item_option(item = boots, option = opt) { DiaryItems.seaBootsTeleport(player, boots) }
    }
    def.equipmentMenu.filterNotNull().distinct().filter { it.equals("operate", true) || it.contains("teleport", true) || it.contains("rellekka", true) }.forEach { opt ->
        on_equipment_option(boots, option = opt) { DiaryItems.seaBootsTeleport(player, boots) }
    }
}
