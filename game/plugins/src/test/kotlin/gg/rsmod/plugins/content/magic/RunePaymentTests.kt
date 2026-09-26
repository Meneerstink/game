package gg.rsmod.plugins.content.magic

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.runecrafting.BindingNecklace
import gg.rsmod.plugins.content.skills.runecrafting.CombinationRune
import gg.rsmod.plugins.content.skills.runecrafting.RunecraftAction
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS import batch "runes" (2026-09-17): shared rune payment, combination runes, staves, rune pouches and combination crafting. */
class RunePaymentTests {
    private fun pay(
        required: Map<Int, Int>,
        carried: Map<Int, Int>,
        free: Set<Int> = emptySet(),
    ) = RunePayment.plan(required, { carried[it] ?: 0 }, { it in free })

    @Test
    fun `combination runes count as both elements and are used first`() {
        // "any spell requiring one water rune, one air rune, or both will spend only one mist rune"
        assertEquals(mapOf(Items.MIST_RUNE to 1, Items.MIND_RUNE to 1), pay(mapOf(Items.AIR_RUNE to 1, Items.MIND_RUNE to 1), mapOf(Items.MIST_RUNE to 5, Items.AIR_RUNE to 5, Items.MIND_RUNE to 1)))
        assertEquals(mapOf(Items.MIST_RUNE to 1), pay(mapOf(Items.AIR_RUNE to 1, Items.WATER_RUNE to 1), mapOf(Items.MIST_RUNE to 1)))
        // Fire Blast 4 fire + 3 air + 1 death with 3 smoke: 3 smoke cover 3 fire + 3 air, 1 fire and the death rune remain.
        assertEquals(
            mapOf(Items.SMOKE_RUNE to 3, Items.FIRE_RUNE to 1, Items.DEATH_RUNE to 1),
            pay(mapOf(Items.FIRE_RUNE to 4, Items.AIR_RUNE to 3, Items.DEATH_RUNE to 1), mapOf(Items.SMOKE_RUNE to 3, Items.FIRE_RUNE to 10, Items.DEATH_RUNE to 1)),
        )
        assertNull(pay(mapOf(Items.FIRE_RUNE to 4, Items.AIR_RUNE to 3), mapOf(Items.SMOKE_RUNE to 2, Items.FIRE_RUNE to 1, Items.AIR_RUNE to 5)), "4 fire needs 2 smoke + 2 fire")
        // Aether runes are removed (owner 2026-09-24): they pay for nothing.
        assertNull(pay(mapOf(Items.COSMIC_RUNE to 1, Items.SOUL_RUNE to 2), mapOf(Items.AETHER_RUNE to 2)))
        // A staff makes its element free before combinations are considered.
        assertEquals(mapOf(Items.SMOKE_RUNE to 1), pay(mapOf(Items.FIRE_RUNE to 5, Items.AIR_RUNE to 1), mapOf(Items.SMOKE_RUNE to 9), free = setOf(Items.FIRE_RUNE)))
        assertEquals(emptyMap(), pay(mapOf(Items.FIRE_RUNE to 5), emptyMap(), free = setOf(Items.FIRE_RUNE)))
        assertNull(pay(mapOf(Items.LAW_RUNE to 1), emptyMap()))
    }

    @Test
    fun `every elemental and combination staff supplies exactly its elements`() {
        fun supplied(staff: Int) = MagicStaves.values().filter { staff in it.staves }.map { it.runeId }.toSet()
        val air = Items.AIR_RUNE
        val water = Items.WATER_RUNE
        val earth = Items.EARTH_RUNE
        val fire = Items.FIRE_RUNE
        val expected =
            mapOf(
                Items.STAFF_OF_AIR to setOf(air), Items.AIR_BATTLESTAFF to setOf(air), Items.MYSTIC_AIR_STAFF to setOf(air),
                Items.STAFF_OF_WATER to setOf(water), Items.WATER_BATTLESTAFF to setOf(water), Items.MYSTIC_WATER_STAFF to setOf(water),
                Items.STAFF_OF_EARTH to setOf(earth), Items.EARTH_BATTLESTAFF to setOf(earth), Items.MYSTIC_EARTH_STAFF to setOf(earth),
                Items.STAFF_OF_FIRE to setOf(fire), Items.FIRE_BATTLESTAFF to setOf(fire), Items.MYSTIC_FIRE_STAFF to setOf(fire),
                Items.LAVA_BATTLESTAFF to setOf(earth, fire), Items.MYSTIC_LAVA_STAFF to setOf(earth, fire),
                Items.MUD_BATTLESTAFF to setOf(water, earth), Items.MYSTIC_MUD_STAFF to setOf(water, earth),
                Items.STEAM_BATTLESTAFF to setOf(water, fire), Items.MYSTIC_STEAM_STAFF to setOf(water, fire),
                Items.SMOKE_BATTLESTAFF to setOf(air, fire), Items.MYSTIC_SMOKE_STAFF to setOf(air, fire),
                Items.MIST_BATTLESTAFF to setOf(air, water), Items.MYSTIC_MIST_STAFF to setOf(air, water),
                Items.DUST_BATTLESTAFF to setOf(air, earth), Items.MYSTIC_DUST_STAFF to setOf(air, earth),
                Items.KODAI_WAND to setOf(water),
                // Batch kits: the (or) staves behave exactly like their base staves.
                Items.LAVA_BATTLESTAFF_OR to setOf(earth, fire), Items.STEAM_BATTLESTAFF_OR to setOf(water, fire),
                Items.MYSTIC_STEAM_STAFF_OR to setOf(water, fire),
            )
        expected.forEach { (staff, runes) -> assertEquals(runes, supplied(staff), "staff $staff") }
        assertEquals(expected.keys, MagicStaves.values().flatMap { it.staves.toList() }.toSet(), "no staff outside the sourced table")
    }

