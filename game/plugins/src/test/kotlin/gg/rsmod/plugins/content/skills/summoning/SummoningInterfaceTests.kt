package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.EnumDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.plugins.api.InterfaceDestination
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the Summoning interface contract against the real cache.
 *
 * The tab (662) and orb (747) are rendered entirely by the cache's own cs2 off a small set of
 * vars, so if any of those var definitions move, the server keeps writing values that no longer
 * mean what it thinks they mean and the whole HUD silently goes wrong with nothing failing. These
 * assertions turn that into a test failure. Evidence trail:
 * `C:\RSPS\RSPS_SUMMONING_2011_EVIDENCE.md`.
 */
class SummoningInterfaceTests {
    @Test
    fun `familiar time varbits live in varp 1176 where script 752 reads them`() {
        val minutes = DEFINITIONS.get(VarbitDef::class.java, 4534)
        assertEquals(1176, minutes.varp)
        assertEquals(7, minutes.startBit)
        assertEquals(31, minutes.endBit)

        val halfMinute = DEFINITIONS.get(VarbitDef::class.java, 4290)
        assertEquals(1176, halfMinute.varp)
        assertEquals(6, halfMinute.startBit)
        assertEquals(6, halfMinute.endBit)
    }

    @Test
    fun `left-click varbits live in the varps 747 and 880 listen to`() {
        val active = DEFINITIONS.get(VarbitDef::class.java, 6454)
        assertEquals(1493, active.varp)
        assertEquals(0, active.startBit)
        assertEquals(3, active.endBit)

        val preview = DEFINITIONS.get(VarbitDef::class.java, 6455)
        assertEquals(1494, preview.varp)
        assertEquals(0, preview.startBit)
        assertEquals(3, preview.endBit)
    }

    /**
     * Script 751 resolves the familiar the tab draws with `ENUM(obj -> npc, 1320, varp 448)`. If
     * this server's own roster disagrees with that enum, the tab would draw one familiar while the
     * server spawned a different one.
     */
    @Test
    fun `every pouch in the roster maps to the same npc the cache's own enum 1320 does`() {
        val enum = DEFINITIONS.get(EnumDef::class.java, 1320)
        assertNotNull(enum)
        var checked = 0
        val mismatches = mutableListOf<String>()
        SummoningPouchData.values.forEach { pouch ->
            val cacheNpc = enum.values[pouch.pouch] as? Int ?: return@forEach
            checked++
            if (cacheNpc != pouch.npc) {
                mismatches += "${pouch.name}: pouch ${pouch.pouch} -> roster ${pouch.npc}, cache $cacheNpc"
            }
        }
        assertTrue(checked >= 50, "enum 1320 should cover most of the roster, only matched $checked")
        assertEquals(emptyList<String>(), mismatches)
    }

    /**
     * The Familiar Inventory grid: 671:27 is a bare layer, and the six vertical divider graphics
     * (671:20..25) plus four horizontal ones (671:16..19) are what prove it is 6 columns by 5 rows
     * - i.e. exactly the 30 cells of the largest beast of burden.
     */
    @Test
    fun `the familiar inventory grid is thirty cells and its window components exist`() {
        val window = LIBRARY.index(3).archive(FamiliarInventory.WINDOW_INTERFACE)
        assertNotNull(window)
        val slots = window.fileIds().toSet()
        listOf(
            FamiliarInventory.GRID_COMPONENT,
            FamiliarInventory.CLOSE_COMPONENT,
            FamiliarInventory.TAKE_BOB_COMPONENT,
        ).forEach { assertTrue(it in slots, "interface 671 should have component $it") }

        val side = LIBRARY.index(3).archive(FamiliarInventory.SIDE_INTERFACE)
        assertNotNull(side)
        assertEquals(1, side.fileIds().size, "665 is the single-layer container interface")
    }

