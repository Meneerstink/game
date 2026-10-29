package gg.rsmod.game.model.item

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World

/**
 * Server-side audit of every cached inventory and worn-item menu option.
 *
 * The cache is the source of advertised options; the plugin registries are the
 * source of executable handlers. Engine-owned options are listed separately so
 * the CSV distinguishes a deliberate engine route from a dead menu entry.
 */
object ItemCensus {
    private val engineEquipmentOptions = setOf("remove", "examine")

    /**
     * N11: the routes the inventory really takes (IfButton1Handler / OpHeld*): Wear/Wield/Equip equip only on op 2 (EquipAction),
     * Drop only on op 5, Use and Examine anywhere; a fifth-slot "Destroy" runs the item's option-10 plugin (destroy_item). A Wear on
     * another slot is a dead option - the Ring of shadows could not be worn (N422).
     */
    fun engineRoute(
        slot: Int,
        option: String,
    ): Boolean =
        when (option.lowercase()) {
            "wear", "wield", "equip" -> slot == 2
            "drop" -> slot == 5
            "use", "examine" -> true
            else -> false
        }

    /** The plugin option index a click on inventory [slot] runs: a fifth-slot Destroy is option 10. */
    fun pluginOption(
        slot: Int,
        option: String,
    ): Int = if (slot == 5 && option.equals("destroy", ignoreCase = true)) 10 else slot

    data class Row(
        val id: Int,
        val name: String,
        val noted: Boolean,
        val inventoryOptions: List<String>,
        val boundInventoryOptions: List<Int>,
        val engineInventoryOptions: List<Int>,
        val equipmentOptions: List<String>,
        val boundEquipmentOptions: List<Int>,
        val engineEquipmentOptions: List<Int>,
    )

    fun collect(world: World): List<Row> =
        world.definitions.getAllKeys(ItemDef::class.java).sorted().map { id ->
            val def = world.definitions.get(ItemDef::class.java, id)
            val inventory = def.inventoryMenu.mapIndexedNotNull { index, option ->
                option?.takeIf { it.isNotBlank() }?.let { index + 1 to it }
            }
            val equipment = def.equipmentMenu.mapIndexedNotNull { index, option ->
                option?.takeIf { it.isNotBlank() }?.let { index + 1 to it }
            }
            Row(
                id = id,
                name = def.name,
                noted = def.noted,
                inventoryOptions = def.inventoryMenu.map { it.orEmpty() },
                boundInventoryOptions = inventory.mapNotNull { (slot, option) ->
                    slot.takeIf { world.plugins.hasItemOption(id, pluginOption(it, option)) }
                },
                engineInventoryOptions = inventory.mapNotNull { (slot, option) ->
                    slot.takeIf { engineRoute(it, option) }
                },
                equipmentOptions = def.equipmentMenu.map { it.orEmpty() },
                boundEquipmentOptions = equipment.mapNotNull { (slot, _) ->
                    slot.takeIf { world.plugins.hasEquipmentOption(id, it) }
                },
                engineEquipmentOptions = equipment.mapNotNull { (slot, option) ->
                    slot.takeIf { option.lowercase() in engineEquipmentOptions }
                },
            )
        }

    fun writeCsv(
        world: World,
        path: String = "./item_inventory.csv",
    ): String {
        val rows = collect(world)
        val lines = mutableListOf(
            "id,name,noted,inventory_options,bound_inventory_options,engine_inventory_options," +
                "equipment_options,bound_equipment_options,engine_equipment_options",
        )
        rows.forEach { row ->
            lines += listOf(
                row.id.toString(),
                csv(row.name),
                row.noted.toString(),
                csv(row.inventoryOptions.joinToString("|")),
                csv(row.boundInventoryOptions.joinToString("|")),
                csv(row.engineInventoryOptions.joinToString("|")),
                csv(row.equipmentOptions.joinToString("|")),
                csv(row.boundEquipmentOptions.joinToString("|")),
                csv(row.engineEquipmentOptions.joinToString("|")),
            ).joinToString(",")
        }
        gg.rsmod.util.io.SharedDataFiles.of(path).writeText(lines.joinToString("\n"))

        val inventoryGaps = rows.count { row ->
            row.inventoryOptions.indices.any { index ->
                val slot = index + 1
                row.inventoryOptions[index].isNotBlank() &&
                    slot !in row.boundInventoryOptions && slot !in row.engineInventoryOptions
            }
        }
        val equipmentGaps = rows.count { row ->
            row.equipmentOptions.indices.any { index ->
                val slot = index + 1
                row.equipmentOptions[index].isNotBlank() &&
                    slot !in row.boundEquipmentOptions && slot !in row.engineEquipmentOptions
            }
        }
        return "item_inventory.csv written: ${rows.size} definitions, " +
            "$inventoryGaps with an unbound inventory option, $equipmentGaps with an unbound worn option."
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
