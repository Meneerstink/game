package gg.rsmod.plugins.content

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Kotlin block comments nest: a slash-star inside a KDoc ("areas/apeatroll/" + "*: TODO") opens a second level, the file's last
 * star-slash closes only that one, and everything after it compiles to nothing. batch9/ape_atoll.plugin.kts was a constructor and main() only for
 * that reason (the whole Ape Atoll course unbound, found by obstacle batch 15, 2026-10-03). Guard: no source file ends inside a
 * block comment.
 */
class SourceCommentGuardTests {
    @Test
    fun `no source file ends inside an unclosed block comment`() {
        val roots = listOf(File("src/main/kotlin"), File("../src/main/kotlin")).filter { it.isDirectory }
        val open =
            roots.flatMap { root -> root.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }.toList() }
                .filter { endsInsideComment(it.readText()) }
                .map { it.path }
        assertEquals("files that end inside a block comment (a '/*' nested in a comment)", emptyList<String>(), open)
    }

    private fun endsInsideComment(s: String): Boolean {
        var depth = 0
        var i = 0
        val n = s.length
        while (i < n) {
            if (depth == 0 && s.startsWith("\"\"\"", i)) {
                val j = s.indexOf("\"\"\"", i + 3)
                i = if (j >= 0) j + 3 else n
                continue
            }
            if (depth == 0 && (s[i] == '"' || s[i] == '\'')) {
                val quote = s[i]
                var j = i + 1
                while (j < n && s[j] != quote && s[j] != '\n') j += if (s[j] == '\\') 2 else 1
                i = j + 1
                continue
            }
            if (depth == 0 && s.startsWith("//", i)) {
                val j = s.indexOf('\n', i)
                i = if (j >= 0) j else n
                continue
            }
            if (s.startsWith("/*", i)) {
                depth++
                i += 2
                continue
            }
            if (depth > 0 && s.startsWith("*/", i)) {
                depth--
                i += 2
                continue
            }
            i++
        }
        return depth > 0
    }
}
