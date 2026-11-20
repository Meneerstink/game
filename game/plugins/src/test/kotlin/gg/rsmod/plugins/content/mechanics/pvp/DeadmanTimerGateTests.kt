package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DMM-ARMAGEDDON D1 (owner 20-11-2026, OSRS Wiki Deadman: Armageddon "PJ timer", the owner's photo): skulled -> the 6-second
 * (10-tick) countdown for every teleport and logout; unskulled -> instant unless hit by a player in the last 10 cycles (wiki
 * "5.4 seconds (9 ticks)", table instant at 6.0 s) or by any npc, bosses included, in the last 5 cycles (wiki "2.4 seconds");
 * the old boss-area exception is gone; logout counts down under the same conditions.
 */
class DeadmanTimerGateTests {
    private val guardedTile = Tile(3212, 3428, 0)

    @Test
    fun `the Deadman teleport and logout delay is 6 seconds - 10 ticks`() {
        assertEquals(10, SevenSecondAction.DURATION_CYCLES)
        assertEquals(6, SevenSecondAction.secondsFor(SevenSecondAction.DURATION_CYCLES))
        assertEquals(10, DeadmanTimerGate.PVP_HIT_TELEPORT_BLOCK_CYCLES)
        assertEquals(5, DeadmanTimerGate.NPC_HIT_TELEPORT_BLOCK_CYCLES)
    }

    @Test
    fun `an unskulled player out of combat teleports and logs out instantly`() {
        val player = newPlayer()
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player))
        assertFalse(DeadmanTimerGate.needsCountdown(player))
    }

    @Test
    fun `a skulled player always gets the countdown, even out of combat and next to a boss`() {
        val player = newPlayer(skulled = true, bossHere = true)
        assertEquals(DeadmanTimerGate.Teleport.COUNTDOWN, DeadmanTimerGate.teleportDecision(player))
        assertTrue(DeadmanTimerGate.needsCountdown(player))
    }

    @Test
    fun `unskulled and hit by a player - blocked for 10 cycles from the attack, then instant`() {
        val player = newPlayer()
        every { player.world.currentCycle } returns 300
        PvpSkull.markAggression(mockk(relaxed = true), player)
        assertEquals(DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT, DeadmanTimerGate.teleportDecision(player))
        assertEquals("You must be out of combat for another 6 seconds to teleport.", DeadmanTimerGate.blockedMessage(player))
        assertTrue(DeadmanTimerGate.needsCountdown(player), "logout gets the 6 s countdown while in combat")

        every { player.world.currentCycle } returns 309 // 5.4 s after the attack: the last blocked cycle
        assertEquals(DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT, DeadmanTimerGate.teleportDecision(player))
        assertEquals("You must be out of combat for another 1 seconds to teleport.", DeadmanTimerGate.blockedMessage(player))

        every { player.world.currentCycle } returns 310 // 6.0 s: the wiki table's instant teleport
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player))
        assertFalse(DeadmanTimerGate.needsCountdown(player))
    }

    @Test
    fun `a player attack that skipped markAggression still blocks through the combat timer`() {
        val player = newPlayer()
        player.timers[TELEPORT_COMBAT_TIMER] = SevenSecondAction.DURATION_CYCLES
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Player>(relaxed = true))
        assertEquals(DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT, DeadmanTimerGate.teleportDecision(player))
        assertEquals("You must be out of combat for another 6 seconds to teleport.", DeadmanTimerGate.blockedMessage(player))
        player.timers[TELEPORT_COMBAT_TIMER] = 5
        assertEquals("You must be out of combat for another 3 seconds to teleport.", DeadmanTimerGate.blockedMessage(player))
    }

    @Test
    fun `an ordinary npc hit blocks for 5 cycles then the teleport is instant`() {
        val player = newPlayer()
        every { player.world.currentCycle } returns 100
        DeadmanTimerGate.markNpcHit(player)
        assertEquals(DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT, DeadmanTimerGate.teleportDecision(player))
        assertEquals("You must be out of combat for another 3 seconds to teleport.", DeadmanTimerGate.blockedMessage(player))
        assertTrue(DeadmanTimerGate.needsCountdown(player))
        every { player.world.currentCycle } returns 104
        assertEquals(DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT, DeadmanTimerGate.teleportDecision(player))
        every { player.world.currentCycle } returns 105
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player))
        assertFalse(DeadmanTimerGate.needsCountdown(player))
    }

    @Test
    fun `a boss hit counts like any npc hit - no more boss or boss-area exception`() {
        val player = newPlayer(bossHere = true)
        every { player.world.currentCycle } returns 100
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(npc(Npcs.KING_BLACK_DRAGON, "King Black Dragon"))
        DeadmanTimerGate.markNpcHit(player)
        assertEquals(DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT, DeadmanTimerGate.teleportDecision(player))
        assertTrue(DeadmanTimerGate.needsCountdown(player), "a boss hit now gives the logout countdown too")
        every { player.world.currentCycle } returns 105
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player))
    }

    @Test
    fun `a player hit followed by a boss hit keeps the longer player window`() {
        val player = newPlayer(bossHere = true)
        every { player.world.currentCycle } returns 500
        PvpSkull.markAggression(mockk(relaxed = true), player)
        every { player.world.currentCycle } returns 503
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(npc(Npcs.KING_BLACK_DRAGON, "King Black Dragon"))
        DeadmanTimerGate.markNpcHit(player)
        assertEquals("You must be out of combat for another 4 seconds to teleport.", DeadmanTimerGate.blockedMessage(player))
        every { player.world.currentCycle } returns 509
        assertEquals(DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT, DeadmanTimerGate.teleportDecision(player))
        assertTrue(DeadmanTimerGate.needsCountdown(player))
        every { player.world.currentCycle } returns 510
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player))
    }

    @Test
    fun `only being hit counts - attacking an npc that never hit you does not`() {
        val fighting = newPlayer()
        fighting.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(npc(9999, "Guard"))
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(fighting))
        assertFalse(DeadmanTimerGate.needsCountdown(fighting))
    }

    @Test
    fun `boss areas come from the boss npcs standing in the region, the God Wars chambers are always included`() {
        val world = mockk<World>(relaxed = true)
        val bosses = PawnList(arrayOfNulls<Npc>(4))
        every { world.npcs } returns bosses
        assertFalse(BossAreas.isBossArea(world, Tile(2273, 4690, 0)), "an empty KBD lair region is not a boss area until KBD stands in it")
        val kbd = npc(Npcs.KING_BLACK_DRAGON, "King Black Dragon", tile = Tile(2273, 4690, 0))
        bosses.add(kbd)
        assertTrue(BossAreas.isBossArea(world, Tile(2260, 4700, 0)), "the live KBD makes its region a boss area")
        assertFalse(BossAreas.isBossArea(world, guardedTile))

        BossAreas.init(world)
        assertTrue(BossAreas.bootRegionCount() >= 5, "KBD region + the four God Wars chamber regions at least")
        val chambers = listOf(Tile(2870, 5360, 2), Tile(2830, 5300, 2), Tile(2900, 5265, 0), Tile(2925, 5325, 2))
        chambers.forEach { assertTrue(BossAreas.isBossArea(world, it), "GWD chamber $it") }
        assertTrue(BossAreas.isBossArea(world, Tile(2270, 4690, 0)), "KBD from the boot scan")
    }

    @Test
    fun `an expired combat window no longer counts`() {
        val player = newPlayer()
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Player>(relaxed = true))
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player))
        assertFalse(DeadmanTimerGate.needsCountdown(player))
    }

    @Test
    fun `shared transport gate always defers regardless of skull status`() {
        val safe = newPlayer()
        var safeRuns = 0
        assertFalse(DeadmanTimerGate.requestRoute(safe, SevenSecondAction.Kind.TRANSPORT) { safeRuns++ })
        assertEquals(0, safeRuns)
        SevenSecondAction.complete(safe)
        assertEquals(1, safeRuns)

        val skulled = newPlayer(skulled = true)
        var skulledRuns = 0
        assertFalse(DeadmanTimerGate.requestRoute(skulled, SevenSecondAction.Kind.PORTAL) { skulledRuns++ })
        assertEquals(0, skulledRuns)
        SevenSecondAction.complete(skulled)
        assertEquals(1, skulledRuns)
    }

    private fun npc(
        id: Int,
        name: String,
        tile: Tile = Tile(0, 0, 0),
    ): Npc {
        val n = mockk<Npc>(relaxed = true)
        every { n.id } returns id
        every { n.tile } returns tile
        every { n.index } returns 1
        val def = mockk<NpcDef>(relaxed = true)
        every { def.name } returns name
        every { n.def } returns def
        return n
    }

    private fun newPlayer(
        skulled: Boolean = false,
        bossHere: Boolean = false,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.skullIcon } returns if (skulled) SkullIcon.DMM_VERY_LOW_RISK.id else SkullIcon.NONE.id
        every { player.attr } returns AttributeMap()
        // The skull state is the running skull timer (PvpSkull.isSkulled), never an icon id.
        every { player.timers } returns TimerMap().also { if (skulled) it[gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER] = PvpSkull.SKULL_DURATION_CYCLES }
        val tile = Tile(3100, 3500, 0) // Edgeville: never a boss region unless this test puts a boss there
        every { player.tile } returns tile
        val world = mockk<World>(relaxed = true)
        val npcs = PawnList(arrayOfNulls<Npc>(2))
        if (bossHere) npcs.add(npc(Npcs.KING_BLACK_DRAGON, "King Black Dragon", tile = tile))
        every { world.npcs } returns npcs
        every { player.world } returns world
        return player
    }
}
