package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.*
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import io.mockk.*
import kotlin.test.*

class ToaRuntimeTests {
    @BeforeTest fun recovery() {
        mockkObject(ToaRecovery)
        every { ToaRecovery.full(any()) } just Runs
        every { ToaRecovery.challenge(any()) } just Runs
        every { ToaRecovery.bossEntry(any()) } just Runs
    }
    @AfterTest fun cleanup() { unmockkObject(ToaRecovery); unmockkObject(ToaLobby.Companion) }

    @Test fun `raid queries use only cached roster and reconnect replaces the old player object`() {
        val f = Fixture(8)
        val raid = ToaRaid(1, f.players.take(2).map { it.uid }, ToaRaidSettings(), f.map(6400), f.world)
        f.players.forEach { f.tiles[it.uid] = ToaMap.instance(raid.map, ToaMap.NEXUS) }
        repeat(100) { assertEquals(f.players.take(2), raid.players()) }
        val old = f.players[0]
        raid.disconnect(old)
        assertEquals(listOf(f.players[1]), raid.players())
        val replacement = mockk<Player>(relaxed = true)
        every { replacement.uid } returns old.uid
        every { replacement.world } returns f.world
        every { replacement.isOnline } returns true
        every { replacement.isDead() } returns false
        every { replacement.tile } answers { f.tiles.getValue(old.uid) }
        every { replacement.teleportTo(any()) } answers { f.tiles[old.uid] = firstArg(); Unit }
        raid.reconnect(replacement)
        assertEquals(listOf(replacement, f.players[1]), raid.players())
        raid.remove(old.uid)
        raid.reconnect(old)
        assertEquals(listOf(f.players[1]), raid.players())
        verify(exactly = 1) { f.world.players }
        raid.close()
        assertTrue(raid.players().isEmpty())
    }

    @Test fun `room starts once after landing and dead entrants cannot reenter or fight`() {
        val f = Fixture(2)
        val raid = ToaRaid(1, f.players.map { it.uid }, ToaRaidSettings(), f.map(6400), f.world)
        f.players.forEach { f.tiles[it.uid] = ToaMap.instance(raid.map, ToaMap.NEXUS) }
        var starts = 0
        val registration = ToaRooms.replaceForTest(ToaRoomKey(ToaPath.HET, false)) { active -> object : ToaRoom {
            override fun start(run: ToaRoomRun) {
                starts++
                assertEquals(ToaPath.HET.challengeEntry, ToaMap.source(active.map, f.players[0].tile))
                assertTrue(f.players[0] in active.activePlayers())
            }
        } }
        try {
            f.players.forEach { assertTrue(raid.enterChallenge(it, ToaPath.HET)) }
            assertEquals(1, starts)
            assertEquals(2, raid.activePlayers().size)
            val dead = f.players[0]
            raid.deathStart(dead); raid.respawn(dead)
            assertFalse(raid.canFight(dead))
            assertFalse(raid.enterChallenge(dead, ToaPath.HET))
            assertEquals(1, raid.activePlayers().size)
            assertTrue(raid.progress.current!!.clear(f.cycle))
            assertEquals(0, raid.progress.failedAttempts)
            verify { ToaRecovery.challenge(f.players[1]) }
            f.players.forEach { assertTrue(raid.enterBoss(it, ToaPath.HET)) }
            assertTrue(raid.progress.current!!.clear(f.cycle))
            verify { ToaRecovery.full(f.players[0]); ToaRecovery.full(f.players[1]) }
        } finally { registration.close(); raid.close() }
    }

    @Test fun `wipe waits for death animations then retries only failed room`() {
        val f = Fixture(2)
        val raid = ToaRaid(1, f.players.map { it.uid }, ToaRaidSettings(), f.map(6400), f.world)
        f.players.forEach { f.tiles[it.uid] = ToaMap.instance(raid.map, ToaMap.NEXUS) }
        f.players.forEach { raid.enterChallenge(it, ToaPath.HET) }
        raid.progress.current!!.clear(0)
        f.players.forEach { raid.enterBoss(it, ToaPath.HET) }
        raid.progress.current!!.clear(0)
        f.players.forEach { raid.enterChallenge(it, ToaPath.SCABARAS) }
        val old = raid.progress.current!!
        f.players.forEach { f.dead += it.uid; raid.deathStart(it) }
        f.cycle++
        raid.cycle()
        assertNull(raid.progress.current)
        assertEquals(1, raid.progress.failedAttempts)
        assertEquals(setOf(ToaPath.HET), raid.progress.completedPaths)
        f.players.forEach { f.dead -= it.uid; raid.respawn(it) }
        f.cycle++; raid.cycle()
        val retry = assertNotNull(raid.progress.current)
        assertEquals(old.key, retry.key)
        assertNotEquals(old.generation, retry.generation)
        assertEquals(2, raid.activePlayers().size)
        assertFalse(old.clear(f.cycle))
        raid.close()
    }

