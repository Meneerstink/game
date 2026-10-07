package gg.rsmod.game.tools.importer;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * "Choose your path" art (sprites for QuestGuideInterfaceImportTool through ChoosePathArtTool), second pass (owner 2026-09-26: "meer prachtige kleuren ... niet alleen rood maar ook gele kleuren,
 * alles high quality"). Obsidian and gold with a per-row colour: MAGIC sapphire/violet, PRAYER radiant gold, GEAR ruby.
 * Everything is painted at 2x and scaled down (supersampling). Writes the sprites and mock-ups of every state.
 */
public class ChoosePathArt {
    static final int W = 488, H = 320;
    static final int CARD_W = 124, CARD_H = 72;
    static final int CARDS_X = 92, ROWS_Y = 68, ROW_PITCH = 78, GAP = 6;
    static final int SS = 2;

    // Gold, from highlight to shadow.
    static final Color G0 = new Color(0xFFFBE0), G1 = new Color(0xFFE27A), G2 = new Color(0xF2B930), G3 = new Color(0xC7861A), G4 = new Color(0x6E440C);
    static final Color CREAM = new Color(0xF3E7C8);

    /** Row themes: light, mid, dark, glow. */
    static final Color[][] ROW = {
        {new Color(0x9FD0FF), new Color(0x3A5BD9), new Color(0x120C3A), new Color(0x6FA8FF)}, // MAGIC sapphire / violet
        {new Color(0xFFF3A8), new Color(0xE0A316), new Color(0x3A2406), new Color(0xFFD24A)}, // PRAYER radiant gold
        {new Color(0xFF9A8A), new Color(0xC0182A), new Color(0x33060C), new Color(0xFF4A3A)}, // GEAR ruby
    };
    static final String[] ROW_NAMES = {"MAGIC", "PRAYER", "GEAR"};

    // ------------------------------------------------------------------------------------------------ helpers

    interface Painter { void paint(Graphics2D g); }

    /** Paints at SS x and scales down with bicubic filtering. */
    public static BufferedImage art(int w, int h, Painter p) {
        BufferedImage big = new BufferedImage(w * SS, h * SS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(big);
        g.scale(SS, SS);
        p.paint(g);
        g.dispose();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D s = img.createGraphics();
        s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        s.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        s.drawImage(big, 0, 0, w, h, null);
        s.dispose();
        return img;
    }

    public static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        return g;
    }

    public static Color a(Color c, double alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.max(0, Math.min(255, alpha * 255)));
    }

    public static Color mix(Color x, Color y, double t) {
        return new Color((int) (x.getRed() + (y.getRed() - x.getRed()) * t), (int) (x.getGreen() + (y.getGreen() - x.getGreen()) * t), (int) (x.getBlue() + (y.getBlue() - x.getBlue()) * t));
    }

    public static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    public static Paint vert(double y0, double y1, float[] f, Color[] c) {
        return new LinearGradientPaint(new Point2D.Double(0, y0), new Point2D.Double(0, y1 <= y0 ? y0 + 1 : y1), f, c);
    }

    public static Paint gold(double y0, double y1) {
        return vert(y0, y1, new float[] {0f, .22f, .5f, .78f, 1f}, new Color[] {G0, G1, G2, G3, G4});
    }

    /** Polished gold: a bright band in the middle as on real metal. */
    public static Paint goldBand(double y0, double y1) {
        return vert(y0, y1, new float[] {0f, .3f, .45f, .55f, .7f, 1f}, new Color[] {G3, G1, G0, G1, G2, G4});
    }

    public static Font serif(float size) {
        String[] names = {"Trajan Pro", "Cinzel", "Georgia", "Palatino Linotype", "Cambria", "Serif"};
        java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) if (have.contains(n)) return new Font(n, Font.BOLD, 1).deriveFont(size);
        return new Font("Serif", Font.BOLD, 1).deriveFont(size);
    }

    public static Shape text(Graphics2D g, String s, Font f, double cx, double baseline, float tracking) {
        Font t = f.deriveFont(Map.of(TextAttribute.TRACKING, tracking));
        TextLayout l = new TextLayout(s, t, g.getFontRenderContext());
        Rectangle2D b = l.getBounds();
        return l.getOutline(AffineTransform.getTranslateInstance(cx - b.getWidth() / 2 - b.getX(), baseline));
    }

    public static void glow(Graphics2D g, Shape s, Color c, int radius, double strength) {
        for (int i = radius; i >= 1; i--) {
            g.setColor(a(c, strength * (1.0 - (double) i / (radius + 1)) / radius * 2.4));
            g.setStroke(new BasicStroke(i * 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(s);
        }
    }

    /** Engraved metal: glow, drop shadow, fill, bright top edge, dark outline. */
    public static void metal(Graphics2D g, Shape s, Paint fill, Color glowColour, int glowRadius) {
        if (glowColour != null) glow(g, s, glowColour, glowRadius, 0.6);
        g.setColor(a(Color.BLACK, 0.85));
        g.fill(AffineTransform.getTranslateInstance(0.9, 1.4).createTransformedShape(s));
        g.setPaint(fill);
        g.fill(s);
        Shape old = g.getClip();
        g.clip(s);
        g.setColor(a(G0, 0.55));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(AffineTransform.getTranslateInstance(0, 0.6).createTransformedShape(s));
        g.setClip(old);
        g.setColor(a(G4, 0.95));
        g.setStroke(new BasicStroke(0.55f));
        g.draw(s);
    }

    public static void grain(Graphics2D g, Shape clip, long seed, double strength) {
        Shape old = g.getClip();
        g.clip(clip);
        Rectangle b = clip.getBounds();
        Random r = new Random(seed);
        for (int i = 0; i < b.width * b.height / 3; i++) {
            double x = b.x + r.nextDouble() * b.width, y = b.y + r.nextDouble() * b.height;
            g.setColor(r.nextBoolean() ? a(Color.WHITE, strength * r.nextDouble()) : a(Color.BLACK, strength * 1.8 * r.nextDouble()));
            g.fill(new Rectangle2D.Double(x, y, 0.5, 0.5));
        }
        g.setClip(old);
    }

    public static Shape diamond(double cx, double cy, double s) {
        GeneralPath p = new GeneralPath();
        p.moveTo(cx - s, cy);
        p.lineTo(cx, cy - s * 1.15);
        p.lineTo(cx + s, cy);
        p.lineTo(cx, cy + s * 1.15);
        p.closePath();
        return p;
    }

    /** A cut gem with a gold setting and a sparkle. */
    public static void gem(Graphics2D g, double cx, double cy, double s, Color light, Color mid, Color dark, Color glowColour) {
        Shape setting = new Ellipse2D.Double(cx - s * 1.35, cy - s * 1.35, s * 2.7, s * 2.7);
        glow(g, setting, glowColour, 4, 0.55);
        g.setPaint(goldBand(cy - s * 1.35, cy + s * 1.35));
        g.fill(setting);
        Shape stone = new Ellipse2D.Double(cx - s, cy - s, s * 2, s * 2);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - s * .3, cy - s * .35), (float) (s * 1.4), new float[] {0f, .45f, 1f}, new Color[] {light, mid, dark}));
        g.fill(stone);
        g.setColor(a(Color.WHITE, 0.85));
        g.fill(new Ellipse2D.Double(cx - s * .55, cy - s * .6, s * .5, s * .35));
        g.setColor(a(G4, 0.9));
        g.setStroke(new BasicStroke(0.5f));
        g.draw(stone);
    }

    public static Shape skull(double cx, double cy, double s) {
        Area a = new Area(new Ellipse2D.Double(cx - s, cy - s, s * 2, s * 1.7));
        a.add(new Area(new RoundRectangle2D.Double(cx - s * .62, cy + s * .3, s * 1.24, s * .72, s * .3, s * .3)));
        a.subtract(new Area(new Ellipse2D.Double(cx - s * .68, cy - s * .35, s * .52, s * .56)));
        a.subtract(new Area(new Ellipse2D.Double(cx + s * .16, cy - s * .35, s * .52, s * .56)));
        GeneralPath nose = new GeneralPath();
        nose.moveTo(cx, cy + s * .18);
        nose.lineTo(cx - s * .14, cy + s * .42);
        nose.lineTo(cx + s * .14, cy + s * .42);
        nose.closePath();
        a.subtract(new Area(nose));
        for (double dx : new double[] {-.3, 0, .3}) a.subtract(new Area(new Rectangle2D.Double(cx + dx * s - s * .05, cy + s * .62, s * .1, s * .4)));
        return a;
    }

    public static Shape swords(double cx, double cy, double s) {
        Area out = new Area();
        for (int side : new int[] {-1, 1}) {
            GeneralPath blade = new GeneralPath();
            blade.moveTo(-s * .09, -s);
            blade.lineTo(s * .09, -s);
            blade.lineTo(s * .09, s * .55);
            blade.lineTo(0, s * .74);
            blade.lineTo(-s * .09, s * .55);
            blade.closePath();
            Area sword = new Area(blade);
            sword.add(new Area(new RoundRectangle2D.Double(-s * .34, -s * .8, s * .68, s * .12, s * .1, s * .1)));
            sword.add(new Area(new Rectangle2D.Double(-s * .06, -s * 1.14, s * .12, s * .36)));
            sword.add(new Area(new Ellipse2D.Double(-s * .11, -s * 1.26, s * .22, s * .22)));
            AffineTransform t = AffineTransform.getTranslateInstance(cx, cy);
            t.rotate(side * Math.toRadians(38) + Math.PI);
            out.add(sword.createTransformedArea(t));
        }
        return out;
    }

    /** Laurel branch: leaves along an arc, mirrored by [side]. */
    public static Shape laurel(double cx, double cy, double r, int side) {
        Area out = new Area();
        for (int i = 0; i < 7; i++) {
            double an = Math.toRadians(200 + i * 20) * side + (side < 0 ? Math.PI : 0);
            double x = cx + Math.cos(an) * r, y = cy + Math.sin(an) * r * 0.55;
            Ellipse2D leaf = new Ellipse2D.Double(-3.2, -1.3, 6.4, 2.6);
            AffineTransform t = AffineTransform.getTranslateInstance(x, y);
            t.rotate(an + Math.PI / 2 * side);
            out.add(new Area(t.createTransformedShape(leaf)));
        }
        return out;
    }

    /** Row emblems: rune star (Magic), holy symbol with rays (Prayer), sword over shield (Gear). */
    public static Shape emblem(int row, double cx, double cy, double s) {
        switch (row) {
            case 0: {
                GeneralPath p = new GeneralPath();
                for (int i = 0; i < 16; i++) {
                    double r = i % 2 == 0 ? s : s * .36;
                    double an = -Math.PI / 2 + i * Math.PI / 8;
                    double x = cx + Math.cos(an) * r, y = cy + Math.sin(an) * r;
                    if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.closePath();
                Area ar = new Area(p);
                ar.subtract(new Area(new Ellipse2D.Double(cx - s * .18, cy - s * .18, s * .36, s * .36)));
                return ar;
            }
            case 1: {
                Area ar = new Area(new RoundRectangle2D.Double(cx - s * .15, cy - s, s * .3, s * 2, s * .1, s * .1));
                ar.add(new Area(new RoundRectangle2D.Double(cx - s * .68, cy - s * .46, s * 1.36, s * .3, s * .1, s * .1)));
                Area ring = new Area(new Ellipse2D.Double(cx - s * .5, cy - s * .81, s, s));
                ring.subtract(new Area(new Ellipse2D.Double(cx - s * .35, cy - s * .66, s * .7, s * .7)));
                ar.add(ring);
                return ar;
            }
            default: {
                GeneralPath shield = new GeneralPath();
                shield.moveTo(cx - s * .72, cy - s * .7);
                shield.lineTo(cx + s * .72, cy - s * .7);
                shield.lineTo(cx + s * .72, cy);
                shield.quadTo(cx + s * .6, cy + s * .7, cx, cy + s);
                shield.quadTo(cx - s * .6, cy + s * .7, cx - s * .72, cy);
                shield.closePath();
                Area ar = new Area(shield);
                Area inner = new Area(AffineTransform.getTranslateInstance(cx * (1 - .72), cy * (1 - .72) + s * .05).createTransformedShape(AffineTransform.getScaleInstance(.72, .72).createTransformedShape(shield)));
                ar.subtract(inner);
                Area sword = new Area(new Rectangle2D.Double(cx - s * .07, cy - s * .62, s * .14, s * 1.3));
                sword.add(new Area(new Rectangle2D.Double(cx - s * .38, cy - s * .3, s * .76, s * .13)));
                ar.add(sword);
                return ar;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ sprites

    public static BufferedImage background() {
        return art(W, H, g -> {
            RoundRectangle2D body = rr(1, 1, W - 2, H - 2, 14);
            // Obsidian with a warm amber light from the top and a faint crimson edge.
            g.setPaint(vert(0, H, new float[] {0f, .4f, 1f}, new Color[] {new Color(0x241810), new Color(0x120C0A), new Color(0x07050A)}));
            g.fill(body);
            g.setClip(body);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(W / 2.0, 6), 300f, new float[] {0f, .5f, 1f}, new Color[] {a(G2, 0.42), a(new Color(0xB04A10), 0.14), a(Color.BLACK, 0)}));
            g.fill(body);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(W / 2.0, H / 2.0), W * .7f, new float[] {.55f, 1f}, new Color[] {a(Color.BLACK, 0), a(new Color(0x3A0508), .75)}));
            g.fill(body);
            // Soft light rays from the header.
            for (int i = -4; i <= 4; i++) {
                GeneralPath p = new GeneralPath();
                p.moveTo(W / 2.0 + i * 5 - 2, 0);
                p.lineTo(W / 2.0 + i * 5 + 2, 0);
                p.lineTo(W / 2.0 + i * 70 + 26, H);
                p.lineTo(W / 2.0 + i * 70 - 26, H);
                p.closePath();
                g.setPaint(vert(0, H, new float[] {0f, .6f}, new Color[] {a(G1, 0.07), a(G1, 0)}));
                g.fill(p);
            }
            g.setClip(null);
            grain(g, body, 7L, 0.035);

            // Header: gold title with an amber glow, laurel branches, skulls over crossed swords.
            Shape title = text(g, "CHOOSE YOUR PATH", serif(21f), W / 2.0, 30, 0.12f);
            Rectangle2D tb = title.getBounds2D();
            metal(g, title, goldBand(tb.getMinY(), tb.getMaxY()), new Color(0xFF9A1A), 8);
            for (int side : new int[] {-1, 1}) {
                double cx = side < 0 ? tb.getMinX() - 30 : tb.getMaxX() + 30;
                metal(g, laurel(cx, 22, 13, side), gold(10, 34), null, 0);
                metal(g, swords(cx, 22, 11), goldBand(10, 34), null, 0);
                metal(g, skull(cx, 21, 7.2), vert(13, 30, new float[] {0f, 1f}, new Color[] {new Color(0xFFF8E8), new Color(0xB8A882)}), new Color(0xFF7A1A), 5);
                double start = side < 0 ? cx - 24 : cx + 24;
                double end = side < 0 ? 24 : W - 24;
                g.setPaint(new LinearGradientPaint(new Point2D.Double(start, 0), new Point2D.Double(end, 0), new float[] {0f, 1f}, new Color[] {a(G1, .95), a(G2, 0)}));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new Line2D.Double(start, 21, end, 21));
                g.setStroke(new BasicStroke(.6f));
                g.draw(new Line2D.Double(start, 25, end + (side < 0 ? 40 : -40), 25));
            }
            // Divider with a ruby between two small sapphires.
            g.setPaint(new LinearGradientPaint(new Point2D.Double(18, 0), new Point2D.Double(W - 18, 0), new float[] {0f, .5f, 1f}, new Color[] {a(G2, 0), G1, a(G2, 0)}));
            g.setStroke(new BasicStroke(1.3f));
            g.draw(new Line2D.Double(18, 39.5, W - 18, 39.5));
            gem(g, W / 2.0 - 16, 39.5, 1.8, ROW[0][0], ROW[0][1], ROW[0][2], ROW[0][3]);
            gem(g, W / 2.0 + 16, 39.5, 1.8, ROW[0][0], ROW[0][1], ROW[0][2], ROW[0][3]);
            gem(g, W / 2.0, 39.5, 3.2, ROW[2][0], ROW[2][1], ROW[2][2], ROW[2][3]);

            // Row plaques in each row's colour, emblem above the name, a slot for the Confirm button.
            for (int r = 0; r < 3; r++) {
                double y = ROWS_Y + r * ROW_PITCH;
                Color[] c = ROW[r];
                RoundRectangle2D plaque = rr(10, y, 76, CARD_H, 8);
                g.setPaint(vert(y, y + CARD_H, new float[] {0f, .5f, 1f}, new Color[] {mix(c[1], Color.BLACK, .35), mix(c[2], Color.BLACK, .2), mix(c[2], Color.BLACK, .55)}));
                g.fill(plaque);
                g.setClip(plaque);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(48, y + 12), 40f, new float[] {0f, 1f}, new Color[] {a(c[3], .55), a(c[3], 0)}));
                g.fill(plaque);
                g.setClip(null);
                grain(g, plaque, 100 + r, 0.035);
                g.setPaint(goldBand(y, y + CARD_H));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(plaque);
                g.setColor(a(G0, .35));
                g.setStroke(new BasicStroke(.6f));
                g.draw(rr(12.5, y + 2.5, 71, CARD_H - 5, 6.5));
                metal(g, emblem(r, 48, y + 11.5, 7.5), goldBand(y + 3, y + 20), c[3], 5);
                Shape name = text(g, ROW_NAMES[r], serif(10.5f), 48, y + 32, 0.1f);
                metal(g, name, gold(y + 23, y + 33), null, 0);
                g.setPaint(new LinearGradientPaint(new Point2D.Double(16, 0), new Point2D.Double(80, 0), new float[] {0f, .5f, 1f}, new Color[] {a(G2, 0), G1, a(G2, 0)}));
                g.setStroke(new BasicStroke(.8f));
                g.draw(new Line2D.Double(16, y + 37.5, 80, y + 37.5));
            }

            // Frame: an obsidian band between polished gold rims, a gem in every corner.
            g.setPaint(vert(0, H, new float[] {0f, 1f}, new Color[] {new Color(0x2A1A10), new Color(0x0A0605)}));
            g.setStroke(new BasicStroke(5f));
            g.draw(rr(4.5, 4.5, W - 9, H - 9, 12));
            g.setPaint(goldBand(0, H));
            g.setStroke(new BasicStroke(1.8f));
            g.draw(rr(1.8, 1.8, W - 3.6, H - 3.6, 14));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(rr(7.3, 7.3, W - 14.6, H - 14.6, 10));
            double[][] corners = {{12, 12}, {W - 12, 12}, {12, H - 12}, {W - 12, H - 12}};
            for (int i = 0; i < 4; i++) {
                Color[] c = ROW[i == 0 || i == 3 ? 2 : 0];
                gem(g, corners[i][0], corners[i][1], 3.3, c[0], c[1], c[2], c[3]);
            }
            // Gold studs along the frame.
            for (double x = 40; x < W - 30; x += 52) {
                for (double y : new double[] {4.5, H - 4.5}) {
                    g.setPaint(goldBand(y - 1.6, y + 1.6));
                    g.fill(new Ellipse2D.Double(x - 1.6, y - 1.6, 3.2, 3.2));
                }
            }
        });
    }

    /** Card faces per row: state 0 normal, 1 selected (golden aura), 2 chosen (gold + warm), 3 locked (slate). */
    public static BufferedImage card(int row, int state) {
        Color[] c = ROW[row];
        return art(CARD_W, CARD_H, g -> {
            RoundRectangle2D shape = rr(1.5, 1.5, CARD_W - 3, CARD_H - 3, 8);
            if (state == 3) {
                g.setPaint(vert(0, CARD_H, new float[] {0f, 1f}, new Color[] {new Color(0x3C3A38), new Color(0x161514)}));
                g.fill(shape);
                grain(g, shape, 50 + row, 0.04);
                g.setColor(new Color(0x6A6560));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(shape);
                // Two dim chains across the card.
                g.setColor(a(new Color(0x8A8680), 0.35));
                g.setStroke(new BasicStroke(1.4f));
                for (double t = 6; t < CARD_W - 6; t += 7) g.draw(new Ellipse2D.Double(t, CARD_H * .42 + (t * 0.06), 6, 3.4));
                return;
            }
            Color top = state == 0 ? mix(c[1], Color.BLACK, .45) : mix(c[1], Color.BLACK, .2);
            g.setPaint(vert(0, CARD_H, new float[] {0f, .45f, 1f}, new Color[] {top, mix(c[2], Color.BLACK, .1), mix(c[2], Color.BLACK, .6)}));
            g.fill(shape);
            g.setClip(shape);
            // Light well behind the icon in the row's colour.
            g.setPaint(new RadialGradientPaint(new Point2D.Double(CARD_W / 2.0, 19), 32f, new float[] {0f, .6f, 1f}, new Color[] {a(state == 0 ? c[3] : G1, state == 0 ? .55 : .75), a(state == 0 ? c[3] : G2, .15), a(Color.BLACK, 0)}));
            g.fill(shape);
            // Glass sheen.
            g.setPaint(vert(0, CARD_H / 2.0, new float[] {0f, 1f}, new Color[] {a(Color.WHITE, .16), a(Color.WHITE, 0)}));
            g.fill(rr(3, 3, CARD_W - 6, CARD_H / 2.0 - 2, 6));
            g.setClip(null);
            grain(g, shape, 60 + row * 7 + state, 0.025);
            // Divider under the icon.
            g.setPaint(new LinearGradientPaint(new Point2D.Double(14, 0), new Point2D.Double(CARD_W - 14, 0), new float[] {0f, .5f, 1f}, new Color[] {a(G2, 0), a(G1, .9), a(G2, 0)}));
            g.setStroke(new BasicStroke(.7f));
            g.draw(new Line2D.Double(14, 34.5, CARD_W - 14, 34.5));
            if (state == 1) {
                // Selected: a white-gold aura inside the rim and sparkles in the corners.
                glow(g, rr(3.5, 3.5, CARD_W - 7, CARD_H - 7, 6.5), Color.WHITE, 6, .9);
                glow(g, rr(3.5, 3.5, CARD_W - 7, CARD_H - 7, 6.5), G0, 4, .9);
                for (double[] p : new double[][] {{9, 9}, {CARD_W - 9, 9}}) {
                    GeneralPath star = new GeneralPath();
                    for (int i = 0; i < 8; i++) {
                        double rad = i % 2 == 0 ? 5.5 : 1.3;
                        double an = -Math.PI / 2 + i * Math.PI / 4;
                        double x = p[0] + Math.cos(an) * rad, y = p[1] + Math.sin(an) * rad;
                        if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
                    }
                    star.closePath();
                    glow(g, star, G0, 3, .8);
                    g.setColor(Color.WHITE);
                    g.fill(star);
                }
            }
            if (state == 1 || state == 2) {
                glow(g, rr(3.5, 3.5, CARD_W - 7, CARD_H - 7, 6.5), state == 1 ? G0 : G1, 4, .8);
                g.setPaint(goldBand(0, CARD_H));
                g.setStroke(new BasicStroke(2.6f));
                g.draw(shape);
                g.setColor(a(G0, .75));
                g.setStroke(new BasicStroke(.7f));
                g.draw(rr(4.2, 4.2, CARD_W - 8.4, CARD_H - 8.4, 5.5));
            } else {
                g.setPaint(gold(0, CARD_H));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(shape);
            }
            // Gem studs in the lower corners, in the row's colour.
            gem(g, 7.5, CARD_H - 7.5, 1.9, c[0], c[1], c[2], c[3]);
            gem(g, CARD_W - 7.5, CARD_H - 7.5, 1.9, c[0], c[1], c[2], c[3]);
        });
    }

    /**
     * Mouse-over light for a card (owner 2026-10-07: "highlight it beautifully"): drawn over the card's face, under its
     * icon and texts. A warm light across the card, a white-gold aura inside the rim and a bright polished-gold rim with
     * a sparkle on each upper corner - the selected face's language, softer, so hover and selection stay distinct.
     */
    public static BufferedImage hover() {
        return art(CARD_W, CARD_H, g -> {
            RoundRectangle2D shape = rr(1.5, 1.5, CARD_W - 3, CARD_H - 3, 8);
            g.setClip(shape);
            // Warm light from the top, strongest behind the icon.
            g.setPaint(new RadialGradientPaint(new Point2D.Double(CARD_W / 2.0, 14), 70f, new float[] {0f, .55f, 1f},
                new Color[] {a(G0, .26), a(G1, .10), a(G1, 0)}));
            g.fill(shape);
            g.setPaint(vert(0, CARD_H, new float[] {0f, .5f, 1f}, new Color[] {a(Color.WHITE, .07), a(Color.WHITE, .02), a(Color.WHITE, 0)}));
            g.fill(shape);
            g.setClip(null);
            glow(g, rr(3.5, 3.5, CARD_W - 7, CARD_H - 7, 6.5), G0, 5, .75);
            g.setPaint(goldBand(0, CARD_H));
            g.setStroke(new BasicStroke(2.2f));
            g.draw(shape);
            g.setColor(a(G0, .6));
            g.setStroke(new BasicStroke(.6f));
            g.draw(rr(4.2, 4.2, CARD_W - 8.4, CARD_H - 8.4, 5.5));
            for (double[] p : new double[][] {{8, 8}, {CARD_W - 8, 8}}) {
                GeneralPath star = new GeneralPath();
                for (int i = 0; i < 8; i++) {
                    double rad = i % 2 == 0 ? 4.2 : 1.0;
                    double an = -Math.PI / 2 + i * Math.PI / 4;
                    double x = p[0] + Math.cos(an) * rad, y = p[1] + Math.sin(an) * rad;
                    if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
                }
                star.closePath();
                glow(g, star, G1, 3, .7);
                g.setColor(a(Color.WHITE, .9));
                g.fill(star);
            }
        });
    }

    public static BufferedImage tick() {
        return art(20, 20, g -> {
            Ellipse2D disc = new Ellipse2D.Double(1.5, 1.5, 17, 17);
            glow(g, disc, new Color(0x7CFF6A), 3, .6);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(8, 7), 12f, new float[] {0f, 1f}, new Color[] {new Color(0x9CFF8A), new Color(0x137A20)}));
            g.fill(disc);
            g.setPaint(goldBand(1, 19));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(disc);
            GeneralPath p = new GeneralPath();
            p.moveTo(5.5, 10.5);
            p.lineTo(8.8, 14);
            p.lineTo(14.8, 6.3);
            g.setColor(a(Color.BLACK, .45));
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(AffineTransform.getTranslateInstance(.5, .8).createTransformedShape(p));
            g.setColor(Color.WHITE);
            g.draw(p);
        });
    }

    public static BufferedImage lock() {
        return art(20, 20, g -> {
            Area shackle = new Area(new RoundRectangle2D.Double(5, 1.5, 10, 12, 10, 10));
            shackle.subtract(new Area(new RoundRectangle2D.Double(7.3, 3.8, 5.4, 10, 5.4, 5.4)));
            g.setPaint(vert(0, 12, new float[] {0f, 1f}, new Color[] {new Color(0xE8E8E8), new Color(0x707070)}));
            g.fill(shackle);
            RoundRectangle2D body = rr(3, 8.5, 14, 10, 2);
            g.setPaint(goldBand(8.5, 18.5));
            g.fill(body);
            g.setColor(G4);
            g.setStroke(new BasicStroke(.8f));
            g.draw(body);
            g.setColor(new Color(0x201505));
            g.fill(new Ellipse2D.Double(8.6, 11.2, 2.8, 2.8));
            g.fill(new Rectangle2D.Double(9.4, 13, 1.2, 3.2));
        });
    }

    /** Buttons: 0 gold (active), 1 dark bronze (disabled), 2 ruby (No / cancel). */
    public static BufferedImage button(int w, int h, int style) {
        return art(w, h, g -> {
            RoundRectangle2D shape = rr(1, 1, w - 2, h - 2, h / 2.0 - 1);
            if (style == 0) {
                glow(g, shape, G1, 3, .5);
                g.setPaint(vert(0, h, new float[] {0f, .45f, .55f, 1f}, new Color[] {new Color(0xFFF1A0), new Color(0xF0B92A), new Color(0xD89A18), new Color(0x7A4A08)}));
            } else if (style == 1) {
                g.setPaint(vert(0, h, new float[] {0f, 1f}, new Color[] {new Color(0x4A3E30), new Color(0x1C1812)}));
            } else {
                g.setPaint(vert(0, h, new float[] {0f, .5f, 1f}, new Color[] {new Color(0xF0605A), new Color(0xB0141E), new Color(0x4A0508)}));
            }
            g.fill(shape);
            g.setPaint(vert(0, h / 2.0, new float[] {0f, 1f}, new Color[] {a(Color.WHITE, style == 1 ? .08 : .5), a(Color.WHITE, 0)}));
            g.fill(rr(4, 2.2, w - 8, h / 2.0 - 1.5, h / 4.0));
            if (style == 1) {
                g.setColor(new Color(0x6A5C48));
                g.setStroke(new BasicStroke(1.1f));
            } else {
                g.setPaint(goldBand(0, h));
                g.setStroke(new BasicStroke(1.5f));
            }
            g.draw(shape);
        });
    }

    public static BufferedImage dialog() {
        int w = 260, h = 118;
        return art(w, h, g -> {
            RoundRectangle2D body = rr(1, 1, w - 2, h - 2, 10);
            g.setPaint(vert(0, h, new float[] {0f, 1f}, new Color[] {new Color(0x2A1C12), new Color(0x0A0605)}));
            g.fill(body);
            g.setClip(body);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(w / 2.0, 0), 160f, new float[] {0f, 1f}, new Color[] {a(G2, .35), a(G2, 0)}));
            g.fill(body);
            g.setClip(null);
            grain(g, body, 900L, .03);
            g.setPaint(goldBand(0, h));
            g.setStroke(new BasicStroke(1.8f));
            g.draw(rr(1.8, 1.8, w - 3.6, h - 3.6, 10));
            g.setStroke(new BasicStroke(1f));
            g.draw(rr(5.5, 5.5, w - 11, h - 11, 7));
            metal(g, skull(w / 2.0, 19, 6.8), vert(12, 28, new float[] {0f, 1f}, new Color[] {new Color(0xFFF8E8), new Color(0xB8A882)}), new Color(0xFF7A1A), 5);
            metal(g, laurel(w / 2.0, 20, 13, -1), gold(10, 32), null, 0);
            metal(g, laurel(w / 2.0, 20, 13, 1), gold(10, 32), null, 0);
        });
    }

    public static BufferedImage close() {
        int s = 22;
        return art(s, s, g -> {
            Ellipse2D disc = new Ellipse2D.Double(1.5, 1.5, s - 3, s - 3);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(9, 8), 13f, new float[] {0f, 1f}, new Color[] {new Color(0xF0605A), new Color(0x5A0810)}));
            g.fill(disc);
            g.setPaint(goldBand(0, s));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(disc);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(7.5, 7.5, s - 7.5, s - 7.5));
            g.draw(new Line2D.Double(s - 7.5, 7.5, 7.5, s - 7.5));
        });
    }

    // ------------------------------------------------------------------------------------------------ mock-up

    static final String[][] NAMES = {
        {"Desert Treasure", "Lunar Diplomacy", "Desert Treasure II"},
        {"Temple at Senntisten", "King's Ransom"},
        {"Dragon Slayer II", "Song of the Elves", "Monkey Madness II"},
    };
    static final String[][] DESC = {
        {"Ancient Magicks|spellbook", "Lunar spellbook|and Vengeance", "Ancient sceptres|and Magus ring"},
        {"Ancient Curses|prayer book", "Piety and|Chivalry"},
        {"Ava's assembler|and Myths' Guild", "Bow of Faerdhinen|and crystal armour", "Zenyte jewellery|and heavy ballista"},
    };

    public static int cardX(int c, int count) {
        return CARDS_X + (3 - count) * (CARD_W + GAP) / 2 + c * (CARD_W + GAP);
    }

    public static void small(Graphics2D g, String s, Color c, double x, double y, float size, boolean bold) {
        g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(size));
        double w = g.getFontMetrics().stringWidth(s);
        g.setColor(Color.BLACK);
        g.drawString(s, (float) (x - w / 2) + 1, (float) y + 1);
        g.setColor(c);
        g.drawString(s, (float) (x - w / 2), (float) y);
    }

    public static BufferedImage mock(int[][] states, boolean[] confirm, boolean dialog) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        g.drawImage(background(), 0, 0, null);
        g.drawImage(close(), W - 32, 10, null);
        small(g, "Choose one unlock per row. Your choice is permanent - other options can", CREAM, W / 2.0, 52, 10.5f, false);
        small(g, "be earned by completing their quest.", CREAM, W / 2.0, 64, 10.5f, false);
        for (int r = 0; r < 3; r++) {
            int y = ROWS_Y + r * ROW_PITCH;
            g.drawImage(button(66, 26, confirm[r] ? 0 : 1), 15, y + 42, null);
            small(g, "Confirm", confirm[r] ? new Color(0x3A1E02) : new Color(0x9A8A70), 48, y + 59, 11f, true);
            for (int c = 0; c < NAMES[r].length; c++) {
                int x = cardX(c, NAMES[r].length);
                int st = states[r][c];
                g.drawImage(card(r, st == 4 ? 0 : st), x, y, null);
                g.setColor(st == 3 ? new Color(0x5A5A5A) : new Color(0xC8B478));
                g.fill(new RoundRectangle2D.Double(x + CARD_W / 2.0 - 11, y + 6, 22, 24, 6, 6));
                Color nameColour = st == 3 ? new Color(0x8A8A8A) : new Color(0xFFD24A);
                small(g, NAMES[r][c], nameColour, x + CARD_W / 2.0, y + 47, 11f, true);
                String[] d = st == 3 ? new String[] {"Complete the quest", "to unlock"} : DESC[r][c].split("\\|");
                for (int i = 0; i < d.length; i++) small(g, d[i], st == 3 ? new Color(0x8A8A8A) : CREAM, x + CARD_W / 2.0, y + 58 + i * 10, 9.5f, false);
                if (st == 2 || st == 4) g.drawImage(tick(), x + CARD_W - 23, y + 4, null);
                if (st == 3) g.drawImage(lock(), x + CARD_W - 23, y + 4, null);
            }
        }
        if (dialog) {
            g.setColor(a(Color.BLACK, .55));
            g.fill(rr(0, 0, W, H, 14));
            int dx = (W - 260) / 2, dy = (H - 118) / 2;
            g.drawImage(dialog(), dx, dy, null);
            small(g, "This choice is permanent.", new Color(0xFFD24A), W / 2.0, dy + 48, 12f, true);
            small(g, "Are you sure?", new Color(0xFFD24A), W / 2.0, dy + 63, 12f, true);
            g.drawImage(button(96, 28, 0), dx + 26, dy + 76, null);
            small(g, "Yes", new Color(0x3A1E02), dx + 26 + 48, dy + 95, 12f, true);
            g.drawImage(button(96, 28, 2), dx + 138, dy + 76, null);
            small(g, "No", Color.WHITE, dx + 138 + 48, dy + 95, 12f, true);
        }
        g.dispose();
        return img;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        ImageIO.write(mock(new int[][] {{0, 0, 0}, {0, 0}, {0, 0, 0}}, new boolean[] {false, false, false}, false), "png", new File(dir, "v2_1_new.png"));
        ImageIO.write(mock(new int[][] {{0, 1, 0}, {0, 0}, {0, 0, 0}}, new boolean[] {true, false, false}, false), "png", new File(dir, "v2_2_selected.png"));
        ImageIO.write(mock(new int[][] {{0, 1, 0}, {0, 0}, {0, 0, 0}}, new boolean[] {true, false, false}, true), "png", new File(dir, "v2_3_confirm.png"));
        ImageIO.write(mock(new int[][] {{3, 2, 3}, {0, 1}, {4, 0, 0}}, new boolean[] {false, true, false}, false), "png", new File(dir, "v2_4_chosen_locked_done.png"));
        System.out.println("written to " + dir);
    }
}
