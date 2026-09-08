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
 * Where the two Summoning panels are allowed to be drawn (owner failures F2 and F3), and, since
 * 2026-09-09, that the Follower Details tab button the owner asked for again is really wired.
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
     * Slot 95 itself still has no tab button baked into its own component - [FollowerDetailsTab]
     * reaches it by focusing the tab from an unrelated spare button elsewhere in the strip, not by
     * giving slot 95 a button of its own.
     */
    @Test
    fun `the Follower Details panel is mounted on the buttonless gameframe slot 95`() {
        assertEquals(SummoningUi.PANEL, InterfaceDestination.SUMMONING_TAB.interfaceId)
        assertEquals(221, InterfaceDestination.SUMMONING_TAB.fixedChildId)
        assertEquals(107, InterfaceDestination.SUMMONING_TAB.resizeChildId)
    }

    /**
     * The reverse of what this test used to assert. An earlier round armed the spare tab slot as
     * a Follower Details tab button, the owner rejected that outright, and this test was written
     * to keep it deleted. The owner has since asked for exactly that placement again, explicitly
     * and in this round (2026-09-09: "make sure to put the follower details always in the empty
     * tab ... if the player has no familiar active the follower details still needs to be visible
     * in the empty tab but it has to be empty") - a newer explicit owner decision supersedes the
     * older one it contradicts, so [FollowerDetailsTab] is back and this test now pins the
     * opposite fact: that it really is wired, in both layout modes, rather than only existing on
     * paper.
     */
    @Test
    fun `the Follower Details tab is armed as a real button in both layout modes`() {
        assertEquals(
            listOf(FIXED_PANE to FIXED_BUTTON, RESIZABLE_PANE to RESIZABLE_BUTTON),
            FollowerDetailsTab.buttons,
        )
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

        /** The Follower Details tab button, fixed gameframe: `548:99`, icon `548:107`. */
        private const val FIXED_PANE = 548
        private const val FIXED_BUTTON = 99

        /** The Follower Details tab button, resizable gameframe: `746:47`, icon `746:31`. */
        private const val RESIZABLE_PANE = 746
        private const val RESIZABLE_BUTTON = 47

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
