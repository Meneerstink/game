package gg.rsmod.game.model

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.def.AnimDef
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Owner 2026-10-03 20:58 (bug-20261003-205829, "agility shortcut animations are still not smooth ... stands on the tile"): every
 * slide that starts right after the approach gets the client's phase 1 (no snap onto the start tile) with its animation delayed
 * alike, and a one-shot animation that ends long before its slide ends the slide (no standing glide through the rock).
 */
class SlideTimingTests {
    private val world =
        World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true)).also {
            it.filestore = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
            it.definitions.loadAll(it.filestore)
        }

    private fun anim(id: Int) = world.definitions.get(AnimDef::class.java, id)

    @Test
    fun `a slide right after the approach glides onto the start tile and delays its animation alike`() {
        // GE tunnel 9311 / 9312: climb-into 2589 with a 50-cycle slide, one cycle after the last walk step.
        val plan = SlideTiming.plan(0, 50, 1, cyclesSinceStep = 1, anim = anim(2589), animationDelay = 0)
        assertEquals(SlideTiming.APPROACH_LEAD, plan.clientDuration1)
        assertEquals(50 + SlideTiming.APPROACH_LEAD, plan.clientDuration2)
        assertEquals(SlideTiming.APPROACH_LEAD, plan.animationDelay)
    }

    @Test
    fun `a player standing still gets the script's own timing`() {
        val plan = SlideTiming.plan(30, 94, 2, cyclesSinceStep = 40, anim = anim(839), animationDelay = 30)
        assertEquals(30, plan.clientDuration1)
        assertEquals(94, plan.clientDuration2)
        assertEquals(30, plan.animationDelay)
        assertNull(plan.shortenedFrom)
    }

    @Test
    fun `Trollheim's four-tile climb down ends with human climbing down 1148 instead of gliding upright`() {
        val climb = anim(1148)
        val plan = SlideTiming.plan(0, 160, 4, cyclesSinceStep = 40, anim = climb, animationDelay = 10)
        assertEquals(160, plan.shortenedFrom)
        assertEquals(10 + climb.frameLength, plan.clientDuration2)
    }

    @Test
    fun `a looping climb and a slide shorter than its animation keep their timing, and nothing slides faster than running`() {
        assertNull(SlideTiming.plan(0, 50, 1, 40, anim(740), 0).shortenedFrom)
        assertEquals(50, SlideTiming.plan(0, 50, 1, 40, anim(2589), 0).clientDuration2)
        // 2591 (30 cycles) on a 4-tile slide of 200: never shorter than 4 x 15.
        assertEquals(60, SlideTiming.plan(0, 200, 4, 40, anim(2591), 0).clientDuration2)
    }
}
