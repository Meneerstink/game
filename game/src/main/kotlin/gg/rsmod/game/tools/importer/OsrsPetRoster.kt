package gg.rsmod.game.tools.importer

import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * The 71 OSRS pets (owner 2026-09-30: "import all osrs pets from osrs and make them fully functioning just like summoning").
 *
 * Source: OSRS Wiki "Pet" (the Boss / Skilling / Other (Collection log) tables, "There are currently 71 available pets"), and per pet
 * the `id` fields of its Infobox NPC (follower + menagerie npc) and Infobox Item, one row per version (fetched 2026-09-30). The
 * "Other (Generic)" followers (cats, dogs, pet rock, quest followers) are not pets in that count and are not in the roster.
 * Which of the two npc ids is the follower is decided by the cache: OSRS npc opcode 122 (follower flag) - never by position.
 */
object OsrsPetRoster {
    val SOURCE = RspsPaths.data("cfg/pets/osrs_pets.tsv")

    /** Server table written by [OsrsPetImportTool] after a successful apply: local item and follower npc ids per variant. */
    val SERVER_TABLE = RspsPaths.data("cfg/pets/pets.tsv")

    data class Row(
        val pet: String,
        val variant: String,
        val osrsItem: Int,
        val osrsNpcs: List<Int>,
        /** The one of [osrsNpcs] with the OSRS follower flag (npc opcode 122), written by `OsrsPetImportTool probe`. */
        val osrsFollower: Int,
        /** Movement sequences of the follower: classic frames import as they are, skeletal (animaya) ones go through [SkeletalBakeTool]. */
        val skeletal: Boolean,
    )

    fun load(file: File = File(SOURCE)): List<Row> =
        file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val c = line.split('\t')
                require(c.size == 6 && c[5] in setOf("classic", "skeletal")) { "bad roster line '$line'" }
                Row(c[0], c[1], c[2].toInt(), c[3].split(',').map { it.trim().toInt() }, c[4].toInt(), c[5] == "skeletal")
            }
}
