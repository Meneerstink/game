package gg.rsmod.game.tools.importer

/**
 * The OSRS mesh -> rev-667 mesh conversion used by [OsrsItemImportTool] (same steps: strict decode, flattened texture fallback,
 * textures replaced by the modern textures' average colour, encode, and a decode-compare that refuses any mismatch), shared with
 * [OsrsFxImportTool] for spotanim models.
 */
object OsrsModelConversion {
    /** OSRS texture id -> rev-667 texture id imported by [OsrsTextureImportTool] (asset map `fx_kind: texture`). */
    val IMPORTED_TEXTURES: Map<Int, Int> = mapOf(59 to 1408)

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
        val imported = raw.faceTexture?.map { it.toInt() }?.firstOrNull { it in IMPORTED_TEXTURES }
        val source =
            if (texFaces == 0 && raw.texSpaceCount == 0) {
                raw
            } else if (imported != null && raw.texMappingType?.all { it.toInt() == 0 } != false) {
                // A texture already imported by OsrsTextureImportTool stays on the model; any other texture is flattened.
                dropped += "model $modelId: texture $imported kept as 667 texture ${IMPORTED_TEXTURES.getValue(imported)}"
                OsrsTextureImportTool.keepTexture(raw, imported, IMPORTED_TEXTURES.getValue(imported)) { tex ->
                    osrsTextureAverageHsl(reader.file(ModernCacheReader.INDEX_TEXTURE, 0, tex) ?: error("modern texture $tex missing"))
                }
            } else {
                dropped += "model $modelId: $texFaces textured faces flattened to the modern textures' average colour"
                stripTextures(raw) { tex ->
                    val t = reader.file(ModernCacheReader.INDEX_TEXTURE, 0, tex) ?: error("modern texture $tex missing")
                    osrsTextureAverageHsl(t)
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
