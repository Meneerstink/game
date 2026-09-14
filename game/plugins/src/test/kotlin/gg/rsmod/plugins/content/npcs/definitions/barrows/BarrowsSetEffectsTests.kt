package gg.rsmod.plugins.content.npcs.definitions.barrows

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Q-040: every Barrows brother has its OSRS Wiki npc set effect on the shared attack model. */
class BarrowsSetEffectsTests {
    @Test
    fun `the roster is the six brother combat definitions and each registers one hook`() {
        val rows = ObjectMapper().readTree(File("../../data/cfg/npcs/npc-attacks.json"))
        val defs = (2025..2030).map { id -> rows.first { it["id"].asInt() == id }["combat_def"].asText() }.toSet()
        assertEquals(defs, BarrowsSetEffects.COMBAT_DEFS)
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/barrows/barrows_set_effects.plugin.kts").readText()
        mapOf("DHAROK" to "onHitRoll", "VERAC" to "onHitRoll", "GUTHAN" to "onHitDealt", "TORAG" to "onHitDealt", "KARIL" to "onHitDealt", "AHRIM" to "onHitDealt")
            .forEach { (brother, hook) ->
                assertEquals(1, Regex("""NpcAttacks\.\w+\(BarrowsSetEffects\.$brother\)""").findAll(plugin).count(), brother)
                assertTrue("NpcAttacks.$hook(BarrowsSetEffects.$brother)" in plugin, brother)
            }
        assertTrue("roll.landsIgnoringPrayer" in plugin.substringAfter("BarrowsSetEffects.KARIL)").substringBefore("NpcAttacks."))
        assertTrue("roll.landsIgnoringPrayer" in plugin.substringAfter("BarrowsSetEffects.AHRIM)"))
    }

    @Test
    fun `set effect formulas equal the wiki`() {
        assertEquals(57, kotlin.math.floor(BarrowsSetEffects.dharokMaxHit(29.0, 1, 100) + 1e-9).toInt())
        assertEquals(29.0, BarrowsSetEffects.dharokMaxHit(29.0, 100, 100))
        assertEquals(40.0, BarrowsSetEffects.toragEnergyAfter(50.0))
        assertEquals(19, BarrowsSetEffects.karilAgilityDrain(99))
        assertEquals(25, BarrowsSetEffects.SET_EFFECT_CHANCE_PERCENT)
        assertEquals(20, BarrowsSetEffects.AHRIM_CHANCE_PERCENT)
        assertEquals(15.0, BarrowsSetEffects.VERAC_PROTECTED_MAX_HIT)
        assertEquals(5, BarrowsSetEffects.AHRIM_STRENGTH_DRAIN)
    }

    @Test
    fun `the shared attack model runs the hooks with one accuracy roll`() {
        val attacks = File("src/main/kotlin/gg/rsmod/plugins/content/combat/attack/NpcAttacks.kt").readText()
        listOf(
            "DragonfireTable.typeFor(row.combatDef, attack.id), row.combatDef)",
            "rollHooks[combatDef]?.invoke(npc, target, roll)",
            "maxHit = roll.maxHit,", "landHit = roll.landHit,",
            "pawnHit.hit.addAction { hook(npc, target, roll, pawnHit.hit.hitmarks.sumOf { it.damage }) }",
            "RangedCombatFormula.getUnprotectedAccuracy(npc, target) >= rollValue",
            "MagicCombatFormula.getUnprotectedAccuracy(npc, target) >= rollValue",
        ).forEach { assertTrue(it in attacks, it) }
        val ranged = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/RangedCombatFormula.kt").readText()
        assertTrue("return getUnprotectedAccuracy(pawn, target, specialAttackMultiplier)" in ranged)
    }
}
