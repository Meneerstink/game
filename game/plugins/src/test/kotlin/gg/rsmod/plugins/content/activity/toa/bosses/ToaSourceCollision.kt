package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.every

/** Independent cache2686 terrain/loc snapshot, with raw planes and all collision-bearing placements.
 * Source hashes are in the resource. This proves source collision, not the installed cache or client rendering.
 */
internal class ToaSourceCollision(world: World, region: Int) {
    val collision = CollisionManager(ChunkSet(world))
    private val baseX = (region shr 8) * 64
    private val baseZ = (region and 255) * 64
    private val settings = Array(4) { Array(64) { IntArray(64) } }
    private val lines = checkNotNull(javaClass.getResourceAsStream("/toa/cache2686-$region-collision.txt"))
        .bufferedReader().use { it.readLines() }.filter { it.isNotBlank() && !it.startsWith('#') }

    /** The client's bridge rule is read from plane1 regardless of the raw tile/loc plane. */
    fun plane(x: Int, z: Int, raw: Int): Int = raw - if (settings[1][x - baseX][z - baseZ] and 2 != 0) 1 else 0

    init {
        every { world.collision } returns collision
        lines.filter { it.startsWith("T ") }.forEach { line ->
            val t = line.split(' ')
            t[3].forEachIndexed { z, flag -> settings[t[1].toInt()][t[2].toInt()][z] = flag.digitToInt(16) }
        }
        val terrain = CollisionUpdate.Builder().apply {
            setType(CollisionUpdate.Type.ADD)
            for (p in 0..3) for (x in 0..63) for (z in 0..63) {
                val height = plane(baseX + x, baseZ + z, p)
                if (height >= 0 && settings[p][x][z] and 1 != 0)
                    putTile(Tile(baseX + x, baseZ + z, height), false, *Direction.NESW)
            }
            // Never let a test route escape into unknown neighbouring maps.
            for (p in 0..3) for (i in -1..64) {
                putTile(Tile(baseX - 1, baseZ + i, p), true, *Direction.NESW)
                putTile(Tile(baseX + 64, baseZ + i, p), true, *Direction.NESW)
                putTile(Tile(baseX + i, baseZ - 1, p), true, *Direction.NESW)
                putTile(Tile(baseX + i, baseZ + 64, p), true, *Direction.NESW)
            }
        }.build()
        collision.applyUpdate(terrain)
        val placements = lines.filter { it.startsWith("L ") }.map { it.substring(2).split(' ').map(String::toInt) }
        val definitions = world.definitions
        val objects = placements.associate { t -> t[0] to ObjectDef(t[0]).apply {
            width = t[6]; length = t[7]; blockwalk = t[8]; blockrange = t[9] != 0; breakroutefinding = t[10] != 0
        } }
        // One typed generic stub avoids MockK's lost auto-hint after thousands of identical recordings/JVM fast throws.
        every { definitions.hint(ObjectDef::class).get(ObjectDef::class.java, any()) } answers { objects.getValue(secondArg()) }
        placements.forEach { t ->
            val height = plane(baseX + t[2], baseZ + t[3], t[1])
            if (height < 0) return@forEach
            collision.applyUpdate(CollisionUpdate.Builder().apply {
                setType(CollisionUpdate.Type.ADD)
                putObject(definitions, StaticObject(t[0], t[4], t[5], Tile(baseX + t[2], baseZ + t[3], height)))
            }.build())
        }
    }
}
