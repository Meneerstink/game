package gg.rsmod.game.tools.importer

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The six Deadman emblems (owner 2026-09-25): one recognisable crest - a horned shield with a pointed foot, a sunken
 * front plate carrying an embossed skull over crossed bones - that grows more impressive per tier:
 *
 *  1. dark ancient iron, plain rim;
 *  2. crimson inlay ring in the rim, crimson eye sockets and border runes;
 *  3. blood cracks, a crimson blood core (a cut gem under the skull) and two side spikes;
 *  4. blackened metal, more cracks, a larger core, lower spikes;
 *  5. blackened metal with an aged-gold bevel, gold skull and runes, a gold-set core and a three-spike crown;
 *  6. black and aged gold with crimson: five-spike crown, gilded horns, the largest gold-set core and glowing cracks.
 *
 * Geometry is built here as a [ModelData]; the plate artwork is painted into one texture per tier (planar mapped onto
 * the plate, front and back). Model units follow the revision-667 item convention: the model stands on y = 0 and
 * extends to negative y (up); the front faces -z, the side the inventory camera looks at.
 */
object DeadmanEmblemArt {
    const val TIERS = 6
    const val TEXTURE_SIZE = 256

    /** Half width and full height of the crest, in model units (set from the probe of comparable 667 item models). */
    const val halfWidth = 50
    const val height = 128

    /** Crest outline, right half, authoring units (x 0..1 outwards, y -1 foot .. +1 top), top centre to foot. */
    private val HALF_OUTLINE =
        listOf(
            0.00 to 0.90, // central peak
            0.16 to 0.72,
            0.36 to 0.79,
            0.60 to 0.98, // horn tip
            0.70 to 0.70,
            0.92 to 0.52, // shoulder
            0.97 to 0.20,
            0.90 to -0.14,
            0.74 to -0.46,
            0.48 to -0.75,
            0.00 to -1.00, // foot
        )

    /** Full outline, clockwise as seen from the front (x right, y up). */
    val OUTLINE: List<Pair<Double, Double>> by lazy {
        val right = HALF_OUTLINE
        val left = HALF_OUTLINE.subList(1, HALF_OUTLINE.size - 1).reversed().map { -it.first to it.second }
        right + left
    }

    /** Centre the rings scale towards (slightly above the geometric middle, so the rim stays even around the horns). */
    private const val CENTRE_Y = 0.05

    private fun hsl(h: Int, s: Int, l: Int) = ((h shl 10) or (s shl 7) or l).toShort()

    class Palette(
        val side: Short,
        val bevel: Short,
        val rim: Short,
        val inlay: Short?,
        val wall: Short,
        val spike: Short,
        val horn: Short?,
        val gem: IntArray?,
        val bezel: Short?,
    )

    private val GOLD_DARK = hsl(7, 5, 34)
    private val GOLD = hsl(7, 5, 46)
    private val GOLD_BRIGHT = hsl(8, 5, 56)

    fun palette(tier: Int): Palette =
        when (tier) {
            1 -> Palette(hsl(0, 0, 16), hsl(0, 0, 30), hsl(0, 0, 24), null, hsl(0, 0, 10), hsl(0, 0, 26), null, null, null)
            2 -> Palette(hsl(0, 0, 15), hsl(0, 0, 29), hsl(0, 0, 22), hsl(0, 7, 24), hsl(0, 0, 9), hsl(0, 0, 26), null, null, null)
            3 -> Palette(hsl(0, 0, 13), hsl(0, 0, 27), hsl(0, 0, 20), hsl(0, 7, 28), hsl(0, 0, 8), hsl(0, 0, 28), null, intArrayOf(20, 28, 36, 46), null)
            4 -> Palette(hsl(0, 0, 9), hsl(0, 0, 22), hsl(0, 0, 14), hsl(0, 7, 32), hsl(0, 1, 6), hsl(0, 1, 20), null, intArrayOf(22, 30, 40, 52), null)
            5 -> Palette(hsl(0, 0, 8), GOLD, hsl(0, 0, 13), hsl(0, 7, 34), hsl(0, 1, 5), GOLD_DARK, GOLD, intArrayOf(24, 32, 44, 58), GOLD)
            else -> Palette(hsl(0, 0, 6), GOLD_BRIGHT, GOLD_DARK, hsl(0, 7, 38), hsl(0, 1, 4), GOLD, GOLD_BRIGHT, intArrayOf(26, 36, 50, 66), GOLD_BRIGHT)
        }

    // ---------------------------------------------------------------- geometry

    private class Mesh {
        val vx = ArrayList<Int>()
        val vy = ArrayList<Int>()
        val vz = ArrayList<Int>()
        val fa = ArrayList<Int>()
        val fb = ArrayList<Int>()
        val fc = ArrayList<Int>()
        val colour = ArrayList<Short>()
        val texture = ArrayList<Short>()
        val space = ArrayList<Byte>()
        val flat = ArrayList<Byte>()
        val spaces = ArrayList<Triple<Int, Int, Int>>()

        fun v(x: Double, y: Double, z: Double): Int {
            vx += Math.round(x).toInt(); vy += Math.round(y).toInt(); vz += Math.round(z).toInt()
            return vx.size - 1
        }

        /** One triangle; [a] [b] [c] clockwise as seen from the side it faces (see [FRONT_CLOCKWISE]). */
        fun f(a: Int, b: Int, c: Int, col: Short, flatShaded: Boolean = true, tex: Int = -1, sp: Int = -1) {
            if (FRONT_CLOCKWISE) { fa += a; fb += b; fc += c } else { fa += a; fb += c; fc += b }
            colour += col; texture += tex.toShort(); space += sp.toByte(); flat += (if (flatShaded) 1 else 0).toByte()
        }

