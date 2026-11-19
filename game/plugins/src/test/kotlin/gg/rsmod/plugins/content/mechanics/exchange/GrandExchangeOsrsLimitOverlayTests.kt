package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import com.google.gson.Gson
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N577 (owner 2026-11-19): the OSRS buy-limit overlay is applied on top of the Void table and only names real, tradeable, unnoted items. */
class GrandExchangeOsrsLimitOverlayTests {
    private class OverlayRow(val id: Int = -1, val limit: Int = 0, val name: String = "")

    private fun norm(text: String) = text.lowercase().replace(Regex("\\s*\\("), "(").replace(Regex("\\s+"), " ").trim()

    private val overlay: List<OverlayRow> =
        File(CFG, "buy-limits-osrs.json").reader().use { Gson().fromJson(it, Array<OverlayRow>::class.java).toList() }

    @Test
    fun `every overlay row is a tradeable unnoted item of that name with a positive limit`() {
        assertTrue(overlay.size > 3000, "overlay has ${overlay.size} rows")
        val wrong =
            overlay.mapNotNull { row ->
                val def = DEFINITIONS.getNullable(ItemDef::class.java, row.id) ?: return@mapNotNull "${row.id} ${row.name}: absent"
                when {
                    row.limit <= 0 -> "${row.id} ${row.name}: limit ${row.limit}"
                    def.noted -> "${row.id} ${row.name}: noted"
                    row.id !in TRADEABLE -> "${row.id} ${row.name}: not tradeable"
                    norm(NAMES[row.id] ?: def.name) != norm(row.name) -> "${row.id} overlay='${row.name}' items.yml='${NAMES[row.id]}' cache='${def.name}'"
                    else -> null
                }
            }
        assertTrue(wrong.isEmpty(), "${wrong.size} rows:\n" + wrong.take(40).joinToString("\n"))
        assertEquals(overlay.size, overlay.map { it.id }.toSet().size, "no id twice")
    }

    @Test
    fun `N577 quest-era items that OSRS lists on the Grand Exchange are tradeable here too`() {
        val flipped =
            listOf(
                23745, 23744, 5496, 6891, 5497, 4687, 30, 5499, 3678, 4668, 10999, 28, 4014, 5498, 5502, 5501, 4689, 5500, 10496, 7468, 3107,
                1969, 14589, 5371, 4016, 2164, 9522, 9524, 6752, 6750, 5503, 5374, 5372, 5370, 5373, 1573, 272, 4012,
            )
        val still = flipped.filter { it !in TRADEABLE }
        assertTrue(still.isEmpty(), "still untradeable: $still")
    }

    @Test
    fun `load applies the overlay over the Void limit and keeps Void rows the overlay does not name`() {
        val saved = GeBuyLimits.table()
        try {
            GeBuyLimits.load(File(CFG, "buy-limits.json"), File(CFG, "buy-limits-osrs.json"))
            val merged = GeBuyLimits.table()
            overlay.forEach { assertEquals(it.limit, merged[it.id], "item ${it.id} ${it.name} takes the OSRS limit") }
            assertEquals(70, merged[Items.ABYSSAL_WHIP], "Abyssal whip: OSRS limit 70")
            val void = File(CFG, "buy-limits.json").reader().use { Gson().fromJson(it, Array<OverlayRow>::class.java).toList() }
            val named = overlay.map { it.id }.toSet()
            void.filter { it.id !in named }.forEach { assertEquals(it.limit, merged[it.id], "Void row ${it.id} untouched") }
            GeBuyLimits.load(File(CFG, "buy-limits.json"))
            assertEquals(void.size, GeBuyLimits.table().size, "without an overlay file the table is exactly the Void table")
        } finally {
            GeBuyLimits.set(saved)
        }
    }

    companion object {
        private val CFG = Paths.get("..", "..", "data", "cfg", "ge").toFile()
        private val DEFINITIONS = DefinitionSet()

        /** Ids with `tradeable: true` in data/cfg/items.yml (the cache definition does not carry the flag). */
        private val NAMES = HashMap<Int, String>()
        private val TRADEABLE: Set<Int> by lazy {
            val ids = HashSet<Int>()
            var cur = -1
            File(Paths.get("..", "..", "data", "cfg", "items.yml").toString()).forEachLine { line ->
                val t = line.trim()
                if (line.startsWith("- id:")) cur = line.substringAfter(":").trim().toInt()
                else if (t == "tradeable: true") ids += cur
                else if (t.startsWith("name:") && cur !in NAMES) NAMES[cur] = t.removePrefix("name:").trim().trim('"')
            }
            ids
        }
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
