package gg.rsmod.plugins.content.drops

import gg.rsmod.game.model.item.Item

/**
 * Represents a possible drop entry.
 */
sealed class DropEntry {
    /**
     * A drop that will contain nothing.
     */
    object NothingDrop : DropEntry()

    /**
     * A drop that will contain an item.
     */
    class ItemDrop(
        val item: Item,
    ) : DropEntry()

    /**
     * A drop that will recurse into a table.
     */
    class TableDrop(
        val table: DropTable,
        /** The name the Drop Viewer shows for this nested table ("Rare drop table"); null = its lines show inline. */
        val label: String? = null,
        /**
         * N614 (15-11): how often the nested table is rolled when this line is hit. OSRS Wiki Nechryael: 'When the rare seed table
         * is rolled, it is rolled twice'. Default 1 = every existing table unchanged.
         */
        val rolls: Int = 1,
    ) : DropEntry()

    /**
     * A drop that contains multiple items.
     */
    class MultiDrop(
        vararg val items: Item,
    ) : DropEntry() {
        fun getDrop(): List<Item> = items.toList()
    }

    /**
     * A drop that will contain an item. The quantity is within a range
     */
    class ItemRangeDrop(
        val item: Item,
        val quantityRange: IntRange,
        /** Only every [step]-th amount from the first can drop (e.g. revenant ether: even amounts only). */
        val step: Int = 1,
    ) : DropEntry() {
        fun getDrop() = Item(item, quantityRange.first + step * (0..(quantityRange.last - quantityRange.first) / step).random())
    }
}
