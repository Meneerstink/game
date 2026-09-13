package gg.rsmod.game.tools.importer

/**
 * Decodes a **modern OSRS** model group into the revision-neutral [ModelData]
 * (modern-content import pipeline, `RSPS_DECISIONS.md` 2026-09-02 STANDING OWNER AUTHORIZATION).
 *
 * Modern models are self-describing through two footer bytes, exactly as the reference client
 * dispatches them:
 *
 *  - `(-1, -3)` - "type 3", 26-byte trailer, carries per-vertex animaya skinning and per-face
 *    z-offsets on top of type 1. This is what the pinned cache holds for the Twisted Bow.
 *  - `(-1, -1)` - "type 1", 23-byte trailer. Structurally what rev-667 calls `decodeNew`.
 *  - anything else - the pre-2008 "old format", which this pipeline does not need and rejects
 *    rather than half-parsing.
 *
 * Only type 3 and type 1 are implemented; an unsupported footer is a hard error, never a silent
 * fallback, because a mis-parsed mesh would produce plausible-looking garbage geometry.
 */
object ModernModelDecoder {
    fun footerOf(data: ByteArray): Pair<Int, Int> {
        require(data.size >= 2) { "Model is too short to have a footer (${data.size} bytes)." }
        return data[data.size - 2].toInt() to data[data.size - 1].toInt()
    }

    /**
     * @param flattenTextures accept complex (types 1-3) texture mappings for a caller that flattens
     * every textured face to a plain colour afterwards ([stripTextures]); the mapping data and the
     * type-3 tail behind it are then not parsed. Default strict behaviour is unchanged.
     */
    fun decode(
        data: ByteArray,
        flattenTextures: Boolean = false,
    ): ModelData {
        val (penultimate, last) = footerOf(data)
        return when {
            penultimate == -1 && last == -3 -> decodeType3(data, flattenTextures)
            penultimate == -1 && last == -1 -> decodeType1(data, flattenTextures)
            penultimate == -1 && last == -2 -> decodeType2(data)
            else -> error("Model uses the pre-2008 old format, which this pipeline does not implement.")
        }
    }

    /** Modern type 3: type 1 plus an animaya flag, an explicit vertex-label block length and a tail. */
    private fun decodeType3(
        data: ByteArray,
        flattenTextures: Boolean,
    ): ModelData {
        val header = ModelBuffer(data, data.size - 26)
        val vertexCount = header.u16()
        val faceCount = header.u16()
        val texSpaceCount = header.u8()
        val flatShadingFlag = header.u8()
        val priorityFlag = header.u8()
        val faceAlphaFlag = header.u8()
        val faceGroupFlag = header.u8()
        val faceTextureFlag = header.u8()
        val vertexLabelFlag = header.u8()
        val animayaFlag = header.u8()
        val vertexLengthX = header.u16()
        val vertexLengthY = header.u16()
        val vertexLengthZ = header.u16()
        val faceDataSize = header.u16()
        val texSpaceSize = header.u16()
        val vertexLabelSize = header.u16()

        return decodeBody(
            data = data,
            vertexCount = vertexCount,
            faceCount = faceCount,
            texSpaceCount = texSpaceCount,
            flatShadingFlag = flatShadingFlag,
            priorityFlag = priorityFlag,
            faceAlphaFlag = faceAlphaFlag,
            faceGroupFlag = faceGroupFlag,
            faceTextureFlag = faceTextureFlag,
            vertexLabelFlag = vertexLabelFlag,
            vertexLengthX = vertexLengthX,
            vertexLengthY = vertexLengthY,
            vertexLengthZ = vertexLengthZ,
            faceDataSize = faceDataSize,
            texSpaceSize = texSpaceSize,
            vertexLabelSize = vertexLabelSize,
            animayaFlag = animayaFlag,
            modernType3 = true,
            flattenTextures = flattenTextures,
        )
    }

