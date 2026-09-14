package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationData
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "ancientgs" (tx-20260913-234640): Ancient godsword, hilt, creation and Blood Sacrifice against the wiki. */
class AncientGodswordTests {
    @Test
    fun `the godsword and hilt match the item pages`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.ANCIENT_GODSWORD..Items.ANCIENT_HILT_NOTED }.associateBy { it.path("id").asInt() }
        val eq = yml.getValue(Items.ANCIENT_GODSWORD).path("equipment")
        assertEquals(listOf(0, 132, 80), listOf("attack_stab", "attack_slash", "attack_crush").map { eq.path(it).asInt() })
        assertEquals(132, eq.path("melee_strength").asInt())
        assertEquals(8, eq.path("prayer").asInt())
        assertEquals(6, eq.path("attack_speed").asInt())
        assertEquals(5, eq.path("equip_type").asInt(), "two-handed")
        assertEquals(7, eq.path("weapon_type").asInt())
        assertEquals(mapOf(0 to 75), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
        assertTrue(yml.getValue(Items.ANCIENT_HILT).path("equipment").isNull)
    }

    @Test
    fun `the hilt attaches to the godsword blade and dismantling gives it back`() {
        val combination = CombinationData.ANCIENT_GODSWORD
        assertEquals(setOf(Items.ANCIENT_HILT, Items.GODSWORD_BLADE), combination.items.toSet())
        assertEquals(Items.ANCIENT_GODSWORD, combination.resultItem)
        val dismantle = File("src/main/kotlin/gg/rsmod/plugins/content/items/godsword.plugin.kts").readText()
        assertTrue("Items.ANCIENT_GODSWORD to Items.ANCIENT_HILT" in dismantle)
    }

    @Test
    fun `Blood Sacrifice constants and heal caps follow the wiki`() {
        assertEquals(50, AncientGodsword.SPECIAL_ENERGY)
        assertEquals(2.0, AncientGodsword.SPECIAL_ACCURACY)
        assertEquals(1.1, AncientGodsword.SPECIAL_DAMAGE)
        assertEquals(8, AncientGodsword.MARK_TICKS)
        assertEquals(5, AncientGodsword.ESCAPE_DISTANCE)
        assertEquals(25, AncientGodsword.SACRIFICE_DAMAGE)
        assertEquals(25, AncientGodsword.heal(targetMaxHitpoints = 255, targetIsPlayer = false, dealt = 25), "NPC cap 25")
        assertEquals(14, AncientGodsword.heal(targetMaxHitpoints = 99, targetIsPlayer = false, dealt = 25), "floor(15% of 99) = 14, below the NPC cap")
        assertEquals(14, AncientGodsword.heal(targetMaxHitpoints = 99, targetIsPlayer = true, dealt = 25), "wiki: capped at 14 for players since max hitpoints is 99")
        assertEquals(10, AncientGodsword.heal(targetMaxHitpoints = 255, targetIsPlayer = false, dealt = 10), "wiki: only healed for the damage done by the sacrifice")
        assertEquals(14, AncientGodsword.heal(targetMaxHitpoints = 99, targetIsPlayer = true, dealt = 20), "damage done above 15% still heals at most 15%")
        assertEquals(0, AncientGodsword.heal(targetMaxHitpoints = 99, targetIsPlayer = true, dealt = 0))
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/ancient_godsword.plugin.kts").readText()
        assertTrue("SpecialAttacks.register(AncientGodsword.SPECIAL_ENERGY, Items.ANCIENT_GODSWORD)" in script)
        assertTrue("wait(AncientGodsword.MARK_TICKS)" in script && "if (!landHit) return@register" in script)
    }
}
