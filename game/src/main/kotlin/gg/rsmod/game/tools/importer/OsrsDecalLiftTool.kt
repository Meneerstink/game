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

    /** Lifts the hidden detail faces of [m] in place; null when there is none. */
    fun lift(m: ModelData): Lift? {
        val pr = m.facePriority ?: return null
        val n = m.faceCount
        val x = m.vertexX
        val y = m.vertexY
        val z = m.vertexZ
        val a = m.faceA
        val b = m.faceB
        val c = m.faceC
        val normal = arrayOfNulls<DoubleArray>(n)
        for (f in 0 until n) {
            val ux = (x[b[f]] - x[a[f]]).toDouble(); val uy = (y[b[f]] - y[a[f]]).toDouble(); val uz = (z[b[f]] - z[a[f]]).toDouble()
            val vx = (x[c[f]] - x[a[f]]).toDouble(); val vy = (y[c[f]] - y[a[f]]).toDouble(); val vz = (z[c[f]] - z[a[f]]).toDouble()
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 0) normal[f] = doubleArrayOf(nx / len, ny / len, nz / len)
        }
        // How far each detail face must come forward (0 = not a hidden detail face).
        val move = DoubleArray(n)
        for (f in 0 until n) {
            val nf = normal[f] ?: continue
            val cx = (x[a[f]] + x[b[f]] + x[c[f]]) / 3.0
            val cy = (y[a[f]] + y[b[f]] + y[c[f]]) / 3.0
            val cz = (z[a[f]] + z[b[f]] + z[c[f]]) / 3.0
            for (g in 0 until n) {
                val ng = normal[g] ?: continue
                if (pr[g] >= pr[f]) continue
                if (nf[0] * ng[0] + nf[1] * ng[1] + nf[2] * ng[2] < SAME_FACING) continue
                val d = (cx - x[a[g]]) * ng[0] + (cy - y[a[g]]) * ng[1] + (cz - z[a[g]]) * ng[2]
                if (d < -MAX_DEPTH || d > CLEARANCE) continue
                if (!insideTriangle(cx - d * ng[0], cy - d * ng[1], cz - d * ng[2], x, y, z, a[g], b[g], c[g], ng)) continue
                move[f] = maxOf(move[f], CLEARANCE - d)
            }
        }
        val detail = (0 until n).filter { move[it] > 0 }.toSet()
        if (detail.isEmpty()) return null
        // A vertex may move when no face of a LOWER priority uses it: the detail itself and the faces of its own layer (the rest of
        // an emblem that is not covered) come forward together; the covering cloth never moves.
        val lowestUser = IntArray(m.vertexCount) { Int.MAX_VALUE }
        for (f in 0 until n) for (v in intArrayOf(a[f], b[f], c[f])) lowestUser[v] = minOf(lowestUser[v], pr[f].toInt())
        val lifted = detail.filter { f -> intArrayOf(a[f], b[f], c[f]).all { lowestUser[it] >= pr[f] } }
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
                val lift = lift(model) ?: return@forEach
                println("DECAL model $id: ${lift.faces} faces lifted, ${lift.vertices} vertices, up to ${lift.maxMove} units")
                mutations += CacheMutation(
                    MODEL_INDEX, id, 0, Rev667ModelEncoder.encode(model), "model $id: OSRS detail faces in front",
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
