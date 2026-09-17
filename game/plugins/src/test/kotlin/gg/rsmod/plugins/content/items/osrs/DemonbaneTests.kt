package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OSRS import batch "demonbane" against the OSRS Wiki item pages and the wiki DPS calculator (2026-09-17). */
class DemonbaneTests {
    @Test
    fun `items yml carries the wiki stats, requirements and tradeability of every imported definition`() {
        val yml =
            ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
                .filter { it.path("id").asInt() in Items.ARCLIGHT..Items.BURNING_CLAW_NOTED }.associateBy { it.path("id").asInt() }
        assertEquals((Items.ARCLIGHT..Items.BURNING_CLAW_NOTED).toSet(), yml.keys, "every definition of the batch has items.yml metadata")
        fun eq(id: Int) = yml.getValue(id).path("equipment")
        fun stats(id: Int) = listOf("attack_stab", "attack_slash", "attack_crush", "defence_stab", "defence_slash", "defence_crush", "defence_magic", "melee_strength", "attack_speed").map { eq(id).path(it).asInt() }
        fun reqs(id: Int) = eq(id).path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        assertEquals(listOf(10, 38, 0, 0, 3, 2, 2, 8, 4), stats(Items.ARCLIGHT))
        assertEquals(listOf(10, 16, -2, 0, 3, 2, 2, 13, 5), stats(Items.ARCLIGHT_INACTIVE), "the inactive sword functions identically to Darklight")
        assertEquals(listOf(63, 70, 0, 0, 3, 2, 5, 13, 4), stats(Items.EMBERLIGHT))
        assertEquals(listOf(43, 54, 0, 3, 6, 1, 0, 32, 4), stats(Items.BURNING_CLAWS))
        assertEquals(mapOf(0 to 75), reqs(Items.ARCLIGHT))
        assertEquals(mapOf(0 to 77), reqs(Items.EMBERLIGHT))
        assertEquals(mapOf(0 to 60), reqs(Items.BURNING_CLAWS))
        assertEquals(5, eq(Items.BURNING_CLAWS).path("equip_type").asInt(), "two-handed")
        assertFalse(yml.getValue(Items.ARCLIGHT).path("tradeable").asBoolean())
        assertFalse(yml.getValue(Items.EMBERLIGHT).path("tradeable").asBoolean())
        assertTrue(yml.getValue(Items.BURNING_CLAWS).path("tradeable").asBoolean())
        assertTrue(yml.getValue(Items.BURNING_CLAW).path("tradeable").asBoolean())
    }

    @Test
    fun `arclight charges, shards, cap, inactive state and infusion`() {
        assertEquals(1_000, Demonbane.charges(Demonbane.created()))
        assertEquals(333, Demonbane.chargesForShards(1))
        assertEquals(1_000, Demonbane.chargesForShards(3))
        assertEquals(9_000, Demonbane.chargesForShards(27), "30 shards in total: 3 to create + 27 = 10,000")
        assertEquals(27, Demonbane.shardsToUse(Demonbane.created(), 40))
        assertEquals(0, Demonbane.shardsToUse(Demonbane.arclight(10_000, 0), 5), "a full sword uses no shards")
        assertEquals(10_000, Demonbane.charges(Demonbane.addShards(Demonbane.arclight(9_900, 0), 1)), "capped at 10,000")
        val spent = Demonbane.arclight(0, 700)
        assertEquals(Items.ARCLIGHT_INACTIVE, spent.id, "0 charges -> inactive variant")
        assertEquals(7, Demonbane.infusionPercent(spent), "infusion is kept")
        val recharged = Demonbane.addShards(spent, 3)
        assertEquals(Items.ARCLIGHT, recharged.id)
        assertEquals(1_000, Demonbane.charges(recharged))
        assertEquals(700, Demonbane.infusion(recharged))
        assertTrue(Demonbane.canUpgrade(Demonbane.arclight(7_000, 3_000)), "7,000 charges and 30 % infusion")
        assertFalse(Demonbane.canUpgrade(Demonbane.arclight(7_000, 2_900)))
        assertFalse(Demonbane.canUpgrade(Item(Items.DARKLIGHT)))
    }

