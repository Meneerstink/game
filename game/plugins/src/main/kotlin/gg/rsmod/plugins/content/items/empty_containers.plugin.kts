package gg.rsmod.plugins.content.items

import gg.rsmod.game.fs.def.ItemDef
import java.io.File

/*
 * "Empty" on every container item the 667 cache offers it on (unfinished potions, weapon poison, jugs, bowls, buckets, cups, pots...):
 * one shared handler over one table, `data/cfg/item_empty.tsv` (item -> emptied item, the 667 Void donor's per-item `empty` keys).
 * The census (item_option_census.csv) listed 358 unbound "Empty" options; this covers every one with a sourced target. Messages are
 * Void's (content/skill/cooking/Empty.kt). Bound late, and only where no plugin already handles the option, so a special Empty (rune
 * pouch, ectophial, ...) always wins.
 */
val EMPTY_TABLE_PATH = "./data/cfg/item_empty.tsv"

val EMPTIED: Map<Int, Int> =
    File(EMPTY_TABLE_PATH).takeIf { it.exists() }?.readLines().orEmpty()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .associate { line -> line.split('\t').let { it[0].toInt() to it[1].toInt() } }

on_world_init_late {
    EMPTIED.forEach { (full, empty) ->
        val def = world.definitions.getNullable(ItemDef::class.java, full) ?: return@forEach
        val index = def.inventoryMenu.indexOfFirst { it.equals("Empty", ignoreCase = true) }
        if (index < 0 || world.plugins.hasItemOption(full, index + 1)) return@forEach
        on_item_option(item = full, option = "Empty") {
            val slot = player.getInteractingSlot()
            if (player.inventory[slot]?.id != full) return@on_item_option
            player.inventory.remove(full, 1, beginSlot = slot)
            player.inventory.add(empty, 1, beginSlot = slot)
            val name = def.name
            if (name.startsWith("Bucket of", ignoreCase = true)) {
                player.message("You empty the contents of the bucket on the floor.")
            } else {
                player.filterableMessage("You empty the ${name.substringBefore(" (").lowercase()}.")
            }
        }
    }
}
