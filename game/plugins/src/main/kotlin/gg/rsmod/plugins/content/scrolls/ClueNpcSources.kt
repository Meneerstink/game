package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.content.areas.wilderness.npcs.WildernessNpcDrops
import gg.rsmod.plugins.content.drops.*
import gg.rsmod.plugins.content.quests.tab.DropSources
import java.io.File
import kotlin.random.Random

/** Exact sourced NPC clue rolls. Name/level conflicts are held in NPC-SOURCE-CONFLICTS.json. */
object ClueNpcSources {
    data class Row(val npc: Int, val name: String, val level: Int, val tier: ClueScrollTier, val denominator: Int)
    val rows by lazy {
        JsonParser().parse(File(RspsPaths.data("cfg/clue/npc-sources.json")).readText()).asJsonArray.map { value ->
            val row = value.asJsonObject
            Row(row["npc"].asInt, row["name"].asString, row["level"].asInt,
                ClueScrollTier.valueOf(row["tier"].asString.uppercase()), row["denominator"].asInt).also {
                require(it.denominator > 0 && it.tier != ClueScrollTier.MASTER)
            }
        }.also { require(it.distinctBy { row -> row.npc to row.tier }.size == it.size) }
    }
    private val byNpc by lazy { rows.groupBy { it.npc } }
    val npcIds: Set<Int> get() = byNpc.keys
    fun has(npc: Int): Boolean = npc in byNpc

    private fun verified(player: Player, npc: Int): List<Row> {
        val source = byNpc[npc] ?: return emptyList()
        val def = player.world.definitions.getNullable(NpcDef::class.java, npc) ?: return emptyList()
        return source.filter { it.name.equals(def.name, ignoreCase = true) && it.level == def.combatLevel }
    }

    /** Remove a replaced clue outcome without renormalising any other item/table weight. */
    fun strip(tables: List<DropTable>, tiers: Set<ClueScrollTier>): List<DropTable> {
        fun isClue(item: Item) = ClueOwnership.tier(item.id) in tiers
        fun table(source: DropTable): DropTable = source.copy(entries = source.entries.map { entry ->
            val drop = when (val d = entry.drop) {
                is DropEntry.ItemDrop -> if (isClue(d.item)) DropEntry.NothingDrop else d
                is DropEntry.ItemRangeDrop -> if (isClue(d.item)) DropEntry.NothingDrop else d
                is DropEntry.MultiDrop -> d.items.filterNot(::isClue).let {
                    if (it.isEmpty()) DropEntry.NothingDrop else DropEntry.MultiDrop(*it.toTypedArray())
                }
                is DropEntry.TableDrop -> DropEntry.TableDrop(table(d.table), d.label)
                DropEntry.NothingDrop -> d
            }
            entry.copy(drop = drop)
        }.toTypedArray())
        return if (tiers.isEmpty()) tables else tables.map(::table)
    }

    fun tables(player: Player, npc: Int, tables: List<DropTable>): List<DropTable> =
        strip(tables, verified(player, npc).map { it.tier }.toSet())

    private fun denominator(player: Player, row: Row): Int = (if (player.tile.getWildernessLevel() > 0)
        WildernessNpcDrops.clueDenominator(player, row.denominator) else row.denominator) /
        gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilDrops.clueFactor(player) // S18 Treasure Hunter: 3x more often

    fun roll(player: Player, npc: Int, random: Random = Random.Default): List<Item> = verified(player, npc).mapNotNull { row ->
        if (random.nextInt(denominator(player, row).coerceAtLeast(1)) != 0) return@mapNotNull null
        ClueContainers.rewardId(player, row.tier)?.let { Item(it) }
    }

    fun lines(player: Player, npc: Int): List<DropSources.Line> = verified(player, npc).mapNotNull { row ->
        val item = ClueItems.local(ClueContainers.boxes.getValue(row.tier)) ?: return@mapNotNull null
        val eligible = ClueContainers.count(player, row.tier) < ClueContainers.capacity(player, row.tier)
        DropSources.Line("clue-npc:${row.tier.name}", item, DropSources.itemName(player.world, item), "1",
            if (eligible) 1.0 / denominator(player, row).coerceAtLeast(1) else 0.0, 1.0 / row.denominator,
            note = "OSRS clue rate; subject to scroll box capacity")
    }
}
