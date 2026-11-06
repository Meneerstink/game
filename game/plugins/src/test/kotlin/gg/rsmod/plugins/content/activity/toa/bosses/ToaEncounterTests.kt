package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.*

class ToaEncounterTests {
    @Test fun `room contexts reject invalid party and path bounds before scheduling`() {
        assertFailsWith<IllegalArgumentException> { ToaScale.validate(ToaTestContext(partySize = 0)) }
        assertFailsWith<IllegalArgumentException> { ToaScale.validate(ToaTestContext(partySize = 9)) }
        assertFailsWith<IllegalArgumentException> { ToaScale.validate(ToaTestContext(pathLevel = 7)) }
        assertFailsWith<IllegalArgumentException> { ToaScale.validate(ToaTestContext(raidLevel = 601)) }
        ToaScale.validate(ToaTestContext(partySize = 8, pathLevel = 6, raidLevel = 600))
    }
    @Test fun `damage caps atRL375 but HP continues to scale at600`() {
        assertEquals(250, ToaScale.damage(100, ToaTestContext(raidLevel = 375)))
        assertEquals(250, ToaScale.damage(100, ToaTestContext(raidLevel = 600)))
        assertEquals(340, ToaScale.hp(100, ToaTestContext(raidLevel = 600)))
        assertEquals(listOf(10, 19, 28, 34, 40, 46, 52, 58), (1..8).map(ToaScale::groupTenths))
        assertEquals(listOf(100, 108, 113, 118, 123, 128, 133), (0..6).map(ToaScale::pathPercent))
    }
    @Test fun `contexts use real lifepoints once, and each NPC has its own mutable combat definition`() {
        val c = ToaTestContext()
        val first = AkkhaCombat.STATS.definition(c)
        val second = AkkhaCombat.STATS.definition(c)
        assertEquals(400, first.lifepoints)
        assertFalse(first === second)
        first.attackStyleType = gg.rsmod.game.model.combat.StyleType.CRUSH
        assertFalse(second.attackStyleType == first.attackStyleType)
        assertEquals(-1, first.respawnDelay)
        assertTrue(first.poisonImmunity && first.venomImmunity)
    }
    @Test fun `two live rooms never share phase generation or queued effects`() {
        val a = AkkhaCombat.Session(ToaTestFixture().npc, ToaTestContext())
        val b = AkkhaCombat.Session(ToaTestFixture().npc, ToaTestContext(partySize = 8, raidLevel = 600, pathLevel = 6))
        var first = 0; var second = 0
        a.later(2) { first++ }; b.later(2) { second++ }
        a.newPhase()
        repeat(4) { a.cycle(); b.cycle() }
        assertEquals(0, first); assertEquals(1, second)
        assertEquals(1, a.generation); assertEquals(0, b.generation)
    }
    @Test fun `online room membership and death are rechecked before every effect`() {
        val f = ToaTestFixture(); val p = mockk<Player>(relaxed = true)
        var online = true; var dead = false; var present = true
        every { p.isOnline } answers { online }; every { p.isDead() } answers { dead }
        every { p.tile } returns Tile(3200, 3200)
        val c = object : ToaEncounterContext {
            override val players get() = if (present) listOf(p) else emptyList()
            override val partySize = 1; override val raidLevel = 0; override val pathLevel = 0
            override val invocations = emptySet<String>()
        }
        val s = AkkhaCombat.Session(f.npc, c)
        assertTrue(s.eligible(p)); online = false; assertFalse(s.eligible(p))
        online = true; dead = true; assertFalse(s.eligible(p))
        dead = false; present = false; assertFalse(s.eligible(p))
        present = true; s.close(); assertFalse(s.eligible(p))
    }
    @Test fun `boss death cancels queued special and cleanup removes children exactly once`() {
        val f = ToaTestFixture(); val s = AkkhaCombat.Session(f.npc, ToaTestContext())
        val child = mockk<Npc>(relaxed = true); every { child.isSpawned() } returns true
        s.children += child
        var lateHit = 0; s.later(1) { lateHit++ }
        every { f.npc.isDead() } returns true
        repeat(3) { s.cycle() }
        assertEquals(0, lateHit)
        s.close(); s.close()
        verify(exactly = 1) { f.world.remove(child) }
        assertTrue(s.children.isEmpty())
    }
}
