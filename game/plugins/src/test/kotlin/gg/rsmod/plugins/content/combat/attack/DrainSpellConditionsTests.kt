package gg.rsmod.plugins.content.combat.attack

import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.combat.strategy.magic.SpellEffect
import gg.rsmod.plugins.content.combat.strategy.magic.StatDrainRule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Root cause (2026-09-14): Void's `not_confused/not_weakened/not_cursed/not_vulnerable` conditions were never ported, so every npc
 * curse-spell attack (Ahrim, dark wizards, chaos druids, skeleton mages) was never selected. The whole roster now runs on
 * [StatDrainRule], the rule player casts use.
 */
class DrainSpellConditionsTests {
    private val spells =
        mapOf(
            "not_confused" to CombatSpell.CONFUSE,
            "not_weakened" to CombatSpell.WEAKEN,
            "not_cursed" to CombatSpell.CURSE,
            "not_vulnerable" to CombatSpell.VULNERABILITY,
        )
    private val skillNames = mapOf(Skills.ATTACK to "attack", Skills.STRENGTH to "strength", Skills.DEFENCE to "defence")

    @Test
    fun `every drain-condition attack drains the stat and percentage of its spell`() {
        val rows = ObjectMapper().readTree(File("../../data/cfg/npcs/npc-attacks.json"))
        val offenders = mutableListOf<String>()
        var checked = 0
        rows.forEach { row ->
            row["attacks"].forEach { attack ->
                val spell = spells[attack["condition"].asText()] ?: return@forEach
                checked++
                val effect = spell.effect as SpellEffect.StatDrain
                val drains = attack["drains"].toList()
                val ok =
                    drains.size == 1 && drains[0]["skill"].asText() == skillNames[effect.skill] &&
                        drains[0]["multiplier"].asDouble() == effect.percent / 100.0
                if (!ok) offenders += "${row["name"].asText()} (${row["id"].asInt()}) ${attack["id"].asText()}"
            }
        }
        assertTrue(checked >= 17, "drain-condition attacks found: $checked")
        assertTrue(offenders.isEmpty(), "drain attacks not matching their spell: $offenders")
    }

    @Test
    fun `every drain condition is registered on the shared rule`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/combat/attack/npc_attack_conditions.plugin.kts").readText()
        spells.forEach { (condition, spell) ->
            assertTrue("""NpcAttacks.condition("$condition") { _, target -> StatDrainRule.canDrain(target, CombatSpell.${spell.name}) }""" in plugin, condition)
        }
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        assertTrue("StatDrainRule.canDrain(base, current, percent, boost)" in strategy)
    }

    @Test
    fun `Ahrim casts Confuse, Weaken and Curse as the OSRS Wiki lists`() {
        val rows = ObjectMapper().readTree(File("../../data/cfg/npcs/npc-attacks.json"))
        val ahrim = rows.first { it["id"].asInt() == 2025 }
        val conditions = ahrim["attacks"].map { it["condition"].asText() }
        assertTrue(listOf("not_confused", "not_weakened", "not_cursed").all { it in conditions }, "$conditions")
        val curse = ahrim["attacks"].first { it["id"].asText() == "curse" }
        // Void magic.anims/gfx/sounds: curse_staff 1165, curse_cast 108, curse 109, curse_impact 110; sounds curse_cast 127, curse_impact 126.
        assertEquals(1165, curse["anim"].asInt())
        assertEquals(listOf(108, 109, 110), listOf(curse["gfx"][0]["id"], curse["projectiles"][0]["id"], curse["impact_gfx"][0]["id"]).map { it.asInt() })
        assertEquals(listOf(127, 126), listOf(curse["target_sounds"][0]["id"], curse["impact_sounds"][0]["id"]).map { it.asInt() })
    }

    @Test
    fun `a stat lowered by the spell cannot be lowered again`() {
        assertEquals(4, StatDrainRule.drainAmount(99, 5))
        assertTrue(StatDrainRule.canDrain(99, 99, 5))
        assertTrue(StatDrainRule.canDrain(99, 96, 5))
        assertFalse(StatDrainRule.canDrain(99, 95, 5))
        assertEquals(9, StatDrainRule.drainAmount(99, 10))
        assertTrue(StatDrainRule.canDrain(99, 91, 10))
        assertFalse(StatDrainRule.canDrain(99, 90, 10))
        // Below level 20 a 5% drain floors to 0: nothing to drain (same as the player cast path).
        assertFalse(StatDrainRule.canDrain(10, 10, 5))
    }
}
