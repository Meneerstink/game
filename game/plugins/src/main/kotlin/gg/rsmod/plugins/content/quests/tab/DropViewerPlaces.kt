package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestWorld

/**
 * Where a Drop Viewer entry lives (N108, the head labels "Wild 40 / Multi / Dangerous" and "Show on map"): the spawn tile of the
 * first npc of that id the world spawned, read live - an npc nobody spawned has no place (no labels, no map button). Breach
 * monsters are where the open breach is; the chest where the timed chest stands.
 */
object DropViewerPlaces {
    class Place(val tile: Tile, val wilderness: Int, val multi: Boolean, val safe: Boolean)

    private val spawns = java.util.concurrent.ConcurrentHashMap<Int, Tile>()

    /** The spawn tile of npc [id]: the first live npc of that id (its spawn tile, which never moves); cached once found. */
    fun spawnOf(world: World, id: Int): Tile? {
        spawns[id]?.let { return it }
        var found: Tile? = null
        world.npcs.forEach { npc -> if (found == null && npc.id == id) found = Tile(npc.spawnTile) }
        return found?.also { spawns[id] = it }
    }

    fun place(world: World, tile: Tile): Place =
        Place(
            tile,
            runCatching { tile.getWildernessLevel() }.getOrDefault(0),
            runCatching { tile.isMulti(world) }.getOrDefault(false),
            runCatching { gg.rsmod.plugins.content.mechanics.pvp.GuardedZones.contains(tile) }.getOrDefault(false),
        )

    fun of(world: World, source: DropSources.Source): Place? {
        val tile =
            when (source.kind) {
                DropSources.Kind.BREACH -> DeadmanBreach.active?.sites?.firstOrNull()?.spawners?.firstOrNull()
                DropSources.Kind.CHEST -> DeadmanChestWorld.activeTimed()?.tile
                DropSources.Kind.GUARDIAN -> DeadmanChestWorld.activeTimed()?.tile
                DropSources.Kind.BARROWS -> Tile(3565, 3289)
                else -> spawnOf(world, source.modelNpc)
            } ?: return null
        return place(world, tile)
    }

    fun inWilderness(world: World, npcId: Int): Boolean = spawnOf(world, npcId)?.let { runCatching { it.getWildernessLevel() > 0 }.getOrDefault(false) } ?: false
}
