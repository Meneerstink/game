package gg.rsmod.game.tools.importer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToaWardenFloorDepthTests {
    @Test
    fun `all three source floors retain their layout and decal separation above the terrain`() {
        ModernCacheReader(File(ToaWardenFloorDepthTool.SOURCE_CACHE)).use { cache ->
            for (id in ToaWardenFloorDepthTool.MODELS.keys) {
                val raw = requireNotNull(cache.file(7, id, 0))
                val rawCopy = raw.copyOf()
                val patch = ToaWardenFloorDepthTool.patch(id, raw)
                val grid = Rev667ModelDecoder.decode(patch.gridOnly)
                val out = Rev667ModelDecoder.decode(patch.output)
                assertTrue(out.vertexY.all { it < 0 }, "No floor vertex may overlap the terrain at y=0")
                assertEquals(grid.vertexCount, out.vertexCount)
                assertEquals(grid.faceCount, out.faceCount)
                assertContentEquals(grid.vertexX, out.vertexX)
                assertContentEquals(grid.vertexZ, out.vertexZ)
                assertContentEquals(grid.faceA, out.faceA)
                assertContentEquals(grid.faceB, out.faceB)
                assertContentEquals(grid.faceC, out.faceC)
                assertContentEquals(grid.faceColour, out.faceColour)
                assertContentEquals(grid.facePriority, out.facePriority)
                assertTrue(grid.vertexY.indices.all { grid.vertexY[it] - out.vertexY[it] == 1 }, "All layers move together")
                assertTrue(OsrsDecalLiftTool.liftAll(out) == null, "The grid must remain above the background")
                assertContentEquals(rawCopy, raw, "Never mutate the pinned source")
                assertContentEquals(patch.output, ToaWardenFloorDepthTool.patch(id, raw).output, "Repeated plan is stable")
                assertContentEquals(patch.output, Rev667ModelEncoder.encode(out))
            }
        }
    }

    @Test
    fun `known floor states are accepted but unrelated edits are rejected`() {
        ModernCacheReader(File(ToaWardenFloorDepthTool.SOURCE_CACHE)).use { cache ->
            val patch = ToaWardenFloorDepthTool.patch(45432, requireNotNull(cache.file(7, 45432, 0)))
            assertTrue(patch.accepts(patch.original))
            assertTrue(patch.accepts(patch.gridOnly))
            assertTrue(patch.accepts(patch.output))
            val changed = Rev667ModelDecoder.decode(patch.gridOnly)
            changed.faceColour[0] = 123
            assertFalse(patch.accepts(Rev667ModelEncoder.encode(changed)))
            assertFailsWith<IllegalArgumentException> { ToaWardenFloorDepthTool.patch(45895, byteArrayOf()) }
        }
    }
}
