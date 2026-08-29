package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.item.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for [DeathItemRiskCalculator]: the protected-item-count
 * rules (unskulled/skulled x Protect Item), combined inventory+equipment
 * ranking, stack-quantity preservation, and the [ItemRiskValueProvider]
 * abstraction actually driving the ranking (rather than a hardcoded field).
 */
class DeathItemRiskCalculatorTests {
    @Test
    fun `unskulled without protect item keeps the 3 highest value stacks`() {
        val inventory = inventoryOf(item(1, value = 10), item(2, value = 5), item(3, value = 20), item(4, value = 1))
        val result =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = emptyEquipment(),
                skulled = false,
                itemProtectionActive = false,
                valueProvider = valueProviderOf(),
            )

        assertEquals(3, result.protectedItemCount)
        assertEquals(setOf(3, 1, 2), result.protected.map { it.item.id }.toSet())
        assertEquals(setOf(4), result.lost.map { it.item.id }.toSet())
    }

    @Test
    fun `unskulled with active protect item keeps 4 stacks`() {
        val inventory = inventoryOf(item(1, value = 10), item(2, value = 5), item(3, value = 20), item(4, value = 1))
        val result =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = emptyEquipment(),
                skulled = false,
                itemProtectionActive = true,
                valueProvider = valueProviderOf(),
            )

        assertEquals(4, result.protectedItemCount)
        assertTrue(result.lost.isEmpty(), "all 4 carried stacks must be protected")
    }

    @Test
    fun `skulled without protect item keeps nothing`() {
        val inventory = inventoryOf(item(1, value = 10), item(2, value = 5))
        val result =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = emptyEquipment(),
                skulled = true,
                itemProtectionActive = false,
                valueProvider = valueProviderOf(),
            )

        assertEquals(0, result.protectedItemCount)
        assertTrue(result.protected.isEmpty())
        assertEquals(2, result.lost.size)
    }

    @Test
    fun `skulled with active protect item keeps only the single highest value stack`() {
        val inventory = inventoryOf(item(1, value = 10), item(2, value = 5), item(3, value = 20))
        val result =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = emptyEquipment(),
                skulled = true,
                itemProtectionActive = true,
                valueProvider = valueProviderOf(),
            )

        assertEquals(1, result.protectedItemCount)
        assertEquals(listOf(3), result.protected.map { it.item.id })
        assertEquals(setOf(1, 2), result.lost.map { it.item.id }.toSet())
    }

    @Test
    fun `protection ranks across inventory and equipment combined`() {
        val inventory = inventoryOf(item(1, value = 100), item(2, value = 1))
        val equipment = equipmentOf(item(3, value = 50), item(4, value = 2))
        // Dedicated provider for this test's own item ids/values (1=100, 2=1, 3=50, 4=2),
        // independent of the other tests' shared `valueProviderOf()` fixture.
        val values = mapOf(1 to 100L, 2 to 1L, 3 to 50L, 4 to 2L)

        val result =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = equipment,
                skulled = false,
                itemProtectionActive = false,
                valueProvider = ItemRiskValueProvider { values[it] ?: 0L },
            )

        // Top 3 by value across both containers: item 1 (100, inv), item 3 (50, equip),
        // item 4 (2, equip); item 2 (1, inv) is the lowest and is lost.
        assertEquals(3, result.protectedItemCount)
        assertEquals(setOf(1, 3, 4), result.protected.map { it.item.id }.toSet())
        assertEquals(setOf(2), result.lost.map { it.item.id }.toSet())

        val protectedFromEquipment = result.protected.first { it.item.id == 3 }
        assertEquals(DeathContainerSource.EQUIPMENT, protectedFromEquipment.source)
        val protectedFromInventory = result.protected.first { it.item.id == 1 }
        assertEquals(DeathContainerSource.INVENTORY, protectedFromInventory.source)
    }

    @Test
    fun `stack amount is preserved and ranked by total stack value, never split`() {
        // Item 10 is a cheap stackable (value 1/ea) but a huge stack, so its total
        // value (1 * 5000 = 5000) outranks a single expensive item worth 4000.
        val cheapBigStack = Item(10, 5000)
        val expensiveSingle = Item(20, 1)
        val inventory = arrayOfNulls<Item?>(28).also {
            it[0] = cheapBigStack
            it[1] = expensiveSingle
        }
        val values = mapOf(10 to 1L, 20 to 4000L)

        val result =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = emptyEquipment(),
                skulled = true,
                itemProtectionActive = true,
                valueProvider = ItemRiskValueProvider { values[it] ?: 0L },
            )

        assertEquals(1, result.protectedItemCount)
        val protected = result.protected.single()
        assertEquals(10, protected.item.id)
        assertEquals(5000, protected.item.amount, "the entire stack's original amount must be preserved, not split")

        val lost = result.lost.single()
        assertEquals(20, lost.item.id)
        assertEquals(1, lost.item.amount)
    }

    @Test
    fun `swapping the value provider changes which stacks are protected`() {
        val inventory = inventoryOf(item(1, value = 0), item(2, value = 0))
        val lowFirst = ItemRiskValueProvider { id -> if (id == 1) 1L else 100L }
        val highFirst = ItemRiskValueProvider { id -> if (id == 1) 100L else 1L }

        val resultA =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = emptyEquipment(),
                skulled = true,
                itemProtectionActive = true,
                valueProvider = lowFirst,
            )
        val resultB =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = emptyEquipment(),
                skulled = true,
                itemProtectionActive = true,
                valueProvider = highFirst,
            )

        assertEquals(2, resultA.protected.single().item.id, "item 2 must be kept when it's the higher-value provider result")
        assertEquals(1, resultB.protected.single().item.id, "item 1 must be kept once the provider ranks it higher instead")
    }

    /**
     * Builds a fixture [Item]. [value] is documentation only at call sites -
     * the actual value used for ranking always comes from whichever
     * [ItemRiskValueProvider] the test passes to [DeathItemRiskCalculator.calculate].
     */
    private fun item(
        id: Int,
        value: Long,
        amount: Int = 1,
    ): Item = Item(id, amount).also { require(value >= 0) }

    private fun inventoryOf(vararg items: Item): Array<Item?> =
        arrayOfNulls<Item?>(28).also { arr -> items.forEachIndexed { i, it -> arr[i] = it } }

    private fun equipmentOf(vararg items: Item): Array<Item?> =
        arrayOfNulls<Item?>(14).also { arr -> items.forEachIndexed { i, it -> arr[i] = it } }

    private fun emptyEquipment(): Array<Item?> = arrayOfNulls(14)

    /**
     * A fixture [ItemRiskValueProvider] matching the `value` passed to each
     * [item] fixture call above by convention (ids used in these tests are
     * mapped 1:1 to the value they were constructed with).
     */
    private fun valueProviderOf(): ItemRiskValueProvider =
        ItemRiskValueProvider { id ->
            when (id) {
                1 -> 10L
                2 -> 5L
                3 -> 20L
                4 -> 2L // "value = 1" fixture item plus +1 to disambiguate from item 2's total in the combined test
                else -> 0L
            }
        }
}
