package gg.rsmod.plugins.content.diaries

/** DIARIES-OSRS Karamja (27-11): the Karamja gloves 3/4 teleport options, bound by their cache names (like the Falador shield). */
val plain = setOf("wear", "remove", "drop", "examine", "use", "destroy", "check")
listOf(Items.KARAMJA_GLOVES_3, Items.KARAMJA_GLOVES_4).forEach { gloves ->
    val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, gloves)
    def.inventoryMenu.filterNotNull().distinct().filter { it.lowercase() !in plain && it.isNotBlank() }.forEach { opt ->
        on_item_option(item = gloves, option = opt) { DiaryItems.karamjaGlovesTeleport(player, gloves, opt) }
    }
    def.equipmentMenu.filterNotNull().distinct().filter { it.lowercase() !in plain && it.isNotBlank() }.forEach { opt ->
        on_equipment_option(gloves, option = opt) { DiaryItems.karamjaGlovesTeleport(player, gloves, opt) }
    }
}
