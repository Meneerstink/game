package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.*
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.*
import kotlin.test.*

/** Real raid/progress/participants/scene/escrow; combat clears are controlled callbacks.
 * enterChallenge/enterWardens exercise the controller API, not walking to the Nexus doors.
 * Physical return of a respawned spectator from the puzzle checkpoint is an OPEN Claude arena-route gate in N462.
 */
class ToaRaidFlowTests {
    @BeforeTest fun recovery() {
        mockkObject(ToaRecovery)
        every { ToaRecovery.full(any()) } just Runs
        every { ToaRecovery.challenge(any()) } just Runs
        every { ToaRecovery.bossEntry(any()) } just Runs
    }
    @AfterTest fun cleanup() { unmockkObject(ToaRecovery) }

    @Test fun `solo completes four paths Wardens and personal chest after wipe and boss death`() = flow(1, 0)
    @Test fun `duo completes four paths Wardens and personal chests after wipe and boss death`() = flow(2, 150)
    @Test fun `eight players complete four paths Wardens and personal chests after wipe and boss death`() = flow(8, 300)

    private fun flow(size: Int, level: Int) {
        val f = Fixture(size)
        val raid = ToaRaid(size.toLong(), f.players.map { it.uid }, ToaRaidSettings(level), f.map, f.world)
        f.players.forEach { f.tiles[it.uid] = ToaMap.instance(f.map, ToaMap.NEXUS) }
        val starts = mutableListOf<ToaRoomRun>()
        val cleanups = mutableListOf<Int>()
        val clears = mutableListOf<ToaRoomKey>()
        val keys = ToaPath.values().flatMap { listOf(ToaRoomKey(it, false), ToaRoomKey(it, true)) } + ToaRoomKey(null, true)
        val registrations = keys.map { key -> ToaRooms.replaceForTest(key) { active -> object : ToaRoom {
            override fun start(run: ToaRoomRun) {
                starts += run
                active.scope(run)!!.onClose { cleanups += run.generation }
            }
            override fun clear(run: ToaRoomRun) { clears += run.key }
        } } }
        // Initialise the production chest hook before temporarily supplying deterministic test loot.
        ToaChest.install()
        val oldRoller = ToaRewards.roller
        val oldCompleted = ToaRaidEvents.completed
        val oldShow = ToaUi.showRewards
        val oldClaimed = ToaRewards.claimed
        val rolls = hashMapOf<PlayerUID, Int>()
        val windows = mutableListOf<PlayerUID>()
        var completions = 0
        ToaRewards.roller = ToaRewardRoller { context ->
            assertEquals(raid.rewardToken, context.raidToken)
            assertEquals(size, context.roster.size)
            assertEquals(1 + if (size == 1) 1 else 0, context.failedAttempts)
            rolls[context.player] = (rolls[context.player] ?: 0) + 1
            listOf(Item(995, 100 + (context.player.value as Int)))
        }
        ToaRaidEvents.completed = { actual, record ->
            assertSame(raid, actual); assertEquals(raid.rewardToken, record.raidToken); completions++
        }
        ToaUi.showRewards = { windows += it.uid }
        ToaRewards.claimed = { _, _ -> }
        fun clear() {
            val run = assertNotNull(raid.progress.current)
            val scope = assertNotNull(raid.scope(run))
            assertTrue(scope.active)
            f.players.forEach { raid.points.damage(it.uid, 1_500) }
            f.cycle++
            assertTrue(run.clear(f.cycle))
            assertFalse(run.clear(f.cycle), "duplicate clear must have no side effects")
            assertFalse(scope.active)
        }
        fun wipeAndRetry(): ToaRoomRun {
            val old = raid.progress.current!!
            val scope = raid.scope(old)!!
            f.players.forEach { p ->
                val before = raid.points.total(p.uid)
                val deaths = raid.points.deaths(p.uid)
                f.dead += p.uid
                raid.deathStart(p); raid.deathStart(p)
                assertEquals(deaths + 1, raid.points.deaths(p.uid))
                assertEquals(maxOf(0, before - maxOf(before / 5, 1_000)), raid.points.total(p.uid))
            }
            assertTrue(raid.activePlayers().isEmpty())
            f.cycle++; raid.cycle()
            assertNull(raid.progress.current, "retry waits for every death animation")
            assertFalse(scope.active)
            f.players.forEach { f.dead -= it.uid; raid.respawn(it) }
            f.cycle++; raid.cycle()
            val retry = assertNotNull(raid.progress.current)
            assertEquals(old.key, retry.key)
            assertNotEquals(old.generation, retry.generation)
            assertFalse(old.clear(f.cycle), "stale callbacks cannot clear the retry")
            assertFalse(old.wipe(f.cycle))
            assertEquals(size, raid.activePlayers().size)
            // A walled boss arena waits for its teleport crystal again after the wipe (0feb684a7).
            if (retry.key.boss && retry.key.path in ToaRaid.ARENA_ENTRY) f.players.forEach { assertTrue(raid.crystal(it, retry.key.path)) }
            return retry
        }
        try {
            raid.prepare(f.players)
            val firstChest = chest(f.map, 0)
            assertFalse(raid.openChest(f.players[0], firstChest))
            f.players.forEach { assertFalse(raid.enterWardens(it)) }
            // Vary the order as well as the mode: every path has to work in every position in the state machine.
            val paths = ToaPath.values().toList().let { it.drop(size % 4) + it.take(size % 4) }
            paths.forEachIndexed { index, path ->
                f.players.forEach { assertFalse(raid.enterBoss(it, path)) }
                f.players.forEach { assertTrue(raid.enterChallenge(it, path)) }
                assertEquals(size, raid.activePlayers().size)
                clear()
                f.players.forEach { assertTrue(raid.enterBoss(it, path)) }
                f.players.forEach { player ->
                    assertTrue(raid.crystal(player, path))
                    assertEquals(ToaRaid.ARENA_ENTRY.getValue(path), ToaMap.source(f.map, player.tile))
                }
                if (index == 1) {
                    val previousPaths = raid.progress.completedPaths.toSet()
                    wipeAndRetry()
                    assertEquals(previousPaths, raid.progress.completedPaths)
                    assertTrue(raid.progress.challengeCleared(path))
                }
                if (index == 2) {
                    if (size == 1) wipeAndRetry() else {
                        val run = raid.progress.current!!
                        val p = f.players.first()
                        val before = raid.points.total(p.uid)
                        f.dead += p.uid
                        raid.deathStart(p); raid.deathStart(p)
                        assertEquals(before - maxOf(before / 5, 1_000), raid.points.total(p.uid))
                        assertEquals(size - 1, raid.activePlayers().size)
                        f.dead -= p.uid; raid.respawn(p)
                        assertFalse(raid.canFight(p))
                        assertFalse(raid.enterBoss(p, path), "a dead entrant spectates until this boss clears")
                        f.cycle++; raid.cycle()
                        assertSame(run, raid.progress.current)
                        assertEquals(1, raid.progress.failedAttempts)
                    }
                }
                clear()
                // A respawned spectator is at the puzzle checkpoint: after the clear the boss door takes it to the boss room
                // (Osmumten), and Proceed then works for everybody.
                f.players.filter { ToaMap.source(f.map, it.tile)?.regionId != path.bossEntry.regionId }.forEach {
                    assertTrue(raid.enterBoss(it, path), "the boss door stays open after the clear")
                    assertEquals(path.bossEntry.regionId, ToaMap.source(f.map, it.tile)?.regionId)
                }
                f.players.forEach { assertTrue(raid.proceed(it)) }
                assertEquals(index + 1, raid.progress.completedPaths.size)
            }
            assertTrue(raid.progress.wardensUnlocked)
            assertEquals(ToaPath.values().toSet(), raid.progress.completedPaths)
            f.players.forEach { assertTrue(raid.enterWardens(it)) }
            // Strategies 1423: the Wardens wait for every party member to speak to Osmumten (Begin) in the walled entry corridor.
            f.players.forEach { assertTrue(raid.readyForWardens(it)) }
            assertEquals(size, raid.activePlayers().size)
            clear()
            assertTrue(raid.progress.finished)
            val completion = assertNotNull(raid.completion)
            assertEquals(1, completions)
            assertEquals(9, clears.size)
            assertEquals(9 + raid.progress.failedAttempts, starts.size)
            assertEquals(starts.map { it.generation }.sorted(), cleanups.sorted())
            assertEquals(cleanups.size, cleanups.toSet().size)
            f.players.forEachIndexed { index, player ->
                assertTrue(raid.crystal(player, null))
                assertEquals(ToaRaid.REWARD_ENTRY, ToaMap.source(f.map, player.tile))
                val obj = chest(f.map, index)
                if (size > 1) assertFalse(raid.openChest(player, chest(f.map, (index + 1) % size)))
                assertTrue(raid.openChest(player, obj)); assertTrue(raid.openChest(player, obj))
                assertEquals(1, rolls[player.uid])
                assertEquals(raid.rewardToken, ToaRewards.stored(player)!!.raidToken)
                ToaRewards.take(player, expectedToken = raid.rewardToken)
                ToaRewards.take(player, expectedToken = raid.rewardToken)
                assertEquals(100 + (player.uid.value as Int), player.inventory.getItemCount(995))
                assertFalse(ToaRewards.hasUnclaimed(player))
                assertTrue(raid.openChest(player, obj))
                assertEquals(1, rolls[player.uid], "an emptied chest must never reroll")
            }
            assertEquals(size * 3, windows.size)
            f.cycle += 100
            assertEquals(completion.elapsedCycles, raid.progress.elapsedCycles(f.cycle))
            f.players.forEach { assertFalse(raid.enterWardens(it)) }
        } finally {
            raid.close()
            registrations.asReversed().forEach { it.close() }
            ToaRewards.roller = oldRoller; ToaRewards.claimed = oldClaimed
            ToaRaidEvents.completed = oldCompleted; ToaUi.showRewards = oldShow
        }
    }

