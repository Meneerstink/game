package gg.rsmod.plugins.api.ext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A4 + K02 N397: a pet head is zoomed out when it overflows its frame and zoomed in when it is drawn far too small. */
class NpcChatheadFitTests {
    private val surface = NpcChatheadFit.DIALOGUE
    private val roomV = (surface.anchorY - surface.frameY1) + (surface.frameY2 - surface.anchorY)

    @Test
    fun `a head that fits its frame keeps the cache camera`() {
        val half = (roomV * 0.7 / 2).toInt() - 2
        val fit = NpcChatheadFit.fit(surface, NpcChatheadFit.Extents(half, half, 10, 10))
        assertEquals(surface.zoom, fit.zoom)
        assertEquals(false, fit.moved)
    }

    @Test
    fun `a head that overflows is zoomed out`() {
        val fit = NpcChatheadFit.fit(surface, NpcChatheadFit.Extents(roomV, roomV, 20, 20))
        assertTrue(fit.zoom > surface.zoom, "zoom ${fit.zoom}")
    }

    @Test
    fun `a head drawn far too small is zoomed in until it fills most of the frame`() {
        val fit = NpcChatheadFit.fit(surface, NpcChatheadFit.Extents(8, 8, 8, 8))
        assertTrue(fit.zoom < surface.zoom, "zoom ${fit.zoom}")
        // 20 px tall (with the 2-px pad per side) out of the frame's room: drawn at FILL of the frame after the zoom.
        val drawn = 20.0 * surface.zoom / fit.zoom
        assertEquals(NpcChatheadFit.FILL, drawn / roomV, 0.02)
    }
}
