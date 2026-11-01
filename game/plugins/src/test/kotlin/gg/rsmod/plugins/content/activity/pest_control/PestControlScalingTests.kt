package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N232: actual game waves, live departures, spinner placement and worst-case no-defence survival on all boats. */
class PestControlScalingTests {
    private class Fixture(tier: PestControlTier, count: Int, shielded: Boolean = false) {
        val world = mockk<World>(relaxed = true)
        val map = mockk<InstancedMap>(relaxed = true)
        val game: PestControlGame
        var hp = PC_KNIGHT_LIFEPOINTS

        init {
            every { map.area } returns Area(3200, 3200, 3263, 3263)
            every { world.collision.isClipped(any()) } returns false
            every { world.instanceAllocator.getMap(any()) } returns map
            game = spyk(PestControlGame(world, tier, map))
            val knight = pest(PC_KNIGHT_IDS[0], Tile(3232, 3232))
            game.knight = knight
            var modifier: ((Hit) -> Unit)? = null
            every { knight.hitModifier = any() } answers { modifier = firstArg() }
            every { knight.hitModifier } answers { modifier }
            game.configureKnightDamage()
            every { knight.getCurrentLifepoints() } answers { hp }
            every { knight.setCurrentLifepoints(any()) } answers { hp = firstArg() }
            every { knight.isDead() } answers { hp <= 0 }
            for (i in 0..3) {
                val (x, z) = PC_PORTAL_OFFSETS[i]
                val portal = pest((if (shielded) tier.shieldedPortalBaseId else tier.portalBaseId) + i, game.local(x, z))
                every { portal.getSize() } returns 3
                every { portal.getCentreTile() } returns portal.tile.transform(1, 1, 0)
                every { portal.getMaximumLifepoints() } returns tier.portalLifepoints
                every { portal.getCurrentLifepoints() } returns tier.portalLifepoints
                game.portals[i] = portal
            }
            repeat(count) {
                val player = mockk<Player>(relaxed = true)
                every { player.attr } returns AttributeMap().also { it[PestControlHandler.GAME_ATTR] = game }
                every { player.isOnline } returns true
                every { player.tile } returns game.local(34, 52) // AFK at arrival: no defence, no portal damage.
                game.players += player
            }
            // NPC definitions/world spawning are the boundary. All wave selection, caps and game ticks stay real.
            every { game.spawnMonster(any(), any(), any()) } answers {
                val npc = pest(firstArg(), secondArg())
                game.monsters += npc
                if (pestMonsterOf(npc.id) == PestMonster.SPINNER) game.spinnerPortal[npc] = thirdArg()
                npc
            }
            every { game.dropShield(any()) } just Runs // Portal transformation has its own shield tests.
        }

        fun impact(hit: Hit): Int {
            game.knight.hitModifier!!.invoke(hit)
            return hit.hitmarks.sumOf { it.damage }
        }

        fun hit(damage: Int = 1000): Hit = Hit.Builder().addHit(damage = damage, type = 0).setDamageDelay(3).build()

        fun pest(id: Int, tile: Tile): Npc {
            val npc = mockk<Npc>(relaxed = true)
            every { npc.id } returns id
            every { npc.tile } returns tile
            every { npc.attr } returns AttributeMap()
            every { npc.isSpawned() } returns true
            every { npc.getSize() } returns 1
            every { npc.hitModifier } returns null
            return npc
        }
    }

    @Test
    fun `one two and five players have bounded waves and direct attackers on every boat`() {
        for (tier in PestControlTier.values()) for (count in listOf(1, 2, 5)) {
            val f = Fixture(tier, count)
            repeat(100) { wave ->
                for (index in 0..3) f.game.spawnWave(index, f.game.portals[index]!!, Random(wave * 4 + index))
                repeat(5) { f.game.spawnKnightShifter() }
                assertTrue(f.game.monsters.size <= f.game.balance.populationCap, "$tier/$count population")
                for (index in 0..3) assertTrue(f.game.spinnerPortal.count { it.value == index } <= f.game.balance.spinnersPerPortal)
                assertTrue(f.game.monsters.count { pestMonsterOf(it.id) == PestMonster.BRAWLER } <= 2)
            }
            assertEquals(f.game.balance.populationCap, f.game.monsters.size)
            assertFalse(f.game.spawnKnightShifter())
        }
    }

    @Test
    fun `solo knight survives four minutes with all portals alive and every pest hitting maximum every tick`() {
        for (tier in PestControlTier.values()) {
            val f = Fixture(tier, 1)
            repeat(PestControlBalance.SOLO_MIN_SURVIVAL_TICKS) {
                f.game.tick()
                // Stronger than real combat: every pest lands 1000 raw HP every tick, including spinners/ravagers.
                repeat(f.game.monsters.size) { f.hp -= f.impact(f.hit()) }
                assertTrue(f.hp > 0, "$tier knight died at tick ${f.game.ticks}")
                assertTrue(f.game.active)
                assertTrue(f.game.monsters.size <= f.game.balance.populationCap)
            }
            assertTrue(f.hp >= 36, "$tier HP ${f.hp}")
            assertTrue(f.game.monsters.isNotEmpty(), "test must actually spawn attackers")
            assertEquals(4, f.game.portals.count { it?.isSpawned() == true })
        }
    }