        fun quad(a: Int, b: Int, c: Int, d: Int, col: Short, flatShaded: Boolean = true) {
            f(a, b, c, col, flatShaded); f(a, c, d, col, flatShaded)
        }

        fun build(): ModelData {
            val m = ModelData(vx.size, fa.size, spaces.size)
            for (i in vx.indices) { m.vertexX[i] = vx[i]; m.vertexY[i] = vy[i]; m.vertexZ[i] = vz[i] }
            for (i in fa.indices) { m.faceA[i] = fa[i]; m.faceB[i] = fb[i]; m.faceC[i] = fc[i]; m.faceColour[i] = colour[i] }
            m.shadingType = flat.toByteArray()
            m.faceTexture = texture.toShortArray()
            m.faceTexSpace = space.toByteArray()
            m.texMappingType = ByteArray(spaces.size)
            m.texSpaceDefA = spaces.map { it.first.toShort() }.toShortArray()
            m.texSpaceDefB = spaces.map { it.second.toShort() }.toShortArray()
            m.texSpaceDefC = spaces.map { it.third.toShort() }.toShortArray()
            return m
        }
    }

    /**
     * Winding the 667 renderer treats as front-facing, as seen from the viewer: true = clockwise on screen. Set by
     * [DeadmanEmblemTool] from a probe of an existing, correctly rendering imported item model before building.
     */
    var FRONT_CLOCKWISE = true

    /** Authoring point (x right, y up, 0..1 of the half extents) on ring [scale] -> model x/y (y down, foot on 0). */
    private fun mx(x: Double, scale: Double) = x * scale * halfWidth
    private fun my(y: Double, scale: Double) = -((CENTRE_Y + (y - CENTRE_Y) * scale) + 1.0) / 2.0 * height

    /** Ring of [OUTLINE] points at [scale] on plane [z]. */
    private fun Mesh.ring(scale: Double, z: Double): IntArray = OUTLINE.map { (x, y) -> v(mx(x, scale), my(y, scale), z) }.toIntArray()

