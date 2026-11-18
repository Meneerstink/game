package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.util.Base64
import kotlin.test.*

class ClueStashChartImportTests {
    private val root = ObjectMapper().readTree(File("../docs/CLUE/cache/stash-chart-baked.json"))
    private val graphics = root["components"].elements().asSequence().filter { it["type"].asInt() == 5 }.map { it["spriteId"].asInt() }.toSet() + 832
    private val sprites = graphics.associateWith { 9000 + it }

    @Test fun `source rows frame checks five tabs and hover destinations survive the native encoder`() {
        val components = ClueStashChartCacheTool.components(root, 1300, (494..497).associateWith { it }, sprites, 6100)
        assertEquals(150, components.size)
        assertTrue(components[4].hidden && components[5].hidden && components[29].hidden)
        assertTrue(components.filter { it.ops.isNotEmpty() }.map { it.ops }.flatten().containsAll(listOf("Easy", "Medium", "Hard", "Elite", "78", "Close")))
        assertTrue(components.none { it.text == "Master" })
        root["controls"]["checks"].forEach { mark ->
            val c = components[mark["component"].asInt()]
            assertEquals(17, c.w); assertEquals(17, c.h); assertTrue(c.hidden)
            assertEquals(sprites.getValue(1210), c.sprite)
        }
        (30..34).forEachIndexed { index, id ->
            val hover = components[id].mouseOver
            assertEquals(8, hover.size); assertEquals(6100, hover.first()); assertEquals(index, hover.last())
            assertEquals(listOf(998, 999, 1000).map(sprites::getValue), listOf(hover[2], hover[4], hover[6]))
            assertTrue(listOf(hover[1], hover[3], hover[5]).all { it ushr 16 == 1300 })
        }
        assertTrue(components.any { it.type == 3 && it.filled && it.opacity == 245 })
        System.getProperty("clue.fixtures")?.let { path ->
            val dir = File(path).apply { mkdirs() }
            components.forEach { dir.resolve("chart-${it.id}.bin").writeBytes(OsrsInterfacePortTool.encode(it)) }
        }
    }

    @Test fun `source hover updates all three static graphics and leaves the selected tab untouched`() {
        val hover = ObjectMapper().readTree(File("../docs/CLUE/cache/hover.json"))
        val bytes = ClueStashChartCacheTool.hoverScript(Base64.getDecoder().decode(hover["targetBytes"].asText()), 2410)
        val script = ClientScriptEditor.decode(bytes)
        assertEquals(7, script.intArgs); assertEquals(7, script.intLocals)
        fun run(selected: Int): List<Pair<Int, Int>> {
            val args = listOf(1300 shl 16 or 49, 9998, 1300 shl 16 or 50, 9999, 1300 shl 16 or 51, 10000, 2)
            val stack = mutableListOf<Int>(); val writes = mutableListOf<Pair<Int, Int>>(); var pc = 0
            while (pc < script.code.size) {
                val ins = script.code[pc++]
                when (ins.opcode) {
                    33 -> stack += args[ins.int]
                    1 -> { assertEquals(2410, ins.int); stack += selected }
                    8 -> { val b = stack.removeAt(stack.lastIndex); val a = stack.removeAt(stack.lastIndex); if (a == b) pc = script.indexOf(ins.target!!) }
                    2105 -> { val component = stack.removeAt(stack.lastIndex); val sprite = stack.removeAt(stack.lastIndex); writes += component to sprite }
                    21 -> { assertTrue(stack.isEmpty()); return writes }
                    else -> fail("Unexpected native hover instruction ${ins.opcode}")
                }
            }
            fail("Hover did not return")
        }
        assertTrue(run(2).isEmpty())
        assertEquals(listOf(49, 50, 51).map { (1300 shl 16 or it) }, run(0).map { it.first })
        assertEquals(listOf(9998, 9999, 10000), run(0).map { it.second })
    }
}
