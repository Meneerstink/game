package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.cfg.Npcs
import io.mockk.every
import io.mockk.mockk
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-09-17: the Deadman 7-second countdown (logout and teleport) only when skulled or in
 * combat, and never for a fight with a boss.
 */
class DeadmanTimerGateTests {
    @Test
    fun `an unskulled player out of combat gets no countdown`() {
        assertFalse(DeadmanTimerGate.needsCountdown(newPlayer()))
    }

    @Test
    fun `a skulled player always gets the countdown`() {
        assertTrue(DeadmanTimerGate.needsCountdown(newPlayer(skulled = true)))
    }

    @Test
    fun `being hit by a player in the last 7 seconds counts as combat`() {
        val player = newPlayer()
        player.timers[TELEPORT_COMBAT_TIMER] = 12
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Player>(relaxed = true))
        assertTrue(DeadmanTimerGate.needsCountdown(player))
    }

    @Test
    fun `being hit by an ordinary npc counts as combat`() {
        val player = newPlayer()
        player.timers[TELEPORT_COMBAT_TIMER] = 12
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(npc(9999, "Guard"))
        assertTrue(DeadmanTimerGate.needsCountdown(player))
    }

    @Test
    fun `being hit by a boss never counts - every boss family`() {
        listOf(
            npc(Npcs.KING_BLACK_DRAGON, "King Black Dragon"),
            npc(Npcs.GENERAL_GRAARDOR, "General Graardor"),
            npc(Npcs.NEX, "Nex"),
            npc(Npcs.CORPOREAL_BEAST, "Corporeal Beast"),
            npc(Npcs.TORMENTED_DEMON_8355, "Tormented demon"),
            npc(Npcs.DHAROK_THE_WRETCHED, "Dharok the Wretched"),
            npc(Npcs.KALPHITE_QUEEN_1160, "Kalphite Queen"),
            npc(12345, "Glacor"), // by cache name, whatever the variant id
        ).forEach { boss ->
            val player = newPlayer()
            player.timers[TELEPORT_COMBAT_TIMER] = 12
            player.attr[LAST_HIT_BY_ATTR] = WeakReference(boss)
            assertFalse(DeadmanTimerGate.needsCountdown(player), "${boss.def.name} must not trigger the countdown")
            assertTrue(BossNpcs.isBoss(boss))
        }
    }

    @Test
    fun `attacking an ordinary npc counts, attacking a boss does not`() {
        val fighting = newPlayer()
        fighting.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(npc(9999, "Guard"))
        assertTrue(DeadmanTimerGate.needsCountdown(fighting))

        val bossing = newPlayer()
        bossing.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(npc(Npcs.TZTOKJAD, "TzTok-Jad"))
        assertFalse(DeadmanTimerGate.needsCountdown(bossing))
    }

    @Test
    fun `an expired combat window no longer counts`() {
        val player = newPlayer()
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Player>(relaxed = true))
        assertFalse(DeadmanTimerGate.needsCountdown(player))
    }

    private fun npc(
        id: Int,
        name: String,
    ): Npc {
        val n = mockk<Npc>(relaxed = true)
        every { n.id } returns id
        val def = mockk<NpcDef>(relaxed = true)
        every { def.name } returns name
        every { n.def } returns def
        return n
    }

    private fun newPlayer(skulled: Boolean = false): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.skullIcon } returns if (skulled) SkullIcon.RED.id else SkullIcon.NONE.id
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        return player
    }
}
