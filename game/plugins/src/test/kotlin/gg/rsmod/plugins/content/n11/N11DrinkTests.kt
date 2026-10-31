package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.items.food.*
import io.mockk.every
import io.mockk.verify
import java.io.File
import kotlin.test.*

class N11DrinkTests {
    private val table get() = ConsumableTable.read(File("../../data/cfg/consumables.tsv"))

    @Test fun `all twenty full keg families consume four sips and leave a calquat keg through the real late route`() {
        N11Fixture().use { f ->
            f.loadConsumables()
            for (group in 0..19) {
                val full = 5777 + group * 8
                f.inventory[0] = Item(full)
                for (dose in 4 downTo 1) {
                    f.timers.remove(POTION_DELAY)
                    val id = full - (4 - dose) * 2
                    f.timers[ATTACK_DELAY] = 7
                    assertTrue(f.repo.executeItem(f.player, id, 1), "id=$id")
                    assertEquals(if (dose == 1) 5769 else id - 2, f.inventory[0]?.id)
                    assertEquals(2, f.timers[POTION_DELAY])
                    assertEquals(7, f.timers[ATTACK_DELAY], "drink does not delay combat")
                    f.repo.executeItem(f.player, f.inventory[0]!!.id, 1)
                    assertEquals(if (dose == 1) 5769 else id - 2, f.inventory[0]?.id, "rapid second sip refused")
                    assertFalse(f.repo.hasItemOption(id + 1, 1), "no noted drink binding")
                }
            }
            verify(exactly = 4) { f.player.alterLifepoints(6, 0) }
            verify(exactly = 4) { f.player.alterLifepoints(4, 0) }
        }
    }

    @Test fun `late table never overrides an existing handler and stale slots cannot drink another item`() {
        N11Fixture().use { f ->
            f.repo.bindItem(5777, 1) { }
            f.loadConsumables() // Would throw on a duplicate binding, as the old 5462 failure did.
            f.inventory[0] = Item(5778)
            f.inventory[1] = Item(5785)
            f.repo.executeItem(f.player, 5785, 1)
            assertEquals(5785, f.inventory[1]?.id)
            assertFalse(f.timers.has(POTION_DELAY))
            verify(exactly = 0) { f.player.alterLifepoints(any(), any()) }
        }
    }

    @Test fun `percentage drunken drains stack from current levels while boosts cap at base plus boost`() {
        N11Fixture().use { f ->
            val levels = levels(f, 99)
            GeDrinks.apply(f.player, 5777, false)
            assertEquals(100, levels[Skills.MINING])
            assertEquals(94, levels[Skills.ATTACK])
            GeDrinks.apply(f.player, 5775, false)
            assertEquals(100, levels[Skills.MINING])
            assertEquals(89, levels[Skills.ATTACK])
            GeDrinks.apply(f.player, 5857, false)
            assertEquals(101, levels[Skills.MINING])
            assertEquals(83, levels[Skills.ATTACK])
            levels[Skills.ATTACK] = 1
            GeDrinks.apply(f.player, 5857, false)
            assertEquals(1, levels[Skills.ATTACK])
        }
    }

    @Test fun `mature names and mind bomb threshold use item identity not display name`() {
        N11Fixture().use { f ->
            val levels = levels(f, 50)
            GeDrinks.apply(f.player, 5881, false)
            assertEquals(54, levels[Skills.MAGIC])
            assertEquals(46, levels[Skills.DEFENCE])
            GeDrinks.apply(f.player, 5913, false)
            assertEquals(54, levels[Skills.COOKING])
            GeDrinks.apply(f.player, 5897, false)
            assertEquals(46, levels[Skills.DEFENCE], "mead has no drunken drains")
            every { f.skills.getMaxLevel(Skills.MAGIC) } returns 49
            levels[Skills.MAGIC] = 49
            GeDrinks.apply(f.player, 5881, false)
            assertEquals(52, levels[Skills.MAGIC])
        }
    }

