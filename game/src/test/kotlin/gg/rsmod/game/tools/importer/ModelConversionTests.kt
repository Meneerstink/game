package gg.rsmod.game.tools.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Gate A5 of `RSPS_CURRENT_SPRINT.json`: prove by decoding, not by reading source, that modern OSRS
 * meshes need converting before a rev-667 client can load them, and that
 * [ModernModelDecoder] -> [Rev667ModelEncoder] -> [Rev667ModelDecoder] preserves the geometry.
 *
 * [Rev667ModelDecoder] is a port of the 667 client's own `Mesh.decodeNew`, so a green round trip is
 * evidence about the *target* algorithm rather than a self-consistent private format.
 *
 * The Twisted Bow cases read the three model groups extracted from the pinned OpenRS2 cache 2686
 * (see `C:\RSPS\import-source\openrs2-2686\PROVENANCE.txt`). Those binaries deliberately live
 * outside every Git repository, so when the pinned extract is not present the upstream cases are
 * skipped rather than silently passing on absent data; [synthetic mesh round trip][roundTripSyntheticMesh]
 * always runs and keeps the codec pair covered on its own.
 */
class ModelConversionTests {
    @Test
    fun `twisted bow models are the modern type 3 format the 667 client cannot read`() {
        forEachPinnedModel { name, data ->
            assertEquals("$name should carry the modern type 3 footer", -1 to -3, ModernModelDecoder.footerOf(data))
        }
    }

    @Test
    fun `twisted bow models survive conversion into the 667 mesh container`() {
        forEachPinnedModel { name, data ->
            val source = ModernModelDecoder.decode(data)
            val converted = Rev667ModelEncoder.encode(source)

            assertEquals("$name converted mesh should carry the 667 footer", -1 to -1, footerOf(converted))

            val target = Rev667ModelDecoder.decode(converted)
            assertGeometryEquivalent(name, source, target)
        }
    }

    @Test
    fun `roundTripSyntheticMesh`() {
        val model = ModelData(vertexCount = 4, faceCount = 2, texSpaceCount = 0)
        val vertices =
            arrayOf(
                intArrayOf(0, 0, 0),
                intArrayOf(120, -30, 4000),
                intArrayOf(-8000, 512, -1),
                intArrayOf(64, 16383, 63),
            )
        vertices.forEachIndexed { i, (x, y, z) ->
            model.vertexX[i] = x
            model.vertexY[i] = y
            model.vertexZ[i] = z
        }
        model.faceA[0] = 0
        model.faceB[0] = 1
        model.faceC[0] = 2
        model.faceA[1] = 2
        model.faceB[1] = 3
        model.faceC[1] = 0
        model.faceColour[0] = 12345
        model.faceColour[1] = -1
        model.facePriority = byteArrayOf(3, 7)
        model.faceAlpha = byteArrayOf(0, -128)
        model.faceLabel = intArrayOf(0, 200)
        model.vertexLabel = intArrayOf(1, 2, 3, 255)
        model.shadingType = byteArrayOf(0, 1)

        val target = Rev667ModelDecoder.decode(Rev667ModelEncoder.encode(model))
        assertGeometryEquivalent("synthetic", model, target)
        assertArrayEquals("shading type", model.shadingType, target.shadingType)
        assertArrayEquals("face priority", model.facePriority, target.facePriority)
        assertArrayEquals("face alpha", model.faceAlpha, target.faceAlpha)
        assertArrayEquals("face label", model.faceLabel, target.faceLabel)
        assertArrayEquals("vertex label", model.vertexLabel, target.vertexLabel)
    }

    @Test
    fun `encoder refuses a vertex delta it cannot represent`() {
        val model = ModelData(vertexCount = 2, faceCount = 1, texSpaceCount = 0)
        model.vertexX[1] = 20000
        val failure = runCatching { Rev667ModelEncoder.encode(model) }.exceptionOrNull()
        assertTrue(
            "An unrepresentable delta must fail loudly, not truncate: $failure",
            failure is IllegalStateException && failure.message!!.contains("shortSmart range"),
        )
    }

    private fun assertGeometryEquivalent(
        name: String,
        source: ModelData,
        target: ModelData,
    ) {
        assertEquals("$name vertex count", source.vertexCount, target.vertexCount)
        assertEquals("$name face count", source.faceCount, target.faceCount)
        assertEquals("$name texture space count", source.texSpaceCount, target.texSpaceCount)
        assertArrayEquals("$name vertex x", source.vertexX, target.vertexX)
        assertArrayEquals("$name vertex y", source.vertexY, target.vertexY)
        assertArrayEquals("$name vertex z", source.vertexZ, target.vertexZ)
        assertArrayEquals("$name face a", source.faceA, target.faceA)
        assertArrayEquals("$name face b", source.faceB, target.faceB)
        assertArrayEquals("$name face c", source.faceC, target.faceC)
        assertArrayEquals("$name face colour", source.faceColour, target.faceColour)
        assertEquals("$name global priority", source.globalPriority, target.globalPriority)
        assertArrayEquals("$name face texture", source.faceTexture, target.faceTexture)
        assertArrayEquals("$name face texture space", source.faceTexSpace, target.faceTexSpace)
        assertArrayEquals("$name texture mapping type", source.texMappingType, target.texMappingType)
    }

    private fun footerOf(data: ByteArray): Pair<Int, Int> = data[data.size - 2].toInt() to data[data.size - 1].toInt()

    private fun forEachPinnedModel(block: (String, ByteArray) -> Unit) {
        val extract = File(PINNED_EXTRACT)
        assumeTrue(
            "Pinned OpenRS2 2686 extract not present at $PINNED_EXTRACT; upstream cases skipped.",
            extract.isDirectory,
        )
        var checked = 0
        TWISTED_BOW_MODELS.forEach { (label, modelId) ->
            val file = File(extract, "model-$modelId.dat")
            assumeTrue("Missing ${file.path}; upstream cases skipped.", file.isFile)
            block("$label model $modelId", file.readBytes())
            checked++
        }
        assertEquals("every pinned Twisted Bow model should be exercised", TWISTED_BOW_MODELS.size, checked)
    }

    private companion object {
        const val PINNED_EXTRACT = "C:\\RSPS\\import-source\\openrs2-2686\\extract"

        /** From gate A4: upstream item 20997's complete model dependency set. */
        val TWISTED_BOW_MODELS =
            listOf(
                "inventory" to 32799,
                "male worn" to 32674,
                "female worn" to 39561,
            )
    }
}
