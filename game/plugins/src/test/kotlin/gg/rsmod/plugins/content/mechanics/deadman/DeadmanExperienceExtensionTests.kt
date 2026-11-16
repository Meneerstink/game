package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.ExperienceLocks
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class DeadmanExperienceExtensionTests {
    @Test
    fun `locked combat XP returns before inspecting or consuming brawling gloves`() {
        val player = mockk<Player>()
        every { player.attr } returns AttributeMap()
        ExperienceLocks.setCombatDisabled(player, true)
        assertEquals(1.0, player.addXp(Skills.ATTACK, 100.0, checkBrawlingGloves = true))
        // Strict mock: any equipment/tile/XP member access would fail before these assertions.
        verify(exactly = 0) { player.equipment }
        verify(exactly = 0) { player.addXp(any(), any(), any(), any()) }
    }
}
