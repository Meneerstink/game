package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy
import gg.rsmod.game.action.PawnPathAction
import gg.rsmod.plugins.content.activity.toa.ToaBossRooms
import gg.rsmod.plugins.content.activity.toa.ToaPath
import gg.rsmod.plugins.content.activity.toa.ToaRaid
import gg.rsmod.plugins.content.activity.toa.ToaScene
import gg.rsmod.plugins.content.combat.Combat
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class ToaZebakReachTests {
    @Test fun `centred nine by nine Zebak can be approached and meleed from the east platform`() {
        val f = ToaTestFixture()
        val spawn = ToaBossRooms.SPAWNS.getValue(ToaPath.CRONDIS)
        assertTrue(spawn.centred, "the old south-west interpretation placed the body four tiles onto the platform")
        assertEquals(Tile(3921, 5408), spawn.tile)
        // Wiki Zebak map centre + cache2686 2/9/11730 size9 -> SW3917,5404; east edge3925.
        val southWest = spawn.tile.transform(-4, -4)
        every { f.npc.tile } returns southWest
        every { f.npc.spawnTile } returns southWest
        every { f.npc.getSize() } returns 9
        val source = ToaSourceCollision(f.world, 15700)
        val collision = source.collision
        assertEquals(0, source.plane(3934, 5408, 1), "bridge flags put the dry arena on collision plane0")
        assertEquals(0, source.plane(3925, 5408, 1), "the water's walk blocking also belongs to plane0")
        assertEquals(Tile(3934, 5408), ToaRaid.ARENA_ENTRY.getValue(ToaPath.CRONDIS))
        assertEquals(Tile(3930, 5408), ToaScene.BOSS_OSMUMTEN.getValue(ToaPath.CRONDIS))
        val p = mockk<Player>(relaxed = true)
        val session = ZebakCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
        every { p.world } returns f.world
        every { p.getSize() } returns 1
        every { p.isOnline } returns true
        every { p.isDead() } returns false
        for (z in 5404..5412) {
            val approach = if (z == 5404) ToaRaid.ARENA_ENTRY.getValue(ToaPath.CRONDIS) else Tile(3930, z)
            val request = PathRequest.Builder().setPoints(approach, southWest).setSourceSize(1, 1)
                .setTargetSize(9, 9).setProjectilePath(false).setTouchRadius(1)
                .clipPathNodes(node = true, link = true).clipDiagonalTiles().clipOverlapTiles().build()
            val route = BFSPathFindingStrategy(collision).calculateRoute(request)
            assertTrue(route.success, "ordinary melee must find the east platform at z=$z")
            every { p.tile } returns route.tail
            assertTrue(Combat.inMeleeReach(p, f.npc, 1))
            assertEquals(3926, route.tail.x)
            assertTrue(route.tail.z in 5404..5412)
            // A shortest route may choose another square on the same edge; every pinned edge square must also allow the hit.
            every { p.tile } returns Tile(3926, z)
            assertTrue(Combat.inMeleeReach(p, f.npc, 1))
            assertTrue(session.meleeReach(p.tile), "Zebak must also be able to melee the player from the walk-blocked water")
            assertEquals(CombatClass.MELEE, session.autoSelector(session).style)
        }
        // 0feb684a7: the generic boss melee reach is checked from the player's tile, so the water-tile failure is gone there too.
        assertTrue(session.actorMeleeReach(f.npc, Tile(3926, 5408)), "the generic boss melee reach covers the east platform edge")
        every { p.tile } returns Tile(3927, 5408)
        assertFalse(Combat.inMeleeReach(p, f.npc, 1))
        assertFalse(session.meleeReach(p.tile))
        assertNotEquals(CombatClass.MELEE, session.autoSelector(session).style)
        assertTrue(Combat.inMeleeReach(p, f.npc, 2), "Chompington Wiki explicitly permits a halberd one tile behind melee")
        // The real approach compares footprints, unlike Combat.raycast's older south-west distance helper.
        // Cover the reported ranged failure with ordinary crossbow, blowpipe and long-range weapon reaches.
        for (range in listOf(8, 5, 10)) {
            val request = PathRequest.Builder().setPoints(ToaRaid.ARENA_ENTRY.getValue(ToaPath.CRONDIS), southWest)
                .setSourceSize(1, 1).setTargetSize(9, 9).setProjectilePath(true).setTouchRadius(range)
                .clipPathNodes(node = true, link = true).clipOverlapTiles().build()
            val route = BFSPathFindingStrategy(collision).calculateRoute(request)
            assertTrue(route.success, "ranged reach $range needs a real route from the crystal landing")
            every { p.tile } returns route.tail
            assertTrue(PawnPathAction.reachedMovingTarget(p, f.npc, range, true))
        }
        assertTrue(collision.isBlocked(Tile(3925, 5408), Direction.EAST, projectile = false), "water stays walk-blocked")
        every { p.tile } returns Tile(3926, 5413)
        assertFalse(Combat.inMeleeReach(p, f.npc, 1), "a diagonal corner is not ordinary melee reach")
        assertFalse(session.meleeReach(p.tile))
        every { p.tile } returns Tile(3926, 5408, 1)
        assertFalse(Combat.inMeleeReach(p, f.npc, 1))
        assertFalse(session.meleeReach(p.tile))
        val wall = CollisionUpdate.Builder().apply {
            setType(CollisionUpdate.Type.ADD)
            putTile(Tile(3926, 5408), true, Direction.WEST)
        }.build()
        collision.applyUpdate(wall)
        every { p.tile } returns Tile(3926, 5408)
        assertFalse(Combat.inMeleeReach(p, f.npc, 1))
        assertFalse(session.meleeReach(p.tile), "a real wall must still block both melee directions")
        assertNotEquals(CombatClass.MELEE, session.autoSelector(session).style)
        session.close()
    }
}
