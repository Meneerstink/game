package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N212 (owner 2026-10-22: "protected status laat ook zien in de intro?" - hide and pause it): a new player's 60 minutes of beginner
 * protection start when the introduction is over, not at account creation, so the intro shows no "Protected" row and costs no time.
 */
class BeginnerProtectionIntroTests {
    @Test
    fun `no protection row before the intro is over, the full 60 minutes after it`() {
        val player = player()
        assertFalse(BeginnerProtection.isProtected(player))
        assertNull(DeadmanHud.graceText(player), "during the intro: no Protected row")

        BeginnerProtection.grantAfterIntro(player)
        assertTrue(BeginnerProtection.isProtected(player))
        assertEquals("60:00", DeadmanHud.graceText(player))
    }

    @Test
    fun `account creation no longer starts the timer, the intro's end does - once`() {
        val kit = File("src/main/kotlin/gg/rsmod/plugins/content/starter/starter_kit.plugin.kts").readText()
        assertFalse("BeginnerProtection." in kit, "the first login does not start the protection")

        val intro = File("src/main/kotlin/gg/rsmod/plugins/content/quests/foundation/TutorIntro.kt").readText()
        val finish = intro.substringAfter("fun finish(player: Player, interrupted: Boolean)")
        assertTrue("if (firstTime) gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection.grantAfterIntro(player)" in finish,
            "finished, skipped or interrupted - the first time only")
    }

    private fun player(): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.timers } returns TimerMap()
        every { p.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        return p
    }
}
