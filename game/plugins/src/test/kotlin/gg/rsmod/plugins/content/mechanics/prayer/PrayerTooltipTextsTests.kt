package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.tools.importer.CurseTooltipCacheTool
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N45 (owner 10-09 Q1 "ALL curse and prayer tooltips"): Augury said "magical accuracy by 20%" (the game gives x1.25 and +4 % magic
 * damage), Rigour left out its +23 % Ranged damage, and both unlock texts sent the player to Daemonheim (DGN removed it; N98:
 * prayer scroll + 70 Defence). Both caches carry the texts (tx-20261023-035622).
 */
class PrayerTooltipTextsTests {
    private val texts = CurseTooltipCacheTool.TOOLTIPS.groupBy({ it.struct }, { it.new })

    @Test
    fun `Augury and Rigour say what the formulas do`() {
        val magic = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MagicCombatFormula.kt").readText()
        val ranged = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/RangedCombatFormula.kt").readText()
        assertTrue("Prayers.isActive(player, Prayer.AUGURY) -> 1.25" in magic && "Prayers.isActive(player, Prayer.AUGURY) -> 0.04" in magic)
        assertTrue("Prayers.isActive(player, Prayer.RIGOUR) -> 1.20" in ranged && "Prayers.isActive(player, Prayer.RIGOUR) -> 1.23" in ranged)
        assertTrue(texts.getValue(1006).any { "accuracy by 25%" in it && "damage by 4%" in it && "Defence by 25%" in it })
        assertTrue(texts.getValue(1029).any { "accuracy by 20%" in it && "damage by 23%" in it && "Defence by 25%" in it })
    }

    @Test
    fun `their unlock texts name the scroll and 70 Defence, not Daemonheim, in both caches`() {
        assertTrue(texts.getValue(1006).any { "Arcane prayer scroll" in it && "Prayer 77" in it && "Defence 70" in it })
        assertTrue(texts.getValue(1029).any { "Dexterous prayer scroll" in it && "Prayer 74" in it && "Defence 70" in it })
        assertTrue(CurseTooltipCacheTool.plan(Paths.get("..", "..", "data", "cache").toFile().toString()).isEmpty())
        assertTrue(CurseTooltipCacheTool.plan(Paths.get("..", "..", "..", "..", "file-server", "cache").toFile().toString()).isEmpty())
    }
}
