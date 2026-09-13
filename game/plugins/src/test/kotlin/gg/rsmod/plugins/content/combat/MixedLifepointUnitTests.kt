package gg.rsmod.plugins.content.combat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-010 B1 (x10 -> 1:1 migration, attempted twice before): fails on mixed units anywhere in the plugins.
 *
 * Lifepoints, hitsplats and damage are 1:1. A raw integer literal of 100 or more flowing straight into a
 * lifepoint change or a raw hit is the x10 donor unit leaking through (Corp regen `+ 250 + count * 50` and core
 * drain `10..130` were live examples). Such figures must go through [LifepointUnits.fromLedger] (or the
 * Summoning / Food ledger boundaries). Every remaining exception is listed with its reason.
 */
class MixedLifepointUnitTests {
    private val pluginsMain = File("src/main/kotlin/gg/rsmod/plugins")

    /** file name -> reason; each entry must still match, so a fixed file drops out of the list. */
    private val allowed = mapOf(
        "home_pool.plugin.kts" to "heal(9999) is a heal-to-full sentinel, capped at the maximum",
        "FightCaveCombatScripts.kt" to "PARKED minigame (Fight Caves): Yt-HurKot +100 heal recorded, not in scope",
    )

    private val mutation = Regex("""(setCurrentLifepoints\(|alterLifepoints\(|\.heal\(|\bhit\(damage\s*=|maxHit\s*=)""")
    private val bigLiteral = Regex("""(?<![\w.])\d{3,}(?![\w.])""")

    private val callOpen = Regex("""(setCurrentLifepoints\(|alterLifepoints\(|\.heal\()""")
    private val assigned = Regex("""\b(?:hit\(damage|maxHit)\s*=\s*(\d{3,})(?![\w.])""")

    /** True when an integer literal >= 100 is an argument of a lifepoint change, or is assigned to damage/maxHit. */
    private fun rawFigures(code: String): Boolean {
        if (assigned.containsMatchIn(code)) return true
        return callOpen.findAll(code).any { match ->
            var depth = 1
            var end = match.range.last + 1
            while (end < code.length && depth > 0) {
                when (code[end]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                end++
            }
            bigLiteral.containsMatchIn(code.substring(match.range.last + 1, (end - 1).coerceAtLeast(match.range.last + 1)))
        }
    }

    @Test
    fun `no raw x10 literal flows into a lifepoint change or hit`() {
        assertTrue(pluginsMain.isDirectory, pluginsMain.absolutePath)
        val hits = pluginsMain.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, raw ->
                    val code = raw.substringBefore("//").trim()
                    if (code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                    if (!mutation.containsMatchIn(code) || code.contains("fromLedger(") || code.contains("dealLedgerHit(")) return@mapIndexedNotNull null
                    if (!rawFigures(code)) return@mapIndexedNotNull null
                    Triple(file.name, index + 1, code)
                }
            }
            .toList()
        val offenders = hits.filter { it.first !in allowed.keys }.map { "${it.first}:${it.second}: ${it.third}" }
        assertTrue(offenders.isEmpty(), "raw x10 literals in lifepoint/hit paths:\n" + offenders.joinToString("\n"))
        val stale = allowed.keys.filter { name -> hits.none { it.first == name } }
        assertEquals(emptyList(), stale, "allow-list entries that no longer match (remove them)")
    }

    @Test
    fun `the ledger conversion is exact for whole life points`() {
        assertEquals(25, LifepointUnits.fromLedger(250))
        assertEquals(65, LifepointUnits.fromLedger(250 + 8 * 50))
        assertEquals(1, LifepointUnits.fromLedger(10))
        assertEquals(13, LifepointUnits.fromLedger(130))
    }
}
