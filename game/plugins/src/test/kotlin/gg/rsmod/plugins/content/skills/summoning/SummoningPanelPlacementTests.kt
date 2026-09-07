package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.InterfaceHookProbeTool
import gg.rsmod.plugins.api.InterfaceDestination
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Where the two Summoning panels are allowed to be drawn (owner failures F2 and F3).
 *
 * The owner reported the Follower Details panel in the wrong place and the "Select left-click
 * option" selector opening as a "fake/custom GUI". Neither is a matter of taste: the cache states
 * the size of every one of these interfaces, and a panel built at the sidebar's dimensions is a
 * sidebar panel. These tests read those dimensions out of the production cache so the placement
 * decision is pinned to evidence rather than to a comment that can rot.
 */
class SummoningPanelPlacementTests {
    /**
     * The sidebar panel region. Probing the resizable gameframe shows every tab slot - the Skills
     * tab at 746:92, the Summoning panel slot 95 at 746:107, and the rest - sharing parent 89 and
     * this exact box. It is the region directly beneath the tab row.
     */
    private val sidebar = 190 to 261

    @Test
    fun `the Follower Details panel is built at the sidebar's dimensions`() {
        assertEquals(sidebar, largestBox(SummoningUi.PANEL))
    }

    /**
     * The whole of F3. Interface 880 is genuine cache content - eight `op1='Select'` rows and a
     * `Confirm Selection` button - but it is a **sidebar** panel, and it was being opened as a
     * modal across the game view. A 190x261 interface stretched over the main screen is what read
     * as a bolted-on custom window.
     */
    @Test
    fun `the left-click selector is built at the sidebar's dimensions, not the main screen's`() {
        assertEquals(sidebar, largestBox(LEFT_CLICK_SELECTOR))
    }

    /**
     * The Skills tab, as the reference point the owner described the location against: the
     * Follower Details panel occupies the same region the Skills tab does.
     */
    @Test
    fun `the Skills tab occupies the same region as the Follower Details panel`() {
        assertEquals(largestBox(SKILLS_TAB_INTERFACE), largestBox(SummoningUi.PANEL))
    }

    /**
     * Slot 95 is the point of the whole arrangement: a gameframe panel region with **no tab
     * button of its own**. It is why the Follower Details panel can satisfy "no separate tab, no
     * separate follower tab button" and still be shown somewhere real.
     */
    @Test
    fun `the Follower Details panel is mounted on the buttonless gameframe slot 95`() {
        assertEquals(SummoningUi.PANEL, InterfaceDestination.SUMMONING_TAB.interfaceId)
        assertEquals(221, InterfaceDestination.SUMMONING_TAB.fixedChildId)
        assertEquals(107, InterfaceDestination.SUMMONING_TAB.resizeChildId)
    }

    /**
     * The rejected solution must stay deleted. An earlier run armed the spare tab slot as a
     * Follower Details tab button; the owner rejected that outright. Nothing in the Summoning
     * package may reference those components again.
     */
    @Test
    fun `no Summoning source arms the spare tab slot as a follower tab button`() {
        val sources =
            Paths
                .get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content", "skills", "summoning")
                .toFile()
                .walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
                .toList()
        assertTrue(sources.isNotEmpty(), "no Summoning sources found to scan")
        sources.forEach { file ->
            val text = file.readText()
            SPARE_TAB_COMPONENTS.forEach { component ->
                assertTrue(
                    !text.contains(component),
                    "${file.name} still references the rejected spare-tab component $component",
                )
            }
        }
    }

    /**
     * The largest box any component of [interfaceId] is baked at, which is the region the
     * interface is designed to fill.
     *
     * Component 0 is deliberately not used for this: it is only sometimes the outermost box.
     * Interface 880 bakes `190x261` on component 0, but interface 320 bakes `0x0` there and puts
     * the real `190x261` on 320:203, and 662 puts it on 662:72. Taking the maximum asks the
     * question that actually matters - how big is this thing meant to be - without depending on
     * which slot a particular interface happens to keep its outer layer in.
     */
    private fun largestBox(interfaceId: Int): Pair<Int, Int> {
        val archive = store.index(ARCHIVE_INTERFACES).archive(interfaceId)
        assertNotNull(archive, "interface $interfaceId is absent from the cache")
        val boxes =
            archive!!.fileIds().sorted().mapNotNull { slot: Int ->
                val data = store.data(ARCHIVE_INTERFACES, interfaceId, slot)
                if (data == null) null else InterfaceHookProbeTool.componentSize(data)
            }
        assertTrue(boxes.isNotEmpty(), "interface $interfaceId decoded no components")
        return boxes.maxByOrNull { it.first * it.second }!!
    }

    companion object {
        private const val ARCHIVE_INTERFACES = 3

        /** Interface 880 - "Select left-click option". */
        private const val LEFT_CLICK_SELECTOR = 880

        /** Interface 320 - the Skills tab. */
        private const val SKILLS_TAB_INTERFACE = 320

        /**
         * The spare tab button and icon pairs an earlier run armed, in both layout modes:
         * `548:99` / `548:107` and `746:47` / `746:31`.
         */
        private val SPARE_TAB_COMPONENTS = listOf("FollowerDetailsTab", "FIXED_BUTTON", "RESIZABLE_BUTTON")

        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        }

        @AfterClass
        @JvmStatic
        fun closeCache() {
            store.close()
        }
    }
}
