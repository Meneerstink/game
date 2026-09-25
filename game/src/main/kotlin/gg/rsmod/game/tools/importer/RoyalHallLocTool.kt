package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Raised copies of two roof locs for the Grand Exchange Royal Hall's tall hip roof (owner 2026-09-25: "het gebouw ziet er
 * niet echt uit als een paleis", "please dont stop untill its perfet").
 *
 * The Legends' Guild slate roof piece 41409 rises 70 (old model units) across one tile, and the client stacks roof rings
 * on higher levels, but a map square has only four. The hall's roof needs five slate rings, so rings 3-5 are copies of
 * 41409 whose vertical offset (loc opcode 71) lifts them onto the ring below: ring 2 is 41409 itself on level 3, ring k
 * sits 70 * (k - 2) higher. The small glass lantern on top is a copy of the glass roof piece 47841 lifted onto ring 5.
 * Nothing else about the definitions changes (models, shapes, recolours, no options), so they look and behave like the
 * originals. The hall's fountain is a byte-exact copy of the Grand Exchange fountain 47150 (owner 2026-09-25: the hall
 * moved onto the exchange's centre, where the owner's own world edit removes 47150 from that tile). Appended as new loc
 * ids after the last one in the cache; one [CacheTransaction] over both production caches, idempotent.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.RoyalHallLocTool plan|apply`
 */
object RoyalHallLocTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val LOC_INDEX = 16

    const val SLATE = 41409
    const val GLASS = 47841
    const val FOUNTAIN = 47150

    /** The slate piece's own opcode-71 value; the rings above add their lift to it. */
    private const val SLATE_BASE_OFFSET = -6
    private const val SLATE_RISE = 70

    /** Glass slope faces start 16 above the loc; the lantern's base meets the top of ring 5 (216 + 70 above level 3). */
    private const val GLASS_OFFSET = -(3 * SLATE_RISE + SLATE_RISE - SLATE_BASE_OFFSET - 16)

    /** A copy of [source]; [offset] replaces its vertical offset (opcode 71), null keeps the definition byte-exact. */
    class Copy(val id: Int, val source: Int, val offset: Int?, val label: String)

    val COPIES =
        listOf(
            Copy(62754, SLATE, SLATE_BASE_OFFSET - SLATE_RISE, "Royal Hall roof ring 3 (slate 41409 raised 70)"),
            Copy(62755, SLATE, SLATE_BASE_OFFSET - 2 * SLATE_RISE, "Royal Hall roof ring 4 (slate 41409 raised 140)"),
            Copy(62756, SLATE, SLATE_BASE_OFFSET - 3 * SLATE_RISE, "Royal Hall roof ring 5 (slate 41409 raised 210)"),
            Copy(62757, GLASS, GLASS_OFFSET, "Royal Hall glass lantern (glass roof 47841 raised onto ring 5)"),
            Copy(62758, FOUNTAIN, null, "Royal Hall fountain (exact copy of the Grand Exchange fountain 47150)"),
        )

    /** A copy of loc [source] as [id], its definition bytes changed by [edit] (models and everything else kept). */
    class Variant(val id: Int, val source: Int, val label: String, val edit: (ByteArray) -> ByteArray)

    /** The gold "Large door" leaves 22435/22437 stand open in the hall's south doorway as scenery: their "Open" option goes. */
    /** [def] without its menu option(s) 30-34 wherever they stand (scenery doors that are never opened). */
    private fun withoutOptions(def: ByteArray): ByteArray {
        var out = def
        for (op in 30..34) {
            // Options come early in these definitions; a tail opcode this walker cannot size means there are no more.
            val at = runCatching { opcodePosition(0, out, op) }.getOrDefault(-1)
            if (at < 0) continue
            var end = at + 1
            while (out[end] != 0.toByte()) end++
            out = out.copyOfRange(0, at) + out.copyOfRange(end + 1, out.size)
        }
        return out
    }

    private fun withoutLeadingOption(def: ByteArray): ByteArray {
        check(def[0] == 30.toByte()) { "expected the definition to start with option 1" }
        val end = def.indexOf(0.toByte())
        return def.copyOfRange(end + 1, def.size)
    }

    /** The white marble counter 37169 is 2 x 1; the hall's stall counters are 1 x 1, the same model at half length. */
    private fun halfCounter(def: ByteArray): ByteArray {
        check(def[0] == 14.toByte() && def[1] == 2.toByte()) { "expected sizeX 2 first" }
        return byteArrayOf(14, 1, 65, 0, 64) + def.copyOfRange(2, def.size)
    }

    /** The Falador castle turret 43730 at 1.375x (model scale opcodes 65-67, 128 = 1x) for the hall's corner turrets. */
    /** The stone tones (hue 6) of the turret model 47452. */
    private val TURRET_STONE =
        listOf(0x1890, 0x1892, 0x1899, 0x189d, 0x189f, 0x18a1, 0x18a5, 0x18a6, 0x18a8, 0x18a9, 0x18ab, 0x18ad, 0x18ae, 0x18b2, 0x18b3,
            0x18b4, 0x18b5, 0x18b8, 0x18bb, 0x18bd, 0x18bf, 0x18c3, 0x18c7, 0x18cc, 0x18cd, 0x18cf, 0x18d4, 0x1923, 0x1a12)

    private fun grandTurret(def: ByteArray): ByteArray {
        check(listOf(65, 66, 67).none { opcodePosition(43730, def, it) >= 0 }) { "turret already scaled" }
        // 1.375 wide and 1.75 high: the owner chose towers that rise from the ground (C:/RSPS/foto/tower_designs.png, 3), in
        // dark granite (tower_colours.png e): every stone tone (hue 6) greyed at 0.55 of its lightness.
        val recolour = java.io.ByteArrayOutputStream()
        recolour.write(40)
        recolour.write(TURRET_STONE.size)
        TURRET_STONE.forEach { c ->
            val granite = (c and 0x7F) * 55 / 100
            recolour.write(byteArrayOf((c shr 8).toByte(), c.toByte(), (granite shr 8).toByte(), granite.toByte()))
        }
        return withModel(43730, byteArrayOf(65, 0, 176.toByte(), 66, 0, 224.toByte(), 67, 0, 176.toByte()) + recolour.toByteArray() + def, TURRET_MODEL, TURRET_MODEL_CLEAN)
    }

    /**
     * The Royal Hall's minimap and world-map icon: a 15 x 15 badge in the style of the game's own (black outline, round
     * ring) - a gold crown with red and pearl gems on 78 purple inside a gold ring. [ICON_ELEMENT] is a copy of the
     * Grand Exchange's MapElementType 637 showing [ICON_SPRITE]; [ICON_LOC] a copy of its invisible marker loc 27990
     * carrying [ICON_ELEMENT], placed on the hall's centre by RoyalHallMapTool.
     */
    const val ICON_SPRITE = 8193
    const val ICON_ELEMENT = 1107
    const val ICON_LOC = 62767
    private const val WORLD_MAP_INDEX = 23
    private const val STATIC_ELEMENTS = "main_staticelements"
    private const val ICON_TILE_X = 3164
    private const val ICON_TILE_Z = 3491
    private const val GE_ELEMENT = 637
    private const val GE_MARKER = 27990
    private const val SPRITE_INDEX = 8
    private const val CONFIG_INDEX = 2
    private const val MAP_ELEMENT_GROUP = 36

    private val ICON_ROWS =
        listOf(
            ".....KKKKK.....",
            "...KKGGGGGKK...",
            "..KGGPPPPPGGK..",
            ".KGPPPPPPPPPGK.",
            ".KGPPPPHPPPPGK.",
            "KGPPHPPGPPHPPGK",
            "KGPPGPGGGPGPPGK",
            "KGPPGGGGGGGPPGK",
            "KGPPGRGWGRGPPGK",
            "KGPPGGGGGGGPPGK",
            ".KGPDDDDDDDPGK.",
            ".KGPPPPPPPPPGK.",
            "..KGGPPPPPGGK..",
            "...KKGGGGGKK...",
            ".....KKKKK.....",
        )
    private val ICON_COLOURS =
        mapOf('K' to 0x0A0A0A, 'G' to 0xE8B830, 'H' to 0xFFE680, 'D' to 0x9C6B12, 'P' to 0x4E1F74, 'R' to 0xD0202A, 'W' to 0xF4F0E8)

    fun iconImage(): java.awt.image.BufferedImage {
        val image = java.awt.image.BufferedImage(15, 15, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        ICON_ROWS.forEachIndexed { y, row -> row.forEachIndexed { x, c -> ICON_COLOURS[c]?.let { image.setRGB(x, y, it or (0xFF shl 24)) } } }
        return image
    }

    /** [def] (a MapElementType, op 1 = u16 sprite first) showing [sprite]. */
    private fun withSprite(def: ByteArray, sprite: Int): ByteArray {
        check(def[0] == 1.toByte()) { "expected the sprite first" }
        return def.copyOf().also {
            it[1] = (sprite shr 8).toByte()
            it[2] = sprite.toByte()
        }
    }

    /** [def] (a marker LocType, op 107 = u16 map element first) carrying [element]. */
    private fun withElement(def: ByteArray, element: Int): ByteArray {
        check(def[0] == 107.toByte()) { "expected the map element first" }
        return def.copyOf().also {
            it[1] = (element shr 8).toByte()
            it[2] = element.toByte()
        }
    }

    /**
     * The 78 carpet: a sprite texture in the house colours replacing the Varrock Palace carpet's procedural gold texture
     * 106. Alternating royal purple diamonds framed by a gold lattice with dark-gold edges and a thin inner gold line, gold
     * studs where the lines cross and a four-pointed gold star in every diamond; 128 px with a 64 px period, so it tiles seamlessly. Its metrics are 106's
     * (same tiling, lighting and low-detail fallback) as a full-size, still texture with a purple average colour.
     */
    const val CARPET_SPRITE = 8194
    const val CARPET_TEXTURE = 1416
    private const val VARROCK_CARPET_TEXTURE = 106
    private const val SPRITE_TEXTURE_TEMPLATE = 40
    private const val TEXTURE_INDEX = 9
    private const val MATERIALS_INDEX = 26

    /** HSL16 royal purple (hue 48 of 64, saturation 4, lightness 36), the carpet's colour on low detail and far away. */
    private const val CARPET_AVERAGE_HSL = (48 shl 10) or (4 shl 7) or 36

    fun carpetImage(): java.awt.image.BufferedImage {
        val size = 128
        val period = 64
        val purple = 0x4A1C6E
        val purpleDeep = 0x3A1458
        val gold = 0xE0B040
        val goldLight = 0xF6D877
        val goldDark = 0x8C5E12
        val image = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until size) for (x in 0 until size) {
            // Diagonal coordinates: lattice lines are x + y = k * period and x - y = k * period.
            val a = Math.floorMod(x + y, period)
            val b = Math.floorMod(x - y, period)
            val la = minOf(a, period - a)
            val lb = minOf(b, period - b)
            val line = minOf(la, lb)
            // Alternating diamonds (checkerboard in diagonal space).
            val parity = Math.floorMod(Math.floorDiv(x + y, period) + Math.floorDiv(x - y, period), 2)
            var rgb = if (parity == 0) purple else purpleDeep
            // Thin inner gold outline in every diamond.
            if (line in 10..11) rgb = goldDark
            // Lattice band: light core, gold, dark-gold rim.
            rgb = when {
                line <= 1 -> goldLight
                line <= 4 -> gold
                line <= 6 -> goldDark
                else -> rgb
            }
            // Studs where two lines cross.
            val cross = kotlin.math.hypot(la.toDouble(), lb.toDouble()) / Math.sqrt(2.0)
            if (cross <= 6.5) rgb = if (cross <= 4.5) (if (cross <= 2.0) goldLight else gold) else goldDark
            // Four-pointed star (points up, down, left and right) in each diamond's centre.
            val da = a - period / 2
            val db = b - period / 2
            val dx = kotlin.math.abs(da + db) / 2
            val dy = kotlin.math.abs(da - db) / 2
            if (dx + dy <= 13 && dx * dy <= 4) rgb = if (dx + dy <= 4) goldLight else gold
            image.setRGB(x, y, rgb or (0xFF shl 24))
        }
        return image
    }

    /**
     * [def] with the Varrock carpet texture replaced by the 78 carpet (loc opcode 41, texture pairs). The Varrock carpet's
     * texture is grey and its recolour (opcode 40) tints it gold; the 78 texture carries its own colours, so every recolour
     * target becomes a neutral grey 1.8x as light (the border stays relatively darker) and the texture shows true.
     */
    private fun with78Carpet(def: ByteArray): ByteArray {
        check(opcodePosition(15550, def, 41) < 0) { "carpet already retextured" }
        val out = def.copyOf()
        val at = opcodePosition(15550, out, 40)
        check(at >= 0) { "carpet has no recolour" }
        val pairs = out[at + 1].toInt() and 0xFF
        for (i in 0 until pairs) {
            val p = at + 2 + i * 4 + 2
            val hsl = ((out[p].toInt() and 0xFF) shl 8) or (out[p + 1].toInt() and 0xFF)
            val neutral = minOf(127, (hsl and 0x7F) * 9 / 5)
            out[p] = 0
            out[p + 1] = neutral.toByte()
        }
        return byteArrayOf(41, 1, 0, VARROCK_CARPET_TEXTURE.toByte(), (CARPET_TEXTURE shr 8).toByte(), CARPET_TEXTURE.toByte()) + out
    }

    /**
     * The royal roof: the Legends' Guild slate 41409 recoloured to deep royal-blue slate (its four slate tints, same
     * lightness) with gilded ridge beams (the wood faces 1710/1714/1718 tinted gold). Rings 3-5 ([COPIES]) derive from it.
     */
    // Hue 40 of 64, saturation 2, the slate's lightness x0.8: a deep, muted slate blue (hue 43 / saturation 3 came out
    // lavender in game, owner 2026-09-25 foto roof).
    // Owner 2026-09-26 chose the ivory roof (hue 8, saturation 1, lightness 100-76) from C:/RSPS/foto/roof_options.png.
    private val ROYAL_SLATE_TINTS = mapOf(0x201c to 0x20E4, 0x2014 to 0x20DC, 0x200c to 0x20D4, 0x2008 to 0x20CC)
    private val GILDED_RIDGE = listOf(0x1710 to 0x2396, 0x1714 to 0x239C, 0x1718 to 0x23A2)

    fun royalSlate(def: ByteArray): ByteArray = slateWith(def, ROYAL_SLATE_TINTS, GILDED_RIDGE)

    /** The slate [def] with its four slate tints mapped by [tints] and the ridge beams recoloured by [ridge]. */
    fun slateWith(def: ByteArray, tints: Map<Int, Int>, ridge: List<Pair<Int, Int>>): ByteArray {
        val at = opcodePosition(SLATE, def, 40)
        check(at >= 0) { "slate has no recolour" }
        val pairs = def[at + 1].toInt() and 0xFF
        val out = java.io.ByteArrayOutputStream()
        out.write(def, 0, at)
        out.write(40)
        out.write(pairs + ridge.size)
        for (i in 0 until pairs) {
            val p = at + 2 + i * 4
            val dst = ((def[p + 2].toInt() and 0xFF) shl 8) or (def[p + 3].toInt() and 0xFF)
            val royal = tints[dst] ?: error("unexpected slate tint ${dst.toString(16)}")
            out.write(def, p, 2)
            out.write(royal shr 8)
            out.write(royal and 0xFF)
        }
        ridge.forEach { (src, dst) ->
            out.write(src shr 8)
            out.write(src and 0xFF)
            out.write(dst shr 8)
            out.write(dst and 0xFF)
        }
        val rest = at + 2 + pairs * 4
        out.write(def, rest, def.size - rest)
        return out.toByteArray()
    }

    /**
     * The premium stall counter: the gold-trimmed counter 45238 at half length (1 x 1, model scale x 0.5) in white marble
     * and gold - its wood (63) and panel (73, 102) textures become the speckled white marble 254, the panel tints gold and
     * everything else near-white. It stays see-through (opcode 18), so npcs are talked to across it.
     */
    private val COUNTER_TINTS = listOf(0x238a to 0x23B2, 0x2123 to 0x23A8, 0x2196 to 0x0074, 0x219a to 0x0072, 0x219f to 0x0074, 0x2212 to 0x006E)

    private fun premiumCounter(def: ByteArray): ByteArray {
        check(def[2] == 14.toByte() && def[3] == 2.toByte()) { "expected sizeX 2 after the first opcode" }
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(65, 0, 64))
        out.write(byteArrayOf(41, 3, 0, 63, 0, 254.toByte(), 0, 73, 0, 254.toByte(), 0, 102, 0, 254.toByte()))
        out.write(40)
        out.write(COUNTER_TINTS.size)
        COUNTER_TINTS.forEach { (src, dst) -> out.write(byteArrayOf((src shr 8).toByte(), src.toByte(), (dst shr 8).toByte(), dst.toByte())) }
        out.write(def, 0, 3)
        out.write(1) // sizeX 1
        out.write(def, 4, def.size - 4)
        return out.toByteArray()
    }

    /**
     * The hall's fountain: the gold-rimmed Fountain of Heroes 36695 widened to 4 x 4 (x/z scale 1.75, height unchanged,
     * 220 units), so the upper floor (240 up) closes over it; the Grand Exchange fountain (611 high) would stick through it.
     */
    private fun grandFountain(def: ByteArray): ByteArray {
        val at = (0 until def.size - 3).first { def[it] == 14.toByte() && def[it + 1] == 2.toByte() && def[it + 2] == 15.toByte() && def[it + 3] == 2.toByte() }
        val out = def.copyOf()
        out[at + 1] = 4
        out[at + 3] = 4
        return byteArrayOf(65, 0, 224.toByte(), 66, 0, 128.toByte(), 67, 0, 224.toByte()) + out
    }

    /**
     * The corner turret's model (Falador castle turret 47452) without the little guard standing on its top platform: every
     * face within 45 units of the centre at or above the platform (height 320) except the flagpole (texture 91), its flag
     * (480) and the platform floor (505) is collapsed to a point. Owner 2026-09-25: "on the round corner things i see a
     * minitaure npcs".
     */
    const val TURRET_MODEL = 47452
    const val TURRET_MODEL_CLEAN = 65427

    /**
     * The mesh uses complex texture mappings, which [Rev667ModelEncoder] cannot write, so only its vertex section is
     * rebuilt (client `Mesh.decodeNew` layout): every vertex used only by guard faces moves onto one point on the platform,
     * collapsing those faces, and all other bytes (faces, colours, textures, labels, mappings) are copied unchanged.
     * Returns the new mesh and the number of guard faces.
     */
    fun turretWithoutGuard(data: ByteArray): Pair<ByteArray, Int> {
        fun u8(p: Int) = data[p].toInt() and 0xFF
        fun u16(p: Int) = (u8(p) shl 8) or u8(p + 1)
        val t = data.size - 23
        val vertexCount = u16(t)
        val faceCount = u16(t + 2)
        val texSpaceCount = u8(t + 4)
        val flags = u8(t + 5)
        check(flags and 0x1 == 0 && flags and 0x8 == 0) { "unexpected mesh flags $flags" }
        val priorityFlag = u8(t + 6)
        val faceAlphaFlag = u8(t + 7)
        val faceGroupFlag = u8(t + 8)
        val faceTextureFlag = u8(t + 9)
        val vertexLabelFlag = u8(t + 10)
        val lenX = u16(t + 11)
        val lenY = u16(t + 13)
        val lenZ = u16(t + 15)
        val faceDataSize = u16(t + 17)
        val texSpaceSize = u16(t + 19)
        var ptr = texSpaceCount
        val vertexFlagsPtr = ptr
        ptr += vertexCount
        val faceTypePtr = ptr
        ptr += faceCount
        if (priorityFlag == 255) ptr += faceCount
        if (faceGroupFlag == 1) ptr += faceCount
        if (vertexLabelFlag == 1) ptr += vertexCount
        if (faceAlphaFlag == 1) ptr += faceCount
        val faceDataPtr = ptr
        ptr += faceDataSize
        val faceTexturePtr = ptr
        if (faceTextureFlag == 1) ptr += faceCount * 2
        ptr += texSpaceSize
        ptr += faceCount * 2
        val vertexXPtr = ptr
        val restPtr = vertexXPtr + lenX + lenY + lenZ

        class Reader(var p: Int) {
            fun smart(): Int {
                val v = u8(p)
                return if (v < 128) { p++; v - 64 } else { val r = u16(p) - 49152; p += 2; r }
            }
        }
        val xs = IntArray(vertexCount)
        val ys = IntArray(vertexCount)
        val zs = IntArray(vertexCount)
        val rx = Reader(vertexXPtr)
        val ry = Reader(vertexXPtr + lenX)
        val rz = Reader(vertexXPtr + lenX + lenY)
        var cx = 0
        var cy = 0
        var cz = 0
        for (i in 0 until vertexCount) {
            val mask = u8(vertexFlagsPtr + i)
            if (mask and 1 != 0) cx += rx.smart()
            if (mask and 2 != 0) cy += ry.smart()
            if (mask and 4 != 0) cz += rz.smart()
            xs[i] = cx
            ys[i] = cy
            zs[i] = cz
        }
        check(rx.p == vertexXPtr + lenX && ry.p == vertexXPtr + lenX + lenY && rz.p == restPtr) { "vertex streams did not line up" }

        val fr = Reader(faceDataPtr)
        val faces = Array(faceCount) { IntArray(3) }
        var a = 0
        var b = 0
        var c = 0
        var last = 0
        for (i in 0 until faceCount) {
            when (u8(faceTypePtr + i)) {
                1 -> { a = fr.smart() + last; b = fr.smart() + a; c = fr.smart() + b; last = c }
                2 -> { b = c; c = fr.smart() + last; last = c }
                3 -> { a = c; c = fr.smart() + last; last = c }
                4 -> { val pa = a; a = b; b = pa; c = fr.smart() + last; last = c }
                else -> error("face type")
            }
            faces[i][0] = a
            faces[i][1] = b
            faces[i][2] = c
        }
        val keep = setOf(91, 480, 505)
        val guard = BooleanArray(faceCount)
        for (f in 0 until faceCount) {
            val (fa, fb, fc) = faces[f].let { Triple(it[0], it[1], it[2]) }
            val y = -(ys[fa] + ys[fb] + ys[fc]) / 3.0
            val x = (xs[fa] + xs[fb] + xs[fc]) / 3.0
            val z = (zs[fa] + zs[fb] + zs[fc]) / 3.0
            val tex = if (faceTextureFlag == 1) u16(faceTexturePtr + f * 2) - 1 else -1
            guard[f] = kotlin.math.hypot(x, z) < 45 && y >= 320 && tex !in keep
        }
        val usedByKept = BooleanArray(vertexCount)
        val usedByGuard = BooleanArray(vertexCount)
        for (f in 0 until faceCount) faces[f].forEach { if (guard[f]) usedByGuard[it] = true else usedByKept[it] = true }
        val shared = (0 until vertexCount).count { usedByGuard[it] && usedByKept[it] }
        check(shared == 0) { "$shared guard vertices are shared with the tower; collapsing them would tear it" }
        for (v in 0 until vertexCount) if (usedByGuard[v]) { xs[v] = 0; ys[v] = -321; zs[v] = 0 }

        val flagsOut = ByteArray(vertexCount)
        val sx = java.io.ByteArrayOutputStream()
        val sy = java.io.ByteArrayOutputStream()
        val sz = java.io.ByteArrayOutputStream()
        fun smart(out: java.io.ByteArrayOutputStream, v: Int) {
            if (v in -64..63) out.write(v + 64) else { check(v in -16384..16383); val w = v + 49152; out.write(w shr 8); out.write(w and 0xFF) }
        }
        var px = 0
        var py = 0
        var pz = 0
        for (i in 0 until vertexCount) {
            var mask = 0
            if (xs[i] != px) { mask = mask or 1; smart(sx, xs[i] - px) }
            if (ys[i] != py) { mask = mask or 2; smart(sy, ys[i] - py) }
            if (zs[i] != pz) { mask = mask or 4; smart(sz, zs[i] - pz) }
            flagsOut[i] = mask.toByte()
            px = xs[i]
            py = ys[i]
            pz = zs[i]
        }
        val out = java.io.ByteArrayOutputStream()
        out.write(data, 0, vertexFlagsPtr)
        out.write(flagsOut)
        out.write(data, vertexFlagsPtr + vertexCount, vertexXPtr - (vertexFlagsPtr + vertexCount))
        out.write(sx.toByteArray())
        out.write(sy.toByteArray())
        out.write(sz.toByteArray())
        out.write(data, restPtr, t - restPtr)
        val trailer = data.copyOfRange(t, data.size)
        fun put16(at: Int, v: Int) { trailer[at] = (v shr 8).toByte(); trailer[at + 1] = v.toByte() }
        put16(11, sx.size())
        put16(13, sy.size())
        put16(15, sz.size())
        out.write(trailer)
        return out.toByteArray() to guard.count { it }
    }
    /**
     * The premium spiral staircase: the stone spiral 34872 (bottom) / 34873 (top) in white marble with gilded rails - its
     * stone textures (70, 377, 67, 105) become the white marble 254 tinted near-white, its wooden rails (the recoloured
     * 1510-1520 faces) are tinted gold. Options (Climb-up / Climb-down) are kept; GeHomeHall routes them.
     */
    private val STAIR_STONE = listOf(0x201e to 0x0070, 0x2022 to 0x0072, 0x2032 to 0x0074, 0x2037 to 0x0076)
    private val STAIR_RAIL_GOLD = mapOf(0x1d10 to 0x23A0, 0x1d18 to 0x23A8, 0x1d1c to 0x23AC, 0x1d20 to 0x23B0)

    private fun premiumStairs(def: ByteArray): ByteArray {
        val at = opcodePosition(34872, def, 40)
        check(at >= 0) { "stairs have no recolour" }
        val pairs = def[at + 1].toInt() and 0xFF
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(41, 4, 0, 70, 0, 254.toByte(), 1, 121, 0, 254.toByte(), 0, 67, 0, 254.toByte(), 0, 105, 0, 254.toByte()))
        out.write(def, 0, at)
        out.write(40)
        out.write(pairs + STAIR_STONE.size)
        for (i in 0 until pairs) {
            val p = at + 2 + i * 4
            val dst = ((def[p + 2].toInt() and 0xFF) shl 8) or (def[p + 3].toInt() and 0xFF)
            val gold = STAIR_RAIL_GOLD[dst] ?: error("unexpected rail tint ${dst.toString(16)}")
            out.write(def, p, 2)
            out.write(gold shr 8)
            out.write(gold and 0xFF)
        }
        STAIR_STONE.forEach { (src, dst) -> out.write(byteArrayOf((src shr 8).toByte(), src.toByte(), (dst shr 8).toByte(), dst.toByte())) }
        val rest = at + 2 + pairs * 4
        out.write(def, rest, def.size - rest)
        return out.toByteArray()
    }

    /** [def] with [pairs] (source colour to target colour) as its only recolour, inserted before everything else. */
    private fun recoloured(def: ByteArray, pairs: List<Pair<Int, Int>>, prefix: ByteArray = ByteArray(0)): ByteArray {
        check(opcodePosition(0, def, 40) < 0) { "already recoloured" }
        val out = java.io.ByteArrayOutputStream()
        out.write(prefix)
        out.write(40)
        out.write(pairs.size)
        pairs.forEach { (s, d) -> out.write(byteArrayOf((s shr 8).toByte(), s.toByte(), (d shr 8).toByte(), d.toByte())) }
        out.write(def)
        return out.toByteArray()
    }

    /**
     * The balcony railing: the wrought-iron railing 15602 gilded (its iron 0031/0039 in bright gold) on white marble
     * plinths (the stone 2812-281a near-white). Owner 2026-09-26: "balcony rails" can be better.
     */
    private fun gildedRailing(def: ByteArray) =
        recoloured(def, listOf(0x0031 to 0x23BC, 0x0039 to 0x23C6, 0x2812 to 0x0074, 0x2816 to 0x0078, 0x281a to 0x007C))

    /**
     * The portico pillar: the Grand Exchange pillar 47169 in bright white ("a white bright color instead of champagne")
     * at 0.95 height, so its top stays under the balcony floor (level 1, 240 up) instead of showing through it.
     */
    private fun brightPillar(def: ByteArray) =
        recoloured(
            def,
            listOf(0x802c to 0x0076, 0x8030 to 0x0078, 0x8031 to 0x0078, 0x8036 to 0x007A, 0x803b to 0x007C, 0x8040 to 0x007E, 0x8044 to 0x007F,
                // the base in gold (owner 2026-09-26: "white gold base pilars", C:/RSPS/foto/pillar_options.png option 3)
                0x8066 to 0x23B2, 0x806a to 0x23B8, 0x806e to 0x23BE, 0x8075 to 0x23C0),
            prefix = byteArrayOf(66, 0, 122),
        )

    /**
     * The stall counter: the opulent table 35454 (cream marble with gold) at half length (1 x 1, z scale 0.5) and
     * see-through (opcode 18), so npcs are talked to across it. The retextured counter kept its wooden planks in game.
     */
    private fun opulentCounter(def: ByteArray): ByteArray {
        val at = (0 until def.size - 1).first { def[it] == 15.toByte() && def[it + 1] == 2.toByte() }
        val out = def.copyOf()
        out[at + 1] = 1
        return byteArrayOf(18, 67, 0, 64) + out
    }

    /** Variants that keep their menu options (the staircases). */
    val WITH_OPTIONS = setOf(62779, 62780)

    /** [def] made see-through for projectiles (opcode 18), so npcs behind it are talked to across it like a bank booth. */
    private fun counterReach(def: ByteArray): ByteArray {
        check(opcodePosition(0, def, 18) < 0) { "already see-through" }
        return byteArrayOf(18) + def
    }

    val VARIANTS =
        listOf(
            Variant(62771, 43953, "Royal Hall stall counter: carved mahogany desk 43953 (3x1), see-through", ::counterReach),
            Variant(62772, 41215, "Royal Hall stall counter: mahogany corner desk 41215 (1x1), see-through", ::counterReach),
            Variant(62773, SLATE, "Royal Hall royal-blue slate roof with gilded ridges (41409 recoloured)", ::royalSlate),
            Variant(62776, 45238, "Royal Hall premium stall counter: white marble and gold (45238 at half length)", ::premiumCounter),
            Variant(62778, 36695, "Royal Hall fountain: the Fountain of Heroes widened to 4x4", ::grandFountain),
            Variant(62781, 35454, "Royal Hall stall counter: the opulent table at half length, see-through", ::opulentCounter),
            Variant(62782, 47169, "Royal Hall portico pillar: the GE pillar in bright white, 0.95 high", ::brightPillar),
            Variant(62783, 15602, "Royal Hall balcony railing: gilded on white marble plinths", ::gildedRailing),
            Variant(62779, 34872, "Royal Hall spiral staircase (bottom): white marble, gilded rails", ::premiumStairs),
            Variant(62780, 34873, "Royal Hall spiral staircase (top): white marble, gilded rails", ::premiumStairs),
            Variant(62768, 15548, "78 carpet corner (Varrock carpet 15548 with the 78 texture)", ::with78Carpet),
            Variant(62769, 15549, "78 carpet edge (Varrock carpet 15549 with the 78 texture)", ::with78Carpet),
            Variant(62770, 15550, "78 carpet middle (Varrock carpet 15550 with the 78 texture)", ::with78Carpet),
            Variant(62766, 43730, "Royal Hall corner turret (Falador castle turret 43730 at 1.375x)", ::grandTurret),
            Variant(ICON_LOC, GE_MARKER, "Royal Hall map marker (27990 carrying map element $ICON_ELEMENT)") { withElement(it, ICON_ELEMENT) },
            Variant(62763, 22435, "Royal Hall open gold door, west leaf (22435 without its option)", ::withoutLeadingOption),
            Variant(62764, 22437, "Royal Hall open gold door, east leaf (22437 without its option)", ::withoutLeadingOption),
            Variant(62784, 1506, "Royal Hall open door, west leaf: white with gold studs (1506 without its option)", ::withoutOptions),
            Variant(62785, 1508, "Royal Hall open door, east leaf: white with gold studs (1508 without its option)", ::withoutOptions),
            Variant(62765, 37169, "Royal Hall 1x1 white marble stall counter (37169 at half length)", ::halfCounter),
        )

    /**
     * The Grand Exchange's visible paving in the centre is four 10 x 10 models (the plane-1 floor there is an invisible
     * walk surface, overlay 124). The hall's floor is cut out of copies of them: [newModel] is [model] with every face
     * whose centre lies on the hall's floor collapsed to a point, and loc [id] is [source] showing [newModel].
     * RoyalHallMapTool puts the copies in place of the originals at ([x], [z]).
     */
    class Cut(val id: Int, val source: Int, val model: Int, val newModel: Int, val x: Int, val z: Int)

    val CUTS =
        listOf(
            Cut(62759, 47606, 19802, 65428, 3155, 3492),
            Cut(62760, 47607, 19805, 65429, 3165, 3492),
            Cut(62761, 47909, 19817, 65430, 3155, 3482),
            Cut(62762, 47911, 19860, 65431, 3165, 3482),
        )
    private const val CUT_SIZE = 10
    private const val MODEL_INDEX = 7

    /**
     * [data] (a rev-667 mesh with the version byte, footer -1 -1) with the faces over the hall floor collapsed; returns
     * the new mesh and the number of faces cut. Version 13+ meshes use 512 units per tile (the client scales them by
     * 1/4), centred on the loc's footprint; +z is north.
     */
    fun cutModel(data: ByteArray, locX: Int, locZ: Int): Pair<ByteArray, Int> {
        val version = data[data.size - 24].toInt() and 0xFF
        check(data[data.size - 23 + 5].toInt() and 0x8 != 0 && version >= 13) { "expected a version-13+ mesh" }
        val model = Rev667ModelDecoder.decode(data)
        val unitsPerTile = 512.0
        var cut = 0
        for (f in 0 until model.faceCount) {
            val a = model.faceA[f]
            val b = model.faceB[f]
            val c = model.faceC[f]
            val cx = locX + CUT_SIZE / 2.0 + (model.vertexX[a] + model.vertexX[b] + model.vertexX[c]) / 3.0 / unitsPerTile
            val cz = locZ + CUT_SIZE / 2.0 + (model.vertexZ[a] + model.vertexZ[b] + model.vertexZ[c]) / 3.0 / unitsPerTile
            val onFloor =
                cx >= RoyalHallMapTool.FLOOR_MIN_X && cx < RoyalHallMapTool.FLOOR_MAX_X + 1 &&
                    cz >= RoyalHallMapTool.FLOOR_MIN_Z && cz < RoyalHallMapTool.FLOOR_MAX_Z + 1
            if (onFloor) {
                model.faceB[f] = a
                model.faceC[f] = a
                cut++
            }
        }
        // The encoder writes a version-12 trailer; put the version byte back in front of it and flag it (bit 3).
        val encoded = Rev667ModelEncoder.encode(model)
        val body = encoded.copyOf(encoded.size - 23)
        val trailer = encoded.copyOfRange(encoded.size - 23, encoded.size)
        trailer[5] = (trailer[5].toInt() or 0x8).toByte()
        return (body + byteArrayOf(version.toByte()) + trailer) to cut
    }

    /** [def] with every opcode-1 model id [from] replaced by [to]. */
    fun withModel(id: Int, def: ByteArray, from: Int, to: Int): ByteArray {
        val at = opcodePosition(id, def, 1)
        check(at >= 0) { "loc $id has no opcode 1" }
        val out = def.copyOf()
        var p = at + 1
        var replaced = 0
        repeat(out[p++].toInt() and 0xFF) {
            p++ // shape
            repeat(out[p++].toInt() and 0xFF) {
                val model = ((out[p].toInt() and 0xFF) shl 8) or (out[p + 1].toInt() and 0xFF)
                if (model == from) {
                    out[p] = (to shr 8).toByte()
                    out[p + 1] = to.toByte()
                    replaced++
                }
                p += 2
            }
        }
        check(replaced > 0) { "loc $id does not use model $from" }
        return out
    }

    /** Returns [def] with its opcode-71 (vertical offset) set to [offset], inserted before the terminator when absent. */
    fun withOffset(id: Int, def: ByteArray, offset: Int): ByteArray {
        val at = opcodePosition(id, def, 71)
        val value = byteArrayOf((offset shr 8).toByte(), offset.toByte())
        return if (at >= 0) {
            def.copyOf().also {
                it[at + 1] = value[0]
                it[at + 2] = value[1]
            }
        } else {
            check(def.last() == 0.toByte()) { "loc $id does not end with the opcode-0 terminator" }
            def.copyOf(def.size - 1) + byteArrayOf(71) + value + byteArrayOf(0)
        }
    }

    /**
     * Position of [wanted] in the opcode stream of a loc definition, or -1. Walks the stream with [Rev667LocType.decode]'s
     * operand sizes by decoding growing prefixes: the first prefix that ends right before an opcode byte equal to [wanted]
     * and decodes cleanly with that opcode's two operand bytes is the match.
     */
    private fun opcodePosition(id: Int, def: ByteArray, wanted: Int): Int {
        var pos = 0
        while (pos < def.size) {
            val op = def[pos].toInt() and 0xFF
            if (op == 0) return -1
            if (op == wanted) return pos
            pos += 1 + operandSize(id, def, pos)
        }
        return -1
    }

    private fun operandSize(id: Int, def: ByteArray, pos: Int): Int {
        val op = def[pos].toInt() and 0xFF
        fun u8(i: Int) = def[pos + 1 + i].toInt() and 0xFF
        return when (op) {
            1, 5 -> {
                var p = 0
                repeat(if (op == 5) 2 else 1) {
                    val shapes = u8(p)
                    p++
                    repeat(shapes) {
                        p++ // shape
                        val models = u8(p)
                        p += 1 + models * 2
                    }
                }
                p
            }
            2, in 30..34, in 150..154 -> {
                var p = 0
                while (def[pos + 1 + p] != 0.toByte()) p++
                p + 1
            }
            14, 15, 19, 28, 29, 39, 69, 75, 81, 101, 104, 162, 178 -> 1
            17, 18, 21, 22, 23, 27, 62, 64, 73, 74, 82, 88, 89, 90, 91, 94, 96, 97, 98, 103, 105, 168, 169, 177, 189 -> 0
            24, 65, 66, 67, 70, 71, 72, 93, 95, 102, 107, 164, 165, 166, 167 -> 2
            40, 41 -> 1 + u8(0) * 4
            42 -> 1 + u8(0)
            78 -> 3
            99, 100 -> 3
            163 -> 4
            173 -> 4
            else -> error("loc $id: opcode $op at $pos is not handled by RoyalHallLocTool")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            COPIES.forEach { copy ->
                val source = library.data(LOC_INDEX, copy.source ushr 8, copy.source and 0xFF) ?: error("loc ${copy.source} missing")
                // The lifted slate rings share the royal roof's colours.
                val base = if (copy.source == SLATE) royalSlate(source) else source
                val wanted = copy.offset?.let { withOffset(copy.source, base, it) } ?: base.copyOf()
                val before = Rev667LocType.decode(copy.source, source)
                val after = Rev667LocType.decode(copy.id, wanted)
                check(after.allModels == before.allModels && after.modelsByShape.keys == before.modelsByShape.keys) { "copy ${copy.id} changed its models" }
                val current = library.data(LOC_INDEX, copy.id ushr 8, copy.id and 0xFF)
                when {
                    current == null -> println("CREATE loc ${copy.id} from ${copy.source} offset ${copy.offset}: ${copy.label}")
                    current.contentEquals(wanted) -> return@forEach println("LOC ${copy.id} already in place")
                    else -> println("REPLACE loc ${copy.id} (Royal Hall copy redefined): ${copy.label}")
                }
                mutations += CacheMutation(LOC_INDEX, copy.id ushr 8, copy.id and 0xFF, wanted, copy.label, current?.let { CacheItemProbeTool.sha1(it) })
            }
            // The world map draws its icons from its own prebuilt data, not the landscape: the icon joins the main area's
            // static elements (index 23, group "main_staticelements": one file per icon = u32 packed coord, u16 element,
            // u8 members), on the fountain beside the Grand Exchange label.
            val staticGroup = library.index(WORLD_MAP_INDEX).archive(STATIC_ELEMENTS) ?: error("$STATIC_ELEMENTS missing")
            val coord = (ICON_TILE_X shl 14) or ICON_TILE_Z
            val staticEntry = byteArrayOf((coord ushr 24).toByte(), (coord ushr 16).toByte(), (coord ushr 8).toByte(), coord.toByte(), (ICON_ELEMENT shr 8).toByte(), ICON_ELEMENT.toByte(), 0)
            val existing = staticGroup.fileIds().firstOrNull { library.data(WORLD_MAP_INDEX, staticGroup.id, it)?.contentEquals(staticEntry) == true }
            if (existing != null) {
                println("WORLD MAP icon already in place (file $existing)")
            } else {
                val file = staticGroup.fileIds().maxOrNull()!! + 1
                println("CREATE world map icon: $STATIC_ELEMENTS file $file, element $ICON_ELEMENT at $ICON_TILE_X,$ICON_TILE_Z")
                mutations += CacheMutation(WORLD_MAP_INDEX, staticGroup.id, file, staticEntry, "Royal Hall world map icon", null)
            }
            val sprite = StoreArtTool.encode(iconImage())
            val currentSprite = library.data(SPRITE_INDEX, ICON_SPRITE, 0)
            when {
                currentSprite == null -> println("CREATE sprite $ICON_SPRITE (Royal Hall map icon)")
                currentSprite.contentEquals(sprite) -> println("SPRITE $ICON_SPRITE already in place")
                else -> println("REPLACE sprite $ICON_SPRITE (Royal Hall map icon redrawn)")
            }
            if (currentSprite == null || !currentSprite.contentEquals(sprite)) {
                mutations += CacheMutation(SPRITE_INDEX, ICON_SPRITE, 0, sprite, "Royal Hall map icon sprite", currentSprite?.let { CacheItemProbeTool.sha1(it) })
            }
            val element = withSprite(library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, GE_ELEMENT) ?: error("map element $GE_ELEMENT missing"), ICON_SPRITE)
            val currentElement = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, ICON_ELEMENT)
            when {
                currentElement == null -> {
                    println("CREATE map element $ICON_ELEMENT (637 with sprite $ICON_SPRITE)")
                    mutations += CacheMutation(CONFIG_INDEX, MAP_ELEMENT_GROUP, ICON_ELEMENT, element, "Royal Hall map element", null)
                }
                currentElement.contentEquals(element) -> println("MAP ELEMENT $ICON_ELEMENT already in place")
                else -> error("map element $ICON_ELEMENT already exists with other content; refusing to overwrite it")
            }
            val carpetSprite = OsrsTextureImportTool.opaqueSprite(carpetImage())
            val currentCarpetSprite = library.data(SPRITE_INDEX, CARPET_SPRITE, 0)
            if (currentCarpetSprite == null || !currentCarpetSprite.contentEquals(carpetSprite)) {
                println("${if (currentCarpetSprite == null) "CREATE" else "REPLACE"} sprite $CARPET_SPRITE (78 carpet texture image)")
                mutations += CacheMutation(SPRITE_INDEX, CARPET_SPRITE, 0, carpetSprite, "78 carpet texture sprite", currentCarpetSprite?.let { CacheItemProbeTool.sha1(it) })
            }
            val template = library.data(TEXTURE_INDEX, SPRITE_TEXTURE_TEMPLATE, 0) ?: error("texture $SPRITE_TEXTURE_TEMPLATE missing")
            val at = OsrsTextureImportTool.spriteParamOffset(template)
            val program = template.copyOf().also { it[at] = (CARPET_SPRITE ushr 8).toByte(); it[at + 1] = CARPET_SPRITE.toByte() }
            val currentProgram = library.data(TEXTURE_INDEX, CARPET_TEXTURE, 0)
            when {
                currentProgram == null -> {
                    println("CREATE texture $CARPET_TEXTURE (sprite $CARPET_SPRITE)")
                    mutations += CacheMutation(TEXTURE_INDEX, CARPET_TEXTURE, 0, program, "78 carpet texture program", null)
                }
                currentProgram.contentEquals(program) -> println("TEXTURE $CARPET_TEXTURE already in place")
                else -> error("texture $CARPET_TEXTURE already exists with other content; refusing to overwrite it")
            }
            val materialsBytes = library.data(MATERIALS_INDEX, 0, 0) ?: error("materials table missing")
            val materials = OsrsTextureImportTool.Materials.decode(materialsBytes)
            val row = Array(OsrsTextureImportTool.MATERIAL_COLUMNS.size) { materials.rows[VARROCK_CARPET_TEXTURE]!![it].copyOf() }
            row[1][0] = 0 // small = false: a 128 px sprite
            row[7][0] = (CARPET_AVERAGE_HSL shr 8).toByte() // low-detail colour: royal purple
            row[7][1] = CARPET_AVERAGE_HSL.toByte()
            row[OsrsTextureImportTool.COL_SPEED_U][0] = 0
            row[OsrsTextureImportTool.COL_SPEED_V][0] = 0
            when {
                materials.present.size == CARPET_TEXTURE -> {
                    println("APPEND material row $CARPET_TEXTURE")
                    val updated = OsrsTextureImportTool.Materials(materials.present + true, materials.rows + arrayOf(row)).encode()
                    mutations += CacheMutation(MATERIALS_INDEX, 0, 0, updated, "materials + 78 carpet texture $CARPET_TEXTURE", CacheItemProbeTool.sha1(materialsBytes))
                }
                materials.rows.getOrNull(CARPET_TEXTURE)?.let { r -> r.indices.all { r[it].contentEquals(row[it]) } } == true -> println("MATERIAL $CARPET_TEXTURE already in place")
                else -> error("materials table has ${materials.present.size} rows; texture $CARPET_TEXTURE is taken by something else")
            }
            // The corner turret without its little guard (TURRET_MODEL_CLEAN, used by 62766).
            val turretSource = library.data(MODEL_INDEX, TURRET_MODEL, 0) ?: error("turret model missing")
            val (turretMesh, turretCut) = turretWithoutGuard(turretSource)
            val currentTurret = library.data(MODEL_INDEX, TURRET_MODEL_CLEAN, 0)
            println("MODEL $TURRET_MODEL_CLEAN = $TURRET_MODEL without its guard ($turretCut faces) ${if (currentTurret == null) "create" else if (currentTurret.contentEquals(turretMesh)) "in place" else "REPLACE"}")
            if (currentTurret == null || !currentTurret.contentEquals(turretMesh)) {
                mutations += CacheMutation(MODEL_INDEX, TURRET_MODEL_CLEAN, 0, turretMesh, "Royal Hall turret without the guard", currentTurret?.let { CacheItemProbeTool.sha1(it) })
            }
            VARIANTS.forEach { v ->
                val source = library.data(LOC_INDEX, v.source ushr 8, v.source and 0xFF) ?: error("loc ${v.source} missing")
                val wanted = v.edit(source)
                val before = Rev667LocType.decode(v.source, source)
                val after = Rev667LocType.decode(v.id, wanted)
                val expectedModels = before.allModels.map { if (it == TURRET_MODEL && v.source == 43730) TURRET_MODEL_CLEAN else it }
                check(after.allModels == expectedModels && (v.id in WITH_OPTIONS || after.options.all { it == null || it.equals("hidden", true) })) { "variant ${v.id} is wrong" }
                val current = library.data(LOC_INDEX, v.id ushr 8, v.id and 0xFF)
                when {
                    current == null -> println("CREATE loc ${v.id} from ${v.source}: ${v.label} (size ${after.sizeX}x${after.sizeZ})")
                    current.contentEquals(wanted) -> return@forEach println("LOC ${v.id} already in place")
                    else -> println("REPLACE loc ${v.id} (Royal Hall variant redefined): ${v.label}")
                }
                mutations += CacheMutation(LOC_INDEX, v.id ushr 8, v.id and 0xFF, wanted, v.label, current?.let { CacheItemProbeTool.sha1(it) })
            }
            CUTS.forEach { cut ->
                val source = library.data(MODEL_INDEX, cut.model, 0) ?: error("model ${cut.model} missing")
                val (mesh, faces) = cutModel(source, cut.x, cut.z)
                val back = Rev667ModelDecoder.decode(mesh)
                val orig = Rev667ModelDecoder.decode(source)
                check(back.vertexCount == orig.vertexCount && back.faceCount == orig.faceCount) { "model ${cut.newModel} changed its size" }
                val currentMesh = library.data(MODEL_INDEX, cut.newModel, 0)
                println("MODEL ${cut.newModel} = ${cut.model} with $faces of ${orig.faceCount} faces cut (${if (currentMesh == null) "create" else if (currentMesh.contentEquals(mesh)) "in place" else "REPLACE"})")
                if (currentMesh == null || !currentMesh.contentEquals(mesh)) {
                    mutations += CacheMutation(MODEL_INDEX, cut.newModel, 0, mesh, "Royal Hall floor cut from GE paving model ${cut.model}", currentMesh?.let { CacheItemProbeTool.sha1(it) })
                }
                val def = library.data(LOC_INDEX, cut.source ushr 8, cut.source and 0xFF) ?: error("loc ${cut.source} missing")
                val wanted = withModel(cut.source, def, cut.model, cut.newModel)
                val current = library.data(LOC_INDEX, cut.id ushr 8, cut.id and 0xFF)
                when {
                    current == null -> println("CREATE loc ${cut.id} = ${cut.source} showing model ${cut.newModel}")
                    current.contentEquals(wanted) -> return@forEach println("LOC ${cut.id} already in place")
                    else -> error("loc ${cut.id} already exists with other content; refusing to overwrite it")
                }
                mutations += CacheMutation(LOC_INDEX, cut.id ushr 8, cut.id and 0xFF, wanted, "Royal Hall paving ${cut.id} (${cut.source} without the hall floor)", null)
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