    /** Modern type 1: the vertex-label block is implicitly one byte per vertex, no animaya. */
    private fun decodeType1(
        data: ByteArray,
        flattenTextures: Boolean,
    ): ModelData {
        val header = ModelBuffer(data, data.size - 23)
        val vertexCount = header.u16()
        val faceCount = header.u16()
        val texSpaceCount = header.u8()
        val flatShadingFlag = header.u8()
        val priorityFlag = header.u8()
        val faceAlphaFlag = header.u8()
        val faceGroupFlag = header.u8()
        val faceTextureFlag = header.u8()
        val vertexLabelFlag = header.u8()
        val vertexLengthX = header.u16()
        val vertexLengthY = header.u16()
        val vertexLengthZ = header.u16()
        val faceDataSize = header.u16()
        val texSpaceSize = header.u16()

        return decodeBody(
            data = data,
            vertexCount = vertexCount,
            faceCount = faceCount,
            texSpaceCount = texSpaceCount,
            flatShadingFlag = flatShadingFlag,
            priorityFlag = priorityFlag,
            faceAlphaFlag = faceAlphaFlag,
            faceGroupFlag = faceGroupFlag,
            faceTextureFlag = faceTextureFlag,
            vertexLabelFlag = vertexLabelFlag,
            vertexLengthX = vertexLengthX,
            vertexLengthY = vertexLengthY,
            vertexLengthZ = vertexLengthZ,
            faceDataSize = faceDataSize,
            texSpaceSize = texSpaceSize,
            vertexLabelSize = if (vertexLabelFlag == 1) vertexCount else 0,
            animayaFlag = 0,
            modernType3 = false,
            flattenTextures = flattenTextures,
        )
    }

