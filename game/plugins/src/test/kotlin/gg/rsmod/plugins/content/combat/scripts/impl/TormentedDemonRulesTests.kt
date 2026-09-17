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
    fun `style rotation and splash special follow Void`() {
        // Void TormentedDemon.kt tds_change_attack: magic -> range -> melee -> magic (0 melee, 1 magic, 2 ranged).
        assertEquals(2, TormentedDemonCombatScript.nextStyle(1), "magic -> ranged")
        assertEquals(0, TormentedDemonCombatScript.nextStyle(2), "ranged -> melee")
        assertEquals(1, TormentedDemonCombatScript.nextStyle(0), "melee -> magic")
        // guthix_temple.combat.toml: style section chance 20 + special chance 1; special hit 281 (x10).
        assertEquals(21, TormentedDemonCombatScript.SPECIAL_ROLL)
        assertEquals(28, TormentedDemonCombatScript.SPLASH_DAMAGE)
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/combat/scripts/impl/TormentedDemonCombatScript.kt").readText()
        assertTrue(script.contains("Items.HOLY_WATER") && !script.contains("Items.SILVERLIGHT"), "owner decision 3: Darklight or holy water")
        assertTrue(script.contains("npc.timers[STYLE_TIMER] = 26") && script.contains("ATTACK_DELAY] = 6"), "Void timer 26 + action delay 6")
    }

    @Test
    fun `one combat definition covers every demon id with the donors' hitpoints`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/demons/tormented_demon.plugin.kts").readText()
        assertTrue(plugin.contains("TormentedDemonCombatScript.ids.forEach { demonId ->") && plugin.contains("set_combat_def(npc = demonId)"))
        assertTrue(plugin.contains("hitpoints = 3260"), "Novite + Void: 3260 (x10)")
        val pipeline = File("src/main/kotlin/gg/rsmod/plugins/content/combat/PawnExt.kt").readText()
        assertTrue(pipeline.contains("TormentedDemonCombatScript.ids && damage >= 0"), "misses must reach the prayer counter")
    }

    @Test
    fun `delayed splash rejects an offline player target`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/combat/scripts/impl/TormentedDemonCombatScript.kt").readText()
        assertTrue("target.isAlive() && (target !is Player || target.isOnline)" in script)
    }
}
