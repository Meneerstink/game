package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.*

class ClueStashSettingImportTests {
    @Test fun `setting leaves native buttons untouched and fits in the existing Settings gap`() {
        val source = ObjectMapper().readTree(File("../docs/CLUE/cache/stash-setting.json"))["6245"]["params"]
        val nodes = ClueStashSettingCacheTool.components(26, 496, 8515, source["1086"].asText())
        assertEquals(listOf(26, 27, 28), nodes.map { it.id })
        assertEquals(0, nodes.first().parent)
        assertTrue(nodes.first().y > 173 && nodes.first().y + nodes.first().h < 206)
        assertTrue(nodes.first().x >= 0 && nodes.first().x + nodes.first().w <= 190)
        assertEquals(source["1086"].asText(), nodes.last().text)
        assertTrue(nodes.drop(1).all { it.parent == 26 && it.x + it.w <= nodes.first().w && it.y + it.h <= nodes.first().h })
        val bytes = nodes.map(OsrsInterfacePortTool::encode)
        assertEquals("Toggle", InterfaceHookProbeTool.componentOps(bytes.first()).first())
        assertEquals(8515, InterfaceHookProbeTool.componentSprite(bytes[1]))
        assertTrue(bytes.all { InterfaceHookProbeTool.componentHooks(it).values.all { hook -> hook == null } })
        System.getProperty("clue.fixtures")?.let { folder ->
            File(folder).apply { mkdirs() }.let { dir -> bytes.forEachIndexed { index, raw -> dir.resolve("setting-$index.bin").writeBytes(raw) } }
        }
    }

    @Test fun `changed source or overlap with native component ids fails before a transaction`() {
        assertFailsWith<IllegalArgumentException> { ClueStashSettingCacheTool.source(byteArrayOf(0)) }
        assertFailsWith<IllegalArgumentException> { ClueStashSettingCacheTool.components(25, 496, 8515, "STASH") }
        assertFailsWith<IllegalArgumentException> { ClueStashSettingCacheTool.components(65533, 496, 8515, "STASH") }
    }
}
