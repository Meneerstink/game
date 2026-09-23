package gg.rsmod.plugins.content.drops

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.player
import mu.KLogging
import java.security.SecureRandom
import java.util.*
import kotlin.collections.set

/**
 * The name of a table that always drops all of its items
 */
const val GUARANTEED_TABLE_NAME = "guaranteed"

/**
 * A utility class that assists with building dynamic drop tables.
 */
object DropTableFactory {
    /**
     * The drop tables for each npc.
     */
    private val tables =
        DropTableType.values().associateWith {
            HashMap<Int, DropTableBuilder.() -> Unit>()
        }

    /**
     * The PRNG for selecting an entry.
     */
    var prng = SecureRandom()

    /**
     * Registers a drop table.
     * @param init  The builder instance.
     */
    fun build(init: DropTableBuilder.() -> Unit): DropTableBuilder.() -> Unit {
        return init
    }

    /**
     * Registers a drop table for an npc.
     * @param table The drop table.
     * @param npcs  The list of npc ids.
     */
    fun register(
        table: DropTableBuilder.() -> Unit,
        vararg ids: Int,
        type: DropTableType = DropTableType.KILL,
    ) {
        ids.forEach { tables[type]!![it] = table }
    }

    /**
     * Whether a table is registered for [id], so data-sourced fallbacks can defer to hand-written ones.
     */
    fun hasTable(
        id: Int,
        type: DropTableType = DropTableType.KILL,
    ): Boolean = tables[type]!!.containsKey(id)

