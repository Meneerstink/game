package gg.rsmod.game.tools.importer

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * OSRS hitsplats 1:1 (owner 2026-10-09, block 1 of the 10-hour plan: "FULL OSRS - 100 % size, OSRS 4-slot layout").
 *
 * Imports every OSRS hitsplat definition (pinned OSRS cache 2686, config archive 2 group 32, ids 0-82) into our hitmark config
 * group 46 at the same ids, so the OSRS multi-splats (12 -> 26/27, 16 -> 28/29, 43 -> 48/28, ...) keep pointing at the right
 * definitions. Source: `tools\cache667\hitsplat_defs.py osrs` dump; research `agent-handoffs\2026-10-09-plan-research-hitsplats.md`.
 *
 *  - Definitions: decoded with the OSRS opcodes (sprite / font fields are big smarts in OSRS) and re-encoded with the 667
 *    opcodes our client's `HitmarkType.decode` reads (same opcodes, sprite / font as g2) plus 17/18 (multi by varbit), which
 *    the client decodes like OSRS. Sprite ids are remapped onto the imported sprites; ids 0-27 replace the 667 hitmarks.
 *  - Sprites: every OSRS sprite a definition uses, group bytes copied unchanged (the OSRS sprite container is the trailer format
 *    this client's `IndexedImage` decodes - proven by LootKeyInterfaceImportTool) to [SPRITE_BASE]...
 *  - Font: OSRS draws a splat without a font in `fontPlain11` (OSRS p11_full, sprite group 494). Our 667 p11 differs in the "0"
 *    glyph (square OSRS 0), so OSRS p11 is imported as its own font [FONT_ID]: the sprite group unchanged, the metrics converted
 *    from the OSRS layout (256 advance widths + ascent) into the 667 FontMetrics layout (magic 0, fixed width, 256 widths,
 *    then the 667 p11 spacing / padding bytes - the advance widths of every printable glyph are identical in both caches).
 *    Every imported definition with text names [FONT_ID] (opcode 1), so the client needs no fallback rule.
 *
 * Usage: `./gradlew :game:runOsrsHitsplatImportTool --args="[--apply]"` (servers stopped; CacheTransaction refuses otherwise).
 */
object OsrsHitsplatImportTool {
    val SOURCE_CACHE = OsrsItemImportTool.SOURCE_CACHE
    val TARGETS = OsrsItemImportTool.TARGETS

    const val INDEX_CONFIG = 2
    const val INDEX_SPRITES = 8
    const val INDEX_FONT_METRICS = 13
    const val OSRS_HITSPLAT_GROUP = 32
    const val HITMARK_GROUP = 46

    /** OSRS hitsplat ids 0..82 (cache 2686). */
    const val OSRS_COUNT = 83

    /** The 667 cache's own hitmark ids 0..27, replaced by the OSRS definitions of the same id. */
    const val CACHE_667_COUNT = 28

    /** First new 667 sprite id: the first id above the cache's last sprite group (8400 on 2026-10-09). */
    const val SPRITE_BASE = 8401

    /** OSRS p11_full (sprite group and font metrics group 494 in cache 2686). */
    const val OSRS_P11 = 494

    class Def(val ops: List<Pair<Int, Any?>>)

    private class Reader(val b: ByteArray) {
        var p = 0

        fun u1() = b[p++].toInt() and 0xFF

        fun u2() = (u1() shl 8) or u1()

        fun s2() = u2().toShort().toInt()

        fun u3() = (u1() shl 16) or (u1() shl 8) or u1()

        fun bigSmart(): Int {
            if (b[p].toInt() and 0x80 != 0) {
                val v = (u1() shl 24) or (u1() shl 16) or (u1() shl 8) or u1()
                return v and 0x7FFFFFFF
            }
            val v = u2()
            return if (v == 32767) -1 else v
        }

        fun jstr2(): String {
            check(u1() == 0) { "jstr2 without the 0 prefix" }
            val start = p
            while (b[p].toInt() != 0) p++
            return String(b, start, p++ - start, Charsets.ISO_8859_1)
        }
    }

    /** Decodes one OSRS hitsplat definition (OSRS HitSplatDefinition.decodeNext, rev 184+ / cache 2686). */
    fun decodeOsrs(bytes: ByteArray): Def {
        val r = Reader(bytes)
        val ops = ArrayList<Pair<Int, Any?>>()
        while (true) {
            val op = r.u1()
            if (op == 0) break
            ops +=
                op to
                when (op) {
                    1, 3, 4, 5, 6 -> r.bigSmart()
                    2 -> r.u3()
                    7, 10, 13 -> r.s2()
                    8 -> r.jstr2()
                    9, 14 -> r.u2()
                    11 -> null
                    12 -> r.u1()
                    17, 18 -> {
                        val varbit = r.u2()
                        val varp = r.u2()
                        val last = if (op == 18) r.u2() else 65535
                        val n = r.u1()
                        val ids = IntArray(n + 1) { r.u2() }
                        intArrayOf(varbit, varp, last) + ids
                    }
                    else -> error("unknown OSRS hitsplat opcode $op")
                }
        }
        return Def(ops)
    }

    /** OSRS sprite ids a definition draws (icon, left, inner, right). */
    fun sprites(def: Def): List<Int> = def.ops.filter { it.first in 3..6 && (it.second as Int) >= 0 }.map { it.second as Int }

    /** Re-encodes [def] in the 667 layout with sprites remapped by [sprite] and, for a text splat, font [fontId] first. */
    fun encode667(
        def: Def,
        sprite: (Int) -> Int,
        fontId: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        fun p1(v: Int) = out.write(v and 0xFF)

        fun p2(v: Int) {
            p1(v shr 8)
            p1(v)
        }
        val hasText = def.ops.any { it.first == 8 }
        val hasFont = def.ops.any { it.first == 1 && (it.second as Int) >= 0 }
        if (hasText && !hasFont) {
            p1(1)
            p2(fontId)
        }
        def.ops.forEach { (op, v) ->
            when (op) {
                1 -> {
                    val font = v as Int
                    if (font >= 0) {
                        // No OSRS hitsplat names a font (cache 2686); refuse silently mapping an OSRS font id onto a 667 one.
                        error("OSRS hitsplat names font $font - not mapped")
                    }
                }
                3, 4, 5, 6 -> {
                    val id = v as Int
                    if (id >= 0) {
                        p1(op)
                        p2(sprite(id))
                    }
                }
                2 -> {
                    p1(op)
                    val c = v as Int
                    p1(c shr 16)
                    p2(c)
                }
                7, 10, 13 -> {
                    p1(op)
                    p2(v as Int)
                }
                8 -> {
                    p1(op)
                    p1(0)
                    out.write((v as String).toByteArray(Charsets.ISO_8859_1))
                    p1(0)
                }
                9, 14 -> {
                    p1(op)
                    p2(v as Int)
                }
                11 -> p1(op)
                12 -> {
                    p1(op)
                    p1(v as Int)
                }
                17, 18 -> {
                    val a = v as IntArray
                    p1(op)
                    p2(a[0])
                    p2(a[1])
                    if (op == 18) p2(a[2])
                    val ids = a.copyOfRange(3, a.size)
                    p1(ids.size - 1)
                    ids.forEach { p2(it) }
                }
            }
        }
        p1(0)
        return out.toByteArray()
    }

    /**
     * 667 FontMetrics bytes for the OSRS p11 font: magic 0, fixed width (0), the 256 OSRS advance widths, then [tail667]
     * (vertical spacing, two unused bytes, padding top / bottom of our own p11).
     */
    fun fontMetrics667(
        osrsMetrics: ByteArray,
        tail667: ByteArray,
    ): ByteArray {
        require(osrsMetrics.size >= 256) { "OSRS font metrics too short: ${osrsMetrics.size}" }
        require(tail667.size == 5) { "667 font metrics tail must be 5 bytes" }
        return byteArrayOf(0, 0) + osrsMetrics.copyOfRange(0, 256) + tail667
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val source = ModernCacheReader(File(SOURCE_CACHE))
        val mutations = mutableListOf<CacheMutation>()
        val defs: List<Def>
        val osrsSprites: List<Int>
        val osrsFontSprite: ByteArray
        val osrsFontMetrics: ByteArray
        source.use {
            val files = source.files(INDEX_CONFIG, OSRS_HITSPLAT_GROUP)
            defs = (0 until OSRS_COUNT).map { id -> decodeOsrs(files[id] ?: error("OSRS hitsplat $id missing")) }
            osrsSprites = defs.flatMap(::sprites).distinct().sorted()
            osrsFontSprite = source.file(INDEX_SPRITES, OSRS_P11, 0) ?: error("OSRS p11 sprite missing")
            osrsFontMetrics = source.file(INDEX_FONT_METRICS, OSRS_P11, 0) ?: error("OSRS p11 metrics missing")
            osrsSprites.forEachIndexed { index, osrsId ->
                val bytes = source.file(INDEX_SPRITES, osrsId, 0) ?: error("OSRS sprite $osrsId missing")
                mutations += CacheMutation(INDEX_SPRITES, SPRITE_BASE + index, 0, bytes, "hitsplat sprite osrs:$osrsId -> 667:${SPRITE_BASE + index}")
            }
        }
        val fontId = SPRITE_BASE + osrsSprites.size
        fun sprite(osrsId: Int) = SPRITE_BASE + osrsSprites.indexOf(osrsId).also { require(it >= 0) }

        // Our own p11 (by name) for the metrics tail, and the current 667 hitmarks 0-27 the import replaces (pinned by sha1).
        val current = HashMap<Int, String>()
        val tail667: ByteArray
        com.displee.cache.CacheLibrary(TARGETS[0]).let { library ->
            try {
                val p11 = library.index(INDEX_SPRITES).archive("p11_full")?.id ?: error("p11_full not found")
                val metrics = library.data(INDEX_FONT_METRICS, p11, 0) ?: error("667 p11 metrics missing")
                tail667 = metrics.copyOfRange(metrics.size - 5, metrics.size)
                println("667 p11_full=$p11 metrics=${metrics.size}b tail=${tail667.joinToString(",") { (it.toInt() and 0xFF).toString() }}")
                for (id in 0 until CACHE_667_COUNT) {
                    library.data(INDEX_CONFIG, HITMARK_GROUP, id)?.let { current[id] = CacheItemProbeTool.sha1(it) }
                }
            } finally {
                library.close()
            }
        }
        mutations += CacheMutation(INDEX_SPRITES, fontId, 0, osrsFontSprite, "hitsplat font sprite osrs:p11_full -> 667:$fontId")
        mutations += CacheMutation(INDEX_FONT_METRICS, fontId, 0, fontMetrics667(osrsFontMetrics, tail667), "hitsplat font metrics osrs:p11 -> 667:$fontId")
        defs.forEachIndexed { id, def ->
            mutations +=
                CacheMutation(
                    INDEX_CONFIG,
                    HITMARK_GROUP,
                    id,
                    encode667(def, ::sprite, fontId),
                    "hitmark $id <- osrs hitsplat $id",
                    expectedCurrentSha1 = current[id],
                )
        }

        val transaction = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("SPRITES ${osrsSprites.size}: ${osrsSprites.mapIndexed { i, s -> "$s->${SPRITE_BASE + i}" }.joinToString(" ")}")
        println("FONT $fontId")
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write hitmarks 0..${OSRS_COUNT - 1}, sprites $SPRITE_BASE..$fontId and font metrics $fontId")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) {
            println("VERIFY_OK transaction=${transaction.id}")
        } else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
