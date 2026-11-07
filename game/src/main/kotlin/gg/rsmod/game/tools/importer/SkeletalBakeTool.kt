package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Breach audit 2026-09-27, phase 4 (skeletal): OSRS "animaya" skeletal animations baked into classic rev-667 frames at import time,
 * so the 667 client plays them with its existing classic animation code - nothing in the client changes.
 *
 * Source of the formats (no Jagex documentation exists): MRobertEvers/oldschool-clientc `docs/skeletal/` (ANIMAYA.md,
 * SkeletalSeq.ts, SkeletalBone.ts, SkeletalBase.ts, Curve.ts, CurveInterp.ts), a port of the OSRS client; this file is a
 * straight port of those, fetched 2026-09-27.
 *
 * How the bake works: the skeletal clip is evaluated like the OSRS client does (curves -> per-bone local matrix
 * rot(Z*X*Y quaternion) * scale, translation -> model-space parent chain -> times the inverse bind matrix) and every vertex is
 * linear-blend skinned (Y and Z flipped around the matrices). The result is a vertex position per frame. A 667 model is then
 * given ONE vertex label per vertex (label = vertex index), and each baked frame is a classic frame on a base with one translate
 * group per vertex, moving that vertex from its bind position to its skinned position. That is exact for any skinning, but a
 * 667 base holds at most 255 groups, so only models with at most 255 vertices can be baked this way; bigger ones are refused.
 *
 * Usage: `SkeletalBakeTool probe` | `SkeletalBakeTool locs [--apply]` (the Breach and Boss Spawn locs of the deadman-breach batch).
 */
object SkeletalBakeTool {
    const val INDEX_SKELETONS = 1
    const val INDEX_ANIMAYA = 22
    const val MAX_VERTICES = 255

    /** Client cycles per baked frame (skeletal clips advance one tick per client cycle). */
    const val STEP = 2

    // ---- byte reading --------------------------------------------------------------------------------------------------

    class In(val b: ByteArray, var p: Int = 0) {
        fun u8() = b[p++].toInt() and 0xFF
        fun s16(): Int = ((u8() shl 8) or u8()).toShort().toInt()
        fun u16() = (u8() shl 8) or u8()
        fun i32() = (u16() shl 16) or u16()
        fun f32() = java.lang.Float.intBitsToFloat(i32()).toDouble()
        /** ByteBuffer.readSmart2 of the OSRS client: a signed smart (u8 - 64 or u16 - 49152); bone 0 is written as 64. */
        fun smart2(): Int = if ((b[p].toInt() and 0xFF) < 128) u8() - 64 else u16() - 49152
        val remaining get() = b.size - p
    }

    // ---- 4x4 column-major matrices (gl-matrix conventions) -------------------------------------------------------------

    fun identity() = DoubleArray(16).also { it[0] = 1.0; it[5] = 1.0; it[10] = 1.0; it[15] = 1.0 }

    fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
        val o = DoubleArray(16)
        for (c in 0..3) for (r in 0..3) {
            var s = 0.0
            for (k in 0..3) s += a[k * 4 + r] * b[c * 4 + k]
            o[c * 4 + r] = s
        }
        return o
    }

    fun invert(a: DoubleArray): DoubleArray {
        val a00 = a[0]; val a01 = a[1]; val a02 = a[2]; val a03 = a[3]
        val a10 = a[4]; val a11 = a[5]; val a12 = a[6]; val a13 = a[7]
        val a20 = a[8]; val a21 = a[9]; val a22 = a[10]; val a23 = a[11]
        val a30 = a[12]; val a31 = a[13]; val a32 = a[14]; val a33 = a[15]
        val b00 = a00 * a11 - a01 * a10; val b01 = a00 * a12 - a02 * a10; val b02 = a00 * a13 - a03 * a10
        val b03 = a01 * a12 - a02 * a11; val b04 = a01 * a13 - a03 * a11; val b05 = a02 * a13 - a03 * a12
        val b06 = a20 * a31 - a21 * a30; val b07 = a20 * a32 - a22 * a30; val b08 = a20 * a33 - a23 * a30
        val b09 = a21 * a32 - a22 * a31; val b10 = a21 * a33 - a23 * a31; val b11 = a22 * a33 - a23 * a32
        var det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06
        if (det == 0.0) return identity()
        det = 1.0 / det
        return doubleArrayOf(
            (a11 * b11 - a12 * b10 + a13 * b09) * det, (a02 * b10 - a01 * b11 - a03 * b09) * det,
            (a31 * b05 - a32 * b04 + a33 * b03) * det, (a22 * b04 - a21 * b05 - a23 * b03) * det,
            (a12 * b08 - a10 * b11 - a13 * b07) * det, (a00 * b11 - a02 * b08 + a03 * b07) * det,
            (a32 * b02 - a30 * b05 - a33 * b01) * det, (a20 * b05 - a22 * b02 + a23 * b01) * det,
            (a10 * b10 - a11 * b08 + a13 * b06) * det, (a01 * b08 - a00 * b10 - a03 * b06) * det,
            (a30 * b04 - a31 * b02 + a33 * b00) * det, (a21 * b02 - a20 * b04 - a23 * b00) * det,
            (a11 * b07 - a10 * b09 - a12 * b06) * det, (a00 * b09 - a01 * b07 + a02 * b06) * det,
            (a31 * b01 - a30 * b03 - a32 * b00) * det, (a20 * b03 - a21 * b01 + a22 * b00) * det,
        )
    }

    /** Quaternion (x, y, z, w). */
    fun axisAngle(ax: Double, ay: Double, az: Double, rad: Double): DoubleArray {
        val s = sin(rad / 2)
        return doubleArrayOf(ax * s, ay * s, az * s, cos(rad / 2))
    }

    fun qmul(a: DoubleArray, b: DoubleArray): DoubleArray {
        val (ax, ay, az, aw) = a.toList()
        val (bx, by, bz, bw) = b.toList()
        return doubleArrayOf(
            ax * bw + aw * bx + ay * bz - az * by,
            ay * bw + aw * by + az * bx - ax * bz,
            az * bw + aw * bz + ax * by - ay * bx,
            aw * bw - ax * bx - ay * by - az * bz,
        )
    }

    fun fromQuat(q: DoubleArray): DoubleArray {
        val (x, y, z, w) = q.toList()
        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val yx = y * x2; val yy = y * y2; val zx = z * x2; val zy = z * y2; val zz = z * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2
        return doubleArrayOf(1 - yy - zz, yx + wz, zx - wy, 0.0, yx - wz, 1 - xx - zz, zy + wx, 0.0, zx + wy, zy - wx, 1 - xx - yy, 0.0, 0.0, 0.0, 0.0, 1.0)
    }

    // ---- skeletal base (idx1 tail) -------------------------------------------------------------------------------------

    class Bone(val parent: Int, val local: List<DoubleArray>) {
        val rotation = ArrayList<DoubleArray>()
        val translation = ArrayList<DoubleArray>()
        val scaling = ArrayList<DoubleArray>()

        init {
            for (m in local) {
                val inv = invert(m)
                val r = DoubleArray(3)
                r[0] = -asin(inv[6])
                if (abs(cos(r[0])) > 0.005) {
                    r[1] = atan2(inv[2], inv[10])
                    r[2] = atan2(inv[4], inv[5])
                } else {
                    r[1] = if (inv[6] < 0) atan2(inv[1], inv[0]) else -atan2(inv[1], inv[0])
                    r[2] = 0.0
                }
                rotation += r
                translation += doubleArrayOf(m[12], m[13], m[14])
                scaling += doubleArrayOf(len(m[0], m[1], m[2]), len(m[4], m[5], m[6]), len(m[8], m[9], m[10]))
            }
        }

        private fun len(x: Double, y: Double, z: Double) = sqrt(x * x + y * y + z * z)
    }

    /** The skeletal tail of an OSRS framemap (the part [OsrsFxImportTool.convertBase] does not read), or null. */
    fun skeletalBase(framemap: ByteArray): List<Bone>? {
        val c = In(framemap)
        val count = c.u8()
        repeat(count) { c.u8() }
        val sizes = IntArray(count) { c.u8() }
        sizes.forEach { repeat(it) { c.u8() } }
        if (c.remaining < 2) return null
        val boneCount = c.u16()
        if (boneCount == 0) return null
        val poses = c.u8()
        return List(boneCount) {
            val parent = c.s16()
            val locals = List(poses) {
                val m = DoubleArray(16) { c.f32() }
                repeat(3) { c.f32() }
                m
            }
            Bone(parent, locals)
        }
    }

    // ---- curves (Curve.ts / CurveInterp.ts) ----------------------------------------------------------------------------

    class Point(val x: Int, val y: Double, val f2: Double, val f3: Double, val f4: Double, val f5: Double) {
        var next: Point? = null
    }

    class Curve(val startInterp: Int, val endInterp: Int, val bool: Boolean, val points: List<Point>) {
        val startTick = points.first().x
        val endTick = points.last().x
        var pointIndex = 0
        var updated = true
        var interpBool = false
        var v0 = 0.0; var v1 = 0.0; var v2 = 0.0; var v3 = 0.0; var v4 = 0.0; var v5 = 0.0
        var v6 = 0.0; var v7 = 0.0; var v8 = 0.0; var v9 = 0.0
        lateinit var values: DoubleArray

        init {
            for (i in 0 until points.size - 1) points[i].next = points[i + 1]
            values = DoubleArray(endTick - startTick + 1) { interpolate(startTick + it) }
        }

        /** Curve.getValue after load(): outside the range the (zero) min/max values - the OSRS client quirk. */
        fun value(t: Int): Double = if (t < startTick || t > endTick) 0.0 else values[t - startTick]

        private fun indexFor(t: Int): Int {
            val p = pointIndex
            if (p < 0 || points[p].x > t || (points[p].next != null && points[p].next!!.x <= t)) {
                if (t < startTick || t > endTick) return -1
                var lo = 0
                var hi = points.size - 1
                var found = pointIndex
                while (lo <= hi) {
                    val mid = (lo + hi) shr 1
                    if (t < points[mid].x) {
                        if (t > points[mid - 1].x) { found = mid - 1; break }
                        hi = mid - 1
                    } else {
                        if (t <= points[mid].x) { found = mid; break }
                        if (t < points[mid + 1].x) { found = mid; break }
                        lo = mid + 1
                    }
                }
                if (found != pointIndex) {
                    pointIndex = found
                    updated = true
                }
            }
            return pointIndex
        }

        fun interpolate(t: Int): Double {
            if (t < startTick) return if (startInterp == 0) points.first().y else extrapolate(t, true)
            if (t > endTick) return if (endInterp == 0) points.last().y else extrapolate(t, false)
            val idx = indexFor(t)
            if (idx < 0 || idx >= points.size) return 0.0
            val point = points[idx]
            val max = Float.MAX_VALUE.toDouble()
            var flat = false
            var step = false
            if (point.f4 == 0.0 && point.f5 == 0.0) {
                flat = true
            } else if (point.f4 == max && point.f5 == max) {
                step = true
            } else if (point.next == null) {
                flat = true
            } else if (updated) {
                val next = point.next!!
                val x0 = point.x.toDouble(); val y0 = point.y
                val x1 = point.f4 * 0.33333334 + x0; val y1 = point.f5 * 0.33333334 + y0
                val x3 = next.x.toDouble(); val y3 = next.y
                val x2 = x3 - next.f2 * 0.33333334; val y2 = y3 - next.f3 * 0.33333334
                if (bool) {
                    var ya = y1
                    var yb = y2
                    val span = x3 - x0
                    if (span != 0.0) {
                        val v = doubleArrayOf((x1 - x0) / span, (x2 - x0) / span)
                        interpBool = v[0] == 0.33333334 && v[1] == 0.6666667
                        val o0 = v[0]
                        val o1 = v[1]
                        if (v[0] < 0.0) v[0] = 0.0
                        if (v[1] > 1.0) v[1] = 1.0
                        if (v[0] > 1.0 || v[1] < -1.0) clampBezier(v)
                        if (v[0] != o0 && o0 != 0.0) ya = (y1 - y0) * v[0] / o0 + y0
                        if (o1 != v[1] && o1 != 1.0) yb = y3 - (1.0 - v[1]) * (y3 - y2) / (1.0 - o1)
                        v0 = x0
                        v1 = x3
                        var a = v[0]
                        var b = v[1] - v[0]
                        var cc = 1.0 - v[1]
                        var d = b - a
                        v5 = cc - b - d; v4 = d + d + d; v3 = a + a + a; v2 = 0.0
                        a = ya - y0; b = yb - ya; cc = y3 - yb; d = b - a
                        v9 = cc - b - d; v8 = d + d + d; v7 = a + a + a; v6 = y0
                    }
                } else {
                    v0 = x0
                    val dx = x3 - x0
                    val dy = y3 - y0
                    var s0 = 0.0
                    var s1 = 0.0
                    if (x1 - x0 != 0.0) s0 = (y1 - y0) / (x1 - x0)
                    if (x3 - x2 != 0.0) s1 = (y3 - y2) / (x3 - x2)
                    val inv = 1.0 / (dx * dx)
                    val a = s0 * dx
                    val b = s1 * dx
                    v2 = inv * (a + b - dy - dy) / dx
                    v3 = inv * (dy + dy + dy - a - a - b)
                    v4 = s0
                    v5 = y0
                }
                updated = false
            }
            if (flat) return point.y
            if (step) return if (point.x != t && point.next != null) point.next!!.y else point.y
            if (bool) return bezier(t.toDouble())
            val d = t - v0
            return v5 + d * ((d * v2 + v3) * d + v4)
        }

        private fun extrapolate(t: Int, start: Boolean): Double {
            val x0 = points.first().x.toDouble()
            val x1 = points.last().x.toDouble()
            val span = x1 - x0
            if (span == 0.0) return points.first().y
            val f = if (t > x1) (t - x1) / span else (t - x0) / span
            var whole = f.toInt().toDouble()
            var frac = abs(f - whole)
            var local = frac * span
            whole = abs(1.0 + whole)
            val half = whole / 2.0
            frac = half - half.toInt()
            val mode = if (start) startInterp else endInterp
            if (start) {
                when (mode) {
                    4 -> local = if (frac != 0.0) local + x0 else x1 - local
                    2, 3 -> local = x1 - local
                    1 -> {
                        val p = points.first()
                        return if (p.f2 != 0.0) p.y - (x0 - t) * p.f3 / p.f2 else p.y
                    }
                }
            } else {
                when (mode) {
                    4 -> local = if (frac != 0.0) x1 - local else local + x0
                    2, 3 -> local += x0
                    1 -> {
                        val p = points.last()
                        return if (p.f4 != 0.0) p.y + p.f5 * (t - x1) / p.f4 else p.y
                    }
                }
            }
            var out = interpolate(local.toInt())
            val rise = points.last().y - points.first().y
            if (start && mode == 3) out -= rise * whole
            if (!start && mode == 3) out += rise * whole
            return out
        }

        private fun clampBezier(v: DoubleArray) {
            val ulp = 1.1920929e-7
            v[1] = 1.0 - v[1]
            if (v[0] < 0.0) v[0] = 0.0
            if (v[1] < 0.0) v[1] = 0.0
            if (v[0] > 1.0 || v[1] > 1.0) {
                val q = 1.0 + v[0] * (v[0] - 2.0 + v[1]) + (v[1] - 2.0) * v[1]
                if (q + ulp > 0.0) {
                    if (ulp + v[0] < 1.3333334) {
                        val a = v[0] - 2.0
                        val b = v[0] - 1.0
                        val r = sqrt(a * a - 4.0 * b * b)
                        val hi = 0.5 * (r - a)
                        if (v[1] + ulp > hi) {
                            v[1] = hi - ulp
                        } else {
                            val lo = (-a - r) * 0.5
                            if (v[1] < lo + ulp) v[1] = lo + ulp
                        }
                    } else {
                        v[0] = 1.3333334 - ulp
                        v[1] = 0.33333334 - ulp
                    }
                }
            }
            v[1] = 1.0 - v[1]
        }

        /** CurveInterp.method8290: solve the bezier x(s) = t for s (numerically), then y(s). */
        private fun bezier(t: Double): Double {
            val u = if (v0 == t) 0.0 else if (t == v1) 1.0 else (t - v0) / (v1 - v0)
            val s =
                if (interpBool) {
                    u
                } else {
                    // x(s) = v2 + s*(v3 + s*(v4 + s*v5)) = u on [0, 1]: bisection (monotone for valid control points).
                    var lo = 0.0
                    var hi = 1.0
                    repeat(60) {
                        val mid = (lo + hi) / 2
                        val x = v2 + mid * (v3 + mid * (v4 + mid * v5))
                        if (x < u) lo = mid else hi = mid
                    }
                    (lo + hi) / 2
                }
            return s * (v7 + s * (s * v9 + v8)) + v6
        }
    }

    private val CURVE_INDICES = intArrayOf(-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 0, 1, 2, 3, 4, 5, 0)

    class Clip(val baseId: Int, val poseId: Int, val boneCurves: Map<Int, Array<Curve?>>) {
        val lastTick: Int get() = boneCurves.values.flatMap { it.filterNotNull() }.maxOfOrNull { it.endTick } ?: 0
    }

    fun decodeClip(bytes: ByteArray): Clip {
        val c = In(bytes)
        c.u8() // version
        val baseId = c.u16()
        c.u16()
        c.u16()
        val poseId = c.u8()
        val count = c.u16()
        val bones = HashMap<Int, Array<Curve?>>()
        repeat(count) {
            val transformType = c.u8()
            val bone = c.smart2()
            val curveType = c.u8()
            val n = c.u16()
            c.u8() // type
            val startInterp = c.u8().let { if (it > 4) 0 else it }
            val endInterp = c.u8().let { if (it > 4) 0 else it }
            val bool = c.u8() != 0
            val points = List(n) { Point(c.s16(), c.f32(), c.f32(), c.f32(), c.f32(), c.f32()) }
            if (transformType == 1 && n > 0) {
                val slot = CURVE_INDICES.getOrElse(curveType) { -1 }
                if (slot >= 0) bones.getOrPut(bone) { arrayOfNulls(9) }[slot] = Curve(startInterp, endInterp, bool, points)
            }
        }
        return Clip(baseId, poseId, bones)
    }

    // ---- bake ------------------------------------------------------------------------------------------------------------

    /** Model-space animation matrices of every bone at [tick] (SkeletalSeq.updateAnimMatrix + SkeletalBone chains). */
    fun finalMatrices(
        bones: List<Bone>,
        clip: Clip,
        tick: Int,
    ): List<DoubleArray> {
        val pose = clip.poseId
        val anim = bones.mapIndexed { i, bone ->
            val curves = clip.boneCurves[i]
            fun ch(slot: Int, default: Double) = curves?.get(slot)?.value(tick) ?: default
            val r = bone.rotation[pose]
            val s = bone.scaling[pose]
            val t = bone.translation[pose]
            var q = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            q = qmul(axisAngle(0.0, 0.0, 1.0, ch(2, r[2])), q)
            q = qmul(axisAngle(1.0, 0.0, 0.0, ch(0, r[0])), q)
            q = qmul(axisAngle(0.0, 1.0, 0.0, ch(1, r[1])), q)
            var m = mul(fromQuat(q), identity())
            val sm = identity().also { it[0] = ch(6, s[0]); it[5] = ch(7, s[1]); it[10] = ch(8, s[2]) }
            m = mul(sm, m)
            m[12] = ch(3, t[0])
            m[13] = ch(4, t[1])
            m[14] = ch(5, t[2])
            m
        }
        val animModel = arrayOfNulls<DoubleArray>(bones.size)
        fun modelOf(i: Int): DoubleArray = animModel[i] ?: (if (bones[i].parent >= 0) mul(modelOf(bones[i].parent), anim[i]) else anim[i]).also { animModel[i] = it }
        val bindModel = arrayOfNulls<DoubleArray>(bones.size)
        fun bindOf(i: Int): DoubleArray = bindModel[i] ?: (if (bones[i].parent >= 0) mul(bindOf(bones[i].parent), bones[i].local[pose]) else bones[i].local[pose]).also { bindModel[i] = it }
        return bones.indices.map { mul(modelOf(it), invert(bindOf(it))) }
    }

    /** Skinned vertex positions (model units) for one frame; unskinned vertices stay where they are. */
    fun skin(
        model: ModelData,
        matrices: List<DoubleArray>,
    ): Array<IntArray> =
        Array(model.vertexCount) { v ->
            val bones = model.animayaBones?.getOrNull(v)
            val weights = model.animayaWeights?.getOrNull(v)
            if (bones == null || weights == null || bones.isEmpty()) return@Array intArrayOf(model.vertexX[v], model.vertexY[v], model.vertexZ[v])
            val x = model.vertexX[v].toDouble()
            val y = -model.vertexY[v].toDouble()
            val z = -model.vertexZ[v].toDouble()
            var ox = 0.0
            var oy = 0.0
            var oz = 0.0
            for (j in bones.indices) {
                val m = matrices.getOrNull(bones[j]) ?: continue
                val w = weights[j] / 255.0
                ox += w * (m[0] * x + m[4] * y + m[8] * z + m[12])
                oy += w * (m[1] * x + m[5] * y + m[9] * z + m[13])
                oz += w * (m[2] * x + m[6] * y + m[10] * z + m[14])
            }
            intArrayOf(Math.round(ox).toInt(), -Math.round(oy).toInt(), -Math.round(oz).toInt())
        }

    /** OSRS skeletal sequence fields (opcode 13 id, 15 range); null when it is not skeletal. */
    /** [sounds]: opcode 14 frame sounds as (tick, OSRS synth id, loops, radius) - played by the OSRS client on that tick. */
    class SkeletalSeq(val animaya: Int, val start: Int, val end: Int, val sounds: List<IntArray> = emptyList(), val mask: IntArray? = null)

    fun decodeSkeletalSeq(bytes: ByteArray): SkeletalSeq? {
        val c = In(bytes)
        var id = -1
        var start = -1
        var end = -1
        val sounds = ArrayList<IntArray>()
        var mask: IntArray? = null
        while (c.remaining > 0) {
            when (val op = c.u8()) {
                0 -> break
                1 -> {
                    val n = c.u16()
                    repeat(n * 3) { c.u16() }
                }
                2 -> c.u16()
                3 -> repeat(c.u8()) { c.u8() }
                4, 19 -> Unit
                5, 8, 9, 10, 11 -> c.u8()
                6, 7 -> c.u16()
                12 -> repeat(c.u8() * 2) { c.u16() }
                13 -> id = c.i32()
                14 -> repeat(c.u16()) {
                    val tick = c.u16()
                    val sound = c.u16()
                    c.u8() // weight
                    val loops = c.u8()
                    val radius = c.u8()
                    c.u8() // retain
                    sounds += intArrayOf(tick, sound, loops, radius)
                }
                15 -> {
                    start = c.u16()
                    end = c.u16()
                }
                16 -> c.u8()
                // N462: the skeletal bone mask (OSRS SequenceDefinition opcode 17): an action plays on the bones NOT in it, the
                // movement on the bones in it.
                17 -> mask = IntArray(c.u8()) { c.u8() }
                18 -> while (c.u8() != 0) Unit
                else -> error("unknown OSRS sequence opcode $op")
            }
        }
        return if (id >= 0) SkeletalSeq(id, start, end, sounds, mask) else null
    }

    class Baked(
        val model: ByteArray,
        val base: ByteArray,
        val frames: List<ByteArray>,
        val durations: IntArray,
        val vertices: Int,
        val bones: Int,
        val clusters: Int,
        val ticks: IntRange,
        /** Largest and mean distance (667 model units) between the classic replay and the skinned OSRS position. */
        val maxError: Double,
        val meanError: Double,
    )

    /** Groups per cluster in the baked base: origin, scale, rotate, translate. */
    private const val GROUPS_PER_CLUSTER = 4
    const val MAX_CLUSTERS = 255 / GROUPS_PER_CLUSTER

    /** A 667 base stores its group count in one byte. */
    const val MAX_BASE_GROUPS = 255

    /** 3x3 rotation of the 667 classic type-2 transform (JavaModel.I): Z first, then X, then Y. */
    fun classicRotation(
        ax: Double,
        ay: Double,
        az: Double,
    ): Array<DoubleArray> {
        val cx = cos(ax); val sx = sin(ax); val cy = cos(ay); val sy = sin(ay); val cz = cos(az); val sz = sin(az)
        return arrayOf(
            doubleArrayOf(cy * cz - sy * sx * sz, cy * sz + sy * sx * cz, sy * cx),
            doubleArrayOf(-cx * sz, cx * cz, -sx),
            doubleArrayOf(-sy * cz - cy * sx * sz, -sy * sz + cy * sx * cz, cy * cx),
        )
    }

    /** Euler angles (x, y, z) of [r] for [classicRotation]. */
    fun classicEuler(r: Array<DoubleArray>): DoubleArray {
        val ax = asin((-r[1][2]).coerceIn(-1.0, 1.0))
        return if (abs(cos(ax)) > 1e-6) {
            doubleArrayOf(ax, atan2(r[0][2], r[2][2]), atan2(-r[1][0], r[1][1]))
        } else {
            doubleArrayOf(ax, atan2(-r[2][0], r[0][0]), 0.0)
        }
    }

    /**
     * The factor the client multiplies a mesh's vertices by when it loads it (`Mesh.upscale` for version < 13 in NPCType, LocType,
     * SpotAnimationType, ObjType). Frames are applied to the upscaled mesh, so a baked translation must be in those units: taking the
     * file coordinates made every baked translation 4x too small and threw the skeletal pets' parts up to ~2 tiles off.
     */
    fun clientUpscale(mesh: ModelData): Int = if (mesh.version < 13) 4 else 1

    /** Angle -> 667 frame value (the client does `value << 2 & 0x3FFF` into a 16384-entry table). */
    fun angleValue(rad: Double): Int {
        var v = Math.round(rad / (2 * Math.PI) * 4096.0).toInt() % 4096
        if (v >= 2048) v -= 4096
        if (v < -2048) v += 4096
        return v
    }

    private fun ByteArrayOutputStream.smarts(v: Int) {
        if (v in -64..63) write(v + 64) else { val w = v + 49152; write(w ushr 8); write(w and 0xFF) }
    }

    /** Bakes OSRS model [modelId] under skeletal sequence [osrsSeq]; [localBase] is the 667 base id the frames name. */
    fun bake(
        reader: ModernCacheReader,
        modelId: Int,
        osrsSeq: Int,
        localBase: Int,
    ): Baked {
        val seqBytes = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, osrsSeq) ?: error("seq $osrsSeq missing")
        val seq = decodeSkeletalSeq(seqBytes) ?: error("seq $osrsSeq is not skeletal")
        val clip = decodeClip(reader.file(INDEX_ANIMAYA, seq.animaya ushr 16, seq.animaya and 0xFFFF) ?: error("animaya ${seq.animaya} missing"))
        val bones = skeletalBase(reader.file(INDEX_SKELETONS, clip.baseId, 0) ?: error("skeleton ${clip.baseId} missing")) ?: error("skeleton ${clip.baseId} has no bones")
        val raw = ModernModelDecoder.decode(reader.file(7, modelId, 0) ?: error("model $modelId missing"))
        val skinBones = raw.animayaBones ?: error("model $modelId carries no skinning")
        val skinWeights = raw.animayaWeights!!
        val ticks = if (seq.start >= 0 && seq.end > seq.start) seq.start until seq.end else 0..clip.lastTick
        val dropped = ArrayList<String>()
        val labelled = Rev667ModelDecoder.decode(OsrsModelConversion.convert(reader, modelId, dropped))
        check(labelled.vertexCount == raw.vertexCount) { "model $modelId changed vertex count in conversion" }
        // Client units per OSRS unit. The client upscales a version < 13 mesh x4 when it loads it ([ModelData.version]) and applies the
        // frames to that upscaled mesh, so translations and the replay work in the upscaled coordinates (OsrsFxImportTool's translate x4).
        val up = clientUpscale(labelled)
        val ref = (0 until raw.vertexCount).maxByOrNull { abs(raw.vertexX[it]) + abs(raw.vertexY[it]) + abs(raw.vertexZ[it]) }!!
        val k = up * (abs(labelled.vertexX[ref]) + abs(labelled.vertexY[ref]) + abs(labelled.vertexZ[ref])).toDouble() /
            (abs(raw.vertexX[ref]) + abs(raw.vertexY[ref]) + abs(raw.vertexZ[ref])).coerceAtLeast(1)
        // Clusters: vertices with the same skin move with the same blended matrix; too many -> by dominant bone.
        fun signature(v: Int) = skinBones[v].indices.sortedBy { skinBones[v][it] }.joinToString(",") { "${skinBones[v][it]}:${skinWeights[v][it]}" }
        var bySig = (0 until raw.vertexCount).groupBy { signature(it) }
        if (bySig.size > MAX_CLUSTERS) {
            bySig = (0 until raw.vertexCount).groupBy { v -> skinBones[v].indices.maxByOrNull { skinWeights[v][it] }?.let { "${skinBones[v][it]}:255" } ?: "" }
        }
        check(bySig.size <= MAX_CLUSTERS) { "model $modelId needs ${bySig.size} clusters, a 667 base holds $MAX_CLUSTERS" }
        val clusters = bySig.entries.toList()
        val clusterOf = IntArray(raw.vertexCount)
        clusters.forEachIndexed { ci, e -> e.value.forEach { clusterOf[it] = ci } }
        labelled.vertexLabel = IntArray(labelled.vertexCount) { clusterOf[it] }
        val model = Rev667ModelEncoder.encode(labelled)
        // Base: per cluster [origin (no labels), scale, rotate, translate].
        val n = clusters.size * GROUPS_PER_CLUSTER
        val types = IntArray(n) { intArrayOf(0, 3, 2, 1)[it % GROUPS_PER_CLUSTER] }
        val labels = List(n) { g -> if (g % GROUPS_PER_CLUSTER == 0) emptyList() else listOf(g / GROUPS_PER_CLUSTER) }
        val base = ByteArrayOutputStream()
        base.write(n)
        types.forEach { base.write(it) }
        repeat(n) { base.write(0) }
        repeat(n) { base.write(0xFF); base.write(0xFF) }
        labels.forEach { base.write(it.size) }
        labels.forEach { l -> l.forEach { base.write(it) } }
        val baseBytes = base.toByteArray()
        val frames = ArrayList<ByteArray>()
        val durations = ArrayList<Int>()
        var maxErr = 0.0
        var sumErr = 0.0
        var errCount = 0
        var maxMove = 0
        var tick = ticks.first
        while (tick <= ticks.last) {
            val mats = finalMatrices(bones, clip, tick)
            val skinned = skin(raw, mats)
            for (v in 0 until raw.vertexCount) maxMove = maxOf(maxMove, abs(skinned[v][0] - raw.vertexX[v]) + abs(skinned[v][1] - raw.vertexY[v]) + abs(skinned[v][2] - raw.vertexZ[v]))
            val flags = IntArray(n)
            val values = ArrayList<Int>()
            clusters.forEachIndexed { ci, e ->
                val v0 = e.value.first()
                // Blended skeletal matrix of this cluster, then into RS model space: F A F with F = diag(1, -1, -1).
                val a = DoubleArray(16)
                skinBones[v0].indices.forEach { j ->
                    val m = mats.getOrNull(skinBones[v0][j]) ?: return@forEach
                    val w = skinWeights[v0][j] / 255.0
                    for (i in 0..15) a[i] += w * m[i]
                }
                if (skinBones[v0].isEmpty()) { a[0] = 1.0; a[5] = 1.0; a[10] = 1.0; a[15] = 1.0 }
                val f = doubleArrayOf(1.0, -1.0, -1.0)
                val l = Array(3) { r -> DoubleArray(3) { col -> f[r] * a[col * 4 + r] * f[col] } }
                val t = DoubleArray(3) { r -> f[r] * a[12 + r] * k }
                val s = DoubleArray(3) { col -> sqrt(l[0][col] * l[0][col] + l[1][col] * l[1][col] + l[2][col] * l[2][col]).coerceAtLeast(1e-9) }
                val r = Array(3) { row -> DoubleArray(3) { col -> l[row][col] / s[col] } }
                val euler = classicEuler(r)
                val g = ci * GROUPS_PER_CLUSTER
                // origin: pivot (0, 0, 0) - flag set so the group is applied
                flags[g] = 1; values += 0
                val sv = IntArray(3) { Math.round(s[it] * 128).toInt().coerceIn(-16384, 16383) }
                if (sv.any { it != 128 }) { flags[g + 1] = 7; sv.forEach { values += it } }
                val rv = IntArray(3) { angleValue(euler[it]) }
                if (rv.any { it != 0 }) { flags[g + 2] = 7; rv.forEach { values += it } }
                val tv = IntArray(3) { Math.round(t[it]).toInt().coerceIn(-16384, 16383) }
                if (tv.any { it != 0 }) { flags[g + 3] = 7; tv.forEach { values += it } }
                // Replay exactly like the client (scale /128, 16384-step trig, translate) and compare with the skinning.
                val rr = classicRotation(rv[0] * 4 * 2 * Math.PI / 16384, rv[1] * 4 * 2 * Math.PI / 16384, rv[2] * 4 * 2 * Math.PI / 16384)
                e.value.forEach { v ->
                    val p = doubleArrayOf(labelled.vertexX[v] * up * sv[0] / 128.0, labelled.vertexY[v] * up * sv[1] / 128.0, labelled.vertexZ[v] * up * sv[2] / 128.0)
                    val q = DoubleArray(3) { row -> rr[row][0] * p[0] + rr[row][1] * p[1] + rr[row][2] * p[2] + tv[row] }
                    val want = doubleArrayOf(skinned[v][0] * k, skinned[v][1] * k, skinned[v][2] * k)
                    val err = sqrt((0..2).sumOf { (q[it] - want[it]) * (q[it] - want[it]) })
                    maxErr = maxOf(maxErr, err)
                    sumErr += err
                    errCount++
                }
            }
            val out = ByteArrayOutputStream()
            out.write(1)
            out.write(localBase ushr 8)
            out.write(localBase and 0xFF)
            out.write(n)
            flags.forEach { out.write(it) }
            // values were appended group by group in flag order (x, y, z per set flag)
            values.forEach { out.smarts(it) }
            val bytes = out.toByteArray()
            OsrsFxImportTool.check667Frame(bytes, baseBytes)
            frames += bytes
            durations += STEP
            tick += STEP
        }
        clip.boneCurves.entries.sortedBy { it.key }.take(4).forEach { (bone, cs) ->
            println("  bone $bone parent=${bones.getOrNull(bone)?.parent} bindRot=${bones.getOrNull(bone)?.rotation?.get(clip.poseId)?.toList()} bindT=${bones.getOrNull(bone)?.translation?.get(clip.poseId)?.toList()} " +
                cs.withIndex().filter { it.value != null }.joinToString(" ") { (slot, cv) -> "s$slot[${cv!!.startTick}..${cv.endTick}]=${"%.3f".format(cv.value(0))}/${"%.3f".format(cv.value(30))}/${"%.3f".format(cv.value(60))}" })
        }
        val m30 = finalMatrices(bones, clip, 30)
        println("  final[0]@30=${m30.firstOrNull()?.toList()?.map { "%.3f".format(it) }} skin0=${skinBones.getOrNull(0)?.toList()}/${skinWeights.getOrNull(0)?.toList()}")
        println("BAKE model $modelId seq $osrsSeq animaya ${seq.animaya} curves=${clip.boneCurves.size} bones=${bones.size} maxVertexMove=$maxMove (osrs units) k=$k")
        return Baked(model, baseBytes, frames, durations.toIntArray(), raw.vertexCount, bones.size, clusters.size, ticks, maxErr, if (errCount > 0) sumErr / errCount else 0.0)
    }
    /** 667 frame values of one cluster: scale (x128), rotation (4096 steps), translation - each clamped to the frame's value range. */
    private fun quantize(
        r: Array<DoubleArray>,
        s: DoubleArray,
        t: DoubleArray,
    ): Triple<IntArray, IntArray, IntArray> {
        val euler = classicEuler(r)
        return Triple(
            IntArray(3) { Math.round(s[it] * 128).toInt().coerceIn(-16384, 16383) },
            IntArray(3) { angleValue(euler[it]) },
            IntArray(3) { Math.round(t[it]).toInt().coerceIn(-16384, 16383) },
        )
    }

    /** Summed squared distance between the client's replay of [values] (scale, then rotate, then translate) and [dst]. */
    private fun replayError(
        values: Triple<IntArray, IntArray, IntArray>,
        src: List<DoubleArray>,
        dst: List<DoubleArray>,
    ): Double {
        val (sv, rv, tv) = values
        val rr = classicRotation(rv[0] * 4 * 2 * Math.PI / 16384, rv[1] * 4 * 2 * Math.PI / 16384, rv[2] * 4 * 2 * Math.PI / 16384)
        var sum = 0.0
        src.indices.forEach { i ->
            val v = src[i]
            val q0 = doubleArrayOf(v[0] * sv[0] / 128.0, v[1] * sv[1] / 128.0, v[2] * sv[2] / 128.0)
            for (row in 0..2) {
                val d = rr[row][0] * q0[0] + rr[row][1] * q0[1] + rr[row][2] * q0[2] + tv[row] - dst[i][row]
                sum += d * d
            }
        }
        return sum
    }

    private fun det3(m: Array<DoubleArray>) =
        m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])

    /** Rotation factor of [m] (Higham's Newton iteration X <- (X + X^-T) / 2); null when [m] is singular or a reflection. */
    private fun polarRotation(m: Array<DoubleArray>): Array<DoubleArray>? {
        var x = Array(3) { m[it].copyOf() }
        repeat(30) {
            val d = det3(x)
            if (abs(d) < 1e-12) return null
            // inverse transpose = cofactor matrix / det
            val cof = Array(3) { r -> DoubleArray(3) { c ->
                val r1 = (r + 1) % 3; val r2 = (r + 2) % 3; val c1 = (c + 1) % 3; val c2 = (c + 2) % 3
                (x[r1][c1] * x[r2][c2] - x[r1][c2] * x[r2][c1]) / d
            } }
            x = Array(3) { r -> DoubleArray(3) { c -> 0.5 * (x[r][c] + cof[r][c]) } }
        }
        return if (det3(x) > 0.5) x else null
    }

    /**
     * Least-squares rotation R, axis scale S (when [initialScale] is given; else 1) and translation t with R S v + t ~ dst, by alternating
     * an orthogonal Procrustes step for R with a closed-form per-axis step for S. Null for a degenerate cluster (fewer than 3 independent
     * vertices), where the representative's matrix stays in use.
     */
    private fun fitRotationScale(
        src: List<DoubleArray>,
        dst: List<DoubleArray>,
        initialRotation: Array<DoubleArray>,
        initialScale: DoubleArray?,
    ): Triple<Array<DoubleArray>, DoubleArray, DoubleArray>? {
        if (src.size < 3) return null
        val vb = DoubleArray(3) { a -> src.sumOf { it[a] } / src.size }
        val yb = DoubleArray(3) { a -> dst.sumOf { it[a] } / dst.size }
        val vc = src.map { v -> DoubleArray(3) { v[it] - vb[it] } }
        val yc = dst.map { y -> DoubleArray(3) { y[it] - yb[it] } }
        var r = initialRotation
        val s = initialScale?.copyOf() ?: doubleArrayOf(1.0, 1.0, 1.0)
        repeat(8) {
            val mm = Array(3) { DoubleArray(3) }
            vc.indices.forEach { i -> for (a in 0..2) for (b in 0..2) mm[a][b] += yc[i][a] * s[b] * vc[i][b] }
            r = polarRotation(mm) ?: return null
            if (initialScale != null) {
                for (b in 0..2) {
                    var num = 0.0
                    var den = 0.0
                    vc.indices.forEach { i ->
                        val ry = r[0][b] * yc[i][0] + r[1][b] * yc[i][1] + r[2][b] * yc[i][2]
                        num += ry * vc[i][b]
                        den += vc[i][b] * vc[i][b]
                    }
                    if (den > 1e-6) s[b] = num / den
                }
            }
        }
        val t = DoubleArray(3) { row -> yb[row] - (0..2).sumOf { c -> r[row][c] * s[c] * vb[c] } }
        return Triple(r, s, t)
    }

    /** [bakeSet] result: one labelled 667 model per input model, one shared base, and per sequence its frames and durations. */
    class BakedSet(
        val models: List<ByteArray>,
        val base: ByteArray,
        val frames: List<List<ByteArray>>,
        val durations: List<IntArray>,
        val vertices: Int,
        val signatureClusters: Int,
        val clusters: Int,
        /** Largest and mean distance (667 model units) between the classic replay and the skinned OSRS position, over every frame. */
        val maxError: Double,
        val meanError: Double,
        /** Vertex-frames whose replay is more than 32 units (1/16 tile, before the npc scale) off the skinned position. */
        val farOff: Int = 0,
        val vertexFrames: Int = 0,
        /** Per sequence the OSRS skeletal tick each baked frame shows (frame `i` of sequence `s` = tick `ticks[s][i]`). */
        val ticks: List<List<Int>> = emptyList(),
    )

    /**
     * [bake] for an npc (OSRS pets, owner 2026-09-30): every body model and every movement sequence of the npc share ONE base, because a
     * 667 BAS plays its stand / walk sequences on the same model. The base holds one shared origin (the 2011scape-client `JavaModel` type 0
     * with no labels sets the origin to its offsets and keeps it for every later rotate / scale of the frame), then per cluster only the
     * groups its motion needs: rotate and translate, plus scale when it scales on some frame - up to 127 clusters instead of [bake]'s 63.
     *
     * Clusters start as the exact skin signatures over all models together (vertices that blend the same bones with the same weights move
     * identically). While the base would exceed [MAX_BASE_GROUPS], the pair whose merge moves the vertices least is merged, one pair at a
     * time: the cost of moving cluster `a` onto cluster `b`'s motion is the summed squared distance, over every baked frame of every sequence
     * and every vertex of `a`, between `a`'s own skinned position and the position `b`'s blended matrix gives it. Each merged cluster then
     * takes the member motion that fits all its vertices best. The remaining error is measured on the classic replay and reported.
     */
    fun bakeSet(
        reader: ModernCacheReader,
        modelIds: List<Int>,
        osrsSeqs: List<Int>,
        localBase: Int,
    ): BakedSet {
        require(modelIds.isNotEmpty() && osrsSeqs.isNotEmpty())
        val seqs = osrsSeqs.map { id ->
            decodeSkeletalSeq(reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, id) ?: error("seq $id missing"))
                ?: error("seq $id is not skeletal")
        }
        val clips = seqs.map { s -> decodeClip(reader.file(INDEX_ANIMAYA, s.animaya ushr 16, s.animaya and 0xFFFF) ?: error("animaya ${s.animaya} missing")) }
        val skeleton = clips.map { it.baseId }.distinct().singleOrNull() ?: error("sequences $osrsSeqs use different skeletons ${clips.map { it.baseId }}")
        val bones = skeletalBase(reader.file(INDEX_SKELETONS, skeleton, 0) ?: error("skeleton $skeleton missing")) ?: error("skeleton $skeleton has no bones")
        val raws = modelIds.map { m -> ModernModelDecoder.decode(reader.file(7, m, 0) ?: error("model $m missing"), flattenTextures = true) }
        val dropped = ArrayList<String>()
        val labelled = modelIds.map { m -> Rev667ModelDecoder.decode(OsrsModelConversion.convert(reader, m, dropped)) }
        raws.indices.forEach { i -> check(labelled[i].vertexCount == raws[i].vertexCount) { "model ${modelIds[i]} changed vertex count in conversion" } }

        // Every vertex of every model: (model index, vertex index).
        val verts = raws.indices.flatMap { mi -> (0 until raws[mi].vertexCount).map { mi to it } }
        fun bonesOf(p: Pair<Int, Int>) = raws[p.first].animayaBones?.getOrNull(p.second) ?: IntArray(0)
        fun weightsOf(p: Pair<Int, Int>) = raws[p.first].animayaWeights?.getOrNull(p.second) ?: IntArray(0)

        // Client units per OSRS unit, as in [bake]: every model is upscaled by the client on load, so positions, translations and errors
        // are in the upscaled coordinates the frames are applied to.
        val ups = labelled.map { clientUpscale(it) }
        check(ups.distinct().size == 1) { "models $modelIds mix mesh versions ${labelled.map { it.version }}" }
        val refs = verts.maxByOrNull { (mi, v) -> abs(raws[mi].vertexX[v]) + abs(raws[mi].vertexY[v]) + abs(raws[mi].vertexZ[v]) }!!
        val k = ups[0] * (abs(labelled[refs.first].vertexX[refs.second]) + abs(labelled[refs.first].vertexY[refs.second]) + abs(labelled[refs.first].vertexZ[refs.second])).toDouble() /
            (abs(raws[refs.first].vertexX[refs.second]) + abs(raws[refs.first].vertexY[refs.second]) + abs(raws[refs.first].vertexZ[refs.second])).coerceAtLeast(1)

        // Frame ticks per sequence (OSRS: one skeletal tick per client cycle; baked every STEP cycles).
        val tickLists = seqs.indices.map { i ->
            val s = seqs[i]
            val range = if (s.start >= 0 && s.end > s.start) s.start until s.end else 0..clips[i].lastTick
            range.step(STEP).toList()
        }
        val frameMats = seqs.indices.flatMap { i -> tickLists[i].map { t -> finalMatrices(bones, clips[i], t) } }

        // Blended RS-space matrix of a skin (bones + weights) on one frame: F A F with F = diag(1, -1, -1), translation in 667 units.
        fun blended(b: IntArray, w: IntArray, mats: List<DoubleArray>): DoubleArray {
            val a = DoubleArray(16)
            b.indices.forEach { j ->
                val m = mats.getOrNull(b[j]) ?: return@forEach
                val wt = w[j] / 255.0
                for (i in 0..15) a[i] += wt * m[i]
            }
            if (b.isEmpty()) { a[0] = 1.0; a[5] = 1.0; a[10] = 1.0; a[15] = 1.0 }
            val f = doubleArrayOf(1.0, -1.0, -1.0)
            // row-major 3x4: [l | t]
            return DoubleArray(12) { idx ->
                val r = idx / 4
                val c = idx % 4
                if (c < 3) f[r] * a[c * 4 + r] * f[c] else f[r] * a[12 + r] * k
            }
        }

        // Exact clusters: skin signatures.
        fun sig(p: Pair<Int, Int>): String {
            val b = bonesOf(p)
            val w = weightsOf(p)
            return b.indices.sortedBy { b[it] }.joinToString(",") { "${b[it]}:${w[it]}" }
        }
        val groupsBySig = verts.groupBy { sig(it) }.values.map { it.toHashSet() }
        val groups = groupsBySig.map { it.toMutableList() }.toMutableList()
        val signatureClusters = groups.size
        // Motion of each cluster: its representative's blended matrix per frame.
        val motion = groups.map { g -> frameMats.map { mats -> blended(bonesOf(g[0]), weightsOf(g[0]), mats) } }.toMutableList()
        // Rest position of a vertex as the client holds it (upscaled), in the labelled model's space.
        fun pos(p: Pair<Int, Int>): DoubleArray {
            val m = labelled[p.first]
            val u = ups[p.first].toDouble()
            return doubleArrayOf(m.vertexX[p.second] * u, m.vertexY[p.second] * u, m.vertexZ[p.second] * u)
        }
        fun apply(m: DoubleArray, p: DoubleArray) = DoubleArray(3) { r -> m[r * 4] * p[0] + m[r * 4 + 1] * p[1] + m[r * 4 + 2] * p[2] + m[r * 4 + 3] }
        fun cost(a: Int, b: Int): Double {
            var sum = 0.0
            for (p in groups[a]) {
                val v = pos(p)
                for (f in frameMats.indices) {
                    val x = apply(motion[a][f], v)
                    val y = apply(motion[b][f], v)
                    // Squared: a merge that throws a few vertices far off must cost more than one that nudges many a little.
                    sum += (x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2])
                }
            }
            return sum
        }
        val originalMotion = motion.toList()
        val members = groups.indices.map { mutableListOf(it) }.toMutableList()
        // Groups a cluster costs in the base: rotate + translate, plus scale only when its motion scales on some frame.
        fun scales(mo: List<DoubleArray>) =
            mo.any { m -> (0..2).any { c -> Math.round(sqrt(m[c] * m[c] + m[4 + c] * m[4 + c] + m[8 + c] * m[8 + c]) * 128).toInt() != 128 } }
        fun groupCost(mo: List<DoubleArray>) = if (scales(mo)) 3 else 2
        var budget = 1 + motion.sumOf { groupCost(it) }
        if (budget > MAX_BASE_GROUPS) {
            val n0 = groups.size
            val costs = Array(n0) { a -> DoubleArray(n0) { b -> if (a == b) Double.MAX_VALUE else cost(a, b) } }
            val alive = BooleanArray(n0) { true }
            while (budget > MAX_BASE_GROUPS) {
                var ba = -1
                var bb = -1
                var best = Double.MAX_VALUE
                for (a in 0 until n0) if (alive[a]) for (b in 0 until n0) if (alive[b] && costs[a][b] < best) { best = costs[a][b]; ba = a; bb = b }
                groups[bb].addAll(groups[ba])
                groups[ba].clear()
                members[bb].addAll(members[ba])
                alive[ba] = false
                budget -= groupCost(motion[ba])
                // b's own vertices grew: the cost of moving b elsewhere changes; moving others onto b does not.
                for (x in 0 until n0) if (alive[x] && x != bb) costs[bb][x] = cost(bb, x)
            }
            val keep = (0 until n0).filter { alive[it] }
            val g2 = keep.map { groups[it] }
            val m2 = keep.map { motion[it] }
            val mem2 = keep.map { members[it] }
            groups.clear(); groups.addAll(g2)
            motion.clear(); motion.addAll(m2)
            members.clear(); members.addAll(mem2)
            // A merged cluster moves with whichever of its members' own motions fits all its vertices best, as long as the base still fits.
            groups.indices.forEach { ci ->
                if (members[ci].size > 1) {
                    val current = groupCost(motion[ci])
                    val best =
                        members[ci].map { originalMotion[it] }
                            .filter { budget - current + groupCost(it) <= MAX_BASE_GROUPS }
                            .minByOrNull { cand ->
                                var sum = 0.0
                                for (p in groups[ci]) {
                                    val v = pos(p)
                                    val own = originalMotion[members[ci].first { m -> p in groupsBySig[m] }]
                                    for (fi in frameMats.indices) {
                                        val x = apply(cand[fi], v)
                                        val y = apply(own[fi], v)
                                        sum += (x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2])
                                    }
                                }
                                sum
                            }
                    if (best != null) {
                        budget += groupCost(best) - current
                        motion[ci] = best
                    }
                }
            }
        }
        check(budget <= MAX_BASE_GROUPS) { "base needs $budget groups" }

        val clusterOf = HashMap<Pair<Int, Int>, Int>()
        groups.forEachIndexed { ci, g -> g.forEach { clusterOf[it] = ci } }
        labelled.forEachIndexed { mi, m -> m.vertexLabel = IntArray(m.vertexCount) { v -> clusterOf.getValue(mi to v) } }
        val models = labelled.map { Rev667ModelEncoder.encode(it) }

        // Base: group 0 = the one shared origin (offsets 0, 0, 0; no labels), then per cluster [scale if it scales], rotate, translate,
        // each labelled with the cluster.
        val typeList = ArrayList<Int>()
        val labels = ArrayList<List<Int>>()
        typeList += 0
        labels += emptyList<Int>()
        val scaleGroup = IntArray(groups.size) { -1 }
        val rotateGroup = IntArray(groups.size)
        val translateGroup = IntArray(groups.size)
        groups.indices.forEach { ci ->
            if (scales(motion[ci])) {
                scaleGroup[ci] = typeList.size
                typeList += 3
                labels += listOf(ci)
            }
            rotateGroup[ci] = typeList.size
            typeList += 2
            labels += listOf(ci)
            translateGroup[ci] = typeList.size
            typeList += 1
            labels += listOf(ci)
        }
        val n = typeList.size
        check(n == budget && n <= MAX_BASE_GROUPS)
        val types = typeList.toIntArray()
        val base = ByteArrayOutputStream()
        base.write(n)
        types.forEach { base.write(it) }
        repeat(n) { base.write(0) }
        repeat(n) { base.write(0xFF); base.write(0xFF) }
        labels.forEach { base.write(it.size) }
        labels.forEach { l -> l.forEach { base.write(it) } }
        val baseBytes = base.toByteArray()

        var maxErr = 0.0
        var sumErr = 0.0
        var errCount = 0
        var farOff = 0
        val allFrames = ArrayList<List<ByteArray>>()
        var f = 0
        for (si in seqs.indices) {
            val out = ArrayList<ByteArray>()
            repeat(tickLists[si].size) {
                val mats = frameMats[f]
                val skinnedByModel = raws.map { skin(it, mats) }
                val flags = IntArray(n)
                val groupValues = arrayOfNulls<IntArray>(n)
                flags[0] = 1
                groupValues[0] = intArrayOf(0)
                groups.forEachIndexed { ci, g ->
                    val m = motion[ci][f]
                    val l = Array(3) { r -> DoubleArray(3) { c -> m[r * 4 + c] } }
                    val t = DoubleArray(3) { m[it * 4 + 3] }
                    val s = DoubleArray(3) { c -> sqrt(l[0][c] * l[0][c] + l[1][c] * l[1][c] + l[2][c] * l[2][c]).coerceAtLeast(1e-9) }
                    val r = Array(3) { row -> DoubleArray(3) { c -> l[row][c] / s[c] } }
                    val src = g.map { pos(it) }
                    val dst = g.map { p -> skinnedByModel[p.first][p.second].let { sk -> doubleArrayOf(sk[0] * k, sk[1] * k, sk[2] * k) } }
                    val allowScale = scaleGroup[ci] >= 0
                    // Two candidates: the representative's blended matrix decomposed, and a least-squares fit to the cluster's own skinned
                    // positions (blended skins carry shear the classic scale-then-rotate cannot hold). The one that replays closer wins.
                    // The blend makes r slightly non-orthogonal; Euler angles of such a matrix are unstable near a 90 degree x rotation, so the
                    // nearest rotation (its polar factor) is what gets decomposed.
                    val decomposed = quantize(polarRotation(r) ?: r, if (allowScale) s else doubleArrayOf(1.0, 1.0, 1.0), t)
                    val fitted = fitRotationScale(src, dst, r, if (allowScale) s else null)?.let { (fr, fs, ft) -> quantize(fr, fs, ft) }
                    val chosen = listOfNotNull(decomposed, fitted).minByOrNull { replayError(it, src, dst) }!!
                    val (sv, rv, tv) = chosen
                    if (allowScale && sv.any { it != 128 }) {
                        flags[scaleGroup[ci]] = 7
                        groupValues[scaleGroup[ci]] = sv
                    }
                    if (rv.any { it != 0 }) {
                        flags[rotateGroup[ci]] = 7
                        groupValues[rotateGroup[ci]] = rv
                    }
                    if (tv.any { it != 0 }) {
                        flags[translateGroup[ci]] = 7
                        groupValues[translateGroup[ci]] = tv
                    }
                    val rr = classicRotation(rv[0] * 4 * 2 * Math.PI / 16384, rv[1] * 4 * 2 * Math.PI / 16384, rv[2] * 4 * 2 * Math.PI / 16384)
                    g.forEach { p ->
                        val v = pos(p)
                        val q0 = doubleArrayOf(v[0] * sv[0] / 128.0, v[1] * sv[1] / 128.0, v[2] * sv[2] / 128.0)
                        val q = DoubleArray(3) { row -> rr[row][0] * q0[0] + rr[row][1] * q0[1] + rr[row][2] * q0[2] + tv[row] }
                        val sk = skinnedByModel[p.first][p.second]
                        val want = doubleArrayOf(sk[0] * k, sk[1] * k, sk[2] * k)
                        val err = sqrt((0..2).sumOf { (q[it] - want[it]) * (q[it] - want[it]) })
                        maxErr = maxOf(maxErr, err)
                        if (err > 32.0) farOff++
                        sumErr += err
                        errCount++
                    }
                }
                val bytes = ByteArrayOutputStream()
                bytes.write(1)
                bytes.write(localBase ushr 8)
                bytes.write(localBase and 0xFF)
                bytes.write(n)
                flags.forEach { bytes.write(it) }
                // values group by group in base order (x, y, z per set flag)
                groupValues.forEach { gv -> gv?.forEach { bytes.smarts(it) } }
                val frame = bytes.toByteArray()
                OsrsFxImportTool.check667Frame(frame, baseBytes)
                out += frame
                f++
            }
            allFrames += out
        }
        return BakedSet(
            models, baseBytes, allFrames, tickLists.map { IntArray(it.size) { STEP } }, verts.size, signatureClusters, groups.size,
            maxErr, if (errCount > 0) sumErr / errCount else 0.0, farOff, errCount, tickLists,
        )
    }

    // ---- main -------------------------------------------------------------------------------------------------------------

    /**
     * OSRS breach spotanim -> local spotanim of the earlier `spots` runs (journals tx-20260927: "osrs spotanim 2519 -> 3173 (baked)" ...).
     */
    val BAKED_BREACH_SPOTS: Map<Int, Int> = mapOf(2519 to 3173, 2553 to 3174, 2554 to 3175, 2556 to 3176)

    /**
     * Re-bakes the already imported breach locs and spotanims into the ids they already have (ROOT_CAUSES #103: their frames were baked in
     * file units). The loc / spotanim / sequence definitions stay byte-identical - the server and DeadmanBreachTests name those ids - and only
     * the frames (and, should they differ, the base and the labelled model) are rewritten. Refuses when the frame count or layout changed.
     */
    private fun rebakeInPlace(
        reader: ModernCacheReader,
        locs: Map<Int, ByteArray>,
        ids: Map<String, Int>,
        apply: Boolean,
    ) {
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            fun put(index: Int, g: Int, f: Int, bytes: ByteArray, label: String) {
                val current = library.data(index, g, f)
                if (current != null && current.contentEquals(bytes)) return
                mutations += CacheMutation(index, g, f, bytes, label, expectedCurrentSha1 = current?.let { CacheItemProbeTool.sha1(it) })
            }

            /** Rewrites the frames of local sequence [seqId] with the bake of [osrsModel] under [osrsSeq]. */
            fun rebake(label: String, osrsModel: Int, osrsSeq: Int, seqId: Int, localModel: Int, modelWritable: Boolean) {
                val seqBytes = library.data(OsrsFxImportTool.INDEX_SEQ, seqId ushr 7, seqId and 0x7F) ?: error("$label: seq $seqId missing")
                val frames = OsrsFxImportTool.decode667SeqFrames(seqBytes)
                val frameset = frames.first() ushr 16
                check(frames.withIndex().all { (i, f) -> f == (frameset shl 16) or i }) { "$label: seq $seqId frames are not frameset $frameset 0..n" }
                val first = library.data(OsrsFxImportTool.INDEX_FRAMES, frameset, 0) ?: error("$label: frame $frameset:0 missing")
                val base = ((first[1].toInt() and 0xFF) shl 8) or (first[2].toInt() and 0xFF)
                val baked = bake(reader, osrsModel, osrsSeq, base)
                check(baked.frames.size == frames.size) { "$label: ${baked.frames.size} baked frames, seq $seqId has ${frames.size}" }
                check(OsrsFxImportTool.encode667Seq(OsrsFxImportTool.Seq(frameDurations = baked.durations, frames = frames)).contentEquals(seqBytes)) {
                    "$label: seq $seqId would change (durations)"
                }
                if (modelWritable) {
                    put(7, localModel, 0, baked.model, "rebake: $label model $osrsModel -> $localModel")
                } else {
                    check(baked.model.contentEquals(ModelArchive.read(library, localModel)!!)) { "$label: model $localModel is not the labelled loc model" }
                }
                put(OsrsFxImportTool.INDEX_BASES, base, 0, baked.base, "rebake: $label base $base")
                baked.frames.forEachIndexed { i, f -> put(OsrsFxImportTool.INDEX_FRAMES, frameset, i, f, "rebake: $label frame $frameset:$i (client units)") }
                println("PLAN rebake $label osrs model $osrsModel seq $osrsSeq -> seq $seqId frameset $frameset base $base frames=${frames.size} error max=${"%.1f".format(baked.maxError)} mean=${"%.2f".format(baked.meanError)}")
            }

            for (loc in OsrsLocImportTool.BATCHES.getValue("deadman-breach")) {
                val def = ModernObjectDef.decode(loc, locs.getValue(loc))
                val localLoc = ids["osrs_loc_loc:$loc"] ?: error("loc $loc not imported")
                val osrsModel = def.models.single()
                val localModel = ids["osrs_loc_model:$osrsModel"] ?: error("model $osrsModel not imported")
                val locBytes = library.data(FeroxImportTool.LOC_INDEX, localLoc ushr 8, localLoc and 0xFF) ?: error("loc $localLoc missing")
                val seqId = Rev667LocType.decode(localLoc, locBytes).animation
                check(seqId >= 0) { "loc $localLoc has no animation" }
                rebake("loc $loc", osrsModel, def.animationId, seqId, localModel, modelWritable = true)
            }
            for ((spotId, localSpot) in BAKED_BREACH_SPOTS) {
                val spot = OsrsFxImportTool.decodeOsrsSpot(reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SPOTANIM, spotId)!!)
                val localModel = ids["osrs_loc_model:${spot.model}"] ?: error("model ${spot.model} not imported")
                val (model, seqId) = AnimCoverage.spotanim(library, localSpot) ?: error("spotanim $localSpot missing")
                check(model == localModel) { "spotanim $localSpot shows model $model, not $localModel" }
                rebake("spotanim $spotId", spot.model, spot.seq, seqId, localModel, modelWritable = false)
            }
        } finally {
            library.close()
        }
        val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
        val plan = tx.preflight()
        val blocking = tx.blockingErrors(plan)
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        blocking.forEach { println("  BLOCKING: $it") }
        check(blocking.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) return println("DRY_RUN")
        val result = tx.apply(plan)
        val problems = tx.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${tx.rollback()}")
            error("transaction ${tx.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val ids = OsrsFxImportTool.existingFx(assetMap)
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val locs = reader.files(ModernCacheReader.INDEX_CONFIG, 6)
            if (args.getOrNull(0) == "probe") {
                for (loc in OsrsLocImportTool.BATCHES.getValue("deadman-breach")) {
                    val def = ModernObjectDef.decode(loc, locs.getValue(loc))
                    val seq = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, def.animationId)?.let { decodeSkeletalSeq(it) }
                    def.models.forEach { m ->
                        val md = ModernModelDecoder.decode(reader.file(7, m, 0)!!)
                        println("LOC $loc '${def.name}' model $m vertices=${md.vertexCount} faces=${md.faceCount} skinned=${md.animayaBones != null} anim=${def.animationId} animaya=${seq?.animaya} range=${seq?.start}..${seq?.end} sounds=${seq?.sounds?.map { it.toList() }}")
                    }
                }
                for (spot in listOf(2519, 2522, 2553, 2554, 2555, 2556, 2557, 2558)) {
                    val s = OsrsFxImportTool.decodeOsrsSpot(reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SPOTANIM, spot) ?: continue)
                    val md = if (s.model >= 0) ModernModelDecoder.decode(reader.file(7, s.model, 0)!!) else null
                    val seq = if (s.seq >= 0) reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, s.seq)?.let { decodeSkeletalSeq(it) } else null
                    println("SPOT $spot model ${s.model} vertices=${md?.vertexCount} skinned=${md?.animayaBones != null} seq=${s.seq} animaya=${seq?.animaya} range=${seq?.start}..${seq?.end} sounds=${seq?.sounds?.map { it.toList() }}")
                }
                return
            }
            if (args.getOrNull(0) == "spots") {
                // VFX_DM_BREACH_SPAWN 2519 / VFX_DM_BREACH_DESPAWN 2553 (gameval SpotanimID, OsrsFxImportTool deadman-breach note): the
                // same Breach model as the loc, so the loc's labelled model (one label per skin cluster) is reused as it is.
                val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
                val mutations = ArrayList<CacheMutation>()
                try {
                    fun next(index: Int): Int = (library.index(index).archiveIds().maxOrNull() ?: -1) + 1
                    val seqGroup = library.index(OsrsFxImportTool.INDEX_SEQ).archiveIds().maxOrNull()!!
                    var nextSeq = (seqGroup shl 7) + (library.index(OsrsFxImportTool.INDEX_SEQ).archive(seqGroup)!!.fileIds().maxOrNull() ?: -1) + 1
                    val spotGroup = library.index(OsrsFxImportTool.INDEX_SPOTANIM).archiveIds().maxOrNull()!!
                    var nextSpot = (spotGroup shl 8) + (library.index(OsrsFxImportTool.INDEX_SPOTANIM).archive(spotGroup)!!.fileIds().maxOrNull() ?: -1) + 1
                    var nextFrameset = next(OsrsFxImportTool.INDEX_FRAMES)
                    var nextBase = next(OsrsFxImportTool.INDEX_BASES)
                    fun put(index: Int, g: Int, f: Int, bytes: ByteArray, label: String) {
                        val current = library.data(index, g, f)?.let { CacheItemProbeTool.sha1(it) }
                        mutations += CacheMutation(index, g, f, bytes, label, expectedCurrentSha1 = current)
                    }
                    // Default: the breach pair; later runs name the ids (RuneLite gameval SpotanimID VFX_DM_BOSS_SPAWN 2554 /
                    // VFX_DM_BOSS_DESPAWN 2556 on the Boss Spawn model).
                    val spotIds = args.drop(1).filter { !it.startsWith("--") }.map { it.toInt() }.ifEmpty { listOf(2519, 2553) }
                    for (spotId in spotIds) {
                        val spot = OsrsFxImportTool.decodeOsrsSpot(reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SPOTANIM, spotId)!!)
                        val localModel = ids["osrs_loc_model:${spot.model}"] ?: error("model ${spot.model} not imported")
                        val base = nextBase++
                        val baked = bake(reader, spot.model, spot.seq, base)
                        check(baked.model.contentEquals(ModelArchive.read(library, localModel)!!)) { "model $localModel is not the labelled loc model; run locs first" }
                        val frameset = nextFrameset++
                        val seqId = nextSeq++
                        val local = nextSpot++
                        put(OsrsFxImportTool.INDEX_BASES, base, 0, baked.base, "skeletal bake base for spotanim $spotId")
                        baked.frames.forEachIndexed { i, f -> put(OsrsFxImportTool.INDEX_FRAMES, frameset, i, f, "skeletal bake frame $i of spotanim $spotId") }
                        val seqBytes = OsrsFxImportTool.encode667Seq(OsrsFxImportTool.Seq(frameDurations = baked.durations, frames = IntArray(baked.frames.size) { (frameset shl 16) or it }))
                        put(OsrsFxImportTool.INDEX_SEQ, seqId ushr 7, seqId and 0x7F, seqBytes, "skeletal bake seq of spotanim $spotId (osrs ${spot.seq})")
                        put(OsrsFxImportTool.INDEX_SPOTANIM, local ushr 8, local and 0xFF, OsrsFxImportTool.encode667Spot(spot, localModel, seqId), "osrs spotanim $spotId -> $local (baked)")
                        println("PLAN spotanim $spotId -> $local model ${spot.model}->$localModel seq ${spot.seq}->$seqId frames=${baked.frames.size} error max=${"%.1f".format(baked.maxError)} mean=${"%.2f".format(baked.meanError)}")
                    }
                } finally {
                    library.close()
                }
                val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
                val plan = tx.preflight()
                val blocking = tx.blockingErrors(plan)
                println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
                blocking.forEach { println("  BLOCKING: $it") }
                check(blocking.isEmpty()) { "preflight blocked; nothing written" }
                if (!apply) return println("DRY_RUN")
                val result = tx.apply(plan)
                val problems = tx.verify()
                if (problems.isNotEmpty()) {
                    problems.forEach { println("  VERIFY_FAILURE: $it") }
                    println("ROLLED_BACK ${tx.rollback()}")
                    error("transaction ${tx.id} failed verification and was rolled back")
                }
                println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
                return
            }
            if (args.getOrNull(0) == "rebake") return rebakeInPlace(reader, locs, ids, apply)
            check(args.getOrNull(0) == "locs") { "Usage: probe | locs [--apply] | spots [--apply] | rebake [--apply]" }
            val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
            val mutations = ArrayList<CacheMutation>()
            try {
                fun next(index: Int): Int = (library.index(index).archiveIds().maxOrNull() ?: -1) + 1
                val group = library.index(OsrsFxImportTool.INDEX_SEQ).archiveIds().maxOrNull()!!
                var nextSeq = (group shl 7) + (library.index(OsrsFxImportTool.INDEX_SEQ).archive(group)!!.fileIds().maxOrNull() ?: -1) + 1
                var nextFrameset = next(OsrsFxImportTool.INDEX_FRAMES)
                var nextBase = next(OsrsFxImportTool.INDEX_BASES)
                fun put(index: Int, g: Int, f: Int, bytes: ByteArray, label: String) {
                    val current = library.data(index, g, f)?.let { CacheItemProbeTool.sha1(it) }
                    mutations += CacheMutation(index, g, f, bytes, label, expectedCurrentSha1 = current)
                }
                for (loc in OsrsLocImportTool.BATCHES.getValue("deadman-breach")) {
                    val def = ModernObjectDef.decode(loc, locs.getValue(loc))
                    val localLoc = ids["osrs_loc_loc:$loc"] ?: error("loc $loc not imported")
                    check(def.models.size == 1) { "loc $loc has ${def.models.size} models" }
                    val osrsModel = def.models.single()
                    val localModel = ids["osrs_loc_model:$osrsModel"] ?: error("model $osrsModel not imported")
                    val base = nextBase++
                    val baked = bake(reader, osrsModel, def.animationId, base)
                    val frameset = nextFrameset++
                    val seqId = nextSeq++
                    put(7, localModel, 0, baked.model, "loc $loc model $osrsModel -> $localModel with one label per vertex")
                    put(OsrsFxImportTool.INDEX_BASES, base, 0, baked.base, "skeletal bake base for loc $loc")
                    baked.frames.forEachIndexed { i, f -> put(OsrsFxImportTool.INDEX_FRAMES, frameset, i, f, "skeletal bake frame $i of loc $loc") }
                    val seq =
                        OsrsFxImportTool.Seq(
                            frameDurations = baked.durations,
                            frames = IntArray(baked.frames.size) { (frameset shl 16) or it },
                        )
                    val seqBytes = OsrsFxImportTool.encode667Seq(seq)
                    OsrsFxImportTool.decode667SeqFrames(seqBytes)
                    put(OsrsFxImportTool.INDEX_SEQ, seqId ushr 7, seqId and 0x7F, seqBytes, "skeletal bake seq of loc $loc (osrs ${def.animationId})")
                    val dropped = ArrayList<String>()
                    val locBytes = Rev667LocType.encodeFromModern(def, { if (it == osrsModel) localModel else error("model $it") }, dropped) { if (it == def.animationId) seqId else null }
                    check(Rev667LocType.decode(localLoc, locBytes).allModels == listOf(localModel)) { "loc $loc model mismatch" }
                    put(FeroxImportTool.LOC_INDEX, localLoc ushr 8, localLoc and 0xFF, locBytes, "loc $loc -> $localLoc with baked animation $seqId")
                    println(
                        "PLAN loc $loc -> $localLoc model $osrsModel -> $localModel vertices=${baked.vertices} bones=${baked.bones} clusters=${baked.clusters} ticks=${baked.ticks} " +
                            "error max=${"%.1f".format(baked.maxError)} mean=${"%.2f".format(baked.meanError)} (667 units) " +
                            "frames=${baked.frames.size} base=$base frameset=$frameset seq=$seqId dropped=$dropped",
                    )
                }
            } finally {
                library.close()
            }
            val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
            val plan = tx.preflight()
            val blocking = tx.blockingErrors(plan)
            println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
            blocking.forEach { println("  BLOCKING: $it") }
            check(blocking.isEmpty()) { "preflight blocked; nothing written" }
            if (!apply) return println("DRY_RUN")
            val result = tx.apply(plan)
            val problems = tx.verify()
            if (problems.isNotEmpty()) {
                problems.forEach { println("  VERIFY_FAILURE: $it") }
                println("ROLLED_BACK ${tx.rollback()}")
                error("transaction ${tx.id} failed verification and was rolled back")
            }
            println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
        }
    }
}
