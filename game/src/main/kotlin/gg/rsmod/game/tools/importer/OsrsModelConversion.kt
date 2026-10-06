package gg.rsmod.game.tools.importer

/**
 * The OSRS mesh -> rev-667 mesh conversion used by [OsrsItemImportTool] (same steps: strict decode, flattened texture fallback,
 * textures replaced by the modern textures' average colour, encode, and a decode-compare that refuses any mismatch), shared with
 * [OsrsFxImportTool] for spotanim models.
 */
object OsrsModelConversion {
    /** OSRS texture id -> rev-667 texture id imported by [OsrsTextureImportTool] (asset map `fx_kind: texture`). */
    val IMPORTED_TEXTURES: Map<Int, Int> = mapOf(59 to 1408)

    /**
     * OSRS textures the rev-667 cache already holds under the same id with the same image: the 667 program is the single sprite
     * op on the OSRS texture's own sprite id. Census 2026-09-27 over every imported model (asset map, 26 OSRS textures used):
     * these 9 match, the other 17 have a different, multi-op 667 program and stay flattened. They were flattened too, so the
     * fire cape lava (40, sprite 485) on Durial321 became one pale orange-pink colour (owner 2026-09-27 "de firecape ... is roze").
     * Guard: [NativeTextureRestoreTool.census] re-proves the sprite match against both caches before it rewrites any model.
     */
    val NATIVE_SAME_TEXTURES: Set<Int> = setOf(6, 8, 12, 17, 19, 21, 27, 34, 40)

    /** Every OSRS texture kept on a converted model, with its rev-667 id. */
    val KEPT_TEXTURES: Map<Int, Int> = NATIVE_SAME_TEXTURES.associateWith { it } + IMPORTED_TEXTURES

    /**
     * [wornSlot]: the equipment slot when the mesh is worn on the player - it is then moved from the 2007 OSRS body onto the
     * 667 one ([OsrsBodyFrame]). Inventory/ground and spotanim meshes pass null and keep their geometry. [female]: the mesh is
     * a female wear model (needed for the legs, [OsrsBodyFrame.fitShin], and the hands, [OsrsBodyFrame.shift]). [legacyHands]: the
     * hands conversion before 2026-10-06, only for [OsrsWristTool].
     */
    fun convert(
        reader: ModernCacheReader,
        modelId: Int,
        dropped: MutableList<String>,
        wornSlot: Int? = null,
        female: Boolean? = null,
        legacyHands: Boolean = false,
    ): ByteArray {
        val bytes = reader.file(ModernCacheReader.INDEX_MODEL, modelId, 0) ?: error("upstream model $modelId missing")
        val raw =
            runCatching { ModernModelDecoder.decode(bytes) }.getOrElse { strict ->
                dropped += "model $modelId: complex texture mappings discarded (${strict.message})"
                ModernModelDecoder.decode(bytes, flattenTextures = true)
            }
        val texFaces = raw.faceTexture?.count { it.toInt() != -1 } ?: 0
        val kept = raw.faceTexture?.map { it.toInt() }?.filter { it in KEPT_TEXTURES }?.toSet().orEmpty()
        val source =
            if (texFaces == 0 && raw.texSpaceCount == 0) {
                raw
            } else if (kept.isNotEmpty() && raw.texMappingType?.all { it.toInt() == 0 } != false) {
                // A texture 667 has (same image) or OsrsTextureImportTool imported stays on the model; any other is flattened.
                dropped += "model $modelId: textures ${kept.sorted()} kept as 667 textures ${kept.sorted().map { KEPT_TEXTURES.getValue(it) }}"
                OsrsTextureImportTool.keepTextures(raw, KEPT_TEXTURES.filterKeys { it in kept }) { tex ->
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
        if (wornSlot != null && wornSlot in OsrsBodyFrame.RENDERED_SLOTS) OsrsBodyFrame.toRev667Frame(source, wornSlot, female, legacyHands)
        val converted = Rev667ModelEncoder.encode(source)
        val differences = ModelConvertTool.compare(source, Rev667ModelDecoder.decode(converted))
        check(differences.isEmpty()) { "model $modelId conversion mismatch: $differences" }
        return converted
    }
}
