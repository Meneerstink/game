package gg.rsmod.plugins.content.items.food

import java.io.File

data class Consumable(
    val id: Int,
    val drink: Boolean,
    val healMin: Int,
    val healMax: Int,
    val leftover: Int,
    val ticks: Int,
    val combo: Boolean,
    val message: String,
)

/** Shared production parser, also used by cache-free route tests. Healing uses the legacy x10 ledger. */
object ConsumableTable {
    /** "You drink the dwarven stout." - without the serving count a keg's name carries ("Dwarven stout (4)", "(m3)"). */
    fun defaultMessage(drink: Boolean, name: String): String =
        "You ${if (drink) "drink" else "eat"} the ${name.replace(Regex("""\s*\(m?\d\)$"""), "").lowercase()}."

    fun read(file: File): List<Consumable> = file.takeIf { it.exists() }?.readLines().orEmpty()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val c = line.split('\t')
            val drink = c[1] == "drink"
            Consumable(
                id = c[0].toInt(), drink = drink,
                healMin = c[2].toInt(), healMax = c[3].toInt(), leftover = c[4].toInt(),
                ticks = c[5].toIntOrNull() ?: if (c[6] == "1") 1 else if (drink) 2 else 3,
                combo = c[6] == "1", message = c[7],
            )
        }
}