    @Test fun `two groups retain returning members and release independently on teleport or last logout`() {
        val f = Fixture(4)
        mockkObject(ToaLobby.Companion)
        every { ToaLobby.canEnter(any()) } returns true
        val a = f.map(6400); val b = f.map(6912)
        val allocator = f.world.instanceAllocator // verify on the mock itself: a getter inside verify{} counts as a call
        every { f.world.instanceAllocator.allocate(f.world, any(), any()) } returnsMany listOf(a, b)
        val lobby = ToaLobby.of(f.world)
        val (p1, p2, p3, p4) = f.players
        lobby.create(p1.uid); assertTrue(lobby.join(p2.uid, p1.uid))
        lobby.create(p3.uid); assertTrue(lobby.join(p4.uid, p3.uid))
        val first = assertNotNull(lobby.start(p1)); val second = assertNotNull(lobby.start(p3))
        assertNotSame(first.map, second.map)
        f.tiles[p1.uid] = ToaMap.LOBBY // allocator logout runs before the plugin hook
        lobby.logout(p1)
        verify(exactly = 0) { allocator.release(f.world, a) }
        lobby.login(p1)
        assertTrue(first.contains(p1))
        lobby.leaveRaid(p2)
        f.tiles[p1.uid] = Tile(3200, 3200) // an external spell/item teleport
        lobby.cycle(); lobby.cycle()
        assertNull(lobby.raid(p1.uid))
        verify(exactly = 1) { allocator.release(f.world, a) }
        assertTrue(second.contains(p3)); assertTrue(second.contains(p4))
        verify(exactly = 0) { allocator.release(f.world, b) }
        for (p in listOf(p3, p4)) { f.tiles[p.uid] = ToaMap.LOBBY; lobby.logout(p) }
        verify(exactly = 1) { allocator.release(f.world, b) }
        assertNull(lobby.raid(p3.uid)); assertNull(lobby.raid(p4.uid))
    }

    @Test fun `Wardens zero HP creates one completion record and freezes the clock before animation`() {
        val f = Fixture(1)
        val p = f.players.single()
        val raid = ToaRaid(1, listOf(p.uid), ToaRaidSettings(), f.map(6400), f.world)
        f.tiles[p.uid] = ToaMap.instance(raid.map, ToaMap.NEXUS)
        val previous = ToaRaidEvents.completed
        var completions = 0
        ToaRaidEvents.completed = { actual, result -> assertSame(raid, actual); assertEquals(raid.rewardToken, result.raidToken); completions++ }
        try {
            ToaPath.values().forEach { path ->
                raid.enterChallenge(p, path); f.cycle++; raid.progress.current!!.clear(f.cycle)
                raid.enterBoss(p, path); f.cycle++; raid.progress.current!!.clear(f.cycle)
            }
            assertTrue(raid.enterWardens(p))
            val final = raid.progress.current!!
            f.cycle++
            assertTrue(final.clear(f.cycle)); assertFalse(final.clear(f.cycle))
            val record = assertNotNull(raid.completion)
            assertEquals(1, completions)
            assertEquals(f.cycle, record.elapsedCycles)
            f.cycle += 100
            assertEquals(record.elapsedCycles, raid.progress.elapsedCycles(f.cycle))
        } finally { ToaRaidEvents.completed = previous; raid.close() }
    }

    private class Fixture(count: Int) {
        val world = mockk<World>(relaxed = true)
        val tiles = hashMapOf<PlayerUID, Tile>()
        val dead = hashSetOf<PlayerUID>()
        var cycle = 0
        val players = (1..count).map { number ->
            val p = mockk<Player>(relaxed = true)
            val uid = PlayerUID(number)
            every { p.uid } returns uid
            every { p.world } returns world
            every { p.attr } returns AttributeMap()
            every { p.tile } answers { tiles.getValue(uid) }
            every { p.isOnline } returns true
            every { p.isLogoutPending } returns false
            every { p.isDead() } answers { uid in dead }
            every { p.teleportTo(any()) } answers { tiles[uid] = firstArg(); Unit }
            val definitions = mockk<DefinitionSet>(relaxed = true)
            every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
            every { p.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)
            tiles[uid] = ToaMap.LOBBY
            p
        }
        init {
            every { world.players } returns PawnList(arrayOfNulls<Player>(10).also { slots ->
                players.forEachIndexed { index, player -> slots[index + 1] = player }
            })
            every { world.currentCycle } answers { cycle }
        }
        fun map(x: Int): InstancedMap = mockk<InstancedMap>(relaxed = true).also { map ->
            val area = Area(x, 0, x + 448, 448)
            every { map.area } returns area
            every { map.sourceTile(any()) } answers {
                val tile = firstArg<Tile>()
                if (!area.contains(tile)) null else Tile(tile.x - x + ToaMap.SOURCE_BASE_X + ToaMap.CACHE_OFFSET_X,
                    tile.z + ToaMap.SOURCE_BASE_Z + ToaMap.CACHE_OFFSET_Z, tile.height)
            }
        }
    }
}
