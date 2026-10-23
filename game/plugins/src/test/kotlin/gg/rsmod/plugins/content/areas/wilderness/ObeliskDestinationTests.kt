package gg.rsmod.plugins.content.areas.wilderness

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.ObeliskOptionTool
import gg.rsmod.game.tools.importer.Rev667LocType
import gg.rsmod.plugins.content.mechanics.travel.ScrollMenu
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A22 (owner 2026-10-10 "obelisk heeft geen echte gui in de poh"): the obelisks offer the OSRS options Activate / Teleport to Destination /
 * Set Destination, and Set Destination opens the OSRS "Select Obelisk destination" scroll with "1: Level 13 Wilderness" .. "6: Level 50".
 */
class ObeliskDestinationTests {
    @Test
    fun `the destination lines are keyed like the OSRS menu`() {
        assertEquals(
            listOf("1: Level 13 Wilderness", "2: Level 19 Wilderness", "3: Level 27 Wilderness", "4: Level 35 Wilderness", "5: Level 44 Wilderness", "6: Level 50 Wilderness"),
            ScrollMenu.keyed(listOf(13, 19, 27, 35, 44, 50).map { "Level $it Wilderness" }),
        )
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/areas/wilderness/wilderness_obelisk.plugin.kts").readText()
        assertTrue("ScrollMenu.open(player, \"Select Obelisk destination\", obeliskDestinationLines())" in script)
        assertTrue("\"Level \${it.name.removePrefix(\"LEVEL_\")} Wilderness\"" in script)
    }

    @Test
    fun `every obelisk loc carries the three OSRS options in both caches`() {
        for (cache in listOf("../../data/cache", "../../../../file-server/cache").filter { File(it).isDirectory }) {
            val library = CacheLibrary(cache)
            try {
                for (id in ObeliskOptionTool.LOCS) {
                    val loc = Rev667LocType.decode(id, library.data(ObeliskOptionTool.LOC_INDEX, id ushr 8, id and 0xFF)!!)
                    assertEquals(listOf("Activate", "Teleport to Destination", "Set Destination"), loc.options.take(3).toList(), "$cache loc $id")
                }
            } finally {
                library.close()
            }
        }
    }
}
