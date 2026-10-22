package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanHud
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-22 (testrun N199/N154): during the intro the Deadman box showed the new player's own "3-17" under a fight of two
 * high-level players - it shows the scene's bracket, combat 88 (74-102); and N154: the opening line's box stayed open as
 * "Please wait..." over the chase in fixed mode.
 */
class IntroHudAndDialogTests {
    @Test
    fun `the Deadman box shows the scene's 74-102 during the intro and the player's own bracket after it`() {
        val player = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        every { player.attr } returns attrs
        every { player.combatLevel } returns 3

        attrs[TutorIntro.ACTIVE] = true
        assertEquals("74-102", DeadmanHud.bracketText(DeadmanHud.hudCombatLevel(player)))
        attrs.remove(TutorIntro.ACTIVE)
        assertEquals("3-17", DeadmanHud.bracketText(DeadmanHud.hudCombatLevel(player)))
    }

    @Test
    fun `the opening line's box is closed once it is clicked`() {
        val scene = File("src/main/kotlin/gg/rsmod/plugins/content/quests/foundation/IntroGuardScene.kt").readText()
        val line = scene.indexOf("a skull into it. Watch.")
        val close = scene.indexOf("player.closeComponent(parent = 752, child = 13)", line)
        assertTrue(line >= 0 && close > line && close < scene.indexOf("\"cine|1\"", line), "closed after the line, before the scene opens")
    }
}
