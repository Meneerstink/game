package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.tools.importer.QuestMapIconTool
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * N255 (owner 2026-10-23: "Aleck op de 2e verdieping ... moet toch ook een quest icoon hebben net als King Narnode"): every short
 * quest's start npc carries the hall quest icon (QuestMapIconTool writes it into the npc definitions), not only the first three.
 */
class FoundationQuestIconTests {
    @Test
    fun `every short quest's start npc carries the quest icon`() {
        assertEquals(FoundationQuests.SHORT.map { it.startNpc }.toSet(), QuestMapIconTool.QUEST_NPCS.toSet())
    }
}
