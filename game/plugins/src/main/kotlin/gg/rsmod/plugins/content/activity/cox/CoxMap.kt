package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.instance.InstancedMapAttribute
import gg.rsmod.game.model.instance.InstancedMapConfiguration
import kotlin.random.Random

/**
 * The raid's private copy of the chambers (same pattern as ToaMap): source coordinates stay in OSRS space, only the copy step adds the
 * cache translation. OsrsRegionImportTool batch 'cox-map' moves the OSRS room banks (squares x 51-52, y 80-85 + 89) and the Olm's
 * chamber 12889 by +0,+24 squares (= +1536 tiles z, the same z-offset as ToA) because 7 of those squares are rev-667 squares in use
 * (dossier COX.txt 'OFFSET-KEUZE 15-11 14:5x'). The entrance (4918/4919/5174/5175) is imported in place.
 *
 * Instance (tsps Map.ChambersOfXeric.js, checked against rsprox OSRS captures): 16x16 chunks; floor 1 on plane 3, floor 2 on plane 2;
 * every chunk of a floor's 16x8 block first gets the plain wall chunk (416,647) turned at random, then each room's 4x4 chunks turned
 * with the room; the Olm's chamber = 5x7 chunks from (402,713) on every plane at instance chunk (0,8).
 */
object CoxMap {
    const val CACHE_OFFSET_X = 0
    const val CACHE_OFFSET_Z = 1536
    const val INSTANCE_CHUNKS = 16
    const val ROOM_TILES = CoxLayouts.ROOM_CHUNKS * 8

    const val WRAPPER_CHUNK_X = 416
    const val WRAPPER_CHUNK_Z = 647
    const val OLM_CHUNK_X = 402
    const val OLM_CHUNK_Z = 713
    const val OLM_WIDTH = 5
    const val OLM_HEIGHT = 7
    const val OLM_AT_CHUNK_X = 0
    const val OLM_AT_CHUNK_Z = 8

    /** OSRS-coordinates (tsps OLM_TILES): the Olm chamber's entrance and middle. */
    val OLM_ENTRANCE_SOURCE = Tile(3232, 5719)
    val OLM_CENTRE_SOURCE = Tile(3232, 5734)

    /** Tiles the raid binds rooms to, OSRS-coordinates, per version (west / straight / east). tsps BOUND - 'unverified' there: walkmap. */
    val RAID_START_SPAWN = listOf(Tile(3278, 5187), Tile(3298, 5188), Tile(3330, 5188))
    val FLOOR_END_HOLE = listOf(Tile(3278, 5168), Tile(3308, 5173), null)
    val FLOOR_START_ROPE = listOf(Tile(3280, 5711), Tile(3312, 5711), Tile(3344, 5711))

    /** The OSRS squares the batch 'cox-map' copies (before the offset) - for multi-combat registration like ToaMap.CACHE_REGIONS. */
    val SOURCE_REGIONS: List<Int> = ((80..85) + 89).flatMap { z -> listOf((51 shl 8) or z, (52 shl 8) or z) } + 12889
    val CACHE_REGIONS: List<Int> = SOURCE_REGIONS.map { region ->
        ((((region shr 8) * 64 + CACHE_OFFSET_X) shr 6) shl 8) or (((region and 255) * 64 + CACHE_OFFSET_Z) shr 6)
    }

    private fun cacheChunk(chunkX: Int, chunkZ: Int, plane: Int) = Tile(chunkX * 8 + CACHE_OFFSET_X, chunkZ * 8 + CACHE_OFFSET_Z, plane)

    /** Where source chunk (sx, sz) of a 4x4 room lands inside the room when the room is turned clockwise [rotation] times. */
    fun rotateChunk(sx: Int, sz: Int, rotation: Int): Pair<Int, Int> = rotateLocal(sx, sz, rotation, CoxLayouts.ROOM_CHUNKS - 1)

    /** A tile of a 32x32 room turned clockwise [rotation] times; [sizeX]/[sizeZ] for an npc/loc footprint's south-west tile. */
    fun rotateRoomTile(x: Int, z: Int, rotation: Int, sizeX: Int = 1, sizeZ: Int = sizeX): Pair<Int, Int> {
        val last = ROOM_TILES - 1
        return when (rotation and 3) {
            0 -> x to z
            1 -> z to last - x - (sizeX - 1)
            2 -> last - x - (sizeX - 1) to last - z - (sizeZ - 1)
            else -> last - z - (sizeZ - 1) to x
        }
    }

    private fun rotateLocal(x: Int, z: Int, rotation: Int, last: Int): Pair<Int, Int> = when (rotation and 3) {
        0 -> x to z
        1 -> z to last - x
        2 -> last - x to last - z
        else -> last - z to x
    }

