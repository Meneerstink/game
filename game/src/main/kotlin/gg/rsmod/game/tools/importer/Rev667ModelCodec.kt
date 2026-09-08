package gg.rsmod.game.tools.importer

import java.io.ByteArrayOutputStream

/**
 * Re-encodes a [ModelData] into the only mesh container this project's rev-667 client can read
 * (modern-content import pipeline, `RSPS_DECISIONS.md` 2026-09-02 STANDING OWNER AUTHORIZATION).
 *
 * The 667 client (`com.jagex.graphics.Mesh`) dispatches on the last two bytes and recognises only
 * `(-1, -1)` -> `decodeNew`, with everything else falling through to the pre-2008 `decodeOld`.
 * Modern OSRS models carry `(-1, -3)`, so they cannot be dropped into a 667 cache unchanged - see
 * `RSPS_CURRENT_SPRINT.json` gate A5.
 *
 * The section *ordering* of `decodeNew` is identical to the modern layout, so this is a
 * re-container rather than a geometry rewrite. The differences that force a rewrite anyway are:
 *
 *  - the trailer is 21 bytes plus the `(-1, -1)` footer, not 24 bytes plus `(-1, -3)`;
 *  - modern stores the vertex-label block length explicitly, 667 infers it as one byte per vertex;
 *  - modern's flat-shading byte is a plain flag, 667's is bit 0 of a `globalFlags` bitfield whose
 *    other bits (particle effects, billboards, version) select trailing sections 667 alone has;
 *  - modern's animaya skinning and per-face z-offsets have no 667 representation and are dropped,
 *    which [ModelData.droppedAnimayaSkinning] / [ModelData.droppedFaceZOffsets] record explicitly.
 *
 * Face indices are always re-emitted with encoding type 1 (three explicit deltas). That is valid
 * for every face and keeps the encoder simple; the delta range is checked rather than assumed,
 * because a silently truncated index would produce a corrupt but loadable mesh.
 */
object Rev667ModelEncoder {
    private const val SMART_SINGLE_MIN = -64
    private const val SMART_SINGLE_MAX = 63
    private const val SMART_MIN = -16384
    private const val SMART_MAX = 16383

    fun encode(model: ModelData): ByteArray {
        val texMappingType = model.texMappingType
        if (model.texSpaceCount > 0) {
            requireNotNull(texMappingType) { "texSpaceCount=${model.texSpaceCount} but no mapping types." }
            val complex = texMappingType.count { it >= 1 && it <= 3 }
            require(complex == 0) { "Rev-667 encoding of complex texture mappings is not implemented." }
        }

        val vertexFlags = ByteArrayOutputStream()
        val vertexX = ByteArrayOutputStream()
        val vertexY = ByteArrayOutputStream()
        val vertexZ = ByteArrayOutputStream()
        var previousX = 0
        var previousY = 0
        var previousZ = 0
        for (i in 0 until model.vertexCount) {
            val deltaX = model.vertexX[i] - previousX
            val deltaY = model.vertexY[i] - previousY
            val deltaZ = model.vertexZ[i] - previousZ
            var mask = 0
            if (deltaX != 0) {
                mask = mask or 0x1
                writeShortSmart(vertexX, deltaX, "vertex $i x")
            }
            if (deltaY != 0) {
                mask = mask or 0x2
                writeShortSmart(vertexY, deltaY, "vertex $i y")
            }
            if (deltaZ != 0) {
                mask = mask or 0x4
                writeShortSmart(vertexZ, deltaZ, "vertex $i z")
            }
            vertexFlags.write(mask)
            previousX = model.vertexX[i]
            previousY = model.vertexY[i]
            previousZ = model.vertexZ[i]
        }

        val faceTypes = ByteArrayOutputStream()
        val faceData = ByteArrayOutputStream()
        var last = 0
        for (i in 0 until model.faceCount) {
            faceTypes.write(1)
            writeShortSmart(faceData, model.faceA[i] - last, "face $i a")
            writeShortSmart(faceData, model.faceB[i] - model.faceA[i], "face $i b")
            writeShortSmart(faceData, model.faceC[i] - model.faceB[i], "face $i c")
            last = model.faceC[i]
        }

        val faceColour = ByteArrayOutputStream()
        val faceTexture = ByteArrayOutputStream()
        val faceTexSpace = ByteArrayOutputStream()
        for (i in 0 until model.faceCount) {
            writeShort(faceColour, model.faceColour[i].toInt())
            val textures = model.faceTexture
            if (textures != null) {
                writeShort(faceTexture, textures[i] + 1)
                val texSpace = model.faceTexSpace
                if (texSpace != null && textures[i] != (-1).toShort()) {
                    faceTexSpace.write(texSpace[i] + 1)
                }
            }
        }

        val planarMapping = ByteArrayOutputStream()
        for (i in 0 until model.texSpaceCount) {
            if (texMappingType!![i].toInt() and 0xFF == 0) {
                writeShort(planarMapping, model.texSpaceDefA!![i].toInt())
                writeShort(planarMapping, model.texSpaceDefB!![i].toInt())
                writeShort(planarMapping, model.texSpaceDefC!![i].toInt())
            }
        }

        val out = ByteArrayOutputStream()
        if (texMappingType != null) out.write(texMappingType)
        out.write(vertexFlags.toByteArray())
        model.shadingType?.let { out.write(it) }
        out.write(faceTypes.toByteArray())
        model.facePriority?.let { out.write(it) }
        model.faceLabel?.let { labels -> labels.forEach { out.write(it and 0xFF) } }
        model.vertexLabel?.let { labels -> labels.forEach { out.write(it and 0xFF) } }
        model.faceAlpha?.let { out.write(it) }
        out.write(faceData.toByteArray())
        out.write(faceTexture.toByteArray())
        out.write(faceTexSpace.toByteArray())
        out.write(faceColour.toByteArray())
        out.write(vertexX.toByteArray())
        out.write(vertexY.toByteArray())
        out.write(vertexZ.toByteArray())
        out.write(planarMapping.toByteArray())

        writeShort(out, model.vertexCount)
        writeShort(out, model.faceCount)
        out.write(model.texSpaceCount)
        // globalFlags: bit 0 flat shading only. Particle effects, billboards and the version byte
        // are 667-only extensions with no modern source data, so they stay off and the client
        // keeps its default version 12 (texture-space scale size 6).
        out.write(if (model.shadingType != null) 0x1 else 0x0)
        out.write(if (model.facePriority != null) 255 else model.globalPriority.toInt() and 0xFF)
        out.write(if (model.faceAlpha != null) 1 else 0)
        out.write(if (model.faceLabel != null) 1 else 0)
        out.write(if (model.faceTexture != null) 1 else 0)
        out.write(if (model.vertexLabel != null) 1 else 0)
        writeShort(out, vertexX.size())
        writeShort(out, vertexY.size())
        writeShort(out, vertexZ.size())
        writeShort(out, faceData.size())
        writeShort(out, faceTexSpace.size())
        out.write(0xFF)
        out.write(0xFF)

        return out.toByteArray()
    }

