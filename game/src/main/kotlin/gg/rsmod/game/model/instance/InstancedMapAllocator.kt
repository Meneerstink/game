package gg.rsmod.game.model.instance

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.game.model.region.Chunk
import gg.rsmod.game.model.region.ChunkCoords

/**
 * A system responsible for allocating and de-allocating [InstancedMap]s.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class InstancedMapAllocator {
    /**
     * A list of active [InstancedMap]s.
     */
    private val maps = mutableListOf<InstancedMap>()

    /**
     * The current cycles that keep track of how long before our allocated
     * should scan for 'inactive' [InstancedMap]s.
     */
    private var deallocationScanCycle = 0

    /**
     * Allocate a new [InstancedMap] given [chunks].
     *
     * @param world
     * The [World] that the instanced map is apart of.
     *
     * @param chunks
     * The [InstancedChunkSet] that holds all the [InstancedChunk]s that will make
     * up the newly constructed [InstancedMap], if applicable.
     */
    fun allocate(
        world: World,
        chunks: InstancedChunkSet,
        configs: InstancedMapConfiguration,
    ): InstancedMap? {
        val area = VALID_AREA
        val step = 64

        /*
         * The total amount of tiles that the new [InstancedMap] will occupy.
         */
        val totalTiles = chunks.regionSize * Chunk.REGION_SIZE

        for (x in area.bottomLeftX until area.topRightX step step) {
            for (z in area.bottomLeftZ until area.topRightZ step step) {
                /*
                 * If a map (or its empty border) is already in the way, we move on.
                 */
                if (!isFree(x, z, totalTiles)) {
                    continue
                }

                val map = allocate(x, z, chunks, configs)
                clearCollision(world, map)
                applyCollision(world, map, configs.bypassObjectChunkBounds)
                maps.add(map)
                addNpcs(configs.npcs, map)
                return map
            }
        }

        return null
    }

    /**
     * True when a map of [regionSize] regions would find a free place right now - lets content refuse an action that costs
     * something (a spell's runes) before it is paid for, instead of paying and then finding no space.
     */
    fun hasFreeSpace(regionSize: Int = 1): Boolean {
        val area = VALID_AREA
        val totalTiles = regionSize * Chunk.REGION_SIZE
        for (x in area.bottomLeftX until area.topRightX step 64) {
            for (z in area.bottomLeftZ until area.topRightZ step 64) {
                if (isFree(x, z, totalTiles)) return true
            }
        }
        return false
    }

    /**
     * N159: the new map's square [x, x + [totalTiles]) x [z, z + [totalTiles]) plus a one-region empty border overlaps no
     * allocated map (Darkan InstanceBuilder.findEmptyChunkBound checks every region with an empty border). Only the two corners
     * were checked before, so a smaller map lying inside a bigger candidate without touching its corners was missed and a 2-3
     * region map (intro, minigame) could be put over an active one-region instance. The border also keeps a wall on a map's
     * edge tile - collision writes both sides of a wall - from flagging the edge row of an active neighbour.
     */
    internal fun isFree(
        x: Int,
        z: Int,
        totalTiles: Int,
    ): Boolean {
        val border = Chunk.REGION_SIZE
        val x0 = x - border
        val z0 = z - border
        val x1 = x + totalTiles - 1 + border
        val z1 = z + totalTiles - 1 + border
        return maps.none { m -> m.area.bottomLeftX <= x1 && m.area.topRightX >= x0 && m.area.bottomLeftZ <= z1 && m.area.topRightZ >= z0 }
    }

    private fun allocate(
        x: Int,
        z: Int,
        chunks: InstancedChunkSet,
        configs: InstancedMapConfiguration,
    ): InstancedMap =
        InstancedMap(
            Area(x, z, x + chunks.regionSize * Chunk.REGION_SIZE, z + chunks.regionSize * Chunk.REGION_SIZE),
            chunks,
            configs.exitTile,
            configs.owner,
            configs.attributes,
        )

    /**
     * Immediately de-allocates [map] (content that manages its own instance lifecycle, e.g.
     * the Fight Cave, calls this once its activity ends).
     */
    fun release(
        world: World,
        map: InstancedMap,
    ) = deallocate(world, map)

    private fun deallocate(
        world: World,
        map: InstancedMap,
    ) {
        if (maps.remove(map)) {
            removeCollision(world, map)
            world.removeAll(map.area)

            /**
             * If the map is de-allocated, we want to move any players in the
             * instance to the [InstancedMap.exitTile].
             */
            world.players.forEach { player ->
                if (map.area.contains(player.tile)) {
                    player.moveTo(map.exitTile)
                }
            }
        }
    }

    internal val activeMapCount: Int get() = maps.size

    internal fun logout(player: Player) {
        val world = player.world

        getMap(player.tile)?.let { map ->
            player.attr[gg.rsmod.game.model.attr.INSTANCE_LOGOUT_TILE_ATTR] = player.tile
            player.attr[gg.rsmod.game.model.attr.INSTANCE_LOGOUT_MAP_ATTR] = map
            player.moveTo(map.exitTile)

            if (map.attr.contains(InstancedMapAttribute.DEALLOCATE_ON_LOGOUT)) {
                val mapOwner = map.owner!! // If map has this attribute, they should also set an owner.
                if (player.uid == mapOwner) {
                    deallocate(world, map)
                }
            }
        }
    }

    internal fun death(player: Player) {
        val world = player.world

        getMap(player.tile)?.let { map ->
            if (map.attr.contains(InstancedMapAttribute.DEALLOCATE_ON_DEATH)) {
                val mapOwner = map.owner!! // If map has this attribute, they should also set an owner.
                if (player.uid == mapOwner) {
                    deallocate(world, map)
                }
            }
        }
    }

    internal fun cycle(world: World) {
        if (deallocationScanCycle++ == SCAN_MAPS_CYCLES) {
            // A copy: deallocate removes from [maps], which made the indexed loop run past the end once two maps existed
            // and one was released (IndexOutOfBoundsException in the world cycle, seen 2026-10-10 with two player houses).
            for (map in maps.toList()) {

                /*
                 * If there's no players in the [map] area, we can de-allocate
                 * the map.
                 */
                if (InstancedMapAttribute.MANAGED_LIFETIME !in map.attr && world.players.none { map.area.contains(it.tile) }) {
                    deallocate(world, map)
                }
            }

            deallocationScanCycle = 0
        }
    }

    /**
     * @return
     * An [InstancedMap] who's area contains [tile], or null if no map is found in said tile.
     */
    fun getMap(tile: Tile): InstancedMap? = maps.find { it.area.contains(tile) }

    /**
     * Before a copy, the collision of every chunk already present in the new map's own area is reset, as Darkan
     * (InstanceBuilder.copyChunk: `chunk.clearCollisionData()` before `loadMap`) and Void (DynamicZones.copy: `Collisions.clear(to)`
     * before `loadZone`) do: a chunk recreated after an earlier map's release (a ground item removed through getOrCreate, a late
     * write) must not add its flags to the new copy. A separate pass, so a large loc copied into one chunk keeps the flags it
     * sets in its neighbour.
     */
    private fun clearCollision(
        world: World,
        map: InstancedMap,
    ) {
        forEachAreaChunk(map) { coords ->
            world.chunks.get(coords, createIfNeeded = false)?.let { chunk ->
                for (height in 0 until chunk.heights) chunk.getMatrix(height).reset()
            }
        }
    }

    private fun forEachAreaChunk(
        map: InstancedMap,
        action: (ChunkCoords) -> Unit,
    ) {
        val bottomLeftChunk = map.area.bottomLeft.chunkCoords
        val topRightChunk = map.area.topRight.chunkCoords
        for (x in bottomLeftChunk.x until topRightChunk.x) {
            for (z in bottomLeftChunk.z until topRightChunk.z) {
                action(ChunkCoords(x, z))
            }
        }
    }

    private fun applyCollision(
        world: World,
        map: InstancedMap,
        bypassObjectChunkBounds: Boolean,
    ) {
        // The map's own chunks: 8 per 64-tile region (Void DynamicZones: "all sixty four zones of one" region; Darkan copies
        // width x height chunks). Chunk.CHUNKS_PER_REGION is 13 - the client's 104-tile view - and blocked a band of chunks
        // OUTSIDE this map's area that its release never removed: a released player house left the next intro's actors on
        // solid tiles in slot 6400,0 (N122).
        val bounds = CHUNKS_PER_MAP_REGION * map.chunks.regionSize
        val heights = Tile.TOTAL_HEIGHT_LEVELS

        val chunks = map.chunks.values

        for (height in 0 until heights) {
            for (x in 0 until bounds) {
                for (z in 0 until bounds) {
                    val coords = InstancedChunkSet.getCoordinates(x, z, height)
                    val chunk = chunks[coords]

                    val chunkH = (coords shr 28) and 0x3
                    val chunkX = (coords shr 14) and 0x3FF
                    val chunkZ = coords and 0x7FF

                    val baseTile = map.area.bottomLeft.transform(chunkX shl 3, chunkZ shl 3, chunkH)
                    val newChunk = world.chunks.getOrCreate(baseTile)

                    if (chunk != null) {
                        val copyTile = Tile.fromRotatedHash(chunk.packed)
                        val copyChunk = world.chunks.get(copyTile.chunkCoords, createIfNeeded = true)!!

                        copyChunk.getEntities<StaticObject>(EntityType.STATIC_OBJECT).forEach { obj ->
                            // The SOURCE plane's objects (copyTile.height), placed on this chunk's plane - the floor the client draws.
                            if (obj.tile.height == copyTile.height && obj.tile.isInSameChunk(copyTile)) {
                                val def = obj.getDef(world.definitions)
                                val width = def.getRotatedWidth(obj)
                                val length = def.getRotatedLength(obj)

                                val localX = obj.tile.x % 8
                                val localZ = obj.tile.z % 8

                                val newObj =
                                    StaticObject(
                                        obj.id,
                                        obj.type,
                                        (obj.rot + chunk.rot) and 0x3,
                                        baseTile.transformAndRotate(localX, localZ, chunk.rot, width, length),
                                    )
                                val insideChunk = newObj.tile.isInSameChunk(baseTile)

                                if (insideChunk) {
                                    newChunk.addEntity(world, newObj, newObj.tile)
                                } else if (!bypassObjectChunkBounds) {
                                    throw IllegalStateException(
                                        "Could not copy object due to its size and rotation outcome (object rotation + chunk rotation). " +
                                            "The object would, otherwise, be spawned out of bounds of its original chunk. [obj=$obj, copy=$newObj]",
                                    )
                                }
                            }
                        }

                        // N462 (ToA Zebak, testclient 07-11 19:3x 'I can't reach that!' from the dry platform beside him): blocked
                        // TERRAIN (water, cache tile flag 0x1) only stops walking - the world loads it as putTile(tile, impenetrable =
                        // false, NESW) (DefinitionSet.loadRegion). The copy used block(impenetrable = true): FULL_COLLISION also stopped
                        // every projectile over the water and overwrote the loc flags already on that tile. Same flags as the world now.
                        val terrain = CollisionUpdate.Builder().apply { setType(CollisionUpdate.Type.ADD) }
                        var anyTerrain = false
                        copyChunk.blockedTiles.forEach { tile ->
                            if (tile.height == copyTile.height && tile.isInSameChunk(copyTile)) {
                                val localX = tile.x % 8
                                val localZ = tile.z % 8
                                val local = baseTile.transformAndRotate(localX, localZ, chunk.rot)
                                newChunk.blockedTiles.add(local)
                                terrain.putTile(local, false, *Direction.NESW)
                                anyTerrain = true
                            }
                        }
                        if (anyTerrain) world.collision.applyUpdate(terrain.build())
                    } else {
                        for (lx in 0 until Chunk.CHUNK_SIZE) {
                            for (lz in 0 until Chunk.CHUNK_SIZE) {
                                newChunk.getMatrix(chunkH).block(lx, lz, impenetrable = true)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun addNpcs(
        npcs: List<Npc>,
        map: InstancedMap,
    ) {
        npcs.forEach { npc ->
            npc.world.spawn(
                Npc(
                    npc.id,
                    Tile(
                        map.area.bottomLeftX + npc.tile.x,
                        map.area.bottomLeftZ + npc.tile.z,
                        npc.tile.height,
                    ),
                    npc.world,
                ),
            )
        }
    }

    private fun removeCollision(
        world: World,
        map: InstancedMap,
    ) {
        forEachAreaChunk(map) { coords -> world.chunks.remove(coords) }
    }

    companion object {
        /**
         * 07 identifies instanced maps by having an X-axis of 6400 or above. They
         * use this for some client scripts, such as a Theatre of Blood cs2 that
         * will change depending on if you're inside the Theatre (in an instance)
         * or in the lobby.
         *
         * Current stats (as of 17/03/2018):
         * Area size: 3200x6400
         * Instance support: 5000 [50 in x-axis * 100 in z-axis]
         *
         * [Instance support]: the amount of instances the world can support at a time,
         * assuming every map is 64x64 in size, which isn't always the case.
         */
        private val VALID_AREA = Area(bottomLeftX = 6400, bottomLeftZ = 0, topRightX = 9600, topRightZ = 6400)

        /** Chunks along one side of a 64-tile region (64 / 8) - not [Chunk.CHUNKS_PER_REGION], the client's 13-chunk view. */
        internal const val CHUNKS_PER_MAP_REGION = Chunk.REGION_SIZE / Chunk.CHUNK_SIZE

        /**
         * The amount of game cycles that must go by before scanning the active
         * [maps] for any [InstancedMap] eligible to be de-allocated.
         */
        private const val SCAN_MAPS_CYCLES = 25
    }
}
