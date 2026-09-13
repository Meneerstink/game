package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.combat.CombatClass
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-012 B7 roster over every tormented demon definition in the revision-667 cache.
 * Head icons from the pristine openrs2 #1473 cache (NpcDefProbeTool 8349..8369): 0, 2, 1 repeating
 * (0 protect melee, 2 protect magic, 1 protect missiles); Novite TormentedDemon.switchPrayers uses the same offsets.
 */
class TormentedDemonRulesTests {
    private val cacheHeadIcons = (8349..8369).associateWith { listOf(0, 2, 1)[(it - 8349) % 3] }
    private val iconStyle = mapOf(0 to CombatClass.MELEE, 2 to CombatClass.MAGIC, 1 to CombatClass.RANGED)

    @Test
    fun `every demon id prays the style its cache head icon shows`() {
        assertEquals((8349..8369).toList(), TormentedDemonCombatScript.ids.sorted(), "script roster must equal the cache ids")
        cacheHeadIcons.forEach { (id, icon) ->
            assertEquals(iconStyle[icon], TormentedDemonCombatScript.prayedStyle(id), "npc $id head icon $icon")
        }
    }

    @Test
    fun `every demon transforms within its own group to the variant showing the prayed style`() {
        TormentedDemonCombatScript.ids.forEach { id ->
            CombatClass.values().filter { it in iconStyle.values }.forEach { style ->
                val variant = TormentedDemonCombatScript.variantFor(id, style)
                assertEquals((id - 8349) / 3, (variant - 8349) / 3, "npc $id -> $variant left its cache group")
                assertEquals(iconStyle[cacheHeadIcons.getValue(variant)], style, "npc $id -> $variant shows the wrong icon for $style")
            }
        }
        assertEquals(31, TormentedDemonCombatScript.PRAYER_SWITCH_DAMAGE, "Void/Novite 310 in the x10 unit")
    }

    @Test
    fun `one combat definition covers every demon id with the donors' hitpoints`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/demons/tormented_demon.plugin.kts").readText()
        assertTrue(plugin.contains("TormentedDemonCombatScript.ids.forEach { demonId ->") && plugin.contains("set_combat_def(npc = demonId)"))
        assertTrue(plugin.contains("hitpoints = 3260"), "Novite + Void: 3260 (x10)")
        val pipeline = File("src/main/kotlin/gg/rsmod/plugins/content/combat/PawnExt.kt").readText()
        assertTrue(pipeline.contains("TormentedDemonCombatScript.ids && damage >= 0"), "misses must reach the prayer counter")
    }
}
