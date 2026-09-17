package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType
import gg.rsmod.plugins.content.items.armor.DegradeTable
import gg.rsmod.plugins.content.items.armor.MoonArmour
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT step 4 batch "moons" against the OSRS Wiki moon equipment pages and the wiki DPS calculator. */
class OsrsMoonsImportTests {
    @Test
    fun `every moon armour piece degrades new - degraded - broken over 3000 points`() {
        assertEquals(9, MoonArmour.values().size)
        MoonArmour.values().forEach { piece ->
            assertEquals(piece.degradedId, DegradeTable.step(piece.newId, null, "piece")?.replaceWith, "$piece first combat tick")
            assertEquals(2_999 * 90 - 1, DegradeTable.step(piece.degradedId, null, "piece")?.charges, "$piece degraded points")
            assertEquals(piece.brokenId, DegradeTable.step(piece.degradedId, 1, "piece")?.replaceWith, "$piece breaks at 0")
        }
        assertEquals(MoonSets.MoonSet.values().flatMap { it.pieces }.toSet(), MoonArmour.values().toSet(), "every piece belongs to one set")
    }

    @Test
    fun `weapon values follow the wiki`() {
        assertEquals(12 to 13, MoonSets.macuahuitlSplit(25), "a max hit of 25 splits into 12 and 13")
        assertEquals(20 to 20, MoonSets.macuahuitlSplit(40))
        assertEquals(3 to 26, MoonSets.eclipseHit(20, 6), "burn 6 left: max +6, min +3")
        assertEquals(25 to 70, MoonSets.eclipseHit(20, 80), "burn bonus capped at 50")
        assertEquals(0, MoonSets.bloodInfusionSelfDamage(3), "no damage below 4 health")
        assertEquals(24, MoonSets.bloodInfusionSelfDamage(99))
        assertEquals(1.48, MoonSets.shacklesAccuracy(32), 1e-9)
        assertEquals(2.125, MoonSets.shacklesDamage(100), 1e-9, "damage boost capped at +112.5 %")
        assertEquals(3.25, MoonSets.shacklesAccuracy(150), 1e-9, "accuracy boost not capped")
        assertEquals(10, Burns.DAMAGE)
        assertEquals(4, Burns.INTERVAL_TICKS)
        assertEquals(5, Burns.MAX_STACKS)
        assertTrue(BowType.values.first { it.item == Items.ECLIPSE_ATLATL }.ammo.contentEquals(arrayOf(Items.ATLATL_DART)))
    }

    @Test
    fun `requirements and combat wiring`() {
        val yml = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.ECLIPSE_MOON_HELM..Items.BLUE_MOON_SPEAR_NOTED }.associateBy { it.path("id").asInt() }
        fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        assertEquals(mapOf(4 to 75, 0 to 50, 2 to 50), reqs(Items.ECLIPSE_ATLATL))
        assertEquals(mapOf(0 to 70, 2 to 75), reqs(Items.DUAL_MACUAHUITL))
        assertEquals(mapOf(0 to 70, 6 to 75), reqs(Items.BLUE_MOON_SPEAR))
        assertEquals(mapOf(1 to 50, 6 to 75), reqs(Items.BLUE_MOON_TASSETS_DEGRADED))
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/RangedCombatFormula.kt").readText()
        assertTrue("Skills.STRENGTH else Skills.RANGED" in formula && "pawn.getStrengthBonus().toDouble()" in formula && "TargetModifiers.equipmentMultiplier(player, target)" in formula)
        val melee = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MeleeCombatStrategy.kt").readText()
        assertTrue("val secondLand = firstLand &&" in melee && "MoonSets.rollBloodrager" in melee)
        assertTrue("MoonSets.BLOODRAGER" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/Combat.kt").readText())
        assertTrue("MoonSets.frostweaver(pawn, target, spell)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText())
        assertTrue("Burns.apply(target)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText())
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/moon_specials.plugin.kts").readText()
        assertTrue("Items.ECLIPSE_ATLATL" in specials && "Items.DUAL_MACUAHUITL" in specials && "Items.BLUE_MOON_SPEAR" in specials)
    }

    /**
     * OSRS-IMPORT audit round 2026-09-17b: the Eclipse atlatl's PvP speed used to be hardcoded to 3 for every PvP
     * attack. OSRS Wiki "Eclipse atlatl" (three independent sentences on the page, re-fetched this round): "During
     * player versus player combat, its base attack speed is 5, unless the full Eclipse armour set is worn" - only
     * then does it become 3. `CombatConfigs.getAttackDelay` gave speed 3 in PvP regardless of armour, an unearned
     * buff for anyone wielding the atlatl without the full set.
     */
    @Test
    fun `the atlatl's PvP speed is 5 without the full Eclipse set and 3 with it`() {
        assertEquals(5, MoonSets.ATLATL_PVP_SPEED_NO_SET)
        assertEquals(3, MoonSets.ATLATL_PVP_SPEED_FULL_SET)
        val configs = File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt").readText()
        assertTrue("speed = gg.rsmod.plugins.content.items.osrs.MoonSets.atlatlPvpSpeed(pawn)" in configs, "speed must be looked up per-attack, not hardcoded")
        assertTrue(
            "speed = gg.rsmod.plugins.content.items.osrs.MoonSets.ATLATL_PVP_SPEED" !in configs,
            "no lingering hardcoded single-value PvP-speed assignment",
        )
    }

    @Test
    fun `delayed burn damage stops for an offline player`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/Burns.kt").readText()
        assertTrue(
            "target.isDead() || (target is Player && !target.isOnline)" in source,
            "burn tick must not hit a logged-out player",
        )
        assertTrue(source.indexOf("target.isOnline") < source.indexOf("target.hit"))
    }
}
