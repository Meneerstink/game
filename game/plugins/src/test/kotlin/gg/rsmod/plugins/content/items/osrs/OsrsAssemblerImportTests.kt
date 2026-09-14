package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices
import gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "assembler" (tx-20260913-231239): Ava's assembler (+ broken, (l)), Vorkath's head, the Ava's device
 * retrieval table and metallic interference. Expected values: OSRS Wiki "Ava's assembler" and "Ava's device".
 */
class OsrsAssemblerImportTests {
    private fun reqs(id: Int) = YML.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }

    @Test
    fun `assembler and its locked variant match the item page with 70 Ranged only`() {
        listOf(Items.AVAS_ASSEMBLER, Items.AVAS_ASSEMBLER_L).forEach { id ->
            val eq = YML.getValue(id).path("equipment")
            assertEquals(1, eq.path("equip_slot").asInt(), "$id cape slot")
            assertEquals(8, eq.path("attack_ranged").asInt())
            assertEquals(listOf(1, 1, 1, 8, 2), listOf("defence_stab", "defence_slash", "defence_crush", "defence_magic", "defence_ranged").map { eq.path(it).asInt() })
            assertEquals(2, eq.path("ranged_strength").asInt())
            assertEquals(mapOf(4 to 70), reqs(id), "$id: no Defence requirement on the wiki")
        }
        assertTrue(YML.getValue(Items.AVAS_ASSEMBLER_BROKEN).path("equipment").isNull)
        assertTrue(YML.getValue(Items.VORKATHS_HEAD).path("equipment").isNull)
    }

    @Test
    fun `metallic torsos from the wiki list interfere, compatible bodies and degraded forms are handled`() {
        listOf("Rune platebody", "Dragon chainbody", "Ahrim's robetop", "Dharok's platebody 100", "Verac's brassard 0").forEach {
            assertTrue(AvasDevices.interferes(it), it)
        }
        listOf("Bronze platebody", "Karil's leathertop", "Dragon chainbody (g)", "Bandos chestplate", "Void knight top", null).forEach {
            assertFalse(AvasDevices.interferes(it), "$it")
        }
        assertEquals(34, AvasDevices.INTERFERING_TORSOS.size)
    }

    @Test
    fun `retrieval rates, blowpipe darts and the PvP death rule follow the wiki`() {
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("device && pawn.hasEquipped(EquipmentType.CAPE, Items.AVAS_ATTRACTOR) -> chance in 20..39" in strategy)
        assertTrue("device && pawn.hasEquipped(EquipmentType.CAPE, Items.AVAS_ACCUMULATOR) -> chance in 20..27" in strategy)
        // Batch capes: every assembler (Masori assembler, assembler max capes, each (l)) shares the rule through AvasDevices.ASSEMBLERS.
        assertTrue("device && pawn.getEquipment(EquipmentType.CAPE)?.id in AvasDevices.ASSEMBLERS -> false" in strategy)
        assertTrue(Items.AVAS_ASSEMBLER in AvasDevices.ASSEMBLERS && Items.AVAS_ASSEMBLER_L in AvasDevices.ASSEMBLERS)
        assertTrue("val breakAmmo = chance in 0..19" in strategy, "20 % of ammunition breaks with every device")
        assertEquals(0.80, Blowpipe.DART_SAVE_CHANCE[Items.AVAS_ASSEMBLER])
        assertEquals(0.80, Blowpipe.DART_SAVE_CHANCE[Items.AVAS_ASSEMBLER_L])
        val breakable = PvpDeathBreakables.breakableFor(Items.AVAS_ASSEMBLER)!!
        assertEquals(Items.AVAS_ASSEMBLER_BROKEN, breakable.brokenId)
        assertEquals(0, breakable.killerCoins)
    }

    companion object {
        private lateinit var YML: Map<Int, JsonNode>

        @BeforeClass
        @JvmStatic
        fun load() {
            val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            YML = root.filter { it.path("id").asInt() >= Items.AVAS_ASSEMBLER }.associateBy { it.path("id").asInt() }
        }
    }
}
