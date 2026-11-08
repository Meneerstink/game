package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.content.activity.toa.ToaIds
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import gg.rsmod.plugins.content.activity.toa.bosses.AkkhaCombat.Quadrant
import kotlin.test.*

class ToaAkkhaVisualTests {
    @Test fun `Memory Blast previews the source marker and blasts empty quadrants then cleans up across enrage and reset`() {
        mockkObject(ToaIds)
        try {
            every { ToaIds.loc(any()) } answers { firstArg<Int>() + 20000 }
            every { ToaIds.spot(any()) } answers { firstArg<Int>() + 1000 }
            for (exit in listOf("normal", "phase", "close")) {
                val f = ToaTestFixture()
                val objects = mutableSetOf<GameObject>()
                val spawned = mutableListOf<GameObject>()
                val graphics = mutableListOf<TileGraphic>()
                every { f.world.getObject(any<Tile>(), any<gg.rsmod.game.model.collision.ObjectType>()) } returns null
                every { f.world.spawn(any<GameObject>()) } answers { firstArg<GameObject>().let { objects += it; spawned += it }; Unit }
                every { f.world.remove(any<GameObject>()) } answers { objects.remove(firstArg<GameObject>()); Unit }
                every { f.world.isSpawned(any<GameObject>()) } answers { firstArg<GameObject>() in objects }
                every { f.world.spawn(any<TileGraphic>()) } answers { graphics += firstArg<TileGraphic>(); Unit }
                val s = AkkhaCombat.Session(f.npc, ToaTestContext())
                s.nextAttack = Int.MAX_VALUE
                s.centre = AkkhaArena.SOURCE_CENTRE
                s.memoryBlast(listOf(Quadrant.FIRE), 4, 22)
                assertEquals(1, spawned.size)
                assertEquals(65868, spawned.single().id)
                assertEquals(Tile(3679, 5409, 1), spawned.single().tile)
                assertTrue(graphics.isEmpty(), "preview uses a loc; elemental hit spots belong to the later blast")
                when (exit) { "phase" -> s.newPhase(); "close" -> s.close() }
                repeat(5) { s.cycle() }
                assertTrue(objects.isEmpty(), "marker cleanup survives $exit")
                if (exit == "normal") {
                    assertEquals(AkkhaArena.blastTiles(s.centre, Quadrant.FIRE).size, graphics.size)
                    assertEquals(3, graphics.map { it.id }.toSet().size)
                    assertTrue(graphics.none { AkkhaCombat.quadrant(it.tile, s.centre) == Quadrant.FIRE })
                } else assertTrue(graphics.isEmpty(), "cancelled phase may not send old blast graphics")
                s.close()
            }
        } finally { unmockkObject(ToaIds) }
    }
    @Test fun `markers align with the four glyph colour clusters in the real source platform models`() {
        val expected = mapOf(Quadrant.FIRE to (45868 to Tile(3679, 5409, 1)),
            Quadrant.SHADOW to (45871 to Tile(3682, 5409, 1)),
            Quadrant.LIGHTNING to (45869 to Tile(3682, 5406, 1)),
            Quadrant.ICE to (45870 to Tile(3679, 5406, 1)))
        expected.forEach { (q, value) ->
            assertEquals(value.first, AkkhaArena.markerLoc(q))
            assertEquals(value.second, AkkhaArena.markerTile(AkkhaArena.SOURCE_CENTRE, q))
            assertEquals(q, AkkhaCombat.quadrant(value.second, AkkhaArena.SOURCE_CENTRE))
        }
    }

    @Test fun `blast draws every unsafe arena tile and no safe or blocked edge tile in any quadrant`() {
        val centre = AkkhaArena.SOURCE_CENTRE
        val floor = AkkhaArena.tiles(centre)
        assertEquals(floor.size, floor.toSet().size)
        // Source plane1 collision row5398 =3679..3682,5399=3676..3685,5406=3671..3690,5417=3679..3682.
        assertEquals((3679..3682).toList(), floor.filter { it.z == 5398 }.map { it.x })
        assertEquals((3676..3685).toList(), floor.filter { it.z == 5399 }.map { it.x })
        assertEquals((3671..3690).toList(), floor.filter { it.z == 5406 }.map { it.x })
        assertEquals((3679..3682).toList(), floor.filter { it.z == 5417 }.map { it.x })
        val quarters = floor.groupBy { AkkhaCombat.quadrant(it, centre) }
        assertEquals(4, quarters.size)
        assertEquals(1, quarters.values.map { it.size }.toSet().size)
        for (safe in Quadrant.values()) {
            val blast = AkkhaArena.blastTiles(centre, safe)
            assertEquals(floor.size * 3 / 4, blast.size)
            assertTrue(blast.none { AkkhaCombat.quadrant(it, centre) == safe })
            assertEquals(3, blast.map { AkkhaArena.impactSpot(AkkhaCombat.quadrant(it, centre)) }.toSet().size)
        }
    }

    @Test fun `marker and full arena blast stay inside translated instances and retain plane`() {
        val centre = Tile(7001, 2008, 2)
        for (q in Quadrant.values()) {
            val marker = AkkhaArena.markerTile(centre, q)
            assertEquals(2, marker.height)
            assertEquals(q, AkkhaCombat.quadrant(marker, centre))
            assertTrue(AkkhaArena.blastTiles(centre, q).all { it.height == 2 && it.x in 6991..7010 && it.z in 1998..2017 })
        }
    }
}
