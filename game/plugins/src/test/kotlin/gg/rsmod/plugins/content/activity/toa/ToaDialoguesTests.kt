package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.messageBox
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ToaDialoguesTests {
    @Test fun `first vault introduction completes once and interrupted conversation remains available`() = runBlocking {
        val p = mockk<Player>(relaxed = true)
        every { p.attr } returns AttributeMap()
        val task = mockk<QueueTask>()
        mockkStatic("gg.rsmod.plugins.api.ext.QueueTaskExtKt")
        var interrupted = true
        var npcLines = 0
        var playerLines = 0
        try {
            coEvery { task.messageBox(*anyVararg()) } just Runs
            coEvery { task.chatNpc(*anyVararg(), npc = any(), facialExpression = any(), title = any(), wrap = any(), animationOverride = any()) } coAnswers {
                if (interrupted) throw IllegalStateException("dialogue interrupted")
                npcLines++
            }
            coEvery { task.chatPlayer(*anyVararg(), facialExpression = any(), title = any(), wrap = any()) } coAnswers { playerLines++ }
            try { ToaDialogues.vault(task, p, 15144); fail("expected interrupted dialogue") } catch (_: IllegalStateException) { }
            assertNull(p.attr[ToaDialogues.MET_OSMUMTEN])
            interrupted = false
            ToaDialogues.vault(task, p, 15144)
            assertEquals(true, p.attr[ToaDialogues.MET_OSMUMTEN])
            assertEquals(4, npcLines)
            assertEquals(3, playerLines)
            // An unclaimed reward gets one subsequent NPC line rather than replaying the entire first meeting.
            p.attr[ToaRewards.ESCROW] = ToaPersonalReward("reward", listOf(ToaRewardItem(995, 1))).encode()
            ToaDialogues.vault(task, p, 15144)
            assertEquals(5, npcLines)
            assertEquals(3, playerLines)
        } finally { unmockkStatic("gg.rsmod.plugins.api.ext.QueueTaskExtKt") }
    }
}