    @Test
    fun `rune pouch slots, capacity, runes only and divine fourth slot`() {
        var pouch = Item(Items.RUNE_POUCH)
        pouch = RunePouch.deposit(pouch, Items.DEATH_RUNE, 20_000)
        assertEquals(16_000, RunePouch.count(pouch, Items.DEATH_RUNE), "16,000 per rune")
        pouch = RunePouch.deposit(RunePouch.deposit(pouch, Items.BLOOD_RUNE, 10), Items.WATER_RUNE, 10)
        assertEquals(0, RunePouch.space(pouch, Items.SOUL_RUNE, 5), "three kinds in a rune pouch")
        assertEquals(0, RunePouch.space(Item(Items.RUNE_POUCH), Items.COINS_995, 5), "only runes")
        val divine = RunePouch.withContents(Item(Items.DIVINE_RUNE_POUCH), RunePouch.contents(pouch))
        assertEquals(5, RunePouch.space(divine, Items.SOUL_RUNE, 5), "a fourth kind in the divine pouch")
        val withdrawn = RunePouch.withdraw(pouch, Items.BLOOD_RUNE, 10)
        assertEquals(listOf(Items.DEATH_RUNE, Items.WATER_RUNE), RunePouch.contents(withdrawn).map { it.id }, "an emptied slot frees up")
        assertEquals(RunePouch.RUNES, RunePouch.RUNES.filter { it > 0 }.toSet())
        assertTrue(Items.WRATH_RUNE in RunePouch.RUNES && Items.MIST_RUNE in RunePouch.RUNES)
    }

    @Test
    fun `removed runes exist nowhere - pouch, combinations, crafting, spells`() {
        val removed = setOf(Items.ARMADYL_RUNE, Items.AETHER_RUNE, Items.AETHER_CATALYST)
        // Owner 2026-09-26: the Crown of Helios item is removed as well.
        assertEquals(removed + Items.CROWN_OF_HELIOS, gg.rsmod.plugins.content.mechanics.removed.RemovedItems.IDS)
        assertTrue(removed.none { it in RunePouch.RUNES })
        assertTrue(RunePayment.COMBINATIONS.none { it.first in removed })
        assertTrue(CombinationRune.values.none { it.id in removed || it.rune in removed })
        assertTrue(gg.rsmod.plugins.content.magic.SpellbookData.values().none { spell -> spell.runes.any { it.id in removed } })
        assertTrue(gg.rsmod.plugins.content.magic.SpellbookData.values().none { it.spellName == "Wind Rush" || it.spellName == "Storm of Armadyl" })
        assertTrue(gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.values.none { it.uniqueId == 3759 || it.uniqueId == 7699 })
    }

    @Test
    fun `combination success and binding necklace`() {
        var flip = false
        assertEquals(13, RunecraftAction.combinationSuccesses(26) { flip = !flip; flip }, "50 % per essence")
        assertEquals(0, RunecraftAction.combinationSuccesses(5) { false }, "a trip can fail completely")
        assertEquals(16, BindingNecklace.CHARGES)
    }

