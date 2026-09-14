package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.items.combine.CombinationData
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "magegearb" against the OSRS Wiki item pages. */
class OsrsMagegearBImportTests {
    private val yml by lazy {
        ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.STAFF_OF_BALANCE..Items.CONFLICTION_GAUNTLETS_NOTED }.associateBy { it.path("id").asInt() }
    }

    private fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }

    @Test
    fun `staff of balance inherits the staff of the dead effects and the shields protect from dragonfire`() {
        assertTrue(Items.STAFF_OF_BALANCE in StaffOfTheDead.DEAD_STAVES && Items.STAFF_OF_BALANCE in StaffOfTheDead.POWER_OF_DEATH_STAVES)
        assertTrue(Items.STAFF_OF_BALANCE in CombatSpell.MAGIC_DART.requiredWeapons)
        assertEquals(mapOf(0 to 75, 6 to 75), reqs(Items.STAFF_OF_BALANCE))
        val dragonfire = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/DragonfireFormula.kt").readText()
        listOf("Items.DRAGONFIRE_WARD,", "Items.DRAGONFIRE_WARD_UNCHARGED", "Items.ANCIENT_WYVERN_SHIELD,", "Items.ANCIENT_WYVERN_SHIELD_UNCHARGED").forEach {
            assertTrue(it in dragonfire, "$it protects from dragonfire")
        }
        assertEquals(mapOf(5 to 50), reqs(Items.ANTLER_GUARD))
        assertEquals(mapOf(3 to 90), reqs(Items.NECKLACE_OF_RUPTURE))
        assertEquals(mapOf(4 to 60), reqs(Items.AQUANITE_HOPPER))
        val rupture = CombinationData.values.single { it.resultItem == Items.NECKLACE_OF_RUPTURE }
        assertEquals(84, rupture.levelRequired)
        assertEquals(500.0, rupture.experience)
    }

    @Test
    fun `confliction gauntlets and aquanite hopper are wired as the wiki describes`() {
        val magic = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        assertTrue("ConflictionGauntlets.roll(pawn, target, spell.uniqueId, accuracy, primary && !iceAgainstPlayer)" in magic)
        assertTrue("ConflictionGauntlets.roll(player, target, weapon.id" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/PoweredStaffCombatStrategy.kt").readText())
        val ranged = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("formula.getAccuracy(pawn, target, 2.0 / 3.0)" in ranged && "formula.getMaxHit(pawn, target, 1.0 / 3.0)" in ranged)
        assertTrue("if (shot?.bolt != null) 1.0 else world.randomDouble() * 3.0" in ranged, "a third of the proc chance, none after a first proc")
        val recipes = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/osrs_magegear.plugin.kts").readText()
        assertTrue("player.inventory.remove(Items.DEMON_TEAR, 10_000)" in recipes && "player.has(Skills.CRAFTING, 83, boostable = true)" in recipes)
    }
}
