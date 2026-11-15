package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.scrolls.ClueScrollTier
import gg.rsmod.plugins.content.scrolls.ClueContainers
import gg.rsmod.plugins.content.scrolls.ClueItems
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class WatsonClueTests {
    private val master = 19835 // source identity on a synthetic DefinitionSet, not an allocated local import ID
    private val box = 24366

    @BeforeTest fun mappings() {
        mockkObject(ClueItems)
        every { ClueItems.local(any()) } answers { callOriginal() }
        every { ClueItems.local(19835) } returns master
        every { ClueItems.local(24366) } returns box
    }
    @AfterTest fun cleanup() { unmockkObject(ClueItems) }
    private fun player(): Player {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { player.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { player.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { player.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        return player
    }

    private fun giveFour(player: Player) = WatsonClues.TIERS.forEach { player.inventory.add(it.scrollId) }

    @Test fun `partial hand ins persist and duplicate tiers are never consumed`() {
        val player = player()
        val easy = ClueScrollTier.EASY
        player.inventory.add(easy.scrollId)
        assertEquals(WatsonClues.Result.STORED, WatsonClues.offer(player, masterId = master))
        player.inventory.add(easy.scrollId)
        assertEquals(WatsonClues.Result.NOTHING_NEW, WatsonClues.offer(player, masterId = master))
        assertTrue(player.inventory.contains(easy.scrollId))
        assertEquals(setOf(easy), WatsonClues.stored(player))
        val saved = player.attr[WatsonClues.STORED]
        val second = player()
        second.attr[WatsonClues.STORED] = saved!!
        assertEquals(WatsonClues.stored(player), WatsonClues.stored(second))
    }

    @Test fun `four clues exchange exactly once even with full inventory`() {
        val player = player()
        giveFour(player)
        while (player.inventory.freeSlotCount > 0) player.inventory.add(995)
        assertEquals(WatsonClues.Result.MASTER_GIVEN, WatsonClues.offer(player, masterId = master))
        assertEquals(1, player.inventory.getItemCount(box))
        assertTrue(WatsonClues.stored(player).isEmpty())
        assertEquals(WatsonClues.Result.NOTHING_NEW, WatsonClues.offer(player, masterId = master))
        assertEquals(24, player.inventory.getItemCount(995))
    }

    @Test fun `capacity in bank grave or Death keeps all deposits until it is freed`() {
        for (which in 0..2) {
            val player = player()
            val container = listOf(player.bank, player.gravestone, player.deathRecovery)[which]
            container[0] = Item(box, 2)
            giveFour(player)
            assertEquals(WatsonClues.Result.MASTER_HELD, WatsonClues.offer(player, masterId = master))
            assertEquals(WatsonClues.TIERS.toSet(), WatsonClues.stored(player))
            container[0] = null
            assertEquals(WatsonClues.Result.MASTER_GIVEN, WatsonClues.offer(player, masterId = master))
            assertEquals(1, player.inventory.getItemCount(box))
        }
    }

    @Test fun `missing import never consumes clues and a second account has no shared deposits`() {
        val first = player(); giveFour(first)
        assertEquals(WatsonClues.Result.MISSING_IMPORT, WatsonClues.offer(first, boxId = null))
        assertEquals(4, first.inventory.occupiedSlotCount)
        assertTrue(WatsonClues.stored(first).isEmpty())
        WatsonClues.offer(first, setOf(ClueScrollTier.EASY), master)
        assertTrue(WatsonClues.stored(player()).isEmpty())
    }

    @Test fun `full collection inventory retains stored clues until a slot becomes available`() {
        val player = player()
        player.attr[WatsonClues.STORED] = 15
        repeat(28) { player.inventory[it] = Item(995) }
        assertEquals(WatsonClues.Result.NO_SPACE, WatsonClues.offer(player, masterId = master))
        assertEquals(WatsonClues.TIERS.toSet(), WatsonClues.stored(player))
        player.inventory[0] = null
        assertEquals(WatsonClues.Result.MASTER_GIVEN, WatsonClues.offer(player, masterId = master))
        assertEquals(1, player.inventory.getItemCount(box))
    }

    @Test fun `box unlock gives a box even while an active master is held but obeys combined capacity`() {
        val player = player()
        val box = 24366 // OSRS identity in synthetic definitions, not a local allocation
        mockkObject(ClueItems)
        try {
            every { ClueItems.local(19835) } returns master
            every { ClueItems.local(24366) } returns box
            player.inventory[0] = Item(master)
            giveFour(player)
            assertEquals(WatsonClues.Result.MASTER_GIVEN, WatsonClues.offer(player, masterId = master, boxId = box))
            assertEquals(1, player.inventory.getItemCount(box))
            assertEquals(1, player.inventory.getItemCount(master))
            giveFour(player)
            assertEquals(WatsonClues.Result.MASTER_HELD, WatsonClues.offer(player, masterId = master, boxId = box))
            assertEquals(1, player.inventory.getItemCount(box))
            assertEquals(WatsonClues.TIERS.toSet(), WatsonClues.stored(player))
            player.inventory[0] = null
            assertEquals(WatsonClues.Result.MASTER_GIVEN, WatsonClues.offer(player, masterId = master, boxId = box))
            assertEquals(2, player.inventory.getItemCount(box))
        } finally { unmockkObject(ClueItems) }
    }
}
