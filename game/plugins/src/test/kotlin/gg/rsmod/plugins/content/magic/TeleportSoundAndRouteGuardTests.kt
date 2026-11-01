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
                    depth == 0 && text[i] == '"' -> {
                        // N493: a real string lexer - escapes, raw strings and dollar-brace templates with strings inside them
                        // (ConsumableTable: a raw-string Regex inside a template of an ordinary string).
                        val end = skipString(text, i)
                        line += text.substring(i, end).count { it == '\n' }
                        i = end
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

    /** Index just after the string literal that starts at [start] (one quote or three); the end of [text] when it never closes. */
    private fun skipString(
        text: String,
        start: Int,
    ): Int {
        val raw = text.startsWith(TRIPLE, start)
        var j = start + if (raw) 3 else 1
        while (j < text.length) {
            when {
                raw && text.startsWith(TRIPLE, j) -> {
                    j += 3
                    while (j < text.length && text[j] == '"') j++ // a raw string may end on more than three quotes
                    return j
                }
                !raw && text[j] == '\\' -> j += 2
                !raw && text[j] == '"' -> return j + 1
                text[j] == '$' && j + 1 < text.length && text[j + 1] == '{' -> j = skipTemplate(text, j + 2)
                else -> j++
            }
        }
        return text.length
    }

    /** Index just after the brace that closes a template whose body starts at [start] (strings inside it are skipped whole). */
    private fun skipTemplate(
        text: String,
        start: Int,
    ): Int {
        var depth = 1
        var k = start
        while (k < text.length) {
            when (text[k]) {
                '"' -> {
                    k = skipString(text, k)
                    continue
                }
                '{' -> depth++
                '}' -> if (--depth == 0) return k + 1
            }
            k++
        }
        return text.length
    }

    private companion object {
        const val TRIPLE = "\"\"\""
    }
}
