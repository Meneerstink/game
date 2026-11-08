package gg.rsmod.game.tools.importer

import org.junit.Assert.*
import org.junit.Test

class ToaObjectTexturePatchTests {
    @Test fun stillMaterialsPreserveCutoutModeAndAllTemplateColumnsExceptAverageColourAndScroll() {
        val template = Array(19) { i -> ByteArray(OsrsTextureImportTool.MATERIAL_COLUMNS[i]) { (i + 1).toByte() } }
        val original = template.map { it.copyOf() }
        val next = ToaObjectTexturePatchTool.stillMaterial(template, 0x20c0)
        assertArrayEquals(byteArrayOf(0x20, 0xc0.toByte()), next[7])
        assertEquals(0, next[8][0].toInt()); assertEquals(0, next[9][0].toInt())
        for (i in template.indices) {
            assertArrayEquals(original[i], template[i])
            if (i !in 7..9) assertArrayEquals(template[i], next[i])
        }
    }

    @Test fun spriteProgramChangesOnlyTheSpriteIdAndKeepsColourAlphaOutputs() {
        val original = byteArrayOf(3, 0, 39, 1, 1, 0, 1, 2, 10, 20, 30)
        val next = ToaObjectTexturePatchTool.spriteProgram(original, 8524)
        assertArrayEquals(original.take(6).toByteArray(), next.take(6).toByteArray())
        assertArrayEquals(original.drop(8).toByteArray(), next.drop(8).toByteArray())
        assertEquals(8524, ((next[6].toInt() and 255) shl 8) or (next[7].toInt() and 255))
        assertEquals(1, original[6].toInt()); assertEquals(2, original[7].toInt())
    }

    private fun source() = ModelData(4, 3, 2).apply {
        intArrayOf(0, 20, 0, 20).copyInto(vertexX)
        intArrayOf(0, -20, -20, 0).copyInto(vertexY)
        intArrayOf(0, 0, 20, 20).copyInto(vertexZ)
        intArrayOf(0, 1, 0).copyInto(faceA); intArrayOf(1, 3, 2).copyInto(faceB); intArrayOf(2, 2, 3).copyInto(faceC)
        shortArrayOf(127, 110, 4000).copyInto(faceColour)
        faceTexture = shortArrayOf(29, 30, -1)
        faceTexSpace = byteArrayOf(0, 1, -1)
        texMappingType = byteArrayOf(0, 0)
        texSpaceDefA = shortArrayOf(0, 1); texSpaceDefB = shortArrayOf(1, 3); texSpaceDefC = shortArrayOf(2, 2)
        faceAlpha = byteArrayOf(0, 90, 30); facePriority = byteArrayOf(4, 0, 1)
        faceLabel = intArrayOf(1, 2, 3); vertexLabel = intArrayOf(0, 0, 1, 1)
    }
    private fun colour(tex: Int) = 1000 + tex

    @Test fun bothPlantTexturesAreRestoredTogetherAndEveryOtherAttributeAndPlainFaceColourSurvives() {
        val src = source()
        val flat = Rev667ModelEncoder.encode(stripTextures(src, ::colour))
        val bytes = ToaObjectTexturePatchTool.restore(src, flat, ToaObjectTexturePatchTool.NATIVE, ::colour)
        val next = Rev667ModelDecoder.decode(bytes)
        assertArrayEquals(shortArrayOf(29, 30, -1), next.faceTexture)
        assertArrayEquals(src.faceTexSpace, next.faceTexSpace)
        assertArrayEquals(src.vertexX, next.vertexX); assertArrayEquals(src.vertexY, next.vertexY); assertArrayEquals(src.vertexZ, next.vertexZ)
        assertArrayEquals(src.faceColour, next.faceColour)
        assertArrayEquals(src.faceAlpha, next.faceAlpha); assertArrayEquals(src.facePriority, next.facePriority)
        assertArrayEquals(src.faceLabel, next.faceLabel); assertArrayEquals(src.vertexLabel, next.vertexLabel)
        assertTrue(ModelConvertTool.compare(src, next).isEmpty())
        assertArrayEquals(bytes, ToaObjectTexturePatchTool.restore(src, bytes, ToaObjectTexturePatchTool.NATIVE, ::colour))
    }

    @Test fun sourceUnrelatedEditsAreRefusedInsteadOfOverwritten() {
        for (kind in listOf("vertex", "colour", "alpha", "priority")) {
            val src = source()
            val edited = stripTextures(src, ::colour)
            when (kind) {
                "vertex" -> edited.vertexX[0]++
                "colour" -> edited.faceColour[2] = 1234
                "alpha" -> edited.faceAlpha!![1]++
                else -> edited.facePriority!![2]++
            }
            try {
                ToaObjectTexturePatchTool.restore(src, Rev667ModelEncoder.encode(edited), ToaObjectTexturePatchTool.NATIVE, ::colour)
                fail("edited $kind overwritten")
            } catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("refusing")) }
        }
    }

    @Test fun keepingUnresolvedGlyphFlatDoesNotEraseBothApprovedPlantTexturesOnTheSameMesh() {
        val src = source().apply { faceTexture!![2] = 96; faceTexSpace!![2] = 0 }
        val result = ToaObjectTexturePatchTool.restore(src, Rev667ModelEncoder.encode(stripTextures(src, ::colour)),
            ToaObjectTexturePatchTool.NATIVE, ::colour)
        val next = Rev667ModelDecoder.decode(result)
        assertArrayEquals(shortArrayOf(29, 30, -1), next.faceTexture)
        assertEquals(colour(96), next.faceColour[2].toInt())
    }

    @Test fun sourceGlyphCanBeRestoredAfterNativePassWithoutFlatteningEitherPlantTextureAgain() {
        val src = source().apply { faceTexture!![2] = 96; faceTexSpace!![2] = 0 }
        val flat = Rev667ModelEncoder.encode(stripTextures(src, ::colour))
        val native = ToaObjectTexturePatchTool.restore(src, flat, ToaObjectTexturePatchTool.NATIVE, ::colour)
        val complete = ToaObjectTexturePatchTool.restore(src, native, ToaObjectTexturePatchTool.NATIVE + (96 to 1410), ::colour)
        val next = Rev667ModelDecoder.decode(complete)
        assertArrayEquals(shortArrayOf(29, 30, 1410), next.faceTexture)
        assertArrayEquals(src.faceColour, next.faceColour)
        assertArrayEquals(src.faceAlpha, next.faceAlpha)
        assertArrayEquals(complete, ToaObjectTexturePatchTool.restore(src, complete, ToaObjectTexturePatchTool.NATIVE + (96 to 1410), ::colour))
    }
}
