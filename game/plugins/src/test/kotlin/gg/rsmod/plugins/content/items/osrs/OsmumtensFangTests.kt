package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.DEFAULT_MIN_HIT
import java.io.File
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "fang" (tx-20260913-232754): Osmumten's fang stats, passives and Eviscerate against the wiki/calculator. */
class OsmumtensFangTests {
    @Test
    fun `the fang matches its item page`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val eq = root.first { it.path("id").asInt() == Items.OSMUMTENS_FANG }.path("equipment")
        assertEquals(105, eq.path("attack_stab").asInt())
        assertEquals(75, eq.path("attack_slash").asInt())
        assertEquals(103, eq.path("melee_strength").asInt())
        assertEquals(5, eq.path("attack_speed").asInt())
        assertEquals(-1, eq.path("equip_type").asInt(), "one-handed")
        assertEquals(5, eq.path("weapon_type").asInt())
        assertEquals(mapOf(0 to 82), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
    }

    @Test
    fun `hit chance follows the calculator formula and is continuous at equal rolls`() {
        fun close(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-12, "expected $expected but was $actual")
        close(1.0 - 102.0 * 203.0 / (6.0 * 301.0 * 301.0), OsmumtensFang.hitChance(300.0, 100.0))
        close(100.0 * 405.0 / (6.0 * 101.0 * 301.0), OsmumtensFang.hitChance(100.0, 300.0))
        val a = 250.0
        close(1.0 - (a + 2.0) * (2.0 * a + 3.0) / (6.0 * (a + 1.0) * (a + 1.0)), OsmumtensFang.hitChance(a, a))
        close(OsmumtensFang.hitChance(a, a), a * (4.0 * a + 5.0) / (6.0 * (a + 1.0) * (a + 1.0)))
        assertTrue(OsmumtensFang.hitChance(300.0, 100.0) > 1.0 - 102.0 / (2.0 * 301.0), "the fang is more accurate than the standard roll")
    }

    @Test
    fun `damage range is 15 to 85 percent and Eviscerate keeps the true max`() {
        assertEquals(9 to 51, OsmumtensFang.damageRange(60.0, special = false), "wiki example: max hit 60 rolls 9-51")
        assertEquals(9 to 60, OsmumtensFang.damageRange(60.0, special = true))
        assertEquals(0 to 6, OsmumtensFang.damageRange(6.0, special = false))
        assertEquals(DEFAULT_MIN_HIT, OsmumtensFang.minHitArgument(0))
        assertEquals(9.0, OsmumtensFang.minHitArgument(9))
        assertEquals(25, OsmumtensFang.SPECIAL_ENERGY)
        assertEquals(1.5, OsmumtensFang.SPECIAL_ACCURACY)
    }

    @Test
    fun `the formula, the strategy and Eviscerate use the fang rules`() {
        assertTrue("OsmumtensFang.usesFangAccuracy(pawn)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MeleeCombatFormula.kt").readText())
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MeleeCombatStrategy.kt").readText()
        assertTrue("OsmumtensFang.damageRange(maxHit, special = false)" in strategy)
        val special = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/osmumtens_fang.plugin.kts").readText()
        assertTrue("SpecialAttacks.register(OsmumtensFang.SPECIAL_ENERGY, Items.OSMUMTENS_FANG)" in special)
        assertTrue("damageRange(maxHit, special = true)" in special && "specialAttackMultiplier = OsmumtensFang.SPECIAL_ACCURACY" in special)
    }
}
