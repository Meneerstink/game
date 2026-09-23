package gg.rsmod.game.tools.importer

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The 78 house-style banner artwork (owner 2026-09-23): very deep royal purple fabric, rich metallic gold trim, a jewelled
 * crown and "78" in gold serif numerals, swallow-tailed. Drawn on a canvas with the cloth's real proportions
 * ([CLOTH_W] x [CLOTH_H], the banner model's 96 x 224 units) and squeezed to the square power-of-two texture the 667
 * client needs; the planar UV mapping of the banner model stretches it back.
 *
 * The swallow-tail notch lives in the model geometry; [NOTCH_APEX] (fraction of the height) is shared with the model so
 * the gold hem drawn here follows the cut edges exactly.
 */
object Banner78Art {
    const val CLOTH_W = 438
    const val CLOTH_H = 1024
    const val TEXTURE_SIZE = 256

    /** Height fraction where the side edges end; the centre point reaches 1.0 (owner reference banner). */
    const val NOTCH_APEX = 0.83

    /**
     * Owner 2026-09-24 master artwork (1122 x 1402 RGBA, transparent background), byte-exact copy of `C:\RSPS\foto\vf`.
     * Never edited; everything in-game is derived from it.
     */
    val MASTER = java.io.File("C:/RSPS/tools/ge-banner/banner78_master.png")

    /** Cloth box inside [MASTER] (alpha-measured): below the rod, the full tattered width, down to the lowest shreds. */
    private const val MASTER_X0 = 125
    private const val MASTER_X1 = 1005
    private const val MASTER_Y0 = 140
    private const val MASTER_Y1 = 1392

    /** Final texture edge; the RSPS client builds this texture at 512 on the hardware toolkits (RspsTextures). */
    const val MASTER_TEXTURE_SIZE = 512

    /** Cloth width for a cloth of height [h] model units, keeping the master's cloth proportions exactly. */
    fun clothWidthFor(h: Int): Int = Math.round(h * (MASTER_X1 - MASTER_X0).toDouble() / (MASTER_Y1 - MASTER_Y0)).toInt()

    /**
     * The master's cloth, squeezed to the square [MASTER_TEXTURE_SIZE] texture the 667 client needs (the planar UV
     * mapping on a cloth of [clothWidthFor] proportions stretches it back). Scaled in premultiplied alpha, in halving
     * steps with bicubic filtering, so edges keep no dark fringe and the skull, blades and "78" stay sharp. Colours are
     * not altered.
     */
    fun masterTexture(): BufferedImage {
        val src = javax.imageio.ImageIO.read(MASTER) ?: error("master missing: $MASTER")
        var img = BufferedImage(MASTER_X1 - MASTER_X0, MASTER_Y1 - MASTER_Y0, BufferedImage.TYPE_INT_ARGB_PRE)
        img.createGraphics().apply {
            drawImage(src.getSubimage(MASTER_X0, MASTER_Y0, MASTER_X1 - MASTER_X0, MASTER_Y1 - MASTER_Y0), 0, 0, null)
            dispose()
        }
        val target = MASTER_TEXTURE_SIZE
        while (img.width > target || img.height > target) {
            val w = maxOf(target, img.width / 2)
            val h = maxOf(target, img.height / 2)
            val next = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
            next.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(img, 0, 0, w, h, null)
                dispose()
            }
            img = next
        }
        val out = BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply {
            drawImage(img, 0, 0, null)
            dispose()
        }
        return out
    }

    /** Average colour of the visible texels as a 667 HSL16 value (materials average-colour column). */
    fun averageHsl(image: BufferedImage): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val c = image.getRGB(x, y)
            if (c ushr 24 < 128) continue
            r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; b += c and 0xFF; n++
        }
        if (n == 0L) return 0
        val hsb = Color.RGBtoHSB((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), null)
        val rf = r / n / 255.0
        val gf = g / n / 255.0
        val bf = b / n / 255.0
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val l = (max + min) / 2
        val s = if (max == min) 0.0 else (max - min) / (1 - kotlin.math.abs(2 * l - 1))
        val hue = (hsb[0] * 64).toInt().coerceIn(0, 63)
        val sat = (s * 8).toInt().coerceIn(0, 7)
        val lum = (l * 128).toInt().coerceIn(0, 127)
        return (hue shl 10) or (sat shl 7) or lum
    }

    /** Owner 2026-09-23: "onze banners zijn lelijk" - his reference artwork is the cloth when present. */
    val REFERENCE = java.io.File("C:/RSPS/tools/ge-banner/banner78_reference.png")

    /** Cloth box inside [REFERENCE] (measured: below the rod, full width, down to the centre point). */
    private const val REF_X0 = 450
    private const val REF_X1 = 998
    private const val REF_Y0 = 118
    private const val REF_Y1 = 1064

    /** The reference cloth cropped, with its flat grey backdrop (reachable from the border) turned into dark cloth. */
    fun referenceCloth(): BufferedImage? {
        if (!REFERENCE.exists()) return null
        val src = javax.imageio.ImageIO.read(REFERENCE) ?: return null
        val w = REF_X1 - REF_X0
        val h = REF_Y1 - REF_Y0
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, src.getRGB(REF_X0 + x, REF_Y0 + y))
        val bg = src.getRGB(5, 5)

        fun isBackdrop(rgb: Int): Boolean {
            val d = maxOf(kotlin.math.abs((rgb shr 16 and 0xFF) - (bg shr 16 and 0xFF)), kotlin.math.abs((rgb shr 8 and 0xFF) - (bg shr 8 and 0xFF)), kotlin.math.abs((rgb and 0xFF) - (bg and 0xFF)))
            return d < 22
        }
        val seen = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        for (x in 0 until w) { stack += x; stack += (h - 1) * w + x }
        for (y in 0 until h) { stack += y * w; stack += y * w + w - 1 }
        val fill = Color(0x18, 0x06, 0x26).rgb
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (seen[i]) continue
            seen[i] = true
            if (!isBackdrop(img.getRGB(i % w, i / w))) continue
            img.setRGB(i % w, i / w, fill)
            val x = i % w
            val y = i / w
            if (x > 0) stack += i - 1
            if (x < w - 1) stack += i + 1
            if (y > 0) stack += i - w
            if (y < h - 1) stack += i + w
        }
        return img
    }

    private val PURPLE_DEEP = Color(0x2c, 0x0a, 0x56)
    private val PURPLE = Color(0x55, 0x1c, 0x9c)
    private val PURPLE_LIGHT = Color(0x4e, 0x1f, 0x80)

    private val GOLD_STOPS = floatArrayOf(0f, 0.28f, 0.5f, 0.62f, 0.8f, 1f)
    private val GOLD_COLOURS =
        arrayOf(Color(0x9a, 0x66, 0x0c), Color(0xf2, 0xba, 0x2a), Color(0xff, 0xfa, 0xd6), Color(0xff, 0xd4, 0x4c), Color(0xd0, 0x94, 0x18), Color(0x86, 0x56, 0x08))

    private fun gold(x0: Float, y0: Float, x1: Float, y1: Float) = LinearGradientPaint(Point2D.Float(x0, y0), Point2D.Float(x1, y1), GOLD_STOPS, GOLD_COLOURS)

    /** Full-size cloth artwork (CLOTH_W x CLOTH_H). */
    fun cloth(): BufferedImage {
        val w = CLOTH_W
        val h = CLOTH_H
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        // Fabric: vertical depth gradient.
        g.paint = LinearGradientPaint(Point2D.Float(0f, 0f), Point2D.Float(0f, h.toFloat()), floatArrayOf(0f, 0.45f, 1f), arrayOf(PURPLE_DEEP, PURPLE, PURPLE_DEEP))
        g.fillRect(0, 0, w, h)
        // Soft vertical folds: three shallow waves of light across the width.
        val fold = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until w) {
            val s = sin(x.toDouble() / w * 3 * 2 * PI - 0.6)
            val a = (s * 34).toInt()
            val c = if (a >= 0) Color(0x9a, 0x6a, 0xd0, a) else Color(0, 0, 0, -a)
            for (y in 0 until h) fold.setRGB(x, y, c.rgb)
        }
        g.drawImage(fold, 0, 0, null)
        // Weave: fine alternating threads and a little noise, the texture of heavy silk.
        val rnd = Random(78)
        for (y in 0 until h) for (x in 0 until w) {
            val thread = if ((x / 2 + y / 2) % 2 == 0) 6 else -6
            val n = rnd.nextInt(-7, 8)
            val rgb = img.getRGB(x, y)
            fun ch(v: Int) = (v + thread + n).coerceIn(0, 255)
            img.setRGB(x, y, (0xFF shl 24) or (ch(rgb shr 16 and 0xFF) shl 16) or (ch(rgb shr 8 and 0xFF) shl 8) or ch(rgb and 0xFF))
        }

        val notchY = (h * NOTCH_APEX).toFloat()
        // Gold hem following the banner outline: sides, top and both swallow-tail edges.
        val outline = GeneralPath().apply {
            moveTo(0f, 0f); lineTo(w.toFloat(), 0f); lineTo(w.toFloat(), h.toFloat()); lineTo(w / 2f, notchY); lineTo(0f, h.toFloat()); closePath()
        }
        val inset = 26f
        val inner = GeneralPath().apply {
            moveTo(inset, inset * 2.2f); lineTo(w - inset, inset * 2.2f); lineTo(w - inset, h - inset * 3.2f)
            lineTo(w / 2f, notchY - inset * 1.25f); lineTo(inset, h - inset * 3.2f); closePath()
        }
        val hem = Area(outline).apply { subtract(Area(inner)) }
        g.paint = gold(0f, 0f, w.toFloat(), w * 0.35f)
        g.fill(hem)
        g.stroke = BasicStroke(3f)
        g.color = Color(0x3a, 0x22, 0x04)
        g.draw(inner)
        // Fine inner filet line.
        val filet = AffineTransform.getTranslateInstance(w / 2.0, h / 2.0).apply { scale(0.9, 0.955); translate(-w / 2.0, -h / 2.0) }.createTransformedShape(inner)
        g.stroke = BasicStroke(4f)
        g.paint = gold(0f, 0f, w.toFloat(), w * 0.25f)
        g.draw(filet)
        // Top hem band where the rod passes through.
        g.paint = GradientPaint(0f, 0f, Color(0x12, 0x04, 0x22), 0f, inset * 2.2f, Color(0x2a, 0x0b, 0x48))
        g.fillRect(0, 0, w, 18)

        // Owner 2026-09-23: no crown - crossed swords behind a large "78", a small gold ornament above and below.
        ornament(g, w / 2f, 170f)
        sword(g, w / 2f, 470f, 660f, 34.0)
        sword(g, w / 2f, 470f, 660f, -34.0)
        numerals(g, w / 2f, 605f, 360f)
        ornament(g, w / 2f, 790f)
        g.dispose()
        return img
    }

    private fun crown(g: Graphics2D, cx: Float, top: Float, width: Float) {
        val hgt = width * 0.62f
        val l = cx - width / 2
        val r = cx + width / 2
        val base = top + hgt
        val band = hgt * 0.26f
        val body = GeneralPath().apply {
            moveTo(l, base)
            lineTo(l - width * 0.03f, top + hgt * 0.22f)
            lineTo(l + width * 0.2f, top + hgt * 0.52f)
            lineTo(l + width * 0.3f, top + hgt * 0.08f)
            lineTo(cx - width * 0.1f, top + hgt * 0.48f)
            lineTo(cx, top - hgt * 0.06f)
            lineTo(cx + width * 0.1f, top + hgt * 0.48f)
            lineTo(r - width * 0.3f, top + hgt * 0.08f)
            lineTo(r - width * 0.2f, top + hgt * 0.52f)
            lineTo(r + width * 0.03f, top + hgt * 0.22f)
            lineTo(r, base)
            closePath()
        }
        val shape = Area(body).apply { add(Area(Rectangle2D.Float(l - 6, base - band * 0.35f, width + 12, band))) }
        shadow(g, shape)
        g.paint = gold(l, top, r, base)
        g.fill(shape)
        g.stroke = BasicStroke(5f)
        g.color = Color(0x33, 0x1c, 0x03)
        g.draw(shape)
        // Band highlight line and pearls on the tips.
        g.stroke = BasicStroke(3f)
        g.color = Color(0xff, 0xef, 0xb8, 170)
        g.drawLine((l + 4).toInt(), (base - band * 0.25f).toInt(), (r - 4).toInt(), (base - band * 0.25f).toInt())
        listOf(l - width * 0.03f to top + hgt * 0.22f, l + width * 0.3f to top + hgt * 0.08f, cx to top - hgt * 0.06f, r - width * 0.3f to top + hgt * 0.08f, r + width * 0.03f to top + hgt * 0.22f)
            .forEach { (x, y) -> jewel(g, x, y - 6, 15f, Color(0xf4, 0xef, 0xe0), Color(0x9c, 0x94, 0x80)) }
        // Jewels set in the band: ruby centre, sapphires either side.
        val jy = base + band * 0.3f
        jewel(g, cx, jy, 22f, Color(0xe0, 0x26, 0x3c), Color(0x6a, 0x06, 0x14))
        jewel(g, cx - width * 0.3f, jy, 16f, Color(0x3a, 0x6c, 0xe8), Color(0x0c, 0x1e, 0x62))
        jewel(g, cx + width * 0.3f, jy, 16f, Color(0x3a, 0x6c, 0xe8), Color(0x0c, 0x1e, 0x62))
    }

    /** A longsword centred on (cx, cy), [length] long, point up, tilted [degrees] from vertical. */
    private fun sword(g: Graphics2D, cx: Float, cy: Float, length: Float, degrees: Double) {
        val old = g.transform
        g.translate(cx.toDouble(), cy.toDouble())
        g.rotate(Math.toRadians(degrees))
        val half = length / 2
        val bladeW = 40f
        val bladeTop = -half
        val guardY = half * 0.46f
        val blade = GeneralPath().apply {
            moveTo(0f, bladeTop); lineTo(bladeW / 2, bladeTop + 46); lineTo(bladeW / 2, guardY); lineTo(-bladeW / 2, guardY); lineTo(-bladeW / 2, bladeTop + 46); closePath()
        }
        shadow(g, blade)
        g.paint = LinearGradientPaint(
            Point2D.Float(-bladeW / 2, 0f), Point2D.Float(bladeW / 2, 0f), floatArrayOf(0f, 0.45f, 0.5f, 1f),
            arrayOf(Color(0xb4, 0xbc, 0xcc), Color(0xff, 0xff, 0xff), Color(0xc4, 0xcc, 0xdc), Color(0x5a, 0x62, 0x74)),
        )
        g.fill(blade)
        g.stroke = BasicStroke(3f)
        g.color = Color(0x1a, 0x1c, 0x22)
        g.draw(blade)
        g.stroke = BasicStroke(2f)
        g.color = Color(0x3a, 0x3e, 0x48)
        g.drawLine(0, (bladeTop + 40).toInt(), 0, (guardY - 6).toInt())
        // Gold crossguard with curled ends, leather grip, gold pommel with a ruby.
        val guard = Area(java.awt.geom.RoundRectangle2D.Float(-58f, guardY, 116f, 16f, 12f, 12f))
        guard.add(Area(Ellipse2D.Float(-66f, guardY - 4, 20f, 24f)))
        guard.add(Area(Ellipse2D.Float(46f, guardY - 4, 20f, 24f)))
        shadow(g, guard)
        g.paint = gold(-60f, guardY, 60f, guardY + 16)
        g.fill(guard)
        g.stroke = BasicStroke(3f)
        g.color = Color(0x33, 0x1c, 0x03)
        g.draw(guard)
        val grip = Rectangle2D.Float(-9f, guardY + 16, 18f, half * 0.34f)
        g.paint = GradientPaint(-9f, 0f, Color(0x2a, 0x12, 0x06), 9f, 0f, Color(0x5a, 0x30, 0x14))
        g.fill(grip)
        g.color = Color(0x14, 0x08, 0x02)
        for (i in 0 until 6) g.drawLine(-9, (guardY + 22 + i * grip.height / 6).toInt(), 9, (guardY + 16 + i * grip.height / 6).toInt())
        val pommelY = guardY + 16 + grip.height
        val pommel = Ellipse2D.Float(-16f, pommelY - 4, 32f, 32f)
        g.paint = gold(-16f, pommelY, 16f, pommelY + 32)
        g.fill(pommel)
        g.color = Color(0x33, 0x1c, 0x03)
        g.draw(pommel)
        jewel(g, 0f, pommelY + 12, 8f, Color(0xe0, 0x26, 0x3c), Color(0x6a, 0x06, 0x14))
        g.transform = old
    }

    private fun jewel(g: Graphics2D, x: Float, y: Float, r: Float, light: Color, dark: Color) {
        val e = Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r)
        g.paint = GradientPaint(x - r, y - r, light, x + r, y + r, dark)
        g.fill(e)
        g.stroke = BasicStroke(3f)
        g.color = Color(0x3a, 0x22, 0x04)
        g.draw(e)
        g.color = Color(255, 255, 255, 200)
        g.fill(Ellipse2D.Float(x - r * 0.5f, y - r * 0.55f, r * 0.5f, r * 0.4f))
    }

    private fun numerals(g: Graphics2D, cx: Float, baseline: Float, size: Float) {
        val font = listOf("Times New Roman", "Serif").map { Font(it, Font.BOLD, size.toInt()) }.first { it.family != "Dialog" || it.name == "Serif" }
        val layout = TextLayout("78", font, g.fontRenderContext)
        val bounds = layout.bounds
        val shape = layout.getOutline(AffineTransform.getTranslateInstance((cx - bounds.width / 2 - bounds.x), baseline.toDouble()))
        shadow(g, shape)
        val b = shape.bounds2D
        g.paint = gold(b.x.toFloat(), b.y.toFloat(), b.maxX.toFloat(), b.maxY.toFloat())
        g.fill(shape)
        g.stroke = BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = Color(0x2a, 0x16, 0x02)
        g.draw(shape)
        // Inner bevel highlight: the outline shifted up-left, clipped to the glyphs.
        val clip = g.clip
        g.clip = shape
        g.stroke = BasicStroke(5f)
        g.color = Color(0xff, 0xf2, 0xc4, 150)
        g.draw(AffineTransform.getTranslateInstance(-4.0, -4.0).createTransformedShape(shape))
        g.clip = clip
    }

    private fun ornament(g: Graphics2D, cx: Float, cy: Float) {
        val s = 34f
        val diamond = GeneralPath().apply { moveTo(cx, cy - s); lineTo(cx + s * 0.7f, cy); lineTo(cx, cy + s); lineTo(cx - s * 0.7f, cy); closePath() }
        val bar = Area(Rectangle2D.Float(cx - 130, cy - 4, 260f, 8f))
        val shape = Area(diamond).apply { add(bar) }
        listOf(-1, 1).forEach { side -> shape.add(Area(Ellipse2D.Float(cx + side * 130f - 9, cy - 9, 18f, 18f))) }
        shadow(g, shape)
        g.paint = gold(cx - 130, cy - s, cx + 130, cy + s)
        g.fill(shape)
        g.stroke = BasicStroke(3f)
        g.color = Color(0x33, 0x1c, 0x03)
        g.draw(shape)
        jewel(g, cx, cy, 11f, Color(0xe0, 0x26, 0x3c), Color(0x6a, 0x06, 0x14))
    }

    private fun shadow(g: Graphics2D, shape: Shape) {
        g.color = Color(0, 0, 0, 110)
        g.fill(AffineTransform.getTranslateInstance(6.0, 8.0).createTransformedShape(shape))
    }

    /** The texture: the cloth squeezed into [TEXTURE_SIZE] x [TEXTURE_SIZE], fully opaque. */
    fun texture(): BufferedImage {
        val cloth = referenceCloth() ?: cloth()
        val out = BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(cloth, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, null)
        g.dispose()
        // The 667 lighting darkens textured faces; lift the whole texture ~15% so the gold still reads as gold in-game.
        for (y in 0 until TEXTURE_SIZE) for (x in 0 until TEXTURE_SIZE) {
            val rgb = out.getRGB(x, y)
            fun lift(v: Int) = (v * 1.15 + 6).toInt().coerceAtMost(255)
            out.setRGB(x, y, (0xFF shl 24) or (lift(rgb shr 16 and 0xFF) shl 16) or (lift(rgb shr 8 and 0xFF) shl 8) or lift(rgb and 0xFF))
        }
        return out
    }
}
