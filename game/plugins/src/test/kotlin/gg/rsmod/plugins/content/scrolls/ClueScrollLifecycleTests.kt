package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class ClueScrollLifecycleTests {
    private fun player(): Player {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()).also { it.stacks = false } }
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { player.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { player.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { player.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        return player
    }

    companion object {
        init {
            // A successful action exercises the real completion/replacement path.
            ClueScrollManager.registerStep(object : ClueStep {
                override val tier = ClueScrollTier.EASY
                override val hint = "lifecycle test step"
                override val mapInterfaceId = 337
                override fun onAttempt(player: Player) = true
            })
        }
    }

    @Test fun `a casket does not block a clue but inventory bank grave and Death do`() {
        val player = player()
        val tier = ClueScrollTier.EASY
        player.inventory[0] = Item(tier.casketId)
        assertTrue(ClueOwnership.mayReceive(player, tier.scrollId))
        for (container in listOf(player.inventory, player.bank, player.gravestone, player.deathRecovery)) {
            val slot = container.nextFreeSlot
            container[slot] = Item(tier.scrollId)
            assertFalse(ClueOwnership.mayReceive(player, tier.scrollId))
            assertTrue(ClueOwnership.mayReceive(player, ClueScrollTier.HARD.scrollId))
            container[slot] = null
        }
    }

    @Test fun `full inventory completes within OSRS length and cannot give a second casket`() {
        val player = player()
        val tier = ClueScrollTier.EASY
        val scroll = Item(tier.scrollId)
        player.inventory[0] = scroll
        (1..27).forEach { player.inventory[it] = Item(995, 1) }
        ClueScrollManager.prepare(player, scroll, tier)
        var completed = 0
        while (player.inventory.contains(tier.scrollId) && completed < 5) {
            assertTrue(ClueScrollManager.tryDig(player)); completed++
        }
        assertTrue(completed in 2..4)
        assertEquals(1, player.inventory.getItemCount(tier.casketId))
        assertFalse(ClueScrollManager.tryDig(player))
        assertEquals(27, player.inventory.getItemCount(995))
    }

    @Test fun `dropping and re picking a scroll preserves its step and tier progress`() {
        val player = player()
        val tier = ClueScrollTier.EASY
        val scroll = Item(tier.scrollId)
        player.inventory[0] = scroll
        ClueScrollManager.prepare(player, scroll, tier)
        assertTrue(ClueScrollManager.tryDig(player)) // easy always requires at least two
        val key = scroll.getAttr(ItemAttribute.CLUE_STEP_KEY)
        player.inventory[0] = null
        val restored = Item(scroll) // same path used by container copies/ground attrs
        player.inventory[0] = restored
        ClueScrollManager.prepare(player, restored, tier)
        assertEquals(key, restored.getAttr(ItemAttribute.CLUE_STEP_KEY))
        assertEquals(1, ClueScrollManager.progress(player, tier))
        player.inventory[0] = Item(tier.scrollId)
        ClueScrollManager.prepare(player, player.inventory[0]!!, tier)
        assertEquals(0, ClueScrollManager.progress(player, tier))
    }
}
