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

    /** Every registered table id of [type] (the debug toolkit's `sweep drops` rolls each one). */
    fun tableIds(type: DropTableType = DropTableType.KILL): Set<Int> = tables[type]!!.keys.toSet()

    /**
     * Whether a table is registered for [id], so data-sourced fallbacks can defer to hand-written ones.
     */
    fun hasTable(
        id: Int,
        type: DropTableType = DropTableType.KILL,
    ): Boolean = tables[type]!!.containsKey(id)

    /** Wiki Drop table: wilderness uniques are pre-rolled; a successful roll replaces the main loot, not tertiaries. */
    fun mainReplacementTable(npcId: Int, tables: List<DropTable>): DropTable? =
        tables.firstOrNull { it.name == "pre_roll" } ?: tables.firstOrNull {
            it.name == "unique" && npcId in intArrayOf(
                gg.rsmod.plugins.api.cfg.Npcs.CALLISTO_OLD, gg.rsmod.plugins.api.cfg.Npcs.VETION_OLD,
                gg.rsmod.plugins.api.cfg.Npcs.VENENATIS_OSRS, gg.rsmod.plugins.api.cfg.Npcs.SPINDEL_OSRS, gg.rsmod.plugins.api.cfg.Npcs.ARTIO, gg.rsmod.plugins.api.cfg.Npcs.CALVARION,
            )
        }

    fun mainTableWeight(npcId: Int, tables: List<DropTable>, rate: Double): Double =
        mainReplacementTable(npcId, tables)?.let { 1.0 - lineChances(it, rate).sum() } ?: 1.0

    /** One shared pre-roll for a group boss; personal rolls must not roll this table again. */
    fun rollMainReplacement(player: Player, npcId: Int, rate: Double): List<Item> =
        tables(player, npcId)?.let { mainReplacementTable(npcId, it) }?.let { DropEntry.TableDrop(it).getDrop(rate) }.orEmpty()

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
            val items = getDrop(player, npcId, type, killRate(player, type)) ?: run {
                gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) { "drop $type table $npcId for ${player.username}: no table" }
                return
            }
            // Debug toolkit: every roll with what it gave; an item id the cache does not have is a PROBLEM (invisible drop).
            gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) {
                "drop $type table $npcId for ${player.username} at ${tile.x},${tile.z}: ${items.joinToString(",") { "${it.id}x${it.amount}" }.ifEmpty { "nothing" }}"
            }
            items.filter { runCatching { world.definitions.getNullable(gg.rsmod.game.fs.def.ItemDef::class.java, it.id) }.getOrNull() == null }.forEach {
                gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.ITEM) { "drop table $npcId gave item ${it.id} which does not exist in the cache" }
            }
            giveDrop(world, player, npcId, tile, items, type)
        } catch (e: Exception) {
            gg.rsmod.game.model.Trace.crash("drop table", e)
        }
    }

    /** Hand out an already rolled reward, retaining ground ownership, Loot Tracker and Collection Log. */
    fun giveDrop(
        world: World,
        player: Player,
        npcId: Int,
        tile: Tile,
        items: List<Item>,
        type: DropTableType = DropTableType.KILL,
        allowLootShare: Boolean = true,
    ) {
        // Boss contribution rewards already have an owner; do not distribute them a second time.
        val sharers = if (type == DropTableType.KILL && allowLootShare) gg.rsmod.plugins.content.inter.friends.LootShare.sharers(player, tile) else null
        // RuneLite Loot Tracker: what each owner really received from this kill, one event per owner.
        val received = LinkedHashMap<Player, MutableList<Item>>()
        items.forEach { item ->
            if (sharers == null) {
                createDrop(world, item, tile, player)
                received.getOrPut(player) { mutableListOf() } += item
            } else {
                gg.rsmod.plugins.content.inter.friends.LootShare.share(world, player, sharers, item, tile) { shared, owner ->
                    createDrop(world, shared, tile, owner)
                    received.getOrPut(owner) { mutableListOf() } += shared
                }
            }
        }
        if (type == DropTableType.KILL) {
            if (received.isEmpty()) received[player] = mutableListOf()
            received.forEach { (owner, loot) -> gg.rsmod.plugins.content.mechanics.hud.LootTracker.npcKill(owner, npcId, loot) }
            // N104: the Collection Log, with the kill count of the Drop Viewer entry this npc belongs to.
            received.forEach { (owner, loot) ->
                runCatching {
                    val source = gg.rsmod.plugins.content.quests.tab.DropSources.forNpc(owner, npcId)?.key
                    gg.rsmod.plugins.content.quests.tab.CollectionLog.record(owner, source, loot.map { it.id to it.amount })
                }.onFailure { gg.rsmod.game.model.Trace.crash("collection log", it) }
            }
        }
    }

    /**
     * N100 (owner 2026-10-17): the chance factor of a monster-kill roll for [player] - the Annihilation tier's drop rate
     * (3x / 3x / 4x / 5x per combat bracket). Every other roll (boxes, chests, caskets, pickpocketing, stalls and the
     * bird-nest route, which calls [getDrop] without a rate) stays 1x (VRAGEN 34 b, team default A).
     */
    fun killRate(
        player: Player,
        type: DropTableType = DropTableType.KILL,
    ): Double = if (type == DropTableType.KILL) gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier.dropRate(player) else 1.0

    /**
     * The chance of every line of [table] for one roll at [rate] (N100, owner 2026-10-18 VRAGEN 34 a = A, "zeldzaam eerst"): the
     * ONE function the roll ([boostedDrop]) and the Drop Viewer read. At 1x it is the plain slot share, as before; an explicit
     * `nothing` line is 0 (the remainder of the roll is nothing). See [DropBoost] for the boost.
     */
    fun lineChances(
        table: DropTable,
        rate: Double,
    ): DoubleArray {
        val entries = table.entries
        if (entries.isEmpty()) return DoubleArray(0)
        val denominator = maxOf(table.totalSlots, entries.last().index).toDouble()
        var previous = 0
        val base =
            DoubleArray(entries.size) { i ->
                val entry = entries[i]
                val share = (entry.index - previous) / denominator
                previous = entry.index
                if (entry.drop is DropEntry.NothingDrop) 0.0 else share
            }
        return DropBoost.chances(base, rate)
    }

    /**
     * The built tables of [tableId] without rolling them (the Drop Viewer reads the chances from these), or null without a table.
     */
    fun tables(
        player: Player,
        tableId: Int,
        type: DropTableType = DropTableType.KILL,
    ): List<DropTable>? {
        val bldr = tables[type]!![tableId] ?: return null
        return try {
            DropTableBuilder(player, prng).apply(bldr).build()
        } catch (e: Exception) {
            gg.rsmod.game.model.Trace.crash("drop viewer table $tableId", e)
            null
        }
    }

    /**
     * Creates a drop but only returns the values
     */
    fun getDrop(
        player: Player,
        tableId: Int,
        type: DropTableType = DropTableType.KILL,
        rate: Double = 1.0,
        replacementLoot: List<Item>? = null,
    ): MutableList<Item>? {
        val items = mutableListOf<Item>()

        val bldr = tables[type]!![tableId] ?: return null
        try {
            val table = DropTableBuilder(player, prng).apply(bldr)

            val tables = table.tables.entries.map { it.value }

            val guaranteed = tables.firstOrNull { it.name == GUARANTEED_TABLE_NAME }
            val remaining = tables.filterNot { it.name == GUARANTEED_TABLE_NAME }
            if (guaranteed != null) {
                // N108 (2026-10-24): every guaranteed line drops - also a quantity range ("1-6") and a multi line; before, only
                // fixed amounts did, so the bulk tables' ranged always-drops (e.g. npc 1831's 1-6) never came.
                items.addAll(guaranteedItems(guaranteed))
            }

            val replacement = mainReplacementTable(tableId, remaining)
            val unique = replacementLoot ?: replacement?.let { DropEntry.TableDrop(it).getDrop(rate) }.orEmpty()
            items.addAll(unique)
            // The builder stores tables in a HashMap: explicitly order this dependent pair.
            val longBone = remaining.firstOrNull { it.name == "long_bone" }
            if (longBone != null) items.addAll(DropEntry.TableDrop(longBone).getDrop(rate))
            val remainingTables = remaining.filter { it !== replacement && it !== longBone && (it.name != "main" || unique.isEmpty()) }.map { DropEntry.TableDrop(it) }
            remainingTables.forEach { entry ->
                // Mod Ash / Wiki Long bone: the 1/5000 curved roll only happens after the long-bone roll fails.
                if (entry.table.name != "curved_bone" || items.none { it.id == gg.rsmod.plugins.api.cfg.Items.LONG_BONE }) {
                    items.addAll(entry.getDrop(rate))
                }
            }
            return items
        } catch (e: Exception) {
            gg.rsmod.game.model.Trace.crash("drop table", e)
        }
        return null
    }

    /** What a guaranteed table hands out on every kill (fixed amounts, a rolled amount of a range, every item of a multi line). */
    fun guaranteedItems(table: DropTable): List<Item> =
        table.entries.flatMap { entry ->
            when (val drop = entry.drop) {
                is DropEntry.ItemDrop -> listOf(drop.item)
                is DropEntry.ItemRangeDrop -> listOf(drop.getDrop())
                is DropEntry.MultiDrop -> drop.getDrop()
                else -> emptyList()
            }
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
            gg.rsmod.game.model.Trace.crash("drop table", e)
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
            gg.rsmod.game.model.Trace.crash("drop table", e)
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
            return guaranteedItems(table)
                .map { it.id }
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
    private fun DropEntry.TableDrop.getDrop(rate: Double = 1.0): List<Item> {
        val entries = table.entries
        if (entries.isEmpty()) {
            return emptyList()
        }
        if (rate > 1.0) return boostedDrop(rate)
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
     * N100, owner 2026-10-18 (VRAGEN 34 a = A "zeldzaam eerst"): at [rate] > 1 the roll is still ONE pick from the table, but the
     * chances are [lineChances] - the rarest lines get rate x their chance, the common ones give way a little ([DropBoost]).
     * Quantities are unchanged. A nested table is entered with its boosted chance and then rolled at 1x, so its items get the
     * factor exactly once (never rate x rate). Donor: Void DropTable.roll(multiplier) (engine/.../drop/DropTable.kt:29-44: the
     * factor sits in the roll, the caller passes it; rarest-first order :46-49).
     */
    private fun DropEntry.TableDrop.boostedDrop(rate: Double): List<Item> {
        val chances = lineChances(table, rate)
        val at = DropBoost.pick(chances, prng.nextDouble())
        if (at < 0) return emptyList()
        return when (val drop = table.entries[at].drop) {
            is DropEntry.ItemDrop -> listOf(drop.item)
            is DropEntry.MultiDrop -> drop.getDrop()
            is DropEntry.TableDrop -> drop.getDrop()
            is DropEntry.ItemRangeDrop -> listOf(drop.getDrop())
            DropEntry.NothingDrop -> emptyList()
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
            // One entity per item (cbefbb361), each owned by the killer like a stackable drop: the split lost the owner, so
            // non-stackable loot (armour, weapons) was public to every player the moment it landed.
            for (i in 0 until item.amount) {
                val ground = GroundItem(item.id, 1, tile, owner as? Player)
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
        step: Int = 1,
    ) {
        val item = Item(id, quantityRange.first)

        occupiedSlots += slots
        entries.add(Entry(occupiedSlots, DropEntry.ItemRangeDrop(item, quantityRange, step)))
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
        label: String? = DropTableNames.of(table),
    ) {
        val tab =
            DropTableBuilder(player, prng)
                .apply(table)
                .build()
                .first()

        occupiedSlots += slots
        entries.add(Entry(occupiedSlots, DropEntry.TableDrop(tab, label)))
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
