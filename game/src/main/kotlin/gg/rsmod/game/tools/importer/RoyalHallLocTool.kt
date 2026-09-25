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
 * originals. Appended as new loc ids after the last one in the cache; one [CacheTransaction] over both production caches,
 * idempotent.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.RoyalHallLocTool plan|apply`
 */
object RoyalHallLocTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val LOC_INDEX = 16

    const val SLATE = 41409
    const val GLASS = 47841

    /** The slate piece's own opcode-71 value; the rings above add their lift to it. */
    private const val SLATE_BASE_OFFSET = -6
    private const val SLATE_RISE = 70

    /** Glass slope faces start 16 above the loc; the lantern's base meets the top of ring 5 (216 + 70 above level 3). */
    private const val GLASS_OFFSET = -(3 * SLATE_RISE + SLATE_RISE - SLATE_BASE_OFFSET - 16)

    class Copy(val id: Int, val source: Int, val offset: Int, val label: String)

    val COPIES =
        listOf(
            Copy(62754, SLATE, SLATE_BASE_OFFSET - SLATE_RISE, "Royal Hall roof ring 3 (slate 41409 raised 70)"),
            Copy(62755, SLATE, SLATE_BASE_OFFSET - 2 * SLATE_RISE, "Royal Hall roof ring 4 (slate 41409 raised 140)"),
            Copy(62756, SLATE, SLATE_BASE_OFFSET - 3 * SLATE_RISE, "Royal Hall roof ring 5 (slate 41409 raised 210)"),
            Copy(62757, GLASS, GLASS_OFFSET, "Royal Hall glass lantern (glass roof 47841 raised onto ring 5)"),
        )

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
                val wanted = withOffset(copy.source, source, copy.offset)
                val before = Rev667LocType.decode(copy.source, source)
                val after = Rev667LocType.decode(copy.id, wanted)
                check(after.allModels == before.allModels && after.modelsByShape.keys == before.modelsByShape.keys) { "copy ${copy.id} changed its models" }
                val current = library.data(LOC_INDEX, copy.id ushr 8, copy.id and 0xFF)
                when {
                    current == null -> println("CREATE loc ${copy.id} from ${copy.source} offset ${copy.offset}: ${copy.label}")
                    current.contentEquals(wanted) -> return@forEach println("LOC ${copy.id} already in place")
                    else -> error("loc ${copy.id} already exists with other content; refusing to overwrite it")
                }
                mutations += CacheMutation(LOC_INDEX, copy.id ushr 8, copy.id and 0xFF, wanted, copy.label, null)
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
