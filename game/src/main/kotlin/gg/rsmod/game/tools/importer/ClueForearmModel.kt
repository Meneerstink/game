package gg.rsmod.game.tools.importer

/**
 * Three-part clue platebodies have no fourth worn slot for the existing 667
 * forearm adaptation. Append the same proven bare forearm geometry to their
 * first mesh, preserving all original faces, skin labels and texture references.
 */
object ClueForearmModel {
    fun append(body: ModelData, arm: ModelData): ModelData {
        require(arm.texSpaceCount == 0 && arm.faceTexture?.all { it.toInt() == -1 } != false)
        require(body.vertexLabel != null && arm.vertexLabel != null)
        val out = ModelData(body.vertexCount + arm.vertexCount, body.faceCount + arm.faceCount, body.texSpaceCount)
        out.version = OsrsBodyFrame.FRAME_VERSION
        for ((mesh, offset) in listOf(body to 0, arm to body.vertexCount)) {
            val scale = if (mesh.version < OsrsBodyFrame.FRAME_VERSION) 4 else 1
            for (i in 0 until mesh.vertexCount) {
                out.vertexX[offset + i] = mesh.vertexX[i] * scale
                out.vertexY[offset + i] = mesh.vertexY[i] * scale
                out.vertexZ[offset + i] = mesh.vertexZ[i] * scale
            }
        }
        for ((mesh, faceOffset, vertexOffset) in listOf(Triple(body, 0, 0), Triple(arm, body.faceCount, body.vertexCount))) {
            for (i in 0 until mesh.faceCount) {
                out.faceA[faceOffset + i] = mesh.faceA[i] + vertexOffset
                out.faceB[faceOffset + i] = mesh.faceB[i] + vertexOffset
                out.faceC[faceOffset + i] = mesh.faceC[i] + vertexOffset
                out.faceColour[faceOffset + i] = mesh.faceColour[i]
            }
        }
        out.vertexLabel = body.vertexLabel!! + arm.vertexLabel!!
        if (body.shadingType != null || arm.shadingType != null) out.shadingType =
            (body.shadingType ?: ByteArray(body.faceCount)) + (arm.shadingType ?: ByteArray(arm.faceCount))
        if (body.faceAlpha != null || arm.faceAlpha != null) out.faceAlpha =
            (body.faceAlpha ?: ByteArray(body.faceCount)) + (arm.faceAlpha ?: ByteArray(arm.faceCount))
        if (body.faceLabel != null || arm.faceLabel != null) out.faceLabel =
            (body.faceLabel ?: IntArray(body.faceCount)) + (arm.faceLabel ?: IntArray(arm.faceCount))
        if (body.facePriority != null || arm.facePriority != null || body.globalPriority != arm.globalPriority) {
            out.facePriority = (body.facePriority ?: ByteArray(body.faceCount) { body.globalPriority }) +
                (arm.facePriority ?: ByteArray(arm.faceCount) { arm.globalPriority })
        } else out.globalPriority = body.globalPriority
        out.faceTexture = body.faceTexture?.let { it + ShortArray(arm.faceCount) { -1 } }
        out.faceTexSpace = body.faceTexSpace?.let { it + ByteArray(arm.faceCount) { -1 } }
        out.texMappingType = body.texMappingType?.clone()
        out.texSpaceDefA = body.texSpaceDefA?.clone()
        out.texSpaceDefB = body.texSpaceDefB?.clone()
        out.texSpaceDefC = body.texSpaceDefC?.clone()
        return out
    }

    fun appendBytes(body: ByteArray, arm: WornForearm.Piece): ByteArray {
        val merged = append(Rev667ModelDecoder.decode(body), Rev667ModelDecoder.decode(arm.bytes))
        val bytes = Rev667ModelEncoder.encode(merged)
        check(ModelConvertTool.compare(merged, Rev667ModelDecoder.decode(bytes)).isEmpty()) { "CLUE forearm merge failed round-trip" }
        return bytes
    }
}