    private fun writeShort(
        out: ByteArrayOutputStream,
        value: Int,
    ) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeShortSmart(
        out: ByteArrayOutputStream,
        value: Int,
        what: String,
    ) {
        when {
            value in SMART_SINGLE_MIN..SMART_SINGLE_MAX -> out.write(value + 64)
            value in SMART_MIN..SMART_MAX -> writeShort(out, value + 49152)
            else -> error("$what delta $value is outside the shortSmart range $SMART_MIN..$SMART_MAX.")
        }
    }
}

/**
 * A faithful port of the rev-667 client's `com.jagex.graphics.Mesh.decodeNew`, used as an
 * independent reader so that [Rev667ModelEncoder] output is proven to decode under the *target*
 * algorithm rather than merely round-tripping through this pipeline's own modern decoder.
 *
 * Only what a converted model can contain is implemented: the particle, billboard and versioned
 * texture-scale sections are rejected outright instead of being partially parsed, since nothing in
 * this pipeline can produce them and a half-read section would mask an encoder bug.
 */
object Rev667ModelDecoder {
    fun decode(data: ByteArray): ModelData {
        val footer = data[data.size - 2].toInt() to data[data.size - 1].toInt()
        check(footer == -1 to -1) { "Rev-667 decodeNew expects footer (-1, -1) but found $footer." }

        val header = ModelBuffer(data, data.size - 23)
        val vertexCount = header.u16()
        val faceCount = header.u16()
        val texSpaceCount = header.u8()
        val globalFlags = header.u8()
        val hasFlatShading = globalFlags and 0x1 != 0
        check(globalFlags and 0x2 == 0) { "Particle-effect meshes are out of scope for this pipeline." }
        check(globalFlags and 0x4 == 0) { "Billboard meshes are out of scope for this pipeline." }
        if (globalFlags and 0x8 != 0) {
            // The version byte sits immediately before the trailer; the client rewinds, reads it and
            // rewinds forward again. It only sizes the texture-space scale block, which is empty
            // unless complex mappings are present, and those are rejected below.
            header.position -= 7
            header.u8()
            header.position += 6
        }
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

        val model = ModelData(vertexCount, faceCount, texSpaceCount)

        var planarMappingCount = 0
        if (texSpaceCount > 0) {
            val types = ByteArray(texSpaceCount)
            val typeBuffer = ModelBuffer(data, 0)
            for (i in 0 until texSpaceCount) {
                types[i] = typeBuffer.i8().toByte()
                if (types[i].toInt() == 0) planarMappingCount++
            }
            check(types.count { it >= 1 && it <= 3 } == 0) {
                "Complex texture mappings are out of scope for this pipeline."
            }
            model.texMappingType = types
        }

        var ptr = texSpaceCount
        val vertexFlagsPtr = ptr
        ptr += vertexCount
        val shadingPtr = ptr
        if (hasFlatShading) ptr += faceCount
        val faceTypePtr = ptr
        ptr += faceCount
        val facePriorityPtr = ptr
        if (priorityFlag == 255) ptr += faceCount
        val faceLabelPtr = ptr
        if (faceGroupFlag == 1) ptr += faceCount
        val vertexLabelPtr = ptr
        if (vertexLabelFlag == 1) ptr += vertexCount
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

        // The client never checks this; it simply seeks to `ptr` for the optional particle and
        // billboard sections. Real 667 meshes are sometimes a byte or two longer than their
        // sections account for, so only an *overrun* is an error.
        check(ptr <= data.size - 23) {
            "Section sizes claim $ptr bytes but the mesh body is only ${data.size - 23} bytes."
        }

        if (priorityFlag == 255) {
            model.facePriority = ByteArray(faceCount)
        } else {
            model.globalPriority = priorityFlag.toByte()
        }
        if (hasFlatShading) model.shadingType = ByteArray(faceCount)
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
            previousX += if (mask and 0x1 != 0) deltaX.shortSmart() else 0
            previousY += if (mask and 0x2 != 0) deltaY.shortSmart() else 0
            previousZ += if (mask and 0x4 != 0) deltaZ.shortSmart() else 0
            model.vertexX[i] = previousX
            model.vertexY[i] = previousY
            model.vertexZ[i] = previousZ
            if (vertexLabelFlag == 1) model.vertexLabel!![i] = labels.u8()
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
            if (hasFlatShading) model.shadingType!![i] = shading.i8().toByte()
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

        return model
    }
}
