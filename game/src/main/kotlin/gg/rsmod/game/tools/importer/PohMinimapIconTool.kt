package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Fixed minimap icons for the player-owned house (owner 2026-10-10: "controleer ook gelijk de minimap iconen in de poh";
 * answer "Alleen POH vast").
 *
 * WHY THE HOUSE ICONS WANDERED. The client collects a loc's map element in `Minimap.drawLevel` and, when the element's
 * MapElementType has `randomise` set (opcode 8 = 1), walks the icon up to 3 tiles onto free neighbouring tiles at random,
 * on every minimap rebuild. The altar (575), transport arrow (612) and portal (621) elements all carry `08 01`, so in the
 * small, furnished house rooms their icons landed beside the obelisk, in doorways and in the wrong room. Only 30 of the
 * cache's 1113 element types are fixed, and none of them is one of these three pictures.
 *
 * WHAT THIS WRITES (house only - the world keeps Jagex's wandering icons). Three element types, byte-for-byte copies of
 * 575 / 612 / 621 with `08 01` turned into `08 00` ([FIXED_ELEMENTS]), and three invisible marker locs, copies of Jagex's
 * own markers 2752 / 7389 / 15531 with their map element (loc opcode 107, one short) pointing at the copy ([MARKERS]).
 * `PlayerHouse` places these markers instead of Jagex's.
 *
 * One [CacheTransaction] over both production caches (servers stopped). Re-running writes the same bytes (NO_OP).
 * Usage: `./gradlew :game:runPohMinimapIconTool --args="plan|apply"`
 */
object PohMinimapIconTool {
    val TARGETS = OsrsItemImportTool.TARGETS

    private const val CONFIG_INDEX = 2
    private const val MAP_ELEMENT_GROUP = 36
    private const val LOC_INDEX = 16
    private const val LOC_OP_MAP_ELEMENT = 107

    /** Jagex element -> its fixed copy (the first ids after the cache's last element, 1112). */
    val FIXED_ELEMENTS = linkedMapOf(575 to 1113, 612 to 1114, 621 to 1115)

    /** Jagex marker loc -> the house's marker loc (the first ids after the OSRS "poh-house" locs, 62927). */
    val MARKERS = linkedMapOf(2752 to 62928, 7389 to 62929, 15531 to 62930)

    /** Jagex's element stream with its randomise flag (`08 01`) set to fixed (`08 00`); exactly one such pair must exist. */
    fun fixedElement(bytes: ByteArray): ByteArray {
        val at = (0 until bytes.size - 1).filter { (bytes[it].toInt() and 0xFF) == 8 && bytes[it + 1].toInt() == 1 }
        check(at.size == 1) { "expected one randomise pair 08 01, found ${at.size}" }
        return bytes.copyOf().also { it[at.single() + 1] = 0 }
    }

    /** The marker loc stream with its map element short (after opcode 107) replaced; exactly one must exist. */
    fun withMapElement(
        bytes: ByteArray,
        from: Int,
        to: Int,
    ): ByteArray {
        val at =
            (0 until bytes.size - 2).filter {
                (bytes[it].toInt() and 0xFF) == LOC_OP_MAP_ELEMENT &&
                    (((bytes[it + 1].toInt() and 0xFF) shl 8) or (bytes[it + 2].toInt() and 0xFF)) == from
            }
        check(at.size == 1) { "expected one map element $from after opcode $LOC_OP_MAP_ELEMENT, found ${at.size}" }
        return bytes.copyOf().also {
            it[at.single() + 1] = (to ushr 8).toByte()
            it[at.single() + 2] = (to and 0xFF).toByte()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode in setOf("plan", "apply")) { "Usage: plan|apply" }
        val library = CacheLibrary(TARGETS[0])
        val mutations =
            try {
                val elementOf = mapOf(2752 to 575, 7389 to 612, 15531 to 621)
                val elements =
                    FIXED_ELEMENTS.map { (jagex, copy) ->
                        val current = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, jagex) ?: error("element $jagex missing")
                        val existing = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, copy)
                        val bytes = fixedElement(current)
                        check(existing == null || existing.contentEquals(bytes)) { "element $copy already holds something else; refusing" }
                        println("ELEMENT $jagex -> $copy randomise off (${current.size} bytes)")
                        CacheMutation(CONFIG_INDEX, MAP_ELEMENT_GROUP, copy, bytes, "poh fixed map element $jagex -> $copy", existing?.let(CacheItemProbeTool::sha1))
                    }
                val locs =
                    MARKERS.map { (jagex, copy) ->
                        val current = library.data(LOC_INDEX, jagex ushr 8, jagex and 0xFF) ?: error("loc $jagex missing")
                        val existing = library.data(LOC_INDEX, copy ushr 8, copy and 0xFF)
                        val element = elementOf.getValue(jagex)
                        val bytes = withMapElement(current, element, FIXED_ELEMENTS.getValue(element))
                        check(existing == null || existing.contentEquals(bytes)) { "loc $copy already holds something else; refusing" }
                        check(Rev667LocType.decode(copy, bytes).allModels == Rev667LocType.decode(jagex, current).allModels) { "loc $copy model mismatch" }
                        println("MARKER $jagex -> $copy element $element -> ${FIXED_ELEMENTS.getValue(element)}")
                        CacheMutation(LOC_INDEX, copy ushr 8, copy and 0xFF, bytes, "poh marker loc $jagex -> $copy", existing?.let(CacheItemProbeTool::sha1))
                    }
                elements + locs
            } finally {
                library.close()
            }

        val transaction = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        plan.forEach { println("PREFLIGHT $it") }
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${transaction.id} mutations=${mutations.size} (nothing written)")
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