    /**
     * The section layout is byte-for-byte the same across type 1, type 3 and rev-667 `decodeNew`;
     * only the trailer differs. Sharing this body is what makes the conversion in
     * [Rev667ModelEncoder] a re-container rather than a geometry rewrite.
     */
    @Suppress("LongParameterList")
    private fun decodeBody(
        data: ByteArray,
        vertexCount: Int,
        faceCount: Int,
        texSpaceCount: Int,
        flatShadingFlag: Int,
        priorityFlag: Int,
        faceAlphaFlag: Int,
        faceGroupFlag: Int,
        faceTextureFlag: Int,
        vertexLabelFlag: Int,
        vertexLengthX: Int,
        vertexLengthY: Int,
        vertexLengthZ: Int,
        faceDataSize: Int,
        texSpaceSize: Int,
        vertexLabelSize: Int,
        animayaFlag: Int,
        modernType3: Boolean,
        flattenTextures: Boolean = false,
    ): ModelData {
        val model = ModelData(vertexCount, faceCount, texSpaceCount)

        var planarMappingCount = 0
        var complexMappingCount = 0
        if (texSpaceCount > 0) {
            val types = ByteArray(texSpaceCount)
            val typeBuffer = ModelBuffer(data, 0)
            for (i in 0 until texSpaceCount) {
                types[i] = typeBuffer.i8().toByte()
                if (types[i].toInt() == 0) planarMappingCount++
            }
            model.texMappingType = types
            val complex = types.count { it >= 1 && it <= 3 }
            complexMappingCount = complex
            check(complex == 0 || flattenTextures) {
                "Model uses $complex complex texture mapping(s) (types 1-3); this pipeline only " +
                    "supports planar mapping, and refuses to import a mesh whose texture data it " +
                    "would silently drop."
            }
        }

        var ptr = texSpaceCount
        val vertexFlagsPtr = ptr
        ptr += vertexCount
        val shadingPtr = ptr
        if (flatShadingFlag == 1) ptr += faceCount
        val faceTypePtr = ptr
        ptr += faceCount
        val facePriorityPtr = ptr
        if (priorityFlag == 255) ptr += faceCount
        val faceLabelPtr = ptr
        if (faceGroupFlag == 1) ptr += faceCount
        val vertexLabelPtr = ptr
        ptr += vertexLabelSize
        val faceAlphaPtr = ptr
        if (faceAlphaFlag == 1) ptr += faceCount
        val faceDataPtr = ptr
        ptr += faceDataSize
        val faceTexturePtr = ptr
        if (faceTextureFlag == 1) ptr += faceCount * 2
        val faceTexSpacePtr = ptr
        ptr += texSpaceSize
        val faceColourPtr = ptr
        ptr += faceCount * 2
        val vertexXPtr = ptr
        ptr += vertexLengthX
        val vertexYPtr = ptr
        ptr += vertexLengthY
        val vertexZPtr = ptr
        ptr += vertexLengthZ
        val planarMappingPtr = ptr
        ptr += planarMappingCount * 6
        val tailPtr = ptr

        if (priorityFlag == 255) {
            model.facePriority = ByteArray(faceCount)
        } else {
            model.globalPriority = priorityFlag.toByte()
        }
        if (flatShadingFlag == 1) model.shadingType = ByteArray(faceCount)
        if (faceAlphaFlag == 1) model.faceAlpha = ByteArray(faceCount)
        if (faceGroupFlag == 1) model.faceLabel = IntArray(faceCount)
        if (vertexLabelFlag == 1) model.vertexLabel = IntArray(vertexCount)
        if (faceTextureFlag == 1) model.faceTexture = ShortArray(faceCount)
        if (faceTextureFlag == 1 && texSpaceCount > 0) model.faceTexSpace = ByteArray(faceCount)

        val flags = ModelBuffer(data, vertexFlagsPtr)
        val deltaX = ModelBuffer(data, vertexXPtr)
        val deltaY = ModelBuffer(data, vertexYPtr)
        val deltaZ = ModelBuffer(data, vertexZPtr)
        val labels = ModelBuffer(data, vertexLabelPtr)
        var previousX = 0
        var previousY = 0
        var previousZ = 0
        for (i in 0 until vertexCount) {
            val mask = flags.u8()
            val x = if (mask and 0x1 != 0) deltaX.shortSmart() else 0
            val y = if (mask and 0x2 != 0) deltaY.shortSmart() else 0
            val z = if (mask and 0x4 != 0) deltaZ.shortSmart() else 0
            previousX += x
            previousY += y
            previousZ += z
            model.vertexX[i] = previousX
            model.vertexY[i] = previousY
            model.vertexZ[i] = previousZ
            if (vertexLabelFlag == 1) model.vertexLabel!![i] = labels.u8()
        }

        // Animaya skinning shares the vertex-label stream and has no rev-667 equivalent. It is read
        // only so the block is provably consumed; the data itself is dropped, and the encoder
        // records that loss rather than pretending the conversion is lossless.
        if (animayaFlag == 1) {
            model.droppedAnimayaSkinning = true
            for (i in 0 until vertexCount) {
                val groupCount = labels.u8()
                repeat(groupCount) {
                    labels.u8()
                    labels.u8()
                }
            }
        }

        val colours = ModelBuffer(data, faceColourPtr)
        val shading = ModelBuffer(data, shadingPtr)
        val priorities = ModelBuffer(data, facePriorityPtr)
        val alphas = ModelBuffer(data, faceAlphaPtr)
        val faceLabels = ModelBuffer(data, faceLabelPtr)
        val textures = ModelBuffer(data, faceTexturePtr)
        val texSpaces = ModelBuffer(data, faceTexSpacePtr)
        for (i in 0 until faceCount) {
            model.faceColour[i] = colours.u16().toShort()
            if (flatShadingFlag == 1) model.shadingType!![i] = shading.i8().toByte()
            if (priorityFlag == 255) model.facePriority!![i] = priorities.i8().toByte()
            if (faceAlphaFlag == 1) model.faceAlpha!![i] = alphas.i8().toByte()
            if (faceGroupFlag == 1) model.faceLabel!![i] = faceLabels.u8()
            if (faceTextureFlag == 1) model.faceTexture!![i] = (textures.u16() - 1).toShort()
            val texSpace = model.faceTexSpace
            if (texSpace != null) {
                texSpace[i] =
                    if (model.faceTexture!![i] == (-1).toShort()) -1 else (texSpaces.u8() - 1).toByte()
            }
        }

        val faceData = ModelBuffer(data, faceDataPtr)
        val faceTypes = ModelBuffer(data, faceTypePtr)
        var a = 0
        var b = 0
        var c = 0
        var last = 0
        for (i in 0 until faceCount) {
            when (val type = faceTypes.u8()) {
                1 -> {
                    a = faceData.shortSmart() + last
                    b = faceData.shortSmart() + a
                    c = faceData.shortSmart() + b
                    last = c
                }
                2 -> {
                    b = c
                    c = faceData.shortSmart() + last
                    last = c
                }
                3 -> {
                    a = c
                    c = faceData.shortSmart() + last
                    last = c
                }
                4 -> {
                    val previousA = a
                    a = b
                    b = previousA
                    c = faceData.shortSmart() + last
                    last = c
                }
                else -> error("Unknown face index encoding type $type at face $i.")
            }
            model.faceA[i] = a
            model.faceB[i] = b
            model.faceC[i] = c
        }

        if (texSpaceCount > 0) {
            model.texSpaceDefA = ShortArray(texSpaceCount)
            model.texSpaceDefB = ShortArray(texSpaceCount)
            model.texSpaceDefC = ShortArray(texSpaceCount)
            val planar = ModelBuffer(data, planarMappingPtr)
            for (i in 0 until texSpaceCount) {
                if (model.texMappingType!![i].toInt() and 0xFF == 0) {
                    model.texSpaceDefA!![i] = planar.u16().toShort()
                    model.texSpaceDefB!![i] = planar.u16().toShort()
                    model.texSpaceDefC!![i] = planar.u16().toShort()
                }
            }
        }

        // Type 3 appends a particle/billboard descriptor and optional per-face z-offsets. Rev-667
        // stores its own particle and billboard sections in a different shape, and has no z-offset
        // concept at all, so the tail is parsed purely to record what the conversion will lose.
        // Complex mapping data sits between the planar block and the tail; its size is not modelled
        // here, so the tail is only read when no complex mapping precedes it.
        if (modernType3 && complexMappingCount == 0) {
            val tail = ModelBuffer(data, tailPtr)
            if (tail.u8() != 0) {
                tail.u16()
                tail.u16()
                tail.u16()
                tail.position += 4
            }
            if (tail.u8() == 1) {
                model.droppedFaceZOffsets = true
            }
        }

        check(planarMappingCount + complexMappingCount == texSpaceCount) {
            "Texture mapping type census disagreed: $planarMappingCount planar of $texSpaceCount."
        }
        return model
    }
}

