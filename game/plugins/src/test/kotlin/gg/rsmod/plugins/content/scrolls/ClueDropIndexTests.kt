package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.watson.MimicRewardView
import gg.rsmod.plugins.content.quests.tab.DropSources
import io.mockk.*
import kotlin.test.*

class ClueDropIndexTests {
    @Test fun `item search refreshes personal casket Mimic and jar chances for the second account`() {
        val world = mockk<World>(relaxed = true)
        val full = mockk<Player>(relaxed = true)
        val second = mockk<Player>(relaxed = true)
        every { full.world } returns world; every { second.world } returns world
        fun receipt(player: Player) = DropSources.View(emptyList(), listOf(DropSources.Line("clue", 995, "Coins", "1",
            if (player === full) 0.0 else 1.0 / 128, 1.0 / 128)))
        mockkObject(ClueRewardView, MimicRewardView, ClueImplingView)
        try {
            every { ClueRewardView.view(any(), any(), any(), any()) } answers { receipt(firstArg()) }
            every { MimicRewardView.view(any(), any()) } answers { receipt(firstArg()) }
            every { ClueImplingView.view(any(), any()) } answers { receipt(firstArg()) }
            val sources = listOf(
                DropSources.Source("clue_easy", "Easy", -1, intArrayOf(), 0, DropSources.Group.CLUES, DropSources.Kind.CLUE),
                DropSources.Source("mimic_master", "Mimic", -1, intArrayOf(), 0, DropSources.Group.BOSSES, DropSources.Kind.MIMIC),
                DropSources.Source("jar", "Jar", -1, intArrayOf(), 0, DropSources.Group.CLUES, DropSources.Kind.IMPLING_JAR,
                    modelItem = gg.rsmod.plugins.content.skills.hunter.ImplingData.byNpc(Npcs.GOURMET_IMPLING)!!.jarItem),
            )
            val ordinary = DropSources.Source("ordinary", "Ordinary", -1, intArrayOf(), 0, DropSources.Group.MONSTERS,
                DropSources.Kind.NPC, tableNpc = Int.MAX_VALUE - 822)
            val original = DropSources.ItemIndex(mapOf(995 to "Coins"), mapOf(995 to
                (sources.map { Triple(it, 0.0, 1.0 / 128) } + Triple(ordinary, 0.5, 0.5))))
            val refreshed = DropSources.personalWildernessIndex(second, original, 1.0, sources + ordinary)
            for (source in sources) {
                assertEquals(1.0 / 128, refreshed.sources.getValue(995).single { it.first === source }.second)
                assertEquals(0.0, original.sources.getValue(995).single { it.first === source }.second)
            }
            assertEquals(0.5, refreshed.sources.getValue(995).single { it.first === ordinary }.second)
            val fullAgain = DropSources.personalWildernessIndex(full, refreshed, 1.0, sources + ordinary)
            assertTrue(fullAgain.sources.getValue(995).filter { it.first !== ordinary }.all { it.second == 0.0 })
        } finally { unmockkObject(ClueRewardView, MimicRewardView, ClueImplingView) }
    }
}