    fun chunks(layout: CoxLayout, random: Random): InstancedChunkSet {
        val builder = InstancedChunkSet.Builder()
        layout.floors.forEachIndexed { floorIndex, floor ->
            val plane = CoxLayouts.FLOOR_PLANES[floorIndex]
            for (x in 0 until INSTANCE_CHUNKS) for (z in 0 until OLM_AT_CHUNK_Z) {
                builder.set(x, z, plane, rot = random.nextInt(4), copy = cacheChunk(WRAPPER_CHUNK_X, WRAPPER_CHUNK_Z, 0))
            }
            for (room in floor) {
                for (sx in 0 until CoxLayouts.ROOM_CHUNKS) for (sz in 0 until CoxLayouts.ROOM_CHUNKS) {
                    val (dx, dz) = rotateChunk(sx, sz, room.rotation)
                    builder.set(room.gridX * CoxLayouts.ROOM_CHUNKS + dx, room.gridZ * CoxLayouts.ROOM_CHUNKS + dz, plane, rot = room.rotation,
                        copy = cacheChunk(room.sourceChunkX + sx, room.sourceChunkZ + sz, room.room.plane))
                }
            }
        }
        for (plane in 0..3) for (x in 0 until OLM_WIDTH) for (z in 0 until OLM_HEIGHT) {
            builder.set(OLM_AT_CHUNK_X + x, OLM_AT_CHUNK_Z + z, plane, copy = cacheChunk(OLM_CHUNK_X + x, OLM_CHUNK_Z + z, plane))
        }
        return builder.build()
    }

    fun allocate(world: World, layout: CoxLayout, exit: Tile, random: Random = Random.Default): InstancedMap? =
        world.instanceAllocator.allocate(world, chunks(layout, random),
            InstancedMapConfiguration.Builder().setExitTile(exit).addAttribute(InstancedMapAttribute.MANAGED_LIFETIME)
                // 15-11 18:2x (testserver: 'Could not copy object due to its size and rotation outcome' on loc 66743 in a turned room):
                // the OSRS client places a turned multi-tile loc whose rotated south-west tile leaves its chunk in the NEIGHBOUR chunk (it
                // only checks the scene bounds). The engine's bypass drops such locs on the server side (client still draws them) -
                // TODO COX.txt 'ENGINE-PUNT': own flag that adds them to the neighbour chunk (Pest Control/POH keep today's bypass).
                .setBypassObjectChunkBounds(true).build())

    /** An OSRS tile of [room]'s source block, where it lands in the raid ([plane] = the floor's instance plane). */
    fun roomTile(baseX: Int, baseZ: Int, plane: Int, room: CoxLaidRoom, source: Tile, sizeX: Int = 1, sizeZ: Int = sizeX): Tile {
        val localX = source.x - room.sourceChunkX * 8
        val localZ = source.z - room.room.chunkZ * 8
        val (tx, tz) = rotateRoomTile(localX, localZ, room.rotation, sizeX, sizeZ)
        return Tile(baseX + room.gridX * ROOM_TILES + tx, baseZ + room.gridZ * ROOM_TILES + tz, plane)
    }

    fun roomTile(map: InstancedMap, floorIndex: Int, room: CoxLaidRoom, source: Tile, sizeX: Int = 1, sizeZ: Int = sizeX): Tile =
        roomTile(map.area.bottomLeftX, map.area.bottomLeftZ, CoxLayouts.FLOOR_PLANES[floorIndex], room, source, sizeX, sizeZ)

    /** A tile of the Olm's chamber given its OSRS coordinate. */
    fun olmTile(map: InstancedMap, source: Tile): Tile = Tile(
        map.area.bottomLeftX + OLM_AT_CHUNK_X * 8 + (source.x - OLM_CHUNK_X * 8),
        map.area.bottomLeftZ + OLM_AT_CHUNK_Z * 8 + (source.z - OLM_CHUNK_Z * 8), source.height)

    /** The room (floor index + room) an instance tile is in, or null (Olm's chamber / outside / wall filler). */
    fun roomAt(map: InstancedMap, layout: CoxLayout, tile: Tile): Pair<Int, CoxLaidRoom>? {
        val lx = tile.x - map.area.bottomLeftX
        val lz = tile.z - map.area.bottomLeftZ
        if (lx < 0 || lz < 0 || lz >= OLM_AT_CHUNK_Z * 8) return null
        val floorIndex = CoxLayouts.FLOOR_PLANES.indexOf(tile.height).takeIf { it in layout.floors.indices } ?: return null
        val room = layout.floors[floorIndex].firstOrNull { it.gridX == lx / ROOM_TILES && it.gridZ == lz / ROOM_TILES } ?: return null
        return floorIndex to room
    }
}
