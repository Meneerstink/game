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
        // 2026-09-14: the Magic experience now comes from the shared special attack experience (SpecialAttackXp -> the magic strategy's
        // damage experience, 0.2 Magic + 0.133 Hitpoints per damage point with the NPC multiplier, the values this plugin added by hand).
        assertTrue("addXp" !in script, "no hand-written experience left in the plugin")
        val shared = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/SpecialAttackXp.kt").readText()
        assertTrue("HitType.MAGIC -> MagicCombatStrategy.addCombatXp(player, target, damage, baseXp = 0.0)" in shared)
        val magic = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        assertTrue("val experience = baseXp + (modDamage * 0.2) * multiplier" in magic && "val hitpointsExperience = (modDamage * 0.133) * multiplier" in magic)
    }

    /**
     * OSRS-IMPORT audit round 2026-09-17b: the "voidwaker" batch imported the hilt/blade/gem but never wired their
     * assembly, so the finished weapon was spawn-only. OSRS Wiki "Voidwaker": assembled from all three pieces for
     * 500,000 coins (Madam Sikaro, absent from this cache - ADAPTED to a direct player combine, same pattern as the
     * crystal singing bowl's Grand Exchange placement).
     */
    @Test
    fun `the assembly plugin consumes all three pieces and 500,000 coins for the finished Voidwaker`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/voidwaker_assembly.plugin.kts").readText()
        assertTrue("ASSEMBLY_COST = 500_000" in script)
        listOf(
            "Items.VOIDWAKER_HILT to Items.VOIDWAKER_BLADE", "Items.VOIDWAKER_HILT to Items.VOIDWAKER_GEM", "Items.VOIDWAKER_BLADE to Items.VOIDWAKER_GEM",
        ).forEach { pair -> assertTrue(pair in script, "missing combine pair: $pair") }
        assertTrue("Item(Items.VOIDWAKER_HILT, 1)" in script && "Item(Items.VOIDWAKER_BLADE, 1)" in script && "Item(Items.VOIDWAKER_GEM, 1)" in script)
        assertTrue("Item(Items.COINS_995, ASSEMBLY_COST)" in script)
        assertTrue("player.grantOrRefund(Item(Items.VOIDWAKER), consumed)" in script)
    }
}
