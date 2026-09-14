package gg.rsmod.game.tools.importer

/**
 * The OSRS mesh -> rev-667 mesh conversion used by [OsrsItemImportTool] (same steps: strict decode, flattened texture fallback,
 * textures replaced by the modern textures' average colour, encode, and a decode-compare that refuses any mismatch), shared with
 * [OsrsFxImportTool] for spotanim models.
 */
object OsrsModelConversion {
    fun convert(
        reader: ModernCacheReader,
        modelId: Int,
        dropped: MutableList<String>,
    ): ByteArray {
        val bytes = reader.file(ModernCacheReader.INDEX_MODEL, modelId, 0) ?: error("upstream model $modelId missing")
        val raw =
            runCatching { ModernModelDecoder.decode(bytes) }.getOrElse { strict ->
                dropped += "model $modelId: complex texture mappings discarded (${strict.message})"
                ModernModelDecoder.decode(bytes, flattenTextures = true)
            }
        val texFaces = raw.faceTexture?.count { it.toInt() != -1 } ?: 0
        val source =
            if (texFaces == 0 && raw.texSpaceCount == 0) {
                raw
            } else {
                dropped += "model $modelId: $texFaces textured faces flattened to the modern textures' average colour"
                stripTextures(raw) { tex ->
                    val t = reader.file(ModernCacheReader.INDEX_TEXTURE, 0, tex) ?: error("modern texture $tex missing")
                    ((t[0].toInt() and 0xFF) shl 8) or (t[1].toInt() and 0xFF)
                }
            }
        if (raw.droppedFaceZOffsets) dropped += "model $modelId: face z-offsets (not representable in 667)"
        if (raw.droppedAnimayaSkinning) dropped += "model $modelId: animaya skinning (not representable in 667)"
        val converted = Rev667ModelEncoder.encode(source)
        val differences = ModelConvertTool.compare(source, Rev667ModelDecoder.decode(converted))
        check(differences.isEmpty()) { "model $modelId conversion mismatch: $differences" }
        return converted
    }
}
