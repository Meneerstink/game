package gg.rsmod.game.model.item

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World

/**
 * Item counterpart of [gg.rsmod.game.model.npc.NpcCensus] (2026-09-22): every unnoted item definition whose cache
 * inventory or worn-equipment menu advertises an option that no plugin handles. The client shows those options, the
 * server answers "Nothing interesting happens." (root-cause class 1). Options the core handles for every item are not listed:
 * the inventory ones only on the slot the core routes them from ([ItemCensus.engineRoute]: Wear on op 2, Drop on op 5 - N11/N422),
 * a fifth-slot Destroy when its option-10 plugin exists, and worn Remove/Examine.
 *
 * Written next to `npc_inventory.csv` at boot, so the Bandit camp teleport kind of gap (a whole scroll family with no
 * handler) shows up as a list instead of one owner report at a time.
 */
object ItemOptionCensus {
    private val WORN_GENERIC = setOf("remove", "examine")

    fun writeCsv(
        world: World,
        path: String = "./item_option_census.csv",
    ): String {
        val lines = mutableListOf("id,name,menu,option")
        var items = 0
        world.definitions.getAllKeys(ItemDef::class.java).sorted().forEach { id ->
            val def = world.definitions.getNullable(ItemDef::class.java, id) ?: return@forEach
            if (def.noted || def.name.isBlank() || def.name == "null") return@forEach
            val before = lines.size
            def.inventoryMenu.forEachIndexed { i, opt ->
                if (!opt.isNullOrBlank() && !ItemCensus.engineRoute(i + 1, opt) && !world.plugins.hasItemOption(id, ItemCensus.pluginOption(i + 1, opt))) {
                    lines += "$id,\"${def.name}\",inventory,\"$opt\""
                }
            }
            def.equipmentMenu.forEachIndexed { i, opt ->
                if (!opt.isNullOrBlank() && opt.lowercase() !in WORN_GENERIC && !world.plugins.hasEquipmentOption(id, i + 1)) {
                    lines += "$id,\"${def.name}\",worn,\"$opt\""
                }
            }
            if (lines.size > before) items++
        }
        gg.rsmod.util.io.SharedDataFiles.of(path).writeText(lines.joinToString("\n"))
        return "item_option_census.csv written: $items item types with at least one unbound option (${lines.size - 1} options)."
    }
}
