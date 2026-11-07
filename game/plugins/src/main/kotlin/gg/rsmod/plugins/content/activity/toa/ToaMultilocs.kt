package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import java.io.File

/**
 * N462 07-11: the OSRS multiloc parents in the ToA/Necropolis squares have no models of their own; the 667 import drops their varbit
 * transform table (Rev667WorldCodec.encodeFromModern), so they rendered and offered nothing (Rewards Niche, lobby cavity, Het statues,
 * vault strange chest ...). data/cfg/toa/multilocs.tsv (work/claude-scripts/toa_multilocs.py from the cache2686 placements) lists each
 * placed parent with its transform[0] (varbit value 0); the server places that variant - like toa_necropolis.plugin.kts does for the
 * pyramid entrance and ToaScene for the doors and chests. Handled elsewhere (left out of the table): 44596 entrance, 46223 Bank Camel,
 * the vault chests and the vault sarcophagus/barrier/glow ([ToaScene.vault]).
 * Left out on purpose: bcs_necropolis_entry 44595 -> 44004 'Rubble' (noclimbable) - OSRS blocks the Necropolis until Beneath Cursed
 * Sands is partly done; here the Necropolis stays open (owner question N462-GEBIED (1)).
 */
object ToaMultilocs {
    data class Row(val raid: Boolean, val osrsParent: Int, val osrsVariant: Int, val tile: Tile, val shape: Int, val rotation: Int)

    private val NOT_PLACED = setOf(44595)

    val rows: List<Row> by lazy {
        val file = File(RspsPaths.data("cfg/toa/multilocs.tsv"))
        if (!file.exists()) return@lazy emptyList()
        file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 8) return@mapNotNull null
            Row(f[0] == "raid", f[1].toInt(), f[2].toInt(), Tile(f[3].toInt(), f[4].toInt(), f[5].toInt()), f[6].toInt(), f[7].toInt())
        }.filter { it.osrsParent !in NOT_PLACED }
    }

    /** Static map squares (Necropolis, lobby): swap the placed parent for its variant. */
    fun placeWorld(world: World) {
        rows.filter { !it.raid }.forEach { row -> swap(world, row, row.tile) }
    }

    /** One raid instance: the parent is copied with the chunks, the variant goes on the translated tile. */
    fun placeRaid(raid: ToaRaid) {
        rows.filter { it.raid }.forEach { row -> swap(raid.world, row, ToaMap.instance(raid.map, row.tile)) }
    }

    private fun swap(world: World, row: Row, tile: Tile) {
        val variant = ToaIds.loc(row.osrsVariant) ?: return
        val parent = ToaIds.loc(row.osrsParent)
        world.chunks.getOrCreate(tile).getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
            .filter { it.id == parent }.toList().forEach { world.remove(it) }
        world.spawn(DynamicObject(variant, row.shape, row.rotation, tile))
    }
}
