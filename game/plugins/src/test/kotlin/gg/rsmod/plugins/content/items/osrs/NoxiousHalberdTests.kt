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
    fun `the passive venom chance and Virulence are wired into melee hits`() {
        assertEquals(0.33, NoxiousHalberd.VENOM_CHANCE)
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MeleeCombatStrategy.kt").readText()
        assertTrue("NoxiousHalberd.rollVenom(pawn, target)" in strategy)
        assertTrue("NoxiousHalberd.takeMinimum(pawn, landHit)" in strategy)
        assertTrue("maxOf(fangRange?.first ?: 0, virulenceMinimum)" in strategy)
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/noxious_halberd.plugin.kts").readText()
        assertTrue("SpecialAttacks.registerInstant(NoxiousHalberd.VIRULENCE_ENERGY, Items.NOXIOUS_HALBERD)" in script, "instant special, energy only on success")
        assertTrue("on_item_unequip(item = Items.NOXIOUS_HALBERD)" in script && "NoxiousHalberd.clearVirulence(player)" in script, "lost on weapon change")
        assertFalse(NoxiousHalberd.VIRULENCE_MINIMUM.persistenceKey != null, "lost on logout: not persisted")
    }

    @Test
    fun `Virulence uses the next poison or venom hit and stays armed until an accurate attack`() {
        assertEquals(50, NoxiousHalberd.VIRULENCE_ENERGY)
        assertEquals("You can only use this special attack whilst you are poisoned.", NoxiousHalberd.VIRULENCE_FAIL_MESSAGE)
        // Poison: next hit = ticksLeft / 5 + 1 (a fresh poison of 6 has 26 ticks left -> 6).
        assertEquals(6, gg.rsmod.plugins.content.mechanics.poison.Poison.getDamageForTicks(6 * 5 - 4))
        assertEquals(1, gg.rsmod.plugins.content.mechanics.poison.Poison.getDamageForTicks(0))
        // Venom: next hit 6, 8, ... capped at 20.
        assertEquals(listOf(6, 8, 20, 20), listOf(0, 1, 7, 50).map { gg.rsmod.plugins.content.mechanics.poison.Venom.damageForTick(it) })
        // A minimum above the max hit is clamped to the max hit by the damage roll.
        val random = kotlin.random.Random(1)
        repeat(50) { assertEquals(12, gg.rsmod.plugins.content.combat.rollDamage(16.0, 12.0, random)) }
        repeat(200) { assertTrue(gg.rsmod.plugins.content.combat.rollDamage(16.0, 40.0, random) in 16..40) }
    }
}
