package gg.rsmod.plugins.content.areas.grandexchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.decor.HomeDecorBake
import gg.rsmod.game.model.decor.HomeDecorFile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.tools.importer.Rev667LocCodec
import gg.rsmod.game.tools.importer.Rev667RegionProbeTool
import gg.rsmod.util.ServerProperties
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Royal Hall baked into region 12598's loc file (owner 2026-10-09: the hall built up piece by piece after every login /
 * teleport; RoyalHallDecorBakeTool, HomeDecorBake): the manifest is exactly what the map holds, home_decor.txt adds nothing
 * beyond it (so no decor loc reaches the client one by one), the carpet keeps drawing on the low preset, and the static locs
 * block exactly like the dynamic ones they replace.
 */
class HomeDecorBakeTests {
    companion object {
        private val DATA = Paths.get("..", "..", "data")
        private val BOX_X = 3148..3182
        private val BOX_Z = 3474..3510

        lateinit var world: World
        lateinit var manifest: List<HomeDecorFile.Placement>

        @BeforeClass
        @JvmStatic
        fun load() {
            val w = World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true))
            w.filestore = CacheLibrary(DATA.resolve("cache").toString())
            w.definitions.loadAll(w.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), w, ServerProperties().loadMap(mapOf("path" to DATA.resolve("xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(w) as MutableList<Service>
            services += xteas
            world = w
            w.chunks.getOrCreate(Tile(3164, 3491, 0))
            manifest = HomeDecorBake.loadManifest(DATA.resolve("cfg").resolve("home_decor.baked.txt"))
        }

        fun objectsAt(t: Tile): List<GameObject> =
            world.chunks.getOrCreate(t).getEntities<GameObject>(t, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).toList()
    }

    @Test
    fun `the manifest lists exactly locs of region 12598's loc file in both caches`() {
        assertTrue(manifest.size > 800, "the hall is not baked (manifest has ${manifest.size} lines)")
        listOf(DATA.resolve("cache").toString(), "../../../../file-server/cache").forEach { path ->
            val library = CacheLibrary(path)
            try {
                val key = Rev667RegionProbeTool.loadKeys(DATA.resolve("xteas").resolve("xteas.json").toFile())[HomeDecorBake.REGION_ID]!!
                val locs = Rev667LocCodec.decode(library.index(5).archive("l49_54", key)!!.file(0)!!.data!!)
                val inFile = locs.groupingBy { listOf(it.id, 3136 + it.localX, 3456 + it.localZ, it.plane, it.type, it.rotation) }.eachCount()
                val missing = manifest.filter { (inFile[listOf(it.id, it.tile.x, it.tile.z, it.tile.height, it.type, it.rot)] ?: 0) == 0 }
                assertTrue(missing.isEmpty(), "$path: manifest locs not in l49_54: ${missing.take(10)}")
            } finally {
                library.close()
            }
        }
    }

    /** The casino star is no map-file loc any more (owner 2026-10-09 212609; MansionMapIconTests guards its static element). */
    @Test
    fun `no casino star marker loc in the map file`() {
        val library = CacheLibrary(DATA.resolve("cache").toString())
        try {
            val key = Rev667RegionProbeTool.loadKeys(DATA.resolve("xteas").resolve("xteas.json").toFile())[HomeDecorBake.REGION_ID]!!
            val stars = Rev667LocCodec.decode(library.index(5).archive("l49_54", key)!!.file(0)!!.data!!).filter { it.id == gg.rsmod.game.tools.importer.RoyalHallLocTool.CASINO_MARKER }
            assertEquals(0, stars.size, "casino star marker locs in l49_54")
        } finally {
            library.close()
        }
    }

    @Test
    fun `home_decor txt adds nothing beyond the bake - no decor loc is sent one by one`() {
        val design = HomeDecorFile.load(DATA.resolve("cfg").resolve("home_decor.txt"))
        val diff = HomeDecorBake.diff(design, manifest)
        assertEquals(emptyList(), diff.extra, "lines spawned dynamically - bake again (RoyalHallDecorBakeTool)")
        assertEquals(emptyList(), diff.missing, "baked locs the file dropped - bake again (RoyalHallDecorBakeTool)")
    }

    @Test
    fun `every baked walk-through ground decoration has forcedecor, so the low preset draws it`() {
        val problems =
            manifest.filter { it.type == 22 }.map { it.id }.distinct().filter { id ->
                val def = world.definitions.get(ObjectDef::class.java, id)
                // client MapRegion: dropped while Ground decoration is off unless interactive, blockwalk 1 or forcedecor (opcode 73,
                // the server's ObjectDef calls it obstructive)
                !def.obstructive && !def.interactive && def.blockwalk != 1
            }
        assertTrue(problems.isEmpty(), "ground decorations the low preset would drop: $problems")
        HomeDecorBake.FORCEDECOR_CARPETS.forEach { (source, copy) ->
            val a = world.definitions.get(ObjectDef::class.java, source)
            val b = world.definitions.get(ObjectDef::class.java, copy)
            assertTrue(b.obstructive && !a.obstructive && a.name == b.name && a.solid == b.solid && a.blockwalk == b.blockwalk, "carpet copy $copy of $source")
        }
    }

    @Test
    fun `the baked locs block exactly like the dynamic ones they replace, on every plane`() {
        fun fingerprint(): List<String> {
            val out = ArrayList<String>()
            for (level in 0..3) for (x in BOX_X) for (z in BOX_Z) {
                val t = Tile(x, z, level)
                val bits = Direction.RS_ORDER.map { d -> (if (world.collision.isBlocked(t, d, false)) 1 else 0) + (if (world.collision.isBlocked(t, d, true)) 2 else 0) }
                out += "$x,$z,$level clipped=${world.collision.isClipped(t)} $bits"
            }
            return out
        }
        val static = fingerprint()
        val inBox = manifest.filter { it.tile.x in BOX_X && it.tile.z in BOX_Z }
        inBox.forEach { p -> objectsAt(p.tile).filter { it.id == p.id && it.type == p.type && it.rot == p.rot }.forEach { world.remove(it) } }
        inBox.forEach { p -> world.spawn(DynamicObject(p.id, p.type, p.rot, p.tile)) }
        val dynamic = fingerprint()
        val differ = static.indices.filter { static[it] != dynamic[it] }.map { "static ${static[it]} / dynamic ${dynamic[it]}" }
        assertTrue(differ.isEmpty(), "collision differs:\n" + differ.take(20).joinToString("\n"))
    }
}
