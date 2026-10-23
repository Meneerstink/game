package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Owner A5 2026-10-11 ("the imbued god capes show no emblem when worn"): OSRS paints a model's faces in priority order, so a
 * detail face of a higher priority shows even where it lies a few units BEHIND a lower-priority face facing the same way - the
 * Imbued Saradomin cape's white emblem (8 faces, priority 6) sits 3-10 units inside the blue cape (priority 5). This client's
 * hardware renderers depth-test opaque model faces and order them by priority only for translucent faces (Model_Sub2 sort key),
 * so the cape in front hid the emblem; a few white pixels showed at its edges.
 *
 * The fix is in the mesh, the same picture OSRS paints: every such detail face is lifted just in front of the face that covers
 * it, along its own normal. Only a face is touched that (1) has a higher priority than the covering face, (2) faces the same way
 * (normals within ~25 degrees), (3) lies at most [MAX_DEPTH] units behind it or on it, with its centre over that face, and
 * (4) uses only vertices that no lower-priority face uses (the rest of its own layer moves with it, the covering face never).
 * N32 (2026-10-23): a detail face that shares a vertex with a lower-priority face (215 imported meshes: max capes, Infernal cape,
 * (g) sets, ...) first gets its own copy of that vertex ([liftAll]: same position, label and skinning, one copy per priority
 * layer), so it can come forward while the cloth stays where it is.
 * Run over every OSRS-imported item model (OSRS_IMPORT_MASTER.yml, role model); one [CacheTransaction] over both production
 * caches; running it again changes only what is still hidden (none after a full run).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.OsrsDecalLiftTool plan|apply [modelId ...]`
 */
object OsrsDecalLiftTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private val MASTER = RspsPaths.root("OSRS_IMPORT_MASTER.yml")
    private const val MODEL_INDEX = 7

    /** Deepest a hidden detail face may lie behind the covering face (model units). */
    const val MAX_DEPTH = 12.0

    /** Gap left in front of the covering face (model units). */
    const val CLEARANCE = 1.0
    private const val SAME_FACING = 0.9

    class Lift(val faces: Int, val vertices: Int, val maxMove: Int)

    /** The OSRS item meshes this server imported (role model). */
    fun importedModels(): List<Int> {
        val ids = sortedSetOf<Int>()
        var model = false
        File(MASTER).forEachLine { line ->
            val t = line.trim()
            when {
                t.startsWith("- role:") -> model = t == "- role: model"
                model && t.startsWith("local_id:") -> {
                    t.removePrefix("local_id:").trim().toIntOrNull()?.let { ids += it }
                    model = false
                }
            }
        }
        return ids.toList()
    }

    /**
     * [m] with every hidden detail face in front ([lift] after giving such faces their own copies of the vertices they share with a
     * lower-priority face); null when nothing is hidden. [m] itself is not changed.
     */
    fun liftAll(m: ModelData): Pair<ModelData, Lift>? {
        // A lifted face can itself cover a detail face of a still higher priority: repeat until nothing is hidden.
        var model = m
        var faces = 0
        var vertices = 0
        var maxMove = 0
        for (round in 0 until 16) {
            val (next, lift) = liftOnce(model) ?: break
            model = next
            faces += lift.faces
            vertices += lift.vertices
            maxMove = maxOf(maxMove, lift.maxMove)
        }
        return if (model === m) null else model to Lift(faces, vertices, maxMove)
    }

    private fun liftOnce(m: ModelData): Pair<ModelData, Lift>? {
        val pr = m.facePriority ?: return null
        val move = hiddenMoves(m) ?: return null
        val n = m.faceCount
        val faces = arrayOf(m.faceA, m.faceB, m.faceC)
        val lowestUser = IntArray(m.vertexCount) { Int.MAX_VALUE }
        for (f in 0 until n) for (k in 0..2) lowestUser[faces[k][f]] = minOf(lowestUser[faces[k][f]], pr[f].toInt())
        // (vertex, priority layer) -> copy, for every vertex a hidden detail face shares with a lower-priority face.
        val copies = linkedMapOf<Long, Int>()
        for (f in 0 until n) {
            if (move[f] <= 0) continue
            for (k in 0..2) {
                val v = faces[k][f]
                if (lowestUser[v] < pr[f]) copies.getOrPut(v.toLong() shl 8 or (pr[f].toLong() and 0xFF)) { m.vertexCount + copies.size }
            }
        }
        val out = if (copies.isEmpty()) copy(m, 0) else copy(m, copies.size)
        copies.forEach { (key, copyIndex) ->
            val v = (key ushr 8).toInt()
            out.vertexX[copyIndex] = m.vertexX[v]
            out.vertexY[copyIndex] = m.vertexY[v]
            out.vertexZ[copyIndex] = m.vertexZ[v]
            out.vertexLabel?.let { it[copyIndex] = m.vertexLabel!![v] }
            out.animayaBones?.let { it[copyIndex] = m.animayaBones!![v].copyOf() }
            out.animayaWeights?.let { it[copyIndex] = m.animayaWeights!![v].copyOf() }
        }
        // Every face of that layer using the vertex takes the copy (the layer stays whole; the cloth keeps the original).
        val outFaces = arrayOf(out.faceA, out.faceB, out.faceC)
        for (f in 0 until n) for (k in 0..2) copies[outFaces[k][f].toLong() shl 8 or (pr[f].toLong() and 0xFF)]?.let { outFaces[k][f] = it }
        val lift = lift(out) ?: return null
        return out to lift
    }

    /** [m] with [extra] more vertices (zeroed), every other field copied. */
    private fun copy(
        m: ModelData,
        extra: Int,
    ): ModelData {
        val out = ModelData(m.vertexCount + extra, m.faceCount, m.texSpaceCount)
        m.vertexX.copyInto(out.vertexX); m.vertexY.copyInto(out.vertexY); m.vertexZ.copyInto(out.vertexZ)
        m.faceA.copyInto(out.faceA); m.faceB.copyInto(out.faceB); m.faceC.copyInto(out.faceC)
        m.faceColour.copyInto(out.faceColour)
        out.shadingType = m.shadingType?.copyOf()
        out.facePriority = m.facePriority?.copyOf()
        out.globalPriority = m.globalPriority
        out.version = m.version
        out.faceAlpha = m.faceAlpha?.copyOf()
        out.faceLabel = m.faceLabel?.copyOf()
        out.vertexLabel = m.vertexLabel?.copyOf(out.vertexCount)
        out.faceTexture = m.faceTexture?.copyOf()
        out.faceTexSpace = m.faceTexSpace?.copyOf()
        out.texMappingType = m.texMappingType?.copyOf()
        out.texSpaceDefA = m.texSpaceDefA?.copyOf()
        out.texSpaceDefB = m.texSpaceDefB?.copyOf()
        out.texSpaceDefC = m.texSpaceDefC?.copyOf()
        out.droppedAnimayaSkinning = m.droppedAnimayaSkinning
        out.animayaBones = m.animayaBones?.let { b -> Array(out.vertexCount) { if (it < b.size) b[it].copyOf() else IntArray(0) } }
        out.animayaWeights = m.animayaWeights?.let { w -> Array(out.vertexCount) { if (it < w.size) w[it].copyOf() else IntArray(0) } }
        out.droppedFaceZOffsets = m.droppedFaceZOffsets
        return out
    }

    /** How far each face of [m] must come forward to show in front of the face covering it (0 = not hidden); null: none. */
    private fun hiddenMoves(m: ModelData): DoubleArray? {
        val pr = m.facePriority ?: return null
        val normal = normals(m)
        val n = m.faceCount
        val x = m.vertexX
        val y = m.vertexY
        val z = m.vertexZ
        val a = m.faceA
        val b = m.faceB
        val c = m.faceC
        val move = DoubleArray(n)
        for (f in 0 until n) {
            val nf = normal[f] ?: continue
            val cx = (x[a[f]] + x[b[f]] + x[c[f]]) / 3.0
            val cy = (y[a[f]] + y[b[f]] + y[c[f]]) / 3.0
            val cz = (z[a[f]] + z[b[f]] + z[c[f]]) / 3.0
            for (g in 0 until n) {
                val ng = normal[g] ?: continue
                if (pr[g] >= pr[f]) continue
                val facing = nf[0] * ng[0] + nf[1] * ng[1] + nf[2] * ng[2]
                if (facing < SAME_FACING) continue
                val d = (cx - x[a[g]]) * ng[0] + (cy - y[a[g]]) * ng[1] + (cz - z[a[g]]) * ng[2]
                // In front by half the clearance counts as shown (a lifted face stays put on the next run).
                if (d < -MAX_DEPTH || d >= CLEARANCE / 2) continue
                if (!insideTriangle(cx - d * ng[0], cy - d * ng[1], cz - d * ng[2], x, y, z, a[g], b[g], c[g], ng)) continue
                // The face moves along its own normal: divided by the facing, it really ends CLEARANCE in front of the covering face.
                move[f] = maxOf(move[f], (CLEARANCE - d) / facing)
            }
        }
        return move.takeIf { mv -> mv.any { it > 0 } }
    }

    private fun normals(m: ModelData): Array<DoubleArray?> {
        val x = m.vertexX
        val y = m.vertexY
        val z = m.vertexZ
        val a = m.faceA
        val b = m.faceB
        val c = m.faceC
        return Array(m.faceCount) { f ->
            val ux = (x[b[f]] - x[a[f]]).toDouble(); val uy = (y[b[f]] - y[a[f]]).toDouble(); val uz = (z[b[f]] - z[a[f]]).toDouble()
            val vx = (x[c[f]] - x[a[f]]).toDouble(); val vy = (y[c[f]] - y[a[f]]).toDouble(); val vz = (z[c[f]] - z[a[f]]).toDouble()
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 0) doubleArrayOf(nx / len, ny / len, nz / len) else null
        }
    }

    /**
     * Lifts the hidden detail faces of [m] in place, only those whose vertices no lower-priority face uses (the 2026-10-11 lift;
     * [liftAll] frees the shared ones first); null when there is none.
     */
    fun lift(m: ModelData): Lift? {
        val pr = m.facePriority ?: return null
        val n = m.faceCount
        val x = m.vertexX
        val y = m.vertexY
        val z = m.vertexZ
        val a = m.faceA
        val b = m.faceB
        val c = m.faceC
        val normal = normals(m)
        // How far each detail face must come forward (0 = not a hidden detail face).
        val move = hiddenMoves(m) ?: return null
        val detail = (0 until n).filter { move[it] > 0 }.toSet()
        if (detail.isEmpty()) return null
        // A vertex may move when no face of a LOWER priority uses it: the detail itself and the faces of its own layer (the rest of
        // an emblem that is not covered) come forward together; the covering cloth never moves.
        val lowestUser = IntArray(m.vertexCount) { Int.MAX_VALUE }
        for (f in 0 until n) for (v in intArrayOf(a[f], b[f], c[f])) lowestUser[v] = minOf(lowestUser[v], pr[f].toInt())
        // A face whose vertex also carries a face of its layer (or above) facing the other way belongs to a closed solid, not a painted
        // sheet: moving it would push that back face out (N32: 7 meshes see-sawed between two positions forever). Left alone.
        val users = Array(m.vertexCount) { ArrayList<Int>(4) }
        for (f in 0 until n) for (v in intArrayOf(a[f], b[f], c[f])) users[v] += f
        fun solid(f: Int): Boolean {
            val nf = normal[f]!!
            return intArrayOf(a[f], b[f], c[f]).any { v ->
                users[v].any { h -> h != f && pr[h] >= pr[f] && normal[h]?.let { nh -> nh[0] * nf[0] + nh[1] * nf[1] + nh[2] * nf[2] < -0.5 } == true }
            }
        }
        val lifted = detail.filter { f -> intArrayOf(a[f], b[f], c[f]).all { lowestUser[it] >= pr[f] } && !solid(f) }
        if (lifted.isEmpty()) return null
        val dx = DoubleArray(m.vertexCount); val dy = DoubleArray(m.vertexCount); val dz = DoubleArray(m.vertexCount)
        val amount = DoubleArray(m.vertexCount)
        lifted.forEach { f ->
            val nf = normal[f]!!
            for (v in intArrayOf(a[f], b[f], c[f])) {
                if (move[f] > amount[v]) {
                    amount[v] = move[f]; dx[v] = nf[0] * move[f]; dy[v] = nf[1] * move[f]; dz[v] = nf[2] * move[f]
                }
            }
        }
        var vertices = 0
        var maxMove = 0
        for (v in 0 until m.vertexCount) {
            if (amount[v] <= 0) continue
            vertices++
            // Round away from zero so the face really ends up in front.
            fun step(d: Double) = if (d >= 0) ceil(d).toInt() else -ceil(-d).toInt()
            x[v] += step(dx[v]); y[v] += step(dy[v]); z[v] += step(dz[v])
            maxMove = maxOf(maxMove, ceil(amount[v]).toInt())
        }
        return Lift(lifted.size, vertices, maxMove)
    }

    private fun insideTriangle(
        px: Double, py: Double, pz: Double,
        x: IntArray, y: IntArray, z: IntArray,
        a: Int, b: Int, c: Int,
        n: DoubleArray,
    ): Boolean {
        // Project onto the plane's dominant axes and test the barycentric signs.
        val ax = abs(n[0]); val ay = abs(n[1]); val az = abs(n[2])
        fun u(i: Int) = if (ax >= ay && ax >= az) y[i].toDouble() else x[i].toDouble()
        fun w(i: Int) = if (az >= ax && az >= ay) y[i].toDouble() else z[i].toDouble()
        val pu = if (ax >= ay && ax >= az) py else px
        val pw = if (az >= ax && az >= ay) py else pz
        fun cross(u1: Double, w1: Double, u2: Double, w2: Double, u3: Double, w3: Double) = (u2 - u1) * (w3 - w1) - (w2 - w1) * (u3 - u1)
        val d1 = cross(pu, pw, u(a), w(a), u(b), w(b))
        val d2 = cross(pu, pw, u(b), w(b), u(c), w(c))
        val d3 = cross(pu, pw, u(c), w(c), u(a), w(a))
        val neg = d1 < 0 || d2 < 0 || d3 < 0
        val pos = d1 > 0 || d2 > 0 || d3 > 0
        return !(neg && pos)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val only = args.drop(1).mapNotNull { it.toIntOrNull() }
        val models = only.ifEmpty { importedModels() }
        val library = CacheLibrary(GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            models.forEach { id ->
                val current = library.data(MODEL_INDEX, id, 0) ?: return@forEach
                val model = runCatching { Rev667ModelDecoder.decode(current) }.getOrNull() ?: return@forEach
                // Never write a mesh this codec cannot reproduce byte for byte.
                if (!Rev667ModelEncoder.encode(model).contentEquals(current)) return@forEach
                val (lifted, lift) = liftAll(model) ?: return@forEach
                println("DECAL model $id: ${lift.faces} faces lifted, ${lift.vertices} vertices (${lifted.vertexCount - model.vertexCount} copied), up to ${lift.maxMove} units")
                mutations += CacheMutation(
                    MODEL_INDEX, id, 0, Rev667ModelEncoder.encode(lifted), "model $id: OSRS detail faces in front",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                )
            }
        } finally {
            library.close()
        }
        println("DECAL_LIFT models scanned=${models.size} to change=${mutations.size}")
        if (mutations.isEmpty()) return
        val transaction = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = transaction.preflight()
        val errors = transaction.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) return
        val result = transaction.apply(preflight)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("DECAL_LIFT transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
