package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.*
import kotlin.test.*

class ToaRewardTransferTests {
    @Test fun `full inventory retains escrow and inventory bank reconnect conserve the actual items`() {
        val defs = mockk<DefinitionSet>()
        every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg<Int>()).apply { stacks = id == 995 } }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        val inventory = ItemContainer(defs, 1, ContainerStackType.NORMAL)
        val bank = ItemContainer(defs, 2, ContainerStackType.STACK)
        every { player.inventory } returns inventory
        every { player.bank } returns bank
        inventory.add(100, 1)
        player.attr[ToaRewards.ESCROW] = ToaPersonalReward("raid", listOf(ToaRewardItem(995, 50), ToaRewardItem(200, 2))).encode()
        ToaRewards.take(player)
        assertEquals(2, ToaRewards.stored(player)!!.items.size)
        assertEquals(1, inventory.getItemCount(100))
        inventory[0] = null
        ToaRewards.take(player)
        assertEquals(50, inventory.getItemCount(995))
        assertEquals(listOf(ToaRewardItem(200, 2)), ToaRewards.stored(player)!!.items)
        // Read the serialized player attribute as a reconnect would, then take the rest through the other route.
        player.attr[ToaRewards.ESCROW] = ToaPersonalReward.decode(player.attr[ToaRewards.ESCROW]!!).encode()
        ToaRewards.take(player, toBank = true)
        ToaRewards.take(player, toBank = true)
        assertEquals(2, bank.getItemCount(200))
        assertEquals(50, inventory.getItemCount(995))
        assertFalse(ToaRewards.hasUnclaimed(player))
    }
}
