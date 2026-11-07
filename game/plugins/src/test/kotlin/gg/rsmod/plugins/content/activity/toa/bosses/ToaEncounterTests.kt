package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.*

class ToaEncounterTests {
    @Test fun `attached lookup caches current and late entrants without scanning any world NPC slots`() {
        val f = ToaTestFixture()
        every { f.world.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        val members = mutableListOf<Player>()
        fun player() = mockk<Player>(relaxed = true).also {
            every { it.world } returns f.world
            every { it.attr } returns gg.rsmod.game.model.attr.AttributeMap()
            every { it.isOnline } returns true
            every { it.isDead() } returns false
            every { it.tile } returns Tile(3200, 3200)
        }
        val first = player(); val late = player(); members += first
        val session = ToaEncounter.attach(f.npc, ToaTestContext(players = members)) { npc, context ->
            object : ToaBossSession(npc, context) {
                override val stats = AkkhaCombat.STATS
                override fun configure() {}
                override fun onTick() {}
            }
        }
        try {
            assertSame(session, first.attr[ToaEncounter.PLAYER_SESSION])
            assertNull(first.attr[ToaEncounter.SESSION], "caching a raid participant must not transfer ownership to the boss debugger")
            assertNull(ToaEncounter.forPlayer(late))
            members += late
            repeat(100) {
                assertSame(session, ToaEncounter.forPlayer(first))
                assertSame(session, ToaEncounter.forPlayer(late))
            }
            assertSame(session, late.attr[ToaEncounter.PLAYER_SESSION])
            members.remove(late)
            assertNull(ToaEncounter.forPlayer(late))
            assertNull(late.attr[ToaEncounter.PLAYER_SESSION])
            every { first.isDead() } returns true
            assertNull(ToaEncounter.forPlayer(first))
            every { first.isDead() } returns false
            assertSame(session, ToaEncounter.forPlayer(first))
        } finally { session.close() }
        assertNull(first.attr[ToaEncounter.PLAYER_SESSION])
        assertNull(ToaEncounter.forPlayer(first))
        verify(exactly = 0) { f.world.npcs }
        verify(exactly = 0) { f.world.players }
    }

    @Test fun `closing an old encounter preserves a newer players session and worlds are isolated`() {
        val a = ToaTestFixture(); val b = ToaTestFixture()
        every { a.world.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        every { b.world.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        val p = mockk<Player>(relaxed = true)
        every { p.world } returns a.world
        every { p.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        every { p.isOnline } returns true; every { p.isDead() } returns false
        every { p.tile } returns Tile(3200, 3200)
        fun attach(f: ToaTestFixture) = ToaEncounter.attach(f.npc, ToaTestContext(players = listOf(p))) { npc, c ->
            object : ToaBossSession(npc, c) {
                override val stats = AkkhaCombat.STATS
                override fun configure() {}
                override fun onTick() {}
            }
        }
        val old = attach(a)
        val otherRoot = mockk<Npc>(relaxed = true)
        every { otherRoot.world } returns a.world
        every { otherRoot.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        every { otherRoot.tile } returns Tile(3200, 3200)
        every { otherRoot.isSpawned() } returns true; every { otherRoot.isDead() } returns false
        val newer = ToaEncounter.attach(otherRoot, ToaTestContext(players = listOf(p))) { npc, c ->
            object : ToaBossSession(npc, c) {
                override val stats = AkkhaCombat.STATS
                override fun configure() {}
                override fun onTick() {}
            }
        }
        old.close()
        assertSame(newer, ToaEncounter.forPlayer(p))
        newer.close()
        val foreign = attach(b)
        // A Player belongs to one world: a foreign context must never be returned from its registry.
        assertNull(ToaEncounter.forPlayer(p))
        assertNull(p.attr[ToaEncounter.PLAYER_SESSION])
        foreign.close()
        verify(exactly = 0) { a.world.npcs; b.world.npcs }
    }

    @Test fun `head icon clones retain per npc baked animations while real form transmogs use their own rig`() {
        val ids = gg.rsmod.plugins.content.activity.toa.ToaIds
        io.mockk.mockkObject(ids)
        try {
            val f = ToaTestFixture()
            io.mockk.every { f.npc.id } returns 15106
            io.mockk.every { f.npc.getTransmogId() } returns 15203
            io.mockk.every { ids.osrsNpcOf(15203) } returns null
            io.mockk.every { ids.osrsNpcOf(15106) } returns 11789
            io.mockk.every { ids.npcSeq(11789, 9770) } returns 21001
            assertEquals(21001, ToaBossFx.seqFor(f.npc, 9770))
            io.mockk.every { f.npc.getTransmogId() } returns 15129
            io.mockk.every { ids.osrsNpcOf(15129) } returns 11756
            io.mockk.every { ids.npcSeq(11756, 9770) } returns 21002
            assertEquals(21002, ToaBossFx.seqFor(f.npc, 9770))
        } finally { io.mockk.unmockkObject(ids) }
    }
    @Test fun `ordinary standalone cleanup removes a live root only once`() {
        val f = ToaTestFixture(); val root = Npc(1, Tile(3200, 3200), f.world)
        f.world.spawn(root)
        val s = AkkhaCombat.Session(root, ToaTestContext())
        s.finishStandalone(root); s.finishStandalone(root)
        assertFalse(root.isSpawned()); verify(exactly = 1) { f.world.remove(root) }
    }
    @Test fun `runtime definition assignment refreshes real equipment bonuses for root and spawned minion`() {
        val f = ToaTestFixture()
        var definition = gg.rsmod.game.model.combat.NpcCombatDef.DEFAULT
        every { f.npc.combatDef } answers { definition }
        every { f.npc.combatDef = any() } answers { definition = firstArg() }
        val s = AkkhaCombat.Session(f.npc, ToaTestContext())
        s.configure()
        assertEquals(115, f.npc.equipmentBonuses[gg.rsmod.plugins.api.BonusSlot.ATTACK_SLASH.id])
        assertEquals(30, f.npc.equipmentBonuses[gg.rsmod.plugins.api.ext.NPC_STRENGTH_BONUS_INDEX])
        assertEquals(115, f.npc.equipmentBonuses[3]); assertEquals(120, f.npc.equipmentBonuses[6])
        val child = assertNotNull(s.spawn(ToaBossNpcs.AKKHA_SHADOW, Tile(3205, 3205), AkkhaCombat.SHADOW_STATS))
        assertEquals(115, child.equipmentBonuses[3]); assertEquals(120, child.equipmentBonuses[6])
        assertEquals(70, child.getCurrentLifepoints()); assertFalse(child.combatDef.completesEncounter)
        s.close(); assertFalse(child.isSpawned())
    }
    @Test fun `a target leaving the room before impact never receives the queued hit`() {
        val f = ToaTestFixture(); val p = mockk<Player>(relaxed = true)
        every { p.isOnline } returns true; every { p.isDead() } returns false
        every { p.tile } returns Tile(3200, 3200)
        val participants = mutableListOf(p)
        val s = AkkhaCombat.Session(f.npc, ToaTestContext(players = participants))
        s.attack(p, 10, null, delay = 3, accuracy = false)
        participants.clear(); repeat(5) { s.cycle() }
        verify(exactly = 0) { p.addHit(any()) }
    }
    @Test fun `source ids cannot silently be sent as local packet ids`() {
        assertNotEquals(ToaBossNpcs.AKKHA, assertNotNull(ToaBossNpcs.local(ToaBossNpcs.AKKHA)))
        assertNotEquals(9784, ToaBossFx.seq(9784))
        assertEquals(-1, ToaBossFx.seq(Int.MAX_VALUE)); assertNull(ToaBossFx.spot(Int.MAX_VALUE))
        assertNull(ToaBossNpcs.local(Int.MAX_VALUE))
    }
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
        assertEquals(List(7) { 100 }, (0..6).map(ToaScale::pathPercent))
    }
    @Test fun `simplified raid discards old invocation and path effects while retaining live membership`() {
        val members = mutableListOf<Player>()
        val c = ToaTestContext(players = members, raidLevel = 300, pathLevel = 6,
            invocations = setOf("Quiet Prayers", "Deadly Prayers", "Insanity", "Stay Vigilant"))
        val s = AkkhaCombat.Session(ToaTestFixture().npc, c)
        assertEquals(0, s.context.pathLevel); assertTrue(s.context.invocations.isEmpty())
        assertFalse(s.inv("Quiet Prayers")); assertFalse(s.inv("Stay Vigilant"))
        assertEquals(220, ToaScale.hp(100, c)); assertEquals(220, ToaScale.damage(100, c))
        val p = mockk<Player>(relaxed = true); members += p
        assertEquals(listOf(p), s.context.players)
        members.clear(); assertTrue(s.context.players.isEmpty())
    }
    @Test fun `Entry Normal and Expert use the OSRS raid scale for HP damage and combat stats`() {
        for ((level, expected) in listOf(0 to 100, 150 to 160, 300 to 220)) {
            val c = ToaTestContext(raidLevel = level)
            assertEquals(expected, ToaScale.hp(100, c))
            assertEquals(expected, ToaScale.damage(100, c))
            assertEquals(expected, ToaScale.stat(100, c))
        }
    }
    @Test fun `raid scope owns child spawning for credit and a rejected spawn never enters the session`() {
        val f = ToaTestFixture(); var credited = 0
        val c = object : ToaEncounterContext by ToaTestContext() {
            override fun spawnChild(npc: Npc): Boolean { credited++; return f.world.spawn(npc) }
        }
        val s = AkkhaCombat.Session(f.npc, c)
        assertNotNull(s.spawn(ToaBossNpcs.AKKHA_SHADOW, Tile(3205, 3205), AkkhaCombat.SHADOW_STATS))
        assertEquals(1, credited); assertEquals(1, s.children.size)
        val denied = AkkhaCombat.Session(f.npc, object : ToaEncounterContext by ToaTestContext() {
            override fun spawnChild(npc: Npc) = false
        })
        assertNull(denied.spawn(ToaBossNpcs.AKKHA_SHADOW, Tile(3205, 3205), AkkhaCombat.SHADOW_STATS))
        assertTrue(denied.children.isEmpty())
        s.close(); denied.close()
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
    @Test fun `solo duo and eight-player pending attacks recheck logout death and plane transfer`() {
        for (size in listOf(1, 2, 8)) {
            val f = ToaTestFixture()
            var eligible = true
            val members = List(size) { i -> mockk<Player>(relaxed = true).also { p ->
                every { p.isOnline } answers { eligible || i % 3 != 0 }
                every { p.isDead() } answers { !eligible && i % 3 == 1 }
                every { p.tile } answers { Tile(3200, 3200, if (!eligible && i % 3 == 2) 1 else 0) }
            } }
            val s = object : ToaBossSession(f.npc, ToaTestContext(players = members, partySize = size)) {
                override val stats = AkkhaCombat.STATS
                override fun onTick() {}
            }
            members.forEach { p -> assertTrue(s.eligible(p)); s.attack(p, 10, null, delay = 2, accuracy = false) }
            eligible = false
            repeat(4) { s.cycle() }
            members.forEach { p -> verify(exactly = 0) { p.addHit(any()) } }
            s.close()
        }
    }
    @Test fun `minion melee reach uses its complete footprint without a diagonal or plane shortcut`() {
        val f = ToaTestFixture(); val s = AkkhaCombat.Session(f.npc, ToaTestContext())
        val child = mockk<Npc>(relaxed = true)
        every { child.world } returns f.world
        every { child.tile } returns Tile(3205, 3205); every { child.getSize() } returns 3
        assertTrue(s.actorMeleeReach(child, Tile(3208, 3207)))
        assertTrue(s.actorMeleeReach(child, Tile(3206, 3206)))
        assertFalse(s.actorMeleeReach(child, Tile(3208, 3208)))
        assertFalse(s.actorMeleeReach(child, Tile(3209, 3207)))
        assertFalse(s.actorMeleeReach(child, Tile(3208, 3207, 1)))
        every { f.world.collision.isBlocked(any(), any(), false) } returns true
        assertFalse(s.actorMeleeReach(child, Tile(3208, 3207)), "an adjacent minion cannot melee through a wall")
        every { f.world.collision.isBlocked(any(), any(), false) } returns false
        assertTrue(s.actorMeleeReach(child, Tile(3208, 3207)))
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
