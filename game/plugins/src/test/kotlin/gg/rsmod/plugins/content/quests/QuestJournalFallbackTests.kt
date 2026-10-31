package gg.rsmod.plugins.content.quests

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.quests.impl.CooksAssistant
import gg.rsmod.plugins.content.quests.impl.DoricsQuest
import gg.rsmod.plugins.content.quests.impl.DruidicRitual
import gg.rsmod.plugins.content.quests.impl.ImpCatcher
import gg.rsmod.plugins.content.quests.impl.PriestInPeril
import gg.rsmod.plugins.content.quests.impl.TheRestlessGhost
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * N475 (2026-10-31, live log): the journal of a quest not started yet (stage 0) hit `else -> TODO()` in six quests and the Quest
 * tab click did nothing. buildQuestStages falls back to the quest's start page instead of failing.
 */
class QuestJournalFallbackTests {
    private val quests = listOf(CooksAssistant, DoricsQuest, DruidicRitual, ImpCatcher, PriestInPeril, TheRestlessGhost)

    @Test
    fun `the six quests have no page for stage 0 - the case the fallback covers`() {
        val player = mockk<Player>(relaxed = true)
        quests.forEach { quest -> assertFailsWith<NotImplementedError>(quest.name) { quest.getObjective(player, 0) } }
    }

    @Test
    fun `opening the journal of an unstarted quest never fails`() {
        quests.forEach { quest ->
            val player = mockk<Player>(relaxed = true)
            player.buildQuestStages(quest)
        }
    }
}
