package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The Grand Exchange home's scenery lives in one data file, `data/cfg/home_decor.txt`, so the home can be designed while
 * the server runs: `home_decor_live.plugin.kts` re-reads the file whenever it changes and swaps the old dressing for the
 * new one in front of every player. Lines (blank lines and `#` comments are ignored):
 *
 *  * `obj <id> <x> <z> [plane] [type] [rot]` - spawn an object (type 10 and rot 0 by default).
 *  * `rect <id> <x1> <z1> <x2> <z2> [plane] [type] [rot] [step]` - the same object on every [step]-th tile of a rectangle.
 *  * `del <x> <z> [plane] [type]` - hide the cache object on that tile (every type when omitted).
 *  * `npc <id> <x> <z> [n|e|s|w]` - move the npc with that id to the tile (spawned when none exists), facing that way.
 */
object HomeDecorFile {
    val PATH: Path = Paths.get("data", "cfg", "home_decor.txt")

    data class Placement(val id: Int, val tile: Tile, val type: Int = 10, val rot: Int = 0)

    data class Removal(val tile: Tile, val type: Int? = null)

    /** An npc that must stand on [tile] facing [facing]: the existing one with this id is moved, or one is spawned. */
    data class NpcPost(val id: Int, val tile: Tile, val facing: Direction = Direction.SOUTH)

    data class Design(val placements: List<Placement>, val removals: List<Removal>, val npcs: List<NpcPost> = emptyList())

    fun load(path: Path = PATH): Design = if (Files.exists(path)) parse(Files.readAllLines(path)) else Design(emptyList(), emptyList())

    private val FACINGS = mapOf("n" to Direction.NORTH, "e" to Direction.EAST, "s" to Direction.SOUTH, "w" to Direction.WEST)

    fun parse(lines: List<String>): Design {
        val placements = ArrayList<Placement>()
        val removals = ArrayList<Removal>()
        val npcs = ArrayList<NpcPost>()
        lines.forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val parts = line.split(Regex("\\s+"))
            if (parts[0] == "npc") {
                require(parts.size in 4..5) { "home_decor.txt line ${index + 1}: npc <id> <x> <z> [n|e|s|w]" }
                val ints = parts.subList(1, 4).map { it.toIntOrNull() ?: error("home_decor.txt line ${index + 1}: '$it' is not a number") }
                val facing = parts.getOrNull(4)?.let { FACINGS[it.lowercase()] ?: error("home_decor.txt line ${index + 1}: facing must be n, e, s or w") }
                npcs += NpcPost(ints[0], Tile(ints[1], ints[2], 0), facing ?: Direction.SOUTH)
                return@forEachIndexed
            }
            val n = parts.drop(1).map { it.toIntOrNull() ?: error("home_decor.txt line ${index + 1}: '$it' is not a number") }
            fun arg(i: Int, default: Int) = n.getOrNull(i) ?: default
            when (parts[0]) {
                "obj" -> {
                    require(n.size >= 3) { "home_decor.txt line ${index + 1}: obj <id> <x> <z> [plane] [type] [rot]" }
                    placements += Placement(n[0], Tile(n[1], n[2], arg(3, 0)), arg(4, 10), arg(5, 0))
                }
                "rect" -> {
                    require(n.size >= 5) { "home_decor.txt line ${index + 1}: rect <id> <x1> <z1> <x2> <z2> [plane] [type] [rot] [step]" }
                    val step = arg(8, 1).coerceAtLeast(1)
                    for (x in minOf(n[1], n[3])..maxOf(n[1], n[3]) step step) {
                        for (z in minOf(n[2], n[4])..maxOf(n[2], n[4]) step step) {
                            placements += Placement(n[0], Tile(x, z, arg(5, 0)), arg(6, 10), arg(7, 0))
                        }
                    }
                }
                "del" -> {
                    require(n.size >= 2) { "home_decor.txt line ${index + 1}: del <x> <z> [plane] [type]" }
                    removals += Removal(Tile(n[0], n[1], arg(2, 0)), n.getOrNull(3))
                }
                else -> error("home_decor.txt line ${index + 1}: unknown command '${parts[0]}'")
            }
        }
        return Design(placements, removals, npcs)
    }
}
