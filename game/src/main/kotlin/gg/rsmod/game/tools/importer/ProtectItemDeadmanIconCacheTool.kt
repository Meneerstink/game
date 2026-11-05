package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N543 (owner 2026-11-05 14:4x, with an OSRS screenshot: 'er moet dus aan de prayerbooks prot item een ronde rode cirkel over de prayer'):
 * the disabled Protect Item cell shows the book's OWN Protect Item icon with the OSRS red prohibition ring over it - OSRS sprite 1193
 * (RuneLite SpriteID.STOP: a 32x32 red ring with a '/' bar, the only cache sprite of that shape; found by a scan of all 2686 sprites,
 * screens\N543\compare-ring.png). Replaces the High Risk skull (OSRS 912, [ProtectItemHighRiskCacheTool], N503).
 *
 * Per book the coloured (param 735) and grey (param 736) icon get a composite = the original icon of that cell (from before N503,
 * import-journal tx-20261101-005542) centred on a 32x32 canvas (the size sprite 912 already had in all three books) + ring 1193 on top:
 * struct 668 normal (123/143), 888 curses (2329/2355), 2766 Ruinous Powers (8763/8787). The tooltip line stays as N503 set it.
 *
 * One [CacheTransaction] over both production caches; running it again is a no-op.
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.ProtectItemDeadmanIconCacheTool plan|apply`
 */
object ProtectItemDeadmanIconCacheTool {
    private const val CONFIG_INDEX = 2
    private const val SPRITE_INDEX = 8
    private const val STRUCT_GROUP = 26
    private const val CANVAS = 32

    /** OSRS 2686 sprite STOP: red ring with a diagonal bar. */
    const val OSRS_RING = 1193

    /** First free 667 sprite on 2026-11-05 (HOOFDLIJST: taken up to 8812). */
    const val FIRST_SPRITE = 8813

    data class Cell(val struct: Int, val colour: Int, val grey: Int)

    /** struct -> its original colour/grey Protect Item icon (667 sprite ids). */
    val CELLS = listOf(Cell(668, 123, 143), Cell(888, 2329, 2355), Cell(2766, 8763, 8787))

    /** New sprite id for cell [i] (0..2), colour or grey. */
    fun spriteFor(i: Int, grey: Boolean) = FIRST_SPRITE + i * 2 + if (grey) 1 else 0

    /** A decoded single-frame sprite as an ARGB canvas (0 = transparent). */
    class Image(val width: Int, val height: Int, val argb: IntArray)

    /** Decodes frame 0 of a JS5 sprite group onto its canvas. */
    fun decode(b: ByteArray): Image {
        val n = b.size
        fun u16(p: Int) = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
        val count = u16(n - 2)
        var p = n - 7 - count * 8
        val canvasW = u16(p)
        val canvasH = u16(p + 2)
        val palLen = (b[p + 4].toInt() and 0xFF) + 1
        p += 5
        val ox = u16(p)
        val oy = u16(p + 2 * count)
        val w = u16(p + 4 * count)
        val h = u16(p + 6 * count)
        var pp = n - 7 - count * 8 - (palLen - 1) * 3
        val pal = IntArray(palLen)
        for (i in 1 until palLen) {
            val c = ((b[pp].toInt() and 0xFF) shl 16) or ((b[pp + 1].toInt() and 0xFF) shl 8) or (b[pp + 2].toInt() and 0xFF)
            pal[i] = if (c == 0) 1 else c
            pp += 3
        }
        val flags = b[0].toInt() and 0xFF
        val idx = IntArray(w * h)
        var q = 1
        if (flags and 1 == 0) {
            for (i in 0 until w * h) idx[i] = b[q++].toInt() and 0xFF
        } else {
            for (x in 0 until w) for (y in 0 until h) idx[y * w + x] = b[q++].toInt() and 0xFF
        }
        val alpha = IntArray(w * h) { 255 }
        if (flags and 2 != 0) {
            if (flags and 1 == 0) {
                for (i in 0 until w * h) alpha[i] = b[q++].toInt() and 0xFF
            } else {
                for (x in 0 until w) for (y in 0 until h) alpha[y * w + x] = b[q++].toInt() and 0xFF
            }
        }
        val out = IntArray(canvasW * canvasH)
        for (y in 0 until h) for (x in 0 until w) {
            val k = idx[y * w + x]
            if (k == 0 || alpha[y * w + x] < 128) continue
            out[(oy + y) * canvasW + ox + x] = (0xFF shl 24) or pal[k]
        }
        return Image(canvasW, canvasH, out)
    }

    /** [base] centred on a 32x32 canvas with [ring] centred on top. */
    fun composite(base: Image, ring: Image): Image {
        val out = IntArray(CANVAS * CANVAS)
        fun draw(img: Image) {
            val dx = (CANVAS - img.width) / 2
            val dy = (CANVAS - img.height) / 2
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val c = img.argb[y * img.width + x]
                val tx = x + dx
                val ty = y + dy
                if (c != 0 && tx in 0 until CANVAS && ty in 0 until CANVAS) out[ty * CANVAS + tx] = c
            }
        }
        draw(base)
        draw(ring)
        return Image(CANVAS, CANVAS, out)
    }

    /** Encodes [img] as a one-frame indexed sprite (row-major, no alpha), trimmed to its opaque bounds. */
    fun encode(img: Image): ByteArray {
        var minX = img.width
        var minY = img.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until img.height) for (x in 0 until img.width) {
            if (img.argb[y * img.width + x] != 0) {
                minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
        }
        check(maxX >= 0) { "empty sprite" }
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        val palette = LinkedHashMap<Int, Int>()
        val idx = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val c = img.argb[(minY + y) * img.width + minX + x]
            if (c == 0) continue
            val rgb = c and 0xFFFFFF
            val i = palette.getOrPut(rgb) { palette.size + 1 }
            idx[y * w + x] = i.toByte()
        }
        check(palette.size <= 255) { "too many colours: ${palette.size}" }
        val o = ByteArrayOutputStream()
        fun u16(v: Int) { o.write(v ushr 8); o.write(v) }
        o.write(0)
        o.write(idx)
        palette.keys.forEach { rgb -> o.write(rgb ushr 16); o.write(rgb ushr 8); o.write(rgb) }
        u16(img.width); u16(img.height)
        o.write(palette.size)
        u16(minX); u16(minY); u16(w); u16(h)
        u16(1)
        return o.toByteArray()
    }

    /** [current] struct with icons [colour]/[grey]; null when already set. */
    fun patched(current: ByteArray, colour: Int, grey: Int): ByteArray? {
        val params = ProtectItemHighRiskCacheTool.decode(current)
        check(ProtectItemHighRiskCacheTool.encode(params).contentEquals(current)) { "struct does not round-trip" }
        if (params[ProtectItemHighRiskCacheTool.PARAM_COLOUR] == colour && params[ProtectItemHighRiskCacheTool.PARAM_GREY] == grey) return null
        params[ProtectItemHighRiskCacheTool.PARAM_COLOUR] = colour
        params[ProtectItemHighRiskCacheTool.PARAM_GREY] = grey
        return ProtectItemHighRiskCacheTool.encode(params)
    }

    fun plan(): List<CacheMutation> {
        val mutations = ArrayList<CacheMutation>()
        val ringBytes = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { it.file(SPRITE_INDEX, OSRS_RING, 0) } ?: error("OSRS sprite $OSRS_RING missing")
        val ring = decode(ringBytes)
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            CELLS.forEachIndexed { i, cell ->
                listOf(false to cell.colour, true to cell.grey).forEach { (grey, baseId) ->
                    val base = decode(requireNotNull(library.data(SPRITE_INDEX, baseId, 0)) { "sprite $baseId missing" })
                    val bytes = encode(composite(base, ring))
                    val id = spriteFor(i, grey)
                    val present = library.data(SPRITE_INDEX, id, 0)
                    if (present == null) {
                        mutations += CacheMutation(SPRITE_INDEX, id, 0, bytes, "sprite $id: Protect Item $baseId + OSRS ring $OSRS_RING (N543)")
                    } else {
                        check(present.contentEquals(bytes)) { "sprite $id is taken by something else" }
                    }
                }
                val current = requireNotNull(library.data(CONFIG_INDEX, STRUCT_GROUP, cell.struct)) { "struct ${cell.struct} missing" }
                patched(current, spriteFor(i, false), spriteFor(i, true))?.let {
                    mutations += CacheMutation(CONFIG_INDEX, STRUCT_GROUP, cell.struct, it, "struct ${cell.struct}: Protect Item + OSRS ring (N543)", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            }
        } finally {
            library.close()
        }
        return mutations
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val mutations = plan()
        if (mutations.isEmpty()) {
            println("PROTECT_ITEM_DEADMAN_ICON already applied")
            return
        }
        val transaction = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
        val preflight = transaction.preflight()
        println(preflight.joinToString("\n"))
        val errors = transaction.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) return
        val result = transaction.apply(preflight)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("PROTECT_ITEM_DEADMAN_ICON transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
