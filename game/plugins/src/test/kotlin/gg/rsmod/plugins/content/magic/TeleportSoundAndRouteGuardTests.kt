package gg.rsmod.plugins.content.magic

import gg.rsmod.plugins.api.cfg.Sfx
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Teleport audit guards (2026-09-22).
 *
 * - The shared [teleport] is the one place teleport sounds come from (Void `teleport.sounds.toml`), so jewellery
 *   plugins must not play their own copy on top of it (root-cause class 4).
 * - Item teleports bind their option by name: a numeric slot bound Ring of kinship's teleport to its "Customise" option.
 * - A block comment must never stay nested across lines: a `*.plugin.kts` path inside a KDoc opened a second comment
 *   level and silently turned the whole home supplies shop into comment text (the script still compiled).
 */
class TeleportSoundAndRouteGuardTests {
    private val content = File("src/main/kotlin/gg/rsmod/plugins/content")

    @Test
    fun `teleport types carry the sourced start and land sounds`() {
        assertEquals(Sfx.TELEPORT_ALL, TeleportType.JEWELRY.startSound)
        assertEquals(Sfx.TELEPORT_REVERSE, TeleportType.JEWELRY.landSound)
        assertEquals(Sfx.TELEPORT_ALL, TeleportType.MODERN.startSound)
        assertEquals(Sfx.POH_TABLET_BREAK_TELEPORT, TeleportType.TAB.startSound)
        assertEquals(Sfx.FT_FAIRY_TELEPORT, TeleportType.FAIRY.startSound)
        assertNull(TeleportType.ANCIENT.startSound, "Void gives ancient teleports no generic sound; the spell plays its own")
    }

    @Test
    fun `jewellery plugins do not play a second teleport sound`() {
        val offenders =
            File(content, "items/jewellery").walkTopDown().filter { it.extension == "kts" }
                .filter { f -> f.readText().contains("AreaSound(") && f.readText().contains("200") }
                .map { it.name }.toList()
        assertTrue(offenders.isEmpty(), "jewellery plugins spawning their own teleport sound: $offenders")
    }

    @Test
    fun `teleport item options are bound by name, never by slot number`() {
        val numeric = Regex("""on_item_option\(item = [^,]+, option = \d+\)""")
        val offenders =
            listOf("items/jewellery", "items/teletabs", "items/osrs").flatMap { dir ->
                File(content, dir).walkTopDown().filter { it.extension == "kts" }
                    .filter { f -> f.readLines().any { numeric.containsMatchIn(it) } }
                    .map { it.name }.toList()
            }
        assertTrue(offenders.isEmpty(), "teleport items bound by slot number: $offenders")
    }

    @Test
    fun `no block comment stays nested across a line break`() {
        val offenders = ArrayList<String>()
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" || it.extension == "kts" }.forEach { file ->
            val text = file.readText()
            var depth = 0
            var line = 1
            var i = 0
            while (i < text.length) {
                when {
                    depth == 0 && text.startsWith("//", i) -> {
                        i = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                        continue
                    }
                    depth == 0 && text.startsWith("\"\"\"", i) -> {
                        val end = text.indexOf("\"\"\"", i + 3)
                        line += text.substring(i, end).count { it == '\n' }
                        i = end + 3
                        continue
                    }
                    depth == 0 && text[i] == '"' -> {
                        var j = i + 1
                        while (j < text.length && !(text[j] == '"' && text[j - 1] != '\\')) j++
                        i = j + 1
                        continue
                    }
                    text.startsWith("/*", i) -> {
                        depth++
                        i += 2
                        continue
                    }
                    text.startsWith("*/", i) && depth > 0 -> {
                        depth--
                        i += 2
                        continue
                    }
                    text[i] == '\n' -> {
                        if (depth > 1) offenders += "${file.path}:$line"
                        line++
                    }
                }
                i++
            }
        }
        assertTrue(offenders.isEmpty(), "nested block comment left open across lines (code may be commented out): ${offenders.distinct().take(10)}")
    }
}
