package gg.rsmod.plugins.content.items.food

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.items.potion.PotionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-010 A2 (owner live 2026-09-13: HP rises above the maximum) and B1 (x10 -> 1:1 migration).
 *
 * Shared path: every food/drink heal ends in `Player.alterLifepoints(value, capValue)`, where lifepoints
 * are 1:1 and capValue is an allowance ABOVE the maximum. The food table stayed on the x10 unit (shark 200)
 * and callers must pass a deliberate, bounded allowance so HP cannot go far above the maximum.
 */
class FoodAndDrinkLifepointUnitTests {
    private fun player(maxLevel: Int = 99): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val skills = mockk<SkillSet>(relaxed = true)
        every { world.definitions.get(ItemDef::class.java, any<Int>()) } returns mockk<ItemDef>(relaxed = true)
        every { player.world } returns world
        every { player.timers } returns TimerMap()
        every { player.skills } returns skills
        every { skills.getMaxLevel(any()) } returns maxLevel
        every { skills.getCurrentLevel(any()) } returns maxLevel
        return player
    }

    /** Dungeoneering foods carry an unsourced "rs3 wiki" figure (see Food.kt); they are not x10-clean. */
    private val unsourcedDungeoneeringFoods = File("src/main/kotlin/gg/rsmod/plugins/content/items/food/Food.kt")
        .readText().substringAfter("Dungeoneering Food").let { tail ->
            Regex("""^\s+([A-Z0-9_]+)\(""", RegexOption.MULTILINE).findAll(tail).map { it.groupValues[1] }.toSet()
        }

    @Test
    fun `every sourced food heal is on the x10 ledger unit`() {
        assertTrue(unsourcedDungeoneeringFoods.contains("CAVE_MORAY"), "Dungeoneering food block not found")
        val offenders = Food.values
            .filter { it.name !in unsourcedDungeoneeringFoods && it != Food.KEBAB && it != Food.STRANGE_FRUIT && it != Food.ANGLERFISH }
            .filter { it.heal <= 0 || it.heal % Food.LEDGER_UNITS_PER_HITPOINT != 0 }
            .map { "${it.name} heal=${it.heal}" }
        assertTrue(offenders.isEmpty(), "food heal not on the x10 ledger unit:\n" + offenders.joinToString("\n"))
        // OSRS Wiki "Strange fruit": "No Hitpoints are restored upon eating the fruit" (it cures poison/venom and restores run energy).
        assertEquals(0, Food.STRANGE_FRUIT.heal)
        assertEquals(22, Foods.anglerfishHeal(99))
    }

    @Test
    fun `every food heals one tenth of its ledger value and only anglerfish may overheal`() {
        // Strange fruit heals nothing (OSRS Wiki) - asserted in the ledger test above.
        Food.values.filter { it != Food.KEBAB && it != Food.STRANGE_FRUIT }.forEach { food ->
            val p = player()
            Foods.eat(p, food)
            val expectedHeal = if (food == Food.ANGLERFISH) Foods.anglerfishHeal(99) else food.heal / 10
            val cap = if (food == Food.ANGLERFISH) expectedHeal else 0
            verify(exactly = 1) { p.alterLifepoints(expectedHeal, cap) }
        }
        assertEquals(20, Food.SHARK.hitpoints)
        assertEquals(22, Foods.anglerfishHeal(99))
    }

    @Test
    fun `constitution drinks heal the lifepoints once and only the brew may overheal`() {
        PotionType.values().filter { Skills.CONSTITUTION in it.alteredSkills }.forEach { type ->
            val p = player()
            val skills = p.skills
            type.apply(p)
            verify(exactly = 0) { skills.alterCurrentLevel(Skills.CONSTITUTION, any(), any()) }
            if (type == PotionType.SARADOMIN_BREW) {
                // Novite: heal(15% of max + 20, 15% of max) on x10 -> 1:1 floor(15% of 99) + 2 = 16 with allowance 16.
                verify(exactly = 1) { p.alterLifepoints(16, 16) }
            } else {
                verify(exactly = 1) { p.alterLifepoints(any(), 0) }
            }
        }
    }
}
