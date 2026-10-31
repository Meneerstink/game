package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * Rewrites the rune arguments of spellbook spells (night run 2026-09-19, owner: "we imported wrath runes and all combination runes
 * but the normal spellbook doesn't show them"; "make our home teleport coloured").
 *
 * Every 667 spell component carries its requirements as the last eight int arguments of its onLoad hook (CS2 6: rune id / amount
 * pairs; CS2 21 greys the icon and CS2 19 counts the runes from exactly these). The server's own spell data differs in two places:
 *  - the four Surge spells use the OSRS runes (air + wrath, OSRS Wiki), the cache still listed 667 blood + death;
 *  - 2026-09-19 every self-teleport was made rune-free (the runes taken out here); N72 (2026-10-31) puts them back
 *    ([RESTORED_TELEPORTS]): teleports cost runes again.
 * The rewrite is length-preserving (int for int) and addressed by value: the old eight-int sequence must occur exactly once in the
 * component, otherwise nothing is written. Non-rune ingredients (Ape Atoll's banana) stay.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.SpellRuneArgsTool [--apply]`
 */
object SpellRuneArgsTool {
    const val INDEX_INTERFACES = 3
    const val WRATH_RUNE = 23743

    /** Rune item ids (RuneFreeTeleportRequirements' rune set, 667 ids) - removed from rune-free teleports. */
    val RUNES = setOf(554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 4694, 4695, 4696, 4697, 4698, 4699, 9075, 21773)

    /** Surge spells: OSRS runes, in the server's SpellbookData order. */
    val SURGES: Map<Pair<Int, Int>, List<Pair<Int, Int>>> =
        mapOf(
            (192 to 84) to listOf(556 to 7, WRATH_RUNE to 1),
            (192 to 87) to listOf(555 to 10, 556 to 7, WRATH_RUNE to 1),
            (192 to 89) to listOf(557 to 10, 556 to 7, WRATH_RUNE to 1),
            (192 to 91) to listOf(554 to 10, 556 to 7, WRATH_RUNE to 1),
        )

    /**
     * N72 (owner 2026-10-31 18:4x "alle teleports moeten nu gewoon runes kosten"): the runes the 2026-09-19 rewrite took out of every
     * self teleport go back in, the server's SpellbookData runes per (book, spell name) - the client greys a teleport without them and
     * the tooltip lists them again. The three Home Teleports stay free (owner 2026-10-31 20:0x).
     */
    val RESTORED_TELEPORTS: Map<Pair<Int, String>, List<Pair<Int, Int>>> =
        mapOf(
            (192 to "Mobilising Armies Teleport") to listOf(555 to 1, 556 to 3, 563 to 1),
            (192 to "Varrock Teleport") to listOf(554 to 1, 556 to 3, 563 to 1),
            (192 to "Lumbridge Teleport") to listOf(557 to 1, 556 to 3, 563 to 1),
            (192 to "Falador Teleport") to listOf(555 to 1, 556 to 3, 563 to 1),
            (192 to "Camelot Teleport") to listOf(556 to 5, 563 to 1),
            (192 to "Ardougne Teleport") to listOf(555 to 2, 563 to 2),
            (192 to "Watchtower Teleport") to listOf(557 to 2, 563 to 2),
            (192 to "Trollheim Teleport") to listOf(554 to 2, 563 to 2),
            (192 to "Teleport to Ape Atoll") to listOf(554 to 2, 555 to 2, 563 to 2, 1963 to 1),
            (192 to "Teleport to House") to listOf(563 to 1, 556 to 1, 557 to 1),
            (193 to "Paddewwa Teleport") to listOf(563 to 2, 554 to 1, 556 to 1),
            (193 to "Senntisten Teleport") to listOf(563 to 2, 566 to 1),
            (193 to "Kharyrll Teleport") to listOf(563 to 2, 565 to 1),
            (193 to "Lassar Teleport") to listOf(563 to 2, 555 to 4),
            (193 to "Dareeyak Teleport") to listOf(563 to 2, 554 to 3, 556 to 2),
            (193 to "Carrallangar Teleport") to listOf(563 to 2, 566 to 2),
            (193 to "Annakarl Teleport") to listOf(563 to 2, 565 to 2),
            (193 to "Ghorrock Teleport") to listOf(563 to 2, 555 to 8),
            (430 to "Barbarian Teleport") to listOf(9075 to 2, 563 to 2, 554 to 3),
            (430 to "Fishing Guild Teleport") to listOf(9075 to 3, 563 to 3, 555 to 8),
            (430 to "Khazard Teleport") to listOf(9075 to 2, 563 to 2, 555 to 4),
            (430 to "Moonclan Teleport") to listOf(9075 to 2, 563 to 1, 557 to 2),
            (430 to "Catherby Teleport") to listOf(9075 to 3, 563 to 3, 555 to 10),
            (430 to "Waterbirth Teleport") to listOf(9075 to 2, 563 to 1, 555 to 1),
            (430 to "Ice Plateau Teleport") to listOf(9075 to 3, 563 to 3, 555 to 8),
            (430 to "Ourania Teleport") to listOf(9075 to 2, 563 to 1, 557 to 6),
            (430 to "South Falador Teleport") to listOf(9075 to 2, 563 to 1, 556 to 2),
            (430 to "North Ardougne Teleport") to listOf(9075 to 2, 563 to 1, 555 to 5),
            (430 to "Trollheim Teleport") to listOf(9075 to 3, 563 to 3, 555 to 10),
        )

    /** The self teleports of 2026-09-19 (TeleportSpell + the POH Teleport to House), addressed by the spell name in their onLoad args. */
    val LUNAR_FREE_TELEPORT_NAMES =
        setOf(
            "Barbarian Teleport", "Fishing Guild Teleport", "Khazard Teleport", "Moonclan Teleport", "Catherby Teleport",
            "Waterbirth Teleport", "Ice Plateau Teleport", "Ourania Teleport", "South Falador Teleport", "North Ardougne Teleport",
            "Trollheim Teleport",
        )

    val STANDARD_FREE_TELEPORT_NAMES =
        setOf(
            "Mobilising Armies Teleport", "Varrock Teleport", "Lumbridge Teleport", "Falador Teleport", "Teleport to House",
            "Camelot Teleport", "Ardougne Teleport", "Watchtower Teleport", "Trollheim Teleport", "Teleport to Ape Atoll",
        )

    val ANCIENT_FREE_TELEPORT_NAMES =
        setOf(
            "Paddewwa Teleport", "Senntisten Teleport", "Kharyrll Teleport", "Lassar Teleport", "Dareeyak Teleport",
            "Carrallangar Teleport", "Annakarl Teleport", "Ghorrock Teleport",
        )

    private fun encodeInts(values: List<Int>): ByteArray {
        val out = ByteArrayOutputStream()
        values.forEach { v ->
            out.write(0) // arg type 0 = int
            out.write(v ushr 24 and 0xFF)
            out.write(v ushr 16 and 0xFF)
            out.write(v ushr 8 and 0xFF)
            out.write(v and 0xFF)
        }
        return out.toByteArray()
    }

    fun padded(pairs: List<Pair<Int, Int>>): List<Int> {
        require(pairs.size <= 4) { "at most four requirements" }
        return (pairs + List(4 - pairs.size) { -1 to 0 }).flatMap { listOf(it.first, it.second) }
    }

    fun indexOf(
        haystack: ByteArray,
        needle: ByteArray,
        from: Int = 0,
    ): Int {
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** Replaces the old eight rune ints with [newInts]; null when the old sequence is not found exactly once. */
    fun rewrite(
        bytes: ByteArray,
        oldInts: List<Int>,
        newInts: List<Int>,
    ): ByteArray? {
        val old = encodeInts(oldInts)
        val first = indexOf(bytes, old)
        if (first < 0 || indexOf(bytes, old, first + 1) >= 0) return null
        val copy = bytes.copyOf()
        encodeInts(newInts).copyInto(copy, first)
        return copy
    }

    /** The spell name and eight rune ints of a component's CS2-6 onLoad hook, decoded by value from its raw bytes. */
    data class SpellArgs(val name: String, val runeInts: List<Int>)

    fun spellArgs(
        library: CacheLibrary,
        interfaceId: Int,
        component: Int,
    ): SpellArgs? {
        val data = library.data(INDEX_INTERFACES, interfaceId, component) ?: return null
        val onLoad = runCatching { InterfaceHookProbeTool.componentHooks(data)["onLoad"] }.getOrNull() ?: return null
        if (onLoad.firstOrNull() != 6 || onLoad.size < 17) return null
        val runeInts = onLoad.takeLast(8).map { it as? Int ?: return null }
        return SpellArgs(onLoad[7] as? String ?: return null, runeInts)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val mutations = mutableListOf<CacheMutation>()
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        try {
            fun plan(
                interfaceId: Int,
                component: Int,
                newPairs: (List<Pair<Int, Int>>) -> List<Pair<Int, Int>>,
            ) {
                val spell = spellArgs(library, interfaceId, component) ?: error("$interfaceId:$component is not a CS2-6 spell")
                val oldPairs = spell.runeInts.chunked(2).map { it[0] to it[1] }.filter { it.first != -1 }
                val newInts = padded(newPairs(oldPairs))
                if (newInts == spell.runeInts) {
                    println("UNCHANGED $interfaceId:$component ${spell.name}")
                    return
                }
                val bytes = library.data(INDEX_INTERFACES, interfaceId, component) ?: error("$interfaceId:$component missing")
                val rewritten = rewrite(bytes, spell.runeInts, newInts) ?: error("$interfaceId:$component rune ints not found exactly once")
                println("PLAN $interfaceId:$component ${spell.name}: ${spell.runeInts} -> $newInts")
                mutations +=
                    CacheMutation(
                        INDEX_INTERFACES, interfaceId, component, rewritten, "spell runes $interfaceId:$component ${spell.name}",
                        // Replace only the exact bytes read here (both caches must hold them); anything else blocks as a CONFLICT.
                        expectedCurrentSha1 = CacheItemProbeTool.sha1(bytes),
                    )
            }
            SURGES.forEach { (key, pairs) -> plan(key.first, key.second) { pairs } }
            val names = mapOf(192 to STANDARD_FREE_TELEPORT_NAMES, 193 to ANCIENT_FREE_TELEPORT_NAMES, 430 to LUNAR_FREE_TELEPORT_NAMES)
            names.forEach { (interfaceId, wanted) ->
                val components = library.index(INDEX_INTERFACES).archive(interfaceId)?.fileIds()?.toList() ?: emptyList()
                val seen = mutableSetOf<String>()
                for (component in components) {
                    val spell = spellArgs(library, interfaceId, component) ?: continue
                    if (spell.name !in wanted) continue
                    seen += spell.name
                    plan(interfaceId, component) { RESTORED_TELEPORTS.getValue(interfaceId to spell.name) }
                }
                require(seen == wanted) { "interface $interfaceId: teleports not found: ${wanted - seen}" }
            }
        } finally {
            library.close()
        }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val preflight = transaction.preflight()
        val errors = transaction.blockingErrors(preflight)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write")
            return
        }
        val applied = transaction.apply(preflight)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) println("VERIFY_OK transaction=${transaction.id}") else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
