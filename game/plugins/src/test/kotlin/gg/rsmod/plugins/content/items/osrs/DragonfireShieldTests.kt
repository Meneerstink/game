package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * OSRS-IMPORT Dragonfire shield charges against the wiki (fetched 2026-09-16, see [DragonfireShield]).
 */
class DragonfireShieldTests {
    @Test
    fun `a charged ward or wyvern shield without a counter is full, the single-id shield stays at 0`() {
        // Owner 2026-09-18: a spawned charged Dragonfire ward read as 0 charges, so Empty skipped its burst sound.
        assertEquals(DragonfireShield.MAX_CHARGES, DragonfireShield.charges(Item(Items.DRAGONFIRE_WARD)))
        assertEquals(DragonfireShield.MAX_CHARGES, DragonfireShield.charges(Item(Items.ANCIENT_WYVERN_SHIELD)))
        assertEquals(0, DragonfireShield.charges(Item(Items.DRAGONFIRE_WARD_UNCHARGED)))
        assertEquals(0, DragonfireShield.charges(Item(Items.DRAGONFIRE_SHIELD)))
    }

    @Test
    fun `charges default to 0 and are readable back after withCharges`() {
        val fresh = Item(Items.DRAGONFIRE_SHIELD)
        assertEquals(0, DragonfireShield.charges(fresh))
        val charged = DragonfireShield.withCharges(fresh, 12)
        assertEquals(12, DragonfireShield.charges(charged))
        assertEquals(12, charged.attr[ItemAttribute.CHARGES])
    }

    @Test
    fun `charges are capped at 50 and clamped to 0 at the floor`() {
        val overCharged = DragonfireShield.withCharges(Item(Items.DRAGONFIRE_SHIELD), 999)
        assertEquals(DragonfireShield.MAX_CHARGES, DragonfireShield.charges(overCharged))
        val emptied = DragonfireShield.withCharges(overCharged, -5)
        assertEquals(0, DragonfireShield.charges(emptied))
        assertNull(emptied.attr[ItemAttribute.CHARGES], "0 charges is stored as no attribute, matching the other charged items' convention")
    }

    @Test
    fun `gainChargeFromDragonfire adds exactly one charge only while the real dragonfire shield is worn`() {
        val player = newPlayerWithShield(Items.DRAGONFIRE_SHIELD, charges = 10)
        DragonfireShield.gainChargeFromDragonfire(player)
        assertEquals(11, DragonfireShield.charges(player.equipment[EquipmentType.SHIELD.id]!!))

        val unrelated = newPlayerWithShield(Items.ANTIDRAGON_SHIELD, charges = 0)
        DragonfireShield.gainChargeFromDragonfire(unrelated)
        assertEquals(Items.ANTIDRAGON_SHIELD, unrelated.equipment[EquipmentType.SHIELD.id]!!.id, "an unrelated shield is never touched")
    }

    @Test
    fun `gainChargeFromDragonfire never exceeds the 50-charge maximum`() {
        val player = newPlayerWithShield(Items.DRAGONFIRE_SHIELD, charges = DragonfireShield.MAX_CHARGES)
        DragonfireShield.gainChargeFromDragonfire(player)
        assertEquals(DragonfireShield.MAX_CHARGES, DragonfireShield.charges(player.equipment[EquipmentType.SHIELD.id]!!))
    }

    private fun newPlayerWithShield(
        shieldId: Int,
        charges: Int,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        val equipment = ItemContainer(gg.rsmod.game.fs.DefinitionSet(), EQUIPMENT_KEY)
        equipment[EquipmentType.SHIELD.id] = if (charges > 0) DragonfireShield.withCharges(Item(shieldId), charges) else Item(shieldId)
        every { player.equipment } returns equipment
        return player
    }
}
