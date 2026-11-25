package gg.rsmod.plugins.content.npcs.definitions.barrows

import kotlin.test.Test
import kotlin.test.assertEquals
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.combat.*
import io.mockk.every
import io.mockk.mockk
import java.lang.ref.WeakReference
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Barrows numbers against the OSRS Wiki (2026-10-03, owner E13 "Barrows precies als OSRS"):
 *  - Barrows/Strategies: prayer drain "begins at eight Prayer points, and increases by one for each brother killed, for a maximum
 *    of fourteen"; "No new monster spawns if a room already contains eleven enemies".
 *  - Chest (Barrows): reward potential = min(combat levels killed, 1000) + 2 per brother (max 1012); one roll plus one per brother
 *    killed, at most seven.
 */
class BarrowsWikiRulesTests {
    /** COMBATENGAGE-FLAKY: multi comes from mocked multi.yml chunks here; a class that loaded the plugins (multiway_combat) leaves the real surface map in this JVM. */
    @kotlin.test.BeforeTest
    fun unloadMultiZones() = gg.rsmod.plugins.content.mechanics.multi.MultiCombatZones.unload()

    @Test
    fun `owned brothers replace crypt combat and share reservations without opening other encounters`() {
        val world = mockk<World>(relaxed = true)
        every { world.npcs } returns PawnList(arrayOfNulls<Npc>(16))
        every { world.instanceAllocator.getMap(any()) } returns null
        every { world.getMultiCombatChunks() } returns emptySet()
        every { world.getMultiCombatRegions() } returns emptySet()
        fun player(): Player = mockk<Player>(relaxed = true).also {
            every { it.world } returns world
            every { it.tile } returns Tile(3550, 9690)
            every { it.attr } returns AttributeMap()
            every { it.timers } returns TimerMap()
            every { it.isOnline } returns true
        }
        var index = 0
        fun npc(id: Int, owner: Player?): Npc = mockk<Npc>(relaxed = true).also {
            every { it.world } returns world
            every { it.tile } returns Tile(3551, 9690)
            every { it.id } returns id
            every { it.owner } returns owner
            every { it.attr } returns AttributeMap()
            every { it.timers } returns TimerMap()
            every { it.index } returns ++index
            world.npcs.add(it)
        }
        val player = player()
        val stranger = player()
        val crypt = npc(Barrows.CRYPT_MONSTERS.first(), null)
        val brother = npc(Barrows.Brother.AHRIM.npc, player)
        val second = npc(Barrows.Brother.DHAROK.npc, player)
        val foreign = npc(Barrows.Brother.AHRIM.npc, stranger)
        player.setCombatTarget(crypt)
        crypt.setCombatTarget(player)
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(crypt)
        player.timers[Combat.PJ_TIMER] = Combat.PJ_TICKS
        assertFalse(player.isAttackedByOtherThan(brother))
        assertTrue(player.isAttackedByOtherThan(foreign))
        assertTrue(BarrowsCombat.takePriority(player, brother))
        assertEquals(null, crypt.getCombatTarget())
        assertEquals(null, player.getCombatTarget())
        assertTrue(player.timers.has(Combat.PJ_TIMER))
        assertEquals(brother, player.liveLastHitBy())
        assertTrue(player.isAttackedByOtherThan(crypt), "the crypt cannot reacquire before the first brother hit")
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(brother)
        player.timers[Combat.PJ_TIMER] = Combat.PJ_TICKS
        assertFalse(player.isAttackedByOtherThan(second))
        assertTrue(player.isAttackedByOtherThan(crypt))
        assertTrue(player.isAttackedByOtherThan(foreign))
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(stranger)
        assertFalse(BarrowsCombat.takePriority(player, brother))
        assertTrue(player.isAttackedByOtherThan(brother), "PvP stays reserved")
    }

    @Test
    fun `prayer drain is 8 plus one per brother killed, at most 14`() {
        assertEquals(8, Barrows.drainAmount(0))
        assertEquals(11, Barrows.drainAmount(3))
        assertEquals(14, Barrows.drainAmount(6))
        assertEquals(14, Barrows.drainAmount(9))
    }

    @Test
    fun `reward potential caps the levels at 1000 and adds 2 per brother`() {
        assertEquals(520 + 8, Barrows.rewardPotential(520, 4))
        assertEquals(1012, Barrows.rewardPotential(5000, 6))
        assertEquals(0, Barrows.rewardPotential(0, 0))
    }

    @Test
    fun `one roll plus one per brother, at most seven`() {
        assertEquals(1, Barrows.rollCount(0))
        assertEquals(5, Barrows.rollCount(4))
        assertEquals(7, Barrows.rollCount(6))
    }

    @Test
    fun `a room holds at most eleven enemies`() {
        assertEquals(11, Barrows.MAX_ROOM_ENEMIES)
    }
}