    private fun chest(map: InstancedMap, slot: Int): DynamicObject {
        // Cache2686 40-loc-placements: toa_vault_chest_loc0..7, in frozen roster order.
        val pins = listOf(Tile(3686, 5145), Tile(3673, 5145), Tile(3686, 5142), Tile(3673, 5142),
            Tile(3686, 5148), Tile(3673, 5148), Tile(3686, 5139), Tile(3673, 5139))
        return DynamicObject(ToaIds.loc(44786)!!, 10, if (slot % 2 == 0) 1 else 3, ToaMap.instance(map, pins[slot]))
    }

    private class Fixture(count: Int) {
        val world = mockk<World>(relaxed = true)
        val definitions = mockk<DefinitionSet>(relaxed = true)
        val tiles = hashMapOf<PlayerUID, Tile>()
        val dead = hashSetOf<PlayerUID>()
        var cycle = 0
        val players = (1..count).map { number ->
            val p = mockk<Player>(relaxed = true)
            val uid = PlayerUID(number)
            every { p.uid } returns uid; every { p.world } returns world
            every { p.attr } returns AttributeMap(); every { p.timers } returns TimerMap()
            every { p.username } returns "toa-flow-$number"
            every { p.tile } answers { tiles.getValue(uid) }
            every { p.isOnline } returns true
            every { p.isDead() } answers { uid in dead }
            every { p.teleportTo(any()) } answers { tiles[uid] = firstArg(); Unit }
            every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
            every { p.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)
            tiles[uid] = ToaMap.LOBBY
            p
        }
        val map = mockk<InstancedMap>(relaxed = true).also { map ->
            val area = Area(6400, 0, 6848, 448)
            every { map.area } returns area
            every { map.sourceTile(any()) } answers {
                val tile = firstArg<Tile>()
                if (!area.contains(tile)) null else Tile(tile.x - 6400 + ToaMap.SOURCE_BASE_X + ToaMap.CACHE_OFFSET_X,
                    tile.z + ToaMap.SOURCE_BASE_Z + ToaMap.CACHE_OFFSET_Z, tile.height)
            }
        }
        init {
            every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()).apply { stacks = true } }
            every { definitions.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
            every { world.definitions } returns definitions
            every { world.gameContext.npcStatCount } returns 5
            every { world.players } returns PawnList(arrayOfNulls<Player>(count + 1).also { slots ->
                players.forEachIndexed { index, player -> slots[index + 1] = player }
            })
            every { world.currentCycle } answers { cycle }
            var index = 10
            every { world.spawn(any<Npc>()) } answers { firstArg<Npc>().index = ++index; true }
            every { world.remove(any<Npc>()) } answers { firstArg<Npc>().index = -1 }
        }
    }
}
