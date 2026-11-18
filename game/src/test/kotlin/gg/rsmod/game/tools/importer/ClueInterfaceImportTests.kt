package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*

class ClueInterfaceImportTests {
    private val root = ObjectMapper().readTree(File("../docs/CLUE/cache/interfaces.json"))
    private val fonts = mapOf(497 to 999)
    private val models = mapOf(3395 to 60001, 1226 to 60002)
    private val sprites = mapOf(539 to 9000, 540 to 9001)

    @Test fun `close hover preserves the sourced callback and translated normal and highlighted sprites`() {
        for ((group, close) in listOf(73 to 4, 203 to 1)) {
            val component = ClueInterfaceImportTool.components(group, root[group.toString()], fonts, models, sprites)[close]
            val hooks = InterfaceHookProbeTool.componentHooks(OsrsInterfacePortTool.encode(component))
            assertEquals(listOf(44, -2147483645, 9001), hooks["onMouseOver"])
            assertEquals(listOf(44, -2147483645, 9000), hooks["onMouseLeave"])
            assertTrue(hooks.filterKeys { it !in setOf("onMouseOver", "onMouseLeave") }.values.all { it == null })
        }
    }

    @Test fun `wide IF3 model preserves all seventeen id bits and the exact source camera`() {
        val components = ClueInterfaceImportTool.components(73, root["73"], fonts, mapOf(3395 to 66084, 1226 to 66085), sprites)
        val bytes = OsrsInterfacePortTool.encode(components[2])
        assertEquals(0x46, bytes[1].toInt() and 255)
        assertEquals(66085, InterfaceHookProbeTool.componentModelId(bytes))
        assertContentEquals(intArrayOf(104, 761, 0, 914), InterfaceHookProbeTool.componentModelCamera(bytes))
        val legacy = ClueInterfaceImportTool.components(73, root["73"], fonts, models, sprites)
        val oldBytes = OsrsInterfacePortTool.encode(legacy[2])
        assertEquals(6, oldBytes[1].toInt() and 255)
        assertEquals(60002, InterfaceHookProbeTool.componentModelId(oldBytes))
        assertEquals(oldBytes.size + 2, bytes.size)
    }

    @Test fun `camera adapter preserves the raw pinned OSRS angles rather than similarly named fields`() {
        val proof = ObjectMapper().readTree(File("../docs/CLUE/cache/ui-camera-order.json"))
        for (group in listOf(73,203)) {
            val components = ClueInterfaceImportTool.components(group,root[group.toString()],fonts,models,sprites)
            for (component in components.filter { it.type == 6 }) {
                val source = proof[group.toString()][component.id.toString()]
                val raw = Base64.getDecoder().decode(source["rawBase64"].asText())
                val hash = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it.toInt() and 255) }
                assertEquals(source["sha256"].asText(),hash)
                val camera = ByteBuffer.wrap(raw,source["sourceCameraOffset"].asInt(),8)
                val expected = IntArray(4) { camera.short.toInt() and 65535 }
                assertContentEquals(expected,InterfaceHookProbeTool.componentModelCamera(OsrsInterfacePortTool.encode(component)))
            }
        }
    }

    @Test fun `source reward screen retains its paper chest close button and all twelve inventory slots`() {
        val components = ClueInterfaceImportTool.components(73, root["73"], fonts, models, sprites)
        assertEquals(17, components.size)
        assertEquals(60001, components[1].model)
        assertEquals(60002, components[2].model)
        assertEquals(listOf("Close"), components[4].ops)
        val slots = components.drop(ClueInterfaceImportTool.REWARD_FIRST)
        assertEquals(12, slots.size)
        assertEquals(listOf(0, 0, 0, 34, 34, 34, 68, 68, 68, 102, 102, 102), slots.map { it.y })
        assertEquals(List(4) { listOf(0, 1, 2) }.flatten(), slots.map { it.xMode })
        assertTrue(slots.all { it.w == 36 && it.h == 32 && it.parent == 3 && it.hidden })
        assertEquals((0..16).toList(), components.map { it.id })
        assertTrue(components.all { OsrsInterfacePortTool.encode(it).isNotEmpty() })
    }

    @Test fun `clue text keeps source q8 font line spacing centering and viewport`() {
        val components = ClueInterfaceImportTool.components(203, root["203"], fonts, models, sprites)
        assertEquals(3, components.size)
        assertEquals(60001, components[0].model)
        val text = components[2]
        assertEquals(999, text.font)
        assertEquals(25, text.lineHeight)
        assertEquals(1, text.alignX); assertEquals(1, text.alignY)
        assertEquals(listOf(126, 37, 266, 256), listOf(text.x, text.y, text.w, text.h))
        assertTrue(text.shadow)
        assertFailsWith<NoSuchElementException> { ClueInterfaceImportTool.components(203, root["203"], emptyMap(), models, sprites) }
        assertFailsWith<NoSuchElementException> { ClueInterfaceImportTool.components(73, root["73"], fonts, emptyMap(), sprites) }
        assertFailsWith<IllegalArgumentException> { ClueInterfaceImportTool.components(322, root["322"], fonts, models, sprites) }
    }
}
