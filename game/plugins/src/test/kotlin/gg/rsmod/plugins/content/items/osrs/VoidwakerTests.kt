package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.DEFAULT_MIN_HIT
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "voidwaker" (tx-20260913-233456): Voidwaker stats, pieces and Disrupt against the wiki/calculator. */
class VoidwakerTests {
    @Test
    fun `voidwaker and its pieces match the item pages`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.VOIDWAKER..Items.VOIDWAKER_GEM_NOTED }.associateBy { it.path("id").asInt() }
        val eq = yml.getValue(Items.VOIDWAKER).path("equipment")
        assertEquals(listOf(70, 80, -2, 5, 0), listOf("attack_stab", "attack_slash", "attack_crush", "attack_magic", "attack_ranged").map { eq.path(it).asInt() })
        assertEquals(listOf(0, 1, 0, 2, 0), listOf("defence_stab", "defence_slash", "defence_crush", "defence_magic", "defence_ranged").map { eq.path(it).asInt() })
        assertEquals(80, eq.path("melee_strength").asInt())
        assertEquals(4, eq.path("attack_speed").asInt())
        assertEquals(6, eq.path("weapon_type").asInt())
        assertEquals(mapOf(0 to 75), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
        listOf(Items.VOIDWAKER_HILT, Items.VOIDWAKER_BLADE, Items.VOIDWAKER_GEM).forEach { assertTrue(yml.getValue(it).path("equipment").isNull, "$it") }
    }

    @Test
    fun `Disrupt rolls between half and one and a half times the melee max hit`() {
        assertEquals(20 to 60, Voidwaker.disruptRange(40.0))
        assertEquals(20 to 61, Voidwaker.disruptRange(41.0), "trunc(41 / 2) = 20, 41 + 20 = 61")
        assertEquals(0 to 1, Voidwaker.disruptRange(1.0))
        assertEquals(DEFAULT_MIN_HIT, Voidwaker.minHitArgument(0))
        assertEquals(50, Voidwaker.SPECIAL_ENERGY)
    }

    @Test
    fun `Disrupt is a guaranteed magic hit that grants Magic experience`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/voidwaker.plugin.kts").readText()
        assertTrue("SpecialAttacks.register(Voidwaker.SPECIAL_ENERGY, Items.VOIDWAKER)" in script)
        assertTrue("landHit = true" in script && "hitType = HitType.MAGIC" in script)
        assertTrue("player.addXp(Skills.MAGIC, counted * 0.2 * multiplier, checkBrawlingGloves = true)" in script)
    }
}
