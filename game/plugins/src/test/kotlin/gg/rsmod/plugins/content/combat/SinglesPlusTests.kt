package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.areas.wilderness.bosses.ArtioCombat
import java.lang.ref.WeakReference
import io.mockk.*
import kotlin.test.*

class SinglesPlusTests {
    private val world = mockk<World>(relaxed = true)
    private var cycle = 100
    init {
        every { world.currentCycle } answers { cycle }
        every { world.npcs } returns PawnList(arrayOfNulls<Npc>(16))
    }
    private fun player(at: Tile): Player = mockk<Player>(relaxed = true).also {
        every { it.tile } returns at
        every { it.world } returns world
        every { it.attr } returns AttributeMap()
        every { it.timers } returns TimerMap()
        every { it.isOnline } returns true
        every { it.isDead() } returns false
    }
    private fun npc(at: Tile, id: Int): Npc = mockk<Npc>(relaxed = true).also {
        every { it.id } returns id
        every { it.tile } returns at
        every { it.world } returns world
        every { it.attr } returns AttributeMap()
        every { it.owner } returns null
        every { it.isDead() } returns false
        every { it.index } returns 1
        world.npcs.add(it)
    }

    @Test fun `players can replace a boss reservation in each singles plus lair and caves but ordinary single stays reserved`() {
        for (at in listOf(Tile(1759,11550), Tile(1887,11543,1), Tile(1632,11556,2), Tile(3200,10100), Tile(3200,3800))) {
            val a = player(at)
            val b = player(at)
            val boss = npc(at, ArtioCombat.NPC)
            a.timers[Combat.PJ_TIMER] = Combat.PJ_TICKS
            a.attr[LAST_HIT_BY_ATTR] = WeakReference(boss)
            assertEquals(!SinglesPlus.contains(at), a.isAttackedByOtherThan(b))
        }
        assertFalse(SinglesPlus.contains(Tile(1887,11543,0)), "wrong plane")
    }

    @Test fun `continuing boss hits cannot expose a player fight to a third PKer and ordinary NPCs yield`() {
        val at = Tile(1759,11550)
        val a = player(at)
        val b = player(at)
        val third = player(at)
        val boss = npc(at, ArtioCombat.NPC)
        SinglesPlus.recordAttack(a, b)
        a.attr[LAST_HIT_BY_ATTR] = WeakReference(boss)
        a.timers[Combat.PJ_TIMER] = Combat.PJ_TICKS
        assertFalse(a.isAttackedByOtherThan(b))
        assertFalse(a.isAttackedByOtherThan(boss))
        assertTrue(a.isAttackedByOtherThan(third))
        val ordinary = npc(at, 1)
        assertTrue(a.isAttackedByOtherThan(ordinary))
        cycle += Combat.PJ_TICKS
        assertFalse(a.isAttackedByOtherThan(third))
        assertEquals(17, Combat.PJ_TICKS)
    }
}
