package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.plugins.content.quests.tab.N108Ui
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-30 (V3): an "Upgraded <name>" too wide for the Follower Details name bar (662:54, 165 px in the small font) reads
 * "Upgr. <name>" there; every other follower name stays whole. The owner named three; the healthy tooth creature is the fourth
 * (its decaying/rotten forms fit). Measured with the production cache's own p11 glyph widths.
 */
class FollowerBarNameTests {
    companion object {
        /** The N423 "Upgraded <name>" npc copies (pet_upgrades.tsv). */
        private val UPGRADED = 14672..15051

        private val world: World by lazy {
            val w = mockk<World>(relaxed = true)
            every { w.filestore } returns CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            w
        }

        private fun name(id: Int) = SummoningTestCache.definitions.getNullable(NpcDef::class.java, id)?.name.orEmpty()
    }

    @Test
    fun `the names too wide for the bar say Upgr and fit whole`() {
        val long = UPGRADED.map(::name).filter { N108Ui.p11(world, it) > 165 }.sorted()
        assertEquals(
            listOf(
                "Upgraded Dagannoth Supreme Jr.",
                "Upgraded Elidinis' Damaged Guardian",
                "Upgraded Tooth creature (healthy)",
                "Upgraded Tumeken's Damaged Guardian",
            ),
            long,
        )
        for (n in long) {
            val bar = SummoningUi.barName(world, n)
            assertTrue(N108Ui.p11(world, bar) <= 165, "$bar is ${N108Ui.p11(world, bar)} px")
            // "Upgr. Tumeken's Damaged Guardian" is still 176 px - wider than the bar graphic itself - so it alone keeps the "..."
            // rule (owner question V5); the other three fit whole.
            if (n == "Upgraded Tumeken's Damaged Guardian") {
                assertEquals("Upgr. Tumeken's Damaged Guar...", bar)
            } else {
                assertEquals("Upgr. " + n.removePrefix("Upgraded "), bar)
            }
        }
    }

    @Test
    fun `every other upgraded name and the plain pet name stays whole`() {
        for (id in UPGRADED) {
            val n = name(id)
            if (N108Ui.p11(world, n) > 165) continue
            assertEquals(n, SummoningUi.barName(world, n))
        }
        assertEquals("Tumeken's Damaged Guardian", SummoningUi.barName(world, "Tumeken's Damaged Guardian"))
    }
}
