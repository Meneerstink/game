package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Hit
import gg.rsmod.game.action.PawnPathAction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.content.combat.Combat
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

/** Strategies oldid15392814: P2 always protects melee; the ejected core is the melee target.
 * Keep source terrain/loc barriers and test both Warden choices rather than moving the statue for a melee weapon.
 */
class ToaWardensReachTests {
    @Test fun `both P2 Wardens admit ranged and magic and their cores have an ordinary melee approach`() {
        for (warden in WardensCombat.Warden.values()) {
            val f = ToaTestFixture()
            val source = ToaSourceCollision(f.world, 15184)
            every { f.npc.spawnTile } returns Tile(3807, 5153, 1)
            every { f.npc.tile } returns Tile(3807, 5153, 1)
            every { f.npc.getSize() } returns 3
            val p = mockk<Player>(relaxed = true)
            every { p.world } returns f.world
            every { p.getSize() } returns 1
            every { p.isOnline } returns true
            every { p.isDead() } returns false
            every { p.tile } returns Tile(3808, 5150, 1)
            for (id in listOf(ToaBossNpcs.ELIDINIS_P2, ToaBossNpcs.TUMEKEN_P2)) {
                val local = assertNotNull(ToaBossNpcs.local(id))
                val forms = listOf(local) + listOfNotNull(
                    gg.rsmod.plugins.content.activity.toa.ToaHeadIcons.copyOf(local, 7),
                    gg.rsmod.plugins.content.activity.toa.ToaHeadIcons.copyOf(local, 8))
                forms.forEach { form ->
                    every { f.world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, form) } returns
                        gg.rsmod.game.fs.def.NpcDef(form).apply { size = 5 }
                }
            }
            val session = WardensCombat.Session(f.npc, ToaTestContext(players = listOf(p)))
            session.phaseTwoWarden = warden
            session.cycle() // The actual P1 director places the two charging statues.
            session.startPhaseTwo()
            // Cache2686 P2 npc11753/11756 SIZE5, independent of the test fixture's default NPC size1.
            val statue = session.phaseTwoTile(warden)
            val target = session.npc
            assertEquals(statue, target.tile)
            assertEquals(5, target.getSize())
            assertFalse(source.collision.raycast(Tile(3808, 5150, 1), session.chargingTile(warden), true),
                "the old charging plinth blocks ranged LOS even though P2 must be attackable")
            for (x in 0..4) for (z in 0..4) {
                assertFalse(source.collision.isBlocked(statue.transform(x, z), gg.rsmod.game.model.Direction.NORTH, false),
                    "the complete active Warden footprint must fit on the room floor")
            }
            for (style in listOf(CombatClass.RANGED, CombatClass.MAGIC)) {
                val range = if (style == CombatClass.RANGED) 8 else 10
                val route = BFSPathFindingStrategy(source.collision).calculateRoute(
                    PathRequest.Builder().setPoints(p.tile, statue).setSourceSize(1, 1).setTargetSize(5, 5)
                        .setProjectilePath(true).setTouchRadius(range)
                        .clipPathNodes(node = true, link = true).clipDiagonalTiles().clipOverlapTiles().build())
                assertTrue(route.success, "$warden needs an approach for $style")
                every { p.tile } returns route.tail
                assertTrue(PawnPathAction.reachedMovingTarget(p, target, range, true), "$warden $style needs actual projectile LOS")
                println("$warden P2 SW=$statue $style range=$range approach=${route.tail}")
            }
            for (returned in 0..1) {
                session.returnedCores = returned
                val melee = Hit.Builder().addHit(40, HitType.MELEE.id).build().also { it.sourceCombatClass = CombatClass.MELEE }
                session.incoming(melee)
                assertEquals(0, melee.hitmarks.single().damage, "P2 always protects melee, before and after the prayer swap")
                val allowed = WardensCombat.allowedStyle(warden, returned)
                val type = if (allowed == CombatClass.RANGED) HitType.RANGE else HitType.MAGIC
                val hit = Hit.Builder().addHit(40, type.id).build().also { it.sourceCombatClass = allowed }
                session.incoming(hit)
                assertEquals(40, hit.hitmarks.single().damage)
            }
            session.exposeCore()
            val core = assertNotNull(session.core)
            val coreTile = core.tile
            val route = BFSPathFindingStrategy(source.collision).calculateRoute(
                PathRequest.Builder().setPoints(Tile(3808, 5150, 1), coreTile).setSourceSize(1, 1).setTargetSize(1, 1)
                    .setProjectilePath(false).setTouchRadius(1)
                    .clipPathNodes(node = true, link = true).clipDiagonalTiles().clipOverlapTiles().build())
            assertTrue(route.success, "$warden core must be reachable by melee")
            every { p.tile } returns route.tail
            assertTrue(Combat.inMeleeReach(p, core, 1), "$warden core must permit the actual melee hit")
            println("$warden core=$coreTile melee approach=${route.tail}")
            session.close()
        }
    }
}