    /**
     * The follower panel is gameframe **slot 95**, not one of the sixteen numbered sidebar tabs.
     * `disasm 8` maps slot 95 to 548:221 / 746:107, `disasm 2457` (the orb's real "Follower
     * Details" onOp) refuses to do anything unless a sub-interface is mounted there, and
     * `disasm 1364` keeps the orb's entire familiar-option layer 747:8 hidden on the same
     * condition. Mounting it on slot 8 instead - the spare minigame tab, whose button carries no
     * baked op1 and whose icon is baked hidden - is what left the owner with no Summoning GUI and
     * an orb menu containing nothing but "Select left-click option". Pinned so it cannot regress.
     */
    @Test
    fun `the follower panel is mounted on gameframe slot 95, and Tabs agrees`() {
        assertEquals(662, InterfaceDestination.SUMMONING_TAB.interfaceId)
        assertEquals(221, InterfaceDestination.SUMMONING_TAB.fixedChildId)
        assertEquals(107, InterfaceDestination.SUMMONING_TAB.resizeChildId)
        assertEquals(95, Tabs.SUMMONING)
        assertTrue(
            InterfaceDestination.values.none {
                it != InterfaceDestination.SUMMONING_TAB && it.fixedChildId == 221
            },
            "548:221 is slot 95 and belongs to the follower panel alone",
        )
    }

    /**
     * varbit 4280 gates the orb's familiar-option layer in `disasm 1364`, and varbit 4288 is the
     * "(<n> Special Move points)" cost the follower panel prints. Both are written by the server,
     * so their varp/bit ranges are part of the contract.
     */
    @Test
    fun `the summoning-unlock and special-cost varbits are where the gameframe reads them`() {
        val unlocked = DEFINITIONS.get(VarbitDef::class.java, 4280)
        assertEquals(1160, unlocked.varp)
        assertEquals(23, unlocked.startBit)
        assertEquals(23, unlocked.endBit)

        val cost = DEFINITIONS.get(VarbitDef::class.java, 4288)
        assertEquals(1175, cost.varp)
        assertEquals(23, cost.startBit)
        assertEquals(27, cost.endBit)
    }

    /**
     * Every scroll the server can bind a special move to must have the 2011 Knowledge Base name and
     * description the panel prints, and its cost must fit varbit 4288's five bits.
     */
    @Test
    fun `every bound special move has sourced panel text that fits the cost varbit`() {
        val missing =
            SummoningSpecialMoves.bindings
                .flatMap { it.scrolls }
                .distinct()
                .filter { SummoningSpecialMoveText[it] == null }
                .map { it.name }
        assertEquals(emptyList<String>(), missing, "special moves with no sourced panel text")

        SummoningSpecialMoveText.entries.forEach { (scroll, text) ->
            assertTrue(text.move.isNotBlank(), "${scroll.name} has a blank special-move name")
            assertTrue(text.description.isNotBlank(), "${scroll.name} has a blank description")
            assertTrue(
                scroll.specialPoints in 0..31,
                "${scroll.name} costs ${scroll.specialPoints}, which does not fit varbit 4288",
            )
        }
    }

    /** No beast of burden may exceed the 30 cells the real window can draw. */
    @Test
    fun `no carrier capacity exceeds the real thirty cell grid`() {
        SummoningPouchData.values.forEach { pouch ->
            val storage = BeastOfBurden.storageFor(pouch) ?: return@forEach
            assertTrue(
                storage.key.capacity <= 30,
                "${pouch.name} carries ${storage.key.capacity}, more than interface 671 can draw",
            )
        }
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        /**
         * Only the two definition types these assertions actually read. A full `loadAll` here
         * pushed the shared 2g test JVM over its heap limit (see `game/plugins/build.gradle`:
         * several content tests already hold a full cache-backed `DefinitionSet` each), which
         * surfaced as an unrelated `NewPlayerStartTests` OOM.
         */
        @BeforeClass
        @JvmStatic
        fun loadDefinitions() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.load(LIBRARY, VarbitDef::class.java)
            DEFINITIONS.load(LIBRARY, EnumDef::class.java)
        }
    }
}