    @Test
    fun `weaken drains base level percentages plus one`() {
        assertEquals(5, Weaken.drainAmount(99, Items.DARKLIGHT, demon = false))
        assertEquals(10, Weaken.drainAmount(99, Items.ARCLIGHT, demon = true))
        assertEquals(15, Weaken.drainAmount(99, Items.EMBERLIGHT, demon = true))
        assertEquals(5, Weaken.drainAmount(99, Items.EMBERLIGHT, demon = false))
        assertEquals(1, Weaken.drainAmount(1, Items.ARCLIGHT_INACTIVE, demon = false))
    }

    @Test
    fun `burning barrage hitsplats follow the calculator distribution`() {
        assertEquals(listOf(20, 10, 10), BurningClaws.split(0, 40))
        assertEquals(listOf(19, 19, 2), BurningClaws.split(1, 40))
        assertEquals(listOf(38, 1, 1), BurningClaws.split(2, 40))
        repeat(2_000) {
            val random = Random(it)
            var rolls = 0
            val landOn = it % 4
            val barrage = BurningClaws.barrage(40, { rolls++ == landOn }, random)
            if (landOn == 3) {
                assertEquals(-1, barrage.successfulRoll)
                assertTrue(barrage.hitsplats in listOf(listOf(0, 0, 0), listOf(1, 0, 0), listOf(1, 1, 0)))
                assertEquals(0.0, barrage.burnChance)
            } else {
                val low = 40 * (3 - landOn) / 4
                assertTrue(barrage.hitsplats.sum() in (low - 2)..(40 + low), "roll $landOn total ${barrage.hitsplats}")
                assertEquals(0.15 * (landOn + 1), barrage.burnChance, 1e-9)
            }
        }
    }

    @Test
    fun `slice and dice follows the calculator distribution`() {
        repeat(2_000) {
            val random = Random(it)
            var rolls = 0
            val landOn = it % 5
            val splats = DragonClaws.sliceAndDice(40, { rolls++ == landOn }, random)
            when (landOn) {
                0 -> assertTrue(splats.sum() in 40..(80 - 1 + 1) && splats[0] >= splats[1] && splats[3] == splats[2] + 1, "$splats")
                1 -> assertTrue(splats[0] == 0 && splats[3] == splats[2] + 1 && splats[1] >= 15, "$splats")
                2 -> assertTrue(splats[0] == 0 && splats[1] == 0 && splats[3] == splats[2] + 1 && splats[2] >= 10, "$splats")
                3 -> assertTrue(splats.take(3) == listOf(0, 0, 0) && splats[3] in 11..50, "$splats")
                else -> assertTrue(splats in listOf(listOf(1, 1, 0, 0), listOf(0, 0, 0, 0)), "$splats")
            }
        }
    }

    @Test
    fun `combat wiring uses the shared routes`() {
        val targetModifiers = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/TargetModifiers.kt").readText()
        assertTrue("Demonbane.meleePercent(player, target)" in targetModifiers)
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MeleeCombatFormula.kt").readText()
        assertEquals(2, Regex("TargetModifiers.meleeDemonbanePercent").findAll(formula).count(), "accuracy and max hit")
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MeleeCombatStrategy.kt").readText()
        assertTrue("Demonbane.afterSuccessfulHit(pawn)" in strategy)
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/melee_specials.plugin.kts").readText()
        assertTrue("Items.DARKLIGHT, Items.ARCLIGHT, Items.ARCLIGHT_INACTIVE, Items.EMBERLIGHT" in specials && "StyleType.STAB" in specials)
        val claws = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/dragonequipment/dragon_claws.plugin.kts").readText()
        assertTrue("Items.BURNING_CLAWS" in claws && "Burns.apply(target)" in claws && "StyleType.SLASH" in claws)
        val death = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/PvpDeathBreakables.kt").readText()
        assertEquals(2, Regex("Demonbane.SYNAPSE_PRODUCTS").findAll(death).count(), "split and execute")
        assertEquals(setOf(Items.EMBERLIGHT, Items.SCORCHING_BOW, Items.PURGING_STAFF), Demonbane.SYNAPSE_PRODUCTS.keys)
        assertEquals(70, Demonbane.ARCLIGHT_PERCENT)
        assertEquals(60, Demonbane.DARKLIGHT_PERCENT)
        assertEquals(5, Demonbane.BURNING_CLAWS_PERCENT)
    }
}
