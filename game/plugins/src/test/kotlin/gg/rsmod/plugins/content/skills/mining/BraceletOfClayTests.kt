package gg.rsmod.plugins.content.skills.mining

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** K12 N430: a worn Bracelet of clay turns 28 mined clay into soft clay, then crumbles. */
class BraceletOfClayTests {
    @Test
    fun `without the bracelet clay stays clay`() {
        assertEquals(Items.CLAY, BraceletOfClay.reward(player(bracelet = false)))
    }

    @Test
    fun `the bracelet gives soft clay for 28 clay and then crumbles`() {
        val player = player(bracelet = true)
        assertEquals("Your bracelet has 28 charges remaining.", BraceletOfClay.checkMessage(player))
        repeat(27) { assertEquals(Items.SOFT_CLAY, BraceletOfClay.reward(player)) }
        assertEquals("Your bracelet has 1 charges remaining.", BraceletOfClay.checkMessage(player))
        assertEquals(Items.SOFT_CLAY, BraceletOfClay.reward(player), "the 28th clay is still soft")
        assertNull(player.equipment[EquipmentType.GLOVES.id], "the bracelet crumbled")
        assertEquals(Items.CLAY, BraceletOfClay.reward(player))
        assertEquals(BraceletOfClay.CHARGES, BraceletOfClay.charges(player), "a new bracelet starts full")
    }

    private fun player(bracelet: Boolean): Player {
        val player = mockk<Player>(relaxed = true)
        val equipment = ItemContainer(DefinitionSet(), EQUIPMENT_KEY)
        if (bracelet) equipment[EquipmentType.GLOVES.id] = Item(Items.BRACELET_OF_CLAY)
        every { player.equipment } returns equipment
        every { player.attr } returns AttributeMap()
        return player
    }
}
