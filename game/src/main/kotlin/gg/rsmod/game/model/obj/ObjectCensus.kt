package gg.rsmod.game.model.obj

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.World
import java.io.File

/**
 * Server-side audit of cached object definitions and registered interaction routes.
 * It reports advertised options separately from bound handlers so dead menu entries
 * and transformed-object gaps can be fixed from evidence instead of guessed IDs.
 */
object ObjectCensus {
    data class Row(
        val id: Int,
        val name: String,
        val interactive: Boolean,
        val solid: Boolean,
        val width: Int,
        val length: Int,
        val options: List<String>,
        val boundOptions: List<Int>,
        val hasItemOnObjectHandler: Boolean,
        val transforms: List<Int>,
    )

    fun collect(world: World): List<Row> =
        world.definitions.getAllKeys(ObjectDef::class.java).sorted().map { id ->
            val def = world.definitions.get(ObjectDef::class.java, id)
            Row(
                id = id,
                name = def.name,
                interactive = def.interactive,
                solid = def.solid,
                width = def.width,
                length = def.length,
                options = def.options.map { it.orEmpty() },
                boundOptions = world.plugins.boundObjectOptions(id).sorted(),
                hasItemOnObjectHandler = world.plugins.hasItemOnObjectHandler(id),
                transforms = def.transforms?.toList() ?: emptyList(),
            )
        }

    fun writeCsv(world: World, path: String = "./object_inventory.csv"): String {
        val rows = collect(world)
        val lines = mutableListOf(
            "id,name,interactive,solid,width,length,options,bound_options,has_item_on_object_handler,transforms",
        )
        rows.forEach { row ->
            lines += listOf(
                row.id.toString(),
                csv(row.name),
                row.interactive.toString(),
                row.solid.toString(),
                row.width.toString(),
                row.length.toString(),
                csv(row.options.joinToString("|")),
                csv(row.boundOptions.joinToString("|")),
                row.hasItemOnObjectHandler.toString(),
                csv(row.transforms.joinToString("|")),
            ).joinToString(",")
        }
        File(path).writeText(lines.joinToString("\n"))

        val interactive = rows.count { it.interactive }
        val deadOptions = rows.count { row ->
            row.options.withIndex().any { it.value.isNotBlank() && (it.index + 1) !in row.boundOptions }
        }
        val transformed = rows.count { it.transforms.isNotEmpty() }
        return "object_inventory.csv written: ${rows.size} definitions, $interactive interactive, " +
            "$deadOptions with at least one unbound advertised option, $transformed transformed"
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}