package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getAttackStyle
import gg.rsmod.plugins.api.ext.getWeaponType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/** OSRS Wiki attack-range rule: salamanders can attack only an adjacent target. */
class SalamanderRangeTests {
    @Test
    fun `all 667 salamander families keep the adjacent attack range`() {
        for (item in SALAMANDERS) {
            val player = mockk<Player>(relaxed = true)
            val equipment = ItemContainer(mockk(relaxed = true), EQUIPMENT_KEY)
            equipment[EquipmentType.WEAPON.id] = Item(item)
            every { player.equipment } returns equipment
            every { player.getWeaponType() } returns WeaponType.SALAMANDER.id
            every { player.getAttackStyle() } returns 1

            assertEquals(1, RangedCombatStrategy.getAttackRange(player), "item=$item")
        }
    }

    private companion object {
        val SALAMANDERS =
            intArrayOf(
                Items.SWAMP_LIZARD,
                Items.ORANGE_SALAMANDER,
                Items.RED_SALAMANDER,
                Items.BLACK_SALAMANDER,
            )
    }
}
