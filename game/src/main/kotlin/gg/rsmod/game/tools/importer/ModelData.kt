package gg.rsmod.game.tools.importer

/**
 * Revision-neutral mesh, the intermediate representation the modern-content import pipeline
 * converts *through* (`RSPS_DECISIONS.md` 2026-09-02 STANDING OWNER AUTHORIZATION).
 *
 * Modern OSRS and rev-667 store the same geometry in different containers, so a decoded modern
 * model is held here and then re-encoded for the target rather than patched in place. Field names
 * follow the rev-667 client ([com.jagex.graphics.Mesh]) where the two generations disagree, since
 * that is the consumer we have to satisfy.
 *
 * Nullable arrays are genuinely optional sections: absent means "this model does not carry that
 * data", which is distinct from an array of zeroes and is encoded differently.
 */
class ModelData(
    val vertexCount: Int,
    val faceCount: Int,
    val texSpaceCount: Int,
) {
    val vertexX = IntArray(vertexCount)
    val vertexY = IntArray(vertexCount)
    val vertexZ = IntArray(vertexCount)

    val faceA = IntArray(faceCount)
    val faceB = IntArray(faceCount)
    val faceC = IntArray(faceCount)
    val faceColour = ShortArray(faceCount)

    /** Per-face shading/render type; rev-667 gates this on the `flatShading` global flag. */
    var shadingType: ByteArray? = null

    /** Per-face priority, present only when the header priority byte is the escape value 255. */
    var facePriority: ByteArray? = null

    /** Global priority used when [facePriority] is absent. */
    var globalPriority: Byte = 0

    var faceAlpha: ByteArray? = null

    /** Per-face skin/group label (modern `packedTransparencyVertexGroups`). */
    var faceLabel: IntArray? = null

    /** Per-vertex skin label (modern `packedVertexGroups`). */
    var vertexLabel: IntArray? = null

    /** Texture id per face, `-1` for untextured faces. */
    var faceTexture: ShortArray? = null

    /** Index into the texture-space tables per textured face, `-1` otherwise. */
    var faceTexSpace: ByteArray? = null

    /** Texture mapping type per texture space; only type 0 (planar) is supported end-to-end. */
    var texMappingType: ByteArray? = null

    var texSpaceDefA: ShortArray? = null
    var texSpaceDefB: ShortArray? = null
    var texSpaceDefC: ShortArray? = null

    /** Set when a modern source carried animaya (multi-bone) skinning, which rev-667 cannot express. */
    var droppedAnimayaSkinning = false

    /** Set when a modern source carried per-face z-offsets, which rev-667 cannot express. */
    var droppedFaceZOffsets = false

    fun describe(): String =
        buildString {
            appendLine("vertexCount=$vertexCount faceCount=$faceCount texSpaceCount=$texSpaceCount")
            appendLine("globalPriority=$globalPriority")
            appendLine(
                "sections: shadingType=${shadingType != null} facePriority=${facePriority != null} " +
                    "faceAlpha=${faceAlpha != null} faceLabel=${faceLabel != null} " +
                    "vertexLabel=${vertexLabel != null} faceTexture=${faceTexture != null} " +
                    "faceTexSpace=${faceTexSpace != null}",
            )
            appendLine("boundsX=${vertexX.minOrNull()}..${vertexX.maxOrNull()}")
            appendLine("boundsY=${vertexY.minOrNull()}..${vertexY.maxOrNull()}")
            appendLine("boundsZ=${vertexZ.minOrNull()}..${vertexZ.maxOrNull()}")
            appendLine("maxVertexReferenced=${(faceA + faceB + faceC).maxOrNull()}")
            appendLine("texturedFaces=${faceTexture?.count { it != (-1).toShort() } ?: 0}")
            appendLine("faceAlphaHistogram=${faceAlpha?.map { it.toInt() and 0xFF }?.groupingBy { it }?.eachCount()?.toSortedMap()}")
            appendLine("facePriorityHistogram=${facePriority?.map { it.toInt() and 0xFF }?.groupingBy { it }?.eachCount()?.toSortedMap()}")
            appendLine("vertexLabelMax=${vertexLabel?.maxOrNull()} faceLabelMax=${faceLabel?.maxOrNull()}")
            append("faceColours=${faceColour.map { it.toInt() and 0xFFFF }.distinct().take(16)}")
        }
}
