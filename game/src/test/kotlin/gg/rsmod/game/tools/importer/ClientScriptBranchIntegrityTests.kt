package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-012 B6 (owner live: "Prayer and HP sometimes bug to 178"). `ClientScriptUnitMigrationTool` removed x10 pairs
 * from the orb/stat scripts 2915/2916/5255 and retargeted only branch opcodes 6..10. The client's
 * `ClientScriptOpCode` also branches on 31/32 (<=, >=), 68..73 (long compares), 86/87 (if true/false) and 51
 * (switch tables): a removed instruction ahead of such a branch shifts its target, so an orb shows a wrong stack
 * value only on the path that takes that branch.
 *
 * Roster: every clientscript that reads the lifepoint varbit 7198 / varp 1240, the prayer varbit 9816, and the three
 * migrated scripts. Read-only, production game cache.
 */
class ClientScriptBranchIntegrityTests {
    private val pushVarp = 1
    private val pushVarbit = 25
    private val pushInt = 0
    private val multiply = 4002
    private val divide = 4003
    private val retargeted = setOf(6, 7, 8, 9, 10)
    private val notRetargeted = setOf(31, 32, 68, 69, 70, 71, 72, 73, 86, 87)
    private val switch = 51
    private val migrated = setOf(2915, 2916, 5255)

    /**
     * Pristine openrs2 #1473 (revision 667) instructions, decoded with [ProductionTabClientScriptPatchTool.decode],
     * with only the `PUSH_CONSTANT_INT 10, MULTIPLY` pairs removed and relative branches recomputed:
     * pristine 2916 = op42(1533) op86(1) op6(7) op0(3) op3305(0) op0(0) op4017(0) op0(10) op4002(0) op21(0) op0(3)
     * op3306(0) op0(1) op4017(0) op0(10) op4002(0) op21(0) op0(0) op21(0); op86(1) still lands on op0(3) (nothing
     * removed in between), op6(7) targeted pristine #10 = production #8, so its operand becomes 5.
     */
    private val expectedMigrated =
        mapOf(
            2915 to listOf("op0(3)", "op3305(0)", "op0(0)", "op4017(0)", "op21(0)", "op0(0)", "op21(0)"),
            2916 to listOf(
                "op42(1533)", "op86(1)", "op6(5)", "op0(3)", "op3305(0)", "op0(0)", "op4017(0)", "op21(0)",
                "op0(3)", "op3306(0)", "op0(1)", "op4017(0)", "op21(0)", "op0(0)", "op21(0)",
            ),
            5255 to listOf("op0(5)", "op3306(0)", "op0(1)", "op4017(0)", "op21(0)", "op0(0)", "op21(0)"),
        )

    @Test
    fun `orb and stat clientscripts keep valid branches and no x10 unit math`() {
        val library = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        val report = mutableListOf<String>()
        val offenders = mutableListOf<String>()
        try {
            val index = library.index(ProductionTabClientScriptPatchTool.CLIENTSCRIPT_INDEX)
            index.archiveIds().sorted().forEach { id ->
                val data = library.data(ProductionTabClientScriptPatchTool.CLIENTSCRIPT_INDEX, id, 0) ?: return@forEach
                val code = runCatching { ProductionTabClientScriptPatchTool.decode(data) }.getOrNull() ?: return@forEach
                val reads = buildList {
                    if (code.any { it.opcode == pushVarbit && it.intOperand == 7198 }) add("varbit7198")
                    if (code.any { it.opcode == pushVarp && it.intOperand == 1240 }) add("varp1240")
                    if (code.any { it.opcode == pushVarbit && it.intOperand == 9816 }) add("varbit9816")
                }
                if (reads.isEmpty() && id !in migrated) return@forEach

                val outOfRange = code.withIndex().filter { (i, ins) ->
                    (ins.opcode in retargeted || ins.opcode in notRetargeted) &&
                        (i + 1 + (ins.intOperand ?: 0)) !in 0..code.size
                }.map { (i, ins) -> "#$i op${ins.opcode}(${ins.intOperand})" }
                val unretargetable = code.withIndex().filter { (_, ins) -> ins.opcode in notRetargeted || ins.opcode == switch }
                    .map { (i, ins) -> "#$i op${ins.opcode}(${ins.intOperand})" }
                val unitPairs = code.zipWithNext().count { (a, b) -> a.opcode == pushInt && a.intOperand == 10 && (b.opcode == multiply || b.opcode == divide) }

                report += "script $id reads=$reads size=${code.size} x10pairs=$unitPairs outOfRange=$outOfRange " +
                    "nonRetargetedBranches=$unretargetable"
                if (outOfRange.isNotEmpty()) offenders += "script $id branch target outside the script: $outOfRange"
                expectedMigrated[id]?.let { expected ->
                    val actual = code.map { "op${it.opcode}(${it.intOperand ?: it.stringOperand})" }
                    if (actual != expected) offenders += "script $id differs from pristine-minus-x10:\n  actual   $actual\n  expected $expected"
                }
                if (unitPairs > 0 && reads.isNotEmpty()) offenders += "script $id $reads still has $unitPairs x10 unit pair(s)"
            }
        } finally {
            library.close()
        }
        println("ClientScriptBranchIntegrityTests:\n" + report.joinToString("\n"))
        assertTrue(report.any { "varbit9816" in it }, "no clientscript reads the prayer varbit - wrong opcode or cache path?")
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }
}
