package gg.rsmod.plugins.content.diaries

/**
 * DIARIES-OSRS Falador (27-11): the Falador shield prayer restore (OSRS Wiki "Falador shield": 25% / 50% / 100% once a day,
 * shield 4 twice). The option names come from the 667 cache, so every option with "pray" or "recharge" in it is bound.
 */
val shields = intArrayOf(Items.FALADOR_SHIELD_1, Items.FALADOR_SHIELD_2, Items.FALADOR_SHIELD_3, Items.FALADOR_SHIELD_4)
val isPrayerOption = { o: String -> o.contains("pray", ignoreCase = true) || o.contains("recharge", ignoreCase = true) }
shields.forEach { shield ->
    val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, shield)
    def.inventoryMenu.filterNotNull().filter(isPrayerOption).distinct().forEach { opt ->
        on_item_option(item = shield, option = opt) { player.message(DiaryItems.faladorPrayerRestore(player, shield)) }
    }
    def.equipmentMenu.filterNotNull().filter(isPrayerOption).distinct().forEach { opt ->
        on_equipment_option(shield, option = opt) { player.message(DiaryItems.faladorPrayerRestore(player, shield)) }
    }
}