    @Test
    fun `every surge spell costs exactly its OSRS wrath rune price, no death or blood rune`() {
        // OWNER-BESLISSING wrath rune optie A (2026-09-17): sourced from OSRS Wiki raw wikitext per spell - Air Surge
        // Rush's line has no elemental catalyst beyond air, the other three add their tier's elemental rune; none of
        // the four ever cost a death or blood rune on OSRS, unlike the pre-fix 667 data this replaces.
        val expected =
            mapOf(
                SpellbookData.WIND_SURGE to mapOf(Items.AIR_RUNE to 7, Items.WRATH_RUNE to 1),
                SpellbookData.WATER_SURGE to mapOf(Items.AIR_RUNE to 7, Items.WATER_RUNE to 10, Items.WRATH_RUNE to 1),
                SpellbookData.EARTH_SURGE to mapOf(Items.AIR_RUNE to 7, Items.EARTH_RUNE to 10, Items.WRATH_RUNE to 1),
                SpellbookData.FIRE_SURGE to mapOf(Items.AIR_RUNE to 7, Items.FIRE_RUNE to 10, Items.WRATH_RUNE to 1),
            )
        expected.forEach { (spell, runes) ->
            val actual = spell.runes.groupBy { it.id }.mapValues { (_, stacks) -> stacks.sumOf { it.amount } }
            assertEquals(runes, actual, "${spell.name} rune cost")
            assertFalse(Items.DEATH_RUNE in actual, "${spell.name} must not cost a death rune")
            assertFalse(Items.BLOOD_RUNE in actual, "${spell.name} must not cost a blood rune")
        }
        // The wrath rune must actually flow through the shared RunePayment plan (combat-cast, autocast and manual
        // cast all call MagicSpells.runePlan -> RunePayment.plan with the spell's own SpellbookData.runes list).
        expected.forEach { (spell, runes) ->
            val plan = RunePayment.plan(runes, available = { runes[it] ?: 0 }, free = { false })
            assertEquals(runes, plan, "${spell.name} plan should consume exactly its own runes when nothing is free")
        }
    }

    @Test
    fun `every spell rune list uses the shared payment sources`() {
        // Unpowered orb (orb charging) and the banana of Ape Atoll Teleport (2 law, 2 fire, 2 water + 1 banana).
        val nonRuneRequirements = setOf(Items.UNPOWERED_ORB, Items.BANANA)
        for (spell in SpellbookData.values()) {
            val grouped = spell.runes.groupBy { it.id }.mapValues { (_, stacks) -> stacks.sumOf { it.amount } }
            assertTrue(
                grouped.keys.all { it in RunePouch.RUNES || it in nonRuneRequirements },
                "${spell.name} contains an unknown rune/item requirement: ${grouped.keys - RunePouch.RUNES - nonRuneRequirements}",
            )
            val runes = grouped.filterKeys { it in RunePouch.RUNES }
            assertEquals(
                runes,
                RunePayment.plan(runes, available = { runes[it] ?: 0 }, free = { false }),
                "${spell.name} must be payable from exact carried runes",
            )

            // Exercise every elemental/combination staff contract against every spell that uses its rune.
            for (staff in MagicStaves.values()) {
                if (staff.runeId !in runes) continue
                val plan = RunePayment.plan(runes, available = { if (it == staff.runeId) 0 else runes[it] ?: 0 }, free = { it == staff.runeId })
                assertNotNull(plan, "${spell.name} should accept ${staff.name}'s supplied rune")
                assertFalse(staff.runeId in plan.orEmpty(), "${spell.name} should not spend ${staff.name}'s supplied rune")
            }
        }
    }

    @Test
    fun `items yml, wiring and death routes`() {
        val yml =
            ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
                .filter { it.path("id").asInt() in Items.WRATH_RUNE..Items.MYSTIC_DUST_STAFF_NOTED }.associateBy { it.path("id").asInt() }
        assertEquals((Items.WRATH_RUNE..Items.MYSTIC_DUST_STAFF_NOTED).toSet(), yml.keys)
        fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        listOf(Items.SMOKE_BATTLESTAFF, Items.MIST_BATTLESTAFF, Items.DUST_BATTLESTAFF).forEach { assertEquals(mapOf(0 to 30, 6 to 30), reqs(it)) }
        listOf(Items.MYSTIC_MIST_STAFF, Items.MYSTIC_DUST_STAFF).forEach { assertEquals(mapOf(0 to 40, 6 to 40), reqs(it)) }
        assertFalse(yml.getValue(Items.RUNE_POUCH).path("tradeable").asBoolean())
        assertFalse(yml.getValue(Items.DIVINE_RUNE_POUCH).path("tradeable").asBoolean())
        assertTrue(yml.getValue(Items.WRATH_RUNE).path("tradeable").asBoolean())
        val spells = File("src/main/kotlin/gg/rsmod/plugins/content/magic/MagicSpells.kt").readText()
        assertEquals(2, Regex("runePlan\\(p, items\\)").findAll(spells).count(), "canCast and removeRunes share the plan")
        assertTrue("RunePouch.take(p, rune" in spells)
        // Owner 2026-09-26: the rune pouch follows the untradeable rule (fate + runes to the killer).
        val death = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/UntradeableDeathProtection.kt").readText()
        assertTrue(Regex("RunePouch.isPouch").findAll(death).count() >= 3, "handles, fate and killer loot")
        assertTrue("RunePouch.contents(outcome.slotItem.item)" in death, "the runes always go to the killer")
    }
}
