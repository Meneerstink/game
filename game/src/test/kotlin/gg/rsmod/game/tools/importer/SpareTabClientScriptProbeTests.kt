package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-010 A4 (owner live 2026-09-13: Follower Details tab icon still disappears / flickers after clicks).
 *
 * Read-only census of the production cache: every clientscript that references the spare tab's
 * components (fixed 548:99 button / 548:107 icon, resizable 746:47 / 746:31) as a component hash, or
 * that reads/writes VARC 823 (clientscript 1766's "Production" gate). The printed instruction windows
 * are the evidence for which client path still hides the icon after clientscript 1766 was patched.
 */
class SpareTabClientScriptProbeTests {
    private val components = mapOf(
        (548 shl 16) or 99 to "548:99",
        (548 shl 16) or 107 to "548:107",
        (746 shl 16) or 47 to "746:47",
        (746 shl 16) or 31 to "746:31",
    )

    @Test
    fun `census of clientscripts touching the spare tab or varc 823`() {
        val library = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        val report = StringBuilder()
        var hits = 0
        try {
            val index = library.index(ProductionTabClientScriptPatchTool.CLIENTSCRIPT_INDEX)
            index.archiveIds().sorted().forEach { id ->
                val data = library.data(ProductionTabClientScriptPatchTool.CLIENTSCRIPT_INDEX, id, 0) ?: return@forEach
                val code = runCatching { ProductionTabClientScriptPatchTool.decode(data) }.getOrNull() ?: return@forEach
                val marks = code.indices.filter { i ->
                    val ins = code[i]
                    ins.intOperand in components.keys || (ins.intOperand == 823 && ins.opcode in setOf(42, 43))
                }
                if (marks.isEmpty()) return@forEach
                hits++
                report.append("script $id (${code.size} ops)\n")
                marks.forEach { i ->
                    val label = components[code[i].intOperand] ?: "varc823 op${code[i].opcode}"
                    val window = (maxOf(0, i - 3)..minOf(code.size - 1, i + 4)).joinToString(" | ") { j ->
                        val c = code[j]
                        "[$j]${c.opcode}:${c.intOperand ?: c.stringOperand ?: ""}"
                    }
                    report.append("  @$i $label :: $window\n")
                }
            }
        } finally {
            library.close()
        }
        // Second pass: full listings of the short/tab-stone scripts and who calls them (GOSUB = opcode 40).
        val focus = setOf(1309, 1312, 1387, 1766, 3215)
        val library2 = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        try {
            val index = library2.index(ProductionTabClientScriptPatchTool.CLIENTSCRIPT_INDEX)
            index.archiveIds().sorted().forEach { id ->
                val data = library2.data(ProductionTabClientScriptPatchTool.CLIENTSCRIPT_INDEX, id, 0) ?: return@forEach
                val code = runCatching { ProductionTabClientScriptPatchTool.decode(data) }.getOrNull() ?: return@forEach
                code.forEachIndexed { i, c -> if (c.opcode == 40 && c.intOperand in focus) report.append("GOSUB ${c.intOperand} from script $id @$i\n") }
                if (id == 3215 || id == 1309 || id == 1312) {
                    val range = if (id == 3215) code.indices else (95..130)
                    report.append("LISTING $id: " + range.filter { it < code.size }.joinToString(" | ") { j -> "[$j]${code[j].opcode}:${code[j].intOperand ?: code[j].stringOperand ?: ""}" } + "\n")
                }
            }
        } finally {
            library2.close()
        }
        // Third pass: which gameframe component hooks run the tab scripts (InterfaceHookProbeTool layout dump,
        // filtered to lines naming 3215/1765/1767/1766).
        val cachePath = Paths.get("..", "data", "cache").toFile().toString()
        listOf("548", "746").forEach { pane ->
            val buffer = java.io.ByteArrayOutputStream()
            val original = System.out
            System.setOut(java.io.PrintStream(buffer))
            try {
                runCatching { InterfaceHookProbeTool.main(arrayOf(cachePath, "layout", pane)) }
            } finally {
                System.setOut(original)
            }
            buffer.toString().lines().filter { line -> listOf("3215", "1765", "1767", "1766").any { line.contains(it) } }
                .forEach { report.append("HOOK $pane :: ${it.trim().take(300)}\n") }
        }
        println("SpareTabClientScriptProbeTests hits=$hits\n$report")
        assertTrue(hits > 0, "clientscript 1766 must at least reference the spare tab")
    }
}
