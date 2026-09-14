package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Q-043-e: Frozen key charges, Bob's recharge price and the kill-count drain message (pre-EoC RuneScape Wiki, see FrozenKey). */
class GodWarsFrozenKeyTests {
    @Test
    fun `recharge price is 49,200 at 1 Smithing to 10,000 at 99, 400 per level`() {
        assertEquals(49_200, FrozenKey.costPerCharge(1))
        assertEquals(10_000, FrozenKey.costPerCharge(99))
        (1 until 99).forEach { level -> assertEquals(400, FrozenKey.costPerCharge(level) - FrozenKey.costPerCharge(level + 1), "level $level") }
        assertEquals(0, FrozenKey.rechargeCost(5, 50))
        assertEquals(5 * 10_000, FrozenKey.rechargeCost(0, 99))
    }

    @Test
    fun `a key has five charges, loses them one by one and never goes below zero`() {
        val legacy = Item(Items.FROZEN_KEY_20120)
        assertEquals(5, FrozenKey.charges(legacy), "a key assembled before charges existed is full")
        var key = FrozenKey.withCharges(legacy, FrozenKey.MAX_CHARGES)
        repeat(5) { key = FrozenKey.withCharges(key, FrozenKey.charges(key) - 1) }
        assertEquals(0, FrozenKey.charges(key))
        assertEquals(Items.FROZEN_KEY_20120, key.id, "the key is not destroyed at the last charge (19 April 2011)")
        assertEquals(0, FrozenKey.charges(FrozenKey.withCharges(key, -1)))
        assertEquals(5, FrozenKey.charges(FrozenKey.withCharges(key, 9)))
        assertEquals(0, FrozenKey.charges(Item(Items.FROZEN_KEY_PIECE_BANDOS)))
    }

    @Test
    fun `door, assembly, Bob and the dungeon exit use the rule`() {
        val root = "src/main/kotlin/gg/rsmod/plugins/content"
        val dungeon = File("$root/areas/godwars/godwars_dungeon.plugin.kts").readText()
        val door = dungeon.substringAfter("on_obj_option(obj = Objs.FROZEN_DOOR").substringBefore("on_obj_option(")
        assertTrue("val keySlot = FrozenKey.chargedSlot(player)" in door)
        assertTrue("FrozenKey.withCharges(key, FrozenKey.charges(key) - 1)" in door)
        assertTrue("FROZEN_KEY_20120" !in door, "the door no longer accepts a key without charges")
        val exit = dungeon.substringAfter("on_exit_region(region)").substringBefore("on_login")
        assertTrue(exit.indexOf("GodWars.resetKillCounts(player)") in 0 until exit.indexOf("player.message(FrozenKey.MSG_KILLCOUNT_DRAINED)"))
        assertEquals("The power of all those you slew in the dungeon drains from your body.", FrozenKey.MSG_KILLCOUNT_DRAINED)
        val assembly = File("$root/npcs/definitions/godwars/godwars_frozen_key.plugin.kts").readText()
        // ItemContainer.add(Item) keeps only id + amount, so a new key relies on "no attribute = five charges".
        assertTrue("player.inventory.add(Items.FROZEN_KEY_20120)" in assembly && "FrozenKey.charges" in assembly)
        val bob = File("$root/areas/lumbridge/bobs_axes.plugin.kts").readText()
        assertTrue("if (FrozenKey.rechargeSlot(task.player) != null)" in bob && "FrozenKey.recharge(task)" in bob)
    }
}
