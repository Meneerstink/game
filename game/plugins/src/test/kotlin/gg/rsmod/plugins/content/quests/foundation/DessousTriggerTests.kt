package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * N211 (owner 2026-10-22): Dessous no longer rises when the player arrives; he rises when the blessed pot (4665) is used on the
 * Vampyre tomb (6437), and that pot is in the player's inventory for the step (before any trip to it).
 */
class DessousTriggerTests {
    @Test
    fun `the Dessous step is triggered by the blessed pot on the Vampyre tomb`() {
        val step = FoundationQuests.SHORT.flatMap { it.steps }.first { it.fight?.npc == Npcs.DESSOUS }
        val trigger = assertNotNull(step.fight!!.trigger)
        assertEquals(Items.BLESSED_POT_4665, trigger.item)
        assertEquals(DESSOUS_TOMB, trigger.obj)
        assertTrue(step.fight!!.tile.isWithinRadius(gg.rsmod.game.model.Tile(3568, 3401, 0), QuestFights.REACH), "the tomb is within the fight's reach")
    }

    @Test
    fun `no path auto-starts a triggered fight`() {
        val helper = File("src/main/kotlin/gg/rsmod/plugins/content/quests/foundation/QuestHelper.kt").readText()
        assertTrue("fight.trigger == null && QuestFights.active(player) == null" in helper, "the helper tick waits for the trigger")
        val quests = File("src/main/kotlin/gg/rsmod/plugins/content/quests/foundation/FoundationQuests.kt").readText()
        assertTrue("next.fight.trigger == null && next.npc == step.npc" in quests, "the next-step auto-start skips it")
        assertTrue("fight.trigger == null && player.tile.height == fight.tile.height" in quests, "talking to the step npc skips it")
        assertTrue("QuestFights.handTriggerItem(player, it)" in quests, "the pot is handed out when the step begins")
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/quests/foundation/quest_foundation.plugin.kts").readText()
        assertTrue("on_item_on_obj(obj = DESSOUS_TOMB, item = Items.BLESSED_POT_4665)" in plugin)
    }
}
