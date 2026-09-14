package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT step 4 batch "barks" against the OSRS Wiki Swampbark / Bloodbark pages. */
class OsrsBarksImportTests {
    @Test
    fun `bloodbark healing and swampbark bind values follow the wiki`() {
        assertEquals(35, BarkArmour.bloodHeal(100, 5, sceptreBoosted = false), "full set: 35 %")
        assertEquals(38, BarkArmour.bloodHeal(100, 5, sceptreBoosted = true), "with the Ancient sceptre: 38.5 %, rounded down")
        assertEquals(25, BarkArmour.bloodHeal(100, 0, sceptreBoosted = false), "no bloodbark: 25 %")
        assertEquals(27, BarkArmour.bloodHeal(100, 0, sceptreBoosted = true), "no bloodbark, sceptre: 27.5 % (unchanged rule)")
        assertEquals(11, BarkArmour.bloodHeal(35, 5, sceptreBoosted = false), "the quarter rounds down first: 35/4 = 8, x 35/25 = 11.2 -> 11 (not 35 % of 35 = 12)")
        assertEquals(2, BarkArmour.BIND_TICKS_PER_PIECE)
        assertEquals(3, BarkArmour.SWAMPBARK_BIND_PIECES.size, "helm, body and legs")
        assertEquals(5, BarkArmour.BLOODBARK_PIECES.size)
        assertEquals(10, BarkArmour.INFUSIONS.size)
        assertEquals(1_450, BarkArmour.INFUSIONS.filter { it.rune == Items.NATURE_RUNE }.sumOf { it.runes }, "a full swampbark set costs 1,450 nature runes")
        assertEquals(1_450, BarkArmour.INFUSIONS.filter { it.rune == Items.BLOOD_RUNE }.sumOf { it.runes })
    }

    @Test
    fun `requirements and wiring`() {
        val yml = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.SWAMPBARK_HELM..Items.BLOODBARK_BOOTS_NOTED }.associateBy { it.path("id").asInt() }
        fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        assertEquals(mapOf(6 to 50, 1 to 50), reqs(Items.SWAMPBARK_BODY))
        assertEquals(mapOf(6 to 60, 1 to 60), reqs(Items.BLOODBARK_BOOTS))
        assertEquals(1.0, yml.getValue(Items.BLOODBARK_LEGS).path("equipment").path("magic_damage").asDouble())
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        assertTrue("BarkArmour.bindBonus(pawn, spell)" in strategy && "bark.bloodHeal(damage, bark.bloodbarkPieces(pawn)" in strategy)
        assertTrue("Altar.NATURE.altar" in File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/bark_infusion.plugin.kts").readText())
    }
}