    /**
     * Band between two rings (same point count). Both rings are listed clockwise as seen from the viewer the band
     * faces ([towardsFront] true = the -z side).
     */
    private fun Mesh.band(outer: IntArray, inner: IntArray, col: Short, towardsFront: Boolean) {
        val n = outer.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            if (towardsFront) quad(outer[i], outer[j], inner[j], inner[i], col) else quad(outer[j], outer[i], inner[i], inner[j], col)
        }
    }

    /** Ear-clipping triangulation of [OUTLINE] (simple polygon), as index triples in outline order. */
    private val PLATE_TRIANGLES: List<IntArray> by lazy {
        val pts = OUTLINE
        val idx = pts.indices.toMutableList()
        // OUTLINE is clockwise in y-up space; ear test uses that orientation.
        fun cross(o: Int, a: Int, b: Int): Double {
            val (ox, oy) = pts[o]; val (ax, ay) = pts[a]; val (bx, by) = pts[b]
            return (ax - ox) * (by - oy) - (ay - oy) * (bx - ox)
        }
        fun inside(p: Int, a: Int, b: Int, c: Int): Boolean {
            val d1 = cross(p, a, b); val d2 = cross(p, b, c); val d3 = cross(p, c, a)
            val neg = d1 < 0 || d2 < 0 || d3 < 0
            val pos = d1 > 0 || d2 > 0 || d3 > 0
            return !(neg && pos)
        }
        val out = ArrayList<IntArray>()
        var guard = 0
        while (idx.size > 3 && guard++ < 1000) {
            var clipped = false
            for (k in idx.indices) {
                val a = idx[(k + idx.size - 1) % idx.size]
                val b = idx[k]
                val c = idx[(k + 1) % idx.size]
                if (cross(a, b, c) >= 0) continue // reflex (clockwise polygon: convex corners turn negative)
                if (idx.any { it != a && it != b && it != c && inside(it, a, b, c) }) continue
                out += intArrayOf(a, b, c)
                idx.removeAt(k)
                clipped = true
                break
            }
            check(clipped) { "plate triangulation failed" }
        }
        out += intArrayOf(idx[0], idx[1], idx[2])
        out
    }

    /** Plate texture frame: bounding box of the plate ring in model units (x0, top y, x1, bottom y). */
    fun plateFrame(scale: Double = PLATE): DoubleArray {
        val xs = OUTLINE.map { mx(it.first, scale) }
        val ys = OUTLINE.map { my(it.second, scale) }
        return doubleArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }

    private const val PLATE = 0.80
    private const val RIM_INNER = 0.84
    private const val INLAY_OUTER = 0.90
    private const val RIM_OUTER = 0.95

    /** Builds tier [tier]'s model with the plate texture [textureId]. */
    fun model(tier: Int, textureId: Int): ModelData {
        val p = palette(tier)
        val thick = 14.0 + tier // thicker, heavier emblem per tier
        val half = thick / 2
        val bevelDepth = 3.0
        val recess = 2.5 + tier * 0.25
        return Mesh().apply {
            // Front rings.
            val sideF = ring(1.0, -half + bevelDepth)
            val rimF = ring(RIM_OUTER, -half)
            val inlayF = ring(INLAY_OUTER, -half)
            val innerF = ring(RIM_INNER, -half)
            val plateF = ring(PLATE, -half + recess)
            // Back rings.
            val sideB = ring(1.0, half - bevelDepth)
            val rimB = ring(RIM_OUTER, half)
            val inlayB = ring(INLAY_OUTER, half)
            val innerB = ring(RIM_INNER, half)
            val plateB = ring(PLATE, half - recess)

            // Outer wall (faces outwards: seen from outside, sideF -> sideB).
            val n = OUTLINE.size
            for (i in 0 until n) {
                val j = (i + 1) % n
                // Outline is clockwise from the front; the outward wall quad seen from outside runs i -> j on the front ring.
                quad(sideF[j], sideF[i], sideB[i], sideB[j], p.side)
            }
            // Bevels, rim tops, inlay, inner walls - front and back.
            band(sideF, rimF, p.bevel, towardsFront = true)
            band(sideB, rimB, p.bevel, towardsFront = false)
            if (p.inlay != null) {
                band(rimF, inlayF, p.rim, towardsFront = true)
                band(inlayF, innerF, p.inlay, towardsFront = true)
                band(rimB, inlayB, p.rim, towardsFront = false)
                band(inlayB, innerB, p.inlay, towardsFront = false)
            } else {
                band(rimF, innerF, p.rim, towardsFront = true)
                band(rimB, innerB, p.rim, towardsFront = false)
            }
            band(innerF, plateF, p.wall, towardsFront = true)
            band(innerB, plateB, p.wall, towardsFront = false)

            // Plates: planar texture space from the plate frame corners (P top-left, M top-right, N bottom-left).
            val (x0, y0, x1, y1) = plateFrame().toList()
            val zf = -half + recess
            val zb = half - recess
            val front = spaces.size
            spaces += Triple(v(x0, y0, zf), v(x1, y0, zf), v(x0, y1, zf))
            val back = spaces.size
            // Back is seen mirrored: P top-right, M top-left, N bottom-right, so the art reads correctly from behind.
            spaces += Triple(v(x1, y0, zb), v(x0, y0, zb), v(x1, y1, zb))
            val white = hsl(0, 0, 127)
            PLATE_TRIANGLES.forEach { (a, b, c) ->
                f(plateF[a], plateF[b], plateF[c], white, flatShaded = false, tex = textureId, sp = front)
                f(plateB[a], plateB[c], plateB[b], white, flatShaded = false, tex = textureId, sp = back)
            }

            // Spikes and horn caps.
            spikes(tier).forEach { s -> spike(s, half * 0.7, p.spike) }
            if (p.horn != null) {
                listOf(1.0, -1.0).forEach { side -> spike(Spike(0.60 * side, 0.98, 0.42 * side, 0.9, if (tier >= 6) 0.16 else 0.11, 0.07), half * 0.75, p.horn) }
            }

            // Blood core: a cut gem under the skull, gold-set from tier 5.
            if (p.gem != null) {
                val size = when (tier) { 3 -> 0.13; 4 -> 0.16; 5 -> 0.18; else -> 0.21 }
                gem(0.0, -0.50, size, -half + recess, p.gem, p.bezel)
            }
        }.build()
    }

    /** A spike from outline point ([x], [y]) along ([dx], [dy]), [length] and base half-width [width] in half-extents. */
    class Spike(val x: Double, val y: Double, val dx: Double, val dy: Double, val length: Double, val width: Double)

    private fun spikes(tier: Int): List<Spike> {
        val out = ArrayList<Spike>()
        fun pair(x: Double, y: Double, dx: Double, dy: Double, len: Double, w: Double) {
            out += Spike(x, y, dx, dy, len, w); out += Spike(-x, y, -dx, dy, len, w)
        }
        if (tier >= 3) pair(0.95, 0.20, 1.0, 0.12, 0.20 + tier * 0.012, 0.075)
        if (tier >= 4) pair(0.78, -0.40, 0.72, -0.62, 0.15 + tier * 0.01, 0.065)
        if (tier >= 5) {
            out += Spike(0.0, 0.88, 0.0, 1.0, if (tier >= 6) 0.30 else 0.22, 0.08)
            pair(0.17, 0.72, 0.12, 1.0, if (tier >= 6) 0.18 else 0.14, 0.06)
        }
        if (tier >= 6) pair(0.36, 0.78, 0.28, 1.0, 0.13, 0.055)
        return out
    }

    /** Four-sided spike: base diamond around the outline point (in the plate plane and through the thickness). */
    private fun Mesh.spike(s: Spike, depth: Double, col: Short) {
        val len = hypot(s.dx, s.dy)
        val ux = s.dx / len
        val uy = s.dy / len
        // Base centre sits slightly inside the outline so the spike grows out of the rim.
        val bx = s.x - ux * 0.05
        val by = s.y - uy * 0.05
        val px = -uy
        val py = ux
        val w = s.width
        val base = listOf(
            v(mx(bx + px * w, 1.0), my(by + py * w, 1.0), 0.0),
            v(mx(bx, 1.0), my(by, 1.0), -depth),
            v(mx(bx - px * w, 1.0), my(by - py * w, 1.0), 0.0),
            v(mx(bx, 1.0), my(by, 1.0), depth),
        )
        val tip = v(mx(s.x + ux * s.length, 1.0), my(s.y + uy * s.length, 1.0), 0.0)
        for (i in 0 until 4) {
            val a = base[i]
            val b = base[(i + 1) % 4]
            f(tip, a, b, col); f(tip, b, a, col) // both windings: thin blade, visible from every side
        }
    }

    /**
     * Cut gem at authoring point ([x], [y]) on the plate plane [z]: a table, crown facets and a girdle ring, flat shaded
     * with facet-dependent lightness ([shades]: dark, mid, light, highlight) for sparkle. Optional gold bezel.
     */
    private fun Mesh.gem(x: Double, y: Double, r: Double, z: Double, shades: IntArray, bezel: Short?) {
        val cx = mx(x, 1.0)
        val cy = my(y, 1.0)
        val rx = r * halfWidth
        val ry = rx
        val sides = 8
        val depth = rx * 0.75
        fun ringAt(scale: Double, dz: Double, turn: Double) = IntArray(sides) { i ->
            val a = 2 * PI * (i + turn) / sides
            v(cx + cos(a) * rx * scale, cy + sin(a) * ry * scale, z + dz)
        }
        if (bezel != null) {
            val outer = ringAt(1.35, 0.0, 0.0)
            val top = ringAt(1.18, -depth * 0.35, 0.0)
            val lip = ringAt(1.0, -depth * 0.35, 0.0)
            for (i in 0 until sides) {
                val j = (i + 1) % sides
                quad(outer[i], outer[j], top[j], top[i], bezel)
                quad(top[i], top[j], lip[j], lip[i], bezel)
            }
        }
        val girdle = ringAt(1.0, -depth * 0.30, 0.0)
        val crown = ringAt(0.62, -depth * 0.85, 0.5)
        val table = v(cx, cy, z - depth)
        for (i in 0 until sides) {
            val j = (i + 1) % sides
            // Facets lit from the upper left: pick the shade from the facet direction.
            val a = 2 * PI * (i + 0.5) / sides
            val lightness = ((-cos(a) * 0.5 - sin(a) * 0.5) + 1) / 2 // 0..1, 1 = faces the light
            val shade = shades[(lightness * (shades.size - 1)).toInt().coerceIn(0, shades.size - 1)]
            val col = hsl(0, 7, shade)
            quad(girdle[i], girdle[j], crown[j], crown[i], col)
            f(table, crown[i], crown[j], hsl(0, 7, (shade + 8).coerceAtMost(90)))
        }
    }

    // ---------------------------------------------------------------- texture

    private fun c(rgb: Int, a: Int = 255) = Color(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF, a)

    private class Theme(
        val baseTop: Int,
        val baseBottom: Int,
        val relief: Int,
        val reliefLight: Int,
        val accent: Int?,
        val accentGlow: Int?,
        val gold: Boolean,
        val cracks: Int,
        val grime: Double,
    )

    private fun theme(tier: Int): Theme =
        when (tier) {
            1 -> Theme(0x57534d, 0x35322e, 0x837d72, 0xb9b2a4, null, null, false, 0, 0.9)
            2 -> Theme(0x4f4b46, 0x2f2c29, 0x7d776c, 0xb2ab9d, 0x7a1116, 0xa3161b, false, 0, 0.7)
            3 -> Theme(0x45413d, 0x282523, 0x746e64, 0xaaa396, 0x8e1318, 0xc41c22, false, 5, 0.55)
            4 -> Theme(0x332f2e, 0x1b1818, 0x625c57, 0x958e86, 0xa3161b, 0xe0262c, false, 8, 0.4)
            5 -> Theme(0x292424, 0x141111, 0xb89040, 0xf0cf7c, 0xab171c, 0xe8282e, true, 8, 0.25)
            else -> Theme(0x1f1a1a, 0x0d0a0a, 0xcaa04a, 0xffe29a, 0xc0181f, 0xff3a38, true, 12, 0.15)
        }

    /**
     * Tier [tier]'s plate artwork, [TEXTURE_SIZE] square, covering [plateFrame] (non-uniformly: drawn in plate model
     * units through a transform so shapes look right once mapped back onto the plate).
     */
    fun texture(tier: Int): BufferedImage {
        val t = theme(tier)
        val img = BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        val (x0, y0, x1, y1) = plateFrame().toList()
        // Plate units: x right, y DOWN (model y), origin at the plate frame's top-left.
        val sx = TEXTURE_SIZE / (x1 - x0)
        val sy = TEXTURE_SIZE / (y1 - y0)
        g.transform = AffineTransform(sx, 0.0, 0.0, sy, -x0 * sx, -y0 * sy)
        val rnd = Random(0xDEAD + tier)

        // Base metal: vertical gradient plus grain.
        g.paint = java.awt.GradientPaint(0f, y0.toFloat(), c(t.baseTop), 0f, y1.toFloat(), c(t.baseBottom))
        g.fill(java.awt.geom.Rectangle2D.Double(x0 - 5, y0 - 5, x1 - x0 + 10, y1 - y0 + 10))
        g.transform = AffineTransform()
        for (i in 0 until 2600) {
            val px = rnd.nextInt(TEXTURE_SIZE)
            val py = rnd.nextInt(TEXTURE_SIZE)
            val light = rnd.nextBoolean()
            g.color = if (light) Color(255, 255, 255, 10 + rnd.nextInt(12)) else Color(0, 0, 0, 16 + rnd.nextInt(22))
            g.fillRect(px, py, 1 + rnd.nextInt(2), 1)
        }
        g.transform = AffineTransform(sx, 0.0, 0.0, sy, -x0 * sx, -y0 * sy)

        // Central glow behind the skull and the core (tier 3+).
        val skullX = 0.0
        val skullY = my(0.10, 1.0)
        val coreY = my(-0.50, 1.0)
        if (t.accentGlow != null && tier >= 3) {
            val r = (18 + tier * 5).toFloat()
            g.paint = RadialGradientPaint(Point2D.Double(0.0, coreY), r, floatArrayOf(0f, 1f), arrayOf(c(t.accentGlow, 60 + tier * 18), c(t.accentGlow, 0)))
            g.fill(Ellipse2D.Double(-r.toDouble(), coreY - r, r * 2.0, r * 2.0))
        }

        // Engraved inner border following the crest, then border runes (tier 2+).
        val border = outlinePath(PLATE * 0.93 / PLATE)
        g.stroke = BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = Color(0, 0, 0, 170)
        g.draw(border)
        g.stroke = BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = if (t.gold) c(t.reliefLight, 230) else Color(255, 255, 255, 40)
        g.draw(AffineTransform.getTranslateInstance(0.0, 1.1).createTransformedShape(border))
        if (t.gold) {
            g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = c(t.relief)
            g.draw(border)
        }
        if (tier >= 2) runes(g, t, rnd)

        // Skull and bones are authored at 1:1 around the skull centre and drawn at SKULL_SCALE.
        val plateTransform = g.transform
        g.transform(AffineTransform.getTranslateInstance(skullX, skullY))
        g.transform(AffineTransform.getScaleInstance(SKULL_SCALE, SKULL_SCALE))
        skullAndBones(g, t, tier)
        g.transform = plateTransform

        // Blood cracks (tier 3+): branch out from the sockets and the core towards the rim.
        if (t.cracks > 0) {
            val k = SKULL_SCALE
            val starts = listOf(skullX - 16 * k to skullY - 14 * k, skullX + 16 * k to skullY - 14 * k, 0.0 to coreY, 0.0 to coreY, skullX - 22 * k to skullY + 4 * k, skullX + 22 * k to skullY + 4 * k)
            for (n in 0 until t.cracks) {
                val (sx0, sy0) = starts[n % starts.size]
                val angle = when (n % starts.size) {
                    0 -> -PI * 0.75 + rnd.nextDouble(-0.35, 0.35)
                    1 -> -PI * 0.25 + rnd.nextDouble(-0.35, 0.35)
                    2 -> PI * 0.5 + rnd.nextDouble(-0.7, 0.7)
                    3 -> PI * (0.5 + rnd.nextDouble(-0.9, 0.9))
                    4 -> PI + rnd.nextDouble(-0.4, 0.4)
                    else -> rnd.nextDouble(-0.4, 0.4)
                }
                crack(g, t, rnd, sx0, sy0, angle, 14.0 + tier * 3 + rnd.nextDouble(0.0, 9.0), 1.5 + tier * 0.1, tier)
            }
        }

        // Gold filigree at the horns and the foot (tier 5+).
        if (t.gold) filigree(g, t, tier)

        // Scratches and grime: heavier on the ancient tiers.
        g.transform = AffineTransform()
        val scratches = (70 * t.grime).toInt()
        for (i in 0 until scratches) {
            val px = rnd.nextDouble(0.0, TEXTURE_SIZE.toDouble())
            val py = rnd.nextDouble(0.0, TEXTURE_SIZE.toDouble())
            val a = rnd.nextDouble(0.0, PI)
            val l = rnd.nextDouble(3.0, 14.0)
            g.stroke = BasicStroke(0.6f)
            g.color = Color(255, 255, 255, 18 + rnd.nextInt(18))
            g.draw(java.awt.geom.Line2D.Double(px, py, px + cos(a) * l, py + sin(a) * l))
        }
        for (i in 0 until (140 * t.grime).toInt()) {
            val px = rnd.nextDouble(0.0, TEXTURE_SIZE.toDouble())
            val py = rnd.nextDouble(0.0, TEXTURE_SIZE.toDouble())
            val r = rnd.nextDouble(2.0, 7.0).toFloat()
            g.paint = RadialGradientPaint(Point2D.Double(px, py), r, floatArrayOf(0f, 1f), arrayOf(Color(20, 14, 8, 55), Color(20, 14, 8, 0)))
            g.fill(Ellipse2D.Double(px - r, py - r, r * 2.0, r * 2.0))
        }
        // Edge darkening towards the rim (ambient occlusion of the sunken plate).
        g.transform = AffineTransform(sx, 0.0, 0.0, sy, -x0 * sx, -y0 * sy)
        for (k in 0 until 6) {
            g.stroke = BasicStroke((9 - k).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = Color(0, 0, 0, 26)
            g.draw(outlinePath(1.0))
        }
        g.dispose()
        return img
    }

    private const val SKULL_SCALE = 0.80

    /** Crossed bones and the embossed skull, centred on the origin, 1 unit = 1 model unit before [SKULL_SCALE]. */
    private fun skullAndBones(g: Graphics2D, t: Theme, tier: Int) {
        val skullX = 0.0
        val skullY = 0.0
        val bones = Area()
        listOf(38.0, -38.0).forEach { deg ->
            val bone = Area(RoundRectangle2D.Double(-44.0, -3.2, 88.0, 6.4, 6.0, 6.0))
            listOf(-44.0, 44.0).forEach { end ->
                bone.add(Area(Ellipse2D.Double(end - 5.0, -7.0, 7.5, 7.5)))
                bone.add(Area(Ellipse2D.Double(end - 5.0, -0.5, 7.5, 7.5)))
            }
            bone.transform(AffineTransform.getRotateInstance(Math.toRadians(deg)))
            bones.add(bone)
        }
        bones.transform(AffineTransform.getTranslateInstance(skullX, skullY + 14))
        emboss(g, bones, t, 0.85)

        // Skull: cranium, cheekbones and jaw as one embossed shape, then sockets, nose and teeth cut in.
        val skull = Area(Ellipse2D.Double(skullX - 27, skullY - 36, 54.0, 50.0))
        skull.add(Area(RoundRectangle2D.Double(skullX - 19, skullY - 2, 38.0, 24.0, 12.0, 12.0)))
        skull.add(Area(Ellipse2D.Double(skullX - 26, skullY - 10, 16.0, 16.0)))
        skull.add(Area(Ellipse2D.Double(skullX + 10, skullY - 10, 16.0, 16.0)))
        emboss(g, skull, t, 1.0)

        // Eye sockets.
        listOf(-1.0, 1.0).forEach { side ->
            val socket = GeneralPath().apply {
                val cx = skullX + side * 11.5
                val cy = skullY - 10
                moveTo(cx - side * 9.5, cy - 4.0)
                curveTo(cx - side * 6, cy - 9.5, cx + side * 6, cy - 8.5, cx + side * 9.0, cy - 1.5)
                curveTo(cx + side * 8.5, cy + 7.5, cx - side * 5.5, cy + 8.5, cx - side * 9.5, cy + 2.0)
                closePath()
            }
            g.color = Color(0, 0, 0, 235)
            g.fill(socket)
            if (t.accent != null) {
                val glow = if (tier >= 3) t.accentGlow!! else t.accent
                val cx = skullX + side * 11.5
                val cy = skullY - 9.0
                val r = if (tier >= 3) 8.5f else 6.5f
                g.paint = RadialGradientPaint(Point2D.Double(cx, cy), r, floatArrayOf(0f, 0.45f, 1f),
                    arrayOf(c(if (tier >= 5) 0xffd0b0 else glow, 255), c(glow, 220), c(t.accent, 0)), MultipleGradientPaint.CycleMethod.NO_CYCLE)
                val clip = g.clip
                g.clip(socket)
                g.fill(Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0))
                g.clip = clip
            }
        }
        // Nose.
        g.color = Color(0, 0, 0, 225)
        g.fill(GeneralPath().apply {
            moveTo(skullX, skullY + 1.0); lineTo(skullX - 4.2, skullY + 8.5); lineTo(skullX + 4.2, skullY + 8.5); closePath()
        })
        // Teeth: dark gaps across the jaw.
        g.stroke = BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        g.color = Color(0, 0, 0, 200)
        g.draw(java.awt.geom.Line2D.Double(skullX - 15, skullY + 13.5, skullX + 15, skullY + 13.5))
        for (k in -3..3) g.draw(java.awt.geom.Line2D.Double(skullX + k * 4.6, skullY + 10.5, skullX + k * 4.6, skullY + 19.0))

    }

    /** Outline of the plate ring scaled by [k] (1 = the plate edge itself), in plate model units. */
    private fun outlinePath(k: Double): Path2D {
        val scale = PLATE * k
        val path = Path2D.Double()
        OUTLINE.forEachIndexed { i, (x, y) ->
            val px = mx(x, scale)
            val py = my(y, scale)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.closePath()
        return path
    }

    /** Raised relief: dark drop shadow, light rim, then the body with a top-lit gradient. */
    private fun emboss(g: Graphics2D, shape: Area, t: Theme, strength: Double) {
        val shadow = shape.createTransformedArea(AffineTransform.getTranslateInstance(1.6, 2.0))
        g.color = Color(0, 0, 0, (160 * strength).toInt())
        g.fill(shadow)
        val light = shape.createTransformedArea(AffineTransform.getTranslateInstance(-0.8, -1.0))
        g.color = c(t.reliefLight, (200 * strength).toInt())
        g.fill(light)
        val b = shape.bounds2D
        g.paint = java.awt.GradientPaint(0f, b.minY.toFloat(), c(mix(t.relief, t.reliefLight, 0.35)), 0f, b.maxY.toFloat(), c(mix(t.relief, 0, 0.35)))
        g.fill(shape)
    }

    private fun mix(a: Int, b: Int, f: Double): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) * (1 - f) + (b shr s and 0xFF) * f).toInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Small engraved (or gilded) glyphs along the inside of the border. */
    private fun runes(g: Graphics2D, t: Theme, rnd: Random) {
        val pts = ArrayList<Pair<Double, Double>>()
        var last: Pair<Double, Double>? = null
        var acc = 0.0
        val scale = 0.86
        OUTLINE.indices.forEach { i ->
            val (ax, ay) = OUTLINE[i]
            val (bx, by) = OUTLINE[(i + 1) % OUTLINE.size]
            val x0 = mx(ax, PLATE * scale); val y0 = my(ay, PLATE * scale)
            val x1 = mx(bx, PLATE * scale); val y1 = my(by, PLATE * scale)
            val len = hypot(x1 - x0, y1 - y0)
            var d = if (last == null) 0.0 else 9.0 - acc
            while (d <= len) {
                val f = d / len
                pts += (x0 + (x1 - x0) * f) to (y0 + (y1 - y0) * f)
                d += 9.0
            }
            acc = (acc + len) % 9.0
            last = x1 to y1
        }
        val col = if (t.gold) c(t.reliefLight, 230) else c(t.accent ?: 0x7a1116, 210)
        pts.forEachIndexed { i, (px, py) ->
            // Skip the runes that would sit on the horns (too tight there).
            if (py < my(0.66, PLATE)) return@forEachIndexed
            g.stroke = BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = Color(0, 0, 0, 150)
            glyph(g, px + 0.5, py + 0.6, i + rnd.nextInt(3))
            g.color = col
            glyph(g, px, py, i + rnd.nextInt(3))
        }
    }

    private fun glyph(g: Graphics2D, x: Double, y: Double, kind: Int) {
        val s = 2.4
        val p = GeneralPath()
        when (kind % 5) {
            0 -> { p.moveTo(x, y - s); p.lineTo(x, y + s); p.moveTo(x - s * 0.7, y - s * 0.3); p.lineTo(x + s * 0.7, y + s * 0.3) }
            1 -> { p.moveTo(x - s * 0.7, y + s); p.lineTo(x, y - s); p.lineTo(x + s * 0.7, y + s) }
            2 -> { p.moveTo(x - s * 0.6, y - s); p.lineTo(x + s * 0.6, y); p.lineTo(x - s * 0.6, y + s) }
            3 -> { p.moveTo(x, y - s); p.lineTo(x, y + s); p.moveTo(x - s * 0.6, y - s * 0.6); p.lineTo(x + s * 0.6, y - s * 0.6) }
            else -> { p.moveTo(x - s * 0.6, y - s); p.lineTo(x + s * 0.6, y + s); p.moveTo(x + s * 0.6, y - s); p.lineTo(x - s * 0.6, y + s) }
        }
        g.draw(p)
    }

    /** One branching crack: dark bed, blood body and (tier 4+) a bright glowing core line. */
    private fun crack(g: Graphics2D, t: Theme, rnd: Random, x: Double, y: Double, angle: Double, length: Double, width: Double, tier: Int) {
        val path = GeneralPath()
        path.moveTo(x, y)
        var cx = x
        var cy = y
        var a = angle
        var left = length
        val branches = ArrayList<Triple<Double, Double, Double>>()
        while (left > 0) {
            val step = rnd.nextDouble(3.0, 6.0)
            a += rnd.nextDouble(-0.55, 0.55)
            cx += cos(a) * step
            cy += sin(a) * step
            path.lineTo(cx, cy)
            left -= step
            if (rnd.nextDouble() < 0.22 && left > 8) branches += Triple(cx, cy, a + (if (rnd.nextBoolean()) 0.9 else -0.9))
        }
        g.stroke = BasicStroke((width + 1.6).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = Color(0, 0, 0, 170)
        g.draw(path)
        g.stroke = BasicStroke(width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = c(t.accent!!, 235)
        g.draw(path)
        if (tier >= 4) {
            g.stroke = BasicStroke((width * 0.38).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = c(if (tier >= 6) 0xff8a5a else t.accentGlow!!, 240)
            g.draw(path)
        }
        branches.forEach { (bx, by, ba) -> crack(g, t, rnd, bx, by, ba, length * 0.35, width * 0.6, tier - 1) }
    }

    /** Aged-gold scrollwork: curls under the horns and a laurel-like chevron above the foot. */
    private fun filigree(g: Graphics2D, t: Theme, tier: Int) {
        listOf(1.0, -1.0).forEach { side ->
            val p = GeneralPath()
            val hx = mx(0.52 * side, PLATE)
            val hy = my(0.70, PLATE)
            p.moveTo(hx, hy)
            p.curveTo(hx - side * 6, hy + 10, hx - side * 16, hy + 8, hx - side * 14, hy + 2)
            p.curveTo(hx - side * 12, hy - 3, hx - side * 7, hy + 1, hx - side * 9, hy + 5)
            g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = Color(0, 0, 0, 160)
            g.draw(AffineTransform.getTranslateInstance(0.6, 0.8).createTransformedShape(p))
            g.color = c(t.reliefLight, 240)
            g.draw(p)
        }
        val fy = my(-0.78, PLATE)
        val chevron = GeneralPath().apply {
            moveTo(-22.0, fy - 16); quadTo(-8.0, fy - 4, 0.0, fy + 4); quadTo(8.0, fy - 4, 22.0, fy - 16)
        }
        g.stroke = BasicStroke(if (tier >= 6) 2.0f else 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = Color(0, 0, 0, 160)
        g.draw(AffineTransform.getTranslateInstance(0.6, 0.8).createTransformedShape(chevron))
        g.color = c(t.reliefLight, 240)
        g.draw(chevron)
    }

    // ---------------------------------------------------------------- preview

    /**
     * Software preview of the inventory icon, following the 667 `ObjType` sprite camera (focal 512 at 16,16 on a
     * 36 x 32 canvas; yaw, then translate by the zoom, then pitch) with a z-buffer, Lambert light from the icon light
     * direction (-50, -10, -50) and nearest texel sampling. [scale] enlarges the canvas for inspection.
     */
    fun previewIcon(model: ModelData, texture: BufferedImage?, zoom2d: Int, xan2d: Int, yan2d: Int, xof2d: Int, yof2d: Int, scale: Int, resize: Int = 128): BufferedImage {
        // The 667 client draws cache model units at 4x (the <<2 on zoom and offsets); resize is the ObjType 110-112 scale.
        val unit = 4.0 * resize / 128
        val w = 36 * scale
        val h = 32 * scale
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val zbuf = DoubleArray(w * h) { Double.MAX_VALUE }
        val zoom = zoom2d shl 2
        val yaw = yan2d * 2 * PI / 2048
        val pitch = xan2d * 2 * PI / 2048
        val minY = (model.vertexY.minOrNull() ?: 0) * unit
        val ty = sin(pitch) * zoom + (yof2d shl 2) - minY / 2.0
        val tz = cos(pitch) * zoom + (yof2d shl 2)
        val n = model.vertexCount
        val px = DoubleArray(n)
        val py = DoubleArray(n)
        val pz = DoubleArray(n)
        val wx = DoubleArray(n)
        val wy = DoubleArray(n)
        val wz = DoubleArray(n)
        for (i in 0 until n) {
            var x = model.vertexX[i] * unit
            var y = model.vertexY[i] * unit
            var z = model.vertexZ[i] * unit
            val x1 = x * cos(yaw) + z * sin(yaw)
            val z1 = -x * sin(yaw) + z * cos(yaw)
            x = x1 + (xof2d shl 2); z = z1 + tz; y += ty
            val y2 = y * cos(pitch) - z * sin(pitch)
            val z2 = y * sin(pitch) + z * cos(pitch)
            wx[i] = x; wy[i] = y2; wz[i] = z2
            px[i] = 16.0 * scale + x * 512 / z2 * scale
            py[i] = 16.0 * scale + y2 * 512 / z2 * scale
            pz[i] = z2
        }
        val light = doubleArrayOf(-50.0, -10.0, -50.0).let { v -> val l = hypot(hypot(v[0], v[1]), v[2]); DoubleArray(3) { v[it] / l } }
        for (f in 0 until model.faceCount) {
            val a = model.faceA[f]; val b = model.faceB[f]; val cIdx = model.faceC[f]
            val area = (px[b] - px[a]) * (py[cIdx] - py[a]) - (py[b] - py[a]) * (px[cIdx] - px[a])
            // Back-face cull with the winding the tool decided is front-facing (clockwise on screen, y down = positive area).
            if ((area > 0) != FRONT_CLOCKWISE) continue
            // Normal in view space.
            val ux = wx[b] - wx[a]; val uy = wy[b] - wy[a]; val uz = wz[b] - wz[a]
            val vx = wx[cIdx] - wx[a]; val vy = wy[cIdx] - wy[a]; val vz = wz[cIdx] - wz[a]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val nl = hypot(hypot(nx, ny), nz).coerceAtLeast(1e-9)
            nx /= nl; ny /= nl; nz /= nl
            val lambert = max(0.0, -(nx * light[0] + ny * light[1] + nz * light[2]))
            val intensity = 0.45 + 0.75 * lambert
            val tex = model.faceTexture?.get(f)?.toInt() ?: -1
            val base = hslToRgb(model.faceColour[f].toInt() and 0xFFFF)
            val sp = model.faceTexSpace?.get(f)?.toInt() ?: -1
            val tP = if (tex >= 0 && sp >= 0) model.texSpaceDefA!![sp].toInt() else -1
            val tM = if (tP >= 0) model.texSpaceDefB!![sp].toInt() else -1
            val tN = if (tP >= 0) model.texSpaceDefC!![sp].toInt() else -1
            val minX = minOf(px[a], px[b], px[cIdx]).toInt().coerceAtLeast(0)
            val maxX = max(px[a], max(px[b], px[cIdx])).toInt().coerceAtMost(w - 1)
            val minYp = minOf(py[a], py[b], py[cIdx]).toInt().coerceAtLeast(0)
            val maxYp = max(py[a], max(py[b], py[cIdx])).toInt().coerceAtMost(h - 1)
            for (yy in minYp..maxYp) for (xx in minX..maxX) {
                val qx = xx + 0.5; val qy = yy + 0.5
                val w0 = ((px[b] - qx) * (py[cIdx] - qy) - (py[b] - qy) * (px[cIdx] - qx)) / area
                val w1 = ((px[cIdx] - qx) * (py[a] - qy) - (py[cIdx] - qy) * (px[a] - qx)) / area
                val w2 = 1 - w0 - w1
                if (w0 < 0 || w1 < 0 || w2 < 0) continue
                val z = w0 * pz[a] + w1 * pz[b] + w2 * pz[cIdx]
                val k = yy * w + xx
                if (z >= zbuf[k]) continue
                zbuf[k] = z
                var rgb = base
                if (texture != null && tP >= 0) {
                    val mx0 = w0 * model.vertexX[a] + w1 * model.vertexX[b] + w2 * model.vertexX[cIdx]
                    val my0 = w0 * model.vertexY[a] + w1 * model.vertexY[b] + w2 * model.vertexY[cIdx]
                    val px0 = model.vertexX[tP].toDouble(); val py0 = model.vertexY[tP].toDouble()
                    val ux0 = model.vertexX[tM] - px0; val uy0 = model.vertexY[tM] - py0
                    val vx0 = model.vertexX[tN] - px0; val vy0 = model.vertexY[tN] - py0
                    val det = ux0 * vy0 - uy0 * vx0
                    val du = ((mx0 - px0) * vy0 - (my0 - py0) * vx0) / det
                    val dv = (ux0 * (my0 - py0) - uy0 * (mx0 - px0)) / det
                    val tx = (du * texture.width).toInt().coerceIn(0, texture.width - 1)
                    val tyy = (dv * texture.height).toInt().coerceIn(0, texture.height - 1)
                    rgb = texture.getRGB(tx, tyy)
                }
                fun ch(s: Int) = ((rgb shr s and 0xFF) * intensity).toInt().coerceIn(0, 255)
                img.setRGB(xx, yy, (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0))
            }
        }
        return img
    }

    /** 667 HSL16 (6-bit hue, 3-bit saturation, 7-bit lightness) to RGB, close to the client's colour table. */
    fun hslToRgb(hsl: Int): Int {
        val h = (hsl shr 10 and 0x3F) / 64.0 + 0.0078125
        val s = (hsl shr 7 and 0x7) / 8.0 + 0.0625
        val l = (hsl and 0x7F) / 128.0
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun hue(t0: Double): Double {
            var t = t0
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            return when {
                6 * t < 1 -> p + (q - p) * 6 * t
                2 * t < 1 -> q
                3 * t < 2 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        val r = (hue(h + 1.0 / 3) * 255).toInt().coerceIn(0, 255)
        val g = (hue(h) * 255).toInt().coerceIn(0, 255)
        val b = (hue(h - 1.0 / 3) * 255).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    /** Outward-facing share of an existing model's faces under the clockwise convention (probe helper). */
    fun clockwiseOutwardShare(model: ModelData): Double {
        val cx = model.vertexX.average(); val cy = model.vertexY.average(); val cz = model.vertexZ.average()
        var out = 0
        var total = 0
        for (f in 0 until model.faceCount) {
            val a = model.faceA[f]; val b = model.faceB[f]; val c = model.faceC[f]
            val ux = (model.vertexX[b] - model.vertexX[a]).toDouble(); val uy = (model.vertexY[b] - model.vertexY[a]).toDouble(); val uz = (model.vertexZ[b] - model.vertexZ[a]).toDouble()
            val vx = (model.vertexX[c] - model.vertexX[a]).toDouble(); val vy = (model.vertexY[c] - model.vertexY[a]).toDouble(); val vz = (model.vertexZ[c] - model.vertexZ[a]).toDouble()
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val mxp = (model.vertexX[a] + model.vertexX[b] + model.vertexX[c]) / 3.0 - cx
            val myp = (model.vertexY[a] + model.vertexY[b] + model.vertexY[c]) / 3.0 - cy
            val mzp = (model.vertexZ[a] + model.vertexZ[b] + model.vertexZ[c]) / 3.0 - cz
            val dot = nx * mxp + ny * myp + nz * mzp
            if (abs(dot) < 1e-6) continue
            total++
            if (dot > 0) out++
        }
        return if (total == 0) 0.0 else out.toDouble() / total
    }
}
