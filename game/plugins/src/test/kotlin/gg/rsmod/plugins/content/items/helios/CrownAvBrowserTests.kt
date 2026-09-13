package gg.rsmod.plugins.content.items.helios

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Paths
import java.util.TreeSet

/** RCV-010 D1: the Crown AV browser steps only through ids that exist in the real 667 cache. */
class CrownAvBrowserTests {
    @Test
    fun `stepping lands only on existing ids in both directions`() {
        val ids = TreeSet(listOf(3, 4, 9, 30))
        assertEquals(3, CrownAvBrowser.step(ids, 0, 0))
        assertEquals(9, CrownAvBrowser.step(ids, 4, 1))
        assertEquals(4, CrownAvBrowser.step(ids, 9, -1))
        assertEquals(30, CrownAvBrowser.step(ids, 9, 10))
        assertEquals(9, CrownAvBrowser.step(ids, 30, -10))
        assertNull(CrownAvBrowser.step(ids, 30, 1))
        assertNull(CrownAvBrowser.step(ids, 3, -1))
    }

    @Test
    fun `every browse source is populated from this cache and contains ids already used in content`() {
        val anims = CrownAvBrowser.ids(DEFINITIONS, STORE, CrownAvBrowser.AvSource.ANIMATION)
        val spotAnims = CrownAvBrowser.ids(DEFINITIONS, STORE, CrownAvBrowser.AvSource.SPOT_ANIM)
        val sounds = CrownAvBrowser.ids(DEFINITIONS, STORE, CrownAvBrowser.AvSource.SYNTH_SOUND)
        // Overload animation 3170 / graphic 560 and Summoning special sound 4161 are real ids used by content.
        assertTrue("animations: ${anims.size}", anims.size > 1000 && 3170 in anims)
        assertTrue("spot anims: ${spotAnims.size}", spotAnims.size > 1000 && 560 in spotAnims)
        assertTrue("synth sounds: ${sounds.size}", sounds.size > 1000 && 4161 in sounds)
        listOf(anims, spotAnims, sounds).forEach { set ->
            var id = CrownAvBrowser.step(set, 0, 0)
            repeat(25) {
                assertTrue(id != null && id in set)
                id = CrownAvBrowser.step(set, id!!, 1)
            }
        }
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var STORE: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            STORE = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(STORE)
        }
    }
}
