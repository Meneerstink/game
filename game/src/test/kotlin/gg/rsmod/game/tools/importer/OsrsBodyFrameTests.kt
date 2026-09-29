package gg.rsmod.game.tools.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * [OsrsBodyFrame]: an OSRS worn mesh is moved from the 2007 body onto the 667 one (owner 2026-09-29, OSRS gear clipping into
 * walls, floors and ceilings), stored as a version-13 mesh the 667 client does not upscale, and never moved twice.
 */
class OsrsBodyFrameTests {
    private fun mesh(vararg labels: Int): ModelData {
        val model = ModelData(vertexCount = labels.size, faceCount = 1, texSpaceCount = 0)
        labels.forEachIndexed { i, _ ->
            model.vertexX[i] = i
            model.vertexY[i] = 6
            model.vertexZ[i] = -i
        }
        model.vertexLabel = labels.copyOf()
        model.faceA[0] = 0
        model.faceB[0] = 1
        model.faceC[0] = 2
        return model
    }

    @Test
    fun `feet move up onto the 667 floor and the mesh becomes version 13`() {
        val model = OsrsBodyFrame.toRev667Frame(mesh(255, 45, 48), slot = 10)
        assertEquals(13, model.version)
        // label 255: (0, -24, 0) on 6 * 4 = 24 -> 0; feet 45 / 48: -26 / -27 from 24.
        assertEquals(listOf(0, -2, -3), model.vertexY.toList())
        assertEquals(listOf(0, 4 - 2, 8 - 2), model.vertexX.toList())
    }

    @Test
    fun `a shield on the OSRS wrist label moves to the 667 shield label`() {
        val model = OsrsBodyFrame.toRev667Frame(mesh(28, 28, 161), slot = 5)
        assertEquals(listOf(161, 161, 161), model.vertexLabel!!.toList())
        assertEquals(24 + 38, model.vertexY[0])
    }

    @Test
    fun `a weapon follows the 667 grip and its hands keep the hand shift`() {
        val model = OsrsBodyFrame.toRev667Frame(mesh(50, 55, 27), slot = 3)
        assertEquals(listOf(24 + 28, 24 + 28, 24 - 30), model.vertexY.toList())
    }

    @Test
    fun `version 13 survives the 667 codec and a converted mesh is never converted again`() {
        val model = OsrsBodyFrame.toRev667Frame(mesh(255, 8, 8), slot = 4)
        val decoded = Rev667ModelDecoder.decode(Rev667ModelEncoder.encode(model))
        assertEquals(13, decoded.version)
        assertEquals(model.vertexY.toList(), decoded.vertexY.toList())
        assertEquals(12, Rev667ModelDecoder.decode(Rev667ModelEncoder.encode(mesh(8, 8, 8))).version)
        val refused = runCatching { OsrsBodyFrame.toRev667Frame(decoded, slot = 4) }
        assertTrue("a version-13 mesh must be refused", refused.isFailure)
    }

    @Test
    fun `the real OSRS Pegasian boots no longer reach through the floor`() {
        assumeTrue(File(OsrsItemImportTool.SOURCE_CACHE).isDirectory)
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val before = Rev667ModelDecoder.decode(OsrsModelConversion.convert(reader, 29252, mutableListOf()))
            val after = Rev667ModelDecoder.decode(OsrsModelConversion.convert(reader, 29252, mutableListOf(), wornSlot = 10))
            assertEquals(24, before.vertexY.maxOrNull()!! * 4)
            assertTrue("soles at ${after.vertexY.maxOrNull()}", after.vertexY.maxOrNull()!! <= 0)
            assertEquals(13, after.version)
        }
    }
}
