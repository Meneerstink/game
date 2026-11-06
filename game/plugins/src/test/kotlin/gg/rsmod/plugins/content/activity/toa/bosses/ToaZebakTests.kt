package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import kotlin.test.*

class ToaZebakTests {
    @Test fun `autonomous director attacks at published intervals and queues threshold special only once`() {
        val f = ToaTestFixture()
        io.mockk.every { f.npc.getMaximumLifepoints() } returns 580
        io.mockk.every { f.npc.getCurrentLifepoints() } returns 493
        val s = ZebakCombat.Session(f.npc, ToaTestContext())
        var autos = 0; var specials = 0
        s.autoSelector = { autos++; ToaAutoAttack(16, CombatClass.RANGED, 4) }
        s.specialDirector = { _, kind -> assertEquals(ZebakCombat.Special.ROAR, kind); specials++ }
        repeat(15) { s.cycle() }
        assertEquals(3, autos); assertEquals(1, specials); assertEquals(1, s.thresholdIndex)
    }
    @Test fun `legacy path level never changes base seven tick and enraged four tick speed`() {
        assertEquals(List(7) { 7 }, (0..6).map { ZebakCombat.attackInterval(false, it) })
        assertEquals(List(7) { 4 }, (0..6).map { ZebakCombat.attackInterval(true, it) })
    }
    @Test fun `ranged and magic remain fully blocked even with an obsolete Quiet Prayers flag`() {
        assertEquals(0, ToaProtection.damage(16, true))
        assertEquals(0, ToaProtection.damage(16, true, quiet = true))
        assertEquals(16, ToaProtection.damage(16, false))
    }
    @Test fun `autos have separate 38 melee and 16 ranged magic maxima, with group-independent damage`() {
        val zero = ToaTestContext()
        assertEquals(38, ZebakCombat.maxHit(CombatClass.MELEE, zero))
        assertEquals(16, ZebakCombat.maxHit(CombatClass.RANGED, zero))
        assertEquals(16, ZebakCombat.maxHit(CombatClass.MAGIC, zero))
        val high = zero.copy(raidLevel = 600, partySize = 8, pathLevel = 6)
        assertEquals(40, ZebakCombat.maxHit(CombatClass.RANGED, high))
        assertEquals(95, ZebakCombat.maxHit(CombatClass.MELEE, high))
    }
    @Test fun `enrage is below a quarter and clears every queued threshold`() {
        assertFalse(ZebakCombat.enraged(145, 580))
        assertTrue(ZebakCombat.enraged(144, 580))
        val f = ToaTestFixture()
        val s = ZebakCombat.Session(f.npc, ToaTestContext())
        s.pending.addLast(ZebakCombat.Special.ROAR)
        s.pending.addLast(ZebakCombat.Special.WAVES)
        io.mockk.every { f.npc.getMaximumLifepoints() } returns 580
        io.mockk.every { f.npc.getCurrentLifepoints() } returns 144
        s.cycle()
        assertTrue(s.isEnraged); assertTrue(s.pending.isEmpty())
        repeat(10) { s.cycle() }
        assertTrue(s.pending.isEmpty(), "crossing four thresholds during enrage must not enqueue delayed specials")
    }
    @Test fun `simplified encounters ignore an old Upset Stomach flag and keep base acid size`() {
        assertEquals(2, ZebakCombat.waterRadius(false)); assertEquals(2, ZebakCombat.waterRadius(true))
        assertEquals(1, ZebakCombat.acidRadius(false)); assertEquals(1, ZebakCombat.acidRadius(true))
        val f = ToaTestFixture()
        val s = ZebakCombat.Session(f.npc, ToaTestContext())
        s.addAcid(Tile(3200, 3200)); assertEquals(9, s.acid.size)
        val upset = ZebakCombat.Session(f.npc, ToaTestContext(invocations = setOf("Upset Stomach")))
        upset.addAcid(Tile(3200, 3200)); assertEquals(9, upset.acid.size)
    }
    @Test fun `roar safety extends three tiles behind a rock, never to the fourth or the wrong plane`() {
        val rock = Tile(100, 100)
        assertTrue(ZebakCombat.behindRock(Tile(101, 100), rock, Direction.EAST))
        assertTrue(ZebakCombat.behindRock(Tile(103, 100), rock, Direction.EAST))
        assertFalse(ZebakCombat.behindRock(Tile(104, 100), rock, Direction.EAST))
        assertFalse(ZebakCombat.behindRock(Tile(101, 100, 1), rock, Direction.EAST))
        assertFalse(ZebakCombat.behindRock(Tile(100, 101), rock, Direction.EAST))
    }
    @Test fun `three roars consume a rock once per wave even without players behind it`() {
        val f = ToaTestFixture(); val s = ZebakCombat.Session(f.npc, ToaTestContext())
        val rock = ZebakCombat.Rock(Tile(3202, 3202)); s.rocks += rock
        s.roar(firstImpact = 1, spacing = 2, baseDamage = 1, direction = Direction.SOUTH)
        repeat(7) { s.cycle() }
        assertEquals(0, rock.hp)
    }
    @Test fun `legacy path levels cannot narrow the base wave gap`() {
        assertEquals(List(7) { 5 }, (0..6).map { ZebakCombat.narrowedGap(5, it) })
    }
    @Test fun `close removes persistent acid rocks and queued roar without late damage`() {
        val f = ToaTestFixture(); val s = ZebakCombat.Session(f.npc, ToaTestContext())
        val rock = ZebakCombat.Rock(Tile(3202, 3202)); s.rocks += rock; s.addAcid(rock.tile)
        s.roar(2, 2, 1, Direction.SOUTH); s.close(); repeat(10) { s.cycle() }
        assertTrue(s.acid.isEmpty()); assertTrue(s.rocks.isEmpty()); assertEquals(150, rock.hp)
    }
}
