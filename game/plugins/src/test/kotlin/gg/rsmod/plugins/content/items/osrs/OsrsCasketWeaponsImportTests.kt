package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType
import gg.rsmod.plugins.content.skills.mining.PickaxeType
import gg.rsmod.plugins.content.skills.woodcutting.AxeType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT casket sub-batch "casket-weapons-tools" against the OSRS Wiki item pages: the gilded axe / pickaxe are "cosmetic variant[s]"
 * of the rune tools, the 3rd Age axe / pickaxe share "the same cutting / mining speed, special attack" as the dragon tools, and the 3rd Age
 * bow "can fire arrows up to dragon arrows" with attack range 9.
 */
class OsrsCasketWeaponsImportTests {
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
        assertTrue("SpecialAttacks.registerInstant(100, Items.DRAGON_PICKAXE, Items.THIRDAGE_PICKAXE)" in specials)
    }
}
