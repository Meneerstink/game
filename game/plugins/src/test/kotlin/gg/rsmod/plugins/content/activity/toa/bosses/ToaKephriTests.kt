package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class ToaKephriTests {
    @Test fun `Agile hatch ignores raid level while preserving infobox group health`() = withToaNpcMappings(11728, 11729) {
        val s = KephriCombat.Session(ToaTestFixture().npc, ToaTestContext(partySize = 8, raidLevel = 600, pathLevel = 6))
        s.autoDirector = {}; s.specialDirector = {}
        s.incubation(mapOf(Tile(3205, 3205) to true), 2, 24, 2); repeat(3) { s.cycle() }
        val scarab = s.agile.single()
        assertEquals(174, scarab.getCurrentLifepoints()); assertEquals(40, scarab.stats.getCurrentLevel(4))
        assertEquals(5, scarab.stats.getCurrentLevel(2)); s.close()
    }
    @Test fun `arcane follows specific one-hit39 threshold and third player hit includes a miss`() {
        val f = ToaTestFixture(); val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false; every { p.tile } returns Tile(3200, 3200)
        val s = KephriCombat.Session(f.npc, ToaTestContext(players = listOf(p), raidLevel = 150))
        val start = Tile(3208, 3196); val next = Tile(3196, 3208)
        val scarab = assertNotNull(s.spawnOverlord(ToaBossNpcs.ARCANE, start))
        fun hit(damage: Int, type: gg.rsmod.plugins.api.HitType = gg.rsmod.plugins.api.HitType.MELEE) {
            scarab.hitModifier!!.invoke(gg.rsmod.game.model.Hit.Builder().addHit(damage, type.id).build().also { it.source = p; it.sourceMaxHit = 50 })
        }
        hit(20); hit(20)
        assertEquals(start, scarab.tile, "two20s in four ticks are not one39+ hit")
        hit(0, gg.rsmod.plugins.api.HitType.BLOCK)
        assertEquals(next, scarab.tile)
        hit(39); assertNotEquals(next, scarab.tile)
        s.close()
    }
    @Test fun `default specials hatch only two dark eggs and reset removes the resulting scarabs`() = withToaNpcMappings(11728, 11729) {
        val f = ToaTestFixture(); val s = KephriCombat.Session(f.npc, ToaTestContext())
        repeat(49) { s.cycle() } // specials at fourth/eighth6tick auto: dung tick18, eggs42, hatch48.
        assertTrue(s.eggs.isEmpty()); assertEquals(2, s.agile.size)
        val children = s.agile.toList()
        s.close()
        assertTrue(children.none { it.isSpawned() }); assertTrue(s.children.isEmpty())
    }
    @Test fun `raid extremes scale Kephri health and damage while legacy paths never add variants`() {
        for (path in listOf(0, 6)) {
            val solo = ToaTestContext(pathLevel = path)
            val group = ToaTestContext(raidLevel = 600, partySize = 8, pathLevel = path)
            assertEquals(150, KephriCombat.STATS.definition(solo).lifepoints)
            assertEquals(2958, KephriCombat.STATS.definition(group).lifepoints)
            assertEquals(272, KephriCombat.STATS.definition(group).stats[2])
            assertEquals(24, ToaScale.damage(24, solo)); assertEquals(60, ToaScale.damage(24, group))
            assertEquals(8, ToaProtection.damage(24, true, 1, 3))
            assertEquals(20, ToaProtection.damage(60, true, 1, 3))
        }
    }
    @Test fun `specific Soldier swarm restores final health independently of passive regeneration`() {
        val f = ToaTestFixture(); val root = gg.rsmod.game.model.entity.Npc(1, Tile(3200, 3200), f.world)
        f.world.spawn(root)
        val s = KephriCombat.Session(root, ToaTestContext())
        s.configure(); s.phase = KephriCombat.Phase.FINAL
        assertNotNull(s.spawnOverlord(ToaBossNpcs.SOLDIER, Tile(3205, 3205)))
        root.setCurrentLifepoints(40)
        repeat(9) { s.cycle() }
        assertEquals(54, root.getCurrentLifepoints()) // two full-health Soldier pulses of7 at ticks0/8.
        s.close()
    }
    @Test fun `eggs and swarms select max damage before accuracy for points and XP`() = withToaNpcMappings(11723, 11728, 11729) {
        val f = ToaTestFixture(); val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false; every { p.tile } returns Tile(3200, 3200)
        val s = KephriCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        s.incubation(mapOf(Tile(3205, 3205) to true), 6, 24, 2)
        val egg = s.eggs.keys.single()
        assertEquals(gg.rsmod.plugins.content.combat.PlayerAttackRules.Rule.MAX_HIT,
            ToaEncounter.playerAttackRule(p, egg, gg.rsmod.plugins.api.HitType.MELEE))
        assertEquals(gg.rsmod.plugins.content.combat.PlayerAttackRules.Rule.MAX_HIT,
            ToaEncounter.playerAttackRule(p, egg, gg.rsmod.plugins.api.HitType.RANGE))
        s.shieldBreaks = 1; s.phase = KephriCombat.Phase.RECHARGE
        s.swarms(List(28) { (if (it == 0) 0 else 1000) to Tile(3208, 3208) }, List(28) { 0 }, { 10 }, 172)
        val swarm = s.children.single { it !== egg }
        assertEquals(40, swarm.attr[gg.rsmod.plugins.content.combat.formula.ElementalWeakness.INSTANCE]!!.percent)
        assertEquals(gg.rsmod.plugins.content.combat.PlayerAttackRules.Rule.MAX_HIT,
            ToaEncounter.playerAttackRule(p, swarm, gg.rsmod.plugins.api.HitType.MAGIC))
        s.close()
        assertNull(ToaEncounter.playerAttackRule(p, egg, gg.rsmod.plugins.api.HitType.MAGIC))
    }
    @Test fun `the specific egg source makes dark eggs hatch without a light egg explosion`() = withToaNpcMappings(11728, 11729) {
        val f = ToaTestFixture(); val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false; every { p.tile } returns Tile(3205, 3205)
        val s = KephriCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        s.autoDirector = {}; s.specialDirector = {}
        s.incubation(mapOf(Tile(3205, 3205) to true), 2, 24, 2)
        repeat(3) { s.cycle() }
        assertEquals(1, s.agile.size)
        io.mockk.verify(exactly = 0) { p.addHit(any()) }
        s.close()
    }
    @Test fun `removed participant cannot hatch into a target and reset removes every egg`() = withToaNpcMappings(11728, 11729) {
        val f = ToaTestFixture(); val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false; every { p.tile } returns Tile(3200, 3200)
        val s = KephriCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        s.incubation(mapOf(Tile(3205, 3205) to true), 2, 24, 2)
        val egg = s.eggs.keys.single()
        every { p.isOnline } returns false
        assertNull(ToaEncounter.playerAttackRule(p, egg, gg.rsmod.plugins.api.HitType.MELEE))
        s.close(); repeat(4) { s.cycle() }
        assertFalse(egg.isSpawned()); assertTrue(s.agile.isEmpty())
    }
    @Test fun `autonomous shield phase keeps six tick fireball cadence before a special`() {
        val f = ToaTestFixture(); val s = KephriCombat.Session(f.npc, ToaTestContext())
        var autos = 0; var specials = 0
        s.autoDirector = { autos++ }; s.specialDirector = { specials++ }
        repeat(25) { s.cycle() }
        assertEquals(5, autos); assertEquals(5, specials)
        assertEquals(KephriCombat.Phase.SHIELD, s.phase)
    }
    @Test fun `Kephri carries the kalphite tag used by the Keris combat formulas`() {
        assertTrue(gg.rsmod.plugins.api.NpcSpecies.KALPHITE in KephriCombat.STATS.definition(ToaTestContext()).species)
    }
    @Test fun `source infobox shield is150 and final health is80, not summary200`() {
        assertEquals(150, KephriCombat.STATS.hp)
        assertEquals(6, KephriCombat.STATS.speed)
        assertEquals(80, KephriCombat.STATS.defence)
    }
    @Test fun `overlords follow base shield breaks even with an obsolete More Overlords flag`() {
        assertEquals(setOf(ToaBossNpcs.SPITTING), KephriCombat.overlords(1, false))
        assertEquals(setOf(ToaBossNpcs.SPITTING), KephriCombat.overlords(1, true))
        assertEquals(setOf(ToaBossNpcs.SOLDIER, ToaBossNpcs.ARCANE), KephriCombat.overlords(2, false))
        assertEquals(setOf(ToaBossNpcs.SOLDIER, ToaBossNpcs.ARCANE), KephriCombat.overlords(2, true))
        assertTrue(KephriCombat.overlords(3, true).isEmpty())
    }
    @Test fun `first intermission schedule is28 swarms, second is24 without invented final batch`() {
        assertEquals(28, KephriCombat.swarmCount(1)); assertEquals(24, KephriCombat.swarmCount(2))
        assertEquals(18, KephriCombat.healingSwarms(1)); assertEquals(14, KephriCombat.healingSwarms(2))
        assertEquals(List(4) { 4 } + List(4) { 3 } + List(4) { 2 } + List(16) { 1 }, (0..27).map(KephriCombat::firstSwarmInterval))
        assertFailsWith<IllegalArgumentException> { KephriCombat.firstSwarmInterval(28) }
    }
    @Test fun `misses count toward arcane relocation but party size changes threshold from3 to10`() {
        assertEquals(3, KephriCombat.arcaneHitCount(1)); assertEquals(10, KephriCombat.arcaneHitCount(8))
    }
    @Test fun `old Blowing Mud flag cannot add targets to simplified dung rotation`() {
        val f = ToaTestFixture()
        val party = List(3) { mockk<Player>(relaxed = true) }
        party.forEach { p -> every { p.isOnline } returns true; every { p.isDead() } returns false; every { p.tile } returns Tile(3200, 3200) }
        val s = KephriCombat.Session(f.npc, ToaTestContext(players = party, partySize = 3, invocations = setOf("Blowing Mud")))
        assertEquals(listOf(party[0]), s.dungTargets())
        assertEquals(listOf(party[1]), s.dungTargets())
        assertEquals(listOf(party[2]), s.dungTargets())
        val solo = KephriCombat.Session(f.npc, ToaTestContext(players = party.take(1), invocations = setOf("Blowing Mud")))
        assertEquals(party.take(1), solo.dungTargets())
    }
    @Test fun `third shield break enters final phase once, with no fourth recharge`() {
        val f = ToaTestFixture(); val s = KephriCombat.Session(f.npc, ToaTestContext())
        s.breakShield(); assertEquals(KephriCombat.Phase.RECHARGE, s.phase)
        s.finishRecharge(100); s.breakShield(); assertEquals(KephriCombat.Phase.RECHARGE, s.phase)
        s.finishRecharge(100); s.breakShield(); assertEquals(KephriCombat.Phase.FINAL, s.phase)
        assertEquals(3, s.shieldBreaks)
        s.breakShield(); assertEquals(3, s.shieldBreaks)
    }
    @Test fun `bomber autos continue during recharge while shield specials are suspended`() {
        val s = KephriCombat.Session(ToaTestFixture().npc, ToaTestContext())
        s.phase = KephriCombat.Phase.RECHARGE
        var bombers = 0; var specials = 0
        s.autoDirector = { bombers++ }; s.specialDirector = { specials++ }
        repeat(13) { s.cycle() }
        assertEquals(3, bombers); assertEquals(0, specials)
    }
    @Test fun `overlord defence stays80 atRL600 and minions retain Kalphite and Fire50 weakness`() {
        val s = KephriCombat.Session(ToaTestFixture().npc, ToaTestContext(raidLevel = 600, partySize = 8, pathLevel = 6))
        val child = assertNotNull(s.spawnOverlord(ToaBossNpcs.ARCANE, Tile(3205, 3205)))
        assertEquals(80, child.combatDef.stats[2]); assertEquals(80, child.stats.getCurrentLevel(2))
        assertEquals(340, child.combatDef.stats[3])
        assertTrue(gg.rsmod.plugins.api.NpcSpecies.KALPHITE in child.combatDef.species)
        val weakness = child.attr[gg.rsmod.plugins.content.combat.formula.ElementalWeakness.INSTANCE]!!
        assertEquals(50, weakness.percent)
        s.close(); assertFalse(child.isSpawned())
    }
    @Test fun `real lethal shield hits never start NPC death and the third break leaves80 real health`() {
        val f = ToaTestFixture()
        val root = gg.rsmod.game.model.entity.Npc(1, Tile(3200, 3200), f.world)
        f.world.spawn(root)
        val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false
        every { p.tile } returns Tile(3200, 3200)
        val s = KephriCombat.Session(root, ToaTestContext(players = listOf(p)))
        s.configure()
        fun strike(amount: Int) {
            val hit = gg.rsmod.game.model.Hit.Builder().addHit(amount, gg.rsmod.plugins.api.HitType.MELEE.id).build()
            hit.source = p; hit.sourceMaxHit = amount
            root.addHit(hit); root.hitsCycle()
        }
        strike(150)
        assertEquals(KephriCombat.Phase.RECHARGE, s.phase); assertEquals(1, root.getCurrentLifepoints())
        s.finishRecharge(100); strike(100)
        assertEquals(KephriCombat.Phase.RECHARGE, s.phase); assertEquals(1, root.getCurrentLifepoints())
        s.finishRecharge(100); strike(100)
        assertEquals(KephriCombat.Phase.FINAL, s.phase); assertEquals(80, root.getCurrentLifepoints())
        assertEquals(80, root.getMaximumLifepoints()); assertEquals(3, s.shieldBreaks)
        assertEquals(35, root.attr[gg.rsmod.plugins.content.combat.formula.ElementalWeakness.INSTANCE]!!.percent)
        s.close(); assertTrue(s.children.isEmpty())
    }
    @Test fun `reset clears dung collision callbacks exactly once and cancels eggs and swarms`() {
        val s = KephriCombat.Session(ToaTestFixture().npc, ToaTestContext())
        var removed = 0; var lateHatch = 0
        s.cleanupDung += { removed++ }; s.dung += Tile(3200, 3200)
        s.later(2) { lateHatch++ }; s.close(); s.close(); repeat(5) { s.cycle() }
        assertEquals(1, removed); assertEquals(0, lateHatch); assertTrue(s.dung.isEmpty())
    }
}

/** Mechanics tests use isolated local ids when Claude's concurrently generated import manifest is incomplete.
 * This supplies no cache data and proves no visual import. The real import preflight remains a separate handoff gate.
 */
internal fun withToaNpcMappings(vararg osrsIds: Int, block: () -> Unit) {
    val reader = gg.rsmod.plugins.content.activity.toa.ToaIds
    io.mockk.mockkObject(reader)
    try {
        every { reader.npc(any()) } answers {
            val source = firstArg<Int>()
            if (source in osrsIds) 20000 + source - 11600 else callOriginal()
        }
        block()
    } finally { io.mockk.unmockkObject(reader) }
}
