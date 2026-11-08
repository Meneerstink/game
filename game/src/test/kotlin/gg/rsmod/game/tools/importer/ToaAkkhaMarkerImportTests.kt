package gg.rsmod.game.tools.importer

import org.junit.Assert.*
import org.junit.Test

class ToaAkkhaMarkerImportTests {
    @Test fun markerAnimationStartsAtFrameZeroAndSurvivesTheSourceLocEncoder() {
        // Source2686 markers all carry89,24=9759,66=37. Both clients use89 to disable random frame starts.
        val source = ModernObjectDef.decode(45868, byteArrayOf(89.toByte(), 24, 0x26, 0x1f, 66, 0, 37, 0))
        assertFalse(source.randomizeAnimStart)
        val bytes = Rev667LocType.encodeFromModern(source, { it }, mutableListOf(), seqRemap = { if (it == 9759) 17453 else null })
        val local = Rev667LocType.decode(66079, bytes)
        assertEquals(17453, local.animation)
        assertTrue(local.opcodes.contains(89))
        val default = ModernObjectDef.decode(1, byteArrayOf(0))
        assertTrue(default.randomizeAnimStart)
        assertFalse(Rev667LocType.decode(1, Rev667LocType.encodeFromModern(default, { it }, mutableListOf())).opcodes.contains(89))
    }

    @Test fun refusesStaticMarkersBeforeAnyCacheOrModelPlanningWhenTheirSequenceIsMissing() {
        try {
            OsrsLocImportTool.requireMarkerSequence("toa-akkha-markers", emptyMap())
            fail("a missing sequence would silently strip the marker animation")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("toa-akkha-marker-seq"))
        }
        OsrsLocImportTool.requireMarkerSequence("toa-akkha-markers", mapOf("seq:9759" to 17453))
        OsrsLocImportTool.requireMarkerSequence("toa-locs", emptyMap())
    }
}
