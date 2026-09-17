package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.poison.WeaponPoison
import java.io.File
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "abyssaldagger" (tx-20260913-235440): Abyssal dagger variants and Abyssal Puncture, plus the forced
 * slash defence roll the wiki DPS calculator uses for dagger and godsword specials.
 */
class AbyssalDaggerTests {
    private val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())

    @AfterTest
    fun close() {
        library.close()
    }

    @Test
    fun `all four daggers share the item page stats and the 70 Attack requirement`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.ABYSSAL_DAGGER..Items.ABYSSAL_DAGGER_P_PLUS_PLUS_NOTED }.associateBy { it.path("id").asInt() }
        AbyssalDagger.IDS.forEach { id ->
            val eq = yml.getValue(id).path("equipment")
            assertEquals(listOf(75, 40, -4, 1), listOf("attack_stab", "attack_slash", "attack_crush", "attack_magic").map { eq.path(it).asInt() }, "$id attack")
            assertEquals(1, eq.path("defence_magic").asInt(), "$id magic defence")
            assertEquals(75, eq.path("melee_strength").asInt(), "$id strength")
            assertEquals(4, eq.path("attack_speed").asInt(), "$id speed")
            assertEquals(5, eq.path("weapon_type").asInt(), "$id dagger class")
            assertEquals(mapOf(0 to 70), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }, "$id requirement")
        }
    }

    @Test
    fun `all poisoned dagger variants are covered by the shared weapon poison roster`() {
        val definitions = DefinitionSet()
        definitions.load(library, ItemDef::class.java)
        AbyssalDagger.IDS.drop(1).forEach { id ->
            assertTrue(WeaponPoison.severity(definitions, id) >= WeaponPoison.WEAPON_POISON, "$id should carry weapon poison")
        }
    }

    @Test
    fun `Abyssal Puncture uses one slash-defence roll for two hits at x1_25 accuracy and x0_85 damage`() {
        assertEquals(25, AbyssalDagger.SPECIAL_ENERGY)
        assertEquals(1.25, AbyssalDagger.SPECIAL_ACCURACY)
        assertEquals(0.85, AbyssalDagger.SPECIAL_DAMAGE)
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/abyssal_dagger.plugin.kts").readText()
        assertTrue("SpecialAttacks.register(AbyssalDagger.SPECIAL_ENERGY, *AbyssalDagger.IDS)" in script)
        assertTrue("defenceStyle = StyleType.SLASH" in script)
        assertEquals(1, Regex("getAccuracyAgainst\\(").findAll(script).count(), "a single attack roll")
        assertTrue("for (i in 0 until 2)" in script && "landHit = landHit" in script)
    }

    @Test
    fun `godsword specials roll against slash defence too`() {
        val godsword = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/ancient_godsword.plugin.kts").readText()
        assertTrue("getAccuracyAgainst(" in godsword && "StyleType.SLASH" in godsword)
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MeleeCombatFormula.kt").readText()
        assertTrue("defenceStyle ?: CombatConfigs.getCombatStyle(pawn)" in formula, "normal attacks keep the selected style")
    }
}
