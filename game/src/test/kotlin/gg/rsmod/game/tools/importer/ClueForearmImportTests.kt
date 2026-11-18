package gg.rsmod.game.tools.importer

import kotlin.test.*

class ClueForearmImportTests {
    private fun triangle(version: Int, colour: Int, priority: Int): ModelData = ModelData(3, 1, 0).also {
        it.version = version
        it.vertexX[1] = 12; it.vertexY[2] = 16; it.vertexZ[2] = 4
        it.vertexLabel = intArrayOf(19, 22, 19)
        it.faceA[0] = 0; it.faceB[0] = 1; it.faceC[0] = 2
        it.faceColour[0] = colour.toShort()
        it.globalPriority = priority.toByte()
    }

    @Test fun `append preserves both triangles skin colours labels and different render priorities`() {
        val body = triangle(12, 1200, 10)
        val arm = triangle(13, 4550, 7)
        val merged = ClueForearmModel.append(body, arm)
        assertEquals(6, merged.vertexCount)
        assertEquals(2, merged.faceCount)
        assertContentEquals(intArrayOf(0,48,0,0,12,0), merged.vertexX)
        assertContentEquals(intArrayOf(0,3), merged.faceA)
        assertContentEquals(intArrayOf(1,4), merged.faceB)
        assertContentEquals(intArrayOf(2,5), merged.faceC)
        assertContentEquals(shortArrayOf(1200,4550), merged.faceColour)
        assertContentEquals(intArrayOf(19,22,19,19,22,19), merged.vertexLabel)
        assertContentEquals(byteArrayOf(10,7), merged.facePriority)
        assertEquals(13, merged.version)
        val encoded = Rev667ModelEncoder.encode(merged)
        assertTrue(ModelConvertTool.compare(merged, Rev667ModelDecoder.decode(encoded)).isEmpty())
        // Source objects are reused by other items; merging must not modify them.
        assertEquals(12, body.vertexX[1]); assertEquals(12, arm.vertexX[1])
        assertEquals(3, body.vertexCount)
    }

    @Test fun `texture coordinates still reference original body vertices and bare skin has no texture`() {
        val body = ModelData(3, 1, 1).also {
            it.version = 13
            it.vertexX[1] = 12; it.vertexY[2] = 16
            it.vertexLabel = intArrayOf(3,3,3)
            it.faceA[0] = 0; it.faceB[0] = 1; it.faceC[0] = 2; it.faceColour[0] = 1200
            it.faceTexture = shortArrayOf(6)
            it.faceTexSpace = byteArrayOf(0)
            it.texMappingType = byteArrayOf(0)
            it.texSpaceDefA = shortArrayOf(0); it.texSpaceDefB = shortArrayOf(1); it.texSpaceDefC = shortArrayOf(2)
        }
        val merged = ClueForearmModel.append(body, triangle(13,4550,0))
        assertContentEquals(shortArrayOf(6,-1), merged.faceTexture)
        assertContentEquals(byteArrayOf(0,-1), merged.faceTexSpace)
        assertContentEquals(shortArrayOf(0), merged.texSpaceDefA)
        assertContentEquals(shortArrayOf(1), merged.texSpaceDefB)
        assertContentEquals(shortArrayOf(2), merged.texSpaceDefC)
        assertTrue(ModelConvertTool.compare(merged, Rev667ModelDecoder.decode(Rev667ModelEncoder.encode(merged))).isEmpty())
    }
}
