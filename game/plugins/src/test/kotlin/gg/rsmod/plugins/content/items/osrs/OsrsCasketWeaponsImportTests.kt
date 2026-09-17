package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType
import gg.rsmod.plugins.content.skills.mining.PickaxeType
import gg.rsmod.plugins.content.skills.woodcutting.AxeType
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT casket sub-batch "casket-weapons-tools" against the OSRS Wiki item pages: the gilded axe / pickaxe are "cosmetic variant[s]"
 * of the rune tools, the 3rd Age axe / pickaxe share "the same cutting / mining speed, special attack" as the dragon tools, and the 3rd Age
 * bow "can fire arrows up to dragon arrows" with attack range 9.
 */
class OsrsCasketWeaponsImportTests {
    /**
     * OSRS-IMPORT audit round 2026-09-17b: the family's other two weapons (3rd Age longsword and 3rd Age wand) were
     * never enumerated by a test, only the axe/pickaxe/bow trio was. Independently re-verified against fresh OSRS
     * Wiki pages this round; no deviation found, this test only closes the coverage gap.
     */
    @Test
    fun `3rd Age longsword and wand match the OSRS item pages`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.THIRDAGE_BOW..Items.THIRDAGE_PICKAXE }.associateBy { it.path("id").asInt() }
        val sword = yml.getValue(Items.THIRDAGE_LONGSWORD).path("equipment")
        assertEquals(listOf(0, 72, 60), listOf("attack_stab", "attack_slash", "attack_crush").map { sword.path(it).asInt() })
        assertEquals(listOf(0, 3, 2), listOf("defence_stab", "defence_slash", "defence_crush").map { sword.path(it).asInt() })
        assertEquals(75, sword.path("melee_strength").asInt())
        assertEquals(5, sword.path("attack_speed").asInt())
        assertEquals(mapOf(0 to 65), sword.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })

        val wand = yml.getValue(Items.THIRDAGE_WAND).path("equipment")
        assertEquals(20, wand.path("attack_magic").asInt())
        assertEquals(20, wand.path("defence_magic").asInt())
        assertEquals(4, wand.path("attack_speed").asInt())
        assertEquals(1, wand.path("weapon_type").asInt(), "staff class, so it autocasts through the shared MagicStaves route")
        assertEquals(mapOf(6 to 65), wand.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
    }

    @Test
    fun `reward tools use the tier they are variants of`() {
        val gildedAxe = AxeType.values().single { it.item == Items.GILDED_AXE }
        val thirdAgeAxe = AxeType.values().single { it.item == Items.THIRDAGE_AXE }
        assertEquals(listOf(AxeType.RUNE.level, AxeType.RUNE.animation, AxeType.RUNE.ivyAnimation), listOf(gildedAxe.level, gildedAxe.animation, gildedAxe.ivyAnimation))
        assertEquals(AxeType.RUNE.ratio, gildedAxe.ratio)
        assertEquals(listOf(AxeType.DRAGON.level, AxeType.DRAGON.animation, AxeType.DRAGON.ivyAnimation), listOf(thirdAgeAxe.level, thirdAgeAxe.animation, thirdAgeAxe.ivyAnimation))
        assertEquals(AxeType.DRAGON.ratio, thirdAgeAxe.ratio)

        val gildedPickaxe = PickaxeType.values().single { it.item == Items.GILDED_PICKAXE }
        val thirdAgePickaxe = PickaxeType.values().single { it.item == Items.THIRDAGE_PICKAXE }
        assertEquals(listOf(PickaxeType.RUNE.level, PickaxeType.RUNE.animation, PickaxeType.RUNE.ticksBetweenRolls), listOf(gildedPickaxe.level, gildedPickaxe.animation, gildedPickaxe.ticksBetweenRolls))
        assertEquals(listOf(PickaxeType.DRAGON.level, PickaxeType.DRAGON.animation, PickaxeType.DRAGON.ticksBetweenRolls), listOf(thirdAgePickaxe.level, thirdAgePickaxe.animation, thirdAgePickaxe.ticksBetweenRolls))
    }

    @Test
    fun `3rd Age bow fires up to dragon arrows at range 9 and the tools share the dragon specials`() {
        val bow = BowType.values().single { it.item == Items.THIRDAGE_BOW }
        assertEquals(BowType.values().single { it.item == Items.DARK_BOW }.ammo.toSet(), bow.ammo.toSet())
        val ranged = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("Items.THIRDAGE_BOW -> 9" in ranged)
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/instant_specials.plugin.kts").readText()
        assertTrue("SpecialAttacks.registerInstant(100, Items.DRAGON_HATCHET, Items.THIRDAGE_AXE)" in specials)
        // Batch kits2 (2026-09-17) added both Dragon pickaxe (or) variants to the same Dragon pickaxe special registration.
        val pickaxeSpecial = specials.lines().single { "SpecialAttacks.registerInstant(100, Items.DRAGON_PICKAXE," in it }
        assertTrue("Items.THIRDAGE_PICKAXE" in pickaxeSpecial && "Items.DRAGON_PICKAXE_OR" in pickaxeSpecial, pickaxeSpecial)
    }
}
