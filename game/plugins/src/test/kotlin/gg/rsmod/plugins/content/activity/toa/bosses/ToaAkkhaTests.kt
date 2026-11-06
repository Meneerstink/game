package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.plugins.api.HitType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class ToaAkkhaTests {
    @Test fun `current single style prayer blocks only Akkhas active style, enrage admits melee only`() {
        val f = ToaTestFixture(); val s = AkkhaCombat.Session(f.npc, ToaTestContext())
        val melee = Hit.Builder().addHit(10, HitType.MELEE.id).build()
        val ranged = Hit.Builder().addHit(10, HitType.RANGE.id).build()
        s.incoming(melee); s.incoming(ranged)
        assertEquals(0, melee.hitmarks.single().damage); assertEquals(10, ranged.hitmarks.single().damage)
        s.enraged = true
        val magic = Hit.Builder().addHit(10, HitType.MAGIC.id).build()
        val melee2 = Hit.Builder().addHit(10, HitType.MELEE.id).build()
        s.incoming(magic); s.incoming(melee2)
        assertEquals(0, magic.hitmarks.single().damage); assertEquals(10, melee2.hitmarks.single().damage)
    }
    @Test fun `published scaling has damage cap but no party damage multiplier`() {
        val c = ToaTestContext()
        assertEquals(400, ToaScale.hp(400, c))
        assertEquals(55, ToaScale.damage(55, c))
        assertEquals(2320, ToaScale.hp(400, c.copy(partySize = 8)))
        assertEquals(55, ToaScale.damage(55, c.copy(partySize = 8)))
        assertEquals(137, ToaScale.damage(55, c.copy(raidLevel = 600)))
        assertEquals(10491, ToaScale.hp(400, c.copy(raidLevel = 600, partySize = 8, pathLevel = 6)))
        assertEquals(182, ToaScale.damage(55, c.copy(raidLevel = 600, pathLevel = 6)))
    }
    @Test fun `special is seventh or sixth attack and styles cycle forward`() {
        assertEquals(6, AkkhaCombat.autosBeforeSpecial(false))
        assertEquals(5, AkkhaCombat.autosBeforeSpecial(true))
        assertEquals(CombatClass.RANGED, AkkhaCombat.nextStyle(CombatClass.MELEE))
        assertEquals(CombatClass.MAGIC, AkkhaCombat.nextStyle(CombatClass.RANGED))
        assertEquals(CombatClass.MELEE, AkkhaCombat.nextStyle(CombatClass.MAGIC))
    }
    @Test fun `four thresholds and one extra memory burst at path two and four`() {
        assertEquals(listOf(320, 240, 160, 80), (0..3).map { AkkhaCombat.shadowThreshold(it, 400) })
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 2), (0..6).map(AkkhaCombat::extraMemoryBursts))
        assertEquals(4, AkkhaCombat.blastInterval(false)); assertEquals(2, AkkhaCombat.blastInterval(true))
    }
    @Test fun `memory never moves diagonally and detonation stack is safe`() {
        assertTrue(AkkhaCombat.adjacent(AkkhaCombat.Quadrant.FIRE, AkkhaCombat.Quadrant.SHADOW))
        assertFalse(AkkhaCombat.adjacent(AkkhaCombat.Quadrant.FIRE, AkkhaCombat.Quadrant.LIGHTNING))
        assertFalse(AkkhaCombat.detonationHits(Tile(10, 10), Tile(10, 10), false))
        assertTrue(AkkhaCombat.detonationHits(Tile(10, 10), Tile(11, 10), false))
        assertFalse(AkkhaCombat.detonationHits(Tile(10, 10), Tile(11, 11), false))
        assertTrue(AkkhaCombat.detonationHits(Tile(10, 10), Tile(11, 11), true))
    }
    @Test fun `orb prayer reduces before cap and successful hit counter scales from three to ten`() {
        assertEquals(15, AkkhaCombat.orbDamage(20, true))
        assertEquals(25, AkkhaCombat.orbDamage(40, true))
        assertEquals(3, AkkhaCombat.enrageHits(1)); assertEquals(10, AkkhaCombat.enrageHits(8))
    }
    @Test fun `phase cancellation and close discard pending special and minions`() {
        val f = ToaTestFixture()
        val s = AkkhaCombat.Session(f.npc, ToaTestContext())
        var hits = 0
        s.later(2) { hits++ }; s.newPhase(); repeat(4) { s.cycle() }
        assertEquals(0, hits)
        s.later(2) { hits++ }; s.close(); repeat(4) { s.cycle() }
        assertEquals(0, hits); assertTrue(s.closed)
    }
}

internal data class ToaTestContext(
    override val players: List<Player> = emptyList(), override val partySize: Int = 1, override val raidLevel: Int = 0,
    override val pathLevel: Int = 0, override val invocations: Set<String> = emptySet(),
) : ToaEncounterContext

internal class ToaTestFixture {
    val npc = mockk<Npc>(relaxed = true)
    val world = mockk<World>(relaxed = true)
    init {
        every { npc.world } returns world
        every { npc.attr } returns AttributeMap()
        every { npc.tile } returns Tile(3200, 3200)
        every { npc.spawnTile } returns Tile(3200, 3200)
        every { npc.getCentreTile() } returns Tile(3201, 3201)
        every { npc.getSize() } returns 3
        every { npc.isSpawned() } returns true
        every { npc.isDead() } returns false
        every { npc.getMaximumLifepoints() } returns 400
        every { npc.getCurrentLifepoints() } returns 400
    }
}
