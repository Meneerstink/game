package gg.rsmod.plugins.content.skills.thieving.pickpocketing

import gg.rsmod.game.fs.DefinitionSet
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

/**
 * OSRS-audit 2026-09-17b, "armour" family - Rogue equipment (see [Pickpocketing.rogueOutfitDoubleLootChance]'s
 * own source note for the full citation and the deliberately-untouched pre-existing `getMultiplier` finding).
 * Only the newly-added, independently-testable [Pickpocketing.rogueOutfitDoubleLootChance] unit is covered here,
 * matching this project's established pattern of testing an extracted unit directly rather than the full
 * suspend `pickpocket` flow (which also needs a `QueueTask`/`DropTableFactory` harness this codebase doesn't have).
 */
class PickpocketingTests {
    @Test
    fun `rogueOutfitDoubleLootChance is 0 with no rogue equipment worn`() {
        val player = newPlayer()
        assertEquals(0.0, Pickpocketing.rogueOutfitDoubleLootChance(player), 1e-9)
    }

    @Test
    fun `rogueOutfitDoubleLootChance is 15 percent per piece worn`() {
        assertEquals(0.15, Pickpocketing.rogueOutfitDoubleLootChance(newPlayer(Items.ROGUE_TOP)), 1e-9)
        assertEquals(0.30, Pickpocketing.rogueOutfitDoubleLootChance(newPlayer(Items.ROGUE_TOP, Items.ROGUE_MASK)), 1e-9)
        assertEquals(
            0.60,
            Pickpocketing.rogueOutfitDoubleLootChance(
                newPlayer(Items.ROGUE_TOP, Items.ROGUE_MASK, Items.ROGUE_TROUSERS, Items.ROGUE_GLOVES),
            ),
            1e-9,
        )
    }

    @Test
    fun `rogueOutfitDoubleLootChance is guaranteed (1_0) with the full 5-piece set, not 75 percent`() {
        val player =
            newPlayer(Items.ROGUE_TOP, Items.ROGUE_MASK, Items.ROGUE_TROUSERS, Items.ROGUE_GLOVES, Items.ROGUE_BOOTS)
        assertEquals(1.0, Pickpocketing.rogueOutfitDoubleLootChance(player), 1e-9)
    }

    private fun newPlayer(vararg wornItems: Int): Player {
        val player = mockk<Player>(relaxed = true)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        val slots =
            listOf(
                EquipmentType.CHEST, EquipmentType.HEAD, EquipmentType.LEGS, EquipmentType.GLOVES, EquipmentType.BOOTS,
            )
        wornItems.forEachIndexed { index, id -> equipment[slots[index].id] = Item(id) }
        every { player.equipment } returns equipment
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
