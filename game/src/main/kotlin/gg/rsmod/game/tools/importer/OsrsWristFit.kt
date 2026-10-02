package gg.rsmod.game.tools.importer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Owner 2026-10-02 ("the osrs armour still invisible at the hands", overnight run block 3). OSRS draws the lower forearm with
 * the HANDS slot (ROOT_CAUSES #106), so every OSRS glove, gauntlet and vambrace carries its own lower-forearm cuff - sized for
 * the thin 2007 forearm. 667 draws that forearm with the arms kit, and under an OSRS body that hides the arms with
 * [WornForearm], which is the same surface. Measured in the real (GL) client and on the meshes: the converted cuffs sit
 * INSIDE that skin (mean radius 14.9 Zaryte vambraces, 18.4 Swamp/Bloodbark gauntlets, against 20.1 for the 667 forearm), so
 * the skin covered them: the vambraces showed as a thin band at the wrist, the gauntlets as a bare forearm.
 *
 * [fit] widens the cuff, per side, around the axis of the 667 forearm until every cuff vertex inside the skin's height lies
 * [MARGIN] outside the skin in its own direction (the skin's cross-section is sliced at that height, so the low-poly prism of
 * the 667 forearm is followed exactly). One uniform radial scale per side keeps the cuff's own shape; the hand (labels 27/28)
 * is untouched. ADAPTED: OSRS has no thicker forearm to fit, this is the same proportional move [OsrsBodyFrame.fitShin] makes
 * for the 667 shin.
 */
object OsrsWristFit {
    /** Equipment slot of gloves / gauntlets / vambraces. */
    const val HANDS_SLOT = 9

    /** Clearance between the fitted cuff and the 667 skin forearm (fine units; 1 tile = 512). */
    const val MARGIN = 3.0

    /** Cuff vertices nearer the 667 centre line than this share of the cuff's outer radius are inner surface (no say in the scale). */
    const val INNER = 0.5

    /** Upper bound of the widening (the 667 forearm is about 1.5x the 2007 one; x3 already covers every imported cuff). */
    const val MAX_SCALE = 3.0

    /** A widening below this is rounding noise: the sheared cuff already clears the skin. */
    const val FITTED = 1.02

    /** The 667 skin forearm of one side: its label and the height it spans (the forearm slants, so its axis is per height). */
    class Side(val label: Int, val top: Int, val bottom: Int)

    fun sides(skin: ModelData): List<Side> {
        val labels = requireNotNull(skin.vertexLabel) { "skin forearm has no vertex labels" }
        return WornForearm.FOREARM_LABELS.sorted().mapNotNull { label ->
            val vs = (0 until skin.vertexCount).filter { labels[it] == label }
            if (vs.isEmpty()) return@mapNotNull null
            Side(label, vs.minOf { skin.vertexY[it] }, vs.maxOf { skin.vertexY[it] })
        }
    }

    /** The skin of [side] cut at height [y]: one segment (two x/z points) per face that crosses it. */
    fun slice(
        skin: ModelData,
        side: Side,
        y: Double,
    ): List<Pair<DoubleArray, DoubleArray>> {
        val labels = skin.vertexLabel ?: return emptyList()
        val out = ArrayList<Pair<DoubleArray, DoubleArray>>()
        for (f in 0 until skin.faceCount) {
            val vs = intArrayOf(skin.faceA[f], skin.faceB[f], skin.faceC[f])
            if (vs.any { labels[it] != side.label }) continue
            val cut = ArrayList<DoubleArray>(3)
            for (e in 0 until 3) {
                val a = vs[e]
                val b = vs[(e + 1) % 3]
                val ya = skin.vertexY[a].toDouble()
                val yb = skin.vertexY[b].toDouble()
                if (ya == yb || (ya - y) * (yb - y) > 0) continue
                val t = (y - ya) / (yb - ya)
                cut += doubleArrayOf(skin.vertexX[a] + t * (skin.vertexX[b] - skin.vertexX[a]), skin.vertexZ[a] + t * (skin.vertexZ[b] - skin.vertexZ[a]))
            }
            if (cut.size >= 2) out += cut[0] to cut[1]
        }
        return out
    }

    /** Centre of the skin's cross-section at [y] (mean of the cut points), clamped into the skin's height; null without skin. */
    fun centre(
        skin: ModelData,
        side: Side,
        y: Double,
    ): DoubleArray? {
        val cut = slice(skin, side, y.coerceIn(side.top + 0.5, side.bottom - 0.5))
        if (cut.isEmpty()) return null
        val pts = cut.flatMap { listOf(it.first, it.second) }
        return doubleArrayOf(pts.map { it[0] }.average(), pts.map { it[1] }.average())
    }

    /** The skin's radius at height [y] in direction [angle] from [centre]: the farthest crossing of that ray with the cut skin. */
    fun skinRadius(
        skin: ModelData,
        side: Side,
        y: Double,
        centre: DoubleArray,
        angle: Double,
    ): Double? {
        val dx = cos(angle)
        val dz = sin(angle)
        var best: Double? = null
        for ((a, b) in slice(skin, side, y)) {
            val px = a[0] - centre[0]
            val pz = a[1] - centre[1]
            val ex = b[0] - a[0]
            val ez = b[1] - a[1]
            val den = dx * ez - dz * ex
            if (kotlin.math.abs(den) < 1e-9) continue
            val along = (px * ez - pz * ex) / den
            val u = (px * dz - pz * dx) / den
            if (along > 0 && u >= -1e-6 && u <= 1 + 1e-6) best = max(best ?: 0.0, along)
        }
        return best
    }

    /** The radial scale [side] of [glove] needs so its cuff clears the skin; 1.0 when it already does or has no cuff there. */
    fun requiredScale(
        glove: ModelData,
        skin: ModelData,
        side: Side,
    ): Double {
        val labels = glove.vertexLabel ?: return 1.0
        // (radius from the 667 centre line, skin radius in that direction) per cuff vertex inside the skin's height.
        val samples = ArrayList<DoubleArray>()
        for (v in 0 until glove.vertexCount) {
            if (labels[v] != side.label) continue
            val y = glove.vertexY[v].toDouble()
            if (y <= side.top || y >= side.bottom) continue
            val c = centre(skin, side, y) ?: continue
            val rx = glove.vertexX[v] - c[0]
            val rz = glove.vertexZ[v] - c[1]
            val r = hypot(rx, rz)
            if (r < 1.0) continue
            val skinR = skinRadius(skin, side, y, c, atan2(rz, rx)) ?: continue
            samples += doubleArrayOf(r, skinR)
        }
        if (samples.isEmpty()) return 1.0
        // A vertex much nearer the centre line than the cuff's outer surface is its inner lining or a fan centre; the share
        // is taken of the cuff's own outer radius, so the same vertices count before and after a widening.
        val outer = samples.maxOf { it[0] }
        var scale = 1.0
        for ((r, skinR) in samples.map { it[0] to it[1] }) {
            if (r < INNER * outer) continue
            scale = max(scale, (skinR + MARGIN) / r)
        }
        return scale.coerceAtMost(MAX_SCALE)
    }

    /**
     * Fits [glove] (a converted, version 13+ worn mesh of the hands slot) onto [skin] in place, per side: the OSRS cuff hangs
     * straight down, the 667 forearm slants (its cross-section centre moves ~25 units forward from elbow to wrist), so the
     * cuff is first sheared onto the 667 centre line (each vertex keeps its offset from the cuff's own centre, taken at the
     * skin's centre for its height), then widened around that line until it clears the skin. Returns the scale per label.
     */
    fun fit(
        glove: ModelData,
        skin: ModelData,
    ): Map<Int, Double> {
        require(glove.version >= OsrsBodyFrame.FRAME_VERSION) { "fit a converted mesh (version ${glove.version})" }
        val labels = glove.vertexLabel ?: return emptyMap()
        val applied = LinkedHashMap<Int, Double>()
        sides(skin).forEach { side ->
            val cuff = (0 until glove.vertexCount).filter { labels[it] == side.label }
            val inside = cuff.filter { glove.vertexY[it] > side.top && glove.vertexY[it] < side.bottom }
            if (inside.isEmpty()) return@forEach
            val gx = inside.map { glove.vertexX[it] }.average()
            val gz = inside.map { glove.vertexZ[it] }.average()
            for (v in cuff) {
                val c = centre(skin, side, glove.vertexY[v].toDouble()) ?: continue
                glove.vertexX[v] = (c[0] + glove.vertexX[v] - gx).roundToInt()
                glove.vertexZ[v] = (c[1] + glove.vertexZ[v] - gz).roundToInt()
            }
            // Widen until it clears: a widening can move which vertices are outer surface, so measure again (at most a few
            // rounds, never past MAX_SCALE in total).
            var total = 1.0
            repeat(4) {
                val scale = requiredScale(glove, skin, side).coerceAtMost(MAX_SCALE / total)
                if (scale < FITTED) return@repeat
                for (v in cuff) {
                    val c = centre(skin, side, glove.vertexY[v].toDouble()) ?: continue
                    glove.vertexX[v] = (c[0] + (glove.vertexX[v] - c[0]) * scale).roundToInt()
                    glove.vertexZ[v] = (c[1] + (glove.vertexZ[v] - c[1]) * scale).roundToInt()
                }
                total *= scale
            }
            applied[side.label] = total
        }
        return applied
    }

    /** [fit] on encoded bytes; returns the bytes unchanged when the mesh already fits. */
    fun fitBytes(
        bytes: ByteArray,
        skinPiece: WornForearm.Piece,
    ): ByteArray {
        val glove = Rev667ModelDecoder.decode(bytes)
        if (fit(glove, Rev667ModelDecoder.decode(skinPiece.bytes, geometryOnly = true)).isEmpty()) return bytes
        val out = Rev667ModelEncoder.encode(glove)
        val back = Rev667ModelDecoder.decode(out)
        check(back.vertexCount == glove.vertexCount && back.faceCount == glove.faceCount) { "wrist fit does not round-trip" }
        return out
    }
}
