package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N503 D1 (owner 2026-11-01 01:5x, letterlijk: "prot item moet dus disabled worden moet er wel gewoon staan maar net als in osrs bij een
 * pvp high riskwereld dat icoon"): Protect Item stays in the prayer tab of all three books, drawn with the OSRS High Risk icon - OSRS
 * sprite 912 (OpenRS2 2686), the Protect Item pouch with a skull and crossbones (found by shape against the normal icon 123; checked by
 * eye 2026-11-01). It is imported once as 667 sprite [SPRITE] and set as both the coloured (param 735) and grey (param 736) icon of the
 * three Protect Item cells: struct 668 (normal book, enum 2279), 888 (curses, enum 863) and 2766 (Ruinous Powers). The hover text's
 * effect line (param 734) says it does not work. The server refuses every activation (ProtectItemDisabled).
 *
 * One [CacheTransaction] over both production caches; running it again is a no-op.
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.ProtectItemHighRiskCacheTool plan|apply`
 */
object ProtectItemHighRiskCacheTool {
    private const val CONFIG_INDEX = 2
    private const val SPRITE_INDEX = 8
    private const val STRUCT_GROUP = 26

    /** OSRS 2686 sprite: Protect Item with the High Risk skull. */
    const val OSRS_SPRITE = 912

    /** First free 667 sprite on 2026-11-01 (HOOFDLIJST: taken up to 8806). */
    const val SPRITE = 8807

    const val PARAM_TOOLTIP = 734
    const val PARAM_COLOUR = 735
    const val PARAM_GREY = 736

    const val DISABLED_LINE = "Does not work in Deadman Mode."

    /** struct -> its effect line today (replaced by [DISABLED_LINE]). */
    val STRUCTS =
        linkedMapOf(
            668 to "Keep 1 extra item if you die",
            888 to "Keep 1 extra item if you die",
            2766 to "Protect one additional item on death.",
        )

    /** A struct's params in cache order: key -> Int or String. */
    fun decode(b: ByteArray): LinkedHashMap<Int, Any> {
        val out = LinkedHashMap<Int, Any>()
        var p = 0
        fun u1() = b[p++].toInt() and 0xFF
        while (p < b.size) {
            val op = u1()
            if (op == 0) break
            check(op == 249) { "unexpected struct opcode $op" }
            repeat(u1()) {
                val isString = u1() == 1
                val key = (u1() shl 16) or (u1() shl 8) or u1()
                if (isString) {
                    val start = p
                    while (b[p].toInt() != 0) p++
                    out[key] = String(b, start, p - start, Charsets.ISO_8859_1)
                    p++
                } else {
                    out[key] = (u1() shl 24) or (u1() shl 16) or (u1() shl 8) or u1()
                }
            }
        }
        return out
    }

    fun encode(params: Map<Int, Any>): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(249)
        o.write(params.size)
        params.forEach { (key, value) ->
            o.write(if (value is String) 1 else 0)
            o.write(key ushr 16)
            o.write(key ushr 8)
            o.write(key)
            if (value is String) {
                o.write(value.toByteArray(Charsets.ISO_8859_1))
                o.write(0)
            } else {
                val v = value as Int
                o.write(v ushr 24)
                o.write(v ushr 16)
                o.write(v ushr 8)
                o.write(v)
            }
        }
        o.write(0)
        return o.toByteArray()
    }

    /** [current] with the High Risk icon and the "does not work" line; null when already done. */
    fun patched(
        current: ByteArray,
        oldLine: String,
    ): ByteArray? {
        val params = decode(current)
        check(encode(params).contentEquals(current)) { "struct does not round-trip" }
        val tooltip = params[PARAM_TOOLTIP] as String
        if (params[PARAM_COLOUR] == SPRITE && params[PARAM_GREY] == SPRITE && tooltip.endsWith(DISABLED_LINE)) return null
        check(tooltip.endsWith(oldLine) || tooltip.endsWith(DISABLED_LINE)) { "tooltip '$tooltip' does not end with '$oldLine'" }
        params[PARAM_COLOUR] = SPRITE
        params[PARAM_GREY] = SPRITE
        params[PARAM_TOOLTIP] = tooltip.removeSuffix(oldLine).removeSuffix(DISABLED_LINE) + DISABLED_LINE
        return encode(params)
    }

    fun plan(): List<CacheMutation> {
        val mutations = ArrayList<CacheMutation>()
        val osrs = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { it.file(SPRITE_INDEX, OSRS_SPRITE, 0) } ?: error("OSRS sprite $OSRS_SPRITE missing")
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            val present = library.data(SPRITE_INDEX, SPRITE, 0)
            if (present == null) {
                mutations += CacheMutation(SPRITE_INDEX, SPRITE, 0, osrs, "sprite $SPRITE: OSRS $OSRS_SPRITE Protect Item (High Risk)")
            } else {
                check(present.contentEquals(osrs)) { "sprite $SPRITE is taken by something else" }
            }
            STRUCTS.forEach { (struct, oldLine) ->
                val current = requireNotNull(library.data(CONFIG_INDEX, STRUCT_GROUP, struct)) { "struct $struct missing" }
                patched(current, oldLine)?.let {
                    mutations += CacheMutation(CONFIG_INDEX, STRUCT_GROUP, struct, it, "struct $struct: Protect Item High Risk icon + text", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
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
            println("PROTECT_ITEM_HIGH_RISK already applied")
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
        println("PROTECT_ITEM_HIGH_RISK transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
