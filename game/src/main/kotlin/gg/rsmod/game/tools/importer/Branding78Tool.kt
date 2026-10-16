package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The owner's definitive Legacy Deadman artwork (2026-10-06, `tools/78-branding-approved`, SHA256-pinned, never edited)
 * as three new cache objects for the developer place mode (`agent-handoffs/2026-10-06-78-branding-plaatsmodus-opdracht.txt`):
 *
 *  * "78 logo" - the round logo on a thin round wall plate (navy rim and back);
 *  * "78 wide banner" - the 3:1 banner as a wide cloth on an iron rod with wall brackets;
 *  * "78 flag" - the 3:2 flag hanging from a horizontal pole on a facade bracket (wall plate, pole, strut, finial).
 *
 * Textures: one 1024 x 1024 alpha-tested, clamped, mipmapped texture per design. The logo fills its square; the banner
 * and the flag keep their own proportions as a band at the top of the square (1024 x 341 and 1024 x 683) - the planar
 * texture spaces of the cloth only cover that band (the N corner sits below the cloth), so nothing is stretched to a
 * square. The RSPS client builds these three at 1024 on the hardware toolkits (RspsTextures); the software toolkit
 * draws every texture at its shared 128.
 *
 * Geometry follows [HouseBannerTool]: model units 128 per tile, negative y is up, origin at the tile centre; at rotation
 * 0 a wall shape (4) hangs on the north tile edge (z = +64) facing the tile. Each loc carries the model for wall
 * decoration (shape 4) and scenery (shape 10), is walk-through and option-less. Per-placement position / height /
 * distance / scale / angle are not in the cache: the server sends them per placement (LOC_CUSTOMISE, LocFit).
 *
 * Ids live in [IDS_FILE]; a later run replaces the same entries. Usage: `Branding78Tool plan|apply` (apply needs both
 * servers stopped; CacheTransaction journals a backup and verifies both caches).
 */
