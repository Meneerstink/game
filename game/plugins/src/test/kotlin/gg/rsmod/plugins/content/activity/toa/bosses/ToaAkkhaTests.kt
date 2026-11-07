package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.freeze
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class ToaAkkhaTests {
    @Test fun `protection icons follow every style and enrage then clear on close`() {
        val icons = gg.rsmod.plugins.content.activity.toa.ToaHeadIcons
        io.mockk.mockkObject(icons)
        try {
            val f = ToaTestFixture(); val frames = mutableListOf<Int>()
            every { icons.show(f.npc, any()) } answers { frames += secondArg<Int>(); true }
            every { icons.clear(f.npc) } returns Unit
            val s = AkkhaCombat.Session(f.npc, ToaTestContext())
            s.configure(); s.style = CombatClass.RANGED; s.style = CombatClass.MAGIC; s.enterEnrage()
            assertEquals(listOf(0, 1, 2, 6), frames)
            s.close(); io.mockk.verify(exactly = 1) { icons.clear(f.npc) }
        } finally { io.mockk.unmockkObject(icons) }
    }
    @Test fun `incoming protection uses launch style after a weapon switch`() {
        val s = AkkhaCombat.Session(ToaTestFixture().npc, ToaTestContext())
        val hit = Hit.Builder().addHit(10, HitType.RANGE.id).build().also { it.sourceCombatClass = CombatClass.MELEE }
        s.incoming(hit); assertEquals(0, hit.hitmarks.single().damage)
    }
    @Test fun `autonomous director performs six autos then special at tick36 and changes style`() {
        val f = ToaTestFixture(); val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false
        every { p.tile } returns Tile(3201, 3199); every { p.equipmentBonuses } returns IntArray(18)
        every { p.equipment[3] } returns null
        val s = AkkhaCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        val auto = mockk<ToaAutoAttack>(relaxed = true)
        s.autoEvidence = CombatClass.values().associateWith { auto }; s.target = p
        var specials = 0; s.specialDirector = { specials++ }; s.shadowChargeDirector = {}
        repeat(37) { s.cycle() }
        io.mockk.verify(exactly = 6) { auto.fire(s, p) }
        assertEquals(1, specials); assertEquals(CombatClass.RANGED, s.style)
        assertEquals(0, s.autos)
    }
    @Test fun `trailing orb expiry keeps the base memory ordering without path extensions`() {
        for (feeling in listOf(false)) {
            val interval = AkkhaCombat.blastInterval(feeling)
            val baseMemoryEnd = 9 + 4 * interval
            val orbExpiry = AkkhaCombat.trailDuration(feeling) + 6
            assertTrue(orbExpiry > baseMemoryEnd)
            assertEquals(29, orbExpiry) // No longer extended by path levels.
        }
    }
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
        assertEquals(7888, ToaScale.hp(400, c.copy(raidLevel = 600, partySize = 8, pathLevel = 6)))
        assertEquals(137, ToaScale.damage(55, c.copy(raidLevel = 600, pathLevel = 6)))
    }
    @Test fun `special is seventh attack and obsolete Stay Vigilant cannot shorten the cycle`() {
        assertEquals(6, AkkhaCombat.autosBeforeSpecial(false))
        assertEquals(6, AkkhaCombat.autosBeforeSpecial(true))
        assertEquals(CombatClass.RANGED, AkkhaCombat.nextStyle(CombatClass.MELEE))
        assertEquals(CombatClass.MAGIC, AkkhaCombat.nextStyle(CombatClass.RANGED))
        assertEquals(CombatClass.MELEE, AkkhaCombat.nextStyle(CombatClass.MAGIC))
    }
    @Test fun `four thresholds and base memory speed ignore obsolete path or Feeling Special flags`() {
        assertEquals(listOf(320, 240, 160, 80), (0..3).map { AkkhaCombat.shadowThreshold(it, 400) })
        assertEquals(List(7) { 0 }, (0..6).map(AkkhaCombat::extraMemoryBursts))
        assertEquals(4, AkkhaCombat.blastInterval(false)); assertEquals(4, AkkhaCombat.blastInterval(true))
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
    @Test fun `Akkha freeze immunity uses the engine freeze gate and is cleared on encounter close`() {
        val f = ToaTestFixture(); val root = Npc(1, Tile(3200, 3200), f.world)
        f.world.spawn(root)
        val s = AkkhaCombat.Session(root, ToaTestContext())
        s.configure()
        var applied = 0
        assertFalse(root.freeze(32) { applied++ })
        assertEquals(0, applied)
        assertFalse(root.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER))
        s.close(); assertFalse(root.timers.has(gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER))
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
        every { world.gameContext.npcStatCount } returns 5
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
        every { npc.equipmentBonuses } returns IntArray(18)
        every { world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, any()) } answers {
            gg.rsmod.game.fs.def.NpcDef(secondArg()).apply { combatLevel = 100; size = 1 }
        }
        val blocks = gg.rsmod.game.sync.block.UpdateBlockSet()
        gg.rsmod.game.sync.block.UpdateBlockType.values().forEach { blocks.updateBlocks[it] = gg.rsmod.game.sync.block.UpdateBlockStructure(1, emptyList()) }
        every { world.npcUpdateBlocks } returns blocks
        var index = 10
        every { world.spawn(any<Npc>()) } answers { firstArg<Npc>().index = ++index; true }
        every { world.remove(any<Npc>()) } answers { firstArg<Npc>().index = -1 }
    }
}
