package gg.rsmod.plugins.tools

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.Tile
import gg.rsmod.game.tools.importer.Rev667FloorCodec
import gg.rsmod.game.tools.importer.Rev667Loc
import gg.rsmod.game.tools.importer.Rev667LocCodec
import gg.rsmod.game.tools.importer.Rev667LocType
import gg.rsmod.game.tools.importer.Rev667RegionProbeTool
import gg.rsmod.game.tools.importer.Rev667TileCodec
import gg.rsmod.game.tools.importer.Rev667TileMap
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Read-only top-down plan render of any world box, straight from the revision-667 cache, so layout work anywhere in the
 * world can be checked without the client: blended underlays, overlays, height shading, walls, objects and the
 * Safe/Dangerous split ([AreaState]). Next to the PNG it writes `<out>.txt` with every loc (id, name, tile, type,
 * rotation) in the box.
 *
 * Usage: `<cachePath> <xteas.json> <minX> <minZ> <maxX> <maxZ> <out.png> [plane=0] [pixelsPerTile=16]`
 *
 * Never writes the cache; safe to run against a copy while the servers run.
 */
object AreaRenderTool {
    private const val CONFIG_INDEX = 2
    private const val LOC_INDEX = 16

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 7) { "Usage: <cachePath> <xteas.json> <minX> <minZ> <maxX> <maxZ> <out.png> [plane] [pixelsPerTile]" }
        val library = CacheLibrary(args[0])
        try {
            render(
                library,
                Rev667RegionProbeTool.loadKeys(File(args[1])),
                args[2].toInt(),
                args[3].toInt(),
                args[4].toInt(),
                args[5].toInt(),
                File(args[6]),
                args.getOrNull(7)?.toInt() ?: 0,
                args.getOrNull(8)?.toInt() ?: 16,
            )
        } finally {
            library.close()
        }
    }

    private class Floors(val underlay: Map<Int, Int>, val overlay: Map<Int, Int>)

    private fun floors(library: CacheLibrary): Floors {
        val index = library.index(CONFIG_INDEX)
        val textures = textureColours(library)
        val under = HashMap<Int, Int>()
        index.archive(1)?.let { a -> a.fileIds().forEach { id -> a.file(id)?.data?.let { under[id] = Rev667FloorCodec.decodeUnderlay(it).rgb } } }
        val over = HashMap<Int, Int>()
        index.archive(4)?.let { a ->
            a.fileIds().forEach { id ->
                a.file(id)?.data?.let {
                    val def = Rev667FloorCodec.decodeOverlay(it)
                    // The client paints a textured overlay with the texture; its average colour is the honest top-down colour.
                    over[id] = when {
                        def.texture >= 0 && textures[def.texture] != null -> textures[def.texture]!!
                        def.blendRgb != -1 -> def.blendRgb
                        else -> def.rgb
                    }
                }
            }
        }
        return Floors(under, over)
    }

    /**
     * Average colour per texture, as the 667 client reads it (`Js5TextureSource`): materials index 26, group 0 file 0 -
     * count, then per-field column arrays; the HSL average colour is the u16 after the exists/disableable/small/flag/
     * byte/alpha/effectType/effectParam1 columns.
     */
    private fun textureColours(library: CacheLibrary): Map<Int, Int> {
        val data = runCatching { library.data(26, 0, 0) }.getOrNull() ?: return emptyMap()
        var pos = 0

        fun g1() = data[pos++].toInt() and 0xFF

        fun g2() = (g1() shl 8) or g1()
        val count = g2()
        val exists = BooleanArray(count) { g1() == 1 }
        repeat(7) { exists.forEach { if (it) g1() } }
        val result = HashMap<Int, Int>()
        for (i in 0 until count) if (exists[i]) result[i] = hslToRgb(g2())
        return result
    }

    private fun hslToRgb(hsl: Int): Int {
        val h = ((hsl shr 10) and 63) / 64.0 + 0.0078125
        val s = ((hsl shr 7) and 7) / 8.0 + 0.0625
        val l = (hsl and 127) / 128.0
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q

        fun channel(t0: Double): Int {
            var t = t0
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            val v = when {
                6 * t < 1 -> p + (q - p) * 6 * t
                2 * t < 1 -> q
                3 * t < 2 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
            return (v * 255).toInt().coerceIn(0, 255)
        }
        return (channel(h + 1.0 / 3) shl 16) or (channel(h) shl 8) or channel(h - 1.0 / 3)
    }

    private fun locType(
        library: CacheLibrary,
        cache: HashMap<Int, Rev667LocType?>,
        id: Int,
    ): Rev667LocType? =
        cache.getOrPut(id) {
            library.data(LOC_INDEX, id shr 8, id and 0xFF)?.let { runCatching { Rev667LocType.decode(id, it) }.getOrNull() }
        }

    fun render(
        library: CacheLibrary,
        keys: Map<Int, IntArray>,
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
        out: File,
        plane: Int,
        scale: Int,
    ) {
        val floors = floors(library)
        val maps = HashMap<Int, Rev667TileMap?>()
        val locs = HashMap<Int, List<Rev667Loc>>()

        fun map(region: Int): Rev667TileMap? =
            maps.getOrPut(region) {
                Rev667RegionProbeTool.regionGroupData(library, "m${region shr 8}_${region and 0xFF}", null)?.let { Rev667TileCodec.decode(it) }
            }

        fun regionLocs(region: Int): List<Rev667Loc> =
            locs.getOrPut(region) {
                Rev667RegionProbeTool.regionGroupData(library, "l${region shr 8}_${region and 0xFF}", keys[region])
                    ?.let { Rev667LocCodec.decode(it) } ?: emptyList()
            }

        fun tile(
            x: Int,
            z: Int,
            level: Int = plane,
        ) = map(((x shr 6) shl 8) or (z shr 6))?.tiles?.get(level)?.get(x and 63)?.get(z and 63)

        val pad = 5
        val w = maxX - minX + 1
        val h = maxZ - minZ + 1
        val legendWidth = 360
        val img = BufferedImage(w * scale + legendWidth, h * scale, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(24, 24, 24)
        g.fillRect(0, 0, img.width, img.height)

        fun px(x: Int) = (x - minX) * scale

        fun pz(z: Int) = (maxZ - z) * scale

        // Heights (client: explicit byte * 8; missing = procedural, treated as flat here) for simple hill shading.
        fun height(
            x: Int,
            z: Int,
        ): Int = tile(x, z)?.height?.takeIf { it >= 0 } ?: 0

        // Floors: underlays blended over a (2*pad+1)^2 window like the client does, overlays on top, then shading.
        for (x in minX..maxX) for (z in minZ..maxZ) {
            var r = 0
            var gg = 0
            var b = 0
            var n = 0
            for (dx in -pad..pad) for (dz in -pad..pad) {
                val u = tile(x + dx, z + dz)?.underlayId ?: 0
                if (u <= 0) continue
                val rgb = floors.underlay[u - 1] ?: continue
                r += (rgb shr 16) and 0xFF
                gg += (rgb shr 8) and 0xFF
                b += rgb and 0xFF
                n++
            }
            var colour = if (n == 0) 0x202020 else ((r / n) shl 16) or ((gg / n) shl 8) or (b / n)
            val t = tile(x, z)
            val ov = (t?.overlayId ?: 0) and 0xFF
            if (ov > 0) {
                val rgb = floors.overlay[ov - 1]
                if (rgb != null && rgb != 0xFF00FF) colour = rgb
            }
            val slope = (height(x - 1, z) - height(x + 1, z)) + (height(x, z + 1) - height(x, z - 1))
            val shade = (1.0 + slope / 40.0).coerceIn(0.6, 1.3)
            val c = Color(
                (((colour shr 16) and 0xFF) * shade).toInt().coerceIn(0, 255),
                (((colour shr 8) and 0xFF) * shade).toInt().coerceIn(0, 255),
                ((colour and 0xFF) * shade).toInt().coerceIn(0, 255),
            )
            g.color = c
            g.fillRect(px(x), pz(z), scale, scale)
            if (AreaState.isDangerous(Tile(x, z, plane))) {
                g.color = Color(255, 0, 0, 45)
                g.fillRect(px(x), pz(z), scale, scale)
            }
            if ((t?.flags ?: 0) and 1 != 0) { // blocked/water flag
                g.color = Color(0, 0, 0, 60)
                g.fillRect(px(x), pz(z), scale, scale)
            }
        }

        // Faint tile grid and a coordinate every 8 tiles.
        g.color = Color(0, 0, 0, 40)
        for (x in minX..maxX + 1) g.drawLine(px(x), 0, px(x), h * scale)
        for (z in minZ - 1..maxZ) g.drawLine(0, pz(z), w * scale, pz(z))

        // Safe/Dangerous boundary as a bold line.
        g.stroke = BasicStroke(3f)
        g.color = Color(255, 40, 40)
        for (x in minX..maxX) for (z in minZ..maxZ) {
            val d = AreaState.isDangerous(Tile(x, z, plane))
            if (x < maxX && d != AreaState.isDangerous(Tile(x + 1, z, plane))) g.drawLine(px(x + 1), pz(z), px(x + 1), pz(z) + scale)
            if (z < maxZ && d != AreaState.isDangerous(Tile(x, z + 1, plane))) g.drawLine(px(x), pz(z), px(x) + scale, pz(z))
        }

        // Locs.
        val types = HashMap<Int, Rev667LocType?>()
        val listing = StringBuilder()
        val legend = LinkedHashMap<Int, String>()
        val regions = HashSet<Int>()
        for (rx in (minX shr 6)..(maxX shr 6)) for (rz in (minZ shr 6)..(maxZ shr 6)) regions.add((rx shl 8) or rz)
        val placed = regions.sorted().flatMap { region ->
            regionLocs(region).map { Triple((region shr 8) * 64 + it.localX, (region and 0xFF) * 64 + it.localZ, it) }
        }.filter { (x, z, loc) -> loc.plane == plane && x in minX..maxX && z in minZ..maxZ }
            .sortedByDescending { it.third.type == 22 } // ground decor first, underneath the rest

        g.font = Font(Font.SANS_SERIF, Font.BOLD, (scale / 2.6).toInt().coerceAtLeast(8))
        for ((x, z, loc) in placed) {
            val def = locType(library, types, loc.id)
            val name = def?.name ?: "?"
            listing.append("${loc.id}\t$name\t$x,$z,$plane\ttype=${loc.type}\trot=${loc.rotation}\n")
            val colour = colourFor(name, loc.id)
            val x0 = px(x)
            val y0 = pz(z)
            when (loc.type) {
                in 0..3 -> { // walls: edge line by rotation (0 west, 1 north, 2 east, 3 south); corners/L as both
                    g.stroke = BasicStroke((scale / 5f).coerceAtLeast(2f))
                    g.color = Color(40, 36, 32)
                    val edges = when (loc.type) {
                        0, 1, 3 -> listOf(loc.rotation)
                        else -> listOf(loc.rotation, (loc.rotation + 1) and 3)
                    }
                    for (e in edges) {
                        when (e) {
                            0 -> g.drawLine(x0, y0, x0, y0 + scale)
                            1 -> g.drawLine(x0, y0, x0 + scale, y0)
                            2 -> g.drawLine(x0 + scale, y0, x0 + scale, y0 + scale)
                            3 -> g.drawLine(x0, y0 + scale, x0 + scale, y0 + scale)
                        }
                    }
                }
                9 -> {
                    g.stroke = BasicStroke((scale / 5f).coerceAtLeast(2f))
                    g.color = Color(40, 36, 32)
                    if (loc.rotation and 1 == 0) g.drawLine(x0, y0 + scale, x0 + scale, y0) else g.drawLine(x0, y0, x0 + scale, y0 + scale)
                }
                in 4..8 -> { // wall decoration: small marker
                    g.color = colour
                    g.fillOval(x0 + scale / 3, y0 + scale / 3, scale / 3, scale / 3)
                }
                22 -> { // ground decoration: translucent dot, unnamed clutter (grass tufts, pebbles) left out
                    if (name == "null") continue
                    g.color = Color(colour.red, colour.green, colour.blue, 110)
                    g.fillOval(x0 + scale / 4, y0 + scale / 4, scale / 2, scale / 2)
                }
                else -> { // 10/11 objects (and roofs 12-21 drawn faintly)
                    val sx = if (loc.rotation and 1 == 1) def?.sizeZ ?: 1 else def?.sizeX ?: 1
                    val sz = if (loc.rotation and 1 == 1) def?.sizeX ?: 1 else def?.sizeZ ?: 1
                    val top = pz(z + sz - 1)
                    if (loc.type in 12..21) {
                        g.color = Color(120, 70, 50, 50)
                        g.fillRect(x0, top, sx * scale, sz * scale)
                    } else {
                        // Unnamed locs are scenery shells (building pieces, roofs): shown faintly so the floor stays readable.
                        g.color = Color(colour.red, colour.green, colour.blue, if (name == "null") 70 else 210)
                        g.fillRect(x0 + 1, top + 1, sx * scale - 2, sz * scale - 2)
                        g.stroke = BasicStroke(1f)
                        g.color = Color(0, 0, 0, 160)
                        g.drawRect(x0 + 1, top + 1, sx * scale - 2, sz * scale - 2)
                        legend.putIfAbsent(loc.id, name)
                        val key = legend.keys.indexOf(loc.id)
                        if (scale >= 12) {
                            g.color = Color.WHITE
                            g.drawString("${key + 1}", x0 + 2, top + g.fontMetrics.ascent)
                        }
                    }
                }
            }
        }

        // Coordinates.
        g.font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        g.color = Color(255, 255, 255, 200)
        for (x in minX..maxX) if (x % 8 == 0) g.drawString("$x", px(x) + 2, 12)
        for (z in minZ..maxZ) if (z % 8 == 0) g.drawString("$z", 2, pz(z) + scale - 2)

        // Legend: numbered object types.
        val lx = w * scale + 10
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        g.color = Color.WHITE
        g.drawString("$minX,$minZ - $maxX,$maxZ plane $plane", lx, 16)
        g.drawString("red tint/line = Dangerous", lx, 32)
        var ly = 52
        legend.entries.forEachIndexed { i, (id, name) ->
            if (ly > img.height - 6) return@forEachIndexed
            g.color = colourFor(name, id)
            g.fillRect(lx, ly - 10, 10, 10)
            g.color = Color.WHITE
            g.drawString("${i + 1}. $name ($id)", lx + 14, ly)
            ly += 15
        }
        g.dispose()
        out.absoluteFile.parentFile?.mkdirs()
        ImageIO.write(img, "png", out)
        File(out.path.removeSuffix(".png") + ".txt").writeText(listing.toString())
        println("RENDERED ${out.absolutePath} ${img.width}x${img.height} locs=${placed.size} types=${legend.size}")
    }

    /** Readable, stable colours: known material words first, otherwise a hash of the name. */
    private fun colourFor(
        name: String,
        id: Int,
    ): Color {
        val n = name.lowercase()
        return when {
            listOf("tree", "bush", "plant", "hedge", "flower", "fern", "grass").any { it in n } -> Color(46, 139, 60)
            listOf("rock", "stone", "boulder", "statue", "pillar").any { it in n } -> Color(140, 140, 150)
            listOf("water", "fountain", "pool").any { it in n } -> Color(60, 110, 200)
            listOf("bank", "booth", "chest").any { it in n } -> Color(200, 160, 40)
            listOf("torch", "fire", "brazier", "lamp", "candle").any { it in n } -> Color(255, 120, 20)
            listOf("banner", "flag", "sign").any { it in n } -> Color(170, 60, 200)
            listOf("table", "chair", "bench", "stool", "crate", "barrel").any { it in n } -> Color(150, 95, 50)
            listOf("door", "gate").any { it in n } -> Color(110, 70, 30)
            else -> {
                val hash = (name.hashCode() * 31 + id).let { it xor (it ushr 13) }
                Color.getHSBColor(((hash and 0xFFFF) / 65535f), 0.55f, 0.85f)
            }
        }
    }
}
