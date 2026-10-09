package gg.rsmod.plugins.content.areas.grandexchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.tools.importer.MansionMapIconTool
import gg.rsmod.game.tools.importer.StoreMapIconTool
import io.netty.buffer.Unpooled
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Grand Exchange / Royal Hall map icons (owner 2026-10-09 201954, 202116, 202308, 212609, 212919, 213724, 214334;
 * MansionMapIconTool), in BOTH caches: the marked world-map icons are gone, no store icon is pinned to a tile (the npcs carry
 * them, so they move with the npc), every custom icon names itself on hover, Kuradal carries the slayer icon and the casino
 * star is a minimap-only element on the casino storey's centre.
 */
class MansionMapIconTests {
    private val caches = listOf(Paths.get("..", "..", "data", "cache").toString(), "../../../../file-server/cache")

    private fun <T> each(block: (CacheLibrary) -> T) =
        caches.forEach { path ->
            val library = CacheLibrary(path)
            try {
                block(library)
            } finally {
                library.close()
            }
        }

    @Test
    fun `the marked GE and music icons are gone from the world map`() =
        each { library ->
            val main = library.index(23).archive("main")!!
            val area = library.data(23, main.id, main.file("area")!!.id)!!
            val left = MansionMapIconTool.areaLocs(area).filter { l -> MansionMapIconTool.WORLD_MAP_REMOVALS.any { (x, z, id) -> l.x == x && l.z == z && l.id == id } }
            assertEquals(emptyList(), left.map { "${it.id} at ${it.x},${it.z}" })
        }

    @Test
    fun `no store icon is pinned to a tile, the casino star is one minimap-only element on the casino storey`() =
        each { library ->
            val statics = library.index(23).archive("main_staticelements")!!
            val entries = statics.fileIds().toList().mapNotNull { library.data(23, statics.id, it) }.filter { it.size == 7 }
            fun element(d: ByteArray) = ((d[4].toInt() and 0xFF) shl 8) or (d[5].toInt() and 0xFF)
            val stores = setOf(StoreMapIconTool.DONATOR_ELEMENT, StoreMapIconTool.DEADMAN_ELEMENT, StoreMapIconTool.LOYALTY_ELEMENT)
            assertEquals(0, entries.count { element(it) in stores }, "store icons pinned to a tile")
            val stars = entries.filter { element(it) == MansionMapIconTool.GAMBLING_ELEMENT }
            assertEquals(1, stars.size, "casino stars")
            assertTrue(
                stars.single().contentEquals(
                    MansionMapIconTool.staticEntry(MansionMapIconTool.GAMBLING_ELEMENT, MansionMapIconTool.GAMBLING_X, MansionMapIconTool.GAMBLING_Z, MansionMapIconTool.GAMBLING_LEVEL),
                ),
                "casino star spot",
            )
            val star = library.data(2, 36, MansionMapIconTool.GAMBLING_ELEMENT)!!
            assertEquals(MansionMapIconTool.withFlags(star, 2).toList(), star.toList(), "casino star must be minimap only")
        }

    @Test
    fun `every custom icon has its own hover name, Jagex's way`() =
        each { library ->
            MansionMapIconTool.CATEGORIES.forEach { cat ->
                cat.elements.forEach { e -> assertEquals(cat.id, MansionMapIconTool.categoryIn(library.data(2, 36, e)!!), "element $e category") }
                assertEquals(MansionMapIconTool.struct(cat).toList(), library.data(2, 26, cat.struct)!!.toList(), "struct of '${cat.name}'")
                val script = library.index(12).archives().single { it.hashName == MansionMapIconTool.scriptNameHash(cat.id) }
                assertEquals(cat.script, script.id, "mouse-over script of '${cat.name}'")
            }
        }

    @Test
    fun `Kuradal carries the Slayer Master icon`() =
        each { library ->
            val def = NpcDef(MansionMapIconTool.KURADAL).also { it.decode(Unpooled.wrappedBuffer(library.data(18, MansionMapIconTool.KURADAL ushr 7, MansionMapIconTool.KURADAL and 0x7F)!!)) }
            assertEquals(MansionMapIconTool.SLAYER_MASTER_ELEMENT, def.mapElement)
        }
}
