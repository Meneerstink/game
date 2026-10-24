package gg.rsmod.plugins.content.drops

import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Herbs
import gg.rsmod.plugins.content.drops.global.MegaRare
import gg.rsmod.plugins.content.drops.global.Rare
import gg.rsmod.plugins.content.drops.global.Seeds

/**
 * The names the Drop Viewer (N108) shows for the shared nested tables: a monster's "Rare drop table" line folds open to the
 * table's own lines (owner design 2026-10-24, GOEDGEKEURD 03). A nested table without a name here shows its lines inline,
 * as before. Matched by identity: every script nests the same global table object.
 */
object DropTableNames {
    private val BY_TABLE: Map<DropTableBuilder.() -> Unit, String> by lazy {
        mapOf(
            Rare.rareTable to "Rare drop table",
            Gems.gemTable to "Gem drop table",
            MegaRare.megaRareTable to "Mega-rare drop table",
            Herbs.minorHerbTable to "Herb drop table",
            Herbs.minorMultipleHerbTable to "Herb drop table",
            Herbs.usefulHerbTable to "Herb drop table",
            Seeds.allotmentSeedTable to "Seed drop table",
            Seeds.uncommonSeedTable to "Seed drop table",
        )
    }

    fun of(table: DropTableBuilder.() -> Unit): String? = runCatching { BY_TABLE[table] }.getOrNull()

    /** Void (GodWars / Tormented demon JSON) references: only the shared rare and gem tables get a fold-open line. */
    fun ofVoidRef(ref: String): String? =
        when (ref.lowercase()) {
            "rare_drop_table", "rare_drop_table_main" -> "Rare drop table"
            "gem_drop_table", "gem_drop_table_main" -> "Gem drop table"
            else -> null
        }
}
