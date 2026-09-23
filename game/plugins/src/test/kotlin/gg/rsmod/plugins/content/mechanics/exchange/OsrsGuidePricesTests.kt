package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-012 decision "GE guide price seeds = OSRS values": every exchangeable 667 item starts at the OSRS Wiki GEPrices snapshot price of
 * the same-named item, and only items OSRS does not have keep the cache value. The whole exchangeable roster is checked.
 */
class OsrsGuidePricesTests {
    private val table = OsrsGuidePrices.load(Paths.get("..", "..", "data", "cfg", "ge", "osrs-ge-guide-prices.json").toFile())
    private var library: CacheLibrary? = null

    @AfterTest
    fun close() {
        library?.close()
    }

    @Test
    fun `the snapshot is the wiki GEPrices module stored verbatim`() {
        assertEquals("13 September 2026 06:31:59 (UTC)", table.snapshot)
        assertEquals(4564, table.byName.size, "item entries (the two %LAST_UPDATE% keys excluded)")
        assertEquals(809253, table.price("Abyssal whip"))
        assertEquals(97, table.price("Lobster"))
        assertEquals(3719, table.price("Dragon bones"))
        assertEquals(38315, table.price("Rune platebody"))
        assertEquals(table.price("Abyssal whip"), table.price("abyssal WHIP"), "names match ignoring case")
        assertEquals(null, table.price("Coins"))
        assertEquals(109, table.price("Strength potion (4)"), "667 dose suffix spacing matches OSRS Strength potion(4)")
        assertEquals(6832, table.price("Prayer potion (3)"))
        assertEquals(null, table.price(""), "nameless items are never seeded")
        assertEquals(null, table.price("Cannonball"), "OSRS renames are not aliased")
    }

    @Test
    fun `every exchangeable item is seeded from the OSRS price of its name, otherwise the cache value`() {
        val lib = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()).also { library = it }
        val definitions = DefinitionSet()
        definitions.load(lib, ItemDef::class.java)
        // The server applies `tradeable` from items.yml at boot (same loader as GrandExchangeInterfaceTests).
        var id = -1
        Paths.get("..", "..", "data", "cfg", "items.yml").toFile().forEachLine { line ->
            when {
                line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                line.startsWith("  tradeable: ") && id >= 0 ->
                    definitions.getNullable(ItemDef::class.java, id)?.tradeable = line.removePrefix("  tradeable: ").trim() == "true"
            }
        }
        @Suppress("UNCHECKED_CAST")
        val items = (definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>).values.filter { GrandExchangeInterface.exchangeable(it) }
        val unmatched = mutableListOf<String>()
        var matched = 0
        items.sortedBy { it.id }.forEach { def ->
            val osrs = table.price(def.name)
            assertEquals(osrs ?: def.cost, OsrsGuidePrices.seed(def, table), "${def.id} ${def.name}")
            if (osrs != null) matched++ else unmatched += "${def.id}\t${def.name}\t${def.cost}"
        }
        File("build").mkdirs()
        File("build/osrs-guide-price-unmatched.txt").writeText(unmatched.joinToString("\n"))
        println("OSRS_GUIDE_PRICES exchangeable=${items.size} matched=$matched unmatched=${unmatched.size}")
        val whip = items.first { it.id == Items.ABYSSAL_WHIP }
        assertEquals(809253, OsrsGuidePrices.seed(whip, table))
        // 2026-09-14 batch potions-antifire added 14 tradeable doses (Extended antifire, the mixes, Extended super antifire), all named as
        // in the OSRS snapshot: exchangeable 12192 -> 12206, seeded 3272 -> 3286, unmatched unchanged at 8920.
        // 2026-09-16 OSRS item parity batch added three tradeable unnoted items, all named as in the OSRS snapshot:
        // Trouver parchment (23730), Incomplete heavy ballista (23731), Unstrung heavy ballista (23733):
        // exchangeable 12206 -> 12209, seeded 3286 -> 3289, unmatched unchanged at 8920.
        // 2026-09-17 OSRS import run added 21 tradeable unnoted items (batch demonbane: Burning claws, Burning claw; batch runes: Wrath rune,
        // Aether rune, Aether catalyst, Smoke/Mist/Dust battlestaff, Mystic mist/dust staff; batch kits: 7 ornament kits, 2 whip mixes,
        // 2 staff upgrade kits), all named as in the OSRS snapshot: exchangeable 12209 -> 12230, seeded 3289 -> 3310, unmatched unchanged at 8920.
        // 2026-09-17 batch kits2 added 6 more (4 dark bow paints, Dragon pickaxe upgrade kit, Zalcano shard), all in the OSRS snapshot: 12230 -> 12236, seeded 3310 -> 3316.
        // 2026-09-19 batch deadman-breach added 5 (Chitin, Trinkets of fairies / avarice / undead / fortuity (inactive)), all in the OSRS
        // snapshot: 12236 -> 12241, seeded 3316 -> 3321. Rocktail removal then removed three unnoted tradeable definitions
        // from the legacy cache route; the Anglerfish import adds one back: 12238 -> 12239 and 3321 -> 3322.
        // 2026-09-19 batch blighted-overload added the 4 doses (23830/23832/23834/23836), all in the OSRS snapshot: 12239 -> 12243,
        // seeded 3322 -> 3326.
        // 2026-09-19 batch owner0919 added 10 tradeable unnoted items (mixed hide set, Spiked manacles, the 5 burning amulet
        // charges): 12243 -> 12253; the OSRS snapshot names 6 of them (the amulet only at (5)): 3326 -> 3332.
        // 2026-09-23 batch emblems added Archaic emblem (tier 5) (23857, tradeable, in the OSRS snapshot): 12253 -> 12254, seeded 3332 -> 3333.
        assertEquals(12254, items.size, "exchangeable items (tradeable, unnoted, not coins)")
        assertEquals(3333, matched, "items seeded from the 13 September 2026 OSRS snapshot; the unmatched list is build/osrs-guide-price-unmatched.txt")
    }

    @Test
    fun `the Grand Exchange selection uses the OSRS seed before any trade`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/exchange/GrandExchangeInterface.kt").readText()
        assertTrue("service.guidePrice(real.id, OsrsGuidePrices.seed(real))" in source)
    }
}