/** Big-endian reader with the RuneScape `shortSmart` encoding, positioned independently per section. */
class ModelBuffer(private val data: ByteArray, var position: Int) {
    fun u8(): Int = data[position++].toInt() and 0xFF

    fun i8(): Int = data[position++].toInt()

    fun u16(): Int = (u8() shl 8) or u8()

    /** One byte biased by 64 when the high bit is clear, otherwise two bytes biased by 49152. */
    fun shortSmart(): Int =
        if ((data[position].toInt() and 0xFF) <= Byte.MAX_VALUE) u8() - 64 else u16() - 49152
}

/**
 * Modern "type 2" model container (footer -1,-2), transcribed from RuneLite's
 * `ModelLoader.decodeType2`. Unlike type 1/3, the per-face render byte carries the flat-shading
 * bit (0x1) and the texture bit (0x2, in which case the face colour field holds the texture id and
 * bits 2+ hold the texture-coordinate index). The result is normalised into the same [ModelData]
 * the rev-667 encoder consumes.
 */
private fun ModernModelDecoder.decodeType2Impl(data: ByteArray): ModelData {
    val header = ModelBuffer(data, data.size - 23)
    val vertexCount = header.u16()
    val faceCount = header.u16()
    val texCount = header.u8()
    val renderTypeFlag = header.u8()
    val priorityFlag = header.u8()
    val alphaFlag = header.u8()
    val faceGroupFlag = header.u8()
    val vertexGroupFlag = header.u8()
    val animayaFlag = header.u8()
    val vertexLengthX = header.u16()
    val vertexLengthY = header.u16()
    val vertexLengthZ = header.u16()
    val faceDataSize = header.u16()
    val vertexLabelSize = header.u16()

    var ptr = 0
    val vertexFlagsPtr = ptr
    ptr += vertexCount
    val faceTypePtr = ptr
    ptr += faceCount
    val priorityPtr = ptr
    if (priorityFlag == 255) ptr += faceCount
    val faceGroupPtr = ptr
    if (faceGroupFlag == 1) ptr += faceCount
    val renderTypePtr = ptr
    if (renderTypeFlag == 1) ptr += faceCount
    val vertexLabelPtr = ptr
    ptr += vertexLabelSize
    val alphaPtr = ptr
    if (alphaFlag == 1) ptr += faceCount
    val faceDataPtr = ptr
    ptr += faceDataSize
    val colourPtr = ptr
    ptr += faceCount * 2
    val texIndexPtr = ptr
    ptr += texCount * 6
    val vertexXPtr = ptr
    ptr += vertexLengthX
    val vertexYPtr = ptr
    ptr += vertexLengthY
    val vertexZPtr = ptr
    ptr += vertexLengthZ
    val tailPtr = ptr

    val model = ModelData(vertexCount, faceCount, texCount)
    if (priorityFlag == 255) model.facePriority = ByteArray(faceCount) else model.globalPriority = priorityFlag.toByte()
    if (alphaFlag == 1) model.faceAlpha = ByteArray(faceCount)
    if (faceGroupFlag == 1) model.faceLabel = IntArray(faceCount)
    if (vertexGroupFlag == 1) model.vertexLabel = IntArray(vertexCount)

    val flags = ModelBuffer(data, vertexFlagsPtr)
    val dx = ModelBuffer(data, vertexXPtr)
    val dy = ModelBuffer(data, vertexYPtr)
    val dz = ModelBuffer(data, vertexZPtr)
    val labels = ModelBuffer(data, vertexLabelPtr)
    var px = 0
    var py = 0
    var pz = 0
    for (i in 0 until vertexCount) {
        val mask = flags.u8()
        px += if (mask and 1 != 0) dx.shortSmart() else 0
        py += if (mask and 2 != 0) dy.shortSmart() else 0
        pz += if (mask and 4 != 0) dz.shortSmart() else 0
        model.vertexX[i] = px
        model.vertexY[i] = py
        model.vertexZ[i] = pz
        if (vertexGroupFlag == 1) model.vertexLabel!![i] = labels.u8()
    }
    if (animayaFlag == 1) {
        model.droppedAnimayaSkinning = true
        for (i in 0 until vertexCount) {
            val n = labels.u8()
            repeat(n) {
                labels.u8()
                labels.u8()
            }
        }
    }

    val colours = ModelBuffer(data, colourPtr)
    val renderTypes = ModelBuffer(data, renderTypePtr)
    val priorities = ModelBuffer(data, priorityPtr)
    val alphas = ModelBuffer(data, alphaPtr)
    val groups = ModelBuffer(data, faceGroupPtr)
    val shading = ByteArray(faceCount)
    val textures = ShortArray(faceCount) { -1 }
    val texCoords = ByteArray(faceCount) { -1 }
    var anyFlat = false
    var anyTextured = false
    for (i in 0 until faceCount) {
        var colour = colours.u16()
        if (renderTypeFlag == 1) {
            val rt = renderTypes.u8()
            if (rt and 1 == 1) {
                shading[i] = 1
                anyFlat = true
            }
            if (rt and 2 == 2) {
                texCoords[i] = (rt shr 2).toByte()
                textures[i] = colour.toShort()
                colour = 127
                if (textures[i].toInt() != -1) anyTextured = true
            }
        }
        model.faceColour[i] = colour.toShort()
        if (priorityFlag == 255) model.facePriority!![i] = priorities.i8().toByte()
        if (alphaFlag == 1) model.faceAlpha!![i] = alphas.i8().toByte()
        if (faceGroupFlag == 1) model.faceLabel!![i] = groups.u8()
    }
    if (anyFlat) model.shadingType = shading
    if (anyTextured) {
        model.faceTexture = textures
        model.faceTexSpace = texCoords
    }

    val faceData = ModelBuffer(data, faceDataPtr)
    val faceTypes = ModelBuffer(data, faceTypePtr)
    var a = 0
    var b = 0
    var c = 0
    var last = 0
    for (i in 0 until faceCount) {
        when (val type = faceTypes.u8()) {
            1 -> {
                a = faceData.shortSmart() + last
                b = faceData.shortSmart() + a
                c = faceData.shortSmart() + b
                last = c
            }
            2 -> {
                b = c
                c = faceData.shortSmart() + last
                last = c
            }
            3 -> {
                a = c
                c = faceData.shortSmart() + last
                last = c
            }
            4 -> {
                val previousA = a
                a = b
                b = previousA
                c = faceData.shortSmart() + last
                last = c
            }
            else -> error("Unknown face index encoding type $type at face $i (type 2).")
        }
        model.faceA[i] = a
        model.faceB[i] = b
        model.faceC[i] = c
    }

    if (texCount > 0) {
        model.texMappingType = ByteArray(texCount)
        model.texSpaceDefA = ShortArray(texCount)
        model.texSpaceDefB = ShortArray(texCount)
        model.texSpaceDefC = ShortArray(texCount)
        val tex = ModelBuffer(data, texIndexPtr)
        for (i in 0 until texCount) {
            model.texSpaceDefA!![i] = tex.u16().toShort()
            model.texSpaceDefB!![i] = tex.u16().toShort()
            model.texSpaceDefC!![i] = tex.u16().toShort()
        }
    }
    val tail = ModelBuffer(data, tailPtr)
    if (tail.u8() == 1) model.droppedFaceZOffsets = true
    return model
}

/** Entry point used by [ModernModelDecoder.decode] for the -1,-2 footer. */
internal fun ModernModelDecoder.decodeType2(data: ByteArray): ModelData = decodeType2Impl(data)