    @Test
    fun `departure reduces existing pests and spinners and queued damage immediately without restarting the window`() {
        for (tier in PestControlTier.values()) {
            val f = Fixture(tier, 5)
            repeat(4) { f.game.spawnKnightShifter() }
            for (index in 0..3) repeat(3) {
                f.game.spawnMonster(PC_SPINNERS[tier.difficulty][0], f.game.spinnerTile(index)!!, index)
            }
            repeat(40) { f.game.spawnWave(it % 4, f.game.portals[it % 4]!!, Random(it)) }
            // A departure detected by tick (offline/walked out) must trim the old population too.
            val queued = f.hit() // Scheduled with five players, lands after the departure.
            f.game.players.drop(2).forEach { every { it.isOnline } returns false }
            f.game.tick()
            assertEquals(2, f.game.players.size)
            assertTrue(f.game.monsters.size <= f.game.balance.populationCap)
            assertEquals(8, f.impact(queued))
            f.game.removePlayer(f.game.players.last())
            assertEquals(1, f.game.players.size)
            assertTrue(f.game.monsters.size <= 12)
            assertTrue(f.game.spinnerPortal.values.groupingBy { it }.eachCount().values.all { it <= 1 })
            assertEquals(0, f.impact(f.hit()), "leaving cannot refill the shared damage window")
            verify(atLeast = 1) { f.world.remove(any<Npc>()) }
        }
    }

    @Test
    fun `every portal spinner is separate walkable visible and in heal range with either portal form`() {
        for (tier in PestControlTier.values()) for (shielded in listOf(true, false)) {
            val f = Fixture(tier, 5, shielded)
            for (index in 0..3) {
                val portal = f.game.portals[index]!!
                val blocked = PestControlBalance.spinnerTiles(portal.tile, 3).first()
                every { f.world.collision.isClipped(blocked) } returns true
                val tiles = (0 until 3).map {
                    val tile = f.game.spinnerTile(index)!!
                    f.game.spawnMonster(PC_SPINNERS[tier.difficulty][0], tile, index)
                    assertTrue(tile.isWithinRadius(portal.getCentreTile(), 5))
                    assertFalse(tile.x in portal.tile.x until portal.tile.x + 3 && tile.z in portal.tile.z until portal.tile.z + 3)
                    assertTrue(tile != blocked)
                    tile
                }
                assertEquals(3, tiles.distinct().size)
                // An old spinner caught under the portal is moved even if the portal is full and no heal roll succeeds.
                val hidden = f.game.spawnMonster(PC_SPINNERS[tier.difficulty][0], portal.tile.transform(1, 1, 0), index)
                PestControlMonsters.spinner(f.game, hidden, roll = 9)
                verify { hidden.moveTo(any()) }
                val distant = f.game.spawnMonster(PC_SPINNERS[tier.difficulty][0], f.game.local(32, 52), index)
                every { portal.getCurrentLifepoints() } returns 10
                PestControlMonsters.spinner(f.game, distant, roll = 0)
                val portalTile = portal.tile
                verify(exactly = 0) { distant.walkTo(portalTile) }
                verify { distant.walkTo(any()) }
            }
        }
    }

    @Test
    fun `impact hook shares one budget across hitmarks and games keep separate budgets`() {
        val f = Fixture(PestControlTier.VETERAN, 1)
        val other = Fixture(PestControlTier.VETERAN, 1)
        val multi = Hit.Builder().addHit(damage = 1000, type = 0).addHit(damage = 1000, type = 0).build()
        assertEquals(4, f.impact(multi))
        assertEquals(listOf(4, 0), multi.hitmarks.map { it.damage })
        assertEquals(0, f.impact(f.hit()))
        assertEquals(4, other.impact(other.hit()))
        assertEquals(0, f.impact(f.hit(0)))
        // Only the game's own knight is configured; portals and same-id NPCs have no damage modifier.
        assertTrue(f.game.portals.all { it!!.hitModifier == null })
        assertTrue(f.pest(PC_KNIGHT_IDS[0], f.game.knight.tile).hitModifier == null)
    }

    @Test
    fun `profiles scale monotonically and an undefended solo game can still be lost`() {
        for (tier in PestControlTier.values()) {
            val profiles = (1..25).map { PestControlBalance.profile(it, tier) }
            profiles.zipWithNext().forEach { (a, b) ->
                assertTrue(a.populationCap <= b.populationCap)
                assertTrue(a.waveTicks >= b.waveTicks)
                assertTrue(a.damageShare <= b.damageShare)
            }
        }
        val budget = PestControlBalance.KnightDamageBudget()
        var hp = PC_KNIGHT_LIFEPOINTS
        for (tick in 0 until PC_GAME_DURATION_TICKS) hp -= budget.apply(tick, 1000, 4)
        assertTrue(hp <= 0, "AFK must not guarantee the 20-minute win")
        assertEquals(0, budget.apply(PC_GAME_DURATION_TICKS, 0, 4))
    }
}
