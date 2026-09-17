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
 * Owner "deadmanmode vervijning" 2026-09-17: skulled -> the 7-second countdown interface; unskulled and
 * hit in the last 7 seconds -> "You must be out of combat for another X seconds to teleport."; boss
 * areas and boss hits never delay a teleport; logout keeps the skulled-or-in-combat countdown.
 */
class DeadmanTimerGateTests {
    private val guardedTile = Tile(3212, 3428, 0)

    @Test
    fun `an unskulled player out of combat teleports and logs out instantly`() {
        val player = newPlayer()
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player))
        assertFalse(DeadmanTimerGate.needsCountdown(player))
    }

    @Test
    fun `a skulled player always gets the countdown, even out of combat and even in a boss area`() {
        val player = newPlayer(skulled = true, bossHere = true)
        assertEquals(DeadmanTimerGate.Teleport.COUNTDOWN, DeadmanTimerGate.teleportDecision(player))
        assertTrue(DeadmanTimerGate.needsCountdown(player))
    }

    @Test
    fun `unskulled and hit by a player in the last 7 seconds is blocked with the remaining seconds`() {
        val player = newPlayer()
        player.timers[TELEPORT_COMBAT_TIMER] = 12
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Player>(relaxed = true))
        assertEquals(DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT, DeadmanTimerGate.teleportDecision(player))
        assertEquals("You must be out of combat for another 7 seconds to teleport.", DeadmanTimerGate.blockedMessage(player))
        player.timers[TELEPORT_COMBAT_TIMER] = 5
        assertEquals("You must be out of combat for another 3 seconds to teleport.", DeadmanTimerGate.blockedMessage(player))
        assertTrue(DeadmanTimerGate.needsCountdown(player), "logout still gets the countdown while in combat")
    }

    @Test
    fun `being hit by an ordinary npc counts as combat`() {
        val player = newPlayer()
        player.timers[TELEPORT_COMBAT_TIMER] = 12
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(npc(9999, "Guard"))
        assertEquals(DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT, DeadmanTimerGate.teleportDecision(player))
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
            assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player), "${boss.def.name} must not delay a teleport")
            assertFalse(DeadmanTimerGate.needsCountdown(player), "${boss.def.name} must not trigger the logout countdown")
            assertTrue(BossNpcs.isBoss(boss))
        }
    }

    @Test
    fun `inside a boss area a hit by a minion never delays the teleport`() {
        val player = newPlayer(bossHere = true)
        player.timers[TELEPORT_COMBAT_TIMER] = 12
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(npc(6260, "Sergeant Strongstack"))
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player))
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
    fun `attacking an ordinary npc counts for logout, attacking a boss does not`() {
        val fighting = newPlayer()
        fighting.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(npc(9999, "Guard"))
        assertTrue(DeadmanTimerGate.needsCountdown(fighting))
        // Only being hit delays a teleport (owner: "as long as they have not been attacked ... in the last 7 seconds").
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(fighting))

        val bossing = newPlayer()
        bossing.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(npc(Npcs.TZTOKJAD, "TzTok-Jad"))
        assertFalse(DeadmanTimerGate.needsCountdown(bossing))
    }

    @Test
    fun `an expired combat window no longer counts`() {
        val player = newPlayer()
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Player>(relaxed = true))
        assertEquals(DeadmanTimerGate.Teleport.INSTANT, DeadmanTimerGate.teleportDecision(player))
        assertFalse(DeadmanTimerGate.needsCountdown(player))
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
