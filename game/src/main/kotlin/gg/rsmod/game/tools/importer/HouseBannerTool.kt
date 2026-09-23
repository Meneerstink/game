package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.sin

/**
 * The 78 Deadman house banner as reusable cache content. Owner 2026-09-24: import the supplied flag artwork exactly
 * (`tools/ge-banner/banner78_master.png`, black cloth, white skull and "78", purple effects, no gold) at the highest
 * quality the 667 client supports, in several formats.
 *
 * One texture ([Banner78Art.masterTexture]: the master's cloth, 512 x 512 with its alpha, alpha-tested, mipmapped,
 * clamped) and three meshes, all with a modelled dark-iron rod and spear finials:
 *  * [wallModel] "78 banner" - a bank-sized wall banner hung from iron brackets against the north edge of its tile;
 *  * [standardModel] "78 standard" - the flag on a crossbar at the top of a free-standing iron pole;
 *  * [largeModel] "78 banner" (large) - a gatehouse/facade-sized wall banner.
 * Wall banners carry models for wall decoration (shape 4) and scenery (shape 10), so they can hang on a wall without
 * taking the wall's own client layer slot.
 *
 * The cloth is a flat grid with shallow folds, textured front and back through planar texture spaces whose P/M/N
 * corners are the cloth's corners; the back space is mirrored so the art never reads backwards. Model units: 128 per
 * tile, negative y is up. The 667 client builds textures at one global size (128); the RSPS client builds this texture
 * at 512 on the OpenGL/native toolkits (RspsTextures), the software toolkit keeps its shared 128.
 *
 * Ids are kept in [IDS_FILE]; later runs replace the same entries, so art or geometry can be refined without new ids.
 * Usage: `HouseBannerTool plan|apply` (apply needs the servers stopped; CacheTransaction journals a backup).
 */
object HouseBannerTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val IDS_FILE = "C:/RSPS/tools/ge-banner/house_banner_ids.properties"
    private const val MODEL_INDEX = 7
    private const val LOC_INDEX = 16

    /** Plain-sprite 667 texture with alpha test (materials alphaBlendMode 1, program output byte 34), used as template. */
    private const val TEMPLATE_TEXTURE = 12

    /** Materials columns (Js5TextureSource order): mipmaps, wrap U, wrap V, average colour, alpha blend mode. */
    private const val COL_MIPMAP = 12
    private const val COL_WRAP_U = 13
    private const val COL_WRAP_V = 14
    private const val COL_AVERAGE = 7
    private const val COL_ALPHA_MODE = 18

    private fun hsl(h: Int, s: Int, l: Int) = ((h shl 10) or (s shl 7) or l).toShort()

    /** The master's rod: near-black iron with lighter steel edges and points. */
    private val IRON_DARK = hsl(0, 0, 14)
    private val IRON = hsl(0, 0, 26)
    private val STEEL = hsl(0, 0, 44)
    private val CLOTH_BASE = hsl(0, 0, 127)

    /** Mesh under construction. */
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
        val spaces = ArrayList<Triple<Int, Int, Int>>()

        fun v(x: Int, y: Int, z: Int): Int {
            vx += x; vy += y; vz += z
            return vx.size - 1
        }

        fun f(a: Int, b: Int, c: Int, col: Short, tex: Int = -1, sp: Int = -1) {
            fa += a; fb += b; fc += c; colour += col; texture += tex.toShort(); space += sp.toByte()
        }

        /** Axis-aligned box as 12 triangles, both windings so it is solid from every side. */
        fun box(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int, col: Short) {
            val p = IntArray(8) { i -> v(if (i and 1 == 0) x0 else x1, if (i and 2 == 0) y0 else y1, if (i and 4 == 0) z0 else z1) }
            val quads = listOf(intArrayOf(0, 1, 3, 2), intArrayOf(4, 6, 7, 5), intArrayOf(0, 4, 5, 1), intArrayOf(2, 3, 7, 6), intArrayOf(0, 2, 6, 4), intArrayOf(1, 5, 7, 3))
            quads.forEach { q ->
                f(p[q[0]], p[q[1]], p[q[2]], col); f(p[q[0]], p[q[2]], p[q[3]], col)
                f(p[q[0]], p[q[2]], p[q[1]], col); f(p[q[0]], p[q[3]], p[q[2]], col)
            }
        }

        /**
         * A four-sided spear point from ([bx], [by], [bz]) along one axis: [dx]/[dy] give the direction (unit), [len] its
         * length, [r] the half-width of the base; a small collar ring sits at the base. Both windings.
         */
        fun spear(bx: Int, by: Int, bz: Int, dx: Int, dy: Int, len: Int, r: Int, col: Short) {
            val tip = v(bx + dx * len, by + dy * len, bz)
            val mid = len / 3
            // Widest point a third of the way out, like the master's leaf-shaped points.
            val cx = bx + dx * mid
            val cy = by + dy * mid
            val ring =
                if (dx != 0) {
                    listOf(v(cx, cy - r, bz), v(cx, cy, bz + r), v(cx, cy + r, bz), v(cx, cy, bz - r))
                } else {
                    listOf(v(cx - r, cy, bz), v(cx, cy, bz + r), v(cx + r, cy, bz), v(cx, cy, bz - r))
                }
            val base = v(bx, by, bz)
            for (i in 0 until 4) {
                val a = ring[i]
                val b = ring[(i + 1) % 4]
                f(tip, a, b, col); f(tip, b, a, col); f(base, b, a, col); f(base, a, b, col)
            }
        }

        /**
         * The cloth: [w] wide, [h] tall, top edge at [top], in the plane z = [z] plus shallow folds. The texture's alpha
         * cuts the tattered outline, so the grid is a plain rectangle. Front texture space P top-left, M top-right,
         * N bottom-left; the back space is mirrored.
         */
        fun cloth(w: Int, h: Int, top: Int, z: Int, texId: Int) {
            val cols = 9
            val rows = 9
            val grid = Array(rows) { IntArray(cols) }
            for (c in 0 until cols) {
                val fx = c / (cols - 1.0)
                val x = (-w / 2.0 + fx * w).toInt()
                // Two and a half soft folds across, deeper towards the hem, none at the rod.
                for (r in 0 until rows) {
                    val fy = r / (rows - 1.0)
                    val fold = (w / 40.0 * fy * sin(fx * 5 * PI)).toInt()
                    grid[r][c] = v(x, (top + h * fy).toInt(), z + fold)
                }
            }
            val front = spaces.size
            spaces += Triple(grid[0][0], grid[0][cols - 1], grid[rows - 1][0])
            val back = spaces.size
            spaces += Triple(grid[0][cols - 1], grid[0][0], grid[rows - 1][cols - 1])
            for (r in 0 until rows - 1) for (c in 0 until cols - 1) {
                val a = grid[r][c]
                val b = grid[r][c + 1]
                val d = grid[r + 1][c]
                val e = grid[r + 1][c + 1]
                f(a, b, e, CLOTH_BASE, texId, front); f(a, e, d, CLOTH_BASE, texId, front)
                f(a, e, b, CLOTH_BASE, texId, back); f(a, d, e, CLOTH_BASE, texId, back)
            }
        }

        /** The master's rod: a dark iron bar with steel spear points at both ends, [half] from the centre. */
        fun rod(half: Int, y: Int, z: Int, thick: Int) {
            box(-half, y - thick, z - thick, half, y + thick, z + thick, IRON_DARK)
            box(-half - 4, y - thick - 2, z - thick - 2, -half + 6, y + thick + 2, z + thick + 2, IRON)
            box(half - 6, y - thick - 2, z - thick - 2, half + 4, y + thick + 2, z + thick + 2, IRON)
            spear(-half - 4, y, z, -1, 0, thick * 7, thick * 2, STEEL)
            spear(half + 4, y, z, 1, 0, thick * 7, thick * 2, STEEL)
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
    }

    /** A wall banner of cloth [w] x [h] whose rod sits at height [rodY], hung [out] units off the north tile edge. */
    private fun wallBanner(texId: Int, w: Int, h: Int, rodY: Int, out: Int): ModelData =
        Mesh().apply {
            val z = 64 - out
            val thick = (w / 44).coerceAtLeast(3)
            rod(w / 2 + thick * 2, rodY, z, thick)
            cloth(w, h, rodY + thick, z, texId)
            // Iron brackets from the rod back to the wall.
            box(-w / 3 - thick, rodY - thick, z, -w / 3 + thick, rodY + thick, 64, IRON)
            box(w / 3 - thick, rodY - thick, z, w / 3 + thick, rodY + thick, 64, IRON)
        }.build()

    /** Bank wall banner: about 1.4 tiles wide, top under a standard wall's cornice. */
    fun wallModel(texId: Int): ModelData = wallBanner(texId, Banner78Art.clothWidthFor(250), 250, -300, 22)

    /** Facade banner for gatehouses and large walls. */
    fun largeModel(texId: Int): ModelData = wallBanner(texId, Banner78Art.clothWidthFor(380), 380, -520, 28)

    /** Free-standing flag: iron pole, crossbar with spear points, spear on top; the flag hangs in front of the pole. */
    fun standardModel(texId: Int): ModelData =
        Mesh().apply {
            val h = 280
            val w = Banner78Art.clothWidthFor(h)
            box(-7, -560, -7, 7, 0, 7, IRON_DARK)
            box(-10, -12, -10, 10, 0, 10, IRON)
            spear(0, -560, 0, 0, -1, 44, 9, STEEL)
            rod(w / 2 + 12, -530, 0, 6)
            cloth(w, h, -524, 10, texId)
        }.build()

    /**
     * Loc definition: [shapes] each get [model]; option-less, [solid] or walk-through; interactive flag 0 (scenery);
     * a little extra ambient so the black/white/purple cloth keeps its colours under the 667 lighting.
     */
    private fun locDef(name: String, model: Int, solid: Boolean, shapes: List<Int>, shiftX: Int = 0): ByteArray =
        ByteArrayOutputStream().apply {
            write(1); write(shapes.size)
            shapes.forEach { shape -> write(shape); write(1); write(model shr 8); write(model and 0xFF) }
            write(2); name.forEach { write(it.code) }; write(0)
            write(19); write(0)
            if (!solid) write(17)
            write(29); write(24)
            if (shiftX != 0) { write(70); write(shiftX shr 8 and 0xFF); write(shiftX and 0xFF) }
            write(0)
        }.toByteArray()

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val idsFile = File(IDS_FILE)
        val ids = Properties().apply { if (idsFile.exists()) idsFile.inputStream().use { load(it) } }

        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            fun fresh(index: Int) = (library.index(index).archiveIds().maxOrNull() ?: 0) + 1
            val materials = OsrsTextureImportTool.Materials.decode(library.data(OsrsTextureImportTool.INDEX_MATERIALS, 0, 0) ?: error("materials missing"))
            val sprite = ids.getProperty("sprite")?.toInt() ?: fresh(OsrsTextureImportTool.INDEX_SPRITES)
            val texture = ids.getProperty("texture")?.toInt() ?: materials.present.size
            val wallModel = ids.getProperty("wallModel")?.toInt() ?: fresh(MODEL_INDEX)
            val standardModel = ids.getProperty("standardModel")?.toInt() ?: (wallModel + 1)
            val largeModel = ids.getProperty("largeModel")?.toInt() ?: fresh(MODEL_INDEX).coerceAtLeast(standardModel + 1)
            check(largeModel <= 0xFFFF) { "model id $largeModel does not fit the loc model field" }
            val lastLoc = library.index(LOC_INDEX).archiveIds().maxOrNull()!!.let { g -> (g shl 8) + (library.index(LOC_INDEX).archive(g)!!.fileIds().maxOrNull() ?: 0) }
            val wallLoc = ids.getProperty("wallLoc")?.toInt() ?: (lastLoc + 1)
            val standardLoc = ids.getProperty("standardLoc")?.toInt() ?: (wallLoc + 1)
            val raisedLoc = ids.getProperty("raisedLoc")?.toInt() ?: (lastLoc + 1)
            val centredLoc = ids.getProperty("centredLoc")?.toInt() ?: (lastLoc + 1)
            println("IDS sprite=$sprite texture=$texture models=$wallModel,$standardModel,$largeModel locs=$wallLoc,$standardLoc,$raisedLoc,$centredLoc")

            fun put(index: Int, group: Int, file: Int, bytes: ByteArray, label: String) {
                val current = library.data(index, group, file)
                if (current != null && current.contentEquals(bytes)) return
                mutations += CacheMutation(index, group, file, bytes, label, current?.let { CacheItemProbeTool.sha1(it) })
            }

            // Texture: the master cloth with its alpha (index 0 of the sprite palette is transparent).
            val image = Banner78Art.masterTexture()
            File("C:/RSPS/tools/ge-banner/banner78_texture_512.png").also { ImageIO.write(image, "png", it) }
            put(OsrsTextureImportTool.INDEX_SPRITES, sprite, 0, StoreArtTool.encode(image), "78 banner sprite ${image.width}x${image.height} with alpha")

            // Materials row: copy of the alpha-tested template, clamped (no edge bleeding), mipmapped, own average colour.
            val template = materials.rows[TEMPLATE_TEXTURE] ?: error("template texture $TEMPLATE_TEXTURE has no metrics")
            check(template[COL_ALPHA_MODE][0].toInt() == 1) { "template $TEMPLATE_TEXTURE is not alpha-tested" }
            check(template[OsrsTextureImportTool.COL_SPEED_U][0].toInt() == 0 && template[OsrsTextureImportTool.COL_SPEED_V][0].toInt() == 0) { "template scrolls" }
            val row = Array(template.size) { template[it].copyOf() }
            row[COL_WRAP_U][0] = 0
            row[COL_WRAP_V][0] = 0
            row[COL_MIPMAP][0] = 2
            val average = Banner78Art.averageHsl(image)
            row[COL_AVERAGE][0] = (average shr 8).toByte()
            row[COL_AVERAGE][1] = average.toByte()
            val present = if (texture < materials.present.size) materials.present else materials.present + true
            val rows = if (texture < materials.rows.size) materials.rows.copyOf() else materials.rows + arrayOf<Array<ByteArray>?>(null)
            rows[texture] = row
            present[texture] = true
            put(OsrsTextureImportTool.INDEX_MATERIALS, 0, 0, OsrsTextureImportTool.Materials(present, rows).encode(), "materials row $texture (78 banner: alpha test, clamp, mipmaps)")

            val templateProgram = library.data(OsrsTextureImportTool.INDEX_TEXTURES, TEMPLATE_TEXTURE) ?: error("template program missing")
            val at = OsrsTextureImportTool.spriteParamOffset(templateProgram)
            put(OsrsTextureImportTool.INDEX_TEXTURES, texture, 0, templateProgram.copyOf().also { it[at] = (sprite ushr 8).toByte(); it[at + 1] = sprite.toByte() }, "78 banner texture program (alpha)")

            listOf(wallModel to wallModel(texture), standardModel to standardModel(texture), largeModel to largeModel(texture)).forEach { (id, model) ->
                val encoded = Rev667ModelEncoder.encode(model)
                val differences = ModelConvertTool.compare(model, Rev667ModelDecoder.decode(encoded))
                check(differences.isEmpty()) { "model $id round trip: $differences" }
                put(MODEL_INDEX, id, 0, encoded, "78 banner model $id (${model.vertexCount} vertices, ${model.faceCount} faces)")
            }
            val wall = listOf(4, 10)
            put(LOC_INDEX, wallLoc ushr 8, wallLoc and 0xFF, locDef("78 banner", wallModel, solid = false, shapes = wall), "loc $wallLoc 78 banner (bank wall)")
            put(LOC_INDEX, standardLoc ushr 8, standardLoc and 0xFF, locDef("78 standard", standardModel, solid = true, shapes = listOf(10)), "loc $standardLoc 78 standard (flag on a pole)")
            put(LOC_INDEX, raisedLoc ushr 8, raisedLoc and 0xFF, locDef("78 banner", largeModel, solid = false, shapes = wall), "loc $raisedLoc 78 banner (large facade)")
            // Shifted half a tile east (rotation 0): the large banner centred on a tile edge, e.g. the middle of a gatehouse.
            put(LOC_INDEX, centredLoc ushr 8, centredLoc and 0xFF, locDef("78 banner", largeModel, solid = false, shapes = wall, shiftX = 64), "loc $centredLoc 78 banner (large, centred on an edge)")
            listOf(wallLoc, standardLoc, raisedLoc, centredLoc).forEach { loc ->
                val bytes = mutations.lastOrNull { it.indexId == LOC_INDEX && (it.groupId shl 8) + it.fileId == loc }?.newBytes ?: return@forEach
                check(Rev667LocType.decode(loc, bytes).allModels.isNotEmpty()) { "loc $loc re-decode failed" }
            }

            ids.setProperty("sprite", "$sprite"); ids.setProperty("texture", "$texture")
            ids.setProperty("wallModel", "$wallModel"); ids.setProperty("standardModel", "$standardModel"); ids.setProperty("largeModel", "$largeModel")
            ids.setProperty("wallLoc", "$wallLoc"); ids.setProperty("standardLoc", "$standardLoc"); ids.setProperty("raisedLoc", "$raisedLoc"); ids.setProperty("centredLoc", "$centredLoc")
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
        idsFile.parentFile.mkdirs()
        idsFile.outputStream().use { ids.store(it, "78 house banner cache ids (HouseBannerTool)") }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir}); ids -> $IDS_FILE")
    }
}
