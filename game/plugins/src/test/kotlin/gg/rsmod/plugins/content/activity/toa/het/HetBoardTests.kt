package gg.rsmod.plugins.content.activity.toa.het

import kotlin.test.*

class HetBoardTests {
    private fun solved(group: Boolean = false): HetBoard {
        val b = HetBoard(HetLayouts.ENERGY_BEAM, group)
        HetLayouts.ENERGY_BEAM_SOLUTION.forEach { (p, r) -> b.mirrorAt(p)!!.rotation = r }
        return b
    }

    @Test fun `every layout starts unsolved and its screenshot solution reaches the shielded statue`() {
        HetLayouts.ALL.forEach { layout ->
            assertFalse(HetBoard(layout, false).trace().hitTarget, layout.name)
            assertTrue(HetLayouts.ALL.all { it.mirrors.count { m -> m.movable } <= 3 })
        }
        val beam = solved().trace()
        assertTrue(beam.hitTarget)
        assertEquals(listOf(HetPos(3673, 5280), HetPos(3673, 5287), HetPos(3684, 5287)), beam.turns)
    }

    @Test fun `rotating one mirror away breaks the beam and four rotations come back`() {
        val b = solved()
        val m = b.mirrorAt(HetPos(3673, 5287))!!
        m.rotate(true); assertFalse(b.trace().hitTarget)
        m.rotate(true); m.rotate(true); m.rotate(true); assertTrue(b.trace().hitTarget)
        m.rotate(false); m.rotate(true); assertTrue(b.trace().hitTarget)
    }

    @Test fun `the back of a mirror and a dirty mirror stop the beam`() {
        assertNull(HetMirror(HetPos(0, 0), 0, true).reflect(HetDir.NORTH)) // arrives on the south side: back of a N/E corner
        assertEquals(HetDir.NORTH, HetMirror(HetPos(0, 0), 0, true).reflect(HetDir.WEST))
        assertNull(HetMirror(HetPos(0, 0), 0, true, dirty = true).reflect(HetDir.WEST))
        val b = solved(group = true)
        assertTrue(b.trace().hitTarget) // the movable solution mirrors are never dirty at the start
        b.mirrorAt(HetPos(3673, 5280))!!.dirty = true
        assertFalse(b.trace().hitTarget)
        assertTrue(b.clean(HetPos(3673, 5280))); assertTrue(b.trace().hitTarget)
    }

    @Test fun `pick up, place, push respect walls, statues and the three-mirror cap`() {
        val b = solved()
        assertFalse(b.pickUp(HetPos(3690, 5286)), "fixed mirror")
        assertTrue(b.pickUp(HetPos(3684, 5287)))
        assertFalse(b.trace().hitTarget)
        assertFalse(b.place(HetPos(3676, 5285)), "stone wall")
        assertFalse(b.place(HetPos(3680, 5280)), "seal")
        assertFalse(b.place(HetPos(3669, 5280)), "outside the room")
        assertTrue(b.place(HetPos(3685, 5287), 2))
        assertFalse(b.place(HetPos(3686, 5287)), "only three movable mirrors")
        assertEquals(HetPos(3684, 5287), b.push(HetPos(3685, 5287), HetDir.WEST))
        assertTrue(b.trace().hitTarget)
        assertNull(b.push(HetPos(3684, 5287), HetDir.NORTH)?.takeIf { it.z > 5289 }, "never out of the room")
    }

    @Test fun `worn barriers block until broken`() {
        val b = HetBoard(HetLayouts.ENERGY_BEAM, false)
        val worn = HetPos(3674, 5282)
        assertTrue(b.blocked(worn)); assertTrue(b.breakWorn(worn)); assertFalse(b.blocked(worn)); assertFalse(b.breakWorn(worn))
    }

    @Test fun `seal hp and Mod Ash mining hits`() {
        assertEquals(119, HetSeal.hp(1))
        assertTrue(HetSeal.hp(2) > HetSeal.hp(1) && HetSeal.hp(8) > HetSeal.hp(3))
        assertEquals(17, HetSeal.hit(85, HetSeal.Tier.DRAGON, 0)) // 7 x 17 = 119: "Dragon Pickaxe and 85 Mining ... 7 times"
        assertEquals(119, 7 * HetSeal.hit(85, HetSeal.Tier.DRAGON, 0))
        assertEquals(15, HetSeal.hit(85, HetSeal.Tier.RUNE, 0))
        assertEquals(21, HetSeal.hit(99, HetSeal.Tier.DRAGON, 2))
        assertEquals(5, HetSeal.hit(1, HetSeal.Tier.BELOW_RUNE, 0))
        assertFailsWith<IllegalArgumentException> { HetSeal.hit(50, HetSeal.Tier.RUNE, 3) }
    }
}
