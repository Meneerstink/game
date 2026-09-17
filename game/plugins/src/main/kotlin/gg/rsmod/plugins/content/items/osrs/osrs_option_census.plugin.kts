package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef

/*
 * Unhandled-route census for the imported OSRS items (owner 2026-09-17c: "every option it has needs to be checked"). After every
 * plugin is bound, each imported item's inventory and worn options are checked for a handler; the ones without are printed once at
 * boot as `OSRS_OPTION_CENSUS` lines, so an option that silently does nothing is visible in the server log instead of in a live test.
 * Options the engine itself serves need no plugin: Wear / Wield / Equip (EquipAction), Drop, Destroy's confirmation is a plugin.
 */
val FIRST_IMPORTED_ITEM = 22328
val ENGINE_INVENTORY_OPTIONS = setOf("wear", "wield", "equip", "drop", "use", "examine")
val ENGINE_WORN_OPTIONS = setOf("remove")

on_world_init_late {
    val count = world.definitions.getCount(ItemDef::class.java)
    val unhandled = sortedMapOf<String, MutableList<String>>()
    for (id in FIRST_IMPORTED_ITEM until count) {
        val def = world.definitions.getNullable(ItemDef::class.java, id) ?: continue
        if (def.noted || def.name.isBlank() || def.name == "null") continue
        def.inventoryMenu.forEachIndexed { index, option ->
            val text = option?.lowercase()?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            if (text in ENGINE_INVENTORY_OPTIONS) return@forEachIndexed
            // "Destroy" is bound for every item by inter/destroy_item.plugin.kts (its own late pass; order between late passes is not fixed).
            if (text == "destroy") return@forEachIndexed
            if (!world.plugins.hasItemOption(id, index + 1)) unhandled.getOrPut("inventory \"$option\"") { mutableListOf() } += "$id ${def.name}"
        }
        def.equipmentMenu.forEachIndexed { index, option ->
            val text = option?.lowercase()?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            if (text in ENGINE_WORN_OPTIONS) return@forEachIndexed
            if (!world.plugins.hasEquipmentOption(id, index + 1)) unhandled.getOrPut("worn \"$option\"") { mutableListOf() } += "$id ${def.name}"
        }
    }
    println("OSRS_OPTION_CENSUS unhandled option kinds=${unhandled.size} items=${unhandled.values.sumOf { it.size }}")
    unhandled.forEach { (option, items) -> println("OSRS_OPTION_CENSUS $option x${items.size}: ${items.joinToString("; ")}") }
}
