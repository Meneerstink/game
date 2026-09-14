package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.mechanics.poison.Poison
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "sceptres" against the OSRS Wiki "Ancient sceptre" table and the item pages. */
class OsrsSceptresImportTests {
    @Test
    fun `the ancient sceptre table - poison, drain, heal and freeze`() {
        assertEquals(10, AncientSceptres.smokeSeverity(2, boosted = false))
        assertEquals(11, AncientSceptres.smokeSeverity(2, boosted = true), "Smoke Rush/Burst severity 11")
        assertEquals(22, AncientSceptres.smokeSeverity(4, boosted = true), "Smoke Blitz/Barrage severity 22")
        assertEquals(2, Poison.getDamageForTicks(10 - 1))
        assertEquals(3, Poison.getDamageForTicks(11 - 1), "severity 11 hits 3")
        assertEquals(5, Poison.getDamageForTicks(22 - 1), "severity 22 hits 5")
        assertEquals(8, AncientSceptres.freezeTicks(8, boosted = true), "Ice Rush receives no buff")
        assertEquals(17, AncientSceptres.freezeTicks(16, boosted = true))
        assertEquals(26, AncientSceptres.freezeTicks(24, boosted = true))
        assertEquals(35, AncientSceptres.freezeTicks(32, boosted = true))
        assertEquals(10, AncientSceptres.shadowDrainPercent(CombatSpell.SHADOW_BURST))
        assertEquals(15, AncientSceptres.shadowDrainPercent(CombatSpell.SHADOW_BARRAGE))
        assertEquals(25, AncientSceptres.bloodHeal(100, boosted = false))
        assertEquals(27, AncientSceptres.bloodHeal(100, boosted = true), "27.5 %, rounded down")
        assertEquals(0.8, AncientSceptres.SMOKE_HEAL_MULTIPLIER)
        assertEquals(10, AncientSceptres.SMOKE_HEAL_REDUCTION_TICKS, "6 seconds")
    }

    @Test
    fun `requirements, locks, PvP death and the dragon hunter wand are wired`() {
        val yml = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.ANCIENT_SCEPTRE..Items.PURGING_STAFF }.associateBy { it.path("id").asInt() }
        fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        assertEquals(mapOf(6 to 70, 2 to 60, 0 to 50), reqs(Items.ANCIENT_SCEPTRE))
        assertEquals(mapOf(6 to 75, 2 to 60, 0 to 50), reqs(Items.ICE_ANCIENT_SCEPTRE_L))
        assertEquals(mapOf(6 to 65), reqs(Items.DRAGON_HUNTER_WAND))
        assertEquals(mapOf(6 to 77, 0 to 50), reqs(Items.PURGING_STAFF))
        val trouver = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/trouver/trouver.plugin.kts").readText()
        listOf("ANCIENT_SCEPTRE_L_BROKEN", "BLOOD_ANCIENT_SCEPTRE_L_BROKEN", "ICE_ANCIENT_SCEPTRE_L_BROKEN", "SMOKE_ANCIENT_SCEPTRE_L_BROKEN", "SHADOW_ANCIENT_SCEPTRE_L_BROKEN").forEach {
            assertTrue("brokenItemId = Items.$it" in trouver, it)
        }
        assertTrue("Item(Items.ANCIENT_STAFF, 1)" in File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/PvpDeathBreakables.kt").readText())
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MagicCombatFormula.kt").readText()
        assertTrue("roll = Math.floor(roll * 7 / 4)" in formula && "hit = Math.floor(hit * 7 / 5)" in formula)
        assertTrue("AncientSceptres.iceAccuracyMultiplier" in formula)
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        assertTrue("Poison.poisonSeverity(target" in strategy && "capValue = sceptres.overhealCap" in strategy)
        assertEquals(Items.SMOKE_ANCIENT_SCEPTRE, AncientSceptres.QUARTZ_UPGRADES[Items.SMOKE_QUARTZ])
        assertEquals(4, AncientSceptres.QUARTZ_UPGRADES.size)
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/osrs_sceptres.plugin.kts").readText()
        assertTrue("item2 = Items.ANCIENT_SCEPTRE" in plugin && "option = \"Dismantle\"" in plugin)
    }
}
