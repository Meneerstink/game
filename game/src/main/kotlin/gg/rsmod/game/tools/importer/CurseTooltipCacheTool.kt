package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Owner 2026-09-29 (Leech Ranged / Leech Magic now carry Rigour's / Augury's offence, AncientCurses.leechMultiplier): the
 * prayer-tab hover text of those two curses must say so. The text is a string param of config structs 899 (Leech Ranged)
 * and 900 (Leech Magic) in config group 26; only that one sentence is replaced, byte for byte, every other param stays.
 * One [CacheTransaction] over both production caches (preflight, journal, verify); running it again is a no-op.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.CurseTooltipCacheTool plan|apply`
 */
object CurseTooltipCacheTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val CONFIG_INDEX = 2
    private const val STRUCT_GROUP = 26

    /** [olds]: every earlier wording of the sentence (the 667 original first); the one present is replaced by [new]. */
    class Tooltip(val struct: Int, val olds: List<String>, val new: String)

    // Owner 2026-09-29, second round: Leech Ranged damage +23 % -> +18 % and Leech Magic damage +4 % -> 0 % ("one hit lower",
    // accuracy unchanged); Leech Defence a flat +15 %.
    val TOOLTIPS =
        listOf(
            Tooltip(
                899,
                listOf(
                    "Boosts Ranged by 5%, increasing to 10% over time, while draining enemy Ranged by 10%, increasing to 25% over time.",
                    "Boosts Ranged accuracy by 20% and Ranged damage by 23%, while draining enemy Ranged by 10%, increasing to 25% over time.",
                ),
                "Boosts Ranged accuracy by 20% and Ranged damage by 18%, while draining enemy Ranged by 10%, increasing to 25% over time.",
            ),
            Tooltip(
                900,
                listOf(
                    "Boosts Magic by 5%, increasing to 10% over time, while draining enemy Magic by 10%, increasing to 25% over time.",
                    "Boosts Magic accuracy by 25% and Magic damage by 4%, while draining enemy Magic by 10%, increasing to 25% over time.",
                ),
                "Boosts Magic accuracy by 25%, while draining enemy Magic by 10%, increasing to 25% over time.",
            ),
            Tooltip(
                901,
                listOf("Boosts Defence by 5%, increasing to 10% over time, while draining enemy Defence by 10%, increasing to 25% over time."),
                "Boosts Defence by 15%, while draining enemy Defence by 10%, increasing to 25% over time.",
            ),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val mutations = plan(GAME_CACHE)
        if (mutations.isEmpty()) {
            println("CURSE_TOOLTIPS already applied")
            return
        }
        val transaction = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = transaction.preflight()
        println(preflight.joinToString("\n"))
        val errors = transaction.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) return
        val result = transaction.apply(preflight)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("CURSE_TOOLTIPS transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    /** The mutations still needed against [cache]; empty once both texts are in place. */
    fun plan(cache: String): List<CacheMutation> {
        val library = CacheLibrary(cache)
        try {
            return TOOLTIPS.mapNotNull { tooltip ->
                val current = requireNotNull(library.data(CONFIG_INDEX, STRUCT_GROUP, tooltip.struct)) { "struct ${tooltip.struct} missing" }
                val patched = replaceText(current, tooltip.olds, tooltip.new) ?: return@mapNotNull null
                CacheMutation(
                    CONFIG_INDEX, STRUCT_GROUP, tooltip.struct, patched, "struct ${tooltip.struct}: curse tooltip",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                )
            }
        } finally {
            library.close()
        }
    }

    /** [data] with whichever of [olds] it holds replaced by [new] (exactly one occurrence); null when [new] is already there. */
    fun replaceText(
        data: ByteArray,
        olds: List<String>,
        new: String,
    ): ByteArray? {
        val text = String(data, Charsets.ISO_8859_1)
        if (text.contains(new)) return null
        val old = olds.firstOrNull { text.contains(it) } ?: error("none of the known texts found: $olds")
        val at = text.indexOf(old)
        check(text.indexOf(old, at + 1) < 0) { "expected exactly one '$old'" }
        return (text.substring(0, at) + new + text.substring(at + old.length)).toByteArray(Charsets.ISO_8859_1)
    }
}