package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N231 (owner 2026-10-23): the GE summoning obelisk shows the Summoning obelisk icon on the minimap and the world map, wherever it stands. */
class ObeliskMapIconToolTests {
    private val caches = listOf(Paths.get("..", "data", "cache").toFile(), Paths.get("..", "..", "..", "file-server", "cache").toFile())

    @Test
    fun `loc 50205 carries the Summoning obelisk map element in both caches`() {
        caches.forEach { cache ->
            val library = CacheLibrary(cache.toString())
            try {
                val def = library.data(ObeliskMapIconTool.LOC_INDEX, ObeliskMapIconTool.OBELISK ushr 8, ObeliskMapIconTool.OBELISK and 0xFF)!!
                assertEquals(ObeliskMapIconTool.SUMMONING_ELEMENT, ObeliskMapIconTool.mapElement(def), "$cache")
            } finally {
                library.close()
            }
        }
    }

    @Test
    fun `the world map gets spawned locs with a map element from the server`() {
        val icons = File("plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/worldedit/NpcMapIcons.kt").readText()
        assertTrue("\"locicons|\"" in icons && "EntityType.DYNAMIC_OBJECT" in icons && "mapElement" in icons)
        assertTrue("ClientHud.send(player, locPayload(player.world))" in icons, "on login")
        assertTrue("ClientHud.send(it, locs)" in icons, "after every world edit")
        val def = File("src/main/kotlin/gg/rsmod/game/fs/def/ObjectDef.kt").readText()
        assertTrue("107 -> mapElement = buf.readUnsignedShort()" in def)
    }
}
