package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "noxious" (tx-20260913-234110): Noxious halberd, its pieces and passive venom against the wiki. */
class NoxiousHalberdTests {
    @Test
    fun `the halberd and its pieces match the item pages`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.NOXIOUS_HALBERD..Items.NOXIOUS_POMMEL }.associateBy { it.path("id").asInt() }
        val eq = yml.getValue(Items.NOXIOUS_HALBERD).path("equipment")
        assertEquals(listOf(80, 132, 0), listOf("attack_stab", "attack_slash", "attack_crush").map { eq.path(it).asInt() })
        assertEquals(142, eq.path("melee_strength").asInt())
        assertEquals(5, eq.path("attack_speed").asInt())
        assertEquals(5, eq.path("equip_type").asInt(), "two-handed")
        assertEquals(WeaponType.HALBERD.id, eq.path("weapon_type").asInt(), "halberd weapon type gives the 2-tile range")
        assertEquals(mapOf(0 to 80), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
        listOf(Items.NOXIOUS_POINT, Items.NOXIOUS_BLADE, Items.NOXIOUS_POMMEL).forEach { assertTrue(yml.getValue(it).path("equipment").isNull, "$it") }
    }

    @Test
    fun `the passive venom chance is wired into melee hits and Virulence is not invented`() {
        assertEquals(0.33, NoxiousHalberd.VENOM_CHANCE)
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MeleeCombatStrategy.kt").readText()
        assertTrue("NoxiousHalberd.rollVenom(pawn, target)" in strategy)
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons")
        assertFalse(specials.walkTopDown().filter { it.isFile }.any { "Items.NOXIOUS_HALBERD" in it.readText() }, "Virulence awaits the owner decision")
    }
}
