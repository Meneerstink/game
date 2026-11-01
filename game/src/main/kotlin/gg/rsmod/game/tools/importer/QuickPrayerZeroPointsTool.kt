package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N483 (found by Claude 2026-10-31 at the N463 test): with 0 Prayer points a click on a quick-prayer box did nothing in all three
 * books. OSRS lets you set up quick prayers at any time; only switching a prayer ON at 0 points is refused ("You need to recharge
 * your Prayer at an altar." + synth 2673, OSRS `[clientscript,prayer_op]`).
 *
 * The 667 cache uses one check for both: clientscript 2295 ("may this prayer be chosen") calls 5256 (= varbit 9816, the Prayer
 * points) and refuses at 0 (GOSUB 5256, BRANCH_IF_FALSE -> sound 2673 + return 0). It is called by
 *   2289 - the prayer's own right-click ops (Activate / Deactivate)   -> keeps the points check (OSRS prayer_op)
 *   2290 - a click in quick-prayer selection mode                     -> no points check
 *   5    - drawing the selection grid (greys out what cannot be chosen) -> no points check
 * So [NEW_SCRIPT] = 2295 with that one branch landing on the next instruction (the level and unlock checks stay), and 2290 and 5
 * call it instead of 2295. The server's quick-prayer selection never checked points (Prayers.selectQuickPrayer, AncientCurses,
 * RuinousPowers) and switching the set on at 0 points is still refused there.
 *
 * Usage: `./gradlew :game:runQuickPrayerZeroPointsTool --args="[--apply]"` with the test server and file server stopped.
 */
object QuickPrayerZeroPointsTool {
    const val SCRIPT_INDEX = 12
    const val CAN_CHOOSE = 2295
    const val POINTS = 5256
    const val NEW_SCRIPT = 5401
    val QUICK_CALLERS = listOf(2290, 5)

    private const val GOSUB = 40
    private const val BRANCH_IF_FALSE = 87

    /** 2295 without the points refusal: the branch after `GOSUB 5256` lands on the next instruction (the value is still popped). */
    fun withoutPointsCheck(data: ByteArray): ByteArray {
        val script = ClientScriptEditor.decode(data)
        val at = script.find(GOSUB to POINTS, BRANCH_IF_FALSE to null)
        check(at.size == 1) { "clientscript $CAN_CHOOSE: expected one GOSUB $POINTS + BRANCH_IF_FALSE, found ${at.size}" }
        val branch = script.code[at.single() + 1]
        branch.target = script.code[at.single() + 2]
        return script.encode()
    }

    /** [data] with its single `GOSUB 2295` calling [NEW_SCRIPT]; unchanged when it already does. */
    fun callNew(
        id: Int,
        data: ByteArray,
    ): ByteArray {
        val script = ClientScriptEditor.decode(data)
        if (script.find(GOSUB to NEW_SCRIPT).size == 1 && script.find(GOSUB to CAN_CHOOSE).isEmpty()) return data
        val at = script.find(GOSUB to CAN_CHOOSE)
        check(at.size == 1) { "clientscript $id: expected one GOSUB $CAN_CHOOSE, found ${at.size}" }
        script.code[at.single()].int = NEW_SCRIPT
        return script.encode()
    }

    /** [data] calling 2295 again instead of [NEW_SCRIPT] (undo). */
    fun callOld(
        id: Int,
        data: ByteArray,
    ): ByteArray {
        val script = ClientScriptEditor.decode(data)
        val at = script.find(GOSUB to NEW_SCRIPT)
        if (at.isEmpty()) return data
        script.code[at.single()].int = CAN_CHOOSE
        return script.encode()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        // --undo: 2290 and 5 call 2295 again (5401 stays, unused) - to compare the client with and without the patch.
        val undo = "--undo" in args
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = mutableListOf<CacheMutation>()
        try {
            val base = requireNotNull(library.data(SCRIPT_INDEX, CAN_CHOOSE, 0)) { "Missing clientscript $CAN_CHOOSE" }
            val wanted = withoutPointsCheck(base)
            val existing = library.data(SCRIPT_INDEX, NEW_SCRIPT, 0)
            when {
                undo -> println("UNDO: callers back to $CAN_CHOOSE")
                existing == null -> mutations += CacheMutation(SCRIPT_INDEX, NEW_SCRIPT, 0, wanted, "clientscript $NEW_SCRIPT: 2295 without the Prayer-points refusal (N483 quick prayers)")
                !existing.contentEquals(wanted) -> error("clientscript $NEW_SCRIPT already exists with other content - pick another id")
                else -> println("ALREADY clientscript $NEW_SCRIPT")
            }
            for (id in QUICK_CALLERS) {
                val current = requireNotNull(library.data(SCRIPT_INDEX, id, 0)) { "Missing clientscript $id" }
                val patched = if (undo) callOld(id, current) else callNew(id, current)
                if (patched.contentEquals(current)) {
                    println("ALREADY clientscript $id")
                    continue
                }
                mutations += CacheMutation(SCRIPT_INDEX, id, 0, patched, "clientscript $id: quick-prayer choice without the points check (calls $NEW_SCRIPT)", expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING TO DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        plan.forEach { println("PREFLIGHT $it") }
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN transaction=${transaction.id}: pass --apply to write")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
