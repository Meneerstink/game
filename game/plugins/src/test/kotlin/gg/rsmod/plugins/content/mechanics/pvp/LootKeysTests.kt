package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.death.DeathItemRiskCalculator
import gg.rsmod.plugins.content.mechanics.death.ItemRiskValueProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RCV-012 decision 3b: the sourced loot key rules (OSRS Wiki "Loot key") on [LootKeys.plan] and the death risk hook. */
class LootKeysTests {
    private val whip = Item(4151, 1)
    private val shark = Item(385, 3)
    private val food = { id: Int -> id == 385 }

    private fun plan(
        free: Int = 5,
        receives: Boolean = true,
        dying: Boolean = false,
        victimKeys: List<List<Item>> = emptyList(),
        loot: List<Item> = listOf(whip, shark),
        foodToFloor: Boolean = false,
    ) = LootKeys.plan(free, receives, dying, victimKeys, loot, foodToFloor, food)

    @Test
    fun `an enabled killer gets the loot as one key and nothing on the ground`() {
        val p = plan()
        assertEquals(listOf(listOf(whip, shark)), p.keysToGive)
        assertTrue(p.ground.isEmpty())
        assertFalse(p.limitReached)
    }

    @Test
    fun `food and potions can be sent to the floor, and no key is made when nothing else is left`() {
        assertEquals(listOf(listOf(whip)), plan(foodToFloor = true).keysToGive)
        assertEquals(listOf(shark), plan(foodToFloor = true).ground)
        val onlyFood = plan(foodToFloor = true, loot = listOf(shark))
        assertTrue(onlyFood.keysToGive.isEmpty())
        assertEquals(listOf(shark), onlyFood.ground)
    }

    @Test
    fun `no loot means no key, and keys switched off drop the loot`() {
        assertTrue(plan(loot = emptyList()).keysToGive.isEmpty())
        val off = plan(receives = false)
        assertTrue(off.keysToGive.isEmpty())
        assertEquals(listOf(whip, shark), off.ground)
    }

    @Test
    fun `a killer holding 5 keys gets the loot on the ground with the limit message`() {
        val p = plan(free = 0)
        assertTrue(p.keysToGive.isEmpty())
        assertEquals(listOf(whip, shark), p.ground)
        assertTrue(p.limitReached)
    }

    @Test
    fun `victim keys transfer even without the enchantment, over the limit they are deleted, loot key first`() {
        val five = List(5) { listOf(Item(995, it + 1)) }
        val withLoot = plan(free = 5, victimKeys = five)
        assertEquals(5, withLoot.keysToGive.size, "loot key + 4 victim keys")
        assertEquals(listOf(whip, shark), withLoot.keysToGive.first())
        assertEquals(1, withLoot.destroyedKeys, "a 5-key victim always costs one key")
        val disabled = plan(free = 5, receives = false, victimKeys = five)
        assertEquals(5, disabled.keysToGive.size)
        assertEquals(0, disabled.destroyedKeys)
    }

    @Test
    fun `a dying or dead killer destroys the victim keys and the loot drops`() {
        val p = plan(dying = true, victimKeys = listOf(listOf(whip)))
        assertTrue(p.keysToGive.isEmpty())
        assertEquals(1, p.destroyedKeys)
        assertEquals(listOf(whip, shark), p.ground)
    }

    @Test
    fun `destroying is refused in a dangerous area from 1,000,000 coins of value`() {
        assertTrue(LootKeys.canDestroyHere(999_999, inDangerousArea = true))
        assertFalse(LootKeys.canDestroyHere(1_000_000, inDangerousArea = true))
        assertTrue(LootKeys.canDestroyHere(5_000_000, inDangerousArea = false))
    }

    @Test
    fun `slot storage round-trips and the five key ids are the imported items`() {
        assertEquals(listOf(Items.LOOT_KEY, 23697, 23698, 23699, 23700), LootKeys.KEY_IDS.toList())
        // Item has identity equality: compare id/amount pairs.
        assertEquals(listOf(4151 to 1, 385 to 3), LootKeys.decode(LootKeys.encode(listOf(whip, shark))).map { it.id to it.amount })
        assertTrue(LootKeys.decode("").isEmpty())
    }

    @Test
    fun `loot keys are always lost on death, even for an unskulled player with Protect Item`() {
        val inventory = arrayOfNulls<Item>(28)
        inventory[0] = Item(Items.LOOT_KEY, 1)
        inventory[1] = whip
        val result =
            DeathItemRiskCalculator.calculate(
                inventory = inventory,
                equipment = arrayOfNulls(14),
                skulled = false,
                itemProtectionActive = true,
                valueProvider = ItemRiskValueProvider { 1 },
                alwaysLost = LootKeys::isKey,
            )
        assertEquals(listOf(Items.LOOT_KEY), result.lost.map { it.item.id })
        assertEquals(listOf(4151), result.protected.map { it.item.id })
    }
}
