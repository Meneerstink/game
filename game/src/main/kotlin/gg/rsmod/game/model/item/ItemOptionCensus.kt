package gg.rsmod.game.model.item

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import java.io.File

/**
 * Item counterpart of [gg.rsmod.game.model.npc.NpcCensus] (2026-09-22): every unnoted item definition whose cache
 * inventory or worn-equipment menu advertises an option that no plugin handles. The client shows those options, the
 * server answers "Nothing interesting happens." (root-cause class 1). Options the core handles for every item
 * (Drop, Destroy, Examine, Wield/Wear/Equip/Remove) are not listed.
 *
 * Written next to `npc_inventory.csv` at boot, so the Bandit camp teleport kind of gap (a whole scroll family with no
 * handler) shows up as a list instead of one owner report at a time.
 */
object ItemOptionCensus {
    private val GENERIC = setOf("drop", "destroy", "examine", "wield", "wear", "equip", "remove", "release", "use")

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
                if (!opt.isNullOrBlank() && opt.lowercase() !in GENERIC && !world.plugins.hasItemOption(id, i + 1)) {
                    lines += "$id,\"${def.name}\",inventory,\"$opt\""
                }
            }
            def.equipmentMenu.forEachIndexed { i, opt ->
                if (!opt.isNullOrBlank() && opt.lowercase() !in GENERIC && !world.plugins.hasEquipmentOption(id, i + 1)) {
                    lines += "$id,\"${def.name}\",worn,\"$opt\""
                }
            }
            if (lines.size > before) items++
        }
        File(path).writeText(lines.joinToString("\n"))
        return "item_option_census.csv written: $items item types with at least one unbound option (${lines.size - 1} options)."
    }
}
