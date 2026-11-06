package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * N435 (owner 2026-10-30: "kun je de effecten per trinket andere kleur doen", choice "oplopend zeldzaam": 6 grey, 9 green,
 * 12 blue, 18 purple, 30 gold): the burden trinket's bind glow, spotanim 1517 (green, model 31639), gets four recoloured copies.
 * A copy is 1517's own definition plus a recolour (spotanim opcode 40): every colour of the model keeps its lightness and takes
 * the tier's hue and saturation (grey: saturation 0). The green tier keeps 1517. Colours are the client's 16-bit HSL.
 *
 * Usage: `./gradlew :game:runTrinketGlowTool --args="[--apply]"` (plan by default; CacheTransaction, both caches). Prints the new
 * spotanim ids in tier order (BurdenTrinkets.Tier.glow).
 */
object TrinketGlowTool {
    private const val INDEX_SPOTANIM = 21
    private const val MODEL_INDEX = 7
    const val GLOW = 1517

    /** Name, hue (0-63) and saturation (0-7) of each new colour, in tier order 6 / 12 / 18 / 30. */
    val COLOURS = listOf(Triple("grey", 0, 0), Triple("blue", 42, 6), Triple("purple", 50, 6), Triple("gold", 8, 7))

    fun hue(hsl: Int) = (hsl ushr 10) and 0x3F

    fun saturation(hsl: Int) = (hsl ushr 7) and 0x7

    fun lightness(hsl: Int) = hsl and 0x7F

    fun tint(
        hsl: Int,
        hue: Int,
        saturation: Int,
    ) = (hue shl 10) or (saturation shl 7) or lightness(hsl)

    /** [def] (a spotanim definition ending in its 0 terminator) with a recolour [pairs] added before the terminator. */
    fun withRecolour(
        def: ByteArray,
        pairs: List<Pair<Int, Int>>,
    ): ByteArray {
        check(def.last() == 0.toByte()) { "spotanim definition does not end in 0" }
        return ByteArrayOutputStream().use { out ->
            out.write(def, 0, def.size - 1)
            out.write(40)
            out.write(pairs.size)
            pairs.forEach { (from, to) ->
                out.write(from ushr 8); out.write(from and 0xFF)
                out.write(to ushr 8); out.write(to and 0xFF)
            }
            out.write(0)
            out.toByteArray()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val mutations = ArrayList<CacheMutation>()
        val ids = ArrayList<Int>()
        val libraries = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        try {
            val library = libraries[0]
            val glow = library.data(INDEX_SPOTANIM, GLOW ushr 8, GLOW and 0xFF) ?: error("spotanim $GLOW missing")
            println("SPOTANIM $GLOW bytes=${glow.joinToString("") { "%02x".format(it) }}")
            check(glow[0] == 1.toByte()) { "spotanim $GLOW does not start with its model" }
            val model = ((glow[1].toInt() and 0xFF) shl 8) or (glow[2].toInt() and 0xFF)
            val mesh = Rev667ModelDecoder.decode(ModelArchive.read(library, model) ?: error("model $model missing"), geometryOnly = true)
            val colours = mesh.faceColour.map { it.toInt() and 0xFFFF }.distinct().sorted()
            println("MODEL $model colours=${colours.joinToString { "$it(h${hue(it)} s${saturation(it)} l${lightness(it)})" }}")
            check(colours.size in 1..255) { "model $model has ${colours.size} colours" }
            // The next free spotanim id, the same in both caches (the 667 client sizes the list from the last group).
            val next =
                libraries.map { lib ->
                    val group = lib.index(INDEX_SPOTANIM).archiveIds().maxOrNull() ?: 0
                    (group shl 8) + (lib.index(INDEX_SPOTANIM).archive(group)?.fileIds()?.maxOrNull() ?: -1) + 1
                }.distinct()
            check(next.size == 1) { "the two caches disagree on the next spotanim id: $next" }
            COLOURS.forEachIndexed { i, (name, h, s) ->
                val id = next[0] + i
                val pairs = colours.map { it to tint(it, h, s) }
                val def = withRecolour(glow, pairs)
                ids += id
                println("PLAN spotanim $id = $GLOW in $name (${pairs.size} colours)")
                mutations += CacheMutation(INDEX_SPOTANIM, id ushr 8, id and 0xFF, def, "N435 burden trinket glow $name")
            }
        } finally {
            libraries.forEach { it.close() }
        }
        println("TIER_GLOWS 6=${ids[0]} 9=$GLOW 12=${ids[1]} 18=${ids[2]} 30=${ids[3]}")
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN transaction=${transaction.id} mutations=${mutations.size}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
