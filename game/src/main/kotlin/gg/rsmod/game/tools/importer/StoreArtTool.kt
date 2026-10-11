package gg.rsmod.game.tools.importer

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * The 78 Store's own art (owner 2026-09-22: "redesign the shop they need to look special ... when someone is thinking maybe i
 * buy donator he opens the shop he sees its beautifull use the highest quality visuals").
 *
 * The revision-667 interface system only draws flat rectangles, lines, text in the bitmap fonts and sprites, which is why
 * the first store looked plain however it was arranged. Every visual here is therefore painted once, at full quality, with
 * Java2D (antialiased shapes and type, gradients, glows, bevels) and stored as ordinary 667 sprites: an indexed image of up
 * to 255 colours plus a per-pixel alpha channel (`IndexedImage.load`; interface graphics are always created with alpha,
 * `Component.sprite`). Colours are reduced by median cut with Floyd-Steinberg error diffusion, so gradients stay smooth.
 *
 * Nothing is taken from another game: the art is drawn from shapes in this file. Sprite ids start at [BASE], the first
 * free sprite group in both caches (probe 2026-09-22: highest group 7940), and are written through [CacheTransaction].
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.StoreArtTool [--png=<dir>]` (dry run / preview; the art is written
 * together with its window by `StoreInterfaceImportTool --apply`)
 */
object StoreArtTool {
    const val BASE = 7941

    /** Full window backgrounds, one per shop theme (tab order). */
    const val BACKGROUND_FIRST = BASE // 7941..7943
    const val TAB_NORMAL_FIRST = BASE + 3 // 7944..7946
    const val TAB_SELECTED_FIRST = BASE + 6 // 7947..7949
    const val SLOT = BASE + 9
    const val SLOT_GLOW = BASE + 10
    const val SLOT_RING = BASE + 11
    const val BUY = BASE + 12
    const val BUY_CONFIRM = BASE + 13
    const val ICON_FIRST = BASE + 14 // 7955..7957 currency icons in tab order
    const val ARROW_LEFT = BASE + 17
    const val ARROW_RIGHT = BASE + 18
    const val CLOSE = BASE + 19
    const val COUNT = 20

    /**
     * Category buttons (owner 2026-10-10): one lit button per shop theme plus one shared unlit button. The 7941.. block ends at
     * 7962 (7961/7962 belong to another tool), so these start in the free 7963..8099 gap (probe 2026-10-10, both caches).
     */
    const val SECTION_BASE = 8060
    const val SECTION_NORMAL = SECTION_BASE
    const val SECTION_SELECTED_FIRST = SECTION_BASE + 1 // 8061..8063 in tab order
    const val SECTION_SPRITE_COUNT = 4

    /** Slot frames by tier (owner 2026-10-10 "luxer"): Premium green, Elite blue, Prestige purple; Basic and untiered use [SLOT]. */
    const val SLOT_TIER_FIRST = SECTION_BASE + SECTION_SPRITE_COUNT // 8064..8066
    val SLOT_TIER_COLOURS = listOf(Color(0x3CD33C), Color(0x3FA7FF), Color(0xC77DFF))

    /**
     * Prev / Next page buttons (owner 2026-10-11: "make the prev page and next page button in the stores nice"): a glossy pill
     * with a gold arrow and its label painted in, and a dimmed state for the first / last page. 8067 Prev, 8068 Next, 8069 Prev
     * dimmed, 8070 Next dimmed.
     */
    const val PAGE_BUTTON_FIRST = SLOT_TIER_FIRST + 3 // 8067..8070
    const val PAGE_BUTTON_WIDTH = 70
    const val PAGE_BUTTON_HEIGHT = 18

    // ---- geometry shared with StoreInterfaceImportTool ------------------------------------------------------------

    // Owner 2026-10-03 (bug 210545): "can be a bit bigger like 8 procent bigger visually also make it shows more items on one
    // page" (488x330 -> 527x360, 6x5 -> 7x6 grid). Owner 2026-10-10 ("check alle stores ... betere leesbaarheid", then "kan veel
    // beter"): 660x470 in the resizable game view (owner choice; the 512x334 fixed viewport clips it), a category row under the
    // shop tabs, a 7x7 grid, a tall showcase for a big model and a roomy card where every fact has its own row.
    // Owner 2026-10-11 ("stores moeten niet overlappen zeker niet in fixed mode", then "bank-formaat"): 512x334, the bank's
    // size - it opens in the game view's modal slot like the bank (fixed 548 and resizable 746), so it never covers the chat,
    // the tabs or the minimap, in either mode and with Stretched Mode. 6x5 grid (30 a page), every card fact on its own row.
    const val WIDTH = 512
    const val HEIGHT = 334
    const val HEADER_SHIFT = -12
    const val TITLE_SIZE = 18f
    const val TAB_Y = 37
    const val TAB_WIDTH = 158
    const val TAB_HEIGHT = 22
    const val TAB_X = 12
    const val TAB_PITCH = 164

    /** Category buttons (All / Kits / Gear / Pets / Capes / Cosmetics) under the shop tabs. */
    const val SECTION_Y = 62
    const val SECTION_X = 12
    const val SECTION_WIDTH = 78
    const val SECTION_HEIGHT = 18
    const val SECTION_PITCH = 82
    const val SECTION_COUNT = 6
    const val STRIP_X = 12
    const val STRIP_Y = 83
    const val STRIP_WIDTH = 488
    const val STRIP_HEIGHT = 18
    const val GRID_X = 12
    const val GRID_Y = 104

    /** Slot pitch: a 36 px slot and a 2 px gap. */
    const val SLOT_PITCH = 38
    const val GRID_WIDTH = 6 * SLOT_PITCH + 4
    const val GRID_HEIGHT = 5 * SLOT_PITCH + 4

    /** Page bar under the grid (Prev / page / Next), level with the Buy button. */
    const val NAV_Y = 302
    const val NAV_HEIGHT = 22
    const val SHOW_X = 252
    const val SHOW_Y = 104
    const val SHOW_WIDTH = 248
    const val SHOW_HEIGHT = 88
    const val CARD_X = 252
    const val CARD_Y = 195
    const val CARD_WIDTH = 248
    const val CARD_HEIGHT = 104

    /** The lit price band at the bottom of the card. */
    const val PRICE_BAND_HEIGHT = 20
    const val SLOT_SIZE = 36
    const val BUY_WIDTH = 170
    const val BUY_HEIGHT = 22
    const val ICON_SIZE = 16
    const val ARROW_WIDTH = 14
    const val ARROW_HEIGHT = 24
    const val CLOSE_SIZE = 20

    class Theme(
        val title: String,
        val tab: String,
        val bgTop: Color,
        val bgBottom: Color,
        val accentLight: Color,
        val accentDark: Color,
        val glow: Color,
    )

    val THEMES =
        listOf(
            Theme("DONATOR STORE", "Donator", Color(0x2A1846), Color(0x0D0719), Color(0xFFE08A), Color(0xB07A1C), Color(0xFFCF5A)),
            Theme("DEADMAN STORE", "Deadman", Color(0x3A0E12), Color(0x0E0406), Color(0xFF6A6A), Color(0x8A1016), Color(0xFF3B3B)),
            Theme("LOYALTY STORE", "Loyalty", Color(0x0F2A45), Color(0x050E1A), Color(0x7FD6FF), Color(0x1B66A8), Color(0x4CC2FF)),
        )

    private val GOLD_HI = Color(0xFFF1B8)
    private val GOLD = Color(0xE9BE58)
    private val GOLD_MID = Color(0xC8932F)
    private val GOLD_DARK = Color(0x6E4A12)

    // ---- painting helpers -----------------------------------------------------------------------------------------

    private fun canvas(
        w: Int,
        h: Int,
        paint: (Graphics2D) -> Unit,
    ): BufferedImage {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        paint(g)
        g.dispose()
        return image
    }

    private fun alpha(
        c: Color,
        a: Double,
    ) = Color(c.red, c.green, c.blue, (a * 255).toInt().coerceIn(0, 255))

    private fun round(
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        r: Double,
    ) = RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2)

    private fun vertical(
        y0: Double,
        y1: Double,
        vararg stops: Pair<Float, Color>,
    ) = LinearGradientPaint(
        Point2D.Double(0.0, y0),
        Point2D.Double(0.0, y1),
        stops.map { it.first }.toFloatArray(),
        stops.map { it.second }.toTypedArray(),
    )

    private fun goldVertical(
        y0: Double,
        y1: Double,
    ) = vertical(y0, y1, 0f to GOLD_HI, 0.35f to GOLD, 0.7f to GOLD_MID, 1f to GOLD_DARK)

    private fun font(
        size: Float,
        bold: Boolean = true,
    ): Font {
        val names = listOf("Georgia", "Palatino Linotype", "Cambria", "Serif")
        val available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val name = names.firstOrNull { it in available } ?: "Serif"
        return Font(name, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size)
    }

    /** Text as a shape, centred on (cx, baseline). */
    private fun textShape(
        g: Graphics2D,
        text: String,
        font: Font,
        cx: Double,
        baseline: Double,
        tracking: Float = 0f,
    ): Shape {
        val attributed = font.deriveFont(mapOf(java.awt.font.TextAttribute.TRACKING to tracking))
        val layout = TextLayout(text, attributed, g.fontRenderContext)
        val bounds = layout.bounds
        return layout.getOutline(AffineTransform.getTranslateInstance(cx - bounds.width / 2 - bounds.x, baseline))
    }

    /** A soft glow behind [shape]: widening translucent strokes. */
    private fun glow(
        g: Graphics2D,
        shape: Shape,
        colour: Color,
        radius: Int,
        strength: Double,
    ) {
        for (i in radius downTo 1) {
            g.color = alpha(colour, strength * (1.0 - i.toDouble() / (radius + 1)) / radius * 2.2)
            g.stroke = BasicStroke(i * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(shape)
        }
    }

    // ---- icons ----------------------------------------------------------------------------------------------------

    private fun crown(
        cx: Double,
        cy: Double,
        s: Double,
    ): Shape {
        val p = GeneralPath()
        p.moveTo(cx - s, cy + s * 0.55)
        p.lineTo(cx - s * 1.05, cy - s * 0.45)
        p.lineTo(cx - s * 0.5, cy + s * 0.05)
        p.lineTo(cx, cy - s * 0.75)
        p.lineTo(cx + s * 0.5, cy + s * 0.05)
        p.lineTo(cx + s * 1.05, cy - s * 0.45)
        p.lineTo(cx + s, cy + s * 0.55)
        p.closePath()
        val a = Area(p)
        a.add(Area(RoundRectangle2D.Double(cx - s, cy + s * 0.62, s * 2, s * 0.32, s * 0.2, s * 0.2)))
        for (dx in listOf(-1.05, 0.0, 1.05)) {
            val dy = if (dx == 0.0) -0.75 else -0.45
            a.add(Area(Ellipse2D.Double(cx + dx * s - s * 0.17, cy + dy * s - s * 0.3, s * 0.34, s * 0.34)))
        }
        return a
    }

    private fun skull(
        cx: Double,
        cy: Double,
        s: Double,
    ): Shape {
        val a = Area(Ellipse2D.Double(cx - s, cy - s * 1.0, s * 2, s * 1.7))
        a.add(Area(RoundRectangle2D.Double(cx - s * 0.62, cy + s * 0.3, s * 1.24, s * 0.72, s * 0.3, s * 0.3)))
        a.subtract(Area(Ellipse2D.Double(cx - s * 0.68, cy - s * 0.35, s * 0.52, s * 0.56)))
        a.subtract(Area(Ellipse2D.Double(cx + s * 0.16, cy - s * 0.35, s * 0.52, s * 0.56)))
        val nose = GeneralPath()
        nose.moveTo(cx, cy + s * 0.18)
        nose.lineTo(cx - s * 0.14, cy + s * 0.42)
        nose.lineTo(cx + s * 0.14, cy + s * 0.42)
        nose.closePath()
        a.subtract(Area(nose))
        for (dx in listOf(-0.3, 0.0, 0.3)) a.subtract(Area(java.awt.geom.Rectangle2D.Double(cx + dx * s - s * 0.05, cy + s * 0.62, s * 0.1, s * 0.4)))
        return a
    }

    private fun star(
        cx: Double,
        cy: Double,
        r: Double,
    ): Shape {
        val p = GeneralPath()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.45
            val angle = -Math.PI / 2 + i * Math.PI / 5
            val x = cx + Math.cos(angle) * radius
            val y = cy + Math.sin(angle) * radius
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.closePath()
        return p
    }

    private fun gem(
        cx: Double,
        cy: Double,
        s: Double,
    ): Shape {
        val p = GeneralPath()
        p.moveTo(cx - s, cy - s * 0.35)
        p.lineTo(cx - s * 0.55, cy - s * 0.85)
        p.lineTo(cx + s * 0.55, cy - s * 0.85)
        p.lineTo(cx + s, cy - s * 0.35)
        p.lineTo(cx, cy + s)
        p.closePath()
        return p
    }

    private fun emblem(
        themeIndex: Int,
        cx: Double,
        cy: Double,
        s: Double,
    ): Shape =
        when (themeIndex) {
            0 -> crown(cx, cy, s)
            1 -> skull(cx, cy, s)
            else -> star(cx, cy, s * 1.15)
        }

    // ---- the art --------------------------------------------------------------------------------------------------

    /** Gold bevelled rounded frame around [shape] of width [w]. */
    private fun goldBorder(
        g: Graphics2D,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        r: Double,
        width: Float,
    ) {
        g.paint = goldVertical(y, y + h)
        g.stroke = BasicStroke(width)
        g.draw(round(x, y, w, h, r))
        g.color = alpha(GOLD_HI, 0.55)
        g.stroke = BasicStroke(0.8f)
        g.draw(round(x - width / 2 + 0.6, y - width / 2 + 0.6, w + width - 1.2, h + width - 1.2, r + width / 2))
        g.color = alpha(Color.BLACK, 0.6)
        g.draw(round(x + width / 2 + 0.4, y + width / 2 + 0.4, w - width - 0.8, h - width - 0.8, (r - width / 2).coerceAtLeast(1.0)))
    }

    /** A dark glass inset panel. */
    private fun inset(
        g: Graphics2D,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        r: Double,
        edge: Color,
        darkness: Double = 0.5,
    ) {
        val shape = round(x, y, w, h, r)
        g.paint = vertical(y, y + h, 0f to alpha(Color.BLACK, darkness * 0.75), 1f to alpha(Color.BLACK, darkness))
        g.fill(shape)
        // inner top shadow and bottom sheen
        g.paint = vertical(y, y + 10, 0f to alpha(Color.BLACK, 0.35), 1f to alpha(Color.BLACK, 0.0))
        g.fill(shape)
        g.color = alpha(Color.WHITE, 0.07)
        g.stroke = BasicStroke(1f)
        g.draw(java.awt.geom.Line2D.Double(x + r, y + h - 1.5, x + w - r, y + h - 1.5))
        g.color = edge
        g.stroke = BasicStroke(1.2f)
        g.draw(shape)
    }

    fun background(themeIndex: Int): BufferedImage {
        val t = THEMES[themeIndex]
        return canvas(WIDTH, HEIGHT) { g ->
            val w = WIDTH.toDouble()
            val h = HEIGHT.toDouble()
            val body = round(1.0, 1.0, w - 2, h - 2, 16.0)
            // Body: deep themed gradient, a warm light from the header and a vignette.
            g.paint = vertical(0.0, h, 0f to t.bgTop, 0.55f to t.bgBottom.brighter(), 1f to t.bgBottom)
            g.fill(body)
            g.clip = body
            g.paint = RadialGradientPaint(Point2D.Double(w / 2, 20.0), 330f, floatArrayOf(0f, 1f), arrayOf(alpha(t.glow, 0.28), alpha(t.glow, 0.0)))
            g.fill(body)
            g.paint = RadialGradientPaint(
                Point2D.Double(w / 2, h / 2),
                w.toFloat() * 0.75f,
                floatArrayOf(0.55f, 1f),
                arrayOf(alpha(Color.BLACK, 0.0), alpha(Color.BLACK, 0.55)),
            )
            g.fill(body)
            // Faint diagonal light streaks.
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.05f)
            g.color = Color.WHITE
            for (i in 0 until 7) {
                val x0 = 40.0 + i * 120
                val p = GeneralPath()
                p.moveTo(x0, 0.0)
                p.lineTo(x0 + 34, 0.0)
                p.lineTo(x0 - 90, h)
                p.lineTo(x0 - 124, h)
                p.closePath()
                g.fill(p)
            }
            g.composite = AlphaComposite.SrcOver
            g.clip = null

            // Header: emblem, title in engraved gold with a glow, flanking ornaments and a jewelled divider.
            val titleFont = font(TITLE_SIZE)
            val titleShape = textShape(g, t.title, titleFont, w / 2 + 12, 34.0 + HEADER_SHIFT, 0.08f)
            val tb = titleShape.bounds2D
            val emblemShape = emblem(themeIndex, tb.minX - 18, 27.0 + HEADER_SHIFT, 7.5)
            glow(g, titleShape, t.glow, 7, 0.5)
            glow(g, emblemShape, t.glow, 6, 0.55)
            g.color = alpha(Color.BLACK, 0.7)
            g.fill(AffineTransform.getTranslateInstance(1.5, 2.0).createTransformedShape(titleShape))
            g.fill(AffineTransform.getTranslateInstance(1.5, 2.0).createTransformedShape(emblemShape))
            g.paint = goldVertical(tb.minY, tb.maxY)
            g.fill(titleShape)
            g.fill(emblemShape)
            g.color = alpha(GOLD_DARK, 0.9)
            g.stroke = BasicStroke(0.8f)
            g.draw(titleShape)
            g.draw(emblemShape)
            // Flourishes left and right of the title.
            for (side in listOf(-1, 1)) {
                val start = if (side < 0) tb.minX - 38 else tb.maxX + 14
                val end = if (side < 0) 42.0 else w - 70
                g.paint = LinearGradientPaint(
                    Point2D.Double(start, 0.0),
                    Point2D.Double(end, 0.0),
                    floatArrayOf(0f, 1f),
                    arrayOf(alpha(GOLD, 0.95), alpha(GOLD, 0.0)),
                )
                g.stroke = BasicStroke(1.4f)
                g.draw(java.awt.geom.Line2D.Double(start, 27.0 + HEADER_SHIFT, end, 27.0 + HEADER_SHIFT))
                g.stroke = BasicStroke(0.8f)
                g.draw(java.awt.geom.Line2D.Double(start, 31.0 + HEADER_SHIFT, end + side * -30, 31.0 + HEADER_SHIFT))
                val d = gem(start, 29.0 + HEADER_SHIFT, 3.2)
                g.paint = vertical(24.0 + HEADER_SHIFT, 33.0 + HEADER_SHIFT, 0f to t.accentLight, 1f to t.accentDark)
                g.fill(d)
                g.color = GOLD
                g.stroke = BasicStroke(0.7f)
                g.draw(d)
            }
            // Divider under the header.
            g.paint = LinearGradientPaint(
                Point2D.Double(20.0, 0.0),
                Point2D.Double(w - 20, 0.0),
                floatArrayOf(0f, 0.5f, 1f),
                arrayOf(alpha(GOLD, 0.0), GOLD, alpha(GOLD, 0.0)),
            )
            g.stroke = BasicStroke(1.2f)
            g.draw(java.awt.geom.Line2D.Double(20.0, 45.5 + HEADER_SHIFT, w - 20, 45.5 + HEADER_SHIFT))
            val centreGem = gem(w / 2, 45.5 + HEADER_SHIFT, 4.5)
            g.paint = vertical(40.0 + HEADER_SHIFT, 50.0 + HEADER_SHIFT, 0f to t.accentLight, 1f to t.accentDark)
            g.fill(centreGem)
            g.color = GOLD_HI
            g.stroke = BasicStroke(0.8f)
            g.draw(centreGem)

            // Panels painted into the window so the components only carry text, items and buttons.
            inset(g, STRIP_X.toDouble(), STRIP_Y.toDouble(), STRIP_WIDTH.toDouble(), STRIP_HEIGHT.toDouble(), 6.0, alpha(t.accentLight, 0.45), 0.45)
            inset(g, GRID_X.toDouble(), GRID_Y.toDouble(), GRID_WIDTH.toDouble(), GRID_HEIGHT.toDouble(), 9.0, alpha(GOLD, 0.45), 0.55)
            inset(g, GRID_X.toDouble(), NAV_Y.toDouble(), GRID_WIDTH.toDouble(), NAV_HEIGHT.toDouble(), 7.0, alpha(GOLD, 0.35), 0.5)
            showcase(g, t)
            card(g, t)

            // Outer gold frame with jewelled corners.
            goldBorder(g, 3.5, 3.5, w - 7, h - 7, 14.0, 5f)
            for ((cx, cy) in listOf(12.5 to 12.5, w - 12.5 to 12.5, 12.5 to h - 12.5, w - 12.5 to h - 12.5)) {
                val corner = gem(cx, cy, 4.8)
                glow(g, corner, t.glow, 4, 0.5)
                g.paint = vertical(cy - 6, cy + 6, 0f to t.accentLight, 1f to t.accentDark)
                g.fill(corner)
                g.color = GOLD_HI
                g.stroke = BasicStroke(0.9f)
                g.draw(corner)
            }
        }
    }

    /**
     * The item card (owner 2026-10-10 "betere leesbaarheid"): a near-black glass panel so the bitmap text has full contrast, a thin
     * accent rule under the name and a lit price band at the bottom in the theme colour.
     */
    private fun card(
        g: Graphics2D,
        t: Theme,
    ) {
        val x = CARD_X.toDouble()
        val y = CARD_Y.toDouble()
        val w = CARD_WIDTH.toDouble()
        val h = CARD_HEIGHT.toDouble()
        inset(g, x, y, w, h, 8.0, alpha(t.accentLight, 0.45), 0.78)
        // accent rule under the item name
        g.paint = LinearGradientPaint(
            Point2D.Double(x + 8, 0.0),
            Point2D.Double(x + w - 8, 0.0),
            floatArrayOf(0f, 0.5f, 1f),
            arrayOf(alpha(t.accentLight, 0.0), alpha(t.accentLight, 0.75), alpha(t.accentLight, 0.0)),
        )
        g.stroke = BasicStroke(1f)
        g.draw(java.awt.geom.Line2D.Double(x + 8, y + 21.5, x + w - 8, y + 21.5))
        // price band
        val by = y + h - PRICE_BAND_HEIGHT - 3
        val band = round(x + 4, by, w - 8, PRICE_BAND_HEIGHT.toDouble(), 6.0)
        g.paint = LinearGradientPaint(
            Point2D.Double(x, 0.0),
            Point2D.Double(x + w, 0.0),
            floatArrayOf(0f, 0.6f, 1f),
            arrayOf(alpha(t.glow, 0.34), alpha(t.glow, 0.12), alpha(t.glow, 0.02)),
        )
        g.fill(band)
        g.paint = vertical(by, by + 8, 0f to alpha(Color.WHITE, 0.10), 1f to alpha(Color.WHITE, 0.0))
        g.fill(band)
        g.color = alpha(GOLD, 0.55)
        g.stroke = BasicStroke(1f)
        g.draw(band)
    }

    /**
     * The preview stage. Owner 2026-10-10 ("lichtstralen storen"): no light rays - a dark stage with one soft halo in the theme
     * colour behind the item, so the item itself is what catches the eye, and a glowing pedestal under it.
     */
    private fun showcase(
        g: Graphics2D,
        t: Theme,
    ) {
        val x = SHOW_X.toDouble()
        val y = SHOW_Y.toDouble()
        val w = SHOW_WIDTH.toDouble()
        val h = SHOW_HEIGHT.toDouble()
        val shape = round(x, y, w, h, 9.0)
        g.paint = vertical(y, y + h, 0f to alpha(Color.BLACK, 0.62), 1f to alpha(Color.BLACK, 0.8))
        g.fill(shape)
        val old = g.clip
        g.clip = shape
        // one soft halo behind the item
        g.paint = RadialGradientPaint(
            Point2D.Double(x + w / 2, y + h * 0.46),
            (h * 0.62).toFloat(),
            floatArrayOf(0f, 0.6f, 1f),
            arrayOf(alpha(t.glow, 0.26), alpha(t.glow, 0.08), alpha(t.glow, 0.0)),
        )
        g.fill(shape)
        // pedestal
        val pedestal = Ellipse2D.Double(x + w / 2 - 70, y + h - 20, 140.0, 13.0)
        g.paint = RadialGradientPaint(
            Point2D.Double(x + w / 2, y + h - 13),
            76f,
            floatArrayOf(0f, 1f),
            arrayOf(alpha(t.glow, 0.55), alpha(t.glow, 0.0)),
        )
        g.fill(Ellipse2D.Double(x + w / 2 - 90, y + h - 28, 180.0, 30.0))
        g.paint = vertical(y + h - 20, y + h - 7, 0f to alpha(t.bgTop.brighter(), 0.95), 1f to alpha(Color.BLACK, 0.95))
        g.fill(pedestal)
        g.paint = goldVertical(y + h - 20, y + h - 7)
        g.stroke = BasicStroke(1.4f)
        g.draw(pedestal)
        g.clip = old
        g.color = alpha(GOLD, 0.7)
        g.stroke = BasicStroke(1.2f)
        g.draw(shape)
        g.color = alpha(GOLD_HI, 0.25)
        g.stroke = BasicStroke(0.8f)
        g.draw(round(x + 2, y + 2, w - 4, h - 4, 7.0))
    }

    fun tab(
        themeIndex: Int,
        selected: Boolean,
    ): BufferedImage {
        val t = THEMES[themeIndex]
        return canvas(TAB_WIDTH, TAB_HEIGHT) { g ->
            val w = TAB_WIDTH.toDouble()
            val h = TAB_HEIGHT.toDouble()
            val shape = round(1.0, 1.0, w - 2, h - 2, 8.0)
            if (selected) {
                g.paint = vertical(0.0, h, 0f to t.accentLight, 0.5f to t.accentDark.brighter(), 1f to t.accentDark)
                g.fill(shape)
                g.paint = vertical(0.0, h / 2, 0f to alpha(Color.WHITE, 0.45), 1f to alpha(Color.WHITE, 0.05))
                g.fill(round(3.0, 2.5, w - 6, h / 2 - 1, 6.0))
                g.paint = goldVertical(0.0, h)
                g.stroke = BasicStroke(1.8f)
                g.draw(shape)
            } else {
                g.paint = vertical(0.0, h, 0f to Color(0x2E2A36), 1f to Color(0x121016))
                g.fill(shape)
                g.paint = vertical(0.0, h / 2, 0f to alpha(Color.WHITE, 0.10), 1f to alpha(Color.WHITE, 0.0))
                g.fill(round(3.0, 2.5, w - 6, h / 2 - 1, 6.0))
                g.color = alpha(GOLD_MID, 0.75)
                g.stroke = BasicStroke(1.1f)
                g.draw(shape)
            }
            val label = textShape(g, t.tab, font(12f), w / 2 + 9, h / 2 + 4.5, 0.04f)
            val lb = label.bounds2D
            val icon = emblem(themeIndex, lb.minX - 12, h / 2, 5.5)
            g.color = alpha(Color.BLACK, if (selected) 0.55 else 0.8)
            g.fill(AffineTransform.getTranslateInstance(1.0, 1.2).createTransformedShape(label))
            g.fill(AffineTransform.getTranslateInstance(1.0, 1.2).createTransformedShape(icon))
            if (selected) {
                g.color = Color.WHITE
                g.fill(label)
                g.paint = goldVertical(h / 2 - 6, h / 2 + 6)
                g.fill(icon)
            } else {
                g.paint = vertical(lb.minY, lb.maxY, 0f to Color(0xF0DDA0), 1f to Color(0xB89548))
                g.fill(label)
                g.fill(icon)
            }
        }
    }

    /** A category button: glossy theme-lit pill when [themeIndex] is selected, a dark glass pill (shared) otherwise. */
    fun section(themeIndex: Int?): BufferedImage =
        canvas(SECTION_WIDTH, SECTION_HEIGHT) { g ->
            val w = SECTION_WIDTH.toDouble()
            val h = SECTION_HEIGHT.toDouble()
            val shape = round(1.0, 1.0, w - 2, h - 2, (h - 2) / 2)
            if (themeIndex != null) {
                val t = THEMES[themeIndex]
                glow(g, shape, t.glow, 2, 0.35)
                g.paint = vertical(0.0, h, 0f to t.accentLight, 0.55f to t.accentDark.brighter(), 1f to t.accentDark)
                g.fill(shape)
                g.paint = vertical(0.0, h / 2, 0f to alpha(Color.WHITE, 0.5), 1f to alpha(Color.WHITE, 0.06))
                g.fill(round(4.0, 2.0, w - 8, h / 2 - 1, (h / 2 - 1) / 2))
                g.paint = goldVertical(0.0, h)
                g.stroke = BasicStroke(1.5f)
                g.draw(shape)
            } else {
                g.paint = vertical(0.0, h, 0f to Color(0x2A2632), 1f to Color(0x0E0C12))
                g.fill(shape)
                g.paint = vertical(0.0, h / 2, 0f to alpha(Color.WHITE, 0.10), 1f to alpha(Color.WHITE, 0.0))
                g.fill(round(4.0, 2.0, w - 8, h / 2 - 1, (h / 2 - 1) / 2))
                g.color = alpha(GOLD_MID, 0.7)
                g.stroke = BasicStroke(1f)
                g.draw(shape)
            }
        }

    fun slot(): BufferedImage =
        canvas(SLOT_SIZE, SLOT_SIZE) { g ->
            val s = SLOT_SIZE.toDouble()
            val shape = round(0.5, 0.5, s - 1, s - 1, 6.0)
            g.paint = vertical(0.0, s, 0f to Color(0x2C2934), 1f to Color(0x121016))
            g.fill(shape)
            g.paint = RadialGradientPaint(Point2D.Double(s / 2, s / 2), (s * 0.55).toFloat(), floatArrayOf(0f, 1f), arrayOf(alpha(Color.WHITE, 0.07), alpha(Color.WHITE, 0.0)))
            g.fill(shape)
            g.paint = vertical(0.0, 6.0, 0f to alpha(Color.BLACK, 0.4), 1f to alpha(Color.BLACK, 0.0))
            g.fill(shape)
            g.color = alpha(GOLD_MID, 0.55)
            g.stroke = BasicStroke(1f)
            g.draw(shape)
            g.color = alpha(Color.WHITE, 0.08)
            g.draw(java.awt.geom.Line2D.Double(6.0, s - 2.0, s - 6, s - 2.0))
        }

    /** A slot of a tiered item: the glass slot lit from below in the tier colour, with a tier-coloured rim and corner gem. */
    fun slotTier(colour: Color): BufferedImage =
        canvas(SLOT_SIZE, SLOT_SIZE) { g ->
            val s = SLOT_SIZE.toDouble()
            val shape = round(0.5, 0.5, s - 1, s - 1, 6.0)
            g.paint = vertical(0.0, s, 0f to Color(0x2C2934), 1f to Color(0x121016))
            g.fill(shape)
            g.paint = RadialGradientPaint(Point2D.Double(s / 2, s * 0.85), (s * 0.75).toFloat(), floatArrayOf(0f, 1f), arrayOf(alpha(colour, 0.32), alpha(colour, 0.0)))
            g.fill(shape)
            g.paint = vertical(0.0, 6.0, 0f to alpha(Color.BLACK, 0.4), 1f to alpha(Color.BLACK, 0.0))
            g.fill(shape)
            g.color = alpha(colour, 0.85)
            g.stroke = BasicStroke(1.2f)
            g.draw(shape)
            val corner = gem(s - 6.0, 6.0, 3.0)
            g.paint = vertical(2.0, 9.0, 0f to colour.brighter(), 1f to colour.darker())
            g.fill(corner)
            g.color = alpha(Color.WHITE, 0.6)
            g.stroke = BasicStroke(0.6f)
            g.draw(corner)
        }

    /** A page button: "Prev" with a left arrow or "Next" with a right arrow, lit when it can be used, dimmed when it cannot. */
    fun pageButton(
        next: Boolean,
        enabled: Boolean,
    ): BufferedImage =
        canvas(PAGE_BUTTON_WIDTH, PAGE_BUTTON_HEIGHT) { g ->
            val w = PAGE_BUTTON_WIDTH.toDouble()
            val h = PAGE_BUTTON_HEIGHT.toDouble()
            if (!enabled) g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f)
            val shape = round(1.0, 1.0, w - 2, h - 2, (h - 2) / 2)
            g.paint = vertical(0.0, h, 0f to Color(0x3A3346), 0.5f to Color(0x221E2A), 1f to Color(0x100E14))
            g.fill(shape)
            g.paint = vertical(0.0, h / 2, 0f to alpha(Color.WHITE, 0.22), 1f to alpha(Color.WHITE, 0.02))
            g.fill(round(4.0, 2.0, w - 8, h / 2 - 1, (h / 2 - 1) / 2))
            g.paint = goldVertical(0.0, h)
            g.stroke = BasicStroke(1.4f)
            g.draw(shape)
            // arrow: a small gold chevron on the outer side
            val ax = if (next) w - 16 else 16.0
            val dir = if (next) 1 else -1
            val arrow = GeneralPath()
            arrow.moveTo(ax - dir * 4.5, h / 2 - 6)
            arrow.lineTo(ax + dir * 3.5, h / 2)
            arrow.lineTo(ax - dir * 4.5, h / 2 + 6)
            arrow.lineTo(ax - dir * 1.5, h / 2)
            arrow.closePath()
            if (enabled) glow(g, arrow, GOLD_HI, 2, 0.5)
            g.paint = goldVertical(h / 2 - 6, h / 2 + 6)
            g.fill(arrow)
            // label beside the arrow
            val label = textShape(g, if (next) "Next" else "Prev", font(11f), if (next) w / 2 - 6 else w / 2 + 6, h / 2 + 4, 0.04f)
            g.color = alpha(Color.BLACK, 0.7)
            g.fill(AffineTransform.getTranslateInstance(1.0, 1.2).createTransformedShape(label))
            val lb = label.bounds2D
            g.paint = vertical(lb.minY, lb.maxY, 0f to Color(0xFFF1B8), 1f to Color(0xD9A845))
            g.fill(label)
        }

    fun slotGlow(): BufferedImage =
        canvas(SLOT_SIZE, SLOT_SIZE) { g ->
            val s = SLOT_SIZE.toDouble()
            val shape = round(0.5, 0.5, s - 1, s - 1, 6.0)
            g.paint = RadialGradientPaint(Point2D.Double(s / 2, s / 2), (s * 0.62).toFloat(), floatArrayOf(0f, 0.6f, 1f), arrayOf(alpha(GOLD_HI, 0.55), alpha(GOLD, 0.28), alpha(GOLD_MID, 0.12)))
            g.fill(shape)
        }

    fun slotRing(): BufferedImage =
        canvas(SLOT_SIZE, SLOT_SIZE) { g ->
            val s = SLOT_SIZE.toDouble()
            glow(g, round(3.0, 3.0, s - 6, s - 6, 5.0), GOLD_HI, 3, 0.6)
            g.paint = goldVertical(0.0, s)
            g.stroke = BasicStroke(2f)
            g.draw(round(1.5, 1.5, s - 3, s - 3, 6.0))
            // small sparkle in the top-right corner
            val sparkle = star(s - 6.5, 6.5, 4.0)
            g.color = GOLD_HI
            g.fill(sparkle)
        }

    fun buy(confirm: Boolean): BufferedImage =
        canvas(BUY_WIDTH, BUY_HEIGHT) { g ->
            val w = BUY_WIDTH.toDouble()
            val h = BUY_HEIGHT.toDouble()
            val shape = round(1.0, 1.0, w - 2, h - 2, (h - 2) / 2)
            g.paint =
                if (confirm) {
                    vertical(0.0, h, 0f to Color(0xFFF08A), 0.5f to Color(0xE8B230), 1f to Color(0x9C6A10))
                } else {
                    vertical(0.0, h, 0f to Color(0x8BF07A), 0.5f to Color(0x35A53A), 1f to Color(0x1C6122))
                }
            g.fill(shape)
            g.paint = vertical(0.0, h / 2, 0f to alpha(Color.WHITE, 0.55), 1f to alpha(Color.WHITE, 0.05))
            g.fill(round(5.0, 2.5, w - 10, h / 2 - 1.5, (h / 2 - 1.5) / 2))
            g.paint = goldVertical(0.0, h)
            g.stroke = BasicStroke(1.6f)
            g.draw(shape)
        }

    fun icon(themeIndex: Int): BufferedImage =
        canvas(ICON_SIZE, ICON_SIZE) { g ->
            val s = ICON_SIZE.toDouble()
            val t = THEMES[themeIndex]
            if (themeIndex == 0) {
                // A cut gem for Donator Points.
                val body = gem(s / 2, s / 2 + 0.5, 6.8)
                g.paint = vertical(1.0, s - 1, 0f to Color(0xFFFFFF), 0.4f to t.accentLight, 1f to t.accentDark)
                g.fill(body)
                g.color = alpha(Color.WHITE, 0.75)
                g.stroke = BasicStroke(0.7f)
                g.draw(java.awt.geom.Line2D.Double(s / 2 - 6.8, s / 2 - 1.8, s / 2 + 6.8, s / 2 - 1.8))
                g.draw(java.awt.geom.Line2D.Double(s / 2 - 3.7, s / 2 - 5.4, s / 2, s / 2 + 7.3))
                g.draw(java.awt.geom.Line2D.Double(s / 2 + 3.7, s / 2 - 5.4, s / 2, s / 2 + 7.3))
                g.color = GOLD_DARK
                g.stroke = BasicStroke(0.9f)
                g.draw(body)
            } else {
                // A minted coin with the shop's emblem.
                val coin = Ellipse2D.Double(0.8, 0.8, s - 1.6, s - 1.6)
                g.paint = vertical(0.0, s, 0f to t.accentLight, 1f to t.accentDark)
                g.fill(coin)
                g.paint = goldVertical(0.0, s)
                g.stroke = BasicStroke(1.3f)
                g.draw(coin)
                g.color = Color.WHITE
                g.fill(emblem(themeIndex, s / 2, s / 2 + 0.3, 4.0))
            }
        }

    fun arrow(left: Boolean): BufferedImage =
        canvas(ARROW_WIDTH, ARROW_HEIGHT) { g ->
            val w = ARROW_WIDTH.toDouble()
            val h = ARROW_HEIGHT.toDouble()
            val p = GeneralPath()
            if (left) {
                p.moveTo(w - 4, 4.0)
                p.lineTo(5.0, h / 2)
                p.lineTo(w - 4, h - 4)
                p.lineTo(w - 8, h / 2)
            } else {
                p.moveTo(4.0, 4.0)
                p.lineTo(w - 5, h / 2)
                p.lineTo(4.0, h - 4)
                p.lineTo(8.0, h / 2)
            }
            p.closePath()
            glow(g, p, GOLD, 3, 0.5)
            g.paint = goldVertical(0.0, h)
            g.fill(p)
            g.color = GOLD_DARK
            g.stroke = BasicStroke(0.8f)
            g.draw(p)
        }

    fun close(): BufferedImage =
        canvas(CLOSE_SIZE, CLOSE_SIZE) { g ->
            val s = CLOSE_SIZE.toDouble()
            val disc = Ellipse2D.Double(1.5, 1.5, s - 3, s - 3)
            g.paint = vertical(0.0, s, 0f to Color(0xC23A3A), 1f to Color(0x5A0E10))
            g.fill(disc)
            g.paint = vertical(0.0, s / 2, 0f to alpha(Color.WHITE, 0.4), 1f to alpha(Color.WHITE, 0.0))
            g.fill(Ellipse2D.Double(4.0, 3.0, s - 8, s / 2 - 2))
            g.paint = goldVertical(0.0, s)
            g.stroke = BasicStroke(1.6f)
            g.draw(disc)
            g.color = Color.WHITE
            g.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(java.awt.geom.Line2D.Double(7.5, 7.5, s - 7.5, s - 7.5))
            g.draw(java.awt.geom.Line2D.Double(s - 7.5, 7.5, 7.5, s - 7.5))
        }

    /** Every sprite this tool owns, by id. */
    fun all(): Map<Int, BufferedImage> {
        val out = linkedMapOf<Int, BufferedImage>()
        for (i in THEMES.indices) out[BACKGROUND_FIRST + i] = background(i)
        for (i in THEMES.indices) out[TAB_NORMAL_FIRST + i] = tab(i, false)
        for (i in THEMES.indices) out[TAB_SELECTED_FIRST + i] = tab(i, true)
        out[SLOT] = slot()
        out[SLOT_GLOW] = slotGlow()
        out[SLOT_RING] = slotRing()
        out[BUY] = buy(false)
        out[BUY_CONFIRM] = buy(true)
        for (i in THEMES.indices) out[ICON_FIRST + i] = icon(i)
        out[ARROW_LEFT] = arrow(true)
        out[ARROW_RIGHT] = arrow(false)
        out[CLOSE] = close()
        check(out.size == COUNT && out.keys == (BASE until BASE + COUNT).toSet()) { "sprite ids must be $BASE..${BASE + COUNT - 1}" }
        out[SECTION_NORMAL] = section(null)
        for (i in THEMES.indices) out[SECTION_SELECTED_FIRST + i] = section(i)
        SLOT_TIER_COLOURS.forEachIndexed { i, colour -> out[SLOT_TIER_FIRST + i] = slotTier(colour) }
        out[PAGE_BUTTON_FIRST] = pageButton(next = false, enabled = true)
        out[PAGE_BUTTON_FIRST + 1] = pageButton(next = true, enabled = true)
        out[PAGE_BUTTON_FIRST + 2] = pageButton(next = false, enabled = false)
        out[PAGE_BUTTON_FIRST + 3] = pageButton(next = true, enabled = false)
        return out
    }

    // ---- 667 sprite encoding --------------------------------------------------------------------------------------

    /** Median-cut palette of at most [max] colours over the opaque pixels. */
    fun palette(
        pixels: IntArray,
        max: Int,
    ): IntArray {
        val colours = pixels.filter { (it ushr 24) != 0 }.map { it and 0xFFFFFF }
        if (colours.isEmpty()) return intArrayOf(0x000001)
        val distinct = colours.distinct()
        if (distinct.size <= max) return distinct.toIntArray()
        var boxes = mutableListOf(colours.toIntArray())
        while (boxes.size < max) {
            var best = -1
            var bestRange = -1
            var bestChannel = 0
            boxes.forEachIndexed { i, box ->
                if (box.size < 2) return@forEachIndexed
                for (channel in 0..2) {
                    val shift = 16 - channel * 8
                    var lo = 255
                    var hi = 0
                    for (c in box) {
                        val v = (c shr shift) and 0xFF
                        if (v < lo) lo = v
                        if (v > hi) hi = v
                    }
                    val range = (hi - lo) * box.size.coerceAtMost(4096)
                    if (range > bestRange) {
                        bestRange = range
                        best = i
                        bestChannel = channel
                    }
                }
            }
            if (best < 0 || bestRange <= 0) break
            val box = boxes.removeAt(best)
            val shift = 16 - bestChannel * 8
            val sorted = box.sortedBy { (it shr shift) and 0xFF }
            boxes.add(sorted.subList(0, sorted.size / 2).toIntArray())
            boxes.add(sorted.subList(sorted.size / 2, sorted.size).toIntArray())
            boxes = boxes.filter { it.isNotEmpty() }.toMutableList()
        }
        return boxes.map { box ->
            var r = 0L
            var g = 0L
            var b = 0L
            for (c in box) {
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            (((r / box.size).toInt() shl 16) or ((g / box.size).toInt() shl 8) or (b / box.size).toInt())
        }.distinct().toIntArray()
    }

    /**
     * One 667 sprite group (`IndexedImage.load`): flags (bit 2 = alpha), raster, alpha, palette, then the trailer of
     * scale size, palette size - 1, offsets, size and count. Index 0 is the transparent entry, so opaque pixels use 1..255.
     */
    fun encode(image: BufferedImage): ByteArray {
        val w = image.width
        val h = image.height
        val argb = image.getRGB(0, 0, w, h, null, 0, w)
        val palette = palette(argb, 255)
        val raster = ByteArray(w * h)
        val alpha = ByteArray(w * h)
        // Floyd-Steinberg on the colour of visible pixels.
        val er = FloatArray(w * h)
        val eg = FloatArray(w * h)
        val eb = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val a = argb[i] ushr 24
                alpha[i] = a.toByte()
                if (a == 0) continue
                val r = (((argb[i] shr 16) and 0xFF) + er[i]).coerceIn(0f, 255f)
                val g = (((argb[i] shr 8) and 0xFF) + eg[i]).coerceIn(0f, 255f)
                val b = ((argb[i] and 0xFF) + eb[i]).coerceIn(0f, 255f)
                var best = 0
                var bestDistance = Float.MAX_VALUE
                for (p in palette.indices) {
                    val c = palette[p]
                    val dr = r - ((c shr 16) and 0xFF)
                    val dg = g - ((c shr 8) and 0xFF)
                    val db = b - (c and 0xFF)
                    val d = dr * dr * 0.30f + dg * dg * 0.59f + db * db * 0.11f
                    if (d < bestDistance) {
                        bestDistance = d
                        best = p
                    }
                }
                raster[i] = (best + 1).toByte()
                val c = palette[best]
                val qr = r - ((c shr 16) and 0xFF)
                val qg = g - ((c shr 8) and 0xFF)
                val qb = b - (c and 0xFF)
                fun spread(
                    dx: Int,
                    dy: Int,
                    f: Float,
                ) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) return
                    val j = ny * w + nx
                    er[j] += qr * f
                    eg[j] += qg * f
                    eb[j] += qb * f
                }
                spread(1, 0, 7f / 16)
                spread(-1, 1, 3f / 16)
                spread(0, 1, 5f / 16)
                spread(1, 1, 1f / 16)
            }
        }
        val out = ByteArrayOutputStream()
        out.write(0x2) // row-major, alpha channel present
        out.write(raster)
        out.write(alpha)
        for (c in palette) {
            val v = if (c == 0) 1 else c
            out.write((v shr 16) and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write(v and 0xFF)
        }
        fun p2(v: Int) {
            out.write((v shr 8) and 0xFF)
            out.write(v and 0xFF)
        }
        p2(w) // scale width
        p2(h) // scale height
        out.write(palette.size) // palette size - 1 (entry 0 is transparent)
        p2(0) // offX
        p2(0) // offY
        p2(w)
        p2(h)
        p2(1) // one frame
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // Since 2026-10-03 the art is sized for one window layout, so it is written only together with that layout:
        // StoreInterfaceImportTool --apply writes interface and art in one transaction. This entry point previews.
        check("--apply" !in args) { "StoreArtTool no longer applies on its own: run StoreInterfaceImportTool --apply (window + art in one transaction)" }
        val apply = false
        val art = all()
        args.firstOrNull { it.startsWith("--png=") }?.substringAfter('=')?.let { dir ->
            File(dir).mkdirs()
            art.forEach { (id, image) -> ImageIO.write(image, "png", File(dir, "store_$id.png")) }
            println("PNG written to $dir")
        }
        // These ids belong to this tool alone, so a re-run may replace its own earlier art (pinned to what is there now).
        val current =
            com.displee.cache.CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0]).let { library ->
                try {
                    art.keys.associateWith { id -> library.data(LootKeyInterfaceImportTool.INDEX_SPRITES, id, 0)?.let(CacheItemProbeTool::sha1) }
                } finally {
                    library.close()
                }
            }
        val mutations =
            art.map { (id, image) ->
                CacheMutation(LootKeyInterfaceImportTool.INDEX_SPRITES, id, 0, encode(image), "store art sprite $id", expectedCurrentSha1 = current[id])
            }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write sprites $BASE..${BASE + COUNT - 1}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) println("VERIFY_OK transaction=${transaction.id}") else problems.forEach { println("  VERIFY_PROBLEM: $it") }
    }
}
