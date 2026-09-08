package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * CLI front end for the mesh half of the modern-content import pipeline
 * (`RSPS_DECISIONS.md` 2026-09-02 STANDING OWNER AUTHORIZATION).
 *
 * Verbs:
 *
 *  - `inspect <modelFile>` - report the source container format and decoded geometry without
 *    writing anything.
 *  - `convert <modelFile> <outFile>` - decode a modern OSRS mesh, re-encode it into the rev-667
 *    `decodeNew` container, verify the result through [Rev667ModelDecoder] (a port of the 667
 *    client's own reader) and only then write it. A verification failure writes nothing.
 *
 * Conversion losses are printed as explicit `DROPPED_*` lines rather than left implicit, so that a
 * later gate cannot mistake a lossy conversion for a faithful one.
 */
object ModelConvertTool {
    /** Rev-667 caches keep meshes in index 7, one group per model. */
    const val MODEL_INDEX = 7

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { USAGE }
        when (val verb = args[0]) {
            "inspect" -> {
                val data = File(args[1]).readBytes()
                report(args[1], data, ModernModelDecoder.decode(data))
            }
            "convert" -> {
                require(args.size >= 3) { USAGE }
                val data = File(args[1]).readBytes()
                val source = ModernModelDecoder.decode(data)
                report(args[1], data, source)

                val converted = Rev667ModelEncoder.encode(source)
                val target = Rev667ModelDecoder.decode(converted)
                val differences = compare(source, target)
                check(differences.isEmpty()) {
                    "Converted mesh does not decode back to the source geometry: ${differences.joinToString()}"
                }

                File(args[2]).parentFile?.mkdirs()
                File(args[2]).writeBytes(converted)
                println(
                    "CONVERTED bytes=${converted.size} sha1=${CacheItemProbeTool.sha1(converted)} " +
                        "verified=REV667_DECODE_NEW out=${args[2]}",
                )
            }
            "verify667" -> {
                require(args.size >= 3) { USAGE }
                val library = CacheLibrary(args[1])
                try {
                    args.drop(2).map { it.toInt() }.forEach { groupId ->
                        val data = library.data(MODEL_INDEX, groupId)
                        if (data == null) {
                            println("MODEL_$groupId=ABSENT")
                            return@forEach
                        }
                        val outcome =
                            runCatching { Rev667ModelDecoder.decode(data) }
                                .fold(
                                    onSuccess = { "OK ${it.describe()}" },
                                    onFailure = { "REJECTED ${it.javaClass.simpleName}: ${it.message}" },
                                )
                        println("MODEL_$groupId bytes=${data.size} sha1=${CacheItemProbeTool.sha1(data)} $outcome")
                    }
                } finally {
                    library.close()
                }
            }
            "analyse667" -> {
                require(args.size >= 3) { USAGE }
                val library = CacheLibrary(args[1])
                try {
                    args.drop(2).map { it.toInt() }.forEach { groupId ->
                        val data = library.data(MODEL_INDEX, groupId)
                        if (data == null) {
                            println("MODEL_$groupId=ABSENT")
                            return@forEach
                        }
                        analyse(groupId, Rev667ModelDecoder.decode(data))
                    }
                } finally {
                    library.close()
                }
            }
            "scan667" -> {
                require(args.size >= 4) { USAGE }
                val library = CacheLibrary(args[1])
                try {
                    val census = sortedMapOf<String, Int>()
                    for (groupId in args[2].toInt()..args[3].toInt()) {
                        val data = library.data(MODEL_INDEX, groupId)
                        val outcome =
                            when {
                                data == null -> "ABSENT"
                                else ->
                                    runCatching { Rev667ModelDecoder.decode(data) }
                                        .fold(onSuccess = { "DECODED" }, onFailure = { it.message ?: "FAILED" })
                            }
                        census[outcome] = (census[outcome] ?: 0) + 1
                    }
                    println("SCAN index=$MODEL_INDEX range=${args[2]}..${args[3]}")
                    census.forEach { (outcome, count) -> println("  $count x $outcome") }
                } finally {
                    library.close()
                }
            }
            else -> error("Unknown verb '$verb'.\n$USAGE")
        }
    }

    private fun report(
        path: String,
        data: ByteArray,
        model: ModelData,
    ) {
        val (penultimate, last) = ModernModelDecoder.footerOf(data)
        println("MODEL=$path bytes=${data.size} sha1=${CacheItemProbeTool.sha1(data)} footer=($penultimate,$last)")
        println(model.describe())
        if (model.droppedAnimayaSkinning) println("DROPPED_ANIMAYA_SKINNING=true")
        if (model.droppedFaceZOffsets) println("DROPPED_FACE_Z_OFFSETS=true")
    }

    /** Field-by-field geometry comparison; empty means the conversion preserved everything 667 reads. */
    fun compare(
        source: ModelData,
        target: ModelData,
    ): List<String> {
        val differences = mutableListOf<String>()
        fun check(
            label: String,
            equal: Boolean,
        ) {
            if (!equal) differences += label
        }
        check("vertexCount", source.vertexCount == target.vertexCount)
        check("faceCount", source.faceCount == target.faceCount)
        check("texSpaceCount", source.texSpaceCount == target.texSpaceCount)
        check("vertexX", source.vertexX.contentEquals(target.vertexX))
        check("vertexY", source.vertexY.contentEquals(target.vertexY))
        check("vertexZ", source.vertexZ.contentEquals(target.vertexZ))
        check("faceA", source.faceA.contentEquals(target.faceA))
        check("faceB", source.faceB.contentEquals(target.faceB))
        check("faceC", source.faceC.contentEquals(target.faceC))
        check("faceColour", source.faceColour.contentEquals(target.faceColour))
        check("globalPriority", source.globalPriority == target.globalPriority)
        check("facePriority", source.facePriority.contentEquals(target.facePriority))
        check("shadingType", source.shadingType.contentEquals(target.shadingType))
        check("faceAlpha", source.faceAlpha.contentEquals(target.faceAlpha))
        check("faceLabel", source.faceLabel.contentEquals(target.faceLabel))
        check("vertexLabel", source.vertexLabel.contentEquals(target.vertexLabel))
        check("faceTexture", source.faceTexture.contentEquals(target.faceTexture))
        check("faceTexSpace", source.faceTexSpace.contentEquals(target.faceTexSpace))
        check("texMappingType", source.texMappingType.contentEquals(target.texMappingType))
        return differences
    }

    /**
     * Sidedness analysis: whether a mesh is a closed solid or a one-sided sheet.
     *
     * The 667 renderer back-face culls, so a sheet whose triangles are all wound the same way is
     * simply invisible from behind - which is what a "the barrier disappears depending on which
     * side you view it from" report looks like. Two independent signals are printed: how many
     * edges are shared by exactly two faces with opposite winding (the manifold test), and the
     * sign histogram of the face normals along each axis.
     */
    private fun analyse(
        groupId: Int,
        model: ModelData,
    ) {
        val edges = HashMap<Long, Int>()
        fun key(a: Int, b: Int) = (a.toLong() shl 32) or b.toLong()
        for (i in 0 until model.faceCount) {
            val a = model.faceA[i]
            val b = model.faceB[i]
            val c = model.faceC[i]
            listOf(a to b, b to c, c to a).forEach { (u, v) -> edges[key(u, v)] = (edges[key(u, v)] ?: 0) + 1 }
        }
        var opposed = 0
        var boundary = 0
        var duplicated = 0
        edges.forEach { (k, count) ->
            val u = (k ushr 32).toInt()
            val v = (k and 0xFFFFFFFFL).toInt()
            val reverse = edges[key(v, u)] ?: 0
            when {
                count > 1 -> duplicated++
                reverse > 0 -> opposed++
                else -> boundary++
            }
        }
        val normals = intArrayOf(0, 0, 0, 0, 0, 0)
        for (i in 0 until model.faceCount) {
            val a = model.faceA[i]
            val b = model.faceB[i]
            val c = model.faceC[i]
            val ux = model.vertexX[b] - model.vertexX[a]
            val uy = model.vertexY[b] - model.vertexY[a]
            val uz = model.vertexZ[b] - model.vertexZ[a]
            val vx = model.vertexX[c] - model.vertexX[a]
            val vy = model.vertexY[c] - model.vertexY[a]
            val vz = model.vertexZ[c] - model.vertexZ[a]
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            if (nx > 0) normals[0]++ else if (nx < 0) normals[1]++
            if (ny > 0) normals[2]++ else if (ny < 0) normals[3]++
            if (nz > 0) normals[4]++ else if (nz < 0) normals[5]++
        }
        println(
            "MODEL_$groupId faces=${model.faceCount} directedEdges=${edges.size} " +
                "opposedPairs=$opposed boundary=$boundary duplicatedDirection=$duplicated",
        )
        println(
            "  normalSigns X+=${normals[0]} X-=${normals[1]} Y+=${normals[2]} Y-=${normals[3]} " +
                "Z+=${normals[4]} Z-=${normals[5]}",
        )
    }

    private const val USAGE =
        "Usage:\n" +
            "  inspect <modelFile>\n" +
            "  convert <modelFile> <outFile>\n" +
            "  verify667 <cachePath> <groupId> [groupId ...]\n" +
            "  scan667 <cachePath> <fromGroupId> <toGroupId>"
}