    /**
     * Gets a drop for a player killing an NPC.
     */
    fun getDrop(
        world: World,
        player: Player,
        npcId: Int,
        tile: Tile,
        type: DropTableType = DropTableType.KILL,
    ) {
        try {
            getDrop(player, npcId, type)?.forEach { createDrop(world, it, tile, player) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Creates a drop but only returns the values
     */
    fun getDrop(
        player: Player,
        tableId: Int,
        type: DropTableType = DropTableType.KILL,
    ): MutableList<Item>? {
        val items = mutableListOf<Item>()

        val bldr = tables[type]!![tableId] ?: return null
        try {
            val table = DropTableBuilder(player, prng).apply(bldr)

            val tables = table.tables.entries.map { it.value }

            val guaranteed = tables.firstOrNull { it.name == GUARANTEED_TABLE_NAME }
            val remaining = tables.filterNot { it.name == GUARANTEED_TABLE_NAME }
            if (guaranteed != null) {
                items.addAll(
                    guaranteed.entries
                        .map { it.drop }
                        .filterIsInstance<DropEntry.ItemDrop>()
                        .map { it.item },
                )
            }

            val remainingTables = remaining.map { DropEntry.TableDrop(it) }
            items.addAll(remainingTables.flatMap { it.getDrop() })
            return items
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Gets a drop from a table and adds to the players inventory
     */
    fun createDropInventory(
        player: Player,
        tableId: Int,
        type: DropTableType = DropTableType.KILL,
    ): MutableList<Item>? {
        return try {
            val drops = getDrop(player, tableId, type)
            drops?.forEach { item ->
                if (player.inventory.hasFreeSpace() ||
                    player.inventory.contains(item.id) &&
                    item.getDef(player.world.definitions).stackable
                ) {
                    player.inventory.add(item)
                    return@forEach
                }
                val groundItem = GroundItem(item.id, item.amount, player.tile, player)
                player.world.spawn(groundItem)
            }
            drops
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun hasInventorySpaceForAnyDrop(
        player: Player,
        tableId: Int,
        type: DropTableType,
    ): Boolean? {
        val bldr = tables[type]!![tableId] ?: return null
        return try {
            val table = DropTableBuilder(player, prng).apply(bldr)
            val tables = table.tables.entries.map { it.value }
            val count = tables.sumOf { requiredInventorySpacesToReceiveDrop(player, it) }
            count <= player.inventory.freeSlotCount
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun requiredInventorySpacesToReceiveDrop(
        player: Player,
        table: DropTable,
    ): Int {
        if (table.name == GUARANTEED_TABLE_NAME) {
            // For every drop in the guaranteed table, count the items that are not stackable, and the items
            // that are stackable but not yet in the inventory
            return table.entries
                .map { it.drop }
                .filterIsInstance<DropEntry.ItemDrop>()
                .map { it.item.id }
                .count(player.inventory::requiresFreeSlotToAdd)
        }

        // For the non-guaranteed tables, at most one item can be dropped. As soon as one item is found that
        // requires inventory space, 1 can be returned
        for (entry in table.entries) {
            val required =
                when (val drop = entry.drop) {
                    is DropEntry.NothingDrop -> 0
                    is DropEntry.MultiDrop -> {
                        drop.items
                            .map {
                                if (player.inventory.requiresFreeSlotToAdd(it.id)) {
                                    1
                                } else {
                                    0
                                }
                            }.sum()
                    }
                    is DropEntry.ItemRangeDrop -> if (player.inventory.requiresFreeSlotToAdd(drop.item.id)) 1 else 0
                    is DropEntry.ItemDrop -> if (player.inventory.requiresFreeSlotToAdd(drop.item.id)) 1 else 0
                    is DropEntry.TableDrop -> requiredInventorySpacesToReceiveDrop(player, drop.table)
                }

            if (required > 0) {
                return required
            }
        }

        return 0
    }

    /**
     * Gets a drop from a table. This operates recursively on
     * nested tables.
     */
    private fun DropEntry.TableDrop.getDrop(): List<Item> {
        val entries = table.entries
        if (entries.isEmpty()) {
            return emptyList()
        }
        // The roll denominator is the table size the script declared with `total(n)`. Slots the
        // script never filled are a chance of nothing - that is what `total(1024)` with a few rare
        // items has always meant, and the builder's own "N total slots, but M were used" check
        // proves it expected the two to agree. Until now the declared total was validated and then
        // thrown away and the roll used `entries.last().index` (the OCCUPIED slots), so an
        // under-filled table handed out its drops far more often than written: a table declaring
        // 1024 slots but filling 280 rolled one-in-280, roughly 3.7x too common, and never rolled
        // nothing at all. A table that over-fills its declared total still rolls against the
        // occupied count, so none of its entries becomes unreachable.
        val occupied = entries.last().index
        val idx = prng.nextInt(maxOf(table.totalSlots, occupied))
        // An index inside the declared-but-unfilled tail is the "nothing" remainder.
        val entry = entries.firstOrNull { it.index > idx } ?: return emptyList()

        return when (val drop = entry.drop) {
            is DropEntry.NothingDrop -> emptyList()
            is DropEntry.ItemDrop -> listOf(drop.item)
            is DropEntry.MultiDrop -> drop.getDrop()
            is DropEntry.TableDrop -> drop.getDrop()
            is DropEntry.ItemRangeDrop -> listOf(drop.getDrop())
        }
    }

    /**
     * Creates a dropped item.
     *
     * @param item   The item to drop.
     * @param player The player getting the loot (or null).
     * @param l      The location of the NPC dropping the loot.
     */
    private fun createDrop(
        world: World,
        item: Item,
        tile: Tile,
        owner: Pawn,
    ) {
        val def = world.definitions.get(ItemDef::class.java, item.id)
        if (def.stackable) {
            val ground = GroundItem(item.id, item.amount, tile, owner as Player)
            world.spawn(ground)
        }
        else {
            for (i in 0 until item.amount) {
                val ground = GroundItem(item.id, 1, tile)
                world.spawn(ground)
            }
        }
    }
}

/**
 * Marks a builder.
 */
@DslMarker
private annotation class BuilderDslMarker

/**
 * A DSL that assists in creating drop tables.
 * @param player    The player who killed the NPC.
 * @param npc       The NPC instance.
 * @param prng      The PRNG instance.
 */
@BuilderDslMarker
class DropTableBuilder(
    val player: Player,
    private val prng: SecureRandom,
) {
    /**
     * The tables that have been constructed.
     */
    val tables = hashMapOf<String, DropTable>()

    /**
     * Builds a table where items inside will *always* be dropped.
     */
    fun guaranteed(builder: TableBuilder.() -> Unit) = table(GUARANTEED_TABLE_NAME, builder)

    /**
     * A helper function for building a singular table.
     */
    fun main(builder: TableBuilder.() -> Unit) = table("main", builder)

    /**
     * Builds a table with a specified name.
     * @param name  The name of the table.
     */
    fun table(
        name: String,
        builder: TableBuilder.() -> Unit,
    ) {
        val bldr = TableBuilder(player, prng, name).apply(builder)
        val table = bldr.build()
        tables[name.lowercase()] = table
    }

    /**
     * Builds the drop tables.
     */
    internal fun build(): List<DropTable> {
        return tables.values.toList()
    }
}

/**
 * A DSL that assists in creating drop tables.
 * @param player    The player who killed the NPC.
 * @param npc       The NPC instance.
 * @param prng      The PRNG instance.
 * @param name      The name of the table.
 */
@BuilderDslMarker
class TableBuilder(
    val player: Player,
    val prng: SecureRandom,
    val name: String? = null,
) {
    /**
     * The total number of slots.
     */
    private var totalSlots = if (name == GUARANTEED_TABLE_NAME) 128 else 0

    /**
     * The number of occupied slots.
     */
    private var occupiedSlots = 0

    /**
     * The drop entries for this table.
     */
    private var entries = mutableListOf<Entry>()

    /**
     * Specifies the total number of slots for this drop table.
     * @param total The total number of slots.
     */
    fun total(total: Int) {
        totalSlots = total
    }

    /**
     * Adds an item to be dropped.
     * @param name      The name of the item.
     * @param quantity  The quantity of the item to drop.
     * @param slots     The number of slots this drop should occupy in the table.
     */
    fun obj(
        id: Int,
        quantity: Int = 1,
        slots: Int = 1,
    ) {
        val item = Item(id, quantity)

        occupiedSlots += slots
        entries.add(Entry(occupiedSlots, DropEntry.ItemDrop(item)))
    }

    fun obj(
        id: Int,
        quantityRange: IntRange,
        slots: Int = 1,
    ) {
        val item = Item(id, quantityRange.first)

        occupiedSlots += slots
        entries.add(Entry(occupiedSlots, DropEntry.ItemRangeDrop(item, quantityRange)))
    }

    fun objs(
        vararg item: Item,
        slots: Int = 1,
    ) {
        occupiedSlots += slots
        entries.add(Entry(occupiedSlots, DropEntry.MultiDrop(*item)))
    }

    /**
     * Adds a table to be dropped.
     * @param table The table.
     * @param slots The number of slots this table occupies.
     */
    fun table(
        table: DropTableBuilder.() -> Unit,
        slots: Int = 1,
    ) {
        val tab =
            DropTableBuilder(player, prng)
                .apply(table)
                .build()
                .first()

        occupiedSlots += slots
        entries.add(Entry(occupiedSlots, DropEntry.TableDrop(tab)))
    }

    /**
     * Adds a chance to drop nothing.
     * @param slots The number of slots.
     */
    fun nothing(slots: Int) {
        occupiedSlots += slots
        entries.add(Entry(occupiedSlots, DropEntry.NothingDrop))
    }

    /**
     * Builds this drop table.
     */
    internal fun build(): DropTable {
        if (occupiedSlots != totalSlots && name != GUARANTEED_TABLE_NAME) {
            // The declared total is NOT the roll denominator - `DropEntry.TableDrop.getDrop` rolls
            // against `entries.last().index`, i.e. the occupied slots - so a mismatch silently
            // changes every rate in this table. The old message named neither the table nor its
            // contents, which made the boot warning unactionable; the sampled item ids identify it.
            val sample =
                entries
                    .mapNotNull { entry ->
                        when (val drop = entry.drop) {
                            is DropEntry.ItemDrop -> drop.item.id
                            is DropEntry.ItemRangeDrop -> drop.item.id
                            is DropEntry.MultiDrop -> drop.items.firstOrNull()?.id
                            else -> null
                        }
                    }.take(5)
            if (occupiedSlots > totalSlots) {
                // The declared total is too small to hold what the table contains, so it cannot be
                // the denominator; the occupied count is used instead and the written rates are
                // diluted. Only the script can say which number is the right one.
                logger.warn(
                    "Drop table '${name ?: "unnamed"}' declares $totalSlots slots but fills $occupiedSlots; " +
                        "the declared total is too small and is ignored, so every rate in it is diluted. Item ids: $sample.",
                )
            } else {
                // Under-filled is legitimate and now behaves as written: the unfilled slots are the
                // table's chance of dropping nothing. Logged at debug only, as a sanity aid.
                logger.debug(
                    "Drop table '${name ?: "unnamed"}' fills $occupiedSlots of $totalSlots slots; " +
                        "the remaining ${totalSlots - occupiedSlots} are its chance of nothing. Item ids: $sample.",
                )
            }
        }

        return DropTable(name, entries.toTypedArray(), totalSlots)
    }

    data class Entry(
        val index: Int,
        val drop: DropEntry,
    )

    companion object : KLogging()
}
