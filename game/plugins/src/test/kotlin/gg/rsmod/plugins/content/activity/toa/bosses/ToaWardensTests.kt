package gg.rsmod.plugins.content.activity.toa.bosses

import kotlin.test.*

class ToaWardensTests {
    @Test fun `nearly arrived P1 charged shot survives P2 transition but distant shots and reset do not`() {
        fun delivered(delay: Int, reset: Boolean = false): Int {
            val f = ToaTestFixture(); val p = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
            io.mockk.every { p.isOnline } returns true; io.mockk.every { p.isDead() } returns false
            io.mockk.every { p.tile } returns gg.rsmod.game.model.Tile(3200, 3200)
            val s = WardensCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
            s.p1Director = {}; s.p2Director = {}
            var hits = 0
            io.mockk.every { p.addHit(any()) } answers { hits++; Unit }
            s.chargedShot(listOf(p), WardensCombat.Warden.ELIDINIS, delay) { 30 }
            repeat(2) { s.cycle() }
            s.startPhaseTwo()
            if (reset) s.close()
            repeat(delay + 1) { s.cycle() }
            s.close()
            return hits
        }
        assertEquals(1, delivered(6), "orb with4ticks left must still hit after the obelisk falls")
        assertEquals(0, delivered(10), "orb with8ticks left must be cancelled")
        assertEquals(0, delivered(6, reset = true), "wipe/close must still cancel the carried shot")
    }
    @Test fun `windmill has all four arms instead of leaving two entire quadrants safe`() {
        val origin = gg.rsmod.game.model.Tile(3808, 5154, 1)
        val first = WardensCombat.windmillTiles(origin, 0)
        for (distance in listOf(1, 6, 12)) {
            for ((dx, dz) in listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0))
                assertTrue(origin.transform(dx * distance, dz * distance) in first)
        }
        assertFalse(origin in first)
        assertFalse(origin.transform(13, 0) in first)
        val diagonal = WardensCombat.windmillTiles(origin, 1)
        for ((dx, dz) in listOf(1 to 1, 1 to -1, -1 to -1, -1 to 1))
            assertTrue(origin.transform(dx * 6, dz * 6) in diagonal)
        assertFalse(origin.transform(6, 0) in diagonal)
        assertEquals(first, WardensCombat.windmillTiles(origin, 8))
    }
    @Test fun `P2 protection swaps on core return and P3 shows all prayers only during siphons`() {
        val icons = gg.rsmod.plugins.content.activity.toa.ToaHeadIcons
        io.mockk.mockkObject(icons)
        try {
            val shown = mutableListOf<Pair<gg.rsmod.game.model.entity.Npc, Int>>()
            val cleared = mutableListOf<gg.rsmod.game.model.entity.Npc>()
            io.mockk.every { icons.show(any(), any()) } answers { shown += firstArg<gg.rsmod.game.model.entity.Npc>() to secondArg<Int>(); true }
            io.mockk.every { icons.clear(any()) } answers { cleared += firstArg<gg.rsmod.game.model.entity.Npc>(); Unit }
            val f = ToaTestFixture(); val s = WardensCombat.Session(f.npc, ToaTestContext())
            s.startPhaseTwo(); assertEquals(8, shown.last().second)
            s.exposeCore(); assertTrue(s.npc in cleared)
            s.returnCore(); assertEquals(7, shown.last().second)
            s.exposeCore(); s.startPhaseThree()
            val p3 = s.npc; assertTrue(p3 in cleared)
            s.siphonDirector = { it.spawnSiphons(listOf(p3.tile.transform(5, 5)), 1, 1) { _, _ -> } }
            p3.setCurrentLifepoints(p3.getMaximumLifepoints() * 4 / 5)
            s.cycle(); assertEquals(p3 to 9, shown.last())
            val count = cleared.count { it === p3 }
            s.cycle(); assertEquals(count + 1, cleared.count { it === p3 })
            s.close()
        } finally { io.mockk.unmockkObject(icons) }
    }
    @Test fun `Wardens raid extremes include group health but no legacy path or invocation damage`() {
        for (path in listOf(0, 6)) {
            val solo = ToaTestContext(pathLevel = path)
            val group = ToaTestContext(raidLevel = 600, partySize = 8, pathLevel = path)
            assertEquals(880, WardensCombat.P3_STATS.definition(solo).lifepoints)
            assertEquals(17353, WardensCombat.P3_STATS.definition(group).lifepoints)
            assertEquals(41760, WardensCombat.coreHp(group))
            assertEquals(65, ToaScale.damage(26, group))
        }
    }
    @Test fun `P2 accuracy and core max hit are pre-roll rules tied to the correct role and style`() {
        val f = ToaTestFixture(); val p = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        io.mockk.every { p.isOnline } returns true; io.mockk.every { p.isDead() } returns false
        io.mockk.every { p.tile } returns gg.rsmod.game.model.Tile(3200, 3200)
        val s = WardensCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        f.npc.attr[ToaEncounter.SESSION] = s
        s.phase = WardensCombat.Phase.P2
        val rule = gg.rsmod.plugins.content.combat.PlayerAttackRules.Rule.ACCURATE
        assertEquals(rule, ToaEncounter.playerAttackRule(p, f.npc, gg.rsmod.plugins.api.HitType.RANGE))
        assertNull(ToaEncounter.playerAttackRule(p, f.npc, gg.rsmod.plugins.api.HitType.MAGIC))
        s.exposeCore()
        val core = assertNotNull(s.core)
        assertEquals(gg.rsmod.plugins.content.combat.PlayerAttackRules.Rule.MAX_HIT,
            ToaEncounter.playerAttackRule(p, core, gg.rsmod.plugins.api.HitType.MELEE))
        assertNull(ToaEncounter.playerAttackRule(p, core, gg.rsmod.plugins.api.HitType.RANGE))
        s.close()
        assertNull(ToaEncounter.playerAttackRule(p, core, gg.rsmod.plugins.api.HitType.MELEE))
    }
    @Test fun `P2 uses launch style even for a BLOCK splat after a weapon switch`() {
        val f = ToaTestFixture(); val s = WardensCombat.Session(f.npc, ToaTestContext())
        s.phase = WardensCombat.Phase.P2
        val blockedStyle = gg.rsmod.game.model.Hit.Builder().addHit(50, gg.rsmod.plugins.api.HitType.RANGE.id).build().also {
            it.sourceCombatClass = gg.rsmod.game.model.combat.CombatClass.MAGIC
        }
        s.incoming(blockedStyle)
        assertEquals(0, blockedStyle.hitmarks.single().damage)
        val allowed = gg.rsmod.game.model.Hit.Builder().addHit(0, gg.rsmod.plugins.api.HitType.BLOCK.id).build().also {
            it.sourceCombatClass = gg.rsmod.game.model.combat.CombatClass.RANGED; it.sourceMaxHit = 42
        }
        s.incoming(allowed)
        assertEquals(0, allowed.hitmarks.single().damage, "no uncredited impact-time reroll")
    }
    @Test fun `source translation retains centred obelisk offset and moves all eight players to the void`() {
        val f = ToaTestFixture()
        io.mockk.every { f.npc.spawnTile } returns gg.rsmod.game.model.Tile(5000, 6000, 1)
        io.mockk.every { f.npc.tile } returns gg.rsmod.game.model.Tile(5000, 6000, 1)
        val party = List(8) { io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true).also { p ->
            io.mockk.every { p.isOnline } returns true; io.mockk.every { p.isDead() } returns false
            io.mockk.every { p.tile } returns gg.rsmod.game.model.Tile(5000, 6000, 1)
        } }
        val s = WardensCombat.Session(f.npc, ToaTestContext(players = party, partySize = 8))
        assertEquals(gg.rsmod.game.model.Tile(5001, 6001, 1), s.fromSource(WardensCombat.OBELISK_CENTRE))
        assertEquals(gg.rsmod.game.model.Tile(4985, 5999, 1), s.chargingTile(WardensCombat.Warden.ELIDINIS))
        assertEquals(gg.rsmod.game.model.Tile(5014, 5999, 1), s.chargingTile(WardensCombat.Warden.TUMEKEN))
        assertEquals(0, WardensCombat.Session(ToaTestFixture().npc, ToaTestContext())
            .chargingTile(WardensCombat.Warden.ELIDINIS).height, "standalone statues remain on the debug player's plane")
        s.transferToPhaseThree()
        assertEquals(s.fromSource(WardensCombat.P3_CENTRE), s.centre)
        io.mockk.verify { f.npc.moveTo(s.fromSource(WardensCombat.P3_ORIGIN)) }
        party.forEach { p -> io.mockk.verify(exactly = 1) { p.teleportTo(any<gg.rsmod.game.model.Tile>()) } }
    }
    @Test fun `siphons use one ordinary accurate melee hit and reject ranged launch styles`() = withToaNpcMappings(11772) {
        val f = ToaTestFixture(); val p = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        io.mockk.every { p.isOnline } returns true; io.mockk.every { p.isDead() } returns false
        io.mockk.every { p.tile } returns gg.rsmod.game.model.Tile(3200, 3200)
        val s = WardensCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        s.phase = WardensCombat.Phase.SIPHONS
        s.spawnSiphons(listOf(gg.rsmod.game.model.Tile(3205, 3205)), 20, 20) { _, _ -> }
        val siphon = s.siphons.single()
        assertEquals(gg.rsmod.plugins.content.combat.PlayerAttackRules.Rule.ACCURATE,
            ToaEncounter.playerAttackRule(p, siphon, gg.rsmod.plugins.api.HitType.MELEE))
        val hit = gg.rsmod.game.model.Hit.Builder().addHit(20, gg.rsmod.plugins.api.HitType.MELEE.id).build().also {
            it.source = p; it.sourceCombatClass = gg.rsmod.game.model.combat.CombatClass.RANGED
        }
        siphon.hitModifier!!.invoke(hit); assertEquals(0, hit.hitmarks.single().damage)
        s.close(); assertFalse(siphon.isSpawned())
    }
    @Test fun `P3 director advances east west centre and suspends slams during siphons`() {
        val f = ToaTestFixture(); val s = WardensCombat.Session(f.npc, ToaTestContext())
        s.phase = WardensCombat.Phase.P3
        val slams = mutableListOf<WardensCombat.Slam>()
        s.slamDirector = { _, slam -> slams += slam }
        repeat(15) { s.cycle() }
        assertEquals(listOf(WardensCombat.Slam.EAST, WardensCombat.Slam.WEST, WardensCombat.Slam.CENTRE), slams)
        s.phase = WardensCombat.Phase.SIPHONS
        repeat(20) { s.cycle() }; assertEquals(3, slams.size)
    }
    @Test fun `P1 director is active each tick and P2 obeys the selected warden interval`() {
        val s = WardensCombat.Session(ToaTestFixture().npc, ToaTestContext())
        var charges = 0; var autos = 0
        s.p1Director = { charges++ }; s.p2Director = { autos++ }
        repeat(8) { s.cycle() }; assertEquals(8, charges)
        s.phase = WardensCombat.Phase.P2; s.phaseTwoWarden = WardensCombat.Warden.TUMEKEN
        repeat(17) { s.cycle() }; assertEquals(3, autos)
    }
    @Test fun `west disrupted warden is destroyed so west block selects Tumeken for P2`() {
        assertEquals(WardensCombat.Warden.TUMEKEN, WardensCombat.phaseTwo(true))
        assertEquals(WardensCombat.Warden.ELIDINIS, WardensCombat.phaseTwo(false))
        assertEquals(WardensCombat.Warden.ELIDINIS, WardensCombat.opposite(WardensCombat.phaseTwo(true)))
    }
    @Test fun `simplified P3 cadence stays seven ticks even with obsolete flags`() {
        assertEquals(7, WardensCombat.slamInterval(emptySet()))
        assertEquals(7, WardensCombat.slamInterval(setOf("Overclocked")))
        assertEquals(7, WardensCombat.slamInterval(setOf("Overclocked", "Overclocked 2")))
        assertEquals(7, WardensCombat.slamInterval(setOf("Overclocked", "Overclocked 2", "Insanity")))
    }
    @Test fun `combined slam leaves the centre column safe and side slams require opposite half`() {
        for (x in -8..8) {
            assertEquals(x >= 0, WardensCombat.slamHits(WardensCombat.Slam.EAST, x))
            assertEquals(x <= 0, WardensCombat.slamHits(WardensCombat.Slam.WEST, x))
            assertEquals(x != 0, WardensCombat.slamHits(WardensCombat.Slam.CENTRE, x))
        }
    }
    @Test fun `siphon health uses currently alive players and not initial party size`() {
        assertEquals(listOf(1, 1, 1, 2, 2, 3, 3, 4), (1..8).map(WardensCombat::siphonHp))
        assertFailsWith<IllegalArgumentException> { WardensCombat.siphonHp(0) }
    }
    @Test fun `core exposure boundaries use remaining true health, with fivefold transfer`() {
        assertEquals(21, WardensCombat.coreExposure(800, 1000))
        assertEquals(29, WardensCombat.coreExposure(799, 1000))
        assertEquals(29, WardensCombat.coreExposure(600, 1000))
        assertEquals(37, WardensCombat.coreExposure(599, 1000))
        assertEquals(275, WardensCombat.coreDamage(55))
        assertEquals(4500, WardensCombat.coreHp(ToaTestContext()))
        assertEquals(7200, WardensCombat.coreHp(ToaTestContext(raidLevel = 600)))
    }
    @Test fun `P2 begins with correct vulnerable style and switches only after core return`() {
        assertEquals(gg.rsmod.game.model.combat.CombatClass.MAGIC, WardensCombat.allowedStyle(WardensCombat.Warden.ELIDINIS, 0))
        assertEquals(gg.rsmod.game.model.combat.CombatClass.RANGED, WardensCombat.allowedStyle(WardensCombat.Warden.TUMEKEN, 0))
        assertEquals(gg.rsmod.game.model.combat.CombatClass.RANGED, WardensCombat.allowedStyle(WardensCombat.Warden.ELIDINIS, 1))
        assertEquals(gg.rsmod.game.model.combat.CombatClass.MAGIC, WardensCombat.allowedStyle(WardensCombat.Warden.TUMEKEN, 1))
    }
    @Test fun `obelisk special cannot repeat until all three were executed`() {
        val s = WardensCombat.Session(ToaTestFixture().npc, ToaTestContext())
        var count = 0
        s.obeliskSpecial(WardensCombat.ObeliskSpecial.CONVERGING) { count++ }
        assertFailsWith<IllegalArgumentException> { s.obeliskSpecial(WardensCombat.ObeliskSpecial.CONVERGING) { count++ } }
        s.obeliskSpecial(WardensCombat.ObeliskSpecial.WINDMILL) { count++ }
        s.obeliskSpecial(WardensCombat.ObeliskSpecial.LIGHTNING) { count++ }
        s.obeliskSpecial(WardensCombat.ObeliskSpecial.CONVERGING) { count++ }
        assertEquals(4, count)
    }
    @Test fun `phantoms follow surviving P3 warden, not the P2 one`() {
        assertEquals(listOf(11777, 11776), WardensCombat.phantoms(WardensCombat.Warden.ELIDINIS))
        assertEquals(listOf(11774, 11775), WardensCombat.phantoms(WardensCombat.Warden.TUMEKEN))
    }
    @Test fun `Akkha phantom starts ranged and switches only after three attacks`() {
        val r = gg.rsmod.game.model.combat.CombatClass.RANGED
        val m = gg.rsmod.game.model.combat.CombatClass.MAGIC
        assertEquals(listOf(r, r, r, m, m, m, r, r, r), (0..8).map(WardensCombat::akkhaPhantomStyle))
    }
    @Test fun `real engine hit processing survives P1 and P2 lethal hits and exposes the core`() {
        val f = ToaTestFixture()
        val root = gg.rsmod.game.model.entity.Npc(1, gg.rsmod.game.model.Tile(3200, 3200), f.world)
        f.world.spawn(root)
        val p = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        io.mockk.every { p.isOnline } returns true; io.mockk.every { p.isDead() } returns false
        io.mockk.every { p.tile } returns gg.rsmod.game.model.Tile(3200, 3200)
        val s = WardensCombat.Session(root, ToaTestContext(players = listOf(p)))
        s.configure()
        fun strike(amount: Int) {
            val target = s.npc
            val hit = gg.rsmod.game.model.Hit.Builder().addHit(amount, gg.rsmod.plugins.api.HitType.RANGE.id).build()
            hit.source = p; hit.sourceMaxHit = amount
            target.addHit(hit); target.hitsCycle()
        }
        strike(260)
        assertEquals(WardensCombat.Phase.P2, s.phase)
        assertEquals(140, s.npc.getCurrentLifepoints()); assertTrue(s.npc.scriptedDeath)
        assertEquals(1, root.getCurrentLifepoints()); assertTrue(root.isSpawned())
        strike(140)
        assertEquals(WardensCombat.Phase.CORE, s.phase)
        assertEquals(1, s.npc.getCurrentLifepoints())
        val core = assertNotNull(s.core); assertEquals(s.coreMaximum, core.getCurrentLifepoints())
        s.returnCore()
        assertEquals(WardensCombat.Phase.P2, s.phase); assertFalse(core.isSpawned())
        assertEquals(140, s.npc.getCurrentLifepoints()); assertEquals(1, s.returnedCores)
        val actor = s.npc
        s.close(); assertFalse(actor.scriptedDeath)
    }
    @Test fun `each Warden form is scoped separately for point multipliers and the root survives until final death`() {
        val f = ToaTestFixture(); val root = gg.rsmod.game.model.entity.Npc(1, gg.rsmod.game.model.Tile(3200, 3200), f.world)
        f.world.spawn(root)
        val scoped = mutableListOf<gg.rsmod.game.model.entity.Npc>()
        val c = object : ToaEncounterContext by ToaTestContext() {
            override fun spawnChild(npc: gg.rsmod.game.model.entity.Npc): Boolean { scoped += npc; return f.world.spawn(npc) }
        }
        val s = WardensCombat.Session(root, c); s.configure(); s.startPhaseTwo()
        val p2 = s.npc
        assertNotSame(root, p2); assertTrue(p2 in scoped)
        assertEquals(ToaBossNpcs.local(ToaBossNpcs.TUMEKEN_P2), p2.id)
        s.exposeCore(); s.startPhaseThree()
        val p3 = s.npc
        assertNotSame(p2, p3); assertTrue(p3 in scoped); assertFalse(p2.isSpawned())
        assertEquals(ToaBossNpcs.local(ToaBossNpcs.ELIDINIS_P3), p3.id)
        assertTrue(root.isSpawned()); assertFalse(root.attr[gg.rsmod.plugins.content.activity.toa.ToaBossRooms.ENCOUNTER_DONE] == true)
        p3.setCurrentLifepoints(0); s.phase = WardensCombat.Phase.ENRAGE; s.close()
        s.finishStandalone(root)
        assertTrue(p3.isSpawned(), "final actor remains for its engine death animation")
        assertTrue(root.isSpawned(), "adapter root remains until completion watcher sees the final despawn")
        s.completeAfterDespawn(p3)
        assertFalse(root.attr[gg.rsmod.plugins.content.activity.toa.ToaBossRooms.ENCOUNTER_DONE] == true)
        f.world.remove(p3); s.completeAfterDespawn(p3)
        assertTrue(root.attr[gg.rsmod.plugins.content.activity.toa.ToaBossRooms.ENCOUNTER_DONE] == true)
        s.finishStandalone(root); assertFalse(root.isSpawned())
    }
    @Test fun `only the first three P2 downs retain their normal damage maps for room credit`() {
        val f = ToaTestFixture(); val root = gg.rsmod.game.model.entity.Npc(1, gg.rsmod.game.model.Tile(3200, 3200), f.world)
        f.world.spawn(root)
        val p = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        val s = WardensCombat.Session(root, ToaTestContext()); s.configure(); s.startPhaseTwo()
        val actors = mutableListOf<gg.rsmod.game.model.entity.Npc>()
        repeat(4) { index ->
            val actor = s.npc; actors += actor; actor.damageMap.add(p, 50)
            s.exposeCore()
            if (index < 3) s.returnCore()
        }
        s.close()
        assertEquals(listOf(50, 50, 50, 0), actors.map { it.damageMap.getDamageFrom(p) })
    }
    @Test fun `multi-hit killing blow is clamped at enrage threshold and heals only once`() {
        val f = ToaTestFixture()
        val root = gg.rsmod.game.model.entity.Npc(1, gg.rsmod.game.model.Tile(3200, 3200), f.world)
        f.world.spawn(root)
        val p = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        io.mockk.every { p.isOnline } returns true; io.mockk.every { p.isDead() } returns false
        io.mockk.every { p.tile } returns gg.rsmod.game.model.Tile(3200, 3200)
        val s = WardensCombat.Session(root, ToaTestContext(players = listOf(p)))
        s.configure(); s.phase = WardensCombat.Phase.P3
        root.combatDef = WardensCombat.P3_STATS.definition(s.context); root.setCurrentLifepoints(50)
        val hit = gg.rsmod.game.model.Hit.Builder().addHit(60, gg.rsmod.plugins.api.HitType.RANGE.id).addHit(1, gg.rsmod.plugins.api.HitType.RANGE.id).build().also { it.source = p }
        root.addHit(hit); root.hitsCycle()
        assertEquals(WardensCombat.Phase.ENRAGE, s.phase)
        assertEquals(219, root.getCurrentLifepoints()) //5% threshold44 + one20% heal176.
        s.enterEnrage(); assertEquals(219, root.getCurrentLifepoints())
        assertEquals(180, root.stats.getCurrentLevel(2))
        s.close()
    }
    @Test fun `a second lethal splat across enrage releases the engine death gate`() {
        val f = ToaTestFixture(); val s = WardensCombat.Session(f.npc, ToaTestContext())
        s.phase = WardensCombat.Phase.P3
        io.mockk.every { f.npc.getMaximumLifepoints() } returns 880
        var hp = 50
        io.mockk.every { f.npc.getCurrentLifepoints() } answers { hp }
        io.mockk.every { f.npc.setCurrentLifepoints(any()) } answers { hp = firstArg() }
        val hit = gg.rsmod.game.model.Hit.Builder().addHit(60, gg.rsmod.plugins.api.HitType.RANGE.id)
            .addHit(300, gg.rsmod.plugins.api.HitType.RANGE.id).build()
        s.incoming(hit)
        assertTrue(s.enraged)
        io.mockk.verify { f.npc.scriptedDeath = false }
    }
    @Test fun `void adapter runs before any P3 floor is built and can move its geometry`() {
        val f = ToaTestFixture(); val s = WardensCombat.Session(f.npc, ToaTestContext())
        val placed = mutableListOf<gg.rsmod.game.model.entity.GameObject>()
        io.mockk.every { f.world.spawn(any<gg.rsmod.game.model.entity.GameObject>()) } answers { placed += firstArg<gg.rsmod.game.model.entity.GameObject>(); Unit }
        s.phase = WardensCombat.Phase.CORE
        s.phaseChanged = { phase ->
            assertEquals(WardensCombat.Phase.P3, phase); assertTrue(placed.isEmpty())
            s.origin = gg.rsmod.game.model.Tile(3300, 3310)
            s.centre = gg.rsmod.game.model.Tile(3300, 3300)
        }
        s.startPhaseThree()
        assertEquals(336, placed.size)
        assertTrue(placed.all { it.tile.x in 3290..3310 && it.tile.z in 3292..3307 })
        s.close()
    }
    @Test fun `enrage heal can occur only once and cancels a queued slam`() {
        val s = WardensCombat.Session(ToaTestFixture().npc, ToaTestContext())
        var slam = 0; s.later(2) { slam++ }
        s.enterEnrage(); val generation = s.generation
        s.enterEnrage(); assertEquals(generation, s.generation)
        repeat(5) { s.cycle() }; assertEquals(0, slam)
        assertEquals(WardensCombat.Phase.ENRAGE, s.phase)
    }
}
