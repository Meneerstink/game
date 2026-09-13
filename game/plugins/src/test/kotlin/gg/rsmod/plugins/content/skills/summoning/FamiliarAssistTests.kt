package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.combat.setCombatTarget
import io.mockk.every
import io.mockk.mockk
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class FamiliarAssistTests {
    @Test
    fun `all fighting familiar policies defend an idle owner only during an active attack`() {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        val attacker = mockk<Npc>(relaxed = true)
        every { attacker.isAlive() } returns true
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(attacker)
        for (pouch in SummoningPouchData.values) {
            val mode = SummoningCombatDefinitions.getByNpc(pouch.npc)!!.assistMode
            player.timers[ACTIVE_COMBAT_TIMER] = 10
            val target = FamiliarCombat.ownerAssistTarget(player, mode)
            if (mode == FamiliarAssistMode.NONE) assertNull(target, pouch.name)
            else assertSame(attacker, target, pouch.name)
            player.timers.remove(ACTIVE_COMBAT_TIMER)
            assertNull(FamiliarCombat.ownerAssistTarget(player, mode), pouch.name)
        }
    }

    @Test
    fun `defensive familiar does not assist an unprovoked attack and ignores dead attackers`() {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        val target = mockk<Npc>(relaxed = true)
        every { target.isAlive() } returns true
        player.setCombatTarget(target)
        assertSame(target, FamiliarCombat.ownerAssistTarget(player, FamiliarAssistMode.ASSIST))
        assertNull(FamiliarCombat.ownerAssistTarget(player, FamiliarAssistMode.DEFENSIVE_ONLY))
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(target)
        player.timers[ACTIVE_COMBAT_TIMER] = 10
        every { target.isAlive() } returns false
        FamiliarAssistMode.values().forEach { assertNull(FamiliarCombat.ownerAssistTarget(player, it)) }
    }
}
