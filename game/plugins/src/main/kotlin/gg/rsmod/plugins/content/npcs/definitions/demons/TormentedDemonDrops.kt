package gg.rsmod.plugins.content.npcs.definitions.demons

import gg.rsmod.plugins.content.drops.DropTableBuilder
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.VoidDropTables

/**
 * RCV-012 B7, owner decision 6 (2026-09-13 "yes"): tormented demons drop Void's guthix_temple tables
 * (`data/cfg/npcs/tormented-demon-drops.json`), for every cache demon id 8349-8369. The 2011 filter is the revision-667
 * cache itself: every item is checked to exist there (TormentedDemonDropTablesTests). Only the hard and elite clue scroll
 * rows are excluded (Treasure Trails parked), listed in the JSON `excluded` block. Replaces the invented table.
 */
object TormentedDemonDrops {
    const val PATH = "./data/cfg/npcs/tormented-demon-drops.json"

    val NPC_IDS = (8349..8369).toList().toIntArray()

    fun tables(doc: VoidDropTables.Document): Map<Int, DropTableBuilder.() -> Unit> =
        NPC_IDS.associateWith { id ->
            val root = doc.npcs[id.toString()] ?: error("tormented-demon-drops.json has no table for npc $id")
            VoidDropTables.builder(doc, root)
        }

    fun register(doc: VoidDropTables.Document) {
        tables(doc).forEach { (id, table) -> DropTableFactory.register(table, id) }
    }
}