    @Test fun `every ale family matches the OSRS brewing formula matrix at level fifty`() {
        val boosts = listOf(mapOf(Skills.MINING to 51, Skills.SMITHING to 51), mapOf(Skills.STRENGTH to 52),
            mapOf(Skills.HERBLORE to 51), mapOf(Skills.MAGIC to 53), mapOf(Skills.STRENGTH to 52),
            emptyMap(), mapOf(Skills.WOODCUTTING to 51), mapOf(Skills.COOKING to 53),
            mapOf(Skills.SLAYER to 52), mapOf(Skills.FARMING to 51))
        val matureBoosts = listOf(mapOf(Skills.MINING to 52, Skills.SMITHING to 52), mapOf(Skills.STRENGTH to 53),
            mapOf(Skills.HERBLORE to 52), mapOf(Skills.MAGIC to 54), mapOf(Skills.STRENGTH to 53),
            emptyMap(), mapOf(Skills.WOODCUTTING to 52), mapOf(Skills.COOKING to 54),
            mapOf(Skills.SLAYER to 54), mapOf(Skills.FARMING to 52))
        val regularDrain = listOf(46, 46, 46, 47, 46, 50, 47, 46, 47, 47)
        val matureDrain = listOf(45, 45, 45, 46, 46, 50, 46, 45, 46, 46)
        N11Fixture().use { f ->
            for (group in 0..19) {
                val actual = levels(f, 50)
                val expected = (0..24).associateWith { 50 }.toMutableMap()
                val type = group % 10
                val mature = group >= 10
                expected.putAll((if (mature) matureBoosts else boosts)[type])
                val drained = when (type) {
                    0, 2, 3 -> listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE)
                    1, 4 -> listOf(Skills.ATTACK)
                    5 -> emptyList()
                    else -> listOf(Skills.ATTACK, Skills.STRENGTH)
                }
                drained.forEach { expected[it] = (if (mature) matureDrain else regularDrain)[type] }
                GeDrinks.apply(f.player, 5777 + group * 8, false)
                assertEquals(expected, actual, "full keg group=$group")
            }
        }
    }

    @Test fun `construction tea has three strengths leaves its own cup and never gives attack`() {
        N11Fixture().use { f ->
            f.loadConsumables()
            val current = levels(f, 50)
            listOf(7730, 7731, 7733, 7734, 7736, 7737).forEachIndexed { index, id ->
                current[Skills.CONSTRUCTION] = 50
                f.timers.remove(POTION_DELAY)
                f.inventory[0] = Item(id)
                assertTrue(f.repo.executeItem(f.player, id, 2))
                assertEquals(listOf(7728, 7728, 7732, 7732, 7735, 7735)[index], f.inventory[0]?.id)
                assertEquals(51 + index / 2, current[Skills.CONSTRUCTION])
                assertEquals(50, current[Skills.ATTACK])
            }
        }
    }

    @Test fun `milky nettle restores run only when injured and chalice leaves a cocktail glass`() {
        N11Fixture().use { f ->
            f.loadConsumables()
            every { f.player.getCurrentLifepoints() } returns 99
            every { f.player.runEnergy } returns 20.0
            f.inventory[0] = Item(4243)
            f.repo.executeItem(f.player, 4243, 2)
            assertEquals(1980, f.inventory[0]?.id)
            verify(exactly = 0) { f.player.runEnergy = any() }
            every { f.player.getCurrentLifepoints() } returns 98
            f.timers.remove(POTION_DELAY)
            f.inventory[0] = Item(4243)
            f.repo.executeItem(f.player, 4243, 2)
            verify { f.player.runEnergy = 25.0 }
            f.timers.remove(POTION_DELAY)
            f.inventory[0] = Item(197)
            f.repo.executeItem(f.player, 197, 1)
            assertEquals(2026, f.inventory[0]?.id)
            listOf(4245, 4246).forEach { id ->
                f.timers.remove(POTION_DELAY)
                f.inventory[0] = Item(id)
                assertTrue(f.repo.executeItem(f.player, id, 2))
                assertEquals(4244, f.inventory[0]?.id)
            }
        }
    }

    @Test fun `chalice enumerates the documented 32 outcomes and damage cannot reduce HP below four`() {
        N11Fixture().use { f ->
            levels(f, 100)
            every { f.player.getCurrentLifepoints() } returns 5
            every { f.world.random(1..3) } returns 2
            val hits = mutableListOf<Hit>()
            every { f.player.addHit(capture(hits)) } answers { Unit }
            (0..31).forEach { GeDrinks.chalice(f.player, it) }
            verify(exactly = 9) { f.player.alterLifepoints(15, 0) }
            verify(exactly = 4) { f.player.alterLifepoints(5, 0) }
            verify(exactly = 1) { f.player.alterLifepoints(30, 0) }
            assertEquals(0, GeDrinks.damage(50, 4))
            assertEquals(0, GeDrinks.damage(50, 1))
            assertEquals(1, GeDrinks.damage(50, 5))
            assertEquals(50, GeDrinks.damage(50, 100))
            assertEquals(3, hits.size)
            assertTrue(hits.all { it.hitmarks.single().damage == 1 })
            every { f.player.getCurrentLifepoints() } returns 4
            hits.forEach { hit ->
                @Suppress("UNCHECKED_CAST")
                val cancel = Hit::class.java.getDeclaredField("cancelCondition").apply { isAccessible = true }.get(hit) as () -> Boolean
                assertFalse(cancel())
                assertEquals(0, hit.hitmarks.single().damage, "damage recalculated after an intervening combat hit")
            }
        }
    }

    @Test fun `table has exactly one row per id and never restores a removed potion`() {
        assertEquals(table.size, table.map { it.id }.toSet().size)
        assertTrue(table.none { it.id in gg.rsmod.plugins.content.items.potion.RemovedPotions.itemIds })
    }

    private fun levels(f: N11Fixture, base: Int): MutableMap<Int, Int> {
        val levels = (0..24).associateWith { base }.toMutableMap()
        every { f.skills.getMaxLevel(any()) } returns base
        every { f.skills.getCurrentLevel(any()) } answers { levels[firstArg()]!! }
        every { f.skills.setCurrentLevel(any(), any()) } answers { levels[firstArg()] = secondArg(); Unit }
        return levels
    }
}
