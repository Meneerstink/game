package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.drops.DropTableFactory
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class ClueDropOwnershipTests {
    @Test fun `actual drop path suppresses an owned tier without changing other loot or casket eligibility`() {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val player = mockk<Player>(relaxed = true)
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { player.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { player.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { player.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        val tier = ClueScrollTier.EASY
        val fixture = Int.MAX_VALUE - 817 // isolated test registry key, never an NPC/cache allocation
        DropTableFactory.register({ guaranteed { obj(tier.scrollId); obj(995, quantity = 7) } }, fixture)
        fun loot() = DropTableFactory.getDrop(player, fixture)!!.associate { it.id to it.amount }
        assertEquals(mapOf(tier.scrollId to 1, 995 to 7), loot())
        player.deathRecovery.add(tier.scrollId)
        assertEquals(mapOf(995 to 7), loot())
        player.deathRecovery[0] = null
        player.inventory.add(tier.casketId)
        player.bank.add(ClueScrollTier.HARD.scrollId)
        assertEquals(mapOf(tier.scrollId to 1, 995 to 7), loot())
    }
}
