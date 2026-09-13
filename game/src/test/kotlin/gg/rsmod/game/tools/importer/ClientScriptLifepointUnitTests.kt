package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-010 A2/B1 client boundary: lifepoints are 1:1 on the server (varbit 7198 / varp 1240) and the
 * poisoned HP orb is driven by varp 102. No clientscript that reads those values may still carry a
 * legacy x10 multiplier pair (PUSH_CONSTANT_INT 10, MULTIPLY) - that is how scripts 2915/2916/5255
 * displayed HP ten times too high before transaction tx-20260912-192125. Read-only, production cache.
 */
class ClientScriptLifepointUnitTests {
    private val pushVarp = 1
    private val pushVarbit = 25
    private val pushInt = 0
    private val multiply = 4002

    @Test
    fun `no lifepoint or poison-orb clientscript keeps a x10 multiplier`() {
        val library = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        val readers = mutableListOf<String>()
        val offenders = mutableListOf<String>()
        var undecodable = 0
        try {
            val index = library.index(ProductionTabClientScriptPatchTool.CLIENTSCRIPT_INDEX)
            index.archiveIds().sorted().forEach { id ->
                val data = library.data(ProductionTabClientScriptPatchTool.CLIENTSCRIPT_INDEX, id, 0) ?: return@forEach
                val code = runCatching { ProductionTabClientScriptPatchTool.decode(data) }.getOrElse { undecodable++; return@forEach }
                val reads = buildList {
                    if (code.any { it.opcode == pushVarbit && it.intOperand == 7198 }) add("varbit7198")
                    if (code.any { it.opcode == pushVarp && it.intOperand == 1240 }) add("varp1240")
                    if (code.any { it.opcode == pushVarp && it.intOperand == 102 }) add("varp102(poison)")
                }
                if (reads.isEmpty()) return@forEach
                val pairs = code.zipWithNext().count { (a, b) -> a.opcode == pushInt && a.intOperand == 10 && b.opcode == multiply }
                readers += "script $id $reads x10pairs=$pairs"
                if (pairs > 0 && reads.any { it != "varp102(poison)" }) offenders += "script $id $reads x10pairs=$pairs"
            }
        } finally {
            library.close()
        }
        println("ClientScriptLifepointUnitTests readers (undecodable=$undecodable):\n" + readers.joinToString("\n"))
        assertTrue(readers.isNotEmpty(), "no clientscript reads the lifepoint varbit - wrong cache path?")
        assertTrue(offenders.isEmpty(), "x10 multiplier left in a lifepoint clientscript:\n" + offenders.joinToString("\n"))
    }
}
