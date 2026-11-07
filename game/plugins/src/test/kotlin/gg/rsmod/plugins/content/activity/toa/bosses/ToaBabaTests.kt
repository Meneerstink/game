package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.content.combat.PlayerAttackRules
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class ToaBabaTests {
    @Test fun `a player can pass through a boulder side or rear without another front knockback`() {
        val rock = Tile(3815, 5402)
        val west = gg.rsmod.game.model.Direction.WEST
        for (z in 5402..5404) {
            assertTrue(BabaCombat.rollingContact(Tile(3815, z), rock, 3, west))
            assertFalse(BabaCombat.rollingContact(Tile(3816, z), rock, 3, west))
            assertFalse(BabaCombat.rollingContact(Tile(3817, z), rock, 3, west))
        }
        assertFalse(BabaCombat.rollingContact(Tile(3815, 5405), rock, 3, west))
        assertFalse(BabaCombat.rollingContact(Tile(3815, 5403, 1), rock, 3, west))
        assertTrue(BabaCombat.rollingContact(Tile(3817, 5403), rock, 3, gg.rsmod.game.model.Direction.EAST))
        assertTrue(BabaCombat.rollingContact(Tile(3816, 5402), rock, 3, gg.rsmod.game.model.Direction.SOUTH))
        assertTrue(BabaCombat.rollingContact(Tile(3816, 5404), rock, 3, gg.rsmod.game.model.Direction.NORTH))
    }
    @Test fun `default melee cutoff admits the walkable western throne edge and resumes normal combat`() {
        val f = ToaTestFixture()
        every { f.npc.spawnTile } returns Tile(3804, 5406)
        every { f.npc.tile } returns Tile(3819, 5406)
        every { f.npc.getSize() } returns 5
        val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false
        every { p.equipmentBonuses } returns IntArray(18)
        every { p.tile } returns Tile(3817, 5408)
        val s = BabaCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        s.rolling = true
        s.incoming(Hit.Builder().addHit(1, HitType.MELEE.id).build().also { it.source = p })
        assertTrue(s.rolling, "a melee hit before the current spawn cutoff must not skip boulders")
        every { p.tile } returns Tile(3818, 5408)
        s.incoming(Hit.Builder().addHit(1, HitType.RANGE.id).build().also { it.source = p })
        assertTrue(s.rolling, "ranged from the throne edge must not skip boulders")
        s.incoming(Hit.Builder().addHit(0, HitType.BLOCK.id).build().also {
            it.source = p; it.sourceCombatClass = gg.rsmod.game.model.combat.CombatClass.MELEE
        })
        assertFalse(s.rolling, "even a melee miss at the reachable throne edge ends Rolling Stones")
        assertTrue(s.boulders.isEmpty())
        var resumed = 0
        s.specialDirector = { resumed++ }
        repeat(BabaCombat.STATS.speed + 1) { s.cycle() }
        assertEquals(1, resumed, "ordinary melee and its special counter must resume after boulders")
        assertSame(p, s.target)
        every { p.tile } returns Tile(3810, 5408)
        s.cycle()
        io.mockk.verify { f.npc.walkTo(Tile(3810, 5408)) }
        s.close()
    }
    @Test fun `default rolling goes from the cached eastern throne toward the western entrance`() {
        val f = ToaTestFixture()
        every { f.npc.getCurrentLifepoints() } returns 253; every { f.npc.getMaximumLifepoints() } returns 380
        every { f.world.collision.canStep(any<gg.rsmod.game.model.Tile>(), gg.rsmod.game.model.Direction.WEST) } returns true
        val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false; every { p.tile } returns Tile(3210, 3206)
        val s = BabaCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        s.cycle()
        io.mockk.verify { f.npc.moveTo(Tile(3215, 3200)) } // throne dais SW (origin +15, 07-11 testclient: borders the floor)
        io.mockk.verify { p.teleportTo(Tile(3192, 3206)) }
        assertTrue(s.rolling)
        s.close()
    }
    @Test fun `boulder collisions use the complete footprint including its far edge`() {
        val rock = Tile(100, 100)
        assertTrue(BabaCombat.overlaps(rock, 3, Tile(102, 102), 1))
        assertFalse(BabaCombat.overlaps(rock, 3, Tile(103, 102), 1))
        assertFalse(BabaCombat.overlaps(rock, 3, Tile(101, 101, 1), 1))
        assertTrue(BabaCombat.overlaps(rock, 3, Tile(102, 102), 3))
    }
    @Test fun `all eight cached sarcophagi retain their north south facing`() {
        assertEquals(8, BabaCombat.COFFINS_OSRS.size)
        assertEquals(setOf(3803, 3808, 3813, 3818), BabaCombat.COFFINS_OSRS.keys.map { it.x }.toSet())
        assertTrue(BabaCombat.COFFINS_OSRS.all { (tile, facing) -> if (tile.z == 5400) facing == 3 else tile.z == 5416 && facing == 1 })
    }
    @Test fun `crossing rolling threshold invokes active director once and suspends autos`() {
        val f = ToaTestFixture(); every { f.npc.getCurrentLifepoints() } returns 253
        every { f.npc.getMaximumLifepoints() } returns 380
        val s = BabaCombat.Session(f.npc, ToaTestContext())
        var starts = 0; s.rollingDirector = { starts++ }
        repeat(10) { s.cycle() }
        assertEquals(1, starts); assertEquals(1, s.rollingStage); assertTrue(s.rolling)
    }
    @Test fun `default baboon count remains within published one to four for all groups`() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4), (1..8).map(BabaCombat::baboonCount))
        assertEquals(BabaCombat.rollingInterval(true), BabaCombat.rollingInterval(false))
    }
    @Test fun `2025 melee prayer fully blocks Ba-Ba and ignores obsolete Quiet Prayers`() {
        assertEquals(0, ToaProtection.damage(24, true))
        assertEquals(24, ToaProtection.damage(24, false))
        assertEquals(0, ToaProtection.damage(24, true, quiet = true))
        assertEquals(24, ToaProtection.damage(24, false, quiet = true))
    }
    @Test fun `melee max and six tick speed follow context without a group damage multiplier`() {
        assertEquals(24, BabaCombat.maxHit(ToaTestContext()))
        assertEquals(60, BabaCombat.maxHit(ToaTestContext(raidLevel = 600, partySize = 8)))
        assertEquals(60, BabaCombat.maxHit(ToaTestContext(raidLevel = 600, partySize = 8, pathLevel = 6)))
        assertEquals(6, BabaCombat.STATS.speed)
        assertEquals(gg.rsmod.game.model.combat.StyleType.CRUSH, BabaCombat.STATS.definition(ToaTestContext()).attackStyleType)
    }
    @Test fun `rolling thresholds use thirds and can each trigger only once`() {
        assertFalse(BabaCombat.rollingThreshold(0, 254, 380))
        assertTrue(BabaCombat.rollingThreshold(0, 253, 380))
        assertFalse(BabaCombat.rollingThreshold(1, 127, 380))
        assertTrue(BabaCombat.rollingThreshold(1, 126, 380))
        assertFalse(BabaCombat.rollingThreshold(2, 0, 380))
    }
    @Test fun `ten boulder rows each have one legal cracked lane reachable from previous lane`() {
        assertTrue(BabaCombat.validCracks(listOf(1, 3, 4, 2, 0, 2, 3, 1, 0, 2)))
        assertFalse(BabaCombat.validCracks(listOf(0, 1, 2, 3, 4, 3, 2, 1, 2, 3)), "initial safe lane is one of the middle three")
        assertFalse(BabaCombat.validCracks(listOf(1, 4, 3, 2, 1, 2, 3, 2, 1, 0)), "may not shift three columns")
        assertFalse(BabaCombat.validCracks(List(9) { 2 }))
        assertEquals(List(7) { 25 }, (0..6).map(BabaCombat::crackedHp))
    }
    @Test fun `boulder health follows its published half group scaling and ignores raid level`() {
        assertEquals(listOf(25, 36, 47, 55, 62, 70, 77, 85), (1..8).map(BabaCombat::boulderHp))
        assertFailsWith<IllegalArgumentException> { BabaCombat.boulderHp(9) }
    }
    @Test fun `rock throw spends blocker health and all blocker kinds null the player hit`() {
        assertEquals(10, BabaCombat.blockerDamage(BabaCombat.BlockerKind.DEBRIS))
        assertEquals(30, BabaCombat.blockerDamage(BabaCombat.BlockerKind.SARCOPHAGUS))
        assertEquals(12, BabaCombat.blockerDamage(BabaCombat.BlockerKind.BABOON))
        assertEquals(3, BabaCombat.BlockerKind.values().size, "players do not provide Rock Throw cover")
    }
    @Test fun `melee beyond spawn line ends rolling but a projectile cannot end it`() {
        val f = ToaTestFixture(); val s = BabaCombat.Session(f.npc, ToaTestContext())
        val p = mockk<Player>(relaxed = true)
        s.rolling = true; s.meleePastSpawnLine = { true }
        val ranged = Hit.Builder().addHit(10, HitType.RANGE.id).build().also { it.source = p }
        s.incoming(ranged); assertTrue(s.rolling)
        val melee = Hit.Builder().addHit(10, HitType.MELEE.id).build().also { it.source = p }
        s.incoming(melee); assertFalse(s.rolling)
    }
    @Test fun `death or reset invalidates a pending rolling row and removes room hazards`() {
        val f = ToaTestFixture(); val s = BabaCombat.Session(f.npc, ToaTestContext())
        s.rolling = true
        var rowSpawns = 0
        s.later(2) { rowSpawns++ }; s.endRolling(); repeat(5) { s.cycle() }
        assertEquals(0, rowSpawns)
        s.later(2) { rowSpawns++ }; s.close(); repeat(5) { s.cycle() }
        assertEquals(0, rowSpawns); assertTrue(s.boulders.isEmpty())
    }
    @Test fun `baboon max is decided before roll for points and XP and foreign players cannot damage it`() {
        val f = ToaTestFixture(); val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false
        every { p.tile } returns Tile(3200, 3200)
        val s = BabaCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        s.summonBaboons(listOf(Tile(3205, 3205)))
        val baboon = s.baboons.single()
        assertEquals(PlayerAttackRules.Rule.MAX_HIT, ToaEncounter.playerAttackRule(p, baboon, HitType.MELEE))
        assertEquals(PlayerAttackRules.Rule.MAX_HIT, ToaEncounter.playerAttackRule(p, baboon, HitType.MAGIC))
        val outsider = mockk<Player>(relaxed = true)
        val foreign = Hit.Builder().addHit(10, HitType.MELEE.id).build().also { it.source = outsider; it.sourceMaxHit = 42 }
        baboon.hitModifier!!.invoke(foreign)
        assertEquals(0, foreign.hitmarks.single().damage)
        assertNull(ToaEncounter.playerAttackRule(outsider, baboon, HitType.MELEE))
        s.close(); assertFalse(baboon.isSpawned())
        assertNull(ToaEncounter.playerAttackRule(p, baboon, HitType.MELEE))
    }
    @Test fun `cracked boulders guarantee ranged max before the roll only`() {
        val f = ToaTestFixture(); val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false
        every { p.tile } returns Tile(3200, 3200)
        val s = BabaCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        // The import table is owned by a concurrent import chain; this rule test only needs a scoped child actor.
        val rock = gg.rsmod.game.model.entity.Npc(2, Tile(3205, 3205), f.world)
        f.world.spawn(rock); rock.attr[ToaEncounter.MINION_SESSION] = s; s.children += rock
        s.guaranteeMaxHit(rock, rangedOnly = true)
        assertEquals(PlayerAttackRules.Rule.MAX_HIT, ToaEncounter.playerAttackRule(p, rock, HitType.RANGE))
        assertNull(ToaEncounter.playerAttackRule(p, rock, HitType.MELEE))
        assertNull(ToaEncounter.playerAttackRule(p, rock, HitType.MAGIC))
    }
    @Test fun `a launched melee miss ends rolling even if its splat is BLOCK`() {
        val f = ToaTestFixture(); val s = BabaCombat.Session(f.npc, ToaTestContext())
        val p = mockk<Player>(relaxed = true)
        s.rolling = true; s.meleePastSpawnLine = { true }
        val hit = Hit.Builder().addHit(0, HitType.BLOCK.id).build().also { it.source = p; it.sourceCombatClass = gg.rsmod.game.model.combat.CombatClass.MELEE }
        s.incoming(hit); assertFalse(s.rolling)
    }
    @Test fun `unproven baboon team mapping cannot silently create a fifth minion`() {
        val s = BabaCombat.Session(ToaTestFixture().npc, ToaTestContext())
        assertFailsWith<IllegalArgumentException> { s.summonBaboons(List(5) { Tile(3200 + it, 3200) }) }
    }
}
