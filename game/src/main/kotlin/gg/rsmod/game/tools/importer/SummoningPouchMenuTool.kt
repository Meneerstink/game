package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N453 (Claude 10-30, owner: RS = left-click on a pouch summons): every summoning pouch had "Summon" as its fourth inventory
 * option (item opcode 38), so the client put its built-in "Use" first and a left-click used the pouch. Research and the id list:
 * ChatGPT-B, `docs/bugs/N453-pouch-menu.md` (146 unnoted pouches; notes, Beacon ring 11014, Calling beads 15354, the empty pouch
 * 12155 and scrolls stay as they are).
 *
 * Per pouch: opcode 35 (first option) = "Summon", opcode 38 removed; every other byte unchanged. The server binds pouches by
 * option NAME (familiar.plugin.kts), so it follows the new slot by itself. Applied transactionally to both caches; each item must
 * still have 38 = "Summon" and no 35 (or already be done). `--apply` writes, without it only the preflight.
 */
object SummoningPouchMenuTool {
    const val FIRST_OPTION = 35
    const val FOURTH_OPTION = 38
    const val SUMMON = "Summon"

    val POUCHES =
        listOf(
            12007, 12009, 12011, 12013, 12015, 12017, 12019, 12021, 12023, 12025, 12027, 12029, 12031, 12033,
            12035, 12037, 12039, 12041, 12043, 12045, 12047, 12049, 12051, 12053, 12055, 12057, 12059, 12061,
            12063, 12065, 12067, 12069, 12071, 12073, 12075, 12077, 12079, 12081, 12083, 12085, 12087, 12089,
            12091, 12093, 12095, 12097, 12099, 12101, 12103, 12105, 12107, 12123, 12526, 12531, 12776, 12778,
            12780, 12782, 12784, 12786, 12788, 12790, 12792, 12794, 12796, 12798, 12800, 12802, 12804, 12806,
            12808, 12810, 12812, 12814, 12816, 12818, 12820, 12822, 14422, 14424, 14426, 14428, 14430, 14623,
            17935, 17936, 17937, 17938, 17939, 17940, 17941, 17942, 17943, 17944, 17945, 17946, 17947, 17948,
            17949, 17950, 17951, 17952, 17953, 17954, 17955, 17956, 17957, 17958, 17959, 17960, 17961, 17962,
            17963, 17964, 17965, 17966, 17967, 17968, 17969, 17970, 17971, 17972, 17973, 17974, 17975, 17976,
            17977, 17978, 17979, 17980, 17981, 17982, 17983, 17984, 17985, 17986, 17987, 17988, 17989, 17990,
            17991, 17992, 17993, 17994, 19622, 21444,
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        check(POUCHES.size == 146 && POUCHES.toSet().size == 146) { "pouch list must hold the 146 researched ids" }
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val mutations =
            try {
                POUCHES.mapNotNull { id ->
                    val current = requireNotNull(CacheItemProbeTool.itemData(library, id)) { "item $id has no data" }
                    val patched = patch(id, current)
                    if (patched === current) {
                        null
                    } else {
                        CacheMutation.item(id, patched, "item $id ${ItemDefCodec.readName(current)}: Summon first option (N453)", CacheItemProbeTool.sha1(current))
                    }
                }
            } finally {
                library.close()
            }
        println("CHANGES ${mutations.size} of ${POUCHES.size} pouches")
        if (mutations.isEmpty()) return
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write ${mutations.size} pouch definitions")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }

    /** [current] with "Summon" moved from the fourth to the first inventory option; the same array when it already is. */
    fun patch(
        id: Int,
        current: ByteArray,
    ): ByteArray {
        val ops = ItemDefCodec.describeOpcodes(current)
        val first = "$FIRST_OPTION=\"$SUMMON\""
        val fourth = "$FOURTH_OPTION=\"$SUMMON\""
        if (first in ops && ops.none { it.startsWith("$FOURTH_OPTION=") }) return current
        check(fourth in ops && ops.none { it.startsWith("$FIRST_OPTION=") }) { "item $id: expected only 38=Summon, found $ops" }
        val edited = ItemDefCodec.cloneWithOverrides(current, stringOverrides = mapOf(FIRST_OPTION to SUMMON), removedOpcodes = setOf(FOURTH_OPTION))
        val after = ItemDefCodec.describeOpcodes(edited)
        check(first in after && after.none { it.startsWith("$FOURTH_OPTION=") }) { "item $id: patch failed: $after" }
        check(after.filterNot { it.startsWith("$FIRST_OPTION=") }.sorted() == ops.filterNot { it.startsWith("$FOURTH_OPTION=") }.sorted()) {
            "item $id: other opcodes changed"
        }
        return edited
    }
}