object Branding78Tool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private val SOURCE_DIR = RspsPaths.root("tools/78-branding-approved")
    private val DERIVED_DIR = "$SOURCE_DIR/derived"
    private val IDS_FILE = "$DERIVED_DIR/branding78_ids.properties"
    private const val MODEL_INDEX = 7
    private const val LOC_INDEX = 16

    /** Plain-sprite 667 texture with alpha test (materials alphaBlendMode 1), the same template HouseBannerTool used. */
    private const val TEMPLATE_TEXTURE = 12
    private const val COL_MIPMAP = 12
    private const val COL_WRAP_U = 13
    private const val COL_WRAP_V = 14
    private const val COL_AVERAGE = 7
    private const val COL_ALPHA_MODE = 18

    const val TEXTURE_SIZE = 1024

    class Source(val file: String, val sha256: String, val width: Int, val height: Int)

    val LOGO = Source("78-logo-definitief.png", "FBC165E98655D07BE4A3F3B364E0E753408A2A74D84A4EBAC4E96082E24DC77B", 1254, 1254)
    val BANNER = Source("78-banner-ontwerp.png", "D8EC80B5D683E38BF3E1E766DECCC7B63FF7BDB5675DB7AA4221F03413924981", 2172, 724)
    val FLAG = Source("78-vlag-ontwerp.png", "F490BBEFC2A565782FE0F7AAB1268CEF34904EC5BEB9A6030753E726B66F5CB9", 1536, 1024)

    private fun hsl(h: Int, s: Int, l: Int) = ((h shl 10) or (s shl 7) or l).toShort()

    /** Navy of the artwork for rims, plates and the back of the logo; dark iron and steel as the house banners' rod. */
    private val NAVY = hsl(38, 6, 22)
    private val IRON_DARK = hsl(0, 0, 14)
    private val IRON = hsl(0, 0, 26)
    private val STEEL = hsl(0, 0, 44)
    private val CLOTH_BASE = hsl(0, 0, 127)

    /** Pixel height of the artwork band at the top of each square texture (the logo fills the square). */
    fun bandHeight(source: Source): Int = Math.round(source.height.toDouble() * TEXTURE_SIZE / source.width).toInt().coerceAtMost(TEXTURE_SIZE)

    fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02X".format(it) }

    /** Loads a pinned source: the bytes must still hash to the approved SHA256 and have the approved size. */
    fun load(source: Source): BufferedImage {
        val file = File(SOURCE_DIR, source.file)
        check(file.exists()) { "approved artwork missing: $file" }
        val hash = sha256(file)
        check(hash == source.sha256) { "${source.file} is not the approved file: SHA256 $hash" }
        val image = ImageIO.read(file) ?: error("cannot read ${source.file}")
        check(image.width == source.width && image.height == source.height) { "${source.file} is ${image.width}x${image.height}" }
        return image
    }

    /**
     * The square texture for [source]: the artwork scaled (premultiplied alpha, bicubic, in halving steps) to
     * 1024 wide, drawn at the top; the rest of the square stays fully transparent (alpha 0 = cut by the alpha test).
     * Colours are not altered; an RGB source (banner, flag: the white areas are part of the design) is fully opaque.
     */
    fun texture(source: Source, image: BufferedImage): BufferedImage {
        var img = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB_PRE)
        img.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        val targetW = TEXTURE_SIZE
        val targetH = bandHeight(source)
        while (img.width / 2 >= targetW) {
            val w = img.width / 2
            val h = Math.round(img.height / 2.0).toInt()
            img = resize(img, w, h)
        }
        if (img.width != targetW || img.height != targetH) img = resize(img, targetW, targetH)
        val out = BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
        return out
    }

    private fun resize(img: BufferedImage, w: Int, h: Int): BufferedImage {
        val next = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
        next.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(img, 0, 0, w, h, null)
            dispose()
        }
        return next
    }

    /** Mesh under construction (same conventions as HouseBannerTool). */
    class Mesh {
        val vx = ArrayList<Int>()
        val vy = ArrayList<Int>()
        val vz = ArrayList<Int>()
        val fa = ArrayList<Int>()
        val fb = ArrayList<Int>()
        val fc = ArrayList<Int>()
        val colour = ArrayList<Short>()
        val texture = ArrayList<Short>()
        val space = ArrayList<Byte>()
        val spaces = ArrayList<Triple<Int, Int, Int>>()

        fun v(x: Int, y: Int, z: Int): Int {
            vx += x; vy += y; vz += z
            return vx.size - 1
        }

        fun f(a: Int, b: Int, c: Int, col: Short, tex: Int = -1, sp: Int = -1) {
            fa += a; fb += b; fc += c; colour += col; texture += tex.toShort(); space += sp.toByte()
        }

        /** Triangle in both windings (solid from both sides). */
        fun f2(a: Int, b: Int, c: Int, col: Short) {
            f(a, b, c, col); f(a, c, b, col)
        }

        /** Axis-aligned box, both windings. */
        fun box(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, col: Short) {
            val p = IntArray(8) { i -> v(if (i and 1 == 0) x0 else x1, if (i and 2 == 0) y0 else y1, if (i and 4 == 0) z0 else z1) }
            val quads = listOf(intArrayOf(0, 1, 3, 2), intArrayOf(4, 6, 7, 5), intArrayOf(0, 4, 5, 1), intArrayOf(2, 3, 7, 6), intArrayOf(0, 2, 6, 4), intArrayOf(1, 5, 7, 3))
            quads.forEach { q ->
                f2(p[q[0]], p[q[1]], p[q[2]], col); f2(p[q[0]], p[q[2]], p[q[3]], col)
            }
        }

        /** A square-section bar of half-width [r] from [p0] to [p1] along any direction, both windings. */
        fun bar(p0: DoubleArray, p1: DoubleArray, r: Double, col: Short) {
            val d = doubleArrayOf(p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2])
            val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
            val n = doubleArrayOf(d[0] / len, d[1] / len, d[2] / len)
            // Perpendicular frame: u = x axis unless the bar runs along x, then z.
            val seed = if (Math.abs(n[0]) < 0.9) doubleArrayOf(1.0, 0.0, 0.0) else doubleArrayOf(0.0, 0.0, 1.0)
            val u = cross(n, seed).let { normalise(it) }
            val w = cross(n, u)
            val corners = ArrayList<Int>()
            for (end in listOf(p0, p1)) {
                for ((su, sw) in listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1)) {
                    corners += v(
                        Math.round(end[0] + u[0] * su * r + w[0] * sw * r).toInt(),
                        Math.round(end[1] + u[1] * su * r + w[1] * sw * r).toInt(),
                        Math.round(end[2] + u[2] * su * r + w[2] * sw * r).toInt(),
                    )
                }
            }
            // Sides.
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                f2(corners[i], corners[j], corners[4 + j], col); f2(corners[i], corners[4 + j], corners[4 + i], col)
            }
            // Caps.
            f2(corners[0], corners[1], corners[2], col); f2(corners[0], corners[2], corners[3], col)
            f2(corners[4], corners[6], corners[5], col); f2(corners[4], corners[7], corners[6], col)
        }

        /** Four-sided spear point from [base] along unit direction [n], [len] long, base half-width [r], both windings. */
        fun spear(base: DoubleArray, n: DoubleArray, len: Double, r: Double, col: Short) {
            val seed = if (Math.abs(n[0]) < 0.9) doubleArrayOf(1.0, 0.0, 0.0) else doubleArrayOf(0.0, 0.0, 1.0)
            val u = normalise(cross(n, seed))
            val w = cross(n, u)
            val tip = v(Math.round(base[0] + n[0] * len).toInt(), Math.round(base[1] + n[1] * len).toInt(), Math.round(base[2] + n[2] * len).toInt())
            val mid = len / 3
            val c = doubleArrayOf(base[0] + n[0] * mid, base[1] + n[1] * mid, base[2] + n[2] * mid)
            val ring = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1).map { (su, sw) ->
                v(Math.round(c[0] + u[0] * su * r + w[0] * sw * r).toInt(), Math.round(c[1] + u[1] * su * r + w[1] * sw * r).toInt(), Math.round(c[2] + u[2] * su * r + w[2] * sw * r).toInt())
            }
            val b = v(Math.round(base[0]).toInt(), Math.round(base[1]).toInt(), Math.round(base[2]).toInt())
            for (i in 0 until 4) {
                f2(tip, ring[i], ring[(i + 1) % 4], col); f2(b, ring[(i + 1) % 4], ring[i], col)
            }
        }

        /**
         * A textured cloth of [w] x [h] whose top edge is at [top], built in "banner space" (x across, y down, the
         * viewer towards -z, shallow folds along z) and mapped through [place] into the model. The texture holds the
         * artwork as a band of [bandPx] rows at the top of a [TEXTURE_SIZE] square: the texture space's N corner sits
         * h * TEXTURE_SIZE / bandPx below the top, so only the band covers the cloth (front and mirrored back space).
         */
        fun cloth(w: Int, h: Int, top: Int, texId: Int, bandPx: Int, foldDepth: Double, place: (Int, Int, Int) -> IntArray) {
            val cols = 13
            val rows = 4
            val grid = Array(rows) { IntArray(cols) }
            fun put(x: Int, y: Int, z: Int): Int {
                val p = place(x, y, z)
                return v(p[0], p[1], p[2])
            }
            for (c in 0 until cols) {
                val fx = c / (cols - 1.0)
                val x = (-w / 2.0 + fx * w).toInt()
                for (r in 0 until rows) {
                    val fy = r / (rows - 1.0)
                    val fold = (foldDepth * fy * sin(fx * 4 * PI)).toInt()
                    grid[r][c] = put(x, (top + h * fy).toInt(), fold)
                }
            }
            val nY = top + Math.round(h.toDouble() * TEXTURE_SIZE / bandPx).toInt()
            val nLeft = put(-w / 2, nY, 0)
            val nRight = put(w / 2, nY, 0)
            val front = spaces.size
            spaces += Triple(grid[0][0], grid[0][cols - 1], nLeft)
            val back = spaces.size
            spaces += Triple(grid[0][cols - 1], grid[0][0], nRight)
            for (r in 0 until rows - 1) for (c in 0 until cols - 1) {
                val a = grid[r][c]
                val b = grid[r][c + 1]
                val d = grid[r + 1][c]
                val e = grid[r + 1][c + 1]
                f(a, b, e, CLOTH_BASE, texId, front); f(a, e, d, CLOTH_BASE, texId, front)
                f(a, e, b, CLOTH_BASE, texId, back); f(a, d, e, CLOTH_BASE, texId, back)
            }
        }

        fun build(): ModelData {
            val m = ModelData(vx.size, fa.size, spaces.size)
            for (i in vx.indices) { m.vertexX[i] = vx[i]; m.vertexY[i] = vy[i]; m.vertexZ[i] = vz[i] }
            for (i in fa.indices) { m.faceA[i] = fa[i]; m.faceB[i] = fb[i]; m.faceC[i] = fc[i]; m.faceColour[i] = colour[i] }
            m.faceTexture = texture.toShortArray()
            m.faceTexSpace = space.toByteArray()
            m.texMappingType = ByteArray(spaces.size)
            m.texSpaceDefA = spaces.map { it.first.toShort() }.toShortArray()
            m.texSpaceDefB = spaces.map { it.second.toShort() }.toShortArray()
            m.texSpaceDefC = spaces.map { it.third.toShort() }.toShortArray()
            return m
        }

        private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

        private fun normalise(a: DoubleArray): DoubleArray {
            val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
            return doubleArrayOf(a[0] / l, a[1] / l, a[2] / l)
        }
    }

    /** Wall plane and the plate's distance off it (2 units: no z-fighting with the wall). */
    private const val WALL_Z = 64

    /**
     * Round wall plate: a 32-sided disc of radius 80 (1.25 tiles across), centre 220 units up, 6 units thick, 2 units
     * off the wall. Front face textured through a planar space whose P/M/N corners are the disc's bounding square
     * (three extra vertices inside the bounds); navy rim and back.
     */
    fun logoModel(texId: Int): ModelData =
        Mesh().apply {
            val r = 80
            val cy = -220
            val zBack = WALL_Z - 2
            val zFront = zBack - 6
            val n = 32
            val front = IntArray(n) { i -> val a = 2 * PI * i / n; v(Math.round(r * Math.cos(a)).toInt(), Math.round(cy + r * Math.sin(a)).toInt(), zFront) }
            val back = IntArray(n) { i -> val a = 2 * PI * i / n; v(Math.round(r * Math.cos(a)).toInt(), Math.round(cy + r * Math.sin(a)).toInt(), zBack) }
            val cf = v(0, cy, zFront)
            val cb = v(0, cy, zBack)
            // Textured both ways round exactly like the house banners' cloth (front winding + front space, reverse
            // winding + mirrored space), so the logo reads correctly from whichever side the client draws
            // (2026-10-06: with one winding the client showed the plate's navy inside, screens 10-logo-512.png).
            val frontSpace = spaces.size
            spaces += Triple(v(-r, cy - r, zFront), v(r, cy - r, zFront), v(-r, cy + r, zFront))
            val backSpace = spaces.size
            spaces += Triple(v(r, cy - r, zFront), v(-r, cy - r, zFront), v(r, cy + r, zFront))
            for (i in 0 until n) {
                val j = (i + 1) % n
                f(cf, front[i], front[j], CLOTH_BASE, texId, frontSpace)
                f(cf, front[j], front[i], CLOTH_BASE, texId, backSpace)
                f2(cb, back[j], back[i], NAVY)
                f2(front[i], front[j], back[j], NAVY); f2(front[i], back[j], back[i], NAVY)
            }
        }.build()

    /** Wide banner: 288 x 96 cloth (3:1, 2.25 tiles wide) on an iron rod 330 up, 16 units off the wall, two brackets. */
    fun wideBannerModel(texId: Int): ModelData =
        Mesh().apply {
            val w = 288
            val h = 96
            val rodY = -330
            val thick = 6
            val z = WALL_Z - 16
            val half = w / 2 + thick * 2
            box(-half, rodY - thick, z - thick, half, rodY + thick, z + thick, IRON_DARK)
            box(-half - 4, rodY - thick - 2, z - thick - 2, -half + 6, rodY + thick + 2, z + thick + 2, IRON)
            box(half - 6, rodY - thick - 2, z - thick - 2, half + 4, rodY + thick + 2, z + thick + 2, IRON)
            spear(doubleArrayOf(-half - 4.0, rodY.toDouble(), z.toDouble()), doubleArrayOf(-1.0, 0.0, 0.0), thick * 6.0, thick * 1.8, STEEL)
            spear(doubleArrayOf(half + 4.0, rodY.toDouble(), z.toDouble()), doubleArrayOf(1.0, 0.0, 0.0), thick * 6.0, thick * 1.8, STEEL)
            box(-w / 3 - thick, rodY - thick, z, -w / 3 + thick, rodY + thick, WALL_Z, IRON)
            box(w / 3 - thick, rodY - thick, z, w / 3 + thick, rodY + thick, WALL_Z, IRON)
            cloth(w, h, rodY + thick, texId, bandHeight(BANNER), w / 90.0) { x, y, fold -> intArrayOf(x, y, z + fold) }
        }.build()

    /**
     * Facade flag: wall plate, a horizontal pole out of the wall (164 units, 1.3 tiles) 338 up with a steel finial, a
     * diagonal strut back to the wall, and the 132 x 88 cloth (3:2) hanging under the pole, its plane across the wall
     * (seen from both sides along the facade; the place mode's angle turns it).
     */
    fun flagModel(texId: Int): ModelData =
        Mesh().apply {
            val poleY = -338
            val poleOut = -104
            box(-14, poleY - 14, WALL_Z - 4, 14, poleY + 14, WALL_Z, IRON_DARK)
            bar(doubleArrayOf(0.0, poleY.toDouble(), WALL_Z - 2.0), doubleArrayOf(0.0, poleY.toDouble(), poleOut.toDouble()), 4.0, IRON_DARK)
            spear(doubleArrayOf(0.0, poleY.toDouble(), poleOut.toDouble()), doubleArrayOf(0.0, 0.0, -1.0), 26.0, 8.0, STEEL)
            bar(doubleArrayOf(0.0, poleY + 40.0, WALL_Z - 2.0), doubleArrayOf(0.0, poleY.toDouble(), poleOut + 44.0), 3.0, IRON)
            val w = 132
            val h = 88
            val zc = (40 + (poleOut + 12)) / 2
            // Banner space (x across, viewer at -z) turned a quarter: across -> -z (outwards), viewer side -> -x.
            cloth(w, h, poleY + 4, texId, bandHeight(FLAG), w / 70.0) { x, y, fold -> intArrayOf(fold, y, zc - x) }
        }.build()

    /**
     * Loc definition as HouseBannerTool.locDef: shapes with one model, name, interactive 0, walk-through, extra ambient -
     * plus opcode 177, the 667 client's "always a dynamic loc" flag (LocType code 177 -> MapRegion isStatic = false): only a
     * dynamic loc owns a LocEntity, and only that takes a LOC_CUSTOMISE (the per-placement fit). Without it the client built
     * a StaticWallDecor and silently dropped the customisation (seen 2026-10-06, client trace setLocCustomisation loc=StaticWallDecor).
     */
    private fun locDef(name: String, model: Int, shapes: List<Int>): ByteArray =
        ByteArrayOutputStream().apply {
            write(1); write(shapes.size)
            shapes.forEach { shape -> write(shape); write(1); write(model shr 8); write(model and 0xFF) }
            write(2); name.forEach { write(it.code) }; write(0)
            write(19); write(0)
            write(17)
            write(29); write(24)
            write(177)
            write(0)
        }.toByteArray()

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val idsFile = File(IDS_FILE)
        val ids = Properties().apply { if (idsFile.exists()) idsFile.inputStream().use { load(it) } }
        File(DERIVED_DIR).mkdirs()

        val designs = listOf("logo" to LOGO, "banner" to BANNER, "flag" to FLAG)
        val images = designs.associate { (key, source) -> key to texture(source, load(source)) }
        images.forEach { (key, image) -> ImageIO.write(image, "png", File(DERIVED_DIR, "78-$key-texture-$TEXTURE_SIZE.png")) }

        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            fun fresh(index: Int) = (library.index(index).archiveIds().maxOrNull() ?: 0) + 1
            val materials = OsrsTextureImportTool.Materials.decode(library.data(OsrsTextureImportTool.INDEX_MATERIALS, 0, 0) ?: error("materials missing"))
            val firstSprite = fresh(OsrsTextureImportTool.INDEX_SPRITES)
            val firstTexture = materials.present.size
            // Model ids must fit the 16-bit loc model field and the model index already runs past 65535, so new models take
            // proven-free holes from the namespace census, as every map/loc import tool here does (Ferox, Shrine of Ralos ...).
            val freeModels =
                if (designs.all { (key, _) -> ids.containsKey("$key.model") }) {
                    emptyList()
                } else {
                    val census = ModelNamespaceCensusTool.census(GAME_CACHE, FILE_SERVER_CACHE, File(FeroxImportTool.ASSET_MAP))
                    check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                    census.provenFreeHoles.filter { it in 1..0xFFFF && library.data(MODEL_INDEX, it, 0) == null }.sorted()
                }
            val lastLoc = library.index(LOC_INDEX).archiveIds().maxOrNull()!!.let { g -> (g shl 8) + (library.index(LOC_INDEX).archive(g)!!.fileIds().maxOrNull() ?: 0) }
            val sprite = HashMap<String, Int>()
            val texture = HashMap<String, Int>()
            val model = HashMap<String, Int>()
            val loc = HashMap<String, Int>()
            designs.forEachIndexed { i, (key, _) ->
                sprite[key] = ids.getProperty("$key.sprite")?.toInt() ?: (firstSprite + i)
                texture[key] = ids.getProperty("$key.texture")?.toInt() ?: (firstTexture + i)
                model[key] = ids.getProperty("$key.model")?.toInt() ?: (freeModels.getOrNull(i) ?: error("no proven-free model id left below 65536 (census holes: ${freeModels.size})"))
                loc[key] = ids.getProperty("$key.loc")?.toInt() ?: (lastLoc + 1 + i)
            }
            check(model.values.all { it <= 0xFFFF }) { "model ids $model do not fit the loc model field" }
            println("IDS sprites=$sprite textures=$texture models=$model locs=$loc")

            fun put(index: Int, group: Int, file: Int, bytes: ByteArray, label: String) {
                val current = library.data(index, group, file)
                if (current != null && current.contentEquals(bytes)) return
                mutations += CacheMutation(index, group, file, bytes, label, current?.let { CacheItemProbeTool.sha1(it) })
            }

            val template = materials.rows[TEMPLATE_TEXTURE] ?: error("template texture $TEMPLATE_TEXTURE has no metrics")
            check(template[COL_ALPHA_MODE][0].toInt() == 1) { "template $TEMPLATE_TEXTURE is not alpha-tested" }
            check(template[OsrsTextureImportTool.COL_SPEED_U][0].toInt() == 0 && template[OsrsTextureImportTool.COL_SPEED_V][0].toInt() == 0) { "template scrolls" }
            val highest = texture.values.maxOrNull()!!
            val present = if (highest < materials.present.size) materials.present.copyOf() else materials.present.copyOf(highest + 1)
            val rows = if (highest < materials.rows.size) materials.rows.copyOf() else materials.rows.copyOf(highest + 1)
            val templateProgram = library.data(OsrsTextureImportTool.INDEX_TEXTURES, TEMPLATE_TEXTURE) ?: error("template program missing")
            val at = OsrsTextureImportTool.spriteParamOffset(templateProgram)

            designs.forEach { (key, source) ->
                val image = images[key]!!
                put(OsrsTextureImportTool.INDEX_SPRITES, sprite[key]!!, 0, StoreArtTool.encode(image), "78 $key sprite ${image.width}x${image.height} with alpha (${source.file})")
                val row = Array(template.size) { template[it].copyOf() }
                row[COL_WRAP_U][0] = 0
                row[COL_WRAP_V][0] = 0
                row[COL_MIPMAP][0] = 2
                val average = Banner78Art.averageHsl(image)
                row[COL_AVERAGE][0] = (average shr 8).toByte()
                row[COL_AVERAGE][1] = average.toByte()
                rows[texture[key]!!] = row
                present[texture[key]!!] = true
                put(OsrsTextureImportTool.INDEX_TEXTURES, texture[key]!!, 0, templateProgram.copyOf().also { it[at] = (sprite[key]!! ushr 8).toByte(); it[at + 1] = sprite[key]!!.toByte() }, "78 $key texture program (alpha test)")
            }
            put(OsrsTextureImportTool.INDEX_MATERIALS, 0, 0, OsrsTextureImportTool.Materials(present, rows).encode(), "materials rows ${texture.values.sorted()} (78 logo/banner/flag: alpha test, clamp, mipmaps)")

            val meshes = mapOf("logo" to logoModel(texture["logo"]!!), "banner" to wideBannerModel(texture["banner"]!!), "flag" to flagModel(texture["flag"]!!))
            meshes.forEach { (key, data) ->
                val encoded = Rev667ModelEncoder.encode(data)
                val differences = ModelConvertTool.compare(data, Rev667ModelDecoder.decode(encoded))
                check(differences.isEmpty()) { "model $key round trip: $differences" }
                put(MODEL_INDEX, model[key]!!, 0, encoded, "78 $key model ${model[key]} (${data.vertexCount} vertices, ${data.faceCount} faces)")
            }
            val names = mapOf("logo" to "78 logo", "banner" to "78 wide banner", "flag" to "78 flag")
            designs.forEach { (key, _) ->
                val id = loc[key]!!
                val bytes = locDef(names[key]!!, model[key]!!, listOf(4, 10))
                check(Rev667LocType.decode(id, bytes).allModels.isNotEmpty()) { "loc $id re-decode failed" }
                put(LOC_INDEX, id ushr 8, id and 0xFF, bytes, "loc $id ${names[key]} (wall decoration + scenery)")
            }
            designs.forEach { (key, _) ->
                ids.setProperty("$key.sprite", "${sprite[key]}"); ids.setProperty("$key.texture", "${texture[key]}")
                ids.setProperty("$key.model", "${model[key]}"); ids.setProperty("$key.loc", "${loc[key]}")
            }
        } finally {
            library.close()
        }
        mutations.forEach { println("MUTATION idx${it.indexId}/${it.groupId}/${it.fileId} ${it.label}") }
        if (mutations.isEmpty()) return println("NOTHING_TO_DO")
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") return println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        idsFile.outputStream().use { ids.store(it, "78 Legacy Deadman branding cache ids (Branding78Tool)") }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir}); ids -> $IDS_FILE")
    }
}
